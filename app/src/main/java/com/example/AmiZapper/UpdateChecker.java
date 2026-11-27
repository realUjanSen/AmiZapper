package com.example.amizapper;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

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
        ProgressDialog checking = new ProgressDialog(activity);
        checking.setMessage("Checking for updates...");
        checking.setCancelable(false);
        checking.show();

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
                        finish(activity, checking, "Update check failed",
                                "Could not reach GitHub (HTTP " + response.code() + ").");
                        return;
                    }
                    JsonObject release = JsonParser.parseString(response.body().string()).getAsJsonObject();
                    latestTag = release.get("tag_name").getAsString();
                    apkUrl = findApkUrl(release.getAsJsonArray("assets"));
                }

                String installed = getInstalledVersion(activity);
                String latest = latestTag.startsWith("v") ? latestTag.substring(1) : latestTag;

                if (apkUrl == null) {
                    finish(activity, checking, "Update check failed", "The latest release has no APK.");
                    return;
                }
                if (compareVersions(latest, installed) <= 0) {
                    finish(activity, checking, "Up to date", "You are on the latest version (" + installed + ").");
                    return;
                }

                activity.runOnUiThread(() -> {
                    checking.dismiss();
                    askToUpdate(activity, latest, apkUrl);
                });

            } catch (Exception e) {
                finish(activity, checking, "Update check failed", String.valueOf(e.getMessage()));
            }
        }).start();
    }

    private static void askToUpdate(Activity activity, String newVersion, String apkUrl) {
        if (activity.isFinishing()) return;
        new AlertDialog.Builder(activity)
                .setTitle("Update available")
                .setMessage("Version " + newVersion + " is available. Download and install it now?")
                .setPositiveButton("Update", (dialog, which) -> downloadAndInstall(activity, apkUrl))
                .setNegativeButton("Later", null)
                .show();
    }

    private static void downloadAndInstall(Activity activity, String apkUrl) {
        ProgressDialog progress = new ProgressDialog(activity);
        progress.setTitle("Downloading update");
        progress.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        progress.setMax(100);
        progress.setProgress(0);
        progress.setProgressNumberFormat(null);
        progress.setMessage("0%");
        progress.setCancelable(false);
        progress.show();

        new Thread(() -> {
            try {
                File apk = downloadApk(activity, apkUrl, percent -> activity.runOnUiThread(() -> {
                    progress.setProgress(percent);
                    progress.setMessage(percent + "%");
                }));
                activity.runOnUiThread(() -> {
                    progress.dismiss();
                    installApk(activity, apk);
                });
            } catch (Exception e) {
                activity.runOnUiThread(progress::dismiss);
                finish(activity, null, "Download failed", String.valueOf(e.getMessage()));
            }
        }).start();
    }

    interface ProgressListener {
        void onProgress(int percent);
    }

    private static File downloadApk(Context context, String url, ProgressListener listener) throws Exception {
        File dir = new File(context.getCacheDir(), "updates");
        if (!dir.exists()) dir.mkdirs();
        File apk = new File(dir, "AmiZapper-update.apk");

        Request request = new Request.Builder().url(url).build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                throw new Exception("Server returned HTTP " + response.code());
            }
            long total = response.body().contentLength();
            long downloaded = 0;
            int lastPercent = -1;

            try (InputStream in = response.body().byteStream();
                 FileOutputStream out = new FileOutputStream(apk)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                    downloaded += read;
                    if (total > 0) {
                        int percent = (int) (downloaded * 100 / total);
                        if (percent != lastPercent) {
                            lastPercent = percent;
                            listener.onProgress(percent);
                        }
                    }
                }
            }
        }
        return apk;
    }

    private static void installApk(Activity activity, File apk) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                && !activity.getPackageManager().canRequestPackageInstalls()) {
            new AlertDialog.Builder(activity)
                    .setTitle("Allow installs")
                    .setMessage("Allow AmiZapper to install apps, then tap Update again.")
                    .setPositiveButton("Open settings", (dialog, which) -> {
                        Intent settings = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                Uri.parse("package:" + activity.getPackageName()));
                        activity.startActivity(settings);
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
            return;
        }

        Uri uri = FileProvider.getUriForFile(activity,
                activity.getPackageName() + ".fileprovider", apk);
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(uri, "application/vnd.android.package-archive");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
        activity.startActivity(intent);
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

    // Shows a message dialog on the UI thread, dismissing an optional progress dialog first.
    private static void finish(Activity activity, ProgressDialog dialogToClose, String title, String message) {
        activity.runOnUiThread(() -> {
            if (dialogToClose != null) dialogToClose.dismiss();
            if (activity.isFinishing()) return;
            new AlertDialog.Builder(activity)
                    .setTitle(title)
                    .setMessage(message)
                    .setPositiveButton("OK", null)
                    .show();
        });
    }
}
