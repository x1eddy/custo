# Shizuku instantiates the user service by reflection in a separate (shell) process.
-keep class app.custo.shizuku.PrivilegedService { <init>(...); *; }
-keep class app.custo.IPrivilegedService** { *; }

# WorkManager (used by Glance widgets) creates its Room database by reflection.
# Without these, R8 strips WorkDatabase_Impl and the app crashes at startup
# ("Failed to create an instance of androidx.work.impl.WorkDatabase").
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-keep class androidx.work.impl.WorkDatabase_Impl { *; }
-keep class androidx.work.impl.** extends androidx.room.RoomDatabase { *; }
