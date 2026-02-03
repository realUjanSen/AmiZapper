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
        int currentOffset = prefs.getInt(PREF_DAY_OFFSET + appWidgetId, 0);

        if (ACTION_PREV_DAY.equals(action)) {
            Log.d(TAG, "PREV_DAY action received for widget " + appWidgetId);
            currentOffset--;
            prefs.edit().putInt(PREF_DAY_OFFSET + appWidgetId, currentOffset).apply();
            updateAppWidget(context, appWidgetManager, appWidgetId);
        } else if (ACTION_NEXT_DAY.equals(action)) {
            Log.d(TAG, "NEXT_DAY action received for widget " + appWidgetId);
            currentOffset++;
            prefs.edit().putInt(PREF_DAY_OFFSET + appWidgetId, currentOffset).apply();
            updateAppWidget(context, appWidgetManager, appWidgetId);
        } else if (ACTION_REFRESH.equals(action)) {
            Log.d(TAG, "REFRESH action received for widget " + appWidgetId);
            Log.d(TAG, "Refreshing widget data from SharedPreferences...");

            // Log the data status
            List<TimetableClass> allClasses = TimetableStorage.loadTimetable(context);
            Log.d(TAG, "REFRESH: Loaded " + allClasses.size() + " total classes from SharedPreferences");

            if (allClasses.isEmpty()) {
                Log.w(TAG, "REFRESH: No timetable data in SharedPreferences! Widget will show 'Loading...' or empty state.");
                Log.w(TAG, "REFRESH: User needs to open app and load timetable first.");
            } else {
                Log.d(TAG, "REFRESH: Data available. First class: " + allClasses.get(0).getSubjectName() + " on " + allClasses.get(0).getDayOfWeek());
            }

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

        // Get day offset
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        int dayOffset = prefs.getInt(PREF_DAY_OFFSET + appWidgetId, 0);

        // Calculate target day
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DAY_OF_MONTH, dayOffset);

        String dayOfWeek = new SimpleDateFormat("EEEE", Locale.US).format(calendar.getTime());
        int dayOfMonth = calendar.get(Calendar.DAY_OF_MONTH);

        // Set header text
        String headerText;
        if (dayOffset == 0) {
            headerText = "TODAY";
        } else {
            String suffix = getOrdinalSuffix(dayOfMonth);
            headerText = dayOfWeek.toUpperCase() + " " + dayOfMonth + suffix;
        }
        views.setTextViewText(R.id.widgetDayText, headerText);

        // Load timetable data
        List<TimetableClass> classes = loadTimetableForDay(context, dayOfWeek);

        // Set up the list adapter with unique URI to prevent caching
        Intent serviceIntent = new Intent(context, TimetableWidgetService.class);
        serviceIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
        serviceIntent.putExtra("dayOfWeek", dayOfWeek);
        // Use unique URI to force Android to not cache the intent
        serviceIntent.setData(Uri.parse("content://com.example.amizapper.widget/day/" +
            appWidgetId + "/" + dayOfWeek + "/" + System.currentTimeMillis()));
        views.setRemoteAdapter(R.id.widgetClassList, serviceIntent);

        // Set empty view for the list
        views.setEmptyView(R.id.widgetClassList, R.id.widgetEmptyText);

        // Show/hide empty state
        if (classes.isEmpty()) {
            views.setViewVisibility(R.id.widgetClassList, View.GONE);
            views.setViewVisibility(R.id.widgetEmptyText, View.VISIBLE);
        } else {
            views.setViewVisibility(R.id.widgetClassList, View.VISIBLE);
            views.setViewVisibility(R.id.widgetEmptyText, View.GONE);
        }

        // Set up navigation button intents
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

        // Refresh button intent
        Intent refreshIntent = new Intent(context, TimetableWidgetProvider.class);
        refreshIntent.setAction(ACTION_REFRESH);
        refreshIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
        PendingIntent refreshPendingIntent = PendingIntent.getBroadcast(context, appWidgetId * 3,
            refreshIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.widgetRefresh, refreshPendingIntent);

        // Click on widget opens app
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

    private List<TimetableClass> loadTimetableForDay(Context context, String dayOfWeek) {
        List<TimetableClass> allClasses = TimetableStorage.loadTimetable(context);
        List<TimetableClass> dayClasses = new ArrayList<>();

        for (TimetableClass cls : allClasses) {
            if (dayOfWeek.equalsIgnoreCase(cls.getDayOfWeek())) {
                dayClasses.add(cls);
            }
        }

        return dayClasses;
    }
}
