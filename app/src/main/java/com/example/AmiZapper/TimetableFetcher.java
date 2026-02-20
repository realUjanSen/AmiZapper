package com.example.amizapper;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Fetches and parses timetable from Amizone using the Calendar/Diary Events API
 */
public class TimetableFetcher {
    private static final String DIARY_EVENTS_URL = "https://s.amizone.net/Calendar/home/GetDiaryEvents";

    private OkHttpClient client;
    private CookieManager cookieManager;

    public TimetableFetcher(CookieManager cookieManager) {
        this.cookieManager = cookieManager;
        this.client = new OkHttpClient();
    }

    public interface FetchCallback {
        void onSuccess(List<TimetableClass> classes);
        void onError(String error);
    }

    public void fetchTimetable(FetchCallback callback) {
        fetchTimetableForWeek(0, callback);
    }

    public void fetchTimetableForWeek(int weekOffset, FetchCallback callback) {
        new Thread(() -> {
            try {
                String cookieString = cookieManager.getCookieString();
                if (cookieString == null) {
                    callback.onError("No cookies found. Please login first.");
                    return;
                }

                // Calculate date range for the requested week
                Calendar cal = Calendar.getInstance();
                int dayOfWeek = cal.get(Calendar.DAY_OF_WEEK);

                // Adjust to Monday of current week
                int daysToMonday = (dayOfWeek == Calendar.SUNDAY) ? -6 : (Calendar.MONDAY - dayOfWeek);
                cal.add(Calendar.DAY_OF_MONTH, daysToMonday);

                // Apply week offset
                cal.add(Calendar.WEEK_OF_YEAR, weekOffset);

                String startDate = formatDate(cal);

                // Move to Sunday of that week
                cal.add(Calendar.DAY_OF_MONTH, 6);
                String endDate = formatDate(cal);

                String url = DIARY_EVENTS_URL + "?start=" + startDate + "&end=" + endDate
                           + "&_=" + System.currentTimeMillis();

                Request request = new Request.Builder()
                        .url(url)
                        .header("Cookie", cookieString)
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:140.0) Gecko/20100101 Firefox/140.0")
                        .header("Accept", "application/json, text/javascript, */*; q=0.01")
                        .header("Accept-Language", "en-US,en;q=0.5")
                        .header("Referer", "https://s.amizone.net/Home")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .build();

                Response response = client.newCall(request).execute();

                if (response.isSuccessful()) {
                    String json = response.body().string();
                    List<TimetableClass> classes = parseJsonTimetable(json);
                    callback.onSuccess(classes);
                } else {
                    callback.onError("Failed to fetch timetable. Status: " + response.code());
                }
            } catch (IOException e) {
                callback.onError("Network error: " + e.getMessage());
            } catch (Exception e) {
                callback.onError("Error parsing timetable: " + e.getMessage());
            }
        }).start();
    }

    private String formatDate(Calendar cal) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        return sdf.format(cal.getTime());
    }

    private static final String TAG = "TimetableFetcher";

    private List<TimetableClass> parseJsonTimetable(String json) throws Exception {
        List<TimetableClass> classes = new ArrayList<>();

        JSONArray events = new JSONArray(json);

        for (int i = 0; i < events.length(); i++) {
            JSONObject event = events.getJSONObject(i);

            // Only process class events (sType = "C") and potentially cancelled classes
            String sType = event.optString("sType", "");
            if (!"C".equals(sType)) {
                // Log non-class events briefly - cancelled classes might have a different sType
                String nonClassTitle = event.optString("title", "");
                String nonClassColor = event.optString("color", "");
                String nonClassName = event.optString("className", "");
                String nonClassCode = event.optString("CourseCode", "");
                Log.d(TAG, "NON-CLASS event: sType='" + sType + "' title='" + nonClassTitle
                        + "' color='" + nonClassColor + "' className='" + nonClassName
                        + "' code='" + nonClassCode + "'");
                continue;
            }

            String title = event.optString("title", "");
            String courseCode = event.optString("CourseCode", "");
            String facultyName = event.optString("FacultyName", "");
            String roomNo = event.optString("RoomNo", "");
            String attndColor = event.optString("AttndColor", "#3a87ad");
            String colorField = event.optString("color", "");
            String classNameField = event.optString("className", "");
            String startStr = event.optString("start", "");
            String endStr = event.optString("end", "");
            String urlField = event.optString("url", "");
            boolean allDay = event.optBoolean("allDay", false);
            int idField = event.optInt("id", -1);

            // Log ALL fields for every class event - crucial to identify the cancelled pattern
            Log.d(TAG, "═══ CLASS EVENT ═══");
            Log.d(TAG, "  id=" + idField + " | sType=" + sType + " | allDay=" + allDay);
            Log.d(TAG, "  title='" + title + "'");
            Log.d(TAG, "  CourseCode='" + courseCode + "'");
            Log.d(TAG, "  FacultyName='" + facultyName + "'");
            Log.d(TAG, "  RoomNo='" + roomNo + "'");
            Log.d(TAG, "  color='" + colorField + "'");
            Log.d(TAG, "  className='" + classNameField + "'");
            Log.d(TAG, "  AttndColor='" + attndColor + "'");
            Log.d(TAG, "  url='" + urlField + "'");
            Log.d(TAG, "  start='" + startStr + "' | end='" + endStr + "'");
            // Also dump the raw JSON of this event
            Log.d(TAG, "  RAW_JSON=" + event.toString());

            // Detect cancelled class:
            // Amizone marks cancelled classes with className/color = "StrikeOutClass"
            boolean isCancelled = "StrikeOutClass".equals(colorField)
                    || "StrikeOutClass".equals(classNameField)
                    || colorField.toLowerCase(Locale.US).contains("cancel")
                    || classNameField.toLowerCase(Locale.US).contains("cancel")
                    || title.toLowerCase(Locale.US).contains("cancelled")
                    || title.toLowerCase(Locale.US).contains("canceled");
            if (isCancelled) {
                Log.d(TAG, "  *** CANCELLED CLASS DETECTED: " + title + " (color='" + colorField + "', className='" + classNameField + "')");
            }

            // Parse times, day, and DATE from the start/end strings
            // Format: "2026/02/02 09:15:00 AM"
            String dayOfWeek = parseDayOfWeek(startStr);
            String dateStr = parseDate(startStr); // Extract actual date yyyy-MM-dd
            String startTime = parseTime(startStr);
            String endTime = parseTime(endStr);
            String timeRange = startTime + " to " + endTime;

            if (!courseCode.isEmpty() && !startTime.isEmpty()) {
                TimetableClass cls = new TimetableClass(
                        timeRange,
                        courseCode,
                        title,  // Subject name comes directly from API
                        facultyName,
                        roomNo,
                        dayOfWeek,
                        attndColor,  // Attendance color
                        dateStr,     // Actual date
                        isCancelled  // Whether the class is cancelled
                );
                classes.add(cls);
                if (isCancelled) {
                    Log.d(TAG, "  ^^ DETECTED AS CANCELLED ^^");
                }
            }
        }

        return classes;
    }

    private String parseDate(String dateTimeStr) {
        try {
            // Format: "2026/02/02 09:15:00 AM" -> "2026-02-02"
            SimpleDateFormat inputFormat = new SimpleDateFormat("yyyy/MM/dd hh:mm:ss a", Locale.US);
            SimpleDateFormat outputFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
            return outputFormat.format(inputFormat.parse(dateTimeStr));
        } catch (Exception e) {
            return null;
        }
    }

    private String parseDayOfWeek(String dateTimeStr) {
        try {
            // Format: "2026/02/02 09:15:00 AM"
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd hh:mm:ss a", Locale.US);
            Calendar cal = Calendar.getInstance();
            cal.setTime(sdf.parse(dateTimeStr));

            String[] days = {"Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"};
            return days[cal.get(Calendar.DAY_OF_WEEK) - 1];
        } catch (Exception e) {
            return "Monday";
        }
    }

    private String parseTime(String dateTimeStr) {
        try {
            // Format: "2026/02/02 09:15:00 AM" -> "09:15" (24-hour format)
            SimpleDateFormat inputFormat = new SimpleDateFormat("yyyy/MM/dd hh:mm:ss a", Locale.US);
            SimpleDateFormat outputFormat = new SimpleDateFormat("HH:mm", Locale.US);
            return outputFormat.format(inputFormat.parse(dateTimeStr));
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "";
    }
}
