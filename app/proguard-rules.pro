# Vortex Browser ProGuard Rules

# Keep WebView JavaScript interface
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Keep preference classes
-keep class * extends androidx.preference.Preference

# Keep model classes
-keep class com.vortex.browser.BookmarkManager$Bookmark { *; }
-keep class com.vortex.browser.HistoryManager$HistoryEntry { *; }
-keep class com.vortex.browser.NotesManager$Note { *; }

# General Android
-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable
-dontwarn org.conscrypt.**
