package com.example.amizapper;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public final class ExamStorage {
    private static final String PREFS_NAME = "ExamData";
    private static final String KEY_EXAMS = "exams";

    private ExamStorage() { }

    public static void save(Context context, List<ExamData> exams) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_EXAMS, new Gson().toJson(exams))
                .apply();
    }

    public static List<ExamData> load(Context context) {
        String json = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_EXAMS, null);
        if (json == null) return new ArrayList<>();
        Type type = new TypeToken<List<ExamData>>() { }.getType();
        List<ExamData> exams = new Gson().fromJson(json, type);
        return exams != null ? exams : new ArrayList<>();
    }
}