# LotteryNet release R8 rules.
# Keep the small set of classes that cross Android, JSON, printing, and crash-reporting boundaries.

-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod, SourceFile, LineNumberTable

-keep class com.lotterynet.pro.BuildConfig { *; }
-keep class com.lotterynet.pro.LotteryNetApp { *; }

# App data models are saved to local JSON/cache and shared across legacy + Edge Function flows.
-keep class com.lotterynet.pro.core.model.** { *; }

# Critical infrastructure uses hand-built JSON, HTTP, sync payloads, and persisted keys.
-keep class com.lotterynet.pro.core.remote.** { *; }
-keep class com.lotterynet.pro.core.storage.** { *; }
-keep class com.lotterynet.pro.core.sync.** { *; }
-keep class com.lotterynet.pro.core.config.** { *; }
-keep class com.lotterynet.pro.core.auth.** { *; }
-keep class com.lotterynet.pro.core.master.** { *; }
-keep class com.lotterynet.pro.core.recharge.** { *; }
-keep class com.lotterynet.pro.core.update.** { *; }
-keep class com.lotterynet.pro.ui.update.** { *; }

# Ticket, voucher, QR, and printer paths must stay stable on POS devices.
-keep class com.lotterynet.pro.core.printing.** { *; }
-keep class com.lotterynet.pro.core.export.** { *; }
-keep class com.lotterynet.pro.core.catalog.** { *; }
-keep class com.google.zxing.** { *; }
-keep class com.journeyapps.barcodescanner.** { *; }
-keep class com.caverock.androidsvg.** { *; }

# Diagnostics bridge for crash reporting keeps references stable.
-keep class com.lotterynet.pro.core.diagnostics.** { *; }

# Bluetooth and vendor POS printer implementations vary by device firmware.
-dontwarn android.bluetooth.**
-dontwarn com.sunmi.**
-dontwarn woyou.**
-dontwarn com.pos.**
