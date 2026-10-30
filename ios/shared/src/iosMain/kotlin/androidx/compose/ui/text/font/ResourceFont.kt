package androidx.compose.ui.text.font

import com.mynotes.app.bundleBytes
import com.mynotes.app.fontFiles

/** نفس Font(R.font.x) في أندرويد: الخط يُقرأ من مجلد font/ داخل حزمة التطبيق. */
fun Font(resId: Int, weight: FontWeight = FontWeight.Normal, style: FontStyle = FontStyle.Normal): Font {
    val name = fontFiles[resId]
    val bytes = bundleBytes("font/$name.ttf") ?: ByteArray(0)
    return androidx.compose.ui.text.platform.Font("mynotes-$name-${weight.weight}", bytes, weight, style)
}
