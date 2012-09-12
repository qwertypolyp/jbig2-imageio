package com.levigo.jbig2.image;

import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.DataBuffer;
import java.awt.image.IndexColorModel;
import java.awt.image.WritableRaster;

import javax.imageio.ImageReadParam;

import com.levigo.jbig2.Bitmap;
import com.levigo.jbig2.JBIG2ReadParam;
import com.levigo.jbig2.util.CombinationOperator;

public class Bitmaps {

  public static WritableRaster asRaster(Bitmap bitmap) {
    if (bitmap == null)
      throw new IllegalArgumentException("bitmap must not be null");

    final JBIG2ReadParam param = new JBIG2ReadParam(1, 1, 0, 0, new Rectangle(0, 0, bitmap.getWidth(),
        bitmap.getHeight()), new Dimension(bitmap.getWidth(), bitmap.getHeight()));

    return asRaster(bitmap, param);
  }

  public static WritableRaster asRaster(Bitmap bitmap, ImageReadParam param) {
    if (bitmap == null)
      throw new IllegalArgumentException("bitmap must not be null");

    if (param == null)
      throw new IllegalArgumentException("param must not be null");

    final Dimension sourceRenderSize = param.getSourceRenderSize();
    final double scaleX = sourceRenderSize.getWidth() / bitmap.getWidth();
    final double scaleY = sourceRenderSize.getHeight() / bitmap.getHeight();

    final Rectangle roi = param.getSourceRegion();
    if (!bitmap.getBounds().equals(roi)) {
      bitmap = Bitmaps.extract(roi, bitmap);
    }
    
    // TODO subsampling

    final Rectangle dstBounds = new Rectangle(0, 0, (int) Math.round(bitmap.getWidth() * scaleX),
        (int) Math.round(bitmap.getHeight() * scaleY));

    final WritableRaster dst = WritableRaster.createInterleavedRaster(DataBuffer.TYPE_BYTE, dstBounds.width,
        dstBounds.height, 1, new Point());

    final boolean requiresScaling = scaleX != 1 || scaleY != 1;
    if (requiresScaling) {
      Resize resizer = new Resize(scaleX, scaleY) {

        @Override
        protected Scanline createScanline(Object src, Object dst, int length) {
          if (src == null)
            throw new IllegalArgumentException("src must not be null");

          if (!(src instanceof Bitmap))
            throw new IllegalArgumentException("src must be from type " + Bitmap.class.getName());

          if (dst == null)
            throw new IllegalArgumentException("dst must not be null");

          if (!(dst instanceof WritableRaster))
            throw new IllegalArgumentException("dst must be from type " + WritableRaster.class.getName());

          return new BitmapScanline((Bitmap) src, (WritableRaster) dst, length);
        }
      };

      Filter filter = Filter.byType(FilterType.Gaussian);
      resizer.resize(bitmap, bitmap.getBounds(), dst, dstBounds, filter, filter);

      System.out.println("Raster created and scaling applied");
    } else {
      int byteIndex = 0;
      for (int y = 0; y < bitmap.getHeight(); y++) {
        for (int x = 0; x < bitmap.getWidth();) {
          final byte oldByte = (byte) ~bitmap.getByte(byteIndex++);
          final int minorWidth = bitmap.getWidth() - x > 8 ? 8 : bitmap.getWidth() - x;
          for (int minorX = minorWidth - 1; minorX >= 0; minorX--, x++) {
            dst.setSample(x, y, 0, (oldByte >> minorX) & 0x1);
          }
        }
        System.out.println("Raster created without scaling");
      }

    }
    return dst;
  }

  public static BufferedImage asBufferedImage(Bitmap bitmap) {
    if (bitmap == null)
      throw new IllegalArgumentException("bitmap must not be null");

    final JBIG2ReadParam param = new JBIG2ReadParam(1, 1, 0, 0, new Rectangle(0, 0, bitmap.getWidth(),
        bitmap.getHeight()), new Dimension(bitmap.getWidth(), bitmap.getHeight()));

    return asBufferedImage(bitmap, param);
  }

