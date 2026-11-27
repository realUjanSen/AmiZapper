package com.example.amizapper;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class UpdateChecker {

    private static final String LATEST_RELEASE_URL =
            "https://api.github.com/repos/realUjanSen/AmiZapper/releases/latest";

    private static final OkHttpClient client = new OkHttpClient();

    public static void checkAndUpdate(Activity activity) {
        Toast.makeText(activity, "Checking for updates...", Toast.LENGTH_SHORT).show();

        new Thread(() -> {
            try {
                Request request = new Request.Builder()
                        .url(LATEST_RELEASE_URL)
                        .header("Accept", "application/vnd.github+json")
                        .header("User-Agent", "AmiZapper-Updater")
                        .build();

                String latestTag;
                String apkUrl;
                try (Response response = client.newCall(request).execute()) {
                    if (!response.isSuccessful() || response.body() == null) {
                        showToast(activity, "Update check failed (HTTP " + response.code() + ")");
                        return;
                    }
                    JsonObject release = JsonParser.parseString(response.body().string()).getAsJsonObject();
                    latestTag = release.get("tag_name").getAsString();
                    apkUrl = findApkUrl(release.getAsJsonArray("assets"));
                }

                String installed = getInstalledVersion(activity);
                String latest = latestTag.startsWith("v") ? latestTag.substring(1) : latestTag;

                if (apkUrl == null) {
                    showToast(activity, "Latest release has no APK");
                    return;
                }
                if (compareVersions(latest, installed) <= 0) {
                    showToast(activity, "You're up to date (" + installed + ")");
                    return;
                }

                showToast(activity, "Downloading " + latestTag + "...");
                File apk = downloadApk(activity, apkUrl);
                activity.runOnUiThread(() -> installApk(activity, apk));

            } catch (Exception e) {
                showToast(activity, "Update failed: " + e.getMessage());
            }
        }).start();
    }

    private static String findApkUrl(JsonArray assets) {
        if (assets == null) return null;
        for (int i = 0; i < assets.size(); i++) {
            JsonObject asset = assets.get(i).getAsJsonObject();
            String name = asset.get("name").getAsString();
            if (name.endsWith(".apk")) {
                return asset.get("browser_download_url").getAsString();
            }
        }
        return null;
    }

    private static File downloadApk(Context context, String url) throws Exception {
        File dir = new File(context.getCacheDir(), "updates");
        if (!dir.exists()) dir.mkdirs();
        File apk = new File(dir, "AmiZapper-update.apk");

        Request request = new Request.Builder().url(url).build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                throw new Exception("download HTTP " + response.code());
            }
            try (InputStream in = response.body().byteStream();
                 FileOutputStream out = new FileOutputStream(apk)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                }
            }
        }
        return apk;
    }

    private static void installApk(Activity activity, File apk) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                && !activity.getPackageManager().canRequestPackageInstalls()) {
            Toast.makeText(activity,
                    "Allow installs from this app, then tap Update again",
                    Toast.LENGTH_LONG).show();
            Intent settings = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:" + activity.getPackageName()));
            activity.startActivity(settings);
            return;
        }

        Uri uri = FileProvider.getUriForFile(activity,
                activity.getPackageName() + ".fileprovider", apk);
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(uri, "application/vnd.android.package-archive");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
        activity.startActivity(intent);
    }

    static String getInstalledVersion(Context context) throws Exception {
        PackageInfo info = context.getPackageManager()
                .getPackageInfo(context.getPackageName(), 0);
        return info.versionName;
    }

    // Returns >0 if a is newer than b, 0 if equal, <0 if older. Compares numeric parts.
    static int compareVersions(String a, String b) {
        String[] pa = a.split("\\.");
        String[] pb = b.split("\\.");
        int n = Math.max(pa.length, pb.length);
        for (int i = 0; i < n; i++) {
            int va = i < pa.length ? parseSafe(pa[i]) : 0;
            int vb = i < pb.length ? parseSafe(pb[i]) : 0;
            if (va != vb) return Integer.compare(va, vb);
        }
        return 0;
    }

    private static int parseSafe(String s) {
        try {
            return Integer.parseInt(s.replaceAll("[^0-9]", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static void showToast(Activity activity, String message) {
        activity.runOnUiThread(() -> Toast.makeText(activity, message, Toast.LENGTH_LONG).show());
    }
}
