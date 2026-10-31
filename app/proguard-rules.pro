-keep class com.lunar.ddz.** { *; }
-dontwarn android.**
-keepclassmembers class * extends android.app.Activity {
    public void *(android.view.View);
}