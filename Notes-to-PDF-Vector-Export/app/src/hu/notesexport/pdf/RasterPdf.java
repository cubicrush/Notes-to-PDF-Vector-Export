package hu.notesexport.pdf;
import android.graphics.*;
import android.graphics.pdf.*;
import android.os.ParcelFileDescriptor;
import java.io.*;

final class RasterPdf {
    static void convert(File source, File target) throws IOException {
        PdfDocument output = new PdfDocument();
        boolean success = false;
        try (ParcelFileDescriptor descriptor = ParcelFileDescriptor.open(source, ParcelFileDescriptor.MODE_READ_ONLY);
             PdfRenderer input = new PdfRenderer(descriptor)) {
            for (int i = 0; i < input.getPageCount(); i++) {
                if (Thread.currentThread().isInterrupted()) throw new IOException("Export cancelled");
                try (PdfRenderer.Page page = input.openPage(i)) {
                    double scale = Math.min(120.0 / 72.0, Math.sqrt(2000000.0 / ((double) page.getWidth() * page.getHeight())));
                    Bitmap bitmap = Bitmap.createBitmap(Math.max(1, (int) (page.getWidth() * scale)), Math.max(1, (int) (page.getHeight() * scale)), Bitmap.Config.ARGB_8888);
                    try {
                        bitmap.eraseColor(Color.WHITE);
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT);
                        PdfDocument.Page dest = output.startPage(new PdfDocument.PageInfo.Builder(page.getWidth(), page.getHeight(), i + 1).create());
                        try { dest.getCanvas().drawBitmap(bitmap, null, new Rect(0, 0, page.getWidth(), page.getHeight()), new Paint(Paint.FILTER_BITMAP_FLAG)); }
                        finally { output.finishPage(dest); }
                    } finally { bitmap.recycle(); }
                }
            }
            try (OutputStream stream = new FileOutputStream(target)) { output.writeTo(stream); }
            success = true;
        } finally { output.close(); if (!success) target.delete(); }
    }
}