  public static BufferedImage asBufferedImage(Bitmap bitmap, ImageReadParam param) {
    if (bitmap == null)
      throw new IllegalArgumentException("bitmap must not be null");

    if (param == null)
      throw new IllegalArgumentException("param must not be null");

    final WritableRaster raster = asRaster(bitmap, param);
    final Dimension sourceRenderSize = param.getSourceRenderSize();
    final double scaleX = sourceRenderSize.getWidth() / bitmap.getWidth();
    final double scaleY = sourceRenderSize.getHeight() / bitmap.getHeight();

    ColorModel cm = null;
    final boolean isScaled = scaleX != 1 || scaleY != 1;
    if (isScaled) {
      final int size = 256;
      final int divisor = size - 1;

      final byte[] gray = new byte[size];
      for (int i = size - 1, s = 0; i >= 0; i--, s++) {
        gray[i] = (byte) (255 - s * 255 / divisor);
      }
      cm = new IndexColorModel(8, size, gray, gray, gray);
    } else {

      cm = new IndexColorModel(8, 2, //
          new byte[]{
              0x00, (byte) 0xff
          }, new byte[]{
              0x00, (byte) 0xff
          }, new byte[]{
              0x00, (byte) 0xff
          });
    }


    return new BufferedImage(cm, raster, false, null);
  }

  /**
   * Returns the specified rectangle area of the bitmap.
   * 
   * @param roi - A {@link Rectangle} that specifies the requested image section.
   * @return A {@code Bitmap} that represents the requested image section.
   */
  public static Bitmap extract(Rectangle roi, Bitmap src) {

    Bitmap dst = new Bitmap(roi.width, roi.height);

    int sourceUpShift = roi.x & 0x07;
    int sourceDownShift = 8 - sourceUpShift;
    int firstTargetByteOfLine = 0;

    int padding = (8 - dst.getWidth() & 0x07);
    int firstSourceByteOfLine = src.getByteIndex(roi.x, roi.y);
    int lastSourceByteOfLine = src.getByteIndex(roi.x + roi.width - 1, roi.y);
    boolean usePadding = dst.getRowStride() == lastSourceByteOfLine + 1 - firstSourceByteOfLine;
    byte value;

    for (int y = roi.y; y < roi.getMaxY(); y++) {

      int sourceOffset = firstSourceByteOfLine;
      int targetOffset = firstTargetByteOfLine;

      if (firstSourceByteOfLine == lastSourceByteOfLine) {
        value = (byte) (src.getByte(sourceOffset) << sourceUpShift);
        value = unpad(padding, value);
        dst.setByte(targetOffset, value);
      } else if (sourceUpShift == 0) {
        for (int x = firstSourceByteOfLine; x <= lastSourceByteOfLine; x++) {
          value = src.getByte(sourceOffset++);

          if (x == lastSourceByteOfLine && usePadding) {
            value = unpad(padding, value);
          }
          dst.setByte(targetOffset++, value);
        }
      } else {
        copyLine(src, dst, sourceUpShift, sourceDownShift, padding, firstSourceByteOfLine, lastSourceByteOfLine,
            usePadding, sourceOffset, targetOffset);
      }

      firstSourceByteOfLine += src.getRowStride();
      lastSourceByteOfLine += src.getRowStride();
      firstTargetByteOfLine += dst.getRowStride();
    }

    return dst;
  }

