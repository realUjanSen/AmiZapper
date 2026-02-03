package com.example.amizapper;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Cookie manager for storing session cookies
 */
public class CookieManager {
    private static final String PREFS_NAME = "AmizoneCookies";
    private static final String KEY_SESSION_ID = "ASP.NET_SessionId";
    private static final String KEY_AUTH = ".ASPXAUTH";
    private static final String KEY_VERIFICATION_TOKEN = "__RequestVerificationToken";

    private SharedPreferences prefs;

    public CookieManager(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public void saveCookies(String sessionId, String auth, String verificationToken) {
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(KEY_SESSION_ID, sessionId);
        editor.putString(KEY_AUTH, auth);
        editor.putString(KEY_VERIFICATION_TOKEN, verificationToken);
        editor.apply();
    }

    public String getSessionId() {
        return prefs.getString(KEY_SESSION_ID, null);
    }

    public String getAuth() {
        return prefs.getString(KEY_AUTH, null);
    }

    public String getVerificationToken() {
        return prefs.getString(KEY_VERIFICATION_TOKEN, null);
    }

    public boolean hasCookies() {
        return getSessionId() != null && getAuth() != null;
    }

    public void clearCookies() {
        prefs.edit().clear().apply();
    }

    public String getCookieString() {
        if (!hasCookies()) {
            return null;
        }

        StringBuilder cookieBuilder = new StringBuilder();

        if (getSessionId() != null) {
            cookieBuilder.append(KEY_SESSION_ID).append("=").append(getSessionId()).append("; ");
        }

        if (getAuth() != null) {
            cookieBuilder.append(KEY_AUTH).append("=").append(getAuth()).append("; ");
        }

        if (getVerificationToken() != null) {
            cookieBuilder.append(KEY_VERIFICATION_TOKEN).append("=").append(getVerificationToken());
        }

        return cookieBuilder.toString();
    }
}
