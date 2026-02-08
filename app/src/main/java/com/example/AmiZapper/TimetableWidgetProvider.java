package com.example.amizapper;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.util.Log;
import android.view.View;
import android.widget.RemoteViews;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
/**
 * Widget provider for timetable homescreen widget
 */
public class TimetableWidgetProvider extends AppWidgetProvider {

    private static final String TAG = "TimetableWidgetProvider";
    private static final String ACTION_PREV_DAY = "com.example.amizapper.PREV_DAY";
    private static final String ACTION_NEXT_DAY = "com.example.amizapper.NEXT_DAY";
    private static final String ACTION_REFRESH = "com.example.amizapper.REFRESH";
    private static final String PREFS_NAME = "TimetableWidgetPrefs";
    private static final String PREF_DAY_OFFSET = "dayOffset_";

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        for (int appWidgetId : appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId);
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);

        String action = intent.getAction();
        if (action == null) return;

        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
        int appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,
                                            AppWidgetManager.INVALID_APPWIDGET_ID);

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return;

        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        int currentDayOffset = prefs.getInt(PREF_DAY_OFFSET + appWidgetId, 0);

        if (ACTION_PREV_DAY.equals(action)) {
            Log.d(TAG, "PREV_DAY action received for widget " + appWidgetId);
            currentDayOffset--;
            // No wrapping - allow navigation to previous weeks
            prefs.edit().putInt(PREF_DAY_OFFSET + appWidgetId, currentDayOffset).apply();
            updateAppWidget(context, appWidgetManager, appWidgetId);
        } else if (ACTION_NEXT_DAY.equals(action)) {
            Log.d(TAG, "NEXT_DAY action received for widget " + appWidgetId);
            currentDayOffset++;
            // No wrapping - allow navigation to future weeks
            prefs.edit().putInt(PREF_DAY_OFFSET + appWidgetId, currentDayOffset).apply();
            updateAppWidget(context, appWidgetManager, appWidgetId);
        } else if (ACTION_REFRESH.equals(action)) {
            Log.d(TAG, "REFRESH action received for widget " + appWidgetId);
            updateAppWidget(context, appWidgetManager, appWidgetId);
        }
    }

    @Override
    public void onDeleted(Context context, int[] appWidgetIds) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        for (int appWidgetId : appWidgetIds) {
            editor.remove(PREF_DAY_OFFSET + appWidgetId);
        }
        editor.apply();
    }

    private void updateAppWidget(Context context, AppWidgetManager appWidgetManager, int appWidgetId) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_timetable);

        // Days array for display (Sunday=0 to Saturday=6 matching Calendar.DAY_OF_WEEK - 1)
        String[] dayNames = {"Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"};

        // Get day offset (days from today - can be negative for past, positive for future)
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        int dayOffset = prefs.getInt(PREF_DAY_OFFSET + appWidgetId, 0); // Default to today (0)

        // Calculate target date by adding dayOffset to today
        Calendar targetDate = Calendar.getInstance();
        targetDate.add(Calendar.DAY_OF_MONTH, dayOffset);

        int dayOfMonth = targetDate.get(Calendar.DAY_OF_MONTH);
        int dayOfWeekIndex = targetDate.get(Calendar.DAY_OF_WEEK) - 1; // Sunday=0, Saturday=6
        String dayOfWeek = dayNames[dayOfWeekIndex];

        // Format the target date as yyyy-MM-dd for proper filtering
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        String targetDateStr = dateFormat.format(targetDate.getTime());

        // Set header text with day of month
        String headerText;
        if (dayOffset == 0) {
            headerText = "TODAY";
        } else {
            String suffix = getOrdinalSuffix(dayOfMonth);
            headerText = dayOfWeek.toUpperCase() + " " + dayOfMonth + suffix;
        }
        views.setTextViewText(R.id.widgetDayText, headerText);

        // Load timetable data by ACTUAL DATE (not just day name)
        List<TimetableClass> classes = loadTimetableForDate(context, targetDateStr, dayOfWeek);

        // Set up the list adapter - pass actual date for proper filtering
        boolean isFutureDay = dayOffset > 0;

        Intent serviceIntent = new Intent(context, TimetableWidgetService.class);
        serviceIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
        serviceIntent.putExtra("dayOfWeek", dayOfWeek);
        serviceIntent.putExtra("dateString", targetDateStr); // Actual date yyyy-MM-dd
        serviceIntent.putExtra("isFutureDay", isFutureDay);
        serviceIntent.setData(Uri.parse("content://com.example.amizapper.widget/day/" +
            appWidgetId + "/" + dayOfWeek + "/" + targetDateStr + "/" + isFutureDay + "/" + System.currentTimeMillis()));
        views.setRemoteAdapter(R.id.widgetClassList, serviceIntent);

        // Set empty view
        views.setEmptyView(R.id.widgetClassList, R.id.widgetEmptyText);

        if (classes.isEmpty()) {
            views.setViewVisibility(R.id.widgetClassList, View.GONE);
            views.setViewVisibility(R.id.widgetEmptyText, View.VISIBLE);
            if (isFutureDay) {
                views.setTextViewText(R.id.widgetEmptyText, "Open app to load schedule");
            } else {
                views.setTextViewText(R.id.widgetEmptyText, "No classes scheduled");
            }
        } else {
            views.setViewVisibility(R.id.widgetClassList, View.VISIBLE);
            views.setViewVisibility(R.id.widgetEmptyText, View.GONE);
        }

        // Navigation buttons
        Intent prevIntent = new Intent(context, TimetableWidgetProvider.class);
        prevIntent.setAction(ACTION_PREV_DAY);
        prevIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
        PendingIntent prevPendingIntent = PendingIntent.getBroadcast(context, appWidgetId * 2,
            prevIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.widgetPrevDay, prevPendingIntent);

        Intent nextIntent = new Intent(context, TimetableWidgetProvider.class);
        nextIntent.setAction(ACTION_NEXT_DAY);
        nextIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
        PendingIntent nextPendingIntent = PendingIntent.getBroadcast(context, appWidgetId * 2 + 1,
            nextIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.widgetNextDay, nextPendingIntent);

        Intent refreshIntent = new Intent(context, TimetableWidgetProvider.class);
        refreshIntent.setAction(ACTION_REFRESH);
        refreshIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
        PendingIntent refreshPendingIntent = PendingIntent.getBroadcast(context, appWidgetId * 3,
            refreshIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.widgetRefresh, refreshPendingIntent);

        Intent launchIntent = new Intent(context, MainActivity.class);
        PendingIntent launchPendingIntent = PendingIntent.getActivity(context, appWidgetId,
            launchIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.widgetDayText, launchPendingIntent);

        appWidgetManager.updateAppWidget(appWidgetId, views);
        appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.widgetClassList);
    }

    private String getOrdinalSuffix(int day) {
        if (day >= 11 && day <= 13) {
            return "TH";
        }
        switch (day % 10) {
            case 1: return "ST";
            case 2: return "ND";
            case 3: return "RD";
            default: return "TH";
        }
    }

    private List<TimetableClass> loadTimetableForDate(Context context, String targetDate, String dayOfWeek) {
        List<TimetableClass> allClasses = TimetableStorage.loadTimetable(context);
        List<TimetableClass> dateClasses = new ArrayList<>();

        for (TimetableClass cls : allClasses) {
            // First try to match by actual date (more accurate)
            if (cls.getDate() != null && cls.getDate().equals(targetDate)) {
                dateClasses.add(cls);
            }
        }

        // If we found classes by date, return them
        if (!dateClasses.isEmpty()) {
            return dateClasses;
        }

        // Fallback: if no date matches (old cached data), filter by day name
        // But this could show wrong data - better to show nothing for future days
        // that don't have data yet
        for (TimetableClass cls : allClasses) {
            if (dayOfWeek.equalsIgnoreCase(cls.getDayOfWeek())) {
                // Only include if date matches OR if date is null (old data for current week)
                if (cls.getDate() == null) {
                    dateClasses.add(cls);
                }
            }
        }

        return dateClasses;
    }
}
