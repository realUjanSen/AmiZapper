package com.example.amizapper;

import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Week View with transposed layout:
 * - Rows = Days (Mon-Sat)
 * - Columns = Time slots (Period 1-8 + Lunch)
 * - Horizontal red timeline that moves right as time passes
 */
public class WeekViewActivity extends AppCompatActivity {

    private static final int CELL_HEIGHT_DP = 60;
    private static final int CELL_WIDTH_DP = 110;
    private static final int DAY_CELL_WIDTH_DP = 60;
    private static final int HEADER_HEIGHT_DP = 35;
    private static final int LUNCH_WIDTH_DP = 60;

    private Map<String, List<TimetableClass>> timetableByDay;
    private boolean hasSaturdayClasses = false;
    private boolean hasPeriod8Classes = false;
    private int effectiveCellHeight = CELL_HEIGHT_DP;
    private LinearLayout headerRow;
    private LinearLayout dayRowsContainer;
    private HorizontalScrollView horizontalScroll;
    private LinearLayout toolbar;

    private final String[] days = {"Mon", "Tue", "Wed", "Thu", "Fri", "Sat"};
    private final String[] fullDays = {"Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"};

    // Time indicator tracking
    private final List<CellIndicatorInfo> cellIndicators = new ArrayList<>();
    private Handler timeHandler;
    private Runnable timeUpdateRunnable;

    private static class CellIndicatorInfo {
        View indicator;
        View indicatorCircle;
        FrameLayout container;
        int dayIndex;
        int periodNumber;
        boolean isLunch;
        String startTime;
        String endTime;

        CellIndicatorInfo(View indicator, View indicatorCircle, FrameLayout container,
                          int dayIndex, int periodNumber, boolean isLunch,
                          String startTime, String endTime) {
            this.indicator = indicator;
            this.indicatorCircle = indicatorCircle;
            this.container = container;
            this.dayIndex = dayIndex;
            this.periodNumber = periodNumber;
            this.isLunch = isLunch;
            this.startTime = startTime;
            this.endTime = endTime;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Enable immersive mode for landscape
        setupImmersiveMode();

        setContentView(R.layout.activity_week_view);

        toolbar = findViewById(R.id.toolbar);
        ImageButton backButton = findViewById(R.id.backButton);
        backButton.setOnClickListener(v -> finish());

        headerRow = findViewById(R.id.headerRow);
        dayRowsContainer = findViewById(R.id.dayRowsContainer);
        horizontalScroll = findViewById(R.id.horizontalScroll);

        loadTimetableData();
        buildGrid();

        // Setup time indicator updates
        setupTimeIndicatorUpdates();

        // Update immersive mode when orientation changes
        updateImmersiveModeForOrientation();
    }

    private void setupTimeIndicatorUpdates() {
        timeHandler = new Handler(Looper.getMainLooper());
        timeUpdateRunnable = () -> {
            updateTimeIndicators();
            timeHandler.postDelayed(timeUpdateRunnable, 30000); // Update every 30 seconds
        };
        timeHandler.post(timeUpdateRunnable);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (timeHandler != null && timeUpdateRunnable != null) {
            timeHandler.removeCallbacks(timeUpdateRunnable);
        }
    }

