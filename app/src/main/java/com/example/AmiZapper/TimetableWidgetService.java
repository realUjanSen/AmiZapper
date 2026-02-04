package com.example.amizapper;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.util.Log;
import android.widget.RemoteViews;
import android.widget.RemoteViewsService;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
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
    private static final int VIEW_TYPE_CLASS = 0;
    private static final int VIEW_TYPE_GAP = 1;

    private Context context;
    private List<Object> items; // Can be TimetableClass or GapIndicator
    private String dayOfWeek;

    // Wrapper class for gap indicators
    private static class GapIndicator {
        // Empty marker class
    }

    public TimetableRemoteViewsFactory(Context context, Intent intent) {
        this.context = context;
        this.items = new ArrayList<>();

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
        Log.d(TAG, "********** onCreate called **********");
        Log.d(TAG, "Context: " + context);
        Log.d(TAG, "DayOfWeek: " + dayOfWeek);
        loadData();
        Log.d(TAG, "********** onCreate finished, items.size = " + items.size() + " **********");
    }

    @Override
    public void onDataSetChanged() {
        Log.d(TAG, "********** onDataSetChanged called **********");
        Log.d(TAG, "DayOfWeek: " + dayOfWeek);
        loadData();
        Log.d(TAG, "********** onDataSetChanged finished, items.size = " + items.size() + " **********");
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "********** onDestroy called **********");
        items.clear();
    }

    @Override
    public int getCount() {
        int count = items.size();
        Log.d(TAG, ">>>>>> getCount returning: " + count + " <<<<<<");
        return count;
    }

    @Override
    public RemoteViews getViewAt(int position) {
        Log.d(TAG, "==================== getViewAt START ====================");
        Log.d(TAG, "getViewAt called for position: " + position);
        Log.d(TAG, "Total items size: " + items.size());

        try {
            if (position >= items.size()) {
                Log.e(TAG, "ERROR: position " + position + " >= size " + items.size());
                return null;
            }

            Object item = items.get(position);

            // Check if this is a gap indicator
            if (item instanceof GapIndicator) {
                Log.d(TAG, "Creating gap indicator view at position " + position);
                RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_gap_indicator);
                return views;
            }

            // Otherwise it's a class card
            TimetableClass cls = (TimetableClass) item;
            Log.d(TAG, "Creating view for: " + cls.getSubjectName());
            Log.d(TAG, "  Course Code: " + cls.getCourseCode());
            Log.d(TAG, "  Time: " + cls.getStartTime() + " - " + cls.getEndTime());
            Log.d(TAG, "  Teacher: " + cls.getShortTeacher());
            Log.d(TAG, "  Location: " + cls.getLocation());

            Log.d(TAG, "Creating RemoteViews with package: " + context.getPackageName());
            RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_class_item);
            Log.d(TAG, "RemoteViews created successfully");

            // Set time
            Log.d(TAG, "Setting start time: " + cls.getStartTime());
            views.setTextViewText(R.id.widgetStartTime, cls.getStartTime());
            Log.d(TAG, "Setting end time: " + cls.getEndTime());
            views.setTextViewText(R.id.widgetEndTime, cls.getEndTime());

            // Set subject name
            Log.d(TAG, "Setting subject name: " + cls.getSubjectName());
            views.setTextViewText(R.id.widgetSubjectName, cls.getSubjectName());

            // Set course code with credits
            String courseCodeText = cls.getCourseCode();
            String creditsText = extractCredits(cls.getSubjectName());
            if (!creditsText.isEmpty()) {
                courseCodeText += " [" + creditsText + " Credits]";
            }
            Log.d(TAG, "Setting course code: " + courseCodeText);
            views.setTextViewText(R.id.widgetCourseCode, courseCodeText);

            // Set teacher and location
            String teacherLocation = cls.getShortTeacher() + " • " + cls.getLocation();
            Log.d(TAG, "Setting teacher/location: " + teacherLocation);
            views.setTextViewText(R.id.widgetTeacherLocation, teacherLocation);

            // Set attendance color bar using setColorFilter (RemoteViews compatible for ImageView)
            try {
                int color = Color.parseColor(cls.getAttendanceColor());
                Log.d(TAG, "Setting attendance color: " + cls.getAttendanceColor());
                // Use setColorFilter with PorterDuff mode for ImageView
                views.setInt(R.id.widgetAttendanceBar, "setColorFilter", color);
            } catch (Exception e) {
                Log.w(TAG, "Failed to parse attendance color, using default blue");
                views.setInt(R.id.widgetAttendanceBar, "setColorFilter", Color.parseColor("#3a87ad"));
            }

            // Show red border if this is the current class
            try {
                boolean isCurrentClass = isCurrentClass(cls.getStartTime(), cls.getEndTime());
                Log.d(TAG, "Current class check: " + isCurrentClass);

                if (isCurrentClass) {
                    // Use background with full red border for current class
                    views.setInt(R.id.widgetCardContainer, "setBackgroundResource", R.drawable.widget_class_item_bg_current);
                } else {
                    // Use normal background
                    views.setInt(R.id.widgetCardContainer, "setBackgroundResource", R.drawable.widget_class_item_bg);
                }
            } catch (Exception e) {
                Log.w(TAG, "Failed to set current class indicator: " + e.getMessage());
                views.setInt(R.id.widgetCardContainer, "setBackgroundResource", R.drawable.widget_class_item_bg);
            }

            // Set click intent to open app
            Intent fillInIntent = new Intent();
            views.setOnClickFillInIntent(R.id.widgetSubjectName, fillInIntent);

            Log.d(TAG, "RemoteViews configured successfully, returning view");
            Log.d(TAG, "==================== getViewAt END (SUCCESS) ====================");
            return views;
        } catch (Exception e) {
            Log.e(TAG, "==================== getViewAt END (EXCEPTION) ====================");
            Log.e(TAG, "CRITICAL ERROR in getViewAt: " + e.getMessage(), e);
            Log.e(TAG, "Exception type: " + e.getClass().getName());
            Log.e(TAG, "Stack trace: ", e);
            return null;
        }
    }

    @Override
    public RemoteViews getLoadingView() {
        Log.w(TAG, "!!!!! getLoadingView called - Widget is showing loading state !!!!!");
        // Return a simple loading view while data is being fetched
        RemoteViews loadingView = new RemoteViews(context.getPackageName(), R.layout.widget_class_item);
        loadingView.setTextViewText(R.id.widgetSubjectName, "Loading...");
        loadingView.setTextViewText(R.id.widgetCourseCode, "");
        loadingView.setTextViewText(R.id.widgetTeacherLocation, "");
        loadingView.setTextViewText(R.id.widgetStartTime, "--:--");
        loadingView.setTextViewText(R.id.widgetEndTime, "--:--");
        Log.w(TAG, "!!!!! Returning loading view !!!!!");
        return loadingView;
    }

    @Override
    public int getViewTypeCount() {
        Log.d(TAG, "getViewTypeCount: returning 2 (class + gap)");
        return 2;
    }

    @Override
    public long getItemId(int position) {
        Log.d(TAG, "getItemId(" + position + "): returning " + position);
        return position;
    }

    @Override
    public boolean hasStableIds() {
        Log.d(TAG, "hasStableIds: returning true");
        return true;
    }

    private boolean isCurrentClass(String startTime, String endTime) {
        // Only show if this is today's widget view and we're within this class period
        if (!isToday()) return false;

        int currentMinutes = getCurrentTimeMinutes();
        int startMinutes = parseTimeToMinutes(startTime);
        int endMinutes = parseTimeToMinutes(endTime);

        return currentMinutes >= startMinutes && currentMinutes <= endMinutes;
    }

    private boolean isToday() {
        java.util.Calendar cal = java.util.Calendar.getInstance();
        int dayOfWeekCal = cal.get(java.util.Calendar.DAY_OF_WEEK);
        String today;
        switch (dayOfWeekCal) {
            case java.util.Calendar.MONDAY: today = "Monday"; break;
            case java.util.Calendar.TUESDAY: today = "Tuesday"; break;
            case java.util.Calendar.WEDNESDAY: today = "Wednesday"; break;
            case java.util.Calendar.THURSDAY: today = "Thursday"; break;
            case java.util.Calendar.FRIDAY: today = "Friday"; break;
            case java.util.Calendar.SATURDAY: today = "Saturday"; break;
            case java.util.Calendar.SUNDAY: today = "Sunday"; break;
            default: today = "";
        }
        return today.equalsIgnoreCase(dayOfWeek);
    }

    private int getCurrentTimeMinutes() {
        java.util.Calendar cal = java.util.Calendar.getInstance();
        return cal.get(java.util.Calendar.HOUR_OF_DAY) * 60 + cal.get(java.util.Calendar.MINUTE);
    }

    private void loadData() {
        items.clear();
        Log.d(TAG, "loadData called - dayOfWeek: " + dayOfWeek);

        try {
            if (dayOfWeek != null) {
                List<TimetableClass> allClasses = TimetableStorage.loadTimetable(context);
                Log.d(TAG, "Loaded " + allClasses.size() + " total classes from storage");

                List<TimetableClass> dayClasses = new ArrayList<>();
                for (TimetableClass cls : allClasses) {
                    if (dayOfWeek.equalsIgnoreCase(cls.getDayOfWeek())) {
                        dayClasses.add(cls);
                    }
                }

                // Sort by start time chronologically
                Collections.sort(dayClasses, new Comparator<TimetableClass>() {
                    @Override
                    public int compare(TimetableClass c1, TimetableClass c2) {
                        return parseTimeToMinutes(c1.getStartTime()) - parseTimeToMinutes(c2.getStartTime());
                    }
                });

                Log.d(TAG, "Filtered to " + dayClasses.size() + " classes for " + dayOfWeek);

                // Build items list with gap indicators
                int currentMinutes = isToday() ? getCurrentTimeMinutes() : -1;

                for (int i = 0; i < dayClasses.size(); i++) {
                    TimetableClass currentClass = dayClasses.get(i);

                    // Check if we should insert a gap indicator before this class
                    if (i > 0 && currentMinutes >= 0) {
                        TimetableClass previousClass = dayClasses.get(i - 1);
                        int prevEndMinutes = parseTimeToMinutes(previousClass.getEndTime());
                        int currentStartMinutes = parseTimeToMinutes(currentClass.getStartTime());

                        // If current time is in the gap between previous and current class
                        if (currentMinutes > prevEndMinutes && currentMinutes < currentStartMinutes) {
                            Log.d(TAG, "Inserting gap indicator between " + previousClass.getSubjectName() + " and " + currentClass.getSubjectName());
                            items.add(new GapIndicator());
                        }
                    }

                    // Add the class
                    items.add(currentClass);
                }

                Log.d(TAG, "Built items list with " + items.size() + " items (classes + gap indicators)");
            } else {
                Log.e(TAG, "dayOfWeek is null!");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error loading data: " + e.getMessage(), e);
        }
    }

    private int parseTimeToMinutes(String time) {
        try {
            if (time == null) return 0;
            String[] parts = time.replace(":", "").split("(?<=\\d{2})(?=\\d{2})");
            if (parts.length < 2) {
                // Try HH:MM format
                parts = time.split(":");
            }
            int hours = Integer.parseInt(parts[0].trim());
            int minutes = Integer.parseInt(parts[1].trim());
            return hours * 60 + minutes;
        } catch (Exception e) {
            return 0;
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
