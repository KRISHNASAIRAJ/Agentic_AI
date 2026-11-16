# Keep Room entities
-keep class com.smsdetector.app.data.db.** { *; }
# Keep Retrofit models
-keep class com.smsdetector.app.data.net.** { *; }
-keepattributes Signature
-dontwarn retrofit2.**
