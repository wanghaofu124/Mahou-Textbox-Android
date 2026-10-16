package com.mahou.textbox;

import android.content.Context;
import android.net.Uri;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;

import androidx.annotation.NonNull;
import androidx.webkit.WebViewAssetLoader;
import androidx.webkit.WebViewClientCompat;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.WeakHashMap;

public final class LocalWebContent {
    public static final String ORIGIN = "https://appassets.androidplatform.net";
    public static final String START_URL = ORIGIN + "/assets/mobile.html";
    public static final String DOCK_URL = ORIGIN + "/assets/dock.html";
    public static final String CUSTOM_BASE_URL = ORIGIN + "/custom/";
    public static final String IMPORT_BASE_URL = ORIGIN + "/imports/";

    // Weak keys protect loaded images without retaining destroyed WebViews.
    private static final WeakHashMap<WebView, Set<String>> ACTIVE_IMPORTS = new WeakHashMap<>();

    private LocalWebContent() {
    }

    public static WebViewClientCompat createClient(Context context, Runnable pageReady) {
        File customRoot = new File(context.getFilesDir(), "assets");
        WebViewAssetLoader loader = new WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(context))
                .addPathHandler("/custom/", new PrivateAssetPathHandler(customRoot, false))
                .addPathHandler("/imports/", new PrivateAssetPathHandler(
                        new File(context.getCacheDir(), "imports"), true))
                .build();
        return new LocalClient(loader, pageReady);
    }

    static synchronized boolean isImportActive(String name) {
        for (Set<String> names : ACTIVE_IMPORTS.values()) {
            if (names.contains(name)) {
                return true;
            }
        }
        return false;
    }

    private static synchronized void markImportActive(WebView view, String url) {
        if (view != null && ImagePickerActivity.isImportUrl(url)) {
            Set<String> names = ACTIVE_IMPORTS.get(view);
            if (names == null) {
                names = new HashSet<>();
                ACTIVE_IMPORTS.put(view, names);
            }
            names.add(url.substring(IMPORT_BASE_URL.length()));
        }
    }

    private static synchronized void releaseImports(WebView view) {
        ACTIVE_IMPORTS.remove(view);
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
            return intercept(view, request.getUrl());
        }

        @Override
        @SuppressWarnings("deprecation")
        public WebResourceResponse shouldInterceptRequest(WebView view, String url) {
            return intercept(view, Uri.parse(url));
        }

        private WebResourceResponse intercept(WebView view, Uri uri) {
            if (!isAllowed(uri)) {
                return blockedResponse(403, "Forbidden");
            }
            if (ImagePickerActivity.isImportUrl(uri.toString())) {
                synchronized (ImagePickerActivity.class) {
                    return loadLocal(view, uri);
                }
            }
            return loadLocal(view, uri);
        }

        private WebResourceResponse loadLocal(WebView view, Uri uri) {
            WebResourceResponse response = loader.shouldInterceptRequest(uri);
            if (response == null || response.getData() == null) {
                return blockedResponse(404, "Not Found");
            }
            markImportActive(view, uri.toString());
            return response;
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
        public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
            releaseImports(view);
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            Uri uri = Uri.parse(url);
            if (isAllowed(uri) && ("/assets/mobile.html".equals(uri.getPath())
                    || "/assets/dock.html".equals(uri.getPath())
                    || "/assets/ime.html".equals(uri.getPath())
                    || "/assets/ime-editor.html".equals(uri.getPath())) && pageReady != null) {
                pageReady.run();
            }
        }

        private boolean isAllowed(Uri uri) {
            String path = uri.getPath();
            return "https".equals(uri.getScheme())
                    && "appassets.androidplatform.net".equals(uri.getHost())
                    && uri.getUserInfo() == null
                    && (uri.getPort() == -1 || uri.getPort() == 443)
                    && path != null && path.indexOf('\\') < 0
                    && !path.contains("/../") && !path.contains("/./")
                    && !path.endsWith("/..") && !path.endsWith("/.")
                    && (path.startsWith("/assets/") || path.startsWith("/custom/")
                    || ImagePickerActivity.isImportUrl(uri.toString()));
        }
    }

    private static WebResourceResponse blockedResponse(int status, String reason) {
        return new WebResourceResponse("text/plain", "UTF-8", status, reason,
                Collections.singletonMap("Cache-Control", "no-store"),
                new ByteArrayInputStream(new byte[0]));
    }

    private static final class PrivateAssetPathHandler implements WebViewAssetLoader.PathHandler {
        private final File root;
        private final boolean importsOnly;

        PrivateAssetPathHandler(File root, boolean importsOnly) {
            this.root = root;
            this.importsOnly = importsOnly;
        }

        @Override
        public WebResourceResponse handle(@NonNull String path) {
            try {
                if (importsOnly && !ImagePickerActivity.isGeneratedImportName(path)) {
                    return null;
                }
                File canonicalRoot = root.getCanonicalFile();
                File candidate = new File(canonicalRoot, path);
                File requested = candidate.getCanonicalFile();
                File expectedRoot = new File(root.getParentFile().getCanonicalFile(), root.getName());
                if (importsOnly && (!expectedRoot.equals(canonicalRoot)
                        || !candidate.getAbsoluteFile().equals(requested))) {
                    return null;
                }
                if (!requested.getPath().startsWith(canonicalRoot.getPath() + File.separator)
                        || !requested.isFile()) {
                    return null;
                }
                String name = requested.getName().toLowerCase(Locale.ROOT);
                String mime;
                if (name.endsWith(".png")) {
                    mime = "image/png";
                } else if (name.endsWith(".jpg") || name.endsWith(".jpeg")) {
                    mime = "image/jpeg";
                } else if (!importsOnly && name.endsWith(".webp")) {
                    mime = "image/webp";
                } else {
                    return null;
                }
                return new WebResourceResponse(mime, null, 200, "OK",
                        Collections.singletonMap("Cache-Control", "no-store"),
                        new FileInputStream(requested));
            } catch (IOException error) {
                return null;
            }
        }
    }
}
