package com.mahou.textbox;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileNotFoundException;

public class ImageShareProvider extends ContentProvider {
    private static final String SHARE_FILE_NAME = "mahou_textbox.png";
    private static final String SHARE_MIME = "image/png";

    private File sharedImage(Uri uri) throws FileNotFoundException {
        if (!"com.mahou.textbox.share".equals(uri.getAuthority())) throw new FileNotFoundException();
        if ("/shared.png".equals(uri.getPath())) return new File(new File(getContext().getCacheDir(), "share"), SHARE_FILE_NAME);
        String path = uri.getPath();
        if (path != null && path.matches("/ime/[0-9a-f-]{36}\\.png")) {
            return new File(new File(getContext().getCacheDir(), "ime-share"), path.substring(5));
        }
        throw new FileNotFoundException("Unknown image URI");
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public String getType(Uri uri) {
        return SHARE_MIME;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (!"r".equals(mode)) throw new FileNotFoundException("Read only");
        return ParcelFileDescriptor.open(sharedImage(uri), ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override
    public AssetFileDescriptor openAssetFile(Uri uri, String mode) throws FileNotFoundException {
        if (!"r".equals(mode)) throw new FileNotFoundException("Read only");
        File file = sharedImage(uri);
        ParcelFileDescriptor descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
        return new AssetFileDescriptor(descriptor, 0, file.length());
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        File file;
        try { file = sharedImage(uri); } catch (FileNotFoundException error) { return null; }
        MatrixCursor cursor = new MatrixCursor(new String[] {
                OpenableColumns.DISPLAY_NAME,
                OpenableColumns.SIZE
        });
        cursor.addRow(new Object[] { file.getName(), file.length() });
        return cursor;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }
}