    private void updateTimeIndicators() {
        int todayIndex = CurrentTimeIndicator.getTodayIndex();
        int currentPeriod = CurrentTimeIndicator.getCurrentPeriodNumber();
        int[] gapPeriods = CurrentTimeIndicator.getGapAdjacentPeriods();

        for (CellIndicatorInfo info : cellIndicators) {
            boolean showIndicator = false;
            float progress = 0;
            boolean atEnd = false;
            boolean atStart = false;

            // Only show on today's row
            if (info.dayIndex != todayIndex) {
                info.indicator.setVisibility(View.GONE);
                info.indicatorCircle.setVisibility(View.GONE);
                continue;
            }

            if (currentPeriod >= 0) {
                // We're in a period (including lunch = 0)
                boolean isCurrentPeriod = (info.isLunch && currentPeriod == 0) ||
                        (!info.isLunch && info.periodNumber == currentPeriod);

                if (isCurrentPeriod) {
                    showIndicator = true;
                    CurrentTimeIndicator.IndicatorPosition pos =
                            CurrentTimeIndicator.getIndicatorPosition(info.startTime, info.endTime);
                    progress = pos.progress;
                }
            } else if (currentPeriod == -2 && gapPeriods != null) {
                // We're in a gap between periods
                int prevPeriod = gapPeriods[0];
                int nextPeriod = gapPeriods[1];

                // Show at end of previous period
                boolean isPrevPeriod = (info.isLunch && prevPeriod == 0) ||
                        (!info.isLunch && info.periodNumber == prevPeriod);
                if (isPrevPeriod) {
                    showIndicator = true;
                    atEnd = true;
                    progress = 1.0f;
                }

                // Show at start of next period
                boolean isNextPeriod = (info.isLunch && nextPeriod == 0) ||
                        (!info.isLunch && info.periodNumber == nextPeriod);
                if (isNextPeriod) {
                    showIndicator = true;
                    atStart = true;
                    progress = 0.0f;
                }
            }

            if (showIndicator) {
                positionCellIndicator(info, progress, atEnd, atStart);
            } else {
                info.indicator.setVisibility(View.GONE);
                info.indicatorCircle.setVisibility(View.GONE);
            }
        }
    }

    private void positionCellIndicator(CellIndicatorInfo info, float progress, boolean atEnd, boolean atStart) {
        info.container.post(() -> {
            int cellWidth = info.container.getWidth();
            if (cellWidth <= 0) return;

            float xPos;
            if (atEnd) {
                xPos = cellWidth - 2;
            } else if (atStart) {
                xPos = 0;
            } else {
                xPos = progress * cellWidth;
            }

            // Position the vertical line
            info.indicator.setVisibility(View.VISIBLE);
            FrameLayout.LayoutParams lineParams = (FrameLayout.LayoutParams) info.indicator.getLayoutParams();
            lineParams.leftMargin = (int) xPos;
            info.indicator.setLayoutParams(lineParams);

            // Position the circle at top
            info.indicatorCircle.setVisibility(View.VISIBLE);
            FrameLayout.LayoutParams circleParams = (FrameLayout.LayoutParams) info.indicatorCircle.getLayoutParams();
            circleParams.leftMargin = (int) (xPos - 4);
            circleParams.topMargin = 0;
            info.indicatorCircle.setLayoutParams(circleParams);
        });
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            updateImmersiveModeForOrientation();
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        updateImmersiveModeForOrientation();
    }

    private void setupImmersiveMode() {
        // Enable edge-to-edge display
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // Use display cutout area (notch)
            WindowManager.LayoutParams layoutParams = getWindow().getAttributes();
            layoutParams.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            getWindow().setAttributes(layoutParams);
        }
    }

