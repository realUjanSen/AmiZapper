package com.example.amizapper;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.core.content.FileProvider;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Locale;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class UpdateChecker {

    private static final String LATEST_RELEASE_URL =
            "https://api.github.com/repos/realUjanSen/AmiZapper/releases/latest";

    private static final OkHttpClient client = new OkHttpClient();

    // One dialog for the whole flow: checking, prompt, progress, errors.
    private static class UpdateDialog {
        final Activity activity;
        final AlertDialog dialog;
        final TextView message;
        final ProgressBar bar;
        final TextView percent;
        Runnable positiveAction;

        UpdateDialog(Activity activity) {
            this.activity = activity;
            float density = activity.getResources().getDisplayMetrics().density;
            int pad = (int) (20 * density);

            LinearLayout root = new LinearLayout(activity);
            root.setOrientation(LinearLayout.VERTICAL);
            root.setPadding(pad, pad, pad, pad);

            message = new TextView(activity);
            message.setTextSize(16);
            root.addView(message);

            bar = new ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal);
            bar.setMax(100);
            bar.setVisibility(View.GONE);
            LinearLayout.LayoutParams barParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            barParams.topMargin = (int) (16 * density);
            root.addView(bar, barParams);

            percent = new TextView(activity);
            percent.setTextSize(14);
            percent.setVisibility(View.GONE);
            LinearLayout.LayoutParams percentParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            percentParams.topMargin = (int) (8 * density);
            root.addView(percent, percentParams);

            dialog = new AlertDialog.Builder(activity)
                    .setTitle("AmiZapper Update")
                    .setView(root)
                    .setPositiveButton("OK", null)
                    .setNegativeButton("Later", null)
                    .create();
            dialog.setCanceledOnTouchOutside(false);
            dialog.show();
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                if (positiveAction != null) positiveAction.run();
            });
        }

        void setButtons(boolean showPositive, boolean showNegative) {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                    .setVisibility(showPositive ? View.VISIBLE : View.GONE);
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
                    .setVisibility(showNegative ? View.VISIBLE : View.GONE);
        }

        void checking() {
            dialog.setCancelable(false);
            message.setText("Checking for updates...");
            bar.setIndeterminate(true);
            bar.setVisibility(View.VISIBLE);
            percent.setVisibility(View.GONE);
            setButtons(false, false);
        }

        void prompt(String text, String positiveLabel, Runnable onPositive) {
            dialog.setCancelable(true);
            message.setText(text);
            bar.setVisibility(View.GONE);
            percent.setVisibility(View.GONE);
            positiveAction = onPositive;
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setText(positiveLabel);
            setButtons(true, true);
        }

        void downloading(long totalBytes) {
            dialog.setCancelable(false);
            message.setText("Downloading update (" + mb(totalBytes) + ")...");
            bar.setIndeterminate(false);
            bar.setProgress(0);
            bar.setVisibility(View.VISIBLE);
            percent.setText("0%");
            percent.setVisibility(View.VISIBLE);
            setButtons(false, false);
        }

        void progress(long downloadedBytes, long totalBytes) {
            int p = totalBytes > 0 ? (int) (downloadedBytes * 100 / totalBytes) : 0;
            bar.setProgress(p);
            percent.setText(p + "%  (" + mb(downloadedBytes) + " / " + mb(totalBytes) + ")");
        }

        void error(String title, String text) {
            dialog.setTitle(title);
            dialog.setCancelable(true);
            message.setText(text);
            bar.setVisibility(View.GONE);
            percent.setVisibility(View.GONE);
            positiveAction = dialog::dismiss;
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setText("OK");
            setButtons(true, false);
        }

        void close() {
            dialog.dismiss();
        }
    }

    public static void checkAndUpdate(Activity activity) {
        UpdateDialog ui = new UpdateDialog(activity);
        ui.checking();

        new Thread(() -> {
            try {
                Request request = new Request.Builder()
                        .url(LATEST_RELEASE_URL)
                        .header("Accept", "application/vnd.github+json")
                        .header("User-Agent", "AmiZapper-Updater")
                        .build();

                String latestTag;
                String apkUrl;
                long apkSize;
                try (Response response = client.newCall(request).execute()) {
                    if (!response.isSuccessful() || response.body() == null) {
                        showError(activity, ui, "Update check failed",
                                "Could not reach GitHub (HTTP " + response.code() + ").");
                        return;
                    }
                    JsonObject release = JsonParser.parseString(response.body().string()).getAsJsonObject();
                    latestTag = release.get("tag_name").getAsString();
                    JsonObject apk = findApkAsset(release.getAsJsonArray("assets"));
                    if (apk == null) {
                        showError(activity, ui, "Update check failed", "The latest release has no APK.");
                        return;
                    }
                    apkUrl = apk.get("browser_download_url").getAsString();
                    apkSize = apk.get("size").getAsLong();
                }

                String installed = getInstalledVersion(activity);
                String latest = latestTag.startsWith("v") ? latestTag.substring(1) : latestTag;
                int cmp = compareVersions(latest, installed);

                String text;
                if (cmp > 0) {
                    text = "Version " + latest + " is available. Download and install it now?";
                } else if (cmp == 0) {
                    text = "You already have version " + installed + ". Reinstall it?";
                } else {
                    text = "The latest release is " + latest + ", which is older than your installed "
                            + installed + ". Install it anyway?";
                }
                text += "\n\nDownload size: " + mb(apkSize);

                String finalText = text;
                long finalSize = apkSize;
                String finalUrl = apkUrl;
                activity.runOnUiThread(() -> ui.prompt(finalText, "Update",
                        () -> download(activity, ui, finalUrl, finalSize)));

            } catch (Exception e) {
                showError(activity, ui, "Update check failed", String.valueOf(e.getMessage()));
            }
        }).start();
    }

    private static void download(Activity activity, UpdateDialog ui, String apkUrl, long apkSize) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                && !activity.getPackageManager().canRequestPackageInstalls()) {
            ui.prompt("Allow AmiZapper to install apps, then tap Update again.",
                    "Open settings", () -> {
                        ui.close();
                        activity.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                Uri.parse("package:" + activity.getPackageName())));
                    });
            return;
        }

        ui.downloading(apkSize);

        new Thread(() -> {
            try {
                File apk = downloadApk(activity, apkUrl, apkSize,
                        (done, total) -> activity.runOnUiThread(() -> ui.progress(done, total)));
                activity.runOnUiThread(() -> {
                    ui.close();
                    installApk(activity, apk);
                });
            } catch (Exception e) {
                showError(activity, ui, "Download failed", String.valueOf(e.getMessage()));
            }
        }).start();
    }

    interface ProgressListener {
        void onProgress(long downloadedBytes, long totalBytes);
    }

    private static File downloadApk(Context context, String url, long expectedSize,
                                    ProgressListener listener) throws Exception {
        File dir = new File(context.getCacheDir(), "updates");
        if (!dir.exists()) dir.mkdirs();
        File apk = new File(dir, "AmiZapper-update.apk");

        Request request = new Request.Builder().url(url).build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                throw new Exception("Server returned HTTP " + response.code());
            }
            long total = expectedSize > 0 ? expectedSize : response.body().contentLength();
            long downloaded = 0;
            int lastPercent = -1;

            try (InputStream in = response.body().byteStream();
                 FileOutputStream out = new FileOutputStream(apk)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                    downloaded += read;
                    int percent = total > 0 ? (int) (downloaded * 100 / total) : 0;
                    if (percent != lastPercent) {
                        lastPercent = percent;
                        listener.onProgress(downloaded, total);
                    }
                }
            }
        }
        return apk;
    }

    private static void installApk(Activity activity, File apk) {
        Uri uri = FileProvider.getUriForFile(activity,
                activity.getPackageName() + ".fileprovider", apk);
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(uri, "application/vnd.android.package-archive");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
        activity.startActivity(intent);
    }

    private static JsonObject findApkAsset(JsonArray assets) {
        if (assets == null) return null;
        for (int i = 0; i < assets.size(); i++) {
            JsonObject asset = assets.get(i).getAsJsonObject();
            if (asset.get("name").getAsString().endsWith(".apk")) {
                return asset;
            }
        }
        return null;
    }

    static String getInstalledVersion(Context context) throws Exception {
        PackageInfo info = context.getPackageManager()
                .getPackageInfo(context.getPackageName(), 0);
        return info.versionName;
    }

    // Returns >0 if a is newer than b, 0 if equal, <0 if older.
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

    private static String mb(long bytes) {
        return String.format(Locale.US, "%.1f MB", bytes / 1024.0 / 1024.0);
    }

    private static void showError(Activity activity, UpdateDialog ui, String title, String text) {
        activity.runOnUiThread(() -> ui.error(title, text));
    }
}
