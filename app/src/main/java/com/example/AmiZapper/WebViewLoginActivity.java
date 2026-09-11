package com.example.amizapper;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;
import android.widget.Toast;

import java.io.ByteArrayInputStream;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;

public class WebViewLoginActivity extends AppCompatActivity {

    private WebView webView;
    private ProgressBar progressBar;
    private com.example.amizapper.CookieManager appCookieManager;
    private String username;
    private String password;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_webview_login);

        appCookieManager = new com.example.amizapper.CookieManager(this);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle("Login to Amizone");
        toolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material);
        toolbar.setNavigationOnClickListener(v -> finish());

        webView = findViewById(R.id.webView);
        progressBar = findViewById(R.id.progressBar);

        username = getIntent().getStringExtra("username");
        password = getIntent().getStringExtra("password");

        setupWebView();
        setupBackPress();
        webView.loadUrl("https://s.amizone.net/");
    }

    private void setupBackPress() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (webView.canGoBack()) {
                    webView.goBack();
                } else {
                    finish();
                }
            }
        });
    }

    private void setupWebView() {
        // Clear any existing cookies to start fresh
        CookieManager webCookieManager = CookieManager.getInstance();
        webCookieManager.removeAllCookies(null);
        webCookieManager.flush();

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                if ("img.amizone.net".equalsIgnoreCase(uri.getHost())
                        && "/AzureImageNew.ashx".equalsIgnoreCase(uri.getPath())
                        && "7".equals(uri.getQueryParameter("Type"))) {
                    return new WebResourceResponse(
                            "image/png",
                            null,
                            new ByteArrayInputStream(new byte[0])
                    );
                }
                return super.shouldInterceptRequest(view, request);
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return false;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                progressBar.setVisibility(View.GONE);

                android.util.Log.d("WebViewLogin", "Page loaded: " + url);

                // Check for "Unauthorized" message on the page
                view.evaluateJavascript("(function() { return document.body.innerText; })()", value -> {
                    if (value != null && value.toLowerCase().contains("unauthorized")) {
                        android.util.Log.w("WebViewLogin", "Detected 'Unauthorized' message on page");
                        // The page might be showing an error but will redirect - just log it
                    }
                });

                // Auto-fill credentials if on login page (with slight delay to ensure page is ready)
                if (url.contains("amizone.net") && !url.contains("/Home")) {
                    view.postDelayed(() -> autoFillCredentials(), 500);
                }

                // Check if we reached Home page
                if (url.contains("/Home")) {
                    extractAndSaveCookies();
                    extractAttendanceFromPage(view);
                }
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                if (newProgress < 100) {
                    progressBar.setVisibility(View.VISIBLE);
                } else {
                    progressBar.setVisibility(View.GONE);
                }
            }
        });
    }

    private void autoFillCredentials() {
        // JavaScript to auto-fill username and password
        String js = "javascript:(function() {" +
                "  var usernameField = document.querySelector('input[name=\"_UserName\"]') || document.querySelector('input[type=\"text\"]');" +
                "  var passwordField = document.querySelector('input[name=\"_Password\"]') || document.querySelector('input[type=\"password\"]');" +
                "  if (usernameField) usernameField.value = '" + username + "';" +
                "  if (passwordField) passwordField.value = '" + password + "';" +
                "})()";

        webView.evaluateJavascript(js, null);
    }

    private void extractAttendanceFromPage(WebView view) {
        String js = "(function() {" +
                "  var items = document.querySelectorAll('#tasks li');" +
                "  var result = [];" +
                "  for(var i=0; i<items.length; i++) {" +
                "    var item = items[i];" +
                "    var codeEl = item.querySelector('.sub-code');" +
                "    var lblEl = item.querySelector('.lbl');" +
                "    var pctEl = item.querySelector('.percentage');" +
                "    var countEl = item.querySelector('.class-count span');" +
                "    if(codeEl && lblEl && pctEl && countEl) {" +
                "      var code = codeEl.textContent.trim();" +
                "      var fullText = lblEl.textContent.trim();" +
                "      var name = fullText.substring(code.length).trim();" +
                "      var pct = pctEl.getAttribute('data-percent');" +
                "      var count = countEl.textContent.trim();" +
                "      result.push(code + '|' + name + '|' + pct + '|' + count);" +
                "    }" +
                "  }" +
                "  return result.join(';;');" +
                "})()";

        view.evaluateJavascript(js, value -> {
            if (value != null && !value.equals("null") && !value.equals("\"\"")) {
                // Remove quotes from JavaScript string result
                String data = value.replace("\"", "");
                android.util.Log.d("WebViewLogin", "Extracted attendance data: " + data);

                // Parse and save attendance
                java.util.List<AttendanceData> attendanceList = new java.util.ArrayList<>();
                String[] items = data.split(";;");
                for (String item : items) {
                    String[] parts = item.split("\\|");
                    if (parts.length == 4) {
                        try {
                            String code = parts[0];
                            String name = parts[1];
                            double percent = Double.parseDouble(parts[2]);
                            String[] countParts = parts[3].split("/");
                            int attended = Integer.parseInt(countParts[0].trim());
                            int total = Integer.parseInt(countParts[1].trim());

                            attendanceList.add(new AttendanceData(code, name, percent, attended, total));
                            android.util.Log.d("WebViewLogin", "Parsed: " + code + " - " + percent + "% (" + attended + "/" + total + ")");
                        } catch (Exception e) {
                            android.util.Log.e("WebViewLogin", "Failed to parse item: " + item, e);
                        }
                    }
                }

                if (!attendanceList.isEmpty()) {
                    AttendanceStorage.saveAttendance(WebViewLoginActivity.this, attendanceList);
                    android.util.Log.d("WebViewLogin", "Saved " + attendanceList.size() + " attendance records from WebView");
                }
            }
        });
    }

    private void extractAndSaveCookies() {
        CookieManager webCookieManager = CookieManager.getInstance();
        String cookiesString = webCookieManager.getCookie("https://s.amizone.net");

        if (cookiesString != null) {
            String[] cookies = cookiesString.split(";");
            String sessionId = null;
            String auth = null;
            String verificationToken = null;

            for (String cookie : cookies) {
                cookie = cookie.trim();
                if (cookie.startsWith("ASP.NET_SessionId=")) {
                    sessionId = cookie.substring("ASP.NET_SessionId=".length());
                } else if (cookie.startsWith(".ASPXAUTH=")) {
                    auth = cookie.substring(".ASPXAUTH=".length());
                } else if (cookie.startsWith("__RequestVerificationToken=")) {
                    verificationToken = cookie.substring("__RequestVerificationToken=".length());
                }
            }

            if (sessionId != null && auth != null) {
                appCookieManager.saveCookies(sessionId, auth, verificationToken);

                Toast.makeText(this, "Login successful! 🎉", Toast.LENGTH_SHORT).show();

                // Go to MainActivity
                Intent intent = new Intent(WebViewLoginActivity.this, MainActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
                finish();
            }
        }
    }
}
