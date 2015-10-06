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
import android.view.Gravity;
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
    private Handler progressHandler = new Handler();
    private Menu menu;
    private NotificationManager nmanager;
    private NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(this);
    private Integer dotCount = 0;
    private Boolean isEnabled = false;
    private final String ExitIntentString = "ExitIntent";
    private final static String PlayControlIntentString = "PlayControlIntent";

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
                isEnabled = true;
                MenuInflater inflater = getMenuInflater();
                inflater.inflate(R.menu.main_menu, menu);
                textViewInfo.setGravity(Gravity.CENTER_VERTICAL | Gravity.CENTER_HORIZONTAL);
                textViewInfo.setText(getString(R.string.start_pos));
                progressHandler.removeCallbacks(updateProgress);
            }
        });

        if (!isInternetAvailable()) {
            textViewInfo.setText(R.string.no_internet_msg);
        } else {
            try {
                mediaPlayer.setDataSource(getString(R.string.url));
                mediaPlayer.prepareAsync();
                textViewInfo.setText(R.string.init);
                progressHandler.postDelayed(updateProgress, 500);
            } catch (IllegalArgumentException | SecurityException | IllegalStateException | IOException e) {
                textViewInfo.setText(R.string.error_msg);
            }
        }

        nmanager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        createNotification();

        registerReceiver(exitIntentReceiver, new IntentFilter(ExitIntentString));
        registerReceiver(playControlIntentReceiver, new IntentFilter(PlayControlIntentString));
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        this.menu = menu;

        if (isEnabled) {
            MenuInflater inflater = getMenuInflater();
            inflater.inflate(R.menu.main_menu, menu);
        }
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

        Intent playControlIntent = new Intent(PlayControlIntentString);
        PendingIntent actionPendingIntent = PendingIntent.getBroadcast(this, 0, playControlIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        Intent exitIntent = new Intent(ExitIntentString);
        PendingIntent exitPendingIntent = PendingIntent.getBroadcast(this, 0, exitIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        boolean isLollipop = (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP);

        Notification notification = mBuilder
                .setContentTitle(getString(R.string.app_short_name))
                .setContentText(getString(R.string.start_pos))
                .setOngoing(true)
                .setSmallIcon(isLollipop ? R.mipmap.ic_launcher_white : R.mipmap.ic_launcher)
                .setContentIntent(mainPendingIntent)
                .addAction(android.R.drawable.ic_media_play, getString(R.string.play), actionPendingIntent)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, getString(R.string.exit), exitPendingIntent).build();

        nmanager.notify(1, notification);
    }

    private final BroadcastReceiver exitIntentReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            unload();
        }
    };

    private final BroadcastReceiver playControlIntentReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            controlPlay();
        }
    };

    private void unload() {
        mediaPlayer.stop();
        durationHandler.removeCallbacks(updateDuration);
        progressHandler.removeCallbacks(updateProgress);
        unregisterReceiver(exitIntentReceiver);
        unregisterReceiver(playControlIntentReceiver);
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
            durationHandler.postDelayed(updateDuration, 500);
            mediaPlayer.start();
            isRunning = true;
        }

        mBuilder.mActions.get(0).icon = isRunning ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play;
        mBuilder.mActions.get(0).title = isRunning ? getString(R.string.pause) : getString(R.string.play);
        nmanager.notify(1, mBuilder.build());
    }

    private Runnable updateDuration = new Runnable() {
        public void run() {
            double timeElapsed = mediaPlayer.getCurrentPosition();
            String duration = String.format("%02d:%02d", TimeUnit.MILLISECONDS.toMinutes((long) timeElapsed),
                    TimeUnit.MILLISECONDS.toSeconds((long) timeElapsed) - TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes((long) timeElapsed)));
            textViewInfo.setText(duration);

            nmanager.notify(1, mBuilder.setContentText(duration).build());

            durationHandler.postDelayed(this, 500);
        }
    };

    private Runnable updateProgress = new Runnable() {
        public void run() {
            if (dotCount++ < 3) {
                textViewInfo.setText(textViewInfo.getText() + ".");
            } else {
                dotCount = 0;
                textViewInfo.setText(R.string.init);
            }
            progressHandler.postDelayed(this, 500);
        }
    };

    private Boolean isInternetAvailable() {
        ConnectivityManager cm = (ConnectivityManager) this.getSystemService(Context.CONNECTIVITY_SERVICE);

        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
        return null != activeNetwork;

    }
}