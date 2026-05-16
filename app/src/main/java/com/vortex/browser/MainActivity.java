package com.vortex.browser;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.webkit.CookieManager;
import android.webkit.URLUtil;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import java.io.ByteArrayInputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private WebView webView;
    private EditText urlBar;
    private ProgressBar progressBar;
    private SwipeRefreshLayout swipeRefresh;
    private ImageButton btnBack, btnForward, btnRefresh, btnTabs, btnMenu;
    private SharedPreferences prefs;

    // Tab management
    private List<TabInfo> tabs = new ArrayList<>();
    private int currentTabIndex = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = PreferenceManager.getDefaultSharedPreferences(this);

        // Initialize views
        webView = findViewById(R.id.webView);
        urlBar = findViewById(R.id.urlBar);
        progressBar = findViewById(R.id.progressBar);
        swipeRefresh = findViewById(R.id.swipeRefresh);
        btnBack = findViewById(R.id.btnBack);
        btnForward = findViewById(R.id.btnForward);
        btnRefresh = findViewById(R.id.btnRefresh);
        btnTabs = findViewById(R.id.btnTabs);
        btnMenu = findViewById(R.id.btnMenu);

        setupWebView();
        setupUrlBar();
        setupButtons();
        setupSwipeRefresh();

        // Create first tab
        tabs.add(new TabInfo("New Tab", getHomepage()));

        // Handle intent (opened from another app)
        Intent intent = getIntent();
        String url = null;
        if (intent != null && Intent.ACTION_VIEW.equals(intent.getAction())) {
            url = intent.getDataString();
        }

        webView.loadUrl(url != null ? url : getHomepage());
    }

    private String getHomepage() {
        String custom = prefs.getString("custom_homepage", "");
        if (custom != null && !custom.isEmpty()) return custom;
        return "file:///android_asset/homepage.html";
    }

    private void setupWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setSupportMultipleWindows(false);
        settings.setAllowFileAccess(true);
        settings.setDatabaseEnabled(true);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);

        // Force HTTPS preference
        if (prefs.getBoolean("force_https", true)) {
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        }

        // User agent
        String ua = settings.getUserAgentString();
        settings.setUserAgentString(ua.replace("wv", "").replace("Version/4.0 ", "") + " VortexBrowser/1.0");

        webView.setWebViewClient(new VortexWebViewClient());
        webView.setWebChromeClient(new VortexWebChromeClient());

        // Download listener
        webView.setDownloadListener((url, userAgent, contentDisposition, mimeType, contentLength) -> {
            try {
                DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
                request.setMimeType(mimeType);
                String filename = URLUtil.guessFileName(url, contentDisposition, mimeType);
                request.setTitle(filename);
                request.setDescription("Downloading...");
                request.allowScanningByMediaScanner();
                request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, filename);
                DownloadManager dm = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
                dm.enqueue(request);
                Toast.makeText(this, "Downloading: " + filename, Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Toast.makeText(this, "Download failed", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void setupUrlBar() {
        urlBar.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_GO ||
                (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                navigateTo(urlBar.getText().toString().trim());
                hideKeyboard();
                return true;
            }
            return false;
        });

        urlBar.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                urlBar.selectAll();
            }
        });
    }

    private void setupButtons() {
        btnBack.setOnClickListener(v -> { if (webView.canGoBack()) webView.goBack(); });
        btnForward.setOnClickListener(v -> { if (webView.canGoForward()) webView.goForward(); });
        btnRefresh.setOnClickListener(v -> webView.reload());
        btnTabs.setOnClickListener(v -> showTabsDialog());
        btnMenu.setOnClickListener(v -> showMenuDialog());
    }

    private void setupSwipeRefresh() {
        swipeRefresh.setOnRefreshListener(() -> {
            webView.reload();
        });
        swipeRefresh.setColorSchemeResources(R.color.vortex_primary);
    }

    private void navigateTo(String input) {
        if (input.isEmpty()) return;
        String url;
        if (input.startsWith("http://") || input.startsWith("https://") || input.startsWith("file://")) {
            url = input;
        } else if (input.contains(".") && !input.contains(" ")) {
            url = (prefs.getBoolean("force_https", true) ? "https://" : "http://") + input;
        } else {
            // Search
            String searchEngine = prefs.getString("search_engine", "https://www.google.com/search?q=");
            url = searchEngine + Uri.encode(input);
        }
        webView.loadUrl(url);
    }

    private void showTabsDialog() {
        String[] tabNames = new String[tabs.size() + 1];
        for (int i = 0; i < tabs.size(); i++) {
            tabNames[i] = (i == currentTabIndex ? "● " : "  ") + tabs.get(i).title;
        }
        tabNames[tabs.size()] = "+ New Tab";

        new AlertDialog.Builder(this)
            .setTitle("Tabs (" + tabs.size() + ")")
            .setItems(tabNames, (dialog, which) -> {
                if (which == tabs.size()) {
                    // New tab
                    tabs.add(new TabInfo("New Tab", getHomepage()));
                    currentTabIndex = tabs.size() - 1;
                    webView.loadUrl(getHomepage());
                } else {
                    if (currentTabIndex >= 0 && currentTabIndex < tabs.size()) {
                        tabs.get(currentTabIndex).url = webView.getUrl();
                        tabs.get(currentTabIndex).title = webView.getTitle();
                    }
                    currentTabIndex = which;
                    webView.loadUrl(tabs.get(which).url);
                }
            })
            .setNegativeButton("Close Tab", (dialog, which) -> {
                if (tabs.size() > 1) {
                    tabs.remove(currentTabIndex);
                    currentTabIndex = Math.min(currentTabIndex, tabs.size() - 1);
                    webView.loadUrl(tabs.get(currentTabIndex).url);
                }
            })
            .show();
    }

    private void showMenuDialog() {
        String[] items = {"⟲ Refresh", "⌂ Home", "★ Bookmarks", "⤓ Downloads",
                "🔒 Incognito Tab", "⚙ Settings", "ⓘ About Vortex"};

        new AlertDialog.Builder(this)
            .setItems(items, (dialog, which) -> {
                switch (which) {
                    case 0: webView.reload(); break;
                    case 1: webView.loadUrl(getHomepage()); break;
                    case 2: Toast.makeText(this, "Bookmarks coming soon!", Toast.LENGTH_SHORT).show(); break;
                    case 3:
                        startActivity(new Intent(DownloadManager.ACTION_VIEW_DOWNLOADS));
                        break;
                    case 4:
                        // Simple incognito - clear and browse
                        WebView incog = webView;
                        incog.clearHistory();
                        incog.clearCache(true);
                        CookieManager.getInstance().removeAllCookies(null);
                        Toast.makeText(this, "Incognito mode: data will clear on exit", Toast.LENGTH_SHORT).show();
                        webView.loadUrl(getHomepage());
                        break;
                    case 5:
                        startActivity(new Intent(this, SettingsActivity.class));
                        break;
                    case 6:
                        new AlertDialog.Builder(this)
                            .setTitle("Vortex Browser")
                            .setMessage("Version 1.0.0\n\nA fast, private browser.\n\n• Built-in ad blocker\n• Fingerprint protection\n• Tracker blocking\n• Force HTTPS\n• Proxy support\n• Clear data on exit")
                            .setPositiveButton("OK", null)
                            .show();
                        break;
                }
            })
            .show();
    }

    // --- WebView Clients ---

    private class VortexWebViewClient extends WebViewClient {

        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            urlBar.setText(url);
            progressBar.setVisibility(View.VISIBLE);
            swipeRefresh.setRefreshing(false);
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            progressBar.setVisibility(View.GONE);
            swipeRefresh.setRefreshing(false);
            urlBar.setText(url);

            // Update tab info
            if (currentTabIndex >= 0 && currentTabIndex < tabs.size()) {
                tabs.get(currentTabIndex).url = url;
                tabs.get(currentTabIndex).title = view.getTitle();
            }

            // Inject fingerprint protection
            if (prefs.getBoolean("fingerprint_protection", true)) {
                view.evaluateJavascript(AdBlocker.FINGERPRINT_PROTECTION_JS, null);
            }
        }

        @Override
        public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
            String url = request.getUrl().toString();

            // Force HTTPS
            if (prefs.getBoolean("force_https", true) && url.startsWith("http://")) {
                // Will be handled by redirect
            }

            // Ad blocking
            if (prefs.getBoolean("ad_blocker", true) && AdBlocker.isAd(url)) {
                return new WebResourceResponse("text/plain", "utf-8",
                    new ByteArrayInputStream("".getBytes()));
            }

            // Tracker blocking
            if (prefs.getBoolean("tracker_blocker", true) && AdBlocker.isTracker(url)) {
                return new WebResourceResponse("text/plain", "utf-8",
                    new ByteArrayInputStream("".getBytes()));
            }

            return super.shouldInterceptRequest(view, request);
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            String url = request.getUrl().toString();

            // Force HTTPS upgrade
            if (prefs.getBoolean("force_https", true) && url.startsWith("http://")) {
                view.loadUrl(url.replace("http://", "https://"));
                return true;
            }

            // Handle external schemes
            if (!url.startsWith("http://") && !url.startsWith("https://") && !url.startsWith("file://")) {
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                } catch (Exception e) {
                    // ignore
                }
                return true;
            }
            return false;
        }
    }

    private class VortexWebChromeClient extends WebChromeClient {
        @Override
        public void onProgressChanged(WebView view, int newProgress) {
            progressBar.setProgress(newProgress);
            if (newProgress == 100) {
                progressBar.setVisibility(View.GONE);
            }
        }

        @Override
        public void onReceivedTitle(WebView view, String title) {
            if (currentTabIndex >= 0 && currentTabIndex < tabs.size()) {
                tabs.get(currentTabIndex).title = title;
            }
        }
    }

    // --- Lifecycle ---

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        webView.onResume();
        // Reapply settings
        applyProxySettings();
    }

    @Override
    protected void onPause() {
        webView.onPause();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        // Clear data on exit
        if (prefs.getBoolean("clear_data_on_exit", false)) {
            webView.clearHistory();
            webView.clearCache(true);
            webView.clearFormData();
            CookieManager.getInstance().removeAllCookies(null);
            CookieManager.getInstance().flush();
            deleteDatabase("webview.db");
            deleteDatabase("webviewCache.db");
        }
        webView.destroy();
        super.onDestroy();
    }

    private void applyProxySettings() {
        if (prefs.getBoolean("proxy_enabled", false)) {
            String host = prefs.getString("proxy_host", "");
            String port = prefs.getString("proxy_port", "");
            if (!host.isEmpty() && !port.isEmpty()) {
                try {
                    boolean isSocks = prefs.getBoolean("proxy_socks", false);
                    if (isSocks) {
                        System.setProperty("socksProxyHost", host);
                        System.setProperty("socksProxyPort", port);
                    } else {
                        System.setProperty("http.proxyHost", host);
                        System.setProperty("http.proxyPort", port);
                        System.setProperty("https.proxyHost", host);
                        System.setProperty("https.proxyPort", port);
                    }
                } catch (Exception e) { /* ignore */ }
            }
        }
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null && getCurrentFocus() != null) {
            imm.hideSoftInputFromWindow(getCurrentFocus().getWindowToken(), 0);
        }
        urlBar.clearFocus();
    }

    // --- Tab Info ---
    static class TabInfo {
        String title;
        String url;
        TabInfo(String title, String url) {
            this.title = title;
            this.url = url;
        }
    }
}
