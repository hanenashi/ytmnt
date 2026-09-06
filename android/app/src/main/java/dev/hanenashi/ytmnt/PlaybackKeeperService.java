package dev.hanenashi.ytmnt;

import android.app.Notification;
import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.Build;
import android.os.IBinder;

public final class PlaybackKeeperService extends Service {
    private static final String CHANNEL_ID = "ytmnt_playback";
    private static final int NOTIFICATION_ID = 108;
    private static final int PLAY_PAUSE_REQUEST_CODE = 109;
    private static final String ACTION_PLAY = "dev.hanenashi.ytmnt.action.PLAY";
    private static final String ACTION_PAUSE = "dev.hanenashi.ytmnt.action.PAUSE";

    private MediaSession mediaSession;
    private boolean playing = true;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        createMediaSession();
        startForeground(NOTIFICATION_ID, buildNotification());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? null : intent.getAction();
        if (ACTION_PAUSE.equals(action)) pause();
        else if (ACTION_PLAY.equals(action)) play();
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        if (mediaSession != null) {
            mediaSession.setActive(false);
            mediaSession.release();
        }
        super.onDestroy();
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        stopSelf();
        super.onTaskRemoved(rootIntent);
    }

    private void createMediaSession() {
        mediaSession = new MediaSession(this, "YTMNT");
        mediaSession.setCallback(new MediaSession.Callback() {
            @Override
            public void onPlay() {
                play();
            }

            @Override
            public void onPause() {
                pause();
            }
        });
        mediaSession.setActive(true);
        updatePlaybackState();
    }

    private void play() {
        playing = true;
        PlaybackWebViewController.play();
        updatePlaybackState();
        updateNotification();
    }

    private void pause() {
        playing = false;
        PlaybackWebViewController.pause();
        updatePlaybackState();
        updateNotification();
    }

    private void updatePlaybackState() {
        long actions = PlaybackState.ACTION_PLAY
                | PlaybackState.ACTION_PAUSE
                | PlaybackState.ACTION_PLAY_PAUSE;
        mediaSession.setPlaybackState(new PlaybackState.Builder()
                .setActions(actions)
                .setState(
                        playing ? PlaybackState.STATE_PLAYING : PlaybackState.STATE_PAUSED,
                        PlaybackState.PLAYBACK_POSITION_UNKNOWN,
                        playing ? 1f : 0f)
                .build());
    }

    private void updateNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        getSystemService(NotificationManager.class).notify(NOTIFICATION_ID, buildNotification());
    }

    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "YTMNT playback",
                NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("Keeps an active YTMNT WebView session alive");
        getSystemService(NotificationManager.class).createNotificationChannel(channel);
    }

    private Notification buildNotification() {
        Intent openApp = new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent contentIntent = PendingIntent.getActivity(
                this,
                0,
                openApp,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Intent toggleIntent = new Intent(this, PlaybackKeeperService.class)
                .setAction(playing ? ACTION_PAUSE : ACTION_PLAY);
        PendingIntent togglePendingIntent = PendingIntent.getService(
                this,
                PLAY_PAUSE_REQUEST_CODE,
                toggleIntent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        Notification.Builder builder = new Notification.Builder(this, CHANNEL_ID);

        return builder
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentTitle(playing ? "YTMNT is playing" : "YTMNT is paused")
                .setContentText("Tap to return to YouTube Music")
                .setContentIntent(contentIntent)
                .setCategory(Notification.CATEGORY_TRANSPORT)
                .setOngoing(true)
                .addAction(
                        playing ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play,
                        playing ? "Pause" : "Play",
                        togglePendingIntent)
                .setStyle(new Notification.MediaStyle()
                        .setMediaSession(mediaSession.getSessionToken())
                        .setShowActionsInCompactView(0))
                .build();
    }
}
