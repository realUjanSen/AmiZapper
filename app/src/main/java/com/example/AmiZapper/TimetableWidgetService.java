package com.example.amizapper;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.util.Log;
import android.widget.RemoteViews;
import android.widget.RemoteViewsService;

import java.util.ArrayList;
import java.util.List;

/**
 * Service to provide data for the widget's ListView
 */
public class TimetableWidgetService extends RemoteViewsService {
    private static final String TAG = "TimetableWidgetService";

    @Override
    public RemoteViewsFactory onGetViewFactory(Intent intent) {
        Log.d(TAG, "onGetViewFactory called with intent: " + intent);
        return new TimetableRemoteViewsFactory(this.getApplicationContext(), intent);
    }
}

class TimetableRemoteViewsFactory implements RemoteViewsService.RemoteViewsFactory {
    private static final String TAG = "TimetableRemoteViewsFactory";
    private Context context;
    private List<TimetableClass> classes;
    private String dayOfWeek;

    public TimetableRemoteViewsFactory(Context context, Intent intent) {
        this.context = context;
        this.classes = new ArrayList<>();

        // Try to get dayOfWeek from intent extras first
        this.dayOfWeek = intent.getStringExtra("dayOfWeek");

        // If not in extras, try to extract from URI data
        if (this.dayOfWeek == null) {
            Uri data = intent.getData();
            if (data != null) {
                List<String> pathSegments = data.getPathSegments();
                // URI format: content://com.example.amizapper.widget/day/{widgetId}/{dayOfWeek}/{timestamp}
                if (pathSegments.size() >= 3) {
                    this.dayOfWeek = pathSegments.get(2); // dayOfWeek is the 3rd segment
                    Log.d(TAG, "Extracted dayOfWeek from URI: " + this.dayOfWeek);
                }
            }
        }

        Log.d(TAG, "Constructor - dayOfWeek: " + this.dayOfWeek);
    }

    @Override
    public void onCreate() {
        Log.d(TAG, "onCreate called");
        loadData();
    }

    @Override
    public void onDataSetChanged() {
        Log.d(TAG, "onDataSetChanged called");
        loadData();
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy called");
        classes.clear();
    }

    @Override
    public int getCount() {
        Log.d(TAG, "getCount: " + classes.size());
        return classes.size();
    }

    @Override
    public RemoteViews getViewAt(int position) {
        Log.d(TAG, "getViewAt called for position: " + position);

        if (position >= classes.size()) {
            Log.e(TAG, "getViewAt: position " + position + " >= size " + classes.size());
            return null;
        }

        TimetableClass cls = classes.get(position);
        Log.d(TAG, "getViewAt: Creating view for " + cls.getSubjectName());

        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_class_item);

        // Set time
        views.setTextViewText(R.id.widgetStartTime, cls.getStartTime());
        views.setTextViewText(R.id.widgetEndTime, cls.getEndTime());

        // Set subject name
        views.setTextViewText(R.id.widgetSubjectName, cls.getSubjectName());

        // Set course code with credits
        String courseCodeText = cls.getCourseCode();
        // Try to extract credits from subject name or course code
        String creditsText = extractCredits(cls.getSubjectName());
        if (!creditsText.isEmpty()) {
            courseCodeText += " [" + creditsText + " Credits]";
        }
        views.setTextViewText(R.id.widgetCourseCode, courseCodeText);

        // Set teacher and location
        String teacherLocation = cls.getShortTeacher() + " • " + cls.getLocation();
        views.setTextViewText(R.id.widgetTeacherLocation, teacherLocation);

        // Set attendance color bar
        try {
            int color = Color.parseColor(cls.getAttendanceColor());
            views.setInt(R.id.widgetAttendanceBar, "setBackgroundColor", color);
        } catch (Exception e) {
            views.setInt(R.id.widgetAttendanceBar, "setBackgroundColor", Color.parseColor("#3a87ad"));
        }

        // Set click intent to open app
        Intent fillInIntent = new Intent();
        views.setOnClickFillInIntent(R.id.widgetSubjectName, fillInIntent);

        return views;
    }

    @Override
    public RemoteViews getLoadingView() {
        return null;
    }

    @Override
    public int getViewTypeCount() {
        return 1;
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public boolean hasStableIds() {
        return true;
    }

    private void loadData() {
        classes.clear();
        Log.d(TAG, "loadData called - dayOfWeek: " + dayOfWeek);

        if (dayOfWeek != null) {
            List<TimetableClass> allClasses = TimetableStorage.loadTimetable(context);
            Log.d(TAG, "Loaded " + allClasses.size() + " total classes from storage");

            for (TimetableClass cls : allClasses) {
                if (dayOfWeek.equalsIgnoreCase(cls.getDayOfWeek())) {
                    classes.add(cls);
                }
            }
            Log.d(TAG, "Filtered to " + classes.size() + " classes for " + dayOfWeek);
        } else {
            Log.e(TAG, "dayOfWeek is null!");
        }
    }

    private String extractCredits(String subjectName) {
        // Try to find pattern like [4] or (4) at the end
        if (subjectName == null) return "";

        if (subjectName.matches(".*\\[\\s*(\\d+)\\s*\\]\\s*$")) {
            return subjectName.replaceAll(".*\\[\\s*(\\d+)\\s*\\]\\s*$", "$1");
        } else if (subjectName.matches(".*\\(\\s*(\\d+)\\s*\\)\\s*$")) {
            return subjectName.replaceAll(".*\\(\\s*(\\d+)\\s*\\)\\s*$", "$1");
        }

        return "";
    }
}
