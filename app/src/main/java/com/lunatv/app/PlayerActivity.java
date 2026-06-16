package com.lunatv.app;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.PictureInPictureParams;
import android.content.res.Configuration;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.util.Rational;
import android.webkit.CookieManager;
import android.widget.Toast;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.OptIn;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.TrackSelectionOverride;
import androidx.media3.common.Tracks;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector;
import androidx.media3.exoplayer.hls.HlsMediaSource;
import androidx.media3.datasource.DataSource;
import androidx.media3.ui.PlayerView;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

@UnstableApi
public class PlayerActivity extends Activity {

    private ExoPlayer player;
    private PlayerView playerView;
    private DefaultTrackSelector trackSelector;

    // 手势控制
    private GestureDetector gestureDetector;
    private AudioManager audioManager;
    private int maxVolume;
    private float brightness = -1f;
    private int seekDelta = 0;
    private View gestureIndicator;
    private TextView gestureText;

    // 状态
    private long resumePosition = C.TIME_UNSET;
    private String videoTitle = "";

    @OptIn(markerClass = UnstableApi.class)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            onCreateInternal(savedInstanceState);
        } catch (Exception e) {
            Log.e("LunaTV", "PlayerActivity crash", e);
            Toast.makeText(this, "播放崩溃: " + e.getClass().getSimpleName() + ": " + e.getMessage(), Toast.LENGTH_LONG).show();
            finish();
        }
    }

    private void onCreateInternal(Bundle savedInstanceState) {
        // 全屏沉浸
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.activity_player);

        // 音量
        audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);

        // 获取 Intent 参数
        String videoUrl = getIntent().getStringExtra("video_url");
        videoTitle = getIntent().getStringExtra("video_title");
        resumePosition = getIntent().getLongExtra("video_position", C.TIME_UNSET);
        if (videoTitle == null) videoTitle = "LunaTV";

        if (videoUrl == null || videoUrl.isEmpty()) {
            finish();
            return;
        }

        // UI
        playerView = findViewById(R.id.player_view);
        gestureIndicator = findViewById(R.id.gesture_indicator);
        gestureText = findViewById(R.id.gesture_text);
        TextView titleView = findViewById(R.id.video_title);
        titleView.setText(videoTitle);

        // 返回按钮
        ImageButton backBtn = findViewById(R.id.btn_back);
        backBtn.setOnClickListener(v -> finish());

        // 初始化播放器
        trackSelector = new DefaultTrackSelector(this);
        player = new ExoPlayer.Builder(this)
            .setTrackSelector(trackSelector)
            .build();

        playerView.setPlayer(player);
        playerView.setUseController(true);

        // HTTP→HTTPS 转换（Android 9+ 默认禁止明文 HTTP）
        if (videoUrl.startsWith("http://")) {
            videoUrl = "https://" + videoUrl.substring(7);
        }

        // 构建带 headers 的 HLS 数据源
        // 部分源站需要正确的 UA 和 Referer 才返回正常内容
        Map<String, String> headers = new HashMap<>();
        headers.put("User-Agent", "AptvPlayer/1.4.10");
        String referer = videoUrl;
        try {
            Uri uri = Uri.parse(videoUrl);
            referer = uri.getScheme() + "://" + uri.getHost() + "/";
        } catch (Exception ignored) {}
        headers.put("Referer", referer);

        // 从 WebView 带上 cookies（认证用）
        try {
            String cookies = CookieManager.getInstance().getCookie(videoUrl);
            if (cookies != null && !cookies.isEmpty()) {
                headers.put("Cookie", cookies);
            }
        } catch (Exception ignored) {}

        DefaultHttpDataSource.Factory httpFactory = new DefaultHttpDataSource.Factory();
        httpFactory.setDefaultRequestProperties(headers);
        httpFactory.setConnectTimeoutMs(15000);
        httpFactory.setReadTimeoutMs(15000);

        // HLS 走自定义数据源，MP4 也走
        DataSource.Factory dataSourceFactory = httpFactory;
        HlsMediaSource.Factory hlsFactory = new HlsMediaSource.Factory(dataSourceFactory);

        // 构建 MediaItem
        MediaItem mediaItem = MediaItem.fromUri(Uri.parse(videoUrl));
        player.setMediaSource(hlsFactory.createMediaSource(mediaItem));

        // 恢复进度
        if (resumePosition != C.TIME_UNSET) {
            player.seekTo(resumePosition);
        }

        player.prepare();
        player.setPlayWhenReady(true);

        // 播放事件监听
        player.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int state) {
                if (state == Player.STATE_ENDED) {
                    // 播放结束，返回上一页
                    finish();
                }
            }

            @Override
            public void onPlayerError(PlaybackException error) {
                // 出错时显示提示并延迟关闭
                gestureIndicator.setVisibility(View.VISIBLE);
                gestureText.setText("播放出错: " + error.getMessage());
                gestureIndicator.postDelayed(() -> finish(), 3000);
            }

            @Override
            public void onTracksChanged(Tracks tracks) {
                // 自动选择最高画质
                selectBestQuality();
            }
        });

        // 手势控制
        setupGestures();
    }

    /**
     * 自动选择最高画质
     */
    private void selectBestQuality() {
        if (player == null || trackSelector == null) return;
        // 不需要手动选择，ExoPlayer 默认自适应码率 (ABR)
        // 如果需要强制最高画质，可以在这里设置
    }

    /**
     * 手势：左半屏滑动=亮度，右半屏滑动=音量，左右滑动=快进快退
     */
    @SuppressLint("ClickableViewAccessibility")
    private void setupGestures() {
        gestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onSingleTapConfirmed(MotionEvent e) {
                // 单击切换控制器显示/隐藏
                if (playerView.isControllerFullyVisible()) {
                    playerView.hideController();
                } else {
                    playerView.showController();
                }
                return true;
            }

            @Override
            public boolean onDoubleTap(MotionEvent e) {
                // 双击快进/快退 10 秒
                float x = e.getX();
                int width = playerView.getWidth();
                if (x < width * 0.4) {
                    // 左侧双击 → 快退 10s
                    player.seekTo(Math.max(0, player.getCurrentPosition() - 10000));
                    showGestureIndicator("⏪ -10s");
                } else if (x > width * 0.6) {
                    // 右侧双击 → 快进 10s
                    player.seekTo(Math.min(player.getDuration(), player.getCurrentPosition() + 10000));
                    showGestureIndicator("⏩ +10s");
                } else {
                    // 中间双击 → 暂停/播放
                    player.setPlayWhenReady(!player.isPlaying());
                    showGestureIndicator(player.isPlaying() ? "▶️ 播放" : "⏸ 暂停");
                }
                return true;
            }
        });

        playerView.setOnTouchListener((v, event) -> {
            gestureDetector.onTouchEvent(event);

            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                seekDelta = 0;
                brightness = getWindow().getAttributes().screenBrightness;
                if (brightness < 0) brightness = 0.5f;
            }

            if (event.getPointerCount() == 1 && event.getActionMasked() == MotionEvent.ACTION_MOVE) {
                float dx = event.getX() - (playerView.getWidth() / 2f);
                float dy = event.getY() - (playerView.getHeight() / 2f);

                // 垂直滑动
                if (Math.abs(dy) > 30 && Math.abs(dy) > Math.abs(dx)) {
                    if (event.getX() < playerView.getWidth() * 0.4) {
                        // 左侧上下滑 → 亮度
                        float delta = -dy / playerView.getHeight();
                        brightness = Math.max(0f, Math.min(1f, brightness + delta));
                        WindowManager.LayoutParams lp = getWindow().getAttributes();
                        lp.screenBrightness = brightness;
                        getWindow().setAttributes(lp);
                        showGestureIndicator(String.format(Locale.CHINA, "亮度 %d%%", (int) (brightness * 100)));
                    } else if (event.getX() > playerView.getWidth() * 0.6) {
                        // 右侧上下滑 → 音量
                        int volumeDelta = (int) (-dy / playerView.getHeight() * maxVolume * 0.5f);
                        int newVol = Math.max(0, Math.min(maxVolume, audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) + volumeDelta));
                        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVol, 0);
                        showGestureIndicator(String.format(Locale.CHINA, "音量 %d%%", (int) ((float) newVol / maxVolume * 100)));
                    }
                }

                // 水平滑动 → 快进/快退
                if (Math.abs(dx) > 30 && Math.abs(dx) > Math.abs(dy)) {
                    int seekMs = (int) (dx / playerView.getWidth() * 60000); // 滑满屏幕 = 60 秒
                    seekDelta += seekMs;
                    long target = player.getCurrentPosition() + seekDelta;
                    target = Math.max(0, Math.min(player.getDuration(), target));
                    showGestureIndicator(String.format(Locale.CHINA, "%s %ds",
                        seekDelta > 0 ? "⏩" : "⏪",
                        Math.abs(seekDelta / 1000)));
                }
            }

            if (event.getActionMasked() == MotionEvent.ACTION_UP) {
                if (Math.abs(seekDelta) > 1000) {
                    long target = player.getCurrentPosition() + seekDelta;
                    target = Math.max(0, Math.min(player.getDuration(), target));
                    player.seekTo(target);
                }
                seekDelta = 0;
                gestureIndicator.setVisibility(View.GONE);
            }

            return true;
        });
    }

    private void showGestureIndicator(String text) {
        gestureText.setText(text);
        gestureIndicator.setVisibility(View.VISIBLE);
        gestureIndicator.removeCallbacks(hideIndicator);
        gestureIndicator.postDelayed(hideIndicator, 800);
    }

    private final Runnable hideIndicator = () -> {
        if (gestureIndicator != null) {
            gestureIndicator.setVisibility(View.GONE);
        }
    };

    /**
     * 画中画
     */
    public void enterPip() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            PictureInPictureParams.Builder builder = new PictureInPictureParams.Builder();
            builder.setAspectRatio(new Rational(16, 9));
            enterPictureInPictureMode(builder.build());
        }
    }

    @Override
    public void onPictureInPictureModeChanged(boolean isInPictureInPictureMode, Configuration newConfig) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig);
        if (isInPictureInPictureMode) {
            playerView.hideController();
        } else {
            playerView.showController();
        }
    }

    // 生命周期
    @Override
    protected void onPause() {
        super.onPause();
        if (player != null && !isInPictureInPictureMode()) {
            player.setPlayWhenReady(false);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (player != null && !isInPictureInPictureMode()) {
            player.setPlayWhenReady(true);
        }
    }

    @Override
    protected void onDestroy() {
        if (player != null) {
            player.release();
            player = null;
        }
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (player != null && player.isPlaying()) {
            // 播放中按返回 → 进入画中画（如果支持）
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && supportsPip()) {
                enterPip();
            } else {
                super.onBackPressed();
            }
        } else {
            super.onBackPressed();
        }
    }

    private boolean supportsPip() {
        try {
            return getPackageManager().hasSystemFeature(android.content.pm.PackageManager.FEATURE_PICTURE_IN_PICTURE);
        } catch (Exception e) {
            return false;
        }
    }
}
