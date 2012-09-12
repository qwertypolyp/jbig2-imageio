package com.levigo.jbig2.image;

import java.awt.image.WritableRaster;

import com.levigo.jbig2.Bitmap;
import com.levigo.jbig2.image.Scanline.ByteBiLevelPackedScanline;

public final class BitmapScanline extends Scanline {

  private Bitmap bitmap;
  private WritableRaster raster;

  private int[] lineBuffer;

  public BitmapScanline(final Bitmap src, final WritableRaster dst, final int width) {
    super(width);
    this.bitmap = src;
    this.raster = dst;
    lineBuffer = new int[length];
  }

  @Override
  protected void clear() {
    lineBuffer = new int[length];
  }

  @Override
  protected void fetch(int x, int y) {
    lineBuffer = new int[length]; // really required?
    int srcByteIdx = bitmap.getByteIndex(x, y);
    while (x < length) {
      final byte srcByte = (byte) ~bitmap.getByte(srcByteIdx++);
      final int bits = bitmap.getWidth() - x > 8 ? 8 : bitmap.getWidth() - x;
      for (int bitPosition = bits - 1; bitPosition >= 0; bitPosition--, x++) {
        if (((srcByte >> bitPosition) & 0x1) != 0)
          lineBuffer[x] = 255;
      }
    }
  }

  @Override
  protected void filter(int[] preShift, int[] postShift, Weighttab[] tabs, Scanline dst) {
    final BitmapScanline dstBitmapScanline = (BitmapScanline) dst;
    final int dstLength = dst.length;

    // start sum at 1<<shift-1 for rounding
    final int start = 1 << postShift[0] - 1;
    final int abuf[] = lineBuffer;
    final int bbuf[] = dstBitmapScanline.lineBuffer;

    // the next two blocks are duplicated except for the missing shift operation if preShift==0.
    final int preShift0 = preShift[0];
    final int postShift0 = postShift[0];
    if (preShift0 != 0) {
      for (int bp = 0, b = 0; b < dstLength; b++) {
        final Weighttab wtab = tabs[b];
        final int an = wtab.weights.length;

        int sum = start;
        for (int wp = 0, ap = wtab.i0; wp < an && ap < abuf.length; wp++) {
          sum += wtab.weights[wp] * (abuf[ap++] >> preShift0);
        }

        final int t = sum >> postShift0;
        bbuf[bp++] = t < 0 ? 0 : t > 255 ? 255 : t;
      }
    } else {
      for (int bp = 0, b = 0; b < dstLength; b++) {
        final Weighttab wtab = tabs[b];
        final int an = wtab.weights.length;

        int sum = start;
        for (int wp = 0, ap = wtab.i0; wp < an && ap < abuf.length; wp++) {
          sum += wtab.weights[wp] * abuf[ap++];
        }

        bbuf[bp++] = sum >> postShift0;
      }
    }
  }

  @Override
  protected void accumulate(int weight, Scanline dst) {
    final BitmapScanline dstBitmapScanline = (BitmapScanline) dst;

    final int abuf[] = lineBuffer;
    final int bbuf[] = dstBitmapScanline.lineBuffer;

    for (int b = 0; b < bbuf.length; b++)
      bbuf[b] += weight * abuf[b];
  }

  @Override
  protected void shift(int[] shift) {
    final int shift0 = shift[0];
    final int half = 1 << shift0 - 1;

    final int abuf[] = lineBuffer;

    for (int b = 0; b < abuf.length; b++) {
      final int t = abuf[b] + half >> shift0;
      abuf[b] = t < 0 ? 0 : t > 255 ? 255 : t;
    }
  }

  @Override
  protected void store(int x, int y) {
    raster.setSamples(x, y, length, 1, 0, lineBuffer);
  }

}