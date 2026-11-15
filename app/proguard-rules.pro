# Shizuku instantiates the user service by reflection in a separate (shell) process.
-keep class app.custo.shizuku.PrivilegedService { <init>(...); *; }
-keep class app.custo.IPrivilegedService** { *; }

# WorkManager (used by Glance widgets) creates its Room database by reflection.
# Without these, R8 strips WorkDatabase_Impl and the app crashes at startup
# ("Failed to create an instance of androidx.work.impl.WorkDatabase").
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-keep class androidx.work.impl.WorkDatabase_Impl { *; }
-keep class androidx.work.impl.** extends androidx.room.RoomDatabase { *; }

# R8 full mode keeps no constructor for "-keep class X" rules without members, and these library
# rules (WorkManager 2.7 and Glance, pulled in by Glance) rely on it: Glance's session work needs
# the InputMerger, and every widget tap creates its ActionCallback by reflection.
-keep class * extends androidx.work.InputMerger { <init>(); }
-keep class * extends androidx.work.Worker { <init>(...); }
-keep class * implements androidx.glance.appwidget.action.ActionCallback { <init>(); }
