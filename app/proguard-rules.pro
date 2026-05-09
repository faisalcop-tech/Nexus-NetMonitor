-keep class com.faisalmalik.nexusnetmonitor.** { *; }
-keep class * extends android.webkit.WebViewClient { *; }
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
-dontwarn android.webkit.**
-keep public class android.webkit.** { *; }
