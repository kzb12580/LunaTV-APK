package com.lunatv.app;

import android.content.Context;
import android.content.Intent;
import android.webkit.JavascriptInterface;

/**
 * JavaScript Bridge — 网页端通过 window.LunaNative 调用原生功能
 */
public class LunaBridge {

    private final Context context;

    public LunaBridge(Context context) {
        this.context = context;
    }

    /**
     * 播放视频 — 网页端调用: window.LunaNative.playVideo(url, title)
     */
    @JavascriptInterface
    public void playVideo(String url, String title) {
        if (url == null || url.isEmpty()) return;
        Intent intent = new Intent(context, PlayerActivity.class);
        intent.putExtra("video_url", url);
        intent.putExtra("video_title", title != null ? title : "LunaTV");
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }

    /**
     * 播放视频（带进度） — 网页端调用: window.LunaNative.playVideoWithPosition(url, title, positionMs)
     */
    @JavascriptInterface
    public void playVideoWithPosition(String url, String title, long positionMs) {
        if (url == null || url.isEmpty()) return;
        Intent intent = new Intent(context, PlayerActivity.class);
        intent.putExtra("video_url", url);
        intent.putExtra("video_title", title != null ? title : "LunaTV");
        intent.putExtra("video_position", positionMs);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }

    /**
     * 通知原生退出播放器（用于播放结束回调）
     */
    @JavascriptInterface
    public boolean isNativePlayer() {
        return true;
    }
}
