package com.hamishnotes.app.store;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.pdf.PdfRenderer;
import android.os.ParcelFileDescriptor;

import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.annotation.RealObject;
import org.robolectric.util.ReflectionHelpers;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Robolectric has no pdfium, so PdfRenderer cannot draw a page on the JVM.
 * For the store screenshots this shadow renders each page with poppler (pdfinfo + pdftoppm)
 * on the host and draws it with the same transform PdfRenderer would use.
 */
@Implements(PdfRenderer.class)
public class HostPdfRenderer {
    static final class Doc {
        final File file;
        final List<float[]> sizes;
        final Map<Integer, Bitmap> pages = new HashMap<>();

        Doc(File file, List<float[]> sizes) {
            this.file = file;
            this.sizes = sizes;
        }
    }

    static final Map<Long, Doc> DOCS = new HashMap<>();
    private static final AtomicLong IDS = new AtomicLong(1000);
    private static final Pattern SIZE = Pattern.compile("Page\\s+(\\d+) size:\\s+([\\d.]+) x ([\\d.]+)");
    /** Width of the page image poppler renders; the app's widest request is 2400 px. */
    private static final int RENDER_PX = 2400;

    @RealObject private PdfRenderer real;

    @Implementation
    protected void __constructor__(ParcelFileDescriptor input) throws Exception {
        File copy = File.createTempFile("hamish-shot", ".pdf");
        copy.deleteOnExit();
        try (FileInputStream in = new FileInputStream(input.getFileDescriptor());
             FileOutputStream out = new FileOutputStream(copy)) {
            in.getChannel().position(0);
            byte[] buf = new byte[1 << 16];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        }
        List<float[]> sizes = pageSizes(copy);
        if (sizes.isEmpty()) throw new java.io.IOException("not a PDF");
        long id = IDS.incrementAndGet();
        synchronized (DOCS) {
            DOCS.put(id, new Doc(copy, sizes));
        }
        ReflectionHelpers.setField(real, "mInput", input);
        ReflectionHelpers.setField(real, "mNativeDocument", id);
        ReflectionHelpers.setField(real, "mPageCount", sizes.size());
        try {
            ReflectionHelpers.setField(real, "mTempPoint", new Point());
        } catch (Throwable ignored) {
        }
        try {
            ReflectionHelpers.setField(real, "mCloseGuard", Class.forName("dalvik.system.CloseGuard").getMethod("get").invoke(null));
        } catch (Throwable ignored) {
        }
    }

    @Implementation
    protected static long nativeOpenPageAndGetSize(long documentPtr, int pageIndex, Point outSize) {
        Doc d = doc(documentPtr);
        float[] s = d.sizes.get(pageIndex);
        outSize.x = Math.round(s[0]);
        outSize.y = Math.round(s[1]);
        return documentPtr * 100_000L + pageIndex;
    }

    @Implementation
    protected static void nativeClosePage(long pagePtr) {
    }

    @Implementation
    protected static void nativeClose(long documentPtr) {
    }

    @Implementation
    protected static boolean nativeScaleForPrinting(long documentPtr) {
        return true;
    }

    @Implementation
    protected static void nativeRenderPage(long documentPtr, long pagePtr, long bitmapHandle,
                                           int clipLeft, int clipTop, int clipRight, int clipBottom,
                                           long transformPtr, int renderMode) {
        throw new UnsupportedOperationException("handled by HostPdfPage.render");
    }

    static Doc doc(long id) {
        synchronized (DOCS) {
            Doc d = DOCS.get(id);
            if (d == null) throw new IllegalStateException("unknown document " + id);
            return d;
        }
    }

    static Bitmap pageImage(Doc d, int index) {
        synchronized (d) {
            Bitmap b = d.pages.get(index);
            if (b != null) return b;
            try {
                float wPts = d.sizes.get(index)[0];
                int dpi = Math.max(36, Math.round(RENDER_PX * 72f / wPts));
                File prefix = File.createTempFile("hamish-page", "");
                prefix.delete();
                Process p = new ProcessBuilder("pdftoppm", "-f", String.valueOf(index + 1), "-l", String.valueOf(index + 1),
                        "-r", String.valueOf(dpi), "-aa", "yes", "-aaVector", "yes", "-png", "-singlefile",
                        d.file.getAbsolutePath(), prefix.getAbsolutePath())
                        .redirectErrorStream(true).start();
                p.getInputStream().readAllBytes();
                p.waitFor();
                File png = new File(prefix.getAbsolutePath() + ".png");
                b = BitmapFactory.decodeFile(png.getAbsolutePath());
                png.delete();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            d.pages.put(index, b);
            return b;
        }
    }

    private static List<float[]> pageSizes(File f) throws Exception {
        Process p = new ProcessBuilder("pdfinfo", "-f", "1", "-l", "100000", f.getAbsolutePath())
                .redirectErrorStream(true).start();
        List<float[]> out = new ArrayList<>();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
            String line;
            while ((line = r.readLine()) != null) {
                Matcher m = SIZE.matcher(line);
                if (m.find()) out.add(new float[]{Float.parseFloat(m.group(2)), Float.parseFloat(m.group(3))});
            }
        }
        p.waitFor();
        return out;
    }

    /** Draws a page the way PdfRenderer.Page.render does: page points → bitmap pixels. */
    static void draw(Doc d, int index, Bitmap dest, Rect clip, Matrix transform) {
        float[] s = d.sizes.get(index);
        Bitmap src = pageImage(d, index);
        Canvas c = new Canvas(dest);
        if (clip != null) c.clipRect(clip);
        Matrix m = new Matrix();
        if (transform != null) m.set(transform);
        else m.setScale(dest.getWidth() / s[0], dest.getHeight() / s[1]);
        c.concat(m);
        c.scale(s[0] / src.getWidth(), s[1] / src.getHeight());
        Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
        c.drawBitmap(src, 0f, 0f, paint);
    }
}
