package com.mahou.textbox;

import android.content.Context;
import android.net.Uri;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;

import androidx.annotation.NonNull;
import androidx.webkit.WebViewAssetLoader;
import androidx.webkit.WebViewClientCompat;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Locale;

public final class LocalWebContent {
    public static final String ORIGIN = "https://appassets.androidplatform.net";
    public static final String START_URL = ORIGIN + "/assets/mobile.html";
    public static final String CUSTOM_BASE_URL = ORIGIN + "/custom/";

    private LocalWebContent() {
    }

    public static WebViewClientCompat createClient(Context context, Runnable pageReady) {
        File customRoot = new File(context.getFilesDir(), "assets");
        WebViewAssetLoader loader = new WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(context))
                .addPathHandler("/custom/", new PrivateAssetPathHandler(customRoot))
                .build();
        return new LocalClient(loader, pageReady);
    }

    private static final class LocalClient extends WebViewClientCompat {
        private final WebViewAssetLoader loader;
        private final Runnable pageReady;

        LocalClient(WebViewAssetLoader loader, Runnable pageReady) {
            this.loader = loader;
            this.pageReady = pageReady;
        }

        @Override
        public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
            return loader.shouldInterceptRequest(request.getUrl());
        }

        @Override
        @SuppressWarnings("deprecation")
        public WebResourceResponse shouldInterceptRequest(WebView view, String url) {
            return loader.shouldInterceptRequest(Uri.parse(url));
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            return !isAllowed(request.getUrl());
        }

        @Override
        @SuppressWarnings("deprecation")
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            return !isAllowed(Uri.parse(url));
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            if (START_URL.equals(url) && pageReady != null) {
                pageReady.run();
            }
        }

        private boolean isAllowed(Uri uri) {
            return "https".equals(uri.getScheme())
                    && "appassets.androidplatform.net".equals(uri.getHost())
                    && (uri.getPath().startsWith("/assets/") || uri.getPath().startsWith("/custom/"));
        }
    }

    private static final class PrivateAssetPathHandler implements WebViewAssetLoader.PathHandler {
        private final File root;

        PrivateAssetPathHandler(File root) {
            this.root = root;
        }

        @Override
        public WebResourceResponse handle(@NonNull String path) {
            try {
                File canonicalRoot = root.getCanonicalFile();
                File requested = new File(canonicalRoot, path).getCanonicalFile();
                if (!requested.getPath().startsWith(canonicalRoot.getPath() + File.separator)
                        || !requested.isFile()) {
                    return null;
                }
                String mime = requested.getName().toLowerCase(Locale.ROOT).endsWith(".png")
                        ? "image/png"
                        : "image/jpeg";
                return new WebResourceResponse(mime, null, new FileInputStream(requested));
            } catch (IOException error) {
                return null;
            }
        }
    }
}
