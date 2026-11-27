package com.example.amizapper;

import android.appwidget.AppWidgetManager;
import android.Manifest;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Build;
import android.provider.Settings;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    private Toolbar toolbar;
    private TabLayout dayTabs;
    private ViewPager2 viewPager;
    private LinearLayout loadingState;
    private ImageButton prevWeekBtn;
    private ImageButton nextWeekBtn;
    private TextView weekRangeText;

    private CookieManager cookieManager;
    private TimetableFetcher timetableFetcher;
    private AttendanceFetcher attendanceFetcher;
    private DayPagerAdapter pagerAdapter;

    private Map<String, List<TimetableClass>> timetableByDay;
    private final String[] fullDays = {"Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"};

    // Week navigation
    private int weekOffset = 0;
    private Calendar displayedWeekStart;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        cookieManager = new CookieManager(this);
        timetableFetcher = new TimetableFetcher(cookieManager);
        attendanceFetcher = new AttendanceFetcher(cookieManager);
        ExamFetcher examFetcher = new ExamFetcher(cookieManager);

        // Clean up old cached data without date info (legacy format)
        TimetableStorage.cleanupOldData(this);

        // Only require login when there is no session AND no saved timetable.
        // Cached data lets the user see their schedule without logging in.
        List<TimetableClass> cached = TimetableStorage.loadTimetable(this);
        boolean hasCache = cached != null && !cached.isEmpty();
        if (!cookieManager.hasCookies() && !hasCache) {
            goToLoginActivity();
            return;
        }

        initViews();
        setupToolbar();
        setupViewPager();
        requestNotificationPermission();
        requestExactAlarmPermissionIfNeeded();

        // Fetch attendance immediately in background
        fetchAttendanceInBackground();

        // Preload exams so the Exam screen opens with current cached data.
        examFetcher.fetch(new ExamFetcher.FetchCallback() {
            @Override
            public void onSuccess(List<ExamData> exams) {
                ExamStorage.save(MainActivity.this, exams);
            }

            @Override
            public void onError(String error) {
                android.util.Log.e("MainActivity", "Exam fetch FAILED: " + error);
                // Keep the session and cached data; login is only shown on explicit action.
            }
        });

        // Try to load cached timetable first
        loadCachedTimetable();
    }

    private void loadCachedTimetable() {
        List<TimetableClass> cachedClasses = TimetableStorage.loadTimetable(this);

        if (cachedClasses != null && !cachedClasses.isEmpty()) {
            // Use cached data
            processTimetable(cachedClasses);
            pagerAdapter.setTimetableData(timetableByDay);
            RoomChangeNotificationManager.schedule(this);

            // Still fetch in background to get updates
            fetchTimetableInBackground();
        } else {
            // No cached data, fetch from network
            fetchTimetable();
        }
    }

    private void fetchTimetableInBackground() {
        // Fetch silently without showing loading indicator
        timetableFetcher.fetchTimetable(new TimetableFetcher.FetchCallback() {
            @Override
            public void onSuccess(List<TimetableClass> classes) {
                runOnUiThread(() -> {
                    processTimetable(classes);
                    pagerAdapter.setTimetableData(timetableByDay);

                    // Merge timetable data (preserves data from other weeks)
                    TimetableStorage.mergeTimetable(MainActivity.this, classes);
                    RoomChangeNotificationManager.schedule(MainActivity.this);

                    // Notify widgets to update
                    updateWidgets();
                });
            }

            @Override
            public void onError(String error) {
                // Keep showing cached data. Do NOT clear the session or redirect.
                android.util.Log.w("MainActivity", "Background refresh failed: " + error);
            }
        });

        // Also fetch attendance in background
        fetchAttendanceInBackground();
    }

    private void fetchAttendanceInBackground() {
        android.util.Log.d("MainActivity", "Starting attendance fetch...");
        attendanceFetcher.fetchAttendance(new AttendanceFetcher.FetchCallback() {
            @Override
            public void onSuccess(List<AttendanceData> attendanceList) {
                android.util.Log.d("MainActivity", "Attendance fetch SUCCESS: " + attendanceList.size() + " records");
                for (AttendanceData att : attendanceList) {
                    android.util.Log.d("MainActivity", "  - " + att.getCourseCode() + ": " + att.getPercentage() + "% (" + att.getClassesAttended() + "/" + att.getTotalClasses() + ")");
                }
                // Save attendance for later use
                AttendanceStorage.saveAttendance(MainActivity.this, attendanceList);
                // Refresh the view to show attendance
                runOnUiThread(() -> {
                    if (pagerAdapter != null) {
                        pagerAdapter.notifyDataSetChanged();
                    }
                });
            }

            @Override
            public void onError(String error) {
                android.util.Log.e("MainActivity", "Attendance fetch FAILED: " + error);
            }
        });
    }

    private void initViews() {
        toolbar = findViewById(R.id.toolbar);
        dayTabs = findViewById(R.id.dayTabs);
        viewPager = findViewById(R.id.viewPager);
        loadingState = findViewById(R.id.loadingState);
        prevWeekBtn = findViewById(R.id.prevWeekBtn);
        nextWeekBtn = findViewById(R.id.nextWeekBtn);
        weekRangeText = findViewById(R.id.weekRangeText);

        // Setup week navigation
        setupWeekNavigation();
    }

    private void setupWeekNavigation() {
        displayedWeekStart = Calendar.getInstance();
        // Set to Monday of current week
        int dayOfWeek = displayedWeekStart.get(Calendar.DAY_OF_WEEK);
        int daysToSubtract = (dayOfWeek == Calendar.SUNDAY) ? 6 : dayOfWeek - Calendar.MONDAY;
        displayedWeekStart.add(Calendar.DAY_OF_MONTH, -daysToSubtract);

        updateWeekRangeText();

        prevWeekBtn.setOnClickListener(v -> {
            weekOffset--;
            displayedWeekStart.add(Calendar.WEEK_OF_YEAR, -1);
            updateWeekRangeText();
            fetchTimetableForWeek();
        });

        nextWeekBtn.setOnClickListener(v -> {
            weekOffset++;
            displayedWeekStart.add(Calendar.WEEK_OF_YEAR, 1);
            updateWeekRangeText();
            fetchTimetableForWeek();
        });
    }

    private void updateWeekRangeText() {
        SimpleDateFormat sdf = new SimpleDateFormat("MMM d", Locale.US);
        Calendar weekEnd = (Calendar) displayedWeekStart.clone();
        weekEnd.add(Calendar.DAY_OF_MONTH, 6);

        if (weekOffset == 0) {
            weekRangeText.setText("This Week");
        } else {
            String text = sdf.format(displayedWeekStart.getTime()) + " - " + sdf.format(weekEnd.getTime());
            weekRangeText.setText(text);
        }

        // Refresh tab titles to show correct dates for the selected week
        updateTabTitles();
    }

    private void updateTabTitles() {
        for (int i = 0; i < dayTabs.getTabCount(); i++) {
            TabLayout.Tab tab = dayTabs.getTabAt(i);
            if (tab != null) {
                tab.setText(getTabTitle(i));
            }
        }
    }

    private void fetchTimetableForWeek() {
        showLoading(true);
        timetableFetcher.fetchTimetableForWeek(weekOffset, new TimetableFetcher.FetchCallback() {
            @Override
            public void onSuccess(List<TimetableClass> classes) {
                runOnUiThread(() -> {
                    showLoading(false);
                    processTimetable(classes);
                    pagerAdapter.setTimetableData(timetableByDay);

                    // Merge new data with existing - preserves data from other weeks
                    TimetableStorage.mergeTimetable(MainActivity.this, classes);
                    RoomChangeNotificationManager.schedule(MainActivity.this);
                    updateWidgets();
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    showLoading(false);
                    Toast.makeText(MainActivity.this, "Error: " + error, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void setupToolbar() {
        setSupportActionBar(toolbar);
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1001);
        }
    }

    private void requestExactAlarmPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            android.app.AlarmManager alarmManager = (android.app.AlarmManager) getSystemService(android.app.AlarmManager.class);
            if (alarmManager != null && !alarmManager.canScheduleExactAlarms()) {
                Intent intent = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                        Uri.parse("package:" + getPackageName()));
                startActivity(intent);
            }
        }
    }

    private void setupViewPager() {
        pagerAdapter = new DayPagerAdapter(getLayoutInflater());
        viewPager.setAdapter(pagerAdapter);

        // Connect TabLayout with ViewPager2
        new TabLayoutMediator(dayTabs, viewPager, (tab, position) -> {
            tab.setText(getTabTitle(position));
        }).attach();


        // Set current day
        Calendar calendar = Calendar.getInstance();
        int dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK);
        int currentDayIndex = (dayOfWeek == Calendar.SUNDAY) ? 6 : dayOfWeek - 2;
        if (currentDayIndex >= 0 && currentDayIndex < 7) {
            viewPager.setCurrentItem(currentDayIndex, false);
        }
    }

    private String getTabTitle(int position) {
        String[] shortDays = {"Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"};

        // Get the date for this day of the week from the displayed week
        Calendar calendar = (Calendar) displayedWeekStart.clone();
        calendar.add(Calendar.DAY_OF_MONTH, position); // position 0 = Monday

        int dayOfMonth = calendar.get(Calendar.DAY_OF_MONTH);

        // Check if this is today
        Calendar today = Calendar.getInstance();
        boolean isToday = calendar.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
                          calendar.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR);

        if (isToday) {
            return "TODAY";
        }

        return shortDays[position] + " " + getOrdinalSuffix(dayOfMonth);
    }

    private String getOrdinalSuffix(int day) {
        if (day >= 11 && day <= 13) {
            return day + "th";
        }
        switch (day % 10) {
            case 1: return day + "st";
            case 2: return day + "nd";
            case 3: return day + "rd";
            default: return day + "th";
        }
    }

    private void fetchTimetable() {
        showLoading(true);

        timetableFetcher.fetchTimetable(new TimetableFetcher.FetchCallback() {
            @Override
            public void onSuccess(List<TimetableClass> classes) {
                runOnUiThread(() -> {
                    showLoading(false);
                    processTimetable(classes);
                    pagerAdapter.setTimetableData(timetableByDay);

                    // Save timetable for widget access
                    TimetableStorage.saveTimetable(MainActivity.this, classes);
                    RoomChangeNotificationManager.schedule(MainActivity.this);

                    // Notify widgets to update
                    updateWidgets();
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    showLoading(false);
                    Toast.makeText(MainActivity.this, "Could not refresh: " + error, Toast.LENGTH_LONG).show();
                    // Only ask for login when there is no session and nothing cached.
                    List<TimetableClass> cached = TimetableStorage.loadTimetable(MainActivity.this);
                    boolean hasCache = cached != null && !cached.isEmpty();
                    if (!cookieManager.hasCookies() && !hasCache) {
                        goToLoginActivity();
                    }
                });
            }
        });

        // Also fetch attendance in background
        fetchAttendanceInBackground();
    }

    private void processTimetable(List<TimetableClass> classes) {
        timetableByDay = new HashMap<>();

        for (String day : fullDays) {
            timetableByDay.put(day, new ArrayList<>());
        }

        // Get the date range for the currently displayed week
        Calendar weekStart = (Calendar) displayedWeekStart.clone();
        Calendar weekEnd = (Calendar) displayedWeekStart.clone();
        weekEnd.add(Calendar.DAY_OF_MONTH, 6); // Add 6 days to get Saturday

        java.text.SimpleDateFormat dateFormat = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US);
        String startDateStr = dateFormat.format(weekStart.getTime());
        String endDateStr = dateFormat.format(weekEnd.getTime());

        for (TimetableClass timetableClass : classes) {
            String day = timetableClass.getDayOfWeek();
            List<TimetableClass> dayClasses = timetableByDay.get(day);

            if (dayClasses != null) {
                // If class has a date, only include if it's within current week
                // Only include classes that have a date AND fall within the displayed week
                if (timetableClass.getDate() != null && !timetableClass.getDate().isEmpty()) {
                    if (timetableClass.getDate().compareTo(startDateStr) >= 0 &&
                        timetableClass.getDate().compareTo(endDateStr) <= 0) {
                        dayClasses.add(timetableClass);
                    }
                }
                // Classes without dates are ignored (stale legacy cache entries)
            }
        }
    }

    private void updateWidgets() {
        Intent intent = new Intent(this, TimetableWidgetProvider.class);
        intent.setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
        int[] ids = AppWidgetManager.getInstance(getApplication())
                .getAppWidgetIds(new android.content.ComponentName(getApplication(), TimetableWidgetProvider.class));
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids);
        sendBroadcast(intent);
    }

    private void showLoading(boolean show) {
        if (show) {
            loadingState.setVisibility(View.VISIBLE);
            viewPager.setVisibility(View.GONE);
        } else {
            loadingState.setVisibility(View.GONE);
            viewPager.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.action_week_view) {
            openWeekView();
            return true;
        } else if (id == R.id.action_refresh) {
            fetchTimetable();
            return true;
        } else if (id == R.id.action_exam) {
            startActivity(new Intent(this, ExamActivity.class));
            return true;
        } else if (id == R.id.action_update) {
            UpdateChecker.checkAndUpdate(this);
            return true;
        } else if (id == R.id.action_logout) {
            cookieManager.clearCookies();
            goToLoginActivity();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void openWeekView() {
        if (timetableByDay == null || timetableByDay.isEmpty()) {
            Toast.makeText(this, "No timetable data available", Toast.LENGTH_SHORT).show();
            return;
        }

        Intent intent = new Intent(this, WeekViewActivity.class);
        intent.putExtra("timetableData", (Serializable) timetableByDay);
        startActivity(intent);
    }

    private void goToLoginActivity() {
        Intent intent = new Intent(MainActivity.this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}