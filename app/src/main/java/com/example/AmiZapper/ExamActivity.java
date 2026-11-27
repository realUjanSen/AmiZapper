package com.example.amizapper;

import android.os.Bundle;
import android.view.Gravity;
import android.graphics.Paint;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;

public class ExamActivity extends AppCompatActivity {
    private static final int DATE_COLUMN = 0;
    private static final int TIME_COLUMN = 1;
    private static final int ROOM_COLUMN = 2;
    private static final int TITLE_COLUMN = 3;
    private static final int COURSE_COLUMN = 4;
    private LinearLayout table;
    private TextView status;
    private ExamFetcher fetcher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_exam);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        toolbar.setNavigationOnClickListener(v -> finish());

        table = findViewById(R.id.examTable);
        status = findViewById(R.id.examStatus);
        fetcher = new ExamFetcher(new CookieManager(this));
        render(ExamStorage.load(this));
        refresh();
    }

    private void refresh() {
        status.setText("Updating exam schedule...");
        fetcher.fetch(new ExamFetcher.FetchCallback() {
            @Override public void onSuccess(List<ExamData> exams) {
                ExamStorage.save(ExamActivity.this, exams);
                runOnUiThread(() -> {
                    render(exams);
                    if (exams.isEmpty()) {
                        status.setText("Logged in, but Amizone returned no exam rows");
                    }
                });
            }

            @Override public void onError(String error) {
                runOnUiThread(() -> {
                    if ("Session expired".equals(error)) {
                        status.setText("Amizone session expired. Please log in again.");
                    } else if (ExamStorage.load(ExamActivity.this).isEmpty()) {
                        status.setText("Could not load exam schedule: " + error);
                    }
                    Toast.makeText(ExamActivity.this, error, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void render(List<ExamData> exams) {
        while (table.getChildCount() > 1) table.removeViewAt(1);
        if (exams.isEmpty()) {
            status.setText("No exams found");
            return;
        }
        status.setText("Updated");
        int[] columnWidths = calculateColumnWidths(exams);
        applyColumnWidths(table.getChildAt(0), columnWidths);
        for (ExamData exam : exams) {
            LinearLayout row = createRow();
            addCell(row, formatDate(exam.getExamDate()), columnWidths[DATE_COLUMN], false);
            addCell(row, formatTime(exam.getExamTime()), columnWidths[TIME_COLUMN], false);
            addCell(row, exam.getRoom(), columnWidths[ROOM_COLUMN], false);
            addCell(row, wrapTitle(exam.getCourseTitle()), 0, true);
            addCell(row, exam.getCourseCode(), columnWidths[COURSE_COLUMN], false);
            table.addView(row);
        }
        table.post(this::equalizeRowHeights);
    }

    private int[] calculateColumnWidths(List<ExamData> exams) {
        Paint paint = new Paint();
        paint.setTextSize(13 * getResources().getDisplayMetrics().scaledDensity);
        String[] headers = {"Date", "Time", "Room", "Title", "Course"};
        String[] longest = headers.clone();
        for (ExamData exam : exams) {
            longest[DATE_COLUMN] = longer(longest[DATE_COLUMN], formatDate(exam.getExamDate()));
            longest[TIME_COLUMN] = longer(longest[TIME_COLUMN], formatTime(exam.getExamTime()));
            longest[ROOM_COLUMN] = longer(longest[ROOM_COLUMN], exam.getRoom());
            longest[COURSE_COLUMN] = longer(longest[COURSE_COLUMN], exam.getCourseCode());
        }
        int paddingPx = dp(16);
        return new int[]{
                (int) paint.measureText(longest[DATE_COLUMN]) + paddingPx,
                (int) paint.measureText(longest[TIME_COLUMN]) + paddingPx,
                (int) paint.measureText(longest[ROOM_COLUMN]) + paddingPx,
                0,
                (int) paint.measureText(longest[COURSE_COLUMN]) + paddingPx
        };
    }

    private String longer(String first, String second) {
        return second != null && second.length() > first.length() ? second : first;
    }

    private LinearLayout createRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setBaselineAligned(false);
        row.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        return row;
    }

    private void applyColumnWidths(android.view.View rowView, int[] widths) {
        if (!(rowView instanceof LinearLayout)) return;
        LinearLayout row = (LinearLayout) rowView;
        for (int i = 0; i < row.getChildCount(); i++) {
            TextView cell = (TextView) row.getChildAt(i);
            if (i == TITLE_COLUMN) {
                cell.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
            } else {
                cell.setLayoutParams(new LinearLayout.LayoutParams(widths[i], LinearLayout.LayoutParams.WRAP_CONTENT));
            }
        }
    }

    private void equalizeRowHeights() {
        for (int rowIndex = 0; rowIndex < table.getChildCount(); rowIndex++) {
            android.view.View rowView = table.getChildAt(rowIndex);
            if (!(rowView instanceof LinearLayout)) continue;
            LinearLayout row = (LinearLayout) rowView;
            int height = 0;
            for (int cellIndex = 0; cellIndex < row.getChildCount(); cellIndex++) {
                height = Math.max(height, row.getChildAt(cellIndex).getMeasuredHeight());
            }
            for (int cellIndex = 0; cellIndex < row.getChildCount(); cellIndex++) {
                android.view.View cell = row.getChildAt(cellIndex);
                LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) cell.getLayoutParams();
                params.height = height;
                cell.setLayoutParams(params);
            }
        }
    }

    private void addCell(LinearLayout row, String value, int widthDp, boolean expand) {
        TextView cell = new TextView(this);
        cell.setText(value);
        cell.setTextSize(13);
        cell.setTextColor(0xFF212121);
        cell.setPadding(8, 10, 8, 10);
        cell.setGravity(Gravity.CENTER_VERTICAL);
        cell.setBackgroundResource(R.drawable.exam_table_cell);
        if (expand) {
            row.addView(cell, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        } else {
                int widthPx = widthDp;
            row.addView(cell, new LinearLayout.LayoutParams(widthPx, LinearLayout.LayoutParams.WRAP_CONTENT));
        }
    }

        private int dp(int value) {
            return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
        }

    private String formatDate(String value) {
        try {
            return new SimpleDateFormat("d MMMM yyyy", Locale.US)
                    .format(new SimpleDateFormat("dd/MM/yyyy", Locale.US).parse(value));
        } catch (ParseException | NullPointerException e) {
            return value;
        }
    }

    private String formatTime(String value) {
        try {
            return new SimpleDateFormat("h:mm a", Locale.US)
                    .format(new SimpleDateFormat("HH:mm", Locale.US).parse(value));
        } catch (ParseException | NullPointerException e) {
            return value;
        }
    }

    private String wrapTitle(String value) {
        if (value == null || value.length() <= 20) return value;
        int split = value.lastIndexOf(' ', 20);
        if (split <= 0) split = value.indexOf(' ', 20);
        return split > 0 ? value.substring(0, split) + "\n" + value.substring(split + 1) : value;
    }
}