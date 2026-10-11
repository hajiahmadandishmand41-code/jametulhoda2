# Add project specific ProGuard rules here.
# R8 (full mode) with the default optimized Android rules keeps Compose, OkHttp,
# Media3 and Coil working; only app-owned reflection would need keep rules.

# OkHttp and Okio ship their own consumer rules.
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# Media3/ExoPlayer keeps player internals through its own consumer rules; the
# app only touches Builder/PlayerView APIs.

# Keep line numbers for readable crash reports without leaking file names.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
