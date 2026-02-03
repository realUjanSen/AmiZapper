package com.example.amizapper;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;
import android.widget.Toast;

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
        webView.loadUrl("https://s.amizone.net");
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
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return false;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                progressBar.setVisibility(View.GONE);

                // Auto-fill credentials if on login page
                if (url.contains("amizone.net") && !url.contains("/Home")) {
                    autoFillCredentials();
                }

                // Check if we reached Home page
                if (url.contains("/Home")) {
                    extractAndSaveCookies();
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
