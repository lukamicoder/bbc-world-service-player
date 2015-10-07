package com.lukamicoder.bbcworldserviceplayer;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.app.NotificationCompat;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.TextView;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity {
    private MediaPlayer mediaPlayer;
    private TextView textViewInfo;
    private Handler durationHandler = new Handler();
    private Handler initHandler = new Handler();
    private Handler noInternetHandler = new Handler();
    private Menu menu;
    private NotificationManager nmanager;
    private NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(this);
    private Integer dotCount = -4;
    private Integer noInternetInterval = 5000;
    private Integer initProgressInterval = 500;
    private Integer initProgressCount = 0;
    private Integer maxInitProgressCount = 60; //30 sec
    private Integer updateDurationInterval = 500;
    private static final Integer NID = 1;
    private static final String EXIT_INTENT = "ExitIntent";
    private static final String PLAYCONTROL_INTENT = "PlayControlIntent";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        textViewInfo = (TextView) findViewById(R.id.textViewInfo);

        mediaPlayer = new MediaPlayer();
        mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
        mediaPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer mp) {
                init();
            }
        });

        if (!isInternetAvailable()) {
            textViewInfo.setText(R.string.no_internet_msg);
            noInternetHandler.postDelayed(checkInternet, noInternetInterval);
        } else {
            preparePlayer();
        }

        nmanager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        createNotification();
    }

    private void preparePlayer() {
        noInternetHandler.removeCallbacks(checkInternet);

        try {
            mediaPlayer.setDataSource(getString(R.string.url));
            mediaPlayer.prepareAsync();
            textViewInfo.setText(R.string.init);
            initHandler.postDelayed(updateInitProgress, initProgressInterval);
        } catch (IllegalArgumentException | SecurityException | IllegalStateException | IOException e) {
            textViewInfo.setText(R.string.error_msg);
        }
    }

    private void init() {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main_menu, menu);
        textViewInfo.setText(getString(R.string.start_pos));

        Intent playControlIntent = new Intent(PLAYCONTROL_INTENT);
        PendingIntent actionPendingIntent = PendingIntent.getBroadcast(this, 0, playControlIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        Intent exitIntent = new Intent(EXIT_INTENT);
        PendingIntent exitPendingIntent = PendingIntent.getBroadcast(this, 0, exitIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        mBuilder.addAction(android.R.drawable.ic_media_play, getString(R.string.play), actionPendingIntent);
        mBuilder.addAction(android.R.drawable.ic_menu_close_clear_cancel, getString(R.string.exit), exitPendingIntent);
        nmanager.notify(NID, mBuilder.build());

        IntentFilter filter = new IntentFilter(EXIT_INTENT);
        filter.addAction(PLAYCONTROL_INTENT);
        registerReceiver(broadcastReceiver, filter);

        initHandler.removeCallbacks(updateInitProgress);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        this.menu = menu;

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menuItemPlayControl:
                controlPlay();

                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onBackPressed() {
        unload();
    }

    private void createNotification() {
        Intent mainIntent = new Intent(this, MainActivity.class);
        mainIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent mainPendingIntent = PendingIntent.getActivity(this, 0, mainIntent, 0);

        boolean isLollipop = (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP);
        Notification notification = mBuilder
                .setContentTitle(getString(R.string.app_short_name))
                .setContentText(getString(R.string.start_pos))
                .setOngoing(true)
                .setSmallIcon(isLollipop ? R.mipmap.ic_launcher_white : R.mipmap.ic_launcher)
                .setContentIntent(mainPendingIntent).build();

        nmanager.notify(NID, notification);
    }

    private final BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            switch (intent.getAction()) {
                case PLAYCONTROL_INTENT:
                    controlPlay();
                    break;
                case EXIT_INTENT:
                    unload();
                    break;
            }
        }
    };

    private void unload() {
        mediaPlayer.stop();
        durationHandler.removeCallbacks(updateDuration);
        initHandler.removeCallbacks(updateInitProgress);
        noInternetHandler.removeCallbacks(checkInternet);
        try {
            unregisterReceiver(broadcastReceiver);
        } catch (IllegalArgumentException ignored) {
        }

        nmanager.cancelAll();
        finish();
    }

    private void controlPlay() {
        MenuItem menuItemPlayControl = menu.findItem(R.id.menuItemPlayControl);

        Boolean isRunning;
        if (menuItemPlayControl.getTitle() == getString(R.string.pause)) {
            mediaPlayer.pause();
            isRunning = false;
            menuItemPlayControl.setIcon(android.R.drawable.ic_media_play);
            menuItemPlayControl.setTitle(getString(R.string.play));
        } else {
            menuItemPlayControl.setIcon(android.R.drawable.ic_media_pause);
            menuItemPlayControl.setTitle(getString(R.string.pause));
            durationHandler.postDelayed(updateDuration, updateDurationInterval);
            mediaPlayer.start();
            isRunning = true;
        }

        mBuilder.mActions.get(0).icon = isRunning ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play;
        mBuilder.mActions.get(0).title = isRunning ? getString(R.string.pause) : getString(R.string.play);
        nmanager.notify(NID, mBuilder.build());
    }

    private Runnable updateDuration = new Runnable() {
        public void run() {
            double timeElapsed = mediaPlayer.getCurrentPosition();
            String duration = String.format("%02d:%02d", TimeUnit.MILLISECONDS.toMinutes((long) timeElapsed),
                    TimeUnit.MILLISECONDS.toSeconds((long) timeElapsed) - TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes((long) timeElapsed)));
            textViewInfo.setText(duration);

            nmanager.notify(NID, mBuilder.setContentText(duration).build());

            durationHandler.postDelayed(this, updateDurationInterval);
        }
    };

    private Runnable updateInitProgress = new Runnable() {
        public void run() {
            if (initProgressCount++ >= maxInitProgressCount) {
                textViewInfo.setText(R.string.unable_to_connect);
                return;
            }

            String text = getText(R.string.init).toString();

            if (dotCount++ == 0) {
                dotCount = -3;
            }

            String padding = String.format("%" + (Math.abs(dotCount) + 2) + "s", " ");
            text = text.substring(0, text.length() + dotCount) + padding;

            textViewInfo.setText(text);

            initHandler.postDelayed(this, initProgressInterval);
        }
    };

    private Runnable checkInternet = new Runnable() {
        public void run() {
            if (isInternetAvailable()) {
                preparePlayer();
                return;
            }

            noInternetHandler.postDelayed(this, noInternetInterval);
        }
    };

    private Boolean isInternetAvailable() {
        ConnectivityManager cm = (ConnectivityManager) this.getSystemService(Context.CONNECTIVITY_SERVICE);

        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
        return null != activeNetwork;
    }
}