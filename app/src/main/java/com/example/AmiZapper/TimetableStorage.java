package com.example.amizapper;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Manages persistent storage of timetable data
 */
public class TimetableStorage {
    private static final String PREFS_NAME = "TimetableData";
    private static final String KEY_TIMETABLE = "timetable_classes";
    private static final String KEY_CACHE_VERSION = "cache_version";
    // Bump this when TimetableClass fields change to force a cache wipe
    private static final int CURRENT_CACHE_VERSION = 3; // bumped: force wipe of stale attendance colors

    /**
     * Save timetable to persistent storage (replaces all data)
     */
    public static void saveTimetable(Context context, List<TimetableClass> classes) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        Gson gson = new Gson();
        String json = gson.toJson(classes);
        prefs.edit()
                .putString(KEY_TIMETABLE, json)
                .putInt(KEY_CACHE_VERSION, CURRENT_CACHE_VERSION)
                .apply();
    }

    /**
     * Merge new timetable data with existing data
     * Uses date as key - new data for a date replaces old data for that date
     */
    public static void mergeTimetable(Context context, List<TimetableClass> newClasses) {
        List<TimetableClass> existing = loadTimetable(context);

        // Build a map of existing classes by date+time+courseCode for deduplication
        Map<String, TimetableClass> classMap = new HashMap<>();

        // Add existing classes
        for (TimetableClass cls : existing) {
            String key = buildKey(cls);
            classMap.put(key, cls);
        }

        // Add/update with new classes (new data takes precedence)
        for (TimetableClass cls : newClasses) {
            String key = buildKey(cls);
            classMap.put(key, cls);
        }

        // Save merged list (only entries with valid dates)
        List<TimetableClass> merged = new ArrayList<>();
        for (TimetableClass cls : classMap.values()) {
            if (cls.getDate() != null && !cls.getDate().isEmpty()) {
                merged.add(cls);
            }
        }
        saveTimetable(context, merged);
    }

    private static String buildKey(TimetableClass cls) {
        // Use date if available, otherwise use dayOfWeek
        String dateKey = cls.getDate() != null ? cls.getDate() : cls.getDayOfWeek();
        return dateKey + "|" + cls.getStartTime() + "|" + cls.getCourseCode();
    }

    /**
     * Load timetable from persistent storage
     */
    public static List<TimetableClass> loadTimetable(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        // Check cache version — wipe if stale schema
        int savedVersion = prefs.getInt(KEY_CACHE_VERSION, 0);
        if (savedVersion != CURRENT_CACHE_VERSION) {
            prefs.edit().remove(KEY_TIMETABLE).putInt(KEY_CACHE_VERSION, CURRENT_CACHE_VERSION).apply();
            return new ArrayList<>();
        }

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

    /**
     * Remove old cached data that doesn't have date information
     * This ensures widget only shows accurate date-specific data
     */
    public static void cleanupOldData(Context context) {
        List<TimetableClass> all = loadTimetable(context);
        List<TimetableClass> withDates = new ArrayList<>();

        for (TimetableClass cls : all) {
            if (cls.getDate() != null && !cls.getDate().isEmpty()) {
                withDates.add(cls);
            }
        }

        // Only save if we removed something
        if (withDates.size() < all.size()) {
            saveTimetable(context, withDates);
        }
    }
}
