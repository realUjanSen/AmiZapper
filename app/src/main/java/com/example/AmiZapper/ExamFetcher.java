package com.example.amizapper;

import android.util.Log;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class ExamFetcher {
    private static final String TAG = "ExamFetcher";
        private static final String HOME_URL = "https://s.amizone.net/Home";
    private static final String EXAM_URL = "https://s.amizone.net/Examination/ExamSchedule?X-Requested-With=XMLHttpRequest";
    private final CookieManager cookieManager;
        private final OkHttpClient client = new OkHttpClient.Builder()
            .followRedirects(true)
            .followSslRedirects(true)
            .build();
        private final OkHttpClient sessionClient = new OkHttpClient.Builder()
            .followRedirects(false)
            .followSslRedirects(false)
            .build();

    public ExamFetcher(CookieManager cookieManager) {
        this.cookieManager = cookieManager;
    }

    public interface FetchCallback {
        void onSuccess(List<ExamData> exams);
        void onError(String error);
    }

    public void fetch(FetchCallback callback) {
        new Thread(() -> {
            try {
                String cookies = cookieManager.getCookieString();
                if (cookies == null || cookies.isEmpty()) {
                    callback.onError("No cookies");
                    return;
                }

                String sessionError = verifyServerSession(cookies);
                if (sessionError != null) {
                    callback.onError(sessionError);
                    return;
                }

                Request request = new Request.Builder()
                        .url(EXAM_URL)
                        .header("Cookie", cookies)
                        .header("User-Agent", "Mozilla/5.0 (Android) AppleWebKit/537.36 Chrome/121 Safari/537.36")
                        .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .header("Referer", "https://s.amizone.net/Home")
                        .build();

                try (Response response = client.newCall(request).execute()) {
                    if (!response.isSuccessful() || response.body() == null) {
                        callback.onError("Request failed: " + response.code());
                        return;
                    }
                    String html = response.body().string();
                    if (isLoginPage(response, html)) {
                        callback.onError("Session expired");
                        return;
                    }
                    List<ExamData> exams = parse(html);
                    Log.d(TAG, "Exam response parsed: " + exams.size() + " rows, HTML length=" + html.length());
                    callback.onSuccess(exams);
                }
            } catch (Exception e) {
                Log.e(TAG, "Exam fetch failed", e);
                callback.onError(e.getMessage() == null ? "Network error" : e.getMessage());
            }
        }).start();
    }

    private String verifyServerSession(String cookies) throws Exception {
        Request request = new Request.Builder()
                .url(HOME_URL)
                .header("Cookie", cookies)
                .header("User-Agent", "Mozilla/5.0 (Android) AppleWebKit/537.36 Chrome/121 Safari/537.36")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Referer", "https://s.amizone.net/")
                .build();

        try (Response response = sessionClient.newCall(request).execute()) {
            String location = response.header("Location", "");
            if (response.code() >= 300 && response.code() < 400
                    && location.toLowerCase(Locale.ROOT).contains("login")) {
                return "Server session expired";
            }
            if (!response.isSuccessful() || response.body() == null) {
                return "Server session check failed: " + response.code();
            }
            String html = response.body().string();
            if (isLoginPage(response, html)) {
                return "Server session expired";
            }
            Log.d(TAG, "Server accepted session: HTTP " + response.code());
            return null;
        }
    }

    static List<ExamData> parse(String html) {
        List<ExamData> exams = new ArrayList<>();
        Elements tables = Jsoup.parse(html).select("table");
        for (Element table : tables) {
            Elements rows = table.select("tr");
            if (rows.size() < 2) continue;

            Elements headers = rows.first().select("th, td");
            int codeIndex = findColumn(headers, "course code", "code");
            int titleIndex = findColumn(headers, "course title", "subject");
            int dateIndex = findColumn(headers, "exam date", "date");
            int timeIndex = findColumn(headers, "exam time", "time");
            int roomIndex = findColumn(headers, "room", "room no", "room number", "venue");

            // The current Amizone response uses this stable order even when labels vary.
            if (codeIndex < 0 && titleIndex < 0 && dateIndex < 0 && timeIndex < 0) {
                codeIndex = 0;
                titleIndex = 1;
                dateIndex = 2;
                timeIndex = 3;
            }

            for (int rowIndex = 1; rowIndex < rows.size(); rowIndex++) {
                Elements cells = rows.get(rowIndex).select("td, th");
                if (cells.isEmpty()) continue;
                String code = cell(cells, codeIndex);
                String title = cell(cells, titleIndex);
                String date = cell(cells, dateIndex);
                String time = cell(cells, timeIndex);
                String room = roomIndex >= 0 ? cell(cells, roomIndex) : findRoom(cells);
                if (code.isEmpty() && title.isEmpty()) continue;
                exams.add(new ExamData(code, title, date, time, room.isEmpty() ? "Not announced" : room));
            }
            if (!exams.isEmpty()) return exams;
        }
        return exams;
    }

    private static boolean isLoginPage(Response response, String html) {
        String path = response.request().url().encodedPath().toLowerCase(Locale.ROOT);
        String normalized = normalize(html);
        return path.contains("login")
                || normalized.contains("name=\"username\"")
                || normalized.contains("id=\"username\"")
                || normalized.contains("login to amizone")
                || normalized.contains("forgot password")
                || normalized.contains("captcha");
    }

    private static int findColumn(Elements headers, String... names) {
        for (int i = 0; i < headers.size(); i++) {
            String header = normalize(headers.get(i).text());
            for (String name : names) {
                if (header.equals(normalize(name))) return i;
            }
        }
        for (int i = 0; i < headers.size(); i++) {
            String header = normalize(headers.get(i).text());
            for (String name : names) {
                if (header.contains(normalize(name))) return i;
            }
        }
        return -1;
    }

    private static String cell(Elements cells, int index) {
        return index >= 0 && index < cells.size() ? cells.get(index).text().trim() : "";
    }

    private static String findRoom(Elements cells) {
        for (Element cell : cells) {
            String text = cell.text().trim();
            String dataTitle = normalize(cell.attr("data-title"));
            if (dataTitle.startsWith("room") || dataTitle.startsWith("venue")) {
                return text;
            }
            Matcher matcher = Pattern.compile("(?:room|venue)\\s*(?:number|no\\.?)?\\s*:\\s*([^|,;]+)", Pattern.CASE_INSENSITIVE)
                    .matcher(text);
            if (matcher.find()) {
                return matcher.group(1).trim();
            }
        }
        return "";
    }

    private static String normalize(String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }
}