package com.wahshi.emb190cbt;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.res.AssetManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends Activity {
    private static final String HOST = "appassets.androidplatform.net";
    private static final String PREFIX = "/assets/";
    private static final String START_URL = "https://" + HOST + PREFIX + "index.html";

    private FrameLayout root;
    private TextView statusView;
    private WebView webView;
    private View customView;
    private WebChromeClient.CustomViewCallback customViewCallback;
    private WebChromeClient chromeClient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.rgb(16, 42, 67));
        getWindow().setNavigationBarColor(Color.rgb(16, 42, 67));

        root = new FrameLayout(this);
        root.setBackgroundColor(Color.rgb(16, 42, 67));

        statusView = new TextView(this);
        statusView.setText("EMB190 CBT\nLoading…");
        statusView.setTextColor(Color.WHITE);
        statusView.setTextSize(22f);
        statusView.setGravity(Gravity.CENTER);
        statusView.setPadding(40, 40, 40, 40);
        root.addView(statusView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        setContentView(root);
        root.postDelayed(() -> initializeWebView(savedInstanceState), 350);
    }

    private void initializeWebView(Bundle savedInstanceState) {
        try {
            try (InputStream ignored = getAssets().open("index.html", AssetManager.ACCESS_STREAMING)) {
                // Asset exists.
            }

            statusView.setText("EMB190 CBT\nStarting training system…");

            webView = new WebView(this);
            WebSettings settings = webView.getSettings();
            settings.setJavaScriptEnabled(true);
            settings.setDomStorageEnabled(true);
            settings.setDatabaseEnabled(true);
            settings.setMediaPlaybackRequiresUserGesture(false);
            settings.setBuiltInZoomControls(true);
            settings.setDisplayZoomControls(false);
            settings.setSupportZoom(true);
            settings.setAllowFileAccess(false);
            settings.setAllowContentAccess(false);
            settings.setCacheMode(WebSettings.LOAD_DEFAULT);
            settings.setUserAgentString(settings.getUserAgentString() + " EMB190APK/2.0");

            webView.setBackgroundColor(Color.rgb(16, 42, 67));
            webView.setWebViewClient(new LocalAssetClient(getAssets()));

            chromeClient = new WebChromeClient() {
                @Override
                public void onShowCustomView(View view, CustomViewCallback callback) {
                    if (customView != null) {
                        callback.onCustomViewHidden();
                        return;
                    }
                    customView = view;
                    customViewCallback = callback;
                    webView.setVisibility(View.GONE);
                    root.addView(customView, new FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT));
                    getWindow().getDecorView().setSystemUiVisibility(
                            View.SYSTEM_UI_FLAG_FULLSCREEN |
                            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
                }

                @Override
                public void onHideCustomView() {
                    if (customView == null) return;
                    root.removeView(customView);
                    customView = null;
                    webView.setVisibility(View.VISIBLE);
                    getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
                    if (customViewCallback != null) customViewCallback.onCustomViewHidden();
                    customViewCallback = null;
                }
            };
            webView.setWebChromeClient(chromeClient);

            root.addView(webView, 0, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));

            if (savedInstanceState == null || webView.restoreState(savedInstanceState) == null) {
                webView.loadUrl(START_URL);
            }
        } catch (Throwable t) {
            showStartupError(t);
        }
    }

    private void showStartupError(Throwable t) {
        String detail = t.getClass().getSimpleName();
        if (!TextUtils.isEmpty(t.getMessage())) detail += ": " + t.getMessage();
        statusView.setText("EMB190 CBT could not start.\n\n" + detail +
                "\n\nTake a screenshot of this screen and send it to ChatGPT.");
        statusView.setVisibility(View.VISIBLE);
        statusView.bringToFront();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        if (webView != null) webView.saveState(outState);
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onBackPressed() {
        if (customView != null && chromeClient != null) {
            chromeClient.onHideCustomView();
        } else if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.stopLoading();
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }

    private class LocalAssetClient extends WebViewClient {
        private final AssetManager assets;

        LocalAssetClient(AssetManager assets) {
            this.assets = assets;
        }

        @Override
        public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
            Uri uri = request.getUrl();
            if (!HOST.equalsIgnoreCase(uri.getHost())) return null;
            return assetResponse(uri);
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            Uri uri = request.getUrl();
            if (HOST.equalsIgnoreCase(uri.getHost())) return false;
            String scheme = uri.getScheme();
            if (scheme == null) return true;
            if (scheme.equals("about") || scheme.equals("data") || scheme.equals("blob") || scheme.equals("javascript")) {
                return false;
            }
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, uri));
            } catch (ActivityNotFoundException e) {
                Toast.makeText(MainActivity.this, "No app can open this link.", Toast.LENGTH_SHORT).show();
            }
            return true;
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            super.onPageFinished(view, url);
            if (statusView != null) statusView.setVisibility(View.GONE);
        }

        @Override
        public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
            super.onReceivedError(view, request, error);
            if (request.isForMainFrame()) {
                statusView.setText("EMB190 CBT page error.\n\n" + error.getDescription() +
                        "\n\nTake a screenshot and send it to ChatGPT.");
                statusView.setVisibility(View.VISIBLE);
                statusView.bringToFront();
            }
        }

        private WebResourceResponse assetResponse(Uri uri) {
            String path = uri.getPath();
            if (path == null || path.equals("/") || path.isEmpty()) path = PREFIX + "index.html";
            if (!path.startsWith(PREFIX)) return error(404, "Not Found");
            path = path.substring(PREFIX.length());
            if (path.contains("..")) return error(403, "Forbidden");

            String mime = mimeType(path);
            String encoding = isText(mime) ? "UTF-8" : null;
            try {
                InputStream in = assets.open(path, AssetManager.ACCESS_STREAMING);
                Map<String, String> headers = new HashMap<>();
                headers.put("Cache-Control", "no-cache");
                headers.put("Access-Control-Allow-Origin", "https://" + HOST);
                return new WebResourceResponse(mime, encoding, 200, "OK", headers, in);
            } catch (FileNotFoundException e) {
                return error(404, "Not Found");
            } catch (IOException e) {
                return error(500, "Asset Error");
            }
        }

        private WebResourceResponse error(int code, String message) {
            byte[] body = message.getBytes(StandardCharsets.UTF_8);
            return new WebResourceResponse(
                    "text/plain", "UTF-8", code, message,
                    Collections.emptyMap(), new ByteArrayInputStream(body));
        }
    }

    private static boolean isText(String mime) {
        return mime.startsWith("text/") || mime.contains("javascript") || mime.contains("json") || mime.contains("xml") || mime.contains("manifest");
    }

    private static String mimeType(String path) {
        String p = path.toLowerCase(Locale.US);
        if (p.endsWith(".html") || p.endsWith(".htm")) return "text/html";
        if (p.endsWith(".js")) return "application/javascript";
        if (p.endsWith(".css")) return "text/css";
        if (p.endsWith(".json")) return "application/json";
        if (p.endsWith(".webmanifest")) return "application/manifest+json";
        if (p.endsWith(".wasm")) return "application/wasm";
        if (p.endsWith(".swf")) return "application/x-shockwave-flash";
        if (p.endsWith(".txt") || p.endsWith(".md")) return "text/plain";
        if (p.endsWith(".svg")) return "image/svg+xml";
        if (p.endsWith(".png")) return "image/png";
        if (p.endsWith(".jpg") || p.endsWith(".jpeg")) return "image/jpeg";
        if (p.endsWith(".gif")) return "image/gif";
        if (p.endsWith(".ico")) return "image/x-icon";
        if (p.endsWith(".xml")) return "application/xml";
        if (p.endsWith(".woff")) return "font/woff";
        if (p.endsWith(".woff2")) return "font/woff2";
        if (p.endsWith(".ttf")) return "font/ttf";
        return "application/octet-stream";
    }
}