    private void updateImmersiveModeForOrientation() {
        int orientation = getResources().getConfiguration().orientation;

        if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            // Landscape: Full immersive mode
            enableFullImmersiveMode();

            // Hide toolbar
            if (toolbar != null) {
                toolbar.setVisibility(View.GONE);
            }

            // Hide action bar/toolbar if present
            if (getSupportActionBar() != null) {
                getSupportActionBar().hide();
            }
        } else {
            // Portrait: Normal mode
            disableImmersiveMode();

            // Show toolbar
            if (toolbar != null) {
                toolbar.setVisibility(View.VISIBLE);
            }

            // Show action bar/toolbar if present
            if (getSupportActionBar() != null) {
                getSupportActionBar().show();
            }
        }
    }

    private void enableFullImmersiveMode() {
        View decorView = getWindow().getDecorView();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+ API
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                controller.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            // Pre-Android 11
            WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(getWindow(), decorView);
            controller.hide(WindowInsetsCompat.Type.systemBars());
            controller.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);

            // Additional flags for older devices
            decorView.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_FULLSCREEN
            );
        }
    }

    private void disableImmersiveMode() {
        View decorView = getWindow().getDecorView();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                controller.show(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
            }
        } else {
            WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(getWindow(), decorView);
            controller.show(WindowInsetsCompat.Type.systemBars());

            decorView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
        }
    }

    @SuppressWarnings("unchecked")
    private void loadTimetableData() {
        timetableByDay = (HashMap<String, List<TimetableClass>>) getIntent().getSerializableExtra("timetableData");
        if (timetableByDay == null) {
            timetableByDay = new HashMap<>();
        }

        // Check if Saturday has any classes
        detectSaturdayClasses();

        // Check if Period 8 (4:30-5:25) has any classes across the week
        detectPeriod8Classes();

        // Adjust cell height if no Saturday classes
        // Distribute Saturday's space among Mon-Fri (20% increase)
        if (!hasSaturdayClasses) {
            effectiveCellHeight = (int)(CELL_HEIGHT_DP * 1.2f);
        } else {
            effectiveCellHeight = CELL_HEIGHT_DP;
        }
    }

    private void detectSaturdayClasses() {
        hasSaturdayClasses = false;
        List<TimetableClass> saturdayClasses = timetableByDay.get("Saturday");
        if (saturdayClasses != null && !saturdayClasses.isEmpty()) {
            hasSaturdayClasses = true;
        }
    }

    private void detectPeriod8Classes() {
        hasPeriod8Classes = false;
        // Check all days for Period 8 (4:30-5:25)
        for (String day : fullDays) {
            List<TimetableClass> classes = timetableByDay.get(day);
            if (classes != null) {
                for (TimetableClass cls : classes) {
                    int period = PeriodSlots.findPeriodNumber(cls.getStartTime());
                    if (period == 8) {
                        hasPeriod8Classes = true;
                        return;
                    }
                }
            }
        }
    }

    private void buildGrid() {
        headerRow.removeAllViews();
        dayRowsContainer.removeAllViews();
        cellIndicators.clear();

        // Build class lookup map: day -> period -> class
        Map<String, Map<Integer, TimetableClass>> classLookup = new HashMap<>();
        Map<String, TimetableClass> holidayLookup = new HashMap<>();
        for (String day : fullDays) {
            Map<Integer, TimetableClass> dayClasses = new HashMap<>();
            List<TimetableClass> classes = timetableByDay.get(day);
            if (classes != null) {
                for (TimetableClass cls : classes) {
                    if (cls.isHoliday()) {
                        holidayLookup.put(day, cls);
                        continue;
                    }
                    int period = PeriodSlots.findPeriodNumber(cls.getStartTime());
                    if (period > 0) {
                        dayClasses.put(period, cls);
                    }
                }
            }
            classLookup.put(day, dayClasses);
        }

        // Build header row (time slots)
        buildHeaderRow();

        // Build day rows - only show Saturday if it has classes
        int daysToShow = hasSaturdayClasses ? days.length : days.length - 1;
        for (int i = 0; i < daysToShow; i++) {
            buildDayRow(i, days[i], fullDays[i], classLookup.get(fullDays[i]), holidayLookup.get(fullDays[i]));
        }
    }

    private void buildHeaderRow() {
        // Empty corner cell - add newline to match time header structure
        addHeaderCell("\nDay", DAY_CELL_WIDTH_DP);

        // Period headers (1-4)
        for (int i = 1; i <= 4; i++) {
            PeriodSlots.PeriodSlot slot = getPeriodSlot(i);
            addHeaderCell(slot.startTime + "\n" + slot.endTime, CELL_WIDTH_DP);
        }

        // Lunch header - add newline to match time header structure
        addHeaderCell("\nLUNCH", LUNCH_WIDTH_DP);

        // Period headers (5-8) - only show Period 8 if it has classes
        int lastPeriod = hasPeriod8Classes ? 8 : 7;
        for (int i = 5; i <= lastPeriod; i++) {
            PeriodSlots.PeriodSlot slot = getPeriodSlot(i);
            addHeaderCell(slot.startTime + "\n" + slot.endTime, CELL_WIDTH_DP);
        }
    }

    private void addHeaderCell(String text, int widthDp) {
        TextView cell = new TextView(this);
        int exactHeight = dpToPx(HEADER_HEIGHT_DP);

        // Set exact height in LayoutParams
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            dpToPx(widthDp),
            exactHeight
        );
        params.setMarginStart(1);
        cell.setLayoutParams(params);

        // Force the exact height programmatically
        cell.setHeight(exactHeight);

        cell.setBackgroundColor(ContextCompat.getColor(this, R.color.holo_blue_dark));
        cell.setGravity(Gravity.CENTER);
        cell.setText(text);
        cell.setTextSize(11);
        cell.setTextColor(Color.WHITE);
        cell.setTypeface(null, Typeface.BOLD);
        cell.setMaxLines(2);
        cell.setIncludeFontPadding(false); // Remove extra font padding
        cell.setLineSpacing(0, 0.8f); // Reduce line spacing for multi-line text
        headerRow.addView(cell);
    }

    private PeriodSlots.PeriodSlot getPeriodSlot(int periodNumber) {
        for (PeriodSlots.PeriodSlot slot : PeriodSlots.getAllSlots()) {
            if (slot.periodNumber == periodNumber) {
                return slot;
            }
        }
        return new PeriodSlots.PeriodSlot(periodNumber, "", "", false, "");
    }

    private void buildDayRow(int dayIndex, String shortDay, String fullDay,
                             Map<Integer, TimetableClass> dayClasses, TimetableClass holiday) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        // Day name cell
        TextView dayCell = new TextView(this);
        LinearLayout.LayoutParams dayParams = new LinearLayout.LayoutParams(dpToPx(DAY_CELL_WIDTH_DP), dpToPx(effectiveCellHeight));
        dayCell.setLayoutParams(dayParams);
        dayCell.setBackgroundColor(ContextCompat.getColor(this, R.color.holo_blue_light));
        dayCell.setGravity(Gravity.CENTER);
        dayCell.setText(shortDay);
        dayCell.setTextSize(14);
        dayCell.setTextColor(Color.WHITE);
        dayCell.setTypeface(null, Typeface.BOLD);
        row.addView(dayCell);

        // Period cells (1-4)
        for (int i = 1; i <= 4; i++) {
            TimetableClass cls = dayClasses != null ? dayClasses.get(i) : null;
            if (i == 1 && cls == null) cls = holiday;
            PeriodSlots.PeriodSlot slot = getPeriodSlot(i);
            row.addView(createGridCell(cls, CELL_WIDTH_DP, dayIndex, i, slot.startTime, slot.endTime));
        }

        // Lunch cell
        row.addView(createLunchCell(dayIndex));

        // Period cells (5-8) - only show Period 8 if it has classes
        int lastPeriod = hasPeriod8Classes ? 8 : 7;
        for (int i = 5; i <= lastPeriod; i++) {
            TimetableClass cls = dayClasses != null ? dayClasses.get(i) : null;
            PeriodSlots.PeriodSlot slot = getPeriodSlot(i);
            row.addView(createGridCell(cls, CELL_WIDTH_DP, dayIndex, i, slot.startTime, slot.endTime));
        }

        dayRowsContainer.addView(row);
    }

    private View createGridCell(TimetableClass cls, int widthDp, int dayIndex, int periodNumber, String startTime, String endTime) {
        // Outer FrameLayout for time indicator overlay
        FrameLayout outerContainer = new FrameLayout(this);
        LinearLayout.LayoutParams outerParams = new LinearLayout.LayoutParams(dpToPx(widthDp), dpToPx(effectiveCellHeight));
        outerParams.setMargins(1, 1, 0, 0);
        outerContainer.setLayoutParams(outerParams);

        // Inner container for attendance indicator and cell content
        LinearLayout container = new LinearLayout(this);
        FrameLayout.LayoutParams containerParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT);
        container.setLayoutParams(containerParams);
        container.setOrientation(LinearLayout.HORIZONTAL);

        if (cls != null) {
            // Attendance indicator bar on the left
            View indicator = new View(this);
            LinearLayout.LayoutParams indicatorParams = new LinearLayout.LayoutParams(dpToPx(4), LinearLayout.LayoutParams.MATCH_PARENT);
            indicator.setLayoutParams(indicatorParams);
            try {
                String attColor = cls.getAttendanceColor();
                if (attColor != null && !attColor.isEmpty()) {
                    indicator.setBackgroundColor(Color.parseColor(attColor));
                } else {
                    indicator.setBackgroundColor(Color.parseColor("#3a87ad"));
                }
            } catch (Exception e) {
                indicator.setBackgroundColor(Color.parseColor("#3a87ad"));
            }
            container.addView(indicator);
        }

        LinearLayout cell = new LinearLayout(this);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1);
        cell.setLayoutParams(params);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setGravity(Gravity.CENTER);
        cell.setPadding(4, 4, 4, 4);

        if (cls != null) {
            if (cls.isHoliday()) {
                cell.setBackgroundColor(Color.parseColor("#8E3B46"));

                TextView holidayLabel = new TextView(this);
                holidayLabel.setText("HOLIDAY");
                holidayLabel.setTextSize(10);
                holidayLabel.setTypeface(null, Typeface.BOLD);
                holidayLabel.setTextColor(Color.WHITE);
                holidayLabel.setGravity(Gravity.CENTER);
                cell.addView(holidayLabel);

                TextView holidayTitle = new TextView(this);
                holidayTitle.setText(cls.getSubjectName());
                holidayTitle.setTextSize(12);
                holidayTitle.setTypeface(null, Typeface.BOLD);
                holidayTitle.setTextColor(Color.WHITE);
                holidayTitle.setGravity(Gravity.CENTER);
                holidayTitle.setMaxLines(3);
                cell.addView(holidayTitle);
            } else {
            // Has a class - show details
            cell.setBackgroundResource(R.drawable.week_class_bg);

            // If cancelled, dim the whole cell
            if (cls.isCancelled()) {
                cell.setAlpha(0.5f);
            }

            // Subject name (no character limit, allow wrapping)
            TextView subjectText = new TextView(this);
            subjectText.setText(cls.getSubjectName());
            subjectText.setTextSize(11);
            subjectText.setTypeface(null, Typeface.BOLD);
            subjectText.setTextColor(ContextCompat.getColor(this, R.color.primary_text));
            subjectText.setGravity(Gravity.CENTER);
            subjectText.setMaxLines(3);
            if (cls.isCancelled()) {
                subjectText.setPaintFlags(subjectText.getPaintFlags() | android.graphics.Paint.STRIKE_THRU_TEXT_FLAG);
            }
            cell.addView(subjectText);

            // Course code
            TextView codeText = new TextView(this);
            codeText.setText(cls.getCourseCode());
            codeText.setTextSize(10);
            codeText.setTextColor(ContextCompat.getColor(this, R.color.holo_blue_dark));
            codeText.setGravity(Gravity.CENTER);
            if (cls.isCancelled()) {
                codeText.setPaintFlags(codeText.getPaintFlags() | android.graphics.Paint.STRIKE_THRU_TEXT_FLAG);
            }
            cell.addView(codeText);

            // Teacher (no character limit, allow wrapping)
            TextView teacherText = new TextView(this);
            teacherText.setText(cls.getShortTeacher());
            teacherText.setTextSize(9);
            teacherText.setTextColor(ContextCompat.getColor(this, R.color.secondary_text));
            teacherText.setGravity(Gravity.CENTER);
            teacherText.setMaxLines(2);
            if (cls.isCancelled()) {
                teacherText.setPaintFlags(teacherText.getPaintFlags() | android.graphics.Paint.STRIKE_THRU_TEXT_FLAG);
            }
            cell.addView(teacherText);

            // Location
            TextView locText = new TextView(this);
            locText.setText(cls.getLocation());
            locText.setTextSize(9);
            locText.setTextColor(ContextCompat.getColor(this, R.color.secondary_text));
            locText.setGravity(Gravity.CENTER);
            if (cls.isCancelled()) {
                locText.setPaintFlags(locText.getPaintFlags() | android.graphics.Paint.STRIKE_THRU_TEXT_FLAG);
            }
            cell.addView(locText);
            }

        } else {
            // Free period
            cell.setBackgroundResource(R.drawable.free_period_bg);

            TextView freeText = new TextView(this);
            freeText.setText("-");
            freeText.setTextSize(16);
            freeText.setTextColor(ContextCompat.getColor(this, R.color.light_text));
            freeText.setGravity(Gravity.CENTER);
            cell.addView(freeText);
        }

        container.addView(cell);
        outerContainer.addView(container);

        // Add time indicator (vertical line for horizontal scrolling week view)
        View timeIndicator = new View(this);
        FrameLayout.LayoutParams lineParams = new FrameLayout.LayoutParams(dpToPx(3), FrameLayout.LayoutParams.MATCH_PARENT);
        timeIndicator.setLayoutParams(lineParams);
        timeIndicator.setBackgroundColor(0xFFE53935);
        timeIndicator.setVisibility(View.GONE);
        timeIndicator.setElevation(10);
        outerContainer.addView(timeIndicator);

        // Add time indicator circle
        View timeCircle = new View(this);
        FrameLayout.LayoutParams circleParams = new FrameLayout.LayoutParams(dpToPx(8), dpToPx(8));
        timeCircle.setLayoutParams(circleParams);
        timeCircle.setBackgroundResource(R.drawable.time_indicator_circle);
        timeCircle.setVisibility(View.GONE);
        timeCircle.setElevation(10);
        outerContainer.addView(timeCircle);

        // Register for time updates
        if (cls == null || !cls.isHoliday()) {
            cellIndicators.add(new CellIndicatorInfo(
                timeIndicator, timeCircle, outerContainer,
                dayIndex, periodNumber, false, startTime, endTime));
        }

        return outerContainer;
    }

    private View createLunchCell(int dayIndex) {
        // Outer FrameLayout for time indicator overlay
        FrameLayout outerContainer = new FrameLayout(this);
        LinearLayout.LayoutParams outerParams = new LinearLayout.LayoutParams(dpToPx(LUNCH_WIDTH_DP), dpToPx(effectiveCellHeight));
        outerParams.setMargins(1, 1, 0, 0);
        outerContainer.setLayoutParams(outerParams);

        LinearLayout cell = new LinearLayout(this);
        FrameLayout.LayoutParams cellParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT);
        cell.setLayoutParams(cellParams);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setGravity(Gravity.CENTER);
        cell.setBackgroundColor(ContextCompat.getColor(this, R.color.lunch_break_bg));

        TextView lunchText = new TextView(this);
        lunchText.setText("🍽");
        lunchText.setTextSize(16);
        lunchText.setGravity(Gravity.CENTER);
        cell.addView(lunchText);

        outerContainer.addView(cell);

        // Get lunch time slot
        PeriodSlots.PeriodSlot lunchSlot = null;
        for (PeriodSlots.PeriodSlot slot : PeriodSlots.getAllSlots()) {
            if (slot.isBreak) {
                lunchSlot = slot;
                break;
            }
        }

        // Add time indicator (vertical line for horizontal scrolling week view)
        View timeIndicator = new View(this);
        FrameLayout.LayoutParams lineParams = new FrameLayout.LayoutParams(dpToPx(3), FrameLayout.LayoutParams.MATCH_PARENT);
        timeIndicator.setLayoutParams(lineParams);
        timeIndicator.setBackgroundColor(0xFFE53935);
        timeIndicator.setVisibility(View.GONE);
        timeIndicator.setElevation(10);
        outerContainer.addView(timeIndicator);

        // Add time indicator circle
        View timeCircle = new View(this);
        FrameLayout.LayoutParams circleParams = new FrameLayout.LayoutParams(dpToPx(8), dpToPx(8));
        timeCircle.setLayoutParams(circleParams);
        timeCircle.setBackgroundResource(R.drawable.time_indicator_circle);
        timeCircle.setVisibility(View.GONE);
        timeCircle.setElevation(10);
        outerContainer.addView(timeCircle);

        // Register for time updates
        if (lunchSlot != null) {
            cellIndicators.add(new CellIndicatorInfo(
                    timeIndicator, timeCircle, outerContainer,
                    dayIndex, 0, true, lunchSlot.startTime, lunchSlot.endTime));
        }

        return outerContainer;
    }

    private int dpToPx(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density);
    }
}
