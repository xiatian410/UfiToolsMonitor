# Project-specific ProGuard rules.

# WorkManager/Room: keep generated database no-arg constructor
-keep class * extends androidx.room.RoomDatabase { <init>(); }
