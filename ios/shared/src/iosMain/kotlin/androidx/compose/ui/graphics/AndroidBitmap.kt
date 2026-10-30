package androidx.compose.ui.graphics

/** نفس دالة Compose في أندرويد: صورة android.graphics.Bitmap (هنا فوق Skia) كـ ImageBitmap دون نسخ. */
fun android.graphics.Bitmap.asImageBitmap(): ImageBitmap = skia.asComposeImageBitmap()
