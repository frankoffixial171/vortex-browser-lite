package com.vortex.browser;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.app.PictureInPictureParams;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Rational;
import android.view.GestureDetector;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.webkit.CookieManager;
import android.webkit.GeolocationPermissions;
import android.webkit.PermissionRequest;
import android.webkit.URLUtil;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.preference.PreferenceManager;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import java.io.ByteArrayInputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private WebView webView;
    private EditText urlBar;
    private ProgressBar progressBar;
    private SwipeRefreshLayout swipeRefresh;
    private ImageButton btnBack, btnForward, btnRefresh, btnTabs, btnMenu;
    private SharedPreferences prefs;

    // Find in page
    private LinearLayout findBar;
    private EditText findInput;
    private TextView findCount;
    private boolean findBarVisible = false;

    // Managers
    private BookmarkManager bookmarkManager;
    private HistoryManager historyManager;
    private NotesManager notesManager;

    // Tab management
    private List<TabInfo> tabs = new ArrayList<>();
    private int currentTabIndex = 0;

    // Desktop mode
    private boolean desktopMode = false;
    private String defaultUserAgent;

    // Gesture detector for swipe
    private GestureDetector gestureDetector;

    // File chooser callback
    private ValueCallback<Uri[]> fileChooserCallback;
    private static final int FILE_CHOOSER_REQUEST = 100;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Apply dark mode before setContentView
        applyDarkMode();

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = PreferenceManager.getDefaultSharedPreferences(this);
        bookmarkManager = new BookmarkManager(this);
        historyManager = new HistoryManager(this);
        notesManager = new NotesManager(this);

        initViews();
        setupWebView();
        setupUrlBar();
        setupButtons();
        setupSwipeRefresh();
        setupFindBar();
        setupGestures();

        // Create first tab
        tabs.add(new TabInfo("New Tab", getHomepage(), null));

        // Handle intent
        Intent intent = getIntent();
        String url = null;
        if (intent != null && Intent.ACTION_VIEW.equals(intent.getAction())) {
            url = intent.getDataString();
        }

        webView.loadUrl(url != null ? url : getHomepage());
    }

    private void initViews() {
        webView = findViewById(R.id.webView);
        urlBar = findViewById(R.id.urlBar);
        progressBar = findViewById(R.id.progressBar);
        swipeRefresh = findViewById(R.id.swipeRefresh);
        btnBack = findViewById(R.id.btnBack);
        btnForward = findViewById(R.id.btnForward);
        btnRefresh = findViewById(R.id.btnRefresh);
        btnTabs = findViewById(R.id.btnTabs);
        btnMenu = findViewById(R.id.btnMenu);
        findBar = findViewById(R.id.findBar);
        findInput = findViewById(R.id.findInput);
        findCount = findViewById(R.id.findCount);
    }

    private void applyDarkMode() {
        SharedPreferences p = PreferenceManager.getDefaultSharedPreferences(this);
        String mode = p.getString("dark_mode", "system");
        switch (mode) {
            case "on":
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
                break;
            case "off":
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
                break;
            default:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
                break;
        }
    }

    private String getHomepage() {
        String custom = prefs.getString("custom_homepage", "");
        if (custom != null && !custom.isEmpty()) return custom;
        return "file:///android_asset/homepage.html";
    }

    // =================== WebView Setup ===================

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
        settings.setGeolocationEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);

        // Save default UA
        defaultUserAgent = settings.getUserAgentString();

        // Force HTTPS
        if (prefs.getBoolean("force_https", true)) {
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        }

        // Custom user agent
        String ua = settings.getUserAgentString();
        settings.setUserAgentString(ua.replace("wv", "").replace("Version/4.0 ", "") + " VortexBrowser/1.0");

        // Force dark mode for web content (API 29+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            String darkMode = prefs.getString("dark_mode", "system");
            if ("on".equals(darkMode)) {
                if (Build.VERSION.SDK_INT >= 33) {
                    settings.setAlgorithmicDarkeningAllowed(true);
                } else {
                    try {
                        androidx.webkit.WebSettingsCompat.setForceDark(
                            settings, androidx.webkit.WebSettingsCompat.FORCE_DARK_ON);
                    } catch (Exception e) { /* fallback */ }
                }
            } else if ("system".equals(darkMode)) {
                if (Build.VERSION.SDK_INT >= 33) {
                    settings.setAlgorithmicDarkeningAllowed(true);
                }
            }
        }

        webView.setWebViewClient(new VortexWebViewClient());
        webView.setWebChromeClient(new VortexWebChromeClient());

        // Download listener
        webView.setDownloadListener((url, userAgent, contentDisposition, mimeType, contentLength) -> {
            try {
                DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
                request.setMimeType(mimeType);
                String filename = URLUtil.guessFileName(url, contentDisposition, mimeType);
                request.setTitle(filename);
                request.setDescription("Downloading via Vortex Browser");
                request.allowScanningByMediaScanner();
                request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, filename);
                DownloadManager dm = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
                dm.enqueue(request);
                Toast.makeText(this, "⤓ Downloading: " + filename, Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Toast.makeText(this, "Download failed", Toast.LENGTH_SHORT).show();
            }
        });

        // Long press to save images / copy links
        webView.setOnLongClickListener(v -> {
            WebView.HitTestResult result = webView.getHitTestResult();
            if (result != null) {
                handleLongPress(result);
                return true;
            }
            return false;
        });
    }

    private void handleLongPress(WebView.HitTestResult result) {
        String url = result.getExtra();
        if (url == null) return;

        List<String> options = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();

        switch (result.getType()) {
            case WebView.HitTestResult.IMAGE_TYPE:
            case WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE:
                options.add("Save image");
                actions.add(() -> downloadUrl(url));
                options.add("Open image in new tab");
                actions.add(() -> openInNewTab(url));
                options.add("Copy image URL");
                actions.add(() -> copyToClipboard(url));
                break;
            case WebView.HitTestResult.SRC_ANCHOR_TYPE:
                options.add("Open in new tab");
                actions.add(() -> openInNewTab(url));
                options.add("Copy link");
                actions.add(() -> copyToClipboard(url));
                options.add("Share link");
                actions.add(() -> shareUrl(url));
                break;
        }

        if (options.isEmpty()) return;

        options.add("Add to notes");
        actions.add(() -> notesManager.addNote(url, webView.getUrl(), webView.getTitle()));

        new AlertDialog.Builder(this)
            .setItems(options.toArray(new String[0]), (d, w) -> actions.get(w).run())
            .show();
    }

    // =================== URL Bar ===================

    private void setupUrlBar() {
        urlBar.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_GO ||
                (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                    && event.getAction() == KeyEvent.ACTION_DOWN)) {
                navigateTo(urlBar.getText().toString().trim());
                hideKeyboard();
                return true;
            }
            return false;
        });

        urlBar.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) urlBar.selectAll();
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
        swipeRefresh.setOnRefreshListener(() -> webView.reload());
        swipeRefresh.setColorSchemeResources(R.color.vortex_primary);
    }

    // =================== Find in Page ===================

    private void setupFindBar() {
        ImageButton findPrev = findViewById(R.id.findPrev);
        ImageButton findNext = findViewById(R.id.findNext);
        ImageButton findClose = findViewById(R.id.findClose);

        findInput.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                webView.findAllAsync(s.toString());
            }
            public void afterTextChanged(Editable s) {}
        });

        findPrev.setOnClickListener(v -> webView.findNext(false));
        findNext.setOnClickListener(v -> webView.findNext(true));
        findClose.setOnClickListener(v -> closeFindBar());

        webView.setFindListener((activeMatchOrdinal, numberOfMatches, isDoneCounting) -> {
            if (isDoneCounting) {
                findCount.setText(numberOfMatches > 0
                    ? (activeMatchOrdinal + 1) + "/" + numberOfMatches
                    : "0/0");
            }
        });
    }

    private void showFindBar() {
        findBar.setVisibility(View.VISIBLE);
        findBarVisible = true;
        findInput.requestFocus();
        showKeyboard(findInput);
    }

    private void closeFindBar() {
        findBar.setVisibility(View.GONE);
        findBarVisible = false;
        findInput.setText("");
        webView.clearMatches();
        hideKeyboard();
    }

    // =================== Gesture Detection ===================

    private void setupGestures() {
        if (!prefs.getBoolean("swipe_gestures", true)) return;

        gestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            private static final int SWIPE_THRESHOLD = 120;
            private static final int SWIPE_VELOCITY = 200;

            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float vX, float vY) {
                if (e1 == null || e2 == null) return false;
                float dX = e2.getX() - e1.getX();
                float dY = e2.getY() - e1.getY();
                if (Math.abs(dX) > Math.abs(dY) && Math.abs(dX) > SWIPE_THRESHOLD && Math.abs(vX) > SWIPE_VELOCITY) {
                    if (dX > 0 && webView.canGoBack()) {
                        webView.goBack();
                        return true;
                    } else if (dX < 0 && webView.canGoForward()) {
                        webView.goForward();
                        return true;
                    }
                }
                return false;
            }
        });

        webView.setOnTouchListener((v, event) -> {
            gestureDetector.onTouchEvent(event);
            return false;
        });
    }

    // =================== Navigation ===================

    private void navigateTo(String input) {
        if (input.isEmpty()) return;
        String url;
        if (input.startsWith("http://") || input.startsWith("https://") || input.startsWith("file://")) {
            url = input;
        } else if (input.contains(".") && !input.contains(" ")) {
            url = (prefs.getBoolean("force_https", true) ? "https://" : "http://") + input;
        } else {
            String searchEngine = prefs.getString("search_engine", "https://www.google.com/search?q=");
            url = searchEngine + Uri.encode(input);
        }
        webView.loadUrl(url);
    }

    // =================== Tabs ===================

    private void showTabsDialog() {
        // Save current tab state
        if (currentTabIndex >= 0 && currentTabIndex < tabs.size()) {
            tabs.get(currentTabIndex).url = webView.getUrl();
            tabs.get(currentTabIndex).title = webView.getTitle();
        }

        String[] tabNames = new String[tabs.size() + 1];
        for (int i = 0; i < tabs.size(); i++) {
            TabInfo t = tabs.get(i);
            String prefix = (i == currentTabIndex) ? "● " : "  ";
            String sleeping = t.isSleeping ? " 💤" : "";
            String group = (t.group != null && !t.group.isEmpty()) ? " [" + t.group + "]" : "";
            tabNames[i] = prefix + t.title + sleeping + group;
        }
        tabNames[tabs.size()] = "+ New Tab";

        new AlertDialog.Builder(this)
            .setTitle("Tabs (" + tabs.size() + ")")
            .setItems(tabNames, (dialog, which) -> {
                if (which == tabs.size()) {
                    newTab(getHomepage());
                } else {
                    switchToTab(which);
                }
            })
            .setNeutralButton("Group Tab", (dialog, which) -> showTabGroupDialog())
            .setNegativeButton("Close Tab", (dialog, which) -> closeCurrentTab())
            .show();
    }

    private void newTab(String url) {
        // Save current tab
        if (currentTabIndex >= 0 && currentTabIndex < tabs.size()) {
            tabs.get(currentTabIndex).url = webView.getUrl();
            tabs.get(currentTabIndex).title = webView.getTitle();
        }
        tabs.add(new TabInfo("New Tab", url, null));
        currentTabIndex = tabs.size() - 1;
        webView.loadUrl(url);
    }

    private void openInNewTab(String url) {
        tabs.add(new TabInfo("Loading...", url, null));
        Toast.makeText(this, "Opened in new tab", Toast.LENGTH_SHORT).show();
    }

    private void switchToTab(int index) {
        if (index < 0 || index >= tabs.size()) return;
        currentTabIndex = index;
        TabInfo tab = tabs.get(index);
        tab.isSleeping = false;
        tab.lastActiveTime = System.currentTimeMillis();
        webView.loadUrl(tab.url);
    }

    private void closeCurrentTab() {
        if (tabs.size() > 1) {
            tabs.remove(currentTabIndex);
            currentTabIndex = Math.min(currentTabIndex, tabs.size() - 1);
            webView.loadUrl(tabs.get(currentTabIndex).url);
        } else {
            Toast.makeText(this, "Can't close the last tab", Toast.LENGTH_SHORT).show();
        }
    }

    private void showTabGroupDialog() {
        if (currentTabIndex < 0 || currentTabIndex >= tabs.size()) return;

        EditText input = new EditText(this);
        input.setHint("Group name (e.g. Work, Social)");
        TabInfo tab = tabs.get(currentTabIndex);
        if (tab.group != null) input.setText(tab.group);

        new AlertDialog.Builder(this)
            .setTitle("Set Tab Group")
            .setView(input)
            .setPositiveButton("Set", (d, w) -> {
                tab.group = input.getText().toString().trim();
                Toast.makeText(this, "Tab grouped: " + tab.group, Toast.LENGTH_SHORT).show();
            })
            .setNegativeButton("Remove Group", (d, w) -> {
                tab.group = null;
                Toast.makeText(this, "Group removed", Toast.LENGTH_SHORT).show();
            })
            .show();
    }

    // Tab sleeping - put inactive tabs to sleep
    private void sleepInactiveTabs() {
        if (!prefs.getBoolean("tab_sleeping", true)) return;
        long sleepAfterMs = 5 * 60 * 1000; // 5 minutes
        long now = System.currentTimeMillis();
        for (int i = 0; i < tabs.size(); i++) {
            if (i != currentTabIndex && !tabs.get(i).isSleeping) {
                if (now - tabs.get(i).lastActiveTime > sleepAfterMs) {
                    tabs.get(i).isSleeping = true;
                }
            }
        }
    }

    // =================== Main Menu ===================

    private void showMenuDialog() {
        boolean isBookmarked = bookmarkManager.isBookmarked(webView.getUrl());

        String[] items = {
            "🔍 Find in page",
            isBookmarked ? "★ Remove bookmark" : "☆ Add bookmark",
            "📚 Bookmarks",
            "🕐 History",
            "📝 Notes",
            "📖 Reader mode",
            "🖥️ Desktop mode " + (desktopMode ? "✓" : ""),
            "🎬 Picture-in-picture",
            "🌐 Translate page",
            "🔗 Share page",
            "⤓ Downloads",
            "🔒 Incognito tab",
            "⚙ Settings",
            "ⓘ About Vortex"
        };

        new AlertDialog.Builder(this)
            .setItems(items, (dialog, which) -> {
                switch (which) {
                    case 0: showFindBar(); break;
                    case 1: toggleBookmark(); break;
                    case 2: openBookmarks(); break;
                    case 3: openHistory(); break;
                    case 4: openNotes(); break;
                    case 5: toggleReaderMode(); break;
                    case 6: toggleDesktopMode(); break;
                    case 7: enterPiPMode(); break;
                    case 8: translatePage(); break;
                    case 9: sharePage(); break;
                    case 10: openDownloads(); break;
                    case 11: openIncognitoTab(); break;
                    case 12: openSettings(); break;
                    case 13: showAbout(); break;
                }
            })
            .show();
    }

    // =================== Bookmarks ===================

    private void toggleBookmark() {
        String url = webView.getUrl();
        if (url == null) return;
        if (bookmarkManager.isBookmarked(url)) {
            bookmarkManager.removeBookmark(url);
            Toast.makeText(this, "Bookmark removed", Toast.LENGTH_SHORT).show();
        } else {
            bookmarkManager.addBookmark(webView.getTitle(), url);
            Toast.makeText(this, "★ Bookmarked!", Toast.LENGTH_SHORT).show();
        }
    }

    private void openBookmarks() {
        List<BookmarkManager.Bookmark> bookmarks = bookmarkManager.getBookmarks();
        if (bookmarks.isEmpty()) {
            Toast.makeText(this, "No bookmarks yet", Toast.LENGTH_SHORT).show();
            return;
        }
        String[] items = new String[bookmarks.size()];
        for (int i = 0; i < bookmarks.size(); i++) {
            BookmarkManager.Bookmark b = bookmarks.get(i);
            items[i] = b.title + "\n" + truncateUrl(b.url);
        }
        new AlertDialog.Builder(this)
            .setTitle("Bookmarks (" + bookmarks.size() + ")")
            .setItems(items, (d, w) -> webView.loadUrl(bookmarks.get(w).url))
            .setNeutralButton("Search", (d, w) -> searchBookmarks())
            .setNegativeButton("Close", null)
            .show();
    }

    private void searchBookmarks() {
        EditText input = new EditText(this);
        input.setHint("Search bookmarks...");
        new AlertDialog.Builder(this)
            .setTitle("Search Bookmarks")
            .setView(input)
            .setPositiveButton("Search", (d, w) -> {
                String query = input.getText().toString().trim();
                List<BookmarkManager.Bookmark> results = bookmarkManager.search(query);
                if (results.isEmpty()) {
                    Toast.makeText(this, "No results", Toast.LENGTH_SHORT).show();
                } else {
                    String[] items = new String[results.size()];
                    for (int i = 0; i < results.size(); i++) {
                        items[i] = results.get(i).title + "\n" + truncateUrl(results.get(i).url);
                    }
                    new AlertDialog.Builder(this)
                        .setTitle("Results")
                        .setItems(items, (d2, w2) -> webView.loadUrl(results.get(w2).url))
                        .show();
                }
            })
            .show();
    }

    // =================== History ===================

    private void openHistory() {
        List<HistoryManager.HistoryEntry> history = historyManager.getHistory();
        if (history.isEmpty()) {
            Toast.makeText(this, "No history yet", Toast.LENGTH_SHORT).show();
            return;
        }
        int count = Math.min(history.size(), 50);
        String[] items = new String[count];
        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault());
        for (int i = 0; i < count; i++) {
            HistoryManager.HistoryEntry h = history.get(i);
            items[i] = h.title + "\n" + truncateUrl(h.url) + " • " + sdf.format(new Date(h.timestamp));
        }
        new AlertDialog.Builder(this)
            .setTitle("History (" + history.size() + ")")
            .setItems(items, (d, w) -> webView.loadUrl(history.get(w).url))
            .setNeutralButton("Search", (d, w) -> searchHistory())
            .setNegativeButton("Clear All", (d, w) -> {
                historyManager.clearHistory();
                Toast.makeText(this, "History cleared", Toast.LENGTH_SHORT).show();
            })
            .show();
    }

    private void searchHistory() {
        EditText input = new EditText(this);
        input.setHint("Search history...");
        new AlertDialog.Builder(this)
            .setTitle("Search History")
            .setView(input)
            .setPositiveButton("Search", (d, w) -> {
                List<HistoryManager.HistoryEntry> results = historyManager.search(input.getText().toString().trim());
                if (results.isEmpty()) {
                    Toast.makeText(this, "No results", Toast.LENGTH_SHORT).show();
                } else {
                    String[] items = new String[results.size()];
                    for (int i = 0; i < results.size(); i++) {
                        items[i] = results.get(i).title + "\n" + truncateUrl(results.get(i).url);
                    }
                    new AlertDialog.Builder(this)
                        .setTitle("Results")
                        .setItems(items, (d2, w2) -> webView.loadUrl(results.get(w2).url))
                        .show();
                }
            })
            .show();
    }

    // =================== Notes ===================

    private void openNotes() {
        List<NotesManager.Note> notes = notesManager.getNotes();
        if (notes.isEmpty()) {
            // Offer to create new note
            createNewNote();
            return;
        }
        String[] items = new String[notes.size() + 1];
        items[0] = "+ New Note";
        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault());
        for (int i = 0; i < notes.size(); i++) {
            NotesManager.Note n = notes.get(i);
            String preview = n.content.length() > 60 ? n.content.substring(0, 60) + "…" : n.content;
            items[i + 1] = preview + "\n" + sdf.format(new Date(n.timestamp));
        }
        new AlertDialog.Builder(this)
            .setTitle("Notes (" + notes.size() + ")")
            .setItems(items, (d, w) -> {
                if (w == 0) {
                    createNewNote();
                } else {
                    showNoteDetail(notes.get(w - 1));
                }
            })
            .show();
    }

    private void createNewNote() {
        EditText input = new EditText(this);
        input.setHint("Write a note...");
        input.setMinLines(3);
        new AlertDialog.Builder(this)
            .setTitle("New Note")
            .setView(input)
            .setPositiveButton("Save", (d, w) -> {
                String content = input.getText().toString().trim();
                if (!content.isEmpty()) {
                    notesManager.addNote(content, webView.getUrl(), webView.getTitle());
                    Toast.makeText(this, "Note saved", Toast.LENGTH_SHORT).show();
                }
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void showNoteDetail(NotesManager.Note note) {
        String msg = note.content;
        if (note.sourceUrl != null && !note.sourceUrl.isEmpty()) {
            msg += "\n\nFrom: " + note.sourceTitle;
        }
        new AlertDialog.Builder(this)
            .setTitle("Note")
            .setMessage(msg)
            .setPositiveButton("OK", null)
            .setNeutralButton("Open Source", (d, w) -> {
                if (note.sourceUrl != null) webView.loadUrl(note.sourceUrl);
            })
            .setNegativeButton("Delete", (d, w) -> {
                notesManager.deleteNote(note.id);
                Toast.makeText(this, "Note deleted", Toast.LENGTH_SHORT).show();
            })
            .show();
    }

    // =================== Reader Mode ===================

    private void toggleReaderMode() {
        String readerJs = "(function() {"
            + "var article = document.querySelector('article') || document.querySelector('[role=main]') || document.querySelector('main') || document.body;"
            + "var title = document.title;"
            + "var content = '';"
            + "var ps = article.querySelectorAll('p, h1, h2, h3, h4, li, blockquote, pre');"
            + "for (var i = 0; i < ps.length; i++) {"
            + "  var tag = ps[i].tagName.toLowerCase();"
            + "  var text = ps[i].innerText.trim();"
            + "  if (text.length > 20 || tag.startsWith('h')) {"
            + "    content += '<' + tag + '>' + text + '</' + tag + '>';"
            + "  }"
            + "}"
            + "if (content.length < 100) content = article.innerHTML;"
            + "document.open();"
            + "document.write('<html><head><meta name=viewport content=\"width=device-width,initial-scale=1\"><style>"
            + "* { margin: 0; padding: 0; box-sizing: border-box; }"
            + "body { font-family: Georgia, serif; max-width: 680px; margin: 0 auto; padding: 24px 16px; background: #FAFAFA; color: #1a1a2e; line-height: 1.8; font-size: 18px; }"
            + "@media (prefers-color-scheme: dark) { body { background: #1a1a2e; color: #e0e0e0; } }"
            + "h1 { font-size: 28px; margin-bottom: 16px; line-height: 1.3; }"
            + "h2, h3 { margin-top: 24px; margin-bottom: 8px; }"
            + "p { margin-bottom: 16px; }"
            + "li { margin-left: 24px; margin-bottom: 8px; }"
            + "pre { background: #f0f0f5; padding: 12px; border-radius: 8px; overflow-x: auto; font-size: 14px; }"
            + "blockquote { border-left: 3px solid #6C5CE7; padding-left: 16px; color: #666; }"
            + ".reader-bar { position: fixed; top: 0; left: 0; right: 0; padding: 8px 16px; background: #6C5CE7; color: white; font-family: sans-serif; font-size: 14px; text-align: center; z-index: 9999; }"
            + "</style></head><body>"
            + "<div class=reader-bar>📖 Reader Mode — Vortex Browser</div>"
            + "<div style=height:40px></div>"
            + "<h1>' + title + '</h1>"
            + "' + content + '"
            + "</body></html>');"
            + "document.close();"
            + "})();";

        webView.evaluateJavascript(readerJs, null);
        Toast.makeText(this, "📖 Reader mode", Toast.LENGTH_SHORT).show();
    }

    // =================== Desktop Mode ===================

    private void toggleDesktopMode() {
        desktopMode = !desktopMode;
        WebSettings settings = webView.getSettings();
        if (desktopMode) {
            String desktopUA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36 VortexBrowser/1.0";
            settings.setUserAgentString(desktopUA);
            settings.setLoadWithOverviewMode(true);
            settings.setUseWideViewPort(true);
            Toast.makeText(this, "🖥️ Desktop mode ON", Toast.LENGTH_SHORT).show();
        } else {
            String ua = defaultUserAgent.replace("wv", "").replace("Version/4.0 ", "") + " VortexBrowser/1.0";
            settings.setUserAgentString(ua);
            Toast.makeText(this, "📱 Mobile mode ON", Toast.LENGTH_SHORT).show();
        }
        webView.reload();
    }

    // =================== Picture-in-Picture ===================

    private void enterPiPMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (getPackageManager().hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) {
                PictureInPictureParams params = new PictureInPictureParams.Builder()
                    .setAspectRatio(new Rational(16, 9))
                    .build();
                enterPictureInPictureMode(params);
            } else {
                Toast.makeText(this, "PiP not supported on this device", Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(this, "PiP requires Android 8+", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onPictureInPictureModeChanged(boolean isInPiPMode, Configuration newConfig) {
        super.onPictureInPictureModeChanged(isInPiPMode, newConfig);
        if (isInPiPMode) {
            // Hide UI, show only WebView
            findViewById(R.id.bottomBar).setVisibility(View.GONE);
            progressBar.setVisibility(View.GONE);
            if (findBarVisible) findBar.setVisibility(View.GONE);
        } else {
            findViewById(R.id.bottomBar).setVisibility(View.VISIBLE);
            if (findBarVisible) findBar.setVisibility(View.VISIBLE);
        }
    }

    // =================== Translation ===================

    private void translatePage() {
        String url = webView.getUrl();
        if (url != null && !url.startsWith("file://")) {
            String translateUrl = "https://translate.google.com/translate?sl=auto&tl=en&u=" + Uri.encode(url);
            webView.loadUrl(translateUrl);
            Toast.makeText(this, "🌐 Translating page...", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "Cannot translate this page", Toast.LENGTH_SHORT).show();
        }
    }

    // =================== Share ===================

    private void sharePage() {
        String url = webView.getUrl();
        if (url == null) return;
        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType("text/plain");
        share.putExtra(Intent.EXTRA_SUBJECT, webView.getTitle());
        share.putExtra(Intent.EXTRA_TEXT, url);
        startActivity(Intent.createChooser(share, "Share via"));
    }

    private void shareUrl(String url) {
        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType("text/plain");
        share.putExtra(Intent.EXTRA_TEXT, url);
        startActivity(Intent.createChooser(share, "Share via"));
    }

    // =================== Downloads ===================

    private void openDownloads() {
        try {
            startActivity(new Intent(DownloadManager.ACTION_VIEW_DOWNLOADS));
        } catch (Exception e) {
            Toast.makeText(this, "Cannot open downloads", Toast.LENGTH_SHORT).show();
        }
    }

    // =================== Incognito ===================

    private void openIncognitoTab() {
        newTab(getHomepage());
        webView.clearHistory();
        webView.clearCache(true);
        CookieManager.getInstance().removeAllCookies(null);
        tabs.get(currentTabIndex).title = "🔒 Incognito";
        Toast.makeText(this, "🔒 Incognito tab — data clears on close", Toast.LENGTH_SHORT).show();
    }

    // =================== Settings / About ===================

    private void openSettings() {
        startActivity(new Intent(this, SettingsActivity.class));
    }

    private void showAbout() {
        new AlertDialog.Builder(this)
            .setTitle("Vortex Browser v1.0")
            .setMessage("Fast. Private. Yours.\n\n"
                + "Features:\n"
                + "🛡️ Ad blocker (40+ networks)\n"
                + "🕵️ Tracker blocker (35+ domains)\n"
                + "🔐 Fingerprint protection\n"
                + "🔒 Force HTTPS\n"
                + "🧹 Clear data on exit\n"
                + "🌐 Proxy support\n"
                + "📖 Reader mode\n"
                + "🌙 Dark mode\n"
                + "🔍 Find in page\n"
                + "🎬 Picture-in-picture\n"
                + "🖥️ Desktop mode\n"
                + "📚 Bookmarks & History\n"
                + "📝 Notes\n"
                + "🌐 Page translation\n"
                + "💤 Tab sleeping\n"
                + "👆 Swipe gestures\n"
                + "📥 Download manager\n\n"
                + "Built with ❤️")
            .setPositiveButton("OK", null)
            .show();
    }

    // =================== Utility ===================

    private void downloadUrl(String url) {
        try {
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
            String filename = URLUtil.guessFileName(url, null, null);
            request.setTitle(filename);
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, filename);
            DownloadManager dm = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
            dm.enqueue(request);
            Toast.makeText(this, "Downloading: " + filename, Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "Download failed", Toast.LENGTH_SHORT).show();
        }
    }

    private void copyToClipboard(String text) {
        android.content.ClipboardManager cm = (android.content.ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        cm.setPrimaryClip(android.content.ClipData.newPlainText("URL", text));
        Toast.makeText(this, "Copied!", Toast.LENGTH_SHORT).show();
    }

    private String truncateUrl(String url) {
        if (url == null) return "";
        if (url.length() > 50) return url.substring(0, 50) + "…";
        return url;
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null && getCurrentFocus() != null) {
            imm.hideSoftInputFromWindow(getCurrentFocus().getWindowToken(), 0);
        }
        urlBar.clearFocus();
    }

    private void showKeyboard(View view) {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT);
    }

    // =================== WebView Clients ===================

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

            // Update tab
            if (currentTabIndex >= 0 && currentTabIndex < tabs.size()) {
                tabs.get(currentTabIndex).url = url;
                tabs.get(currentTabIndex).title = view.getTitle();
                tabs.get(currentTabIndex).lastActiveTime = System.currentTimeMillis();
            }

            // Add to history
            if (!prefs.getBoolean("incognito_active", false)) {
                historyManager.addEntry(view.getTitle(), url);
            }

            // Fingerprint protection
            if (prefs.getBoolean("fingerprint_protection", true)) {
                view.evaluateJavascript(AdBlocker.FINGERPRINT_PROTECTION_JS, null);
            }

            // Sleep inactive tabs
            sleepInactiveTabs();
        }

        @Override
        public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
            String url = request.getUrl().toString();

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

            // Force HTTPS
            if (prefs.getBoolean("force_https", true) && url.startsWith("http://")) {
                view.loadUrl(url.replace("http://", "https://"));
                return true;
            }

            // External schemes
            if (!url.startsWith("http://") && !url.startsWith("https://") && !url.startsWith("file://")) {
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                } catch (Exception e) { /* ignore */ }
                return true;
            }
            return false;
        }
    }

    private class VortexWebChromeClient extends WebChromeClient {
        @Override
        public void onProgressChanged(WebView view, int newProgress) {
            progressBar.setProgress(newProgress);
            if (newProgress == 100) progressBar.setVisibility(View.GONE);
        }

        @Override
        public void onReceivedTitle(WebView view, String title) {
            if (currentTabIndex >= 0 && currentTabIndex < tabs.size()) {
                tabs.get(currentTabIndex).title = title;
            }
        }

        // Per-site permissions: camera, mic
        @Override
        public void onPermissionRequest(PermissionRequest request) {
            String[] resources = request.getResources();
            new AlertDialog.Builder(MainActivity.this)
                .setTitle("Permission Request")
                .setMessage("This site wants to access your device features. Allow?")
                .setPositiveButton("Allow", (d, w) -> request.grant(resources))
                .setNegativeButton("Deny", (d, w) -> request.deny())
                .setCancelable(false)
                .show();
        }

        // Geolocation permission
        @Override
        public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback callback) {
            new AlertDialog.Builder(MainActivity.this)
                .setTitle("Location Access")
                .setMessage(origin + " wants to know your location. Allow?")
                .setPositiveButton("Allow", (d, w) -> callback.invoke(origin, true, false))
                .setNegativeButton("Deny", (d, w) -> callback.invoke(origin, false, false))
                .setCancelable(false)
                .show();
        }

        // File upload
        @Override
        public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback,
                                          FileChooserParams fileChooserParams) {
            if (fileChooserCallback != null) fileChooserCallback.onReceiveValue(null);
            fileChooserCallback = filePathCallback;
            Intent intent = fileChooserParams.createIntent();
            try {
                startActivityForResult(intent, FILE_CHOOSER_REQUEST);
            } catch (Exception e) {
                fileChooserCallback = null;
                return false;
            }
            return true;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == FILE_CHOOSER_REQUEST) {
            Uri[] results = null;
            if (resultCode == Activity.RESULT_OK && data != null) {
                String dataString = data.getDataString();
                if (dataString != null) {
                    results = new Uri[]{Uri.parse(dataString)};
                }
            }
            if (fileChooserCallback != null) {
                fileChooserCallback.onReceiveValue(results);
                fileChooserCallback = null;
            }
        }
    }

    // =================== Lifecycle ===================

    @Override
    public void onBackPressed() {
        if (findBarVisible) {
            closeFindBar();
        } else if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        webView.onResume();
        applyProxySettings();
    }

    @Override
    protected void onPause() {
        webView.onPause();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (prefs.getBoolean("clear_data_on_exit", false)) {
            webView.clearHistory();
            webView.clearCache(true);
            webView.clearFormData();
            CookieManager.getInstance().removeAllCookies(null);
            CookieManager.getInstance().flush();
            deleteDatabase("webview.db");
            deleteDatabase("webviewCache.db");
            if (prefs.getBoolean("clear_history_on_exit", false)) {
                historyManager.clearHistory();
            }
        }
        webView.destroy();
        super.onDestroy();
    }

    private void applyProxySettings() {
        if (prefs.getBoolean("proxy_enabled", false)) {
            String host = prefs.getString("proxy_host", "");
            String port = prefs.getString("proxy_port", "");
            if (host != null && !host.isEmpty() && port != null && !port.isEmpty()) {
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

    // =================== Tab Info ===================

    static class TabInfo {
        String title;
        String url;
        String group;
        boolean isSleeping = false;
        long lastActiveTime;

        TabInfo(String title, String url, String group) {
            this.title = title;
            this.url = url;
            this.group = group;
            this.lastActiveTime = System.currentTimeMillis();
        }
    }
}