  private static void copyLine(Bitmap src, Bitmap dst, int sourceUpShift, int sourceDownShift, int padding,
      int firstSourceByteOfLine, int lastSourceByteOfLine, boolean usePadding, int sourceOffset, int targetOffset) {

    byte value;

    for (int x = firstSourceByteOfLine; x < lastSourceByteOfLine; x++) {

      if (sourceOffset + 1 < src.getSize()) {
        boolean isLastByte = x + 1 == lastSourceByteOfLine;

        value = (byte) (src.getByte(sourceOffset++) << sourceUpShift | (src.getByte(sourceOffset) & 0xff) >>> sourceDownShift);

        if (isLastByte && !usePadding) {
          value = unpad(padding, value);
        }

        dst.setByte(targetOffset++, value);

        if (isLastByte && usePadding) {
          value = unpad(padding, (byte) ((src.getByte(sourceOffset) & 0xff) << sourceUpShift));
          dst.setByte(targetOffset, value);
        }

      } else {
        value = (byte) (src.getByte(sourceOffset++) << sourceUpShift & 0xff);
        dst.setByte(targetOffset++, value);
      }
    }
  }

  /**
   * Removes unnecessary bits from a byte.
   * 
   * @param padding - The amount of unnecessary bits.
   * @param value - The byte that should be cleaned up.
   * @return A cleaned byte.
   */
  private static byte unpad(int padding, byte value) {
    return (byte) (value >> padding << padding);
  }

  /**
   * The method combines two given bytes with an logical operator.
   * <p>
   * The JBIG2 Standard specifies 5 possible combinations of bytes.<br>
   * <p>
   * <b>Hint:</b> Please take a look at ISO/IEC 14492:2001 (E) for detailed definition and
   * description of the operators.
   * 
   * @param value1 - The value that should be combined with value2.
   * @param value2 - The value that should be combined with value1.
   * @param op - The specified combination operator.
   * 
   * @return The combination result.
   */
  public static byte combineBytes(byte value1, byte value2, CombinationOperator op) {

    switch (op){
      case OR :
        return (byte) (value2 | value1);
      case AND :
        return (byte) (value2 & value1);
      case XOR :
        return (byte) (value2 ^ value1);
      case XNOR :
        return (byte) ~(value1 ^ value2);
      case REPLACE :
      default :
        // Old value is replaced by new value.
        return value2;
    }
  }

  /**
   * This method combines a given bitmap with the current instance.
   * <p>
   * Parts of the bitmap to blit that are outside of the target bitmap will be ignored.
   * 
   * @param src - The bitmap that should be combined with the one of the current instance.
   * @param x - The x coordinate where the upper left corner of the bitmap to blit should be
   *          positioned.
   * @param y - The y coordinate where the upper left corner of the bitmap to blit should be
   *          positioned.
   * @param combinationOperator - The combination operator for combining two pixels.
   */
  public static void blit(Bitmap src, Bitmap dst, int x, int y, CombinationOperator combinationOperator) {

    int startLine = 0;
    int sourceStartByteIndex = 0;
    int sourceEndByteIndex = (src.getRowStride() - 1);

    // Ignore those parts of the source bitmap which would be placed outside the target bitmap.
    if (x < 0) {
      sourceStartByteIndex = -x;
      x = 0;
    } else if (x + src.getWidth() > dst.getWidth()) {
      sourceEndByteIndex -= (src.getWidth() + x - dst.getWidth());
    }

    if (y < 0) {
      startLine = -y;
      y = 0;
      sourceStartByteIndex += src.getRowStride();
      sourceEndByteIndex += src.getRowStride();
    } else if (y + src.getHeight() > dst.getHeight()) {
      startLine = src.getHeight() + y - dst.getHeight();
    }

    int shiftVal1 = x & 0x07;
    int shiftVal2 = 8 - shiftVal1;

    int padding = src.getWidth() & 0x07;
    int toShift = shiftVal2 - padding;

    boolean useShift = (shiftVal2 & 0x07) != 0;
    boolean specialCase = src.getWidth() <= ((sourceEndByteIndex - sourceStartByteIndex) << 3) + shiftVal2;

    int targetStartByte = dst.getByteIndex(x, y);

    if (!useShift) {
      blitUnshifted(src, dst, startLine, targetStartByte, sourceStartByteIndex, sourceEndByteIndex, combinationOperator);
    } else if (specialCase) {
      blitSpecialShifted(src, dst, startLine, targetStartByte, sourceStartByteIndex, sourceEndByteIndex, toShift,
          shiftVal1, shiftVal2, combinationOperator);
    } else {
      blitShifted(src, dst, startLine, targetStartByte, sourceStartByteIndex, sourceEndByteIndex, toShift, shiftVal1,
          shiftVal2, combinationOperator, padding);
    }
  }

