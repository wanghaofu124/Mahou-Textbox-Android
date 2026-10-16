package com.mahou.textbox;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;

import androidx.core.content.ContextCompat;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

@RunWith(AndroidJUnit4.class)
public class GeneratorStateStoreInstrumentedTest {
    private Context context;
    private SharedPreferences isolatedPreferences;
    private final BlockingQueue<Intent> broadcasts = new LinkedBlockingQueue<>();
    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            broadcasts.add(intent);
        }
    };

    @Before
    public void setUp() {
        Context app = ApplicationProvider.getApplicationContext();
        String suffix = "-state-test-" + UUID.randomUUID();
        context = new ContextWrapper(app) {
            @Override
            public SharedPreferences getSharedPreferences(String name, int mode) {
                isolatedPreferences = super.getSharedPreferences(name + suffix, mode);
                return isolatedPreferences;
            }
        };
        ContextCompat.registerReceiver(context, receiver,
                new IntentFilter(GeneratorStateStore.ACTION_STATE_CHANGED),
                ContextCompat.RECEIVER_NOT_EXPORTED);
    }

    @After
    public void tearDown() {
        context.unregisterReceiver(receiver);
        if (isolatedPreferences != null) {
            isolatedPreferences.edit().clear().commit();
        }
    }

    @Test
    public void partialPatchesMergeAndRevisionsPersist() throws Exception {
        JSONObject initial = new JSONObject(GeneratorStateStore.read(context));
        assertEquals(5, initial.length());
        assertEquals(0L, initial.getLong("revision"));
        for (String field : new String[]{"text", "character", "emotion", "background"}) {
            assertEquals("", initial.getString(field));
        }
        assertEquals(0, isolatedPreferences.getAll().size());

        JSONObject seeded = new JSONObject(GeneratorStateStore.update(context,
                "{\"text\":\"legacy text\",\"character\":\"alisa\",\"emotion\":\"happy\",\"background\":\"room\"}"));
        assertEquals(1L, seeded.getLong("revision"));
        JSONObject changed = new JSONObject(GeneratorStateStore.update(context,
                "{\"text\":\"quotes \\\" and newline \\n\"}"));
        assertEquals("alisa", changed.getString("character"));
        assertEquals("happy", changed.getString("emotion"));
        assertEquals("room", changed.getString("background"));
        assertEquals(2L, changed.getLong("revision"));
        assertEquals(changed.toString(), GeneratorStateStore.read(context));

        JSONObject repeated = new JSONObject(GeneratorStateStore.update(context,
                "{\"character\":\"alisa\"}"));
        assertEquals(3L, repeated.getLong("revision"));
        assertEquals(repeated.toString(), GeneratorStateStore.update(context, "{}"));
    }

    @Test
    public void successfulUpdateBroadcastsFullStateInSameProcess() throws Exception {
        String json = GeneratorStateStore.update(context, "{\"text\":\"from main\"}");
        Intent delivered = broadcasts.poll(5, TimeUnit.SECONDS);
        assertNotNull(delivered);
        assertEquals(context.getPackageName(), delivered.getPackage());
        assertEquals(json, delivered.getStringExtra(GeneratorStateStore.EXTRA_STATE_JSON));
        assertEquals(5, new JSONObject(json).length());
        GeneratorStateStore.update(context, "{}");
        assertNull(broadcasts.poll(200, TimeUnit.MILLISECONDS));
    }

    @Test
    public void invalidFieldsAreRejectedAtomicallyWithoutBroadcast() throws Exception {
        String original = GeneratorStateStore.read(context);
        for (String patch : new String[]{"{\"text\":\"must not apply\",\"unknown\":\"x\"}",
                "{\"revision\":99}", "{\"character\":null}", "{\"emotion\":false}",
                "{\"background\":123}", "[]", "{broken", "{} trailing"}) {
            try {
                GeneratorStateStore.update(context, patch);
                fail("Invalid patch accepted: " + patch);
            } catch (IllegalArgumentException expected) {
                assertEquals(original, GeneratorStateStore.read(context));
            }
        }
        assertNull(broadcasts.poll(200, TimeUnit.MILLISECONDS));
    }

    @Test
    public void concurrentWindowsDoNotLosePartialPatchesOrRevisions() throws Exception {
        ExecutorService workers = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<?> text = workers.submit(() -> updateRepeatedly(start, "text"));
            Future<?> character = workers.submit(() -> updateRepeatedly(start, "character"));
            start.countDown();
            text.get(15, TimeUnit.SECONDS);
            character.get(15, TimeUnit.SECONDS);
            JSONObject state = new JSONObject(GeneratorStateStore.read(context));
            assertEquals("text-19", state.getString("text"));
            assertEquals("character-19", state.getString("character"));
            assertEquals(40L, state.getLong("revision"));
            for (long revision = 1; revision <= 40; revision++) {
                Intent intent = broadcasts.poll(5, TimeUnit.SECONDS);
                assertNotNull(intent);
                JSONObject delivered = new JSONObject(intent.getStringExtra(GeneratorStateStore.EXTRA_STATE_JSON));
                assertEquals(revision, delivered.getLong("revision"));
            }
        } finally {
            workers.shutdownNow();
        }
    }

    @Test
    public void revisionCannotOverflow() {
        GeneratorStateStore.read(context);
        isolatedPreferences.edit().putLong("revision", Long.MAX_VALUE).commit();
        try {
            GeneratorStateStore.update(context, "{\"text\":\"overflow\"}");
            fail("Revision overflow accepted");
        } catch (IllegalStateException expected) {
            assertEquals(Long.MAX_VALUE, isolatedPreferences.getLong("revision", -1));
            assertEquals("", isolatedPreferences.getString("text", ""));
        }
    }

    private void updateRepeatedly(CountDownLatch start, String field) {
        try {
            start.await();
            for (int i = 0; i < 20; i++) {
                GeneratorStateStore.update(context, new JSONObject()
                        .put(field, field + "-" + i).toString());
            }
        } catch (Exception error) {
            throw new AssertionError(error);
        }
    }
}
