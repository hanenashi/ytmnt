package dev.hanenashi.ytmnt.gecko;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.MediaMetadata;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.Build;
import android.os.IBinder;

public final class PlaybackKeeperService extends Service {
    static final String ACTION_MEDIA_ACTIVE = "dev.hanenashi.ytmnt.gecko.action.MEDIA_ACTIVE";
    static final String ACTION_MEDIA_INACTIVE = "dev.hanenashi.ytmnt.gecko.action.MEDIA_INACTIVE";
    static final String ACTION_STATE_PLAYING = "dev.hanenashi.ytmnt.gecko.action.STATE_PLAYING";
    static final String ACTION_STATE_PAUSED = "dev.hanenashi.ytmnt.gecko.action.STATE_PAUSED";
    static final String ACTION_METADATA = "dev.hanenashi.ytmnt.gecko.action.METADATA";
    static final String EXTRA_TITLE = "title";
    static final String EXTRA_ARTIST = "artist";
    static final String EXTRA_ALBUM = "album";

    private static final String CHANNEL_ID = "ytmnt_gecko_playback";
    private static final int NOTIFICATION_ID = 208;
    private static final int TOGGLE_REQUEST_CODE = 209;
    private static final int NEXT_REQUEST_CODE = 210;
    private static final int PREVIOUS_REQUEST_CODE = 211;
    private static final String ACTION_PLAY = "dev.hanenashi.ytmnt.gecko.action.PLAY";
    private static final String ACTION_PAUSE = "dev.hanenashi.ytmnt.gecko.action.PAUSE";
    private static final String ACTION_NEXT = "dev.hanenashi.ytmnt.gecko.action.NEXT";
    private static final String ACTION_PREVIOUS = "dev.hanenashi.ytmnt.gecko.action.PREVIOUS";

    private MediaSession mediaSession;
    private boolean playing;
    private String title = "YouTube Music";
    private String artist = "YTMNT";
    private String album = "";

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
        if (ACTION_PLAY.equals(action)) {
            playing = true;
            PlaybackGeckoController.play();
        } else if (ACTION_PAUSE.equals(action)) {
            playing = false;
            PlaybackGeckoController.pause();
        } else if (ACTION_NEXT.equals(action)) {
            PlaybackGeckoController.next();
        } else if (ACTION_PREVIOUS.equals(action)) {
            PlaybackGeckoController.previous();
        } else if (ACTION_STATE_PLAYING.equals(action)) {
            playing = true;
        } else if (ACTION_STATE_PAUSED.equals(action)) {
            playing = false;
        } else if (ACTION_METADATA.equals(action)) {
            title = clean(intent.getStringExtra(EXTRA_TITLE), "YouTube Music");
            artist = clean(intent.getStringExtra(EXTRA_ARTIST), "YTMNT");
            album = clean(intent.getStringExtra(EXTRA_ALBUM), "");
            updateMetadata();
        } else if (ACTION_MEDIA_INACTIVE.equals(action)) {
            stopSelf();
            return START_NOT_STICKY;
        }

        updatePlaybackState();
        updateNotification();
        return START_NOT_STICKY;
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

    private void createMediaSession() {
        mediaSession = new MediaSession(this, "YTMNT Gecko");
        mediaSession.setCallback(new MediaSession.Callback() {
            @Override
            public void onPlay() {
                dispatch(ACTION_PLAY);
            }

            @Override
            public void onPause() {
                dispatch(ACTION_PAUSE);
            }

            @Override
            public void onSkipToNext() {
                dispatch(ACTION_NEXT);
            }

            @Override
            public void onSkipToPrevious() {
                dispatch(ACTION_PREVIOUS);
            }
        });
        mediaSession.setActive(true);
        updateMetadata();
        updatePlaybackState();
    }

    private void dispatch(String action) {
        onStartCommand(new Intent(this, PlaybackKeeperService.class).setAction(action), 0, 0);
    }

    private void updateMetadata() {
        MediaMetadata.Builder builder = new MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, title)
                .putString(MediaMetadata.METADATA_KEY_ARTIST, artist);
        if (!album.isEmpty()) builder.putString(MediaMetadata.METADATA_KEY_ALBUM, album);
        mediaSession.setMetadata(builder.build());
    }

    private void updatePlaybackState() {
        long actions = PlaybackState.ACTION_PLAY
                | PlaybackState.ACTION_PAUSE
                | PlaybackState.ACTION_PLAY_PAUSE
                | PlaybackState.ACTION_SKIP_TO_NEXT
                | PlaybackState.ACTION_SKIP_TO_PREVIOUS;
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
        channel.setDescription("Keeps YouTube Music playing in the background");
        getSystemService(NotificationManager.class).createNotificationChannel(channel);
    }

    private Notification buildNotification() {
        PendingIntent contentIntent = PendingIntent.getActivity(
                this,
                0,
                new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        PendingIntent previousIntent = servicePendingIntent(PREVIOUS_REQUEST_CODE, ACTION_PREVIOUS);
        PendingIntent toggleIntent = servicePendingIntent(
                TOGGLE_REQUEST_CODE,
                playing ? ACTION_PAUSE : ACTION_PLAY);
        PendingIntent nextIntent = servicePendingIntent(NEXT_REQUEST_CODE, ACTION_NEXT);

        return new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentTitle(title)
                .setContentText(artist)
                .setContentIntent(contentIntent)
                .setCategory(Notification.CATEGORY_TRANSPORT)
                .setOngoing(playing)
                .addAction(android.R.drawable.ic_media_previous, "Previous", previousIntent)
                .addAction(
                        playing ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play,
                        playing ? "Pause" : "Play",
                        toggleIntent)
                .addAction(android.R.drawable.ic_media_next, "Next", nextIntent)
                .setStyle(new Notification.MediaStyle()
                        .setMediaSession(mediaSession.getSessionToken())
                        .setShowActionsInCompactView(0, 1, 2))
                .build();
    }

    private PendingIntent servicePendingIntent(int requestCode, String action) {
        return PendingIntent.getService(
                this,
                requestCode,
                new Intent(this, PlaybackKeeperService.class).setAction(action),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private static String clean(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
