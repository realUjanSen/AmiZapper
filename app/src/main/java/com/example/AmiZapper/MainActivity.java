package com.example.amizapper;

import android.appwidget.AppWidgetManager;
import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    private Toolbar toolbar;
    private TabLayout dayTabs;
    private ViewPager2 viewPager;
    private LinearLayout loadingState;

    private CookieManager cookieManager;
    private TimetableFetcher timetableFetcher;
    private DayPagerAdapter pagerAdapter;

    private Map<String, List<TimetableClass>> timetableByDay;
    private final String[] fullDays = {"Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        cookieManager = new CookieManager(this);
        timetableFetcher = new TimetableFetcher(cookieManager);

        // Check if logged in
        if (!cookieManager.hasCookies()) {
            goToLoginActivity();
            return;
        }

        initViews();
        setupToolbar();
        setupViewPager();

        // Try to load cached timetable first
        loadCachedTimetable();
    }

    private void loadCachedTimetable() {
        List<TimetableClass> cachedClasses = TimetableStorage.loadTimetable(this);

        if (cachedClasses != null && !cachedClasses.isEmpty()) {
            // Use cached data
            processTimetable(cachedClasses);
            pagerAdapter.setTimetableData(timetableByDay);

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

                    // Save timetable for widget access
                    TimetableStorage.saveTimetable(MainActivity.this, classes);

                    // Notify widgets to update
                    updateWidgets();
                });
            }

            @Override
            public void onError(String error) {
                // Silent fail - we already have cached data
                runOnUiThread(() -> {
                    if (error.contains("cookies") || error.contains("401")) {
                        cookieManager.clearCookies();
                        goToLoginActivity();
                    }
                });
            }
        });
    }

    private void initViews() {
        toolbar = findViewById(R.id.toolbar);
        dayTabs = findViewById(R.id.dayTabs);
        viewPager = findViewById(R.id.viewPager);
        loadingState = findViewById(R.id.loadingState);
    }

    private void setupToolbar() {
        setSupportActionBar(toolbar);
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

        // Get the date for this day of the week
        Calendar calendar = Calendar.getInstance();
        int currentDayOfWeek = calendar.get(Calendar.DAY_OF_WEEK);
        int currentIndex = (currentDayOfWeek == Calendar.SUNDAY) ? 6 : currentDayOfWeek - 2;

        // Check if this is today
        if (position == currentIndex) {
            return "TODAY";
        }

        // Calculate difference to target day
        int diff = position - currentIndex;
        calendar.add(Calendar.DAY_OF_MONTH, diff);

        int dayOfMonth = calendar.get(Calendar.DAY_OF_MONTH);
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

                    // Notify widgets to update
                    updateWidgets();
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    showLoading(false);
                    Toast.makeText(MainActivity.this, "Error: " + error, Toast.LENGTH_LONG).show();

                    if (error.contains("cookies") || error.contains("401")) {
                        cookieManager.clearCookies();
                        goToLoginActivity();
                    }
                });
            }
        });
    }

    private void processTimetable(List<TimetableClass> classes) {
        timetableByDay = new HashMap<>();

        for (String day : fullDays) {
            timetableByDay.put(day, new ArrayList<>());
        }

        for (TimetableClass timetableClass : classes) {
            String day = timetableClass.getDayOfWeek();
            List<TimetableClass> dayClasses = timetableByDay.get(day);
            if (dayClasses != null) {
                dayClasses.add(timetableClass);
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
