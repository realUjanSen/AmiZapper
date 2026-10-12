package com.example.amizapper;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class RoomChangeNotificationManager {
    private static final String PREFS_NAME = "RoomChangeNotifications";
    private static final String KEY_ALARM_IDS = "alarmIds";
    private static final String DATE_FORMAT = "yyyy-MM-dd HH:mm";
    private static final long NOTIFICATION_LEAD_MILLIS = 5 * 60 * 1000L;

    private RoomChangeNotificationManager() {}

    public static void schedule(Context context) {
        Context appContext = context.getApplicationContext();
        AlarmManager alarmManager = (AlarmManager) appContext.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) return;

        Set<String> oldIds = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getStringSet(KEY_ALARM_IDS, Collections.emptySet());
        for (String oldId : oldIds) {
            cancelAlarm(appContext, alarmManager, Integer.parseInt(oldId));
        }

        Set<String> newIds = new HashSet<>();
        List<TimetableClass> classes = TimetableStorage.loadTimetable(appContext);
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Calendar.getInstance().getTime());

        for (String date : uniqueDates(classes)) {
            if (date.compareTo(today) < 0) continue;

            List<TimetableClass> dayClasses = new ArrayList<>();
            for (TimetableClass cls : classes) {
                if (date.equals(cls.getDate()) && !cls.isCancelled()) dayClasses.add(cls);
            }
            dayClasses.sort(Comparator.comparingInt(cls -> parseMinutes(cls.getStartTime())));

            for (int i = 1; i < dayClasses.size(); i++) {
                TimetableClass previous = dayClasses.get(i - 1);
                TimetableClass next = dayClasses.get(i);
                String previousRoom = normalizeRoom(previous.getLocation());
                String nextRoom = normalizeRoom(next.getLocation());
                if (previousRoom.isEmpty() || nextRoom.isEmpty() || previousRoom.equals(nextRoom)) continue;

                long nextClassAt = parseDateTime(date, next.getStartTime());
                long now = System.currentTimeMillis();
                if (nextClassAt <= now) continue;

                long triggerAt = nextClassAt - NOTIFICATION_LEAD_MILLIS;
                if (triggerAt <= now) {
                    triggerAt = now + 1000L;
                }

                int requestCode = (date + "|" + next.getStartTime() + "|" + next.getCourseCode()).hashCode();
                Intent intent = new Intent(appContext, RoomChangeNotificationReceiver.class)
                        .putExtra(RoomChangeNotificationReceiver.EXTRA_DATE, date)
                        .putExtra(RoomChangeNotificationReceiver.EXTRA_SUBJECT, next.getSubjectName())
                        .putExtra(RoomChangeNotificationReceiver.EXTRA_ROOM, next.getLocation())
                        .putExtra(RoomChangeNotificationReceiver.EXTRA_PREVIOUS_ROOM, previous.getLocation());
                PendingIntent pendingIntent = PendingIntent.getBroadcast(appContext, requestCode, intent,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent);
                } else {
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent);
                }
                newIds.add(String.valueOf(requestCode));
            }
        }

        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putStringSet(KEY_ALARM_IDS, newIds).apply();
    }

    private static Set<String> uniqueDates(List<TimetableClass> classes) {
        Set<String> dates = new HashSet<>();
        for (TimetableClass cls : classes) {
            if (cls.getDate() != null && !cls.getDate().isEmpty()) dates.add(cls.getDate());
        }
        return dates;
    }

    private static void cancelAlarm(Context context, AlarmManager alarmManager, int requestCode) {
        PendingIntent pendingIntent = PendingIntent.getBroadcast(context, requestCode,
                new Intent(context, RoomChangeNotificationReceiver.class),
                PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent);
            pendingIntent.cancel();
        }
    }

    private static int parseMinutes(String time) {
        try {
            String[] parts = time.split(":");
            return Integer.parseInt(parts[0]) * 60 + Integer.parseInt(parts[1]);
        } catch (Exception e) {
            return Integer.MAX_VALUE;
        }
    }

    private static long parseDateTime(String date, String time) {
        try {
            return new SimpleDateFormat(DATE_FORMAT, Locale.US).parse(date + " " + time).getTime();
        } catch (ParseException e) {
            return 0L;
        }
    }

    private static String normalizeRoom(String room) {
        return room == null ? "" : room.trim().replaceAll("\\s+", " ").toLowerCase(Locale.US);
    }
}