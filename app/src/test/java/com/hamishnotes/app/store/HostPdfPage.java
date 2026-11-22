package com.hamishnotes.app.store;

import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.pdf.PdfRenderer;

import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.annotation.RealObject;
import org.robolectric.util.ReflectionHelpers;

/** Page half of {@link HostPdfRenderer}: render() draws the poppler image of the page. */
@Implements(PdfRenderer.Page.class)
public class HostPdfPage {
    @RealObject private PdfRenderer.Page real;

    @Implementation
    protected void render(Bitmap destination, Rect destClip, Matrix transform, int renderMode) {
        long pagePtr = ReflectionHelpers.getField(real, "mNativePage");
        long docId = pagePtr / 100_000L;
        int index = (int) (pagePtr % 100_000L);
        HostPdfRenderer.draw(HostPdfRenderer.doc(docId), index, destination, destClip, transform);
    }
}
