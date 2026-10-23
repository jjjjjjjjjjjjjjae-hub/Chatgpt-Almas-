package kz.almas.onayuidemo;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.PermissionRequest;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.io.InputStream;
import java.util.Arrays;

public class MainActivity extends Activity {
    private static final String ORIGIN_HOST = "appassets.androidplatform.net";
    private static final String HOME = "https://appassets.androidplatform.net/assets/index.html";
    private static final int CAMERA_REQUEST = 203;
    private WebView webView;
    private PermissionRequest pendingCameraRequest;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.WHITE);

        webView = new WebView(this);
        webView.setBackgroundColor(Color.WHITE);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setDefaultTextEncodingName("UTF-8");

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                if ("https".equals(uri.getScheme()) && ORIGIN_HOST.equals(uri.getHost()) &&
                    "/assets/index.html".equals(uri.getPath())) {
                    try {
                        InputStream input = getAssets().open("index.html");
                        return new WebResourceResponse("text/html", "UTF-8", input);
                    } catch (Exception ignored) {
                        return new WebResourceResponse("text/plain", "UTF-8", null);
                    }
                }
                return super.shouldInterceptRequest(view, request);
            }
        });
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onPermissionRequest(final PermissionRequest request) {
                runOnUiThread(() -> {
                    Uri origin = request.getOrigin();
                    boolean trusted = "https".equals(origin.getScheme()) &&
                        ORIGIN_HOST.equals(origin.getHost());
                    boolean video = Arrays.asList(request.getResources()).contains(
                        PermissionRequest.RESOURCE_VIDEO_CAPTURE);
                    if (!trusted || !video) { request.deny(); return; }
                    if (Build.VERSION.SDK_INT >= 23 &&
                        checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                        pendingCameraRequest = request;
                        requestPermissions(new String[]{Manifest.permission.CAMERA}, CAMERA_REQUEST);
                    } else {
                        request.grant(new String[]{PermissionRequest.RESOURCE_VIDEO_CAPTURE});
                    }
                });
            }
        });

        root.addView(webView, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        TextView note = new TextView(this);
        note.setText("UI DEMO • РЕСМИ ONAY! ЕМЕС • ТӨЛЕМ ЖАСАЛМАЙДЫ");
        note.setTextSize(10);
        note.setTextColor(Color.rgb(90, 90, 100));
        note.setBackgroundColor(Color.rgb(246, 246, 248));
        note.setGravity(Gravity.CENTER);
        int height = (int) (22 * getResources().getDisplayMetrics().density);
        root.addView(note, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, height));
        setContentView(root);
        webView.loadUrl(HOME);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != CAMERA_REQUEST || pendingCameraRequest == null) return;
        PermissionRequest request = pendingCameraRequest;
        pendingCameraRequest = null;
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED)
            request.grant(new String[]{PermissionRequest.RESOURCE_VIDEO_CAPTURE});
        else request.deny();
    }

    @Override
    public void onBackPressed() {
        // Dispatch Escape to the existing HTML navigation without changing its QR data.
        webView.evaluateJavascript(
            "(function(){var ids=['page-codepay','page-my-tickets','page-monthly-pass','page-topup','page-transfer','page-qr','page-routes','page-notifications'];var open=ids.some(function(id){var e=document.getElementById(id);return e&&getComputedStyle(e).display!=='none'&&!e.classList.contains('hidden');});if(open){document.dispatchEvent(new KeyboardEvent('keydown',{key:'Escape'}));return true;}return false;})()",
            value -> {
                if (!"true".equals(value)) MainActivity.super.onBackPressed();
            });
    }

    @Override
    protected void onDestroy() {
        if (pendingCameraRequest != null) pendingCameraRequest.deny();
        if (webView != null) webView.destroy();
        super.onDestroy();
    }
}
