package com.example.amizapper;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.net.Uri;
import android.os.Build;

public class RoomChangeNotificationReceiver extends BroadcastReceiver {
    static final String EXTRA_DATE = "date";
    static final String EXTRA_SUBJECT = "subject";
    static final String EXTRA_ROOM = "room";
    static final String EXTRA_PREVIOUS_ROOM = "previousRoom";
    private static final String CHANNEL_ID = "room_changes";

    @Override
    public void onReceive(Context context, Intent intent) {
        NotificationManager notificationManager = context.getSystemService(NotificationManager.class);
        Uri sound = Uri.parse("android.resource://" + context.getPackageName() + "/" + R.raw.universfield_ring);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Room changes",
                    NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription("Alerts when your next class is in a different room");
            channel.enableVibration(true);
            channel.setSound(sound, new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build());
            notificationManager.createNotificationChannel(channel);
        }

        Intent launchIntent = new Intent(context, MainActivity.class);
        PendingIntent launchPendingIntent = PendingIntent.getActivity(context, 0, launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        String subject = intent.getStringExtra(EXTRA_SUBJECT);
        String room = intent.getStringExtra(EXTRA_ROOM);
        String previousRoom = intent.getStringExtra(EXTRA_PREVIOUS_ROOM);
        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(context, CHANNEL_ID)
                : new Notification.Builder(context).setSound(sound);
        builder.setSmallIcon(R.drawable.app_icon)
                .setContentTitle("Room change for " + subject)
                .setContentText("Move from " + previousRoom + " to " + room)
                .setStyle(new Notification.BigTextStyle().bigText("Your next class is in " + room
                        + ". You are currently scheduled in " + previousRoom + "."))
                .setPriority(Notification.PRIORITY_HIGH)
                .setCategory(Notification.CATEGORY_EVENT)
                .setAutoCancel(true)
                .setContentIntent(launchPendingIntent);
        notificationManager.notify((intent.getStringExtra(EXTRA_DATE) + subject).hashCode(), builder.build());
    }
}