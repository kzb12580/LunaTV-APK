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
    private String siteUrl = BuildConfig.SERVER_URL;

    // 注入原生桥接 + 修复图片 + 屏蔽首次 Douban 错误弹窗
    private static final String INIT_JS =
        "(function() {" +
        // ===== 0. 注入原生桥接标识 =====
        "  if (!window.LunaNative) {" +
        "    window.LunaNative = {" +
        "      isNative: true," +
        "      playVideo: function(url, title) {" +
        "        if (window._lunaBridge) {" +
        "          window._lunaBridge.playVideo(url, title || '');" +
        "        }" +
        "      }," +
        "      playVideoWithPosition: function(url, title, pos) {" +
        "        if (window._lunaBridge) {" +
        "          window._lunaBridge.playVideoWithPosition(url, title || '', pos || 0);" +
        "        }" +
        "      }" +
        "    };" +
        "  }" +
        // ===== 1. 屏蔽首次 Douban 错误弹窗 =====
        "  if (!window.__errorPatched) {" +
        "    window.__errorPatched = true;" +
        "    window.__firstLoadTime = Date.now();" +
        "    var origTrigger = window.triggerGlobalError;" +
        "    if (typeof origTrigger === 'function') {" +
        "      window.triggerGlobalError = function(msg) {" +
        "        if (Date.now() - window.__firstLoadTime < 8000 && msg && msg.indexOf('豆瓣') !== -1) {" +
        "          console.log('[LunaTV-APK] 暂时屏蔽首次 Douban 错误:', msg);" +
        "          return;" +
        "        }" +
        "        return origTrigger.apply(this, arguments);" +
        "      };" +
        "    }" +
        "    var origDispatch = window.dispatchEvent;" +
        "    window.dispatchEvent = function(evt) {" +
        "      if (evt && evt.type === 'global-error' && Date.now() - window.__firstLoadTime < 8000) {" +
        "        var detail = evt.detail || '';" +
        "        if (typeof detail === 'string' && detail.indexOf('豆瓣') !== -1) {" +
        "          console.log('[LunaTV-APK] 暂时屏蔽首次 Douban 错误事件:', detail);" +
        "          return false;" +
        "        }" +
        "      }" +
        "      return origDispatch.apply(this, arguments);" +
        "    };" +
        "    var origCE = window.CustomEvent;" +
        "    if (typeof origCE === 'function') {" +
        "      window.CustomEvent = function(type, opts) {" +
        "        var evt = new origCE(type, opts);" +
        "        if (type === 'global-error' && Date.now() - window.__firstLoadTime < 8000) {" +
        "          var d = opts && opts.detail || '';" +
        "          if (typeof d === 'string' && d.indexOf('豆瓣') !== -1) {" +
        "            evt.stopPropagation = function(){};" +
        "          }" +
        "        }" +
        "        return evt;" +
        "      };" +
        "      window.CustomEvent.prototype = origCE.prototype;" +
        "    }" +
        "    var observer = new MutationObserver(function(mutations) {" +
        "      if (Date.now() - window.__firstLoadTime > 8000) return;" +
        "      mutations.forEach(function(m) {" +
        "        m.addedNodes.forEach(function(node) {" +
        "          if (node.nodeType === 1) {" +
        "            var text = node.textContent || '';" +
        "            if (text.indexOf('豆瓣') !== -1 && text.indexOf('失败') !== -1) {" +
        "              console.log('[LunaTV-APK] 移除 Douban 错误弹窗');" +
        "              node.remove();" +
        "            }" +
        "          }" +
        "        });" +
        "      });" +
        "    });" +
        "    observer.observe(document.body || document.documentElement, {childList: true, subtree: true});" +
        "    setTimeout(function() {" +
        "      if (origTrigger) window.triggerGlobalError = origTrigger;" +
        "      if (origDispatch) window.dispatchEvent = origDispatch;" +
        "      observer.disconnect();" +
        "    }, 8000);" +
        "  }" +
        // ===== 2. 修复图片渲染 =====
        "  if (window.__imgFixed) return;" +
        "  window.__imgFixed = true;" +
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

        // ★ 注册 JS 桥接 — 网页通过 window._lunaBridge 调用原生播放器
        webView.addJavascriptInterface(new LunaBridge(this), "_lunaBridge");

        // 页面加载完成后注入修复 + 桥接
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

        webView.setWebChromeClient(new WebChromeClient());
        webView.loadUrl(siteUrl);
    }

    private void injectFix(WebView view) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
            view.evaluateJavascript(INIT_JS, null);
            view.postDelayed(() -> {
                view.evaluateJavascript("window.__imgFixed = false;", null);
                view.evaluateJavascript(INIT_JS, null);
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
