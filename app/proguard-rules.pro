# Shizuku instantiates the user service by reflection in a separate (shell) process.
-keep class app.custo.shizuku.PrivilegedService { <init>(...); *; }
-keep class app.custo.IPrivilegedService** { *; }
