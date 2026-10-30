package android.os

import java.io.File
import java.io.FileNotFoundException

/** في iOS يكفي مسار الملف (PdfRenderer يفتحه بنفسه). */
class ParcelFileDescriptor private constructor(val file: File) {
    fun close() {}

    companion object {
        const val MODE_READ_ONLY = 0x10000000

        fun open(file: File, @Suppress("UNUSED_PARAMETER") mode: Int): ParcelFileDescriptor {
            if (!file.exists()) throw FileNotFoundException(file.path)
            return ParcelFileDescriptor(file)
        }
    }
}
