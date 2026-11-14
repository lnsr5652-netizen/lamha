# لمحة (Lamha) — معرض صور واستديو لأندرويد

المطور: أحمد الحاج (حزيز)
Copyright © 2026 Ahmed Al-Haj (Haziz). All Rights Reserved.

## بناء APK
1. افتح المجلد في Android Studio (Koala أو أحدث) وانتظر مزامنة Gradle (يحتاج إنترنت أول مرة).
2. من الطرفية داخل Android Studio: `./gradlew assembleDebug` (أو `gradle assembleDebug` إن لم يُنشأ wrapper).
3. الناتج: `app/build/outputs/apk/debug/app-debug.apk`
   أو من القائمة: Build ▸ Build APK(s).
4. لنسخة release: `./gradlew assembleRelease` (موقّعة بمفتاح debug مؤقتاً؛ بدّله بمفتاحك قبل النشر).

المتطلب: Android 11 (API 30) فما فوق.
