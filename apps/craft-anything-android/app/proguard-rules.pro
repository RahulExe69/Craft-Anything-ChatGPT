# Rust JNI exports are bound to the fully-qualified MainActivity class name.
# Keep it and its native declarations stable in minified release builds.
-keep class ai.storytold.craftanything.MainActivity { *; }

-keep class ai.storytold.craftanything.NativeBuildBridge { *; }
