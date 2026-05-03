package com.lunatv.app;

import android.app.Activity;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

public class MainActivity extends Activity {

    private WebView webView;
    private static final String SITE_URL = "https://cftv.kouzhaobo.com";

    // 修复图片在 WebView 中的显示问题
    private static final String FIX_JS =
        "(function() {" +
        "  if (window.__lunatvFixed) return;" +
        "  window.__lunatvFixed = true;" +
        // 注入 CSS
        "  var s = document.createElement('style');" +
        "  s.textContent = '' +" +
        "    'html, body { overflow-x: hidden !important; }' +" +
        "    '[data-nimg] { position: relative !important; width: 100% !important; height: auto !important; }' +" +
        "    '[data-nimg] img { position: static !important; width: 100% !important; height: auto !important; object-fit: cover !important; display: block !important; }' +" +
        "    'div[class*=\"aspect\"] { position: relative !important; }' +" +
        "    'div[class*=\"aspect\"] > div { position: absolute !important; inset: 0 !important; }' +" +
        "    'div[class*=\"aspect\"] > div img { position: absolute !important; top: 0 !important; left: 0 !important; width: 100% !important; height: 100% !important; object-fit: cover !important; }' +" +
        "    'img[loading=\"lazy\"] { display: block !important; }';" +
        "  document.head.appendChild(s);" +
        // 为 aspect 容器设置 padding-bottom hack（兼容旧 WebView）
        "  document.querySelectorAll('[class*=\"aspect-\"]').forEach(function(el) {" +
        "    var m = el.className.match(/aspect-\\\\[(\\\\d+)\\\\/(\\\\d+)\\\\]/);" +
        "    if (m) {" +
        "      var ratio = (parseInt(m[2]) / parseInt(m[1]) * 100).toFixed(2);" +
        "      el.style.paddingBottom = ratio + '%';" +
        "      el.style.height = '0';" +
        "      el.style.overflow = 'hidden';" +
        "      el.style.position = 'relative';" +
        "    }" +
        "  });" +
        // 为 aspect 容器内的图片设置绝对定位
        "  document.querySelectorAll('[class*=\"aspect-\"] img').forEach(function(img) {" +
        "    img.style.position = 'absolute';" +
        "    img.style.top = '0';" +
        "    img.style.left = '0';" +
        "    img.style.width = '100%';" +
        "    img.style.height = '100%';" +
        "    img.style.objectFit = 'cover';" +
        "  });" +
        "})()";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 全屏沉浸式
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        );

        // 隐藏导航栏
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_FULLSCREEN
            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        );

        webView = new WebView(this);
        setContentView(webView);

        // WebView 设置
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);

        // 硬件加速
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);

        // 页面加载完成后注入修复
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                if (url.contains("kouzhaobo.com") || url.startsWith("javascript:")) {
                    return false;
                }
                return false;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                injectFix(view);
            }
        });

        // 全屏视频支持
        webView.setWebChromeClient(new WebChromeClient());

        // 加载网站
        webView.loadUrl(SITE_URL);
    }

    private void injectFix(WebView view) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
            view.evaluateJavascript(FIX_JS, null);
            // SPA 路由切换后重新注入（重置标志位）
            view.postDelayed(() -> {
                view.evaluateJavascript("window.__lunatvFixed = false;", null);
                view.evaluateJavascript(FIX_JS, null);
            }, 2000);
        }
    }

    // 返回键：网页后退
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && webView.canGoBack()) {
            webView.goBack();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onResume() {
        super.onResume();
        webView.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        webView.onPause();
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.destroy();
        }
        super.onDestroy();
    }
}
