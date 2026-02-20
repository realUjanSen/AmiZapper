package com.example.amizapper;
import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;
public class AttendanceStorage {
    private static final String PREFS_NAME = "AttendancePrefs";
    private static final String KEY_DATA = "attendance_data";
    public static void saveAttendance(Context context, List<AttendanceData> list) {
        try {
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            JSONArray arr = new JSONArray();
            for (AttendanceData d : list) {
                JSONObject obj = new JSONObject();
                obj.put("code", d.getCourseCode());
                obj.put("name", d.getSubjectName());
                obj.put("pct", d.getPercentage());
                obj.put("att", d.getClassesAttended());
                obj.put("tot", d.getTotalClasses());
                arr.put(obj);
            }
            prefs.edit().putString(KEY_DATA, arr.toString()).apply();
        } catch (Exception e) { }
    }
    public static List<AttendanceData> loadAttendance(Context context) {
        List<AttendanceData> list = new ArrayList<>();
        try {
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            String json = prefs.getString(KEY_DATA, null);
            if (json != null) {
                JSONArray arr = new JSONArray(json);
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject obj = arr.getJSONObject(i);
                    list.add(new AttendanceData(
                        obj.getString("code"),
                        obj.getString("name"),
                        obj.getDouble("pct"),
                        obj.getInt("att"),
                        obj.getInt("tot")
                    ));
                }
            }
        } catch (Exception e) { }
        return list;
    }
    public static AttendanceData getForCourse(Context context, String courseCode) {
        for (AttendanceData d : loadAttendance(context)) {
            if (d.getCourseCode().trim().equalsIgnoreCase(courseCode.trim())) return d;
        }
        return null;
    }
}
