package com.example.amizapper;
import android.util.Log;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
public class AttendanceFetcher {
    private static final String TAG = "AttendanceFetcher";
    // Try the AJAX partial URL first, then fall back to main Home page
    private static final String HOME_PARTIAL_URL = "https://s.amizone.net/Home/_Home";
    private static final String HOME_URL = "https://s.amizone.net/Home";
    private final OkHttpClient client;
    private final CookieManager cookieManager;
    public AttendanceFetcher(CookieManager cookieManager) {
        this.cookieManager = cookieManager;
        this.client = new OkHttpClient.Builder().followRedirects(true).followSslRedirects(true).build();
    }
    public interface FetchCallback {
        void onSuccess(List<AttendanceData> attendanceList);
        void onError(String error);
    }
    public void fetchAttendance(FetchCallback callback) {
        new Thread(() -> {
            try {
                String cookieString = cookieManager.getCookieString();
                Log.d(TAG, "=== ATTENDANCE FETCH START ===");
                if (cookieString == null || cookieString.isEmpty()) {
                    Log.e(TAG, "No cookies available!");
                    callback.onError("No cookies");
                    return;
                }
                Log.d(TAG, "Cookie present: " + cookieString.length() + " chars");

                // Try AJAX partial first
                String[] urlsToTry = {HOME_PARTIAL_URL, HOME_URL};
                for (String url : urlsToTry) {
                    Log.d(TAG, "Trying URL: " + url);
                    Request request = new Request.Builder()
                            .url(url)
                            .header("Cookie", cookieString)
                            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36")
                            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                            .header("X-Requested-With", "XMLHttpRequest")
                            .header("Referer", "https://s.amizone.net/Home")
                            .build();
                    try (Response response = client.newCall(request).execute()) {
                    Log.d(TAG, "Response code: " + response.code());
                    if (response.isSuccessful() && response.body() != null) {
                        String html = response.body().string();
                        Log.d(TAG, "HTML length: " + html.length());

                        // Log a snippet around "My Attendance" section
                        int attIndex = html.indexOf("My Attendance");
                        if (attIndex != -1) {
                            int start = Math.max(0, attIndex - 50);
                            int end = Math.min(html.length(), attIndex + 500);
                            Log.d(TAG, "Found 'My Attendance' section: " + html.substring(start, end));
                        } else {
                            Log.w(TAG, "'My Attendance' NOT FOUND in HTML from " + url);
                            // Log first 1000 chars to see what we got
                            Log.d(TAG, "HTML preview: " + html.substring(0, Math.min(1000, html.length())));
                        }

                        List<AttendanceData> result = parseAttendanceFromHtml(html);
                        if (!result.isEmpty()) {
                            Log.d(TAG, "=== ATTENDANCE FETCH END: " + result.size() + " records from " + url + " ===");
                            callback.onSuccess(result);
                            return;
                        } else {
                            Log.w(TAG, "Parsed 0 records from " + url + ", trying next URL...");
                        }
                    } else {
                        // Log error response body
                        if (response.body() != null) {
                            String errorBody = response.body().string();
                            Log.e(TAG, "HTTP error body (first 500 chars): " + errorBody.substring(0, Math.min(500, errorBody.length())));
                        }
                        Log.e(TAG, "HTTP error: " + response.code() + " from " + url);
                    }
                    } // end try-with-resources
                } // end url loop
                Log.e(TAG, "All attendance URLs failed");
                callback.onError("All URLs failed");
            } catch (Exception e) {
                Log.e(TAG, "Exception: " + e.getMessage(), e);
                callback.onError(e.getMessage());
            }
        }).start();
    }
    private List<AttendanceData> parseAttendanceFromHtml(String html) {
        List<AttendanceData> list = new ArrayList<>();

        // Try to find the "My Attendance" widget section first
        int widgetStart = html.indexOf("My Attendance");
        if (widgetStart == -1) {
            Log.w(TAG, "Could not find 'My Attendance' in HTML");
            return list;
        }
        Log.d(TAG, "Found 'My Attendance' at position: " + widgetStart);

        // Find the id="tasks" within that section
        int start = html.indexOf("id=\"tasks\"", widgetStart);
        if (start == -1) {
            Log.w(TAG, "Could not find id=\"tasks\" after 'My Attendance'");
            return list;
        }

        int end = html.indexOf("</ul>", start);
        if (end == -1) end = html.length();
        String section = html.substring(start, end);
        Log.d(TAG, "Found tasks section, length: " + section.length());

        // Log first 1000 chars of the section for debugging
        Log.d(TAG, "Section preview: " + section.substring(0, Math.min(1000, section.length())));

        // Match each <li> item - more flexible pattern to match "item-green clearfix" etc
        Pattern li = Pattern.compile("<li\\s+class=\"item-\\w+\\s+clearfix\"[^>]*>([\\s\\S]*?)</li>", Pattern.CASE_INSENSITIVE);
        Matcher m = li.matcher(section);

        int count = 0;
        while (m.find()) {
            count++;
            String content = m.group(1);
            if (content == null) continue;

            Log.d(TAG, "Processing li #" + count + ", length: " + content.length());

            // Extract course code from <span class="sub-code">CODE </span>
            String code = extract(content, "<span\\s+class=\"sub-code\">\\s*([A-Z]+\\d+)\\s*</span>").trim();
            if (code.isEmpty()) {
                // Try alternate pattern
                code = extract(content, "sub-code[\"'>]\\s*([A-Z]+\\d+)").trim();
            }
            Log.d(TAG, "  Extracted code: '" + code + "'");

            // Extract subject name - it follows the sub-code span within the lbl span
            // Pattern: <span class="lbl"><span class="sub-code">CODE </span>       Subject Name</span>
            String name = "";
            Pattern namePattern = Pattern.compile("sub-code[^>]*>[^<]*</span>\\s*([^<]+)</span>", Pattern.CASE_INSENSITIVE);
            Matcher nameMatcher = namePattern.matcher(content);
            if (nameMatcher.find() && nameMatcher.group(1) != null) {
                name = nameMatcher.group(1).trim();
            }
            Log.d(TAG, "  Extracted name: '" + name + "'");

            // Extract percentage from data-percent attribute
            String pct = extract(content, "data-percent=\"([\\d.]+)\"");
            Log.d(TAG, "  Extracted pct: '" + pct + "'");

            // Extract class count like "6/6" or "14/14"
            String cnt = extract(content, "class-count[^>]*>[^<]*<span>\\s*([\\d]+/[\\d]+)\\s*</span>");
            if (cnt.isEmpty()) {
                cnt = extract(content, ">\\s*(\\d+/\\d+)\\s*</");
            }
            Log.d(TAG, "  Extracted count: '" + cnt + "'");

            double percent = 0;
            try {
                if (!pct.isEmpty()) percent = Double.parseDouble(pct);
            } catch (NumberFormatException e) {
                Log.w(TAG, "Could not parse percent: " + pct);
            }

            int att = 0, tot = 0;
            if (!cnt.isEmpty()) {
                String[] parts = cnt.trim().split("/");
                if (parts.length == 2) {
                    try {
                        att = Integer.parseInt(parts[0].trim());
                        tot = Integer.parseInt(parts[1].trim());
                    } catch (NumberFormatException e) {
                        Log.w(TAG, "Could not parse count: " + cnt);
                    }
                }
            }

            if (!code.isEmpty()) {
                AttendanceData data = new AttendanceData(code, name, percent, att, tot);
                list.add(data);
                Log.d(TAG, "SUCCESS Parsed #" + count + ": " + code + " (" + name + ") -> " + percent + "% (" + att + "/" + tot + ")");
            } else {
                Log.w(TAG, "FAILED li #" + count + " - no code found. Content: " + content.substring(0, Math.min(200, content.length())));
            }
        }

        Log.d(TAG, "Total <li> items found: " + count + ", successfully parsed: " + list.size());
        return list;
    }
    private String extract(String text, String regex) {
        Matcher m = Pattern.compile(regex, Pattern.CASE_INSENSITIVE).matcher(text);
        return m.find() && m.group(1) != null ? m.group(1).trim() : "";
    }

    // Test method to parse HTML from assets
    public static void testParseFromAssets(android.content.Context context) {
        try {
            Log.d(TAG, "=== TESTING PARSE FROM ASSETS ===");
            java.io.InputStream is = context.getAssets().open("Homehtml.html");
            java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(is));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
            reader.close();

            String html = sb.toString();
            Log.d(TAG, "Loaded HTML from assets: " + html.length() + " chars");

            AttendanceFetcher fetcher = new AttendanceFetcher(null);
            List<AttendanceData> results = fetcher.parseAttendanceFromHtml(html);

            Log.d(TAG, "=== TEST RESULTS: " + results.size() + " attendance records ===");
            for (AttendanceData data : results) {
                Log.d(TAG, "  " + data.getCourseCode() + ": " + data.getPercentage() + "% (" +
                      data.getClassesAttended() + "/" + data.getTotalClasses() + ") - " + data.getSubjectName());
            }
        } catch (Exception e) {
            Log.e(TAG, "Test failed: " + e.getMessage(), e);
        }
    }
}