  private static void blitUnshifted(Bitmap src, Bitmap dst, int startLine, int targetStartByte,
      int sourceStartByteIndex, int sourceEndByteIndex, CombinationOperator op) {

    for (int line = startLine; line < src.getHeight(); line++, targetStartByte += dst.getRowStride(), sourceStartByteIndex += src.getRowStride(), sourceEndByteIndex += src.getRowStride()) {
      int targetByteIndex = targetStartByte;

      // Go through the bytes in a line of the Symbol
      for (int sourceByteIndex = sourceStartByteIndex; sourceByteIndex <= sourceEndByteIndex; sourceByteIndex++) {
        byte oldByte = dst.getByte(targetByteIndex);
        byte newByte = src.getByte(sourceByteIndex);
        dst.setByte(targetByteIndex++, Bitmaps.combineBytes(oldByte, newByte, op));
      }
    }
  }

  private static void blitSpecialShifted(Bitmap bitmapToBlit, Bitmap dst, int startLine, int targetStartByte,
      int sourceStartByteIndex, int sourceEndByteIndex, int toShift, int shiftVal1, int shiftVal2,
      CombinationOperator op) {

    for (int line = startLine; line < bitmapToBlit.getHeight(); line++, targetStartByte += dst.getRowStride(), sourceStartByteIndex += bitmapToBlit.getRowStride(), sourceEndByteIndex += bitmapToBlit.getRowStride()) {
      short register = 0;
      int targetByteIndex = targetStartByte;

      // Go through the bytes in a line of the Symbol
      for (int sourceByteIndex = sourceStartByteIndex; sourceByteIndex <= sourceEndByteIndex; sourceByteIndex++) {
        byte oldByte = dst.getByte(targetByteIndex);
        register = (short) ((register | bitmapToBlit.getByte(sourceByteIndex) & 0xff) << shiftVal2);
        byte newByte = (byte) (register >> 8);

        if (sourceByteIndex == sourceEndByteIndex) {
          newByte = unpad(toShift, newByte);
        }

        dst.setByte(targetByteIndex++, Bitmaps.combineBytes(oldByte, newByte, op));
        register <<= shiftVal1;
      }
    }
  }

  private static void blitShifted(Bitmap src, Bitmap dst, int startLine, int targetStartByte, int sourceStartByteIndex,
      int sourceEndByteIndex, int toShift, int shiftVal1, int shiftVal2, CombinationOperator op, int padding) {

    for (int line = startLine; line < src.getHeight(); line++, targetStartByte += dst.getRowStride(), sourceStartByteIndex += src.getRowStride(), sourceEndByteIndex += src.getRowStride()) {
      short register = 0;
      int targetByteIndex = targetStartByte;

      // Go through the bytes in a line of the Symbol
      for (int sourceByteIndex = sourceStartByteIndex; sourceByteIndex <= sourceEndByteIndex; sourceByteIndex++) {
        byte oldByte = dst.getByte(targetByteIndex);
        register = (short) ((register | src.getByte(sourceByteIndex) & 0xff) << shiftVal2);

        byte newByte = (byte) (register >> 8);
        dst.setByte(targetByteIndex++, Bitmaps.combineBytes(oldByte, newByte, op));

        register <<= shiftVal1;

        if (sourceByteIndex == sourceEndByteIndex) {
          newByte = (byte) (register >> (8 - shiftVal2));

          if (padding != 0) {
            newByte = unpad(8 + toShift, newByte);
          }

          oldByte = dst.getByte(targetByteIndex);
          dst.setByte(targetByteIndex, Bitmaps.combineBytes(oldByte, newByte, op));
        }
      }
    }
  }


}
