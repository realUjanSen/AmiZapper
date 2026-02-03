package com.example.amizapper;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

/**
 * Manages persistent storage of timetable data
 */
public class TimetableStorage {
    private static final String PREFS_NAME = "TimetableData";
    private static final String KEY_TIMETABLE = "timetable_classes";

    /**
     * Save timetable to persistent storage
     */
    public static void saveTimetable(Context context, List<TimetableClass> classes) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        Gson gson = new Gson();
        String json = gson.toJson(classes);
        prefs.edit().putString(KEY_TIMETABLE, json).apply();
    }

    /**
     * Load timetable from persistent storage
     */
    public static List<TimetableClass> loadTimetable(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String json = prefs.getString(KEY_TIMETABLE, null);

        if (json == null) {
            return new ArrayList<>();
        }

        Gson gson = new Gson();
        Type type = new TypeToken<List<TimetableClass>>(){}.getType();
        List<TimetableClass> classes = gson.fromJson(json, type);

        return classes != null ? classes : new ArrayList<>();
    }

    /**
     * Clear all timetable data
     */
    public static void clearTimetable(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().remove(KEY_TIMETABLE).apply();
    }
}
