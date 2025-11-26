# Consumer ProGuard rules for library
# These rules will be merged into the consuming app's ProGuard configuration

# Keep SDK public API
-keep class com.baji.sdk.** { *; }
-keep interface com.baji.sdk.** { *; }

