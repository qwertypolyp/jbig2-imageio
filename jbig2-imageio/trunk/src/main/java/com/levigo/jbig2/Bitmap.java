/**
 * Copyright (C) 1995-2010 levigo holding gmbh.
 * 
 * This program is free software: you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 * even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License along with this program. If
 * not, see <http://www.gnu.org/licenses/>.
 */

package com.levigo.jbig2;

import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.DataBuffer;
import java.awt.image.DataBufferByte;
import java.awt.image.IndexColorModel;
import java.awt.image.Raster;
import java.awt.image.SampleModel;
import java.awt.image.SinglePixelPackedSampleModel;
import java.awt.image.WritableRaster;

import com.levigo.jbig2.util.CombinationOperator;

/**
 * This class represents a bi-level image that is organized like a bitmap.
 * 
 * @author <a href="mailto:m.krzikalla@levigo.de">Matth�us Krzikalla</a>
 */
public class Bitmap {

  class ResultImageData {

    private JBIG2ReadParam param;

    private Rectangle sourceRegion;
    private Dimension sourceRenderSize;
    private byte[] resultByteArray;

    private boolean isScaled;
    private boolean isSubsampled;

    private int sourceWidth;
    private int sourceHeight;

    private int targetWidth;
    private int targetHeight;

    private int spreadFactorX;
    private int spreadFactorY;

    ResultImageData(JBIG2ReadParam param) {
      computeRequestedImageData(param);
    }

    public byte[] getResultByteArray() {
      return resultByteArray;
    }

    private void computeRequestedImageData(JBIG2ReadParam param) {
      initStartParameters(param);
      determineIfScalingNecessary();
      computeSourceRegion(param);
      cropSourceRegionFromResult();
      if (isScaled) {
        computeSpreadFactors();
        scaleResultByteArray();
      }
      determineIfSubsamplingNecessary();
      computeTargetSize(param);
    }

    private void initStartParameters(JBIG2ReadParam param) {
      this.param = param;
      this.sourceRegion = param.getSourceRegion();
      this.sourceRenderSize = param.getSourceRenderSize();
      this.resultByteArray = pixelArray;
      this.sourceWidth = sourceRegion.width;
      this.sourceHeight = sourceRegion.height;
    }

    private void computeTargetSize(JBIG2ReadParam param) {
      if (isSubsampled) {
        targetWidth = getSubsampledTargetWidth();
        targetHeight = getSubsampledTargetHeight();
        resultByteArray = subSample(resultByteArray, param, targetWidth, targetHeight, sourceWidth, sourceHeight);
      } else {
        targetWidth = sourceWidth;
        targetHeight = sourceHeight;
      }
    }

    private int getSubsampledTargetWidth() {
      return (int) Math.ceil((double) sourceWidth / (double) param.getSourceXSubsampling());
    }

    private int getSubsampledTargetHeight() {
      return (int) Math.ceil((double) sourceHeight / (double) param.getSourceYSubsampling());
    }

    private void determineIfScalingNecessary() {
      this.isScaled = isScaled(sourceRenderSize);
    }

    private void computeSourceRegion(JBIG2ReadParam param) {
      this.sourceRegion = sourceRegion.intersection(new Rectangle(0, 0, width, height));
      if (isScaled) {
        this.sourceRegion = scaleSourceRegion(sourceRegion, sourceRenderSize);
      }
    }

    private void cropSourceRegionFromResult() {
      if (width != sourceRegion.width || height != sourceRegion.height) {
        resultByteArray = getSourceRegion(resultByteArray, sourceRegion);
      }
    }

    private void computeSpreadFactors() {
      this.spreadFactorX = calculateSpreadFactorX(sourceRenderSize);
      this.spreadFactorY = calculateSpreadFactorY(sourceRenderSize);
    }

    private void scaleResultByteArray() {
      resultByteArray = scaleDownBiLevelToGrayscale(sourceRegion, spreadFactorX, spreadFactorY, sourceWidth,
          sourceHeight, resultByteArray);
    }

    private void determineIfSubsamplingNecessary() {
      this.isSubsampled = param.getSourceXSubsampling() != 1 || param.getSourceYSubsampling() != 1;
    }
  }

  /** The height of the bitmap in pixels. */
  private final int height;

  /** The width of the bitmap in pixels. */
  private final int width;

  /** The amount of bytes used per row. */
  private final int rowStride;

  /** 8 pixels per byte, 0 for white, 1 for black */
  private byte[] byteArray;

  /** 1 pixel per byte, 0 for white, 255 for black */
  private byte[] pixelArray;

  /**
   * Creates an instance of a blank image.<br>
   * The image data is stored in a byte array. Each pixels is stored as one bit, so that each byte
   * contains 8 pixel. A pixel has by default the value {@code 0} for white and {@code 1} for black. <br>
   * Row stride means the amount of bytes per line. It is computed automatically and fills the pad
   * bits with 0.<br>
   * 
   * @param height - The real height of the bitmap in pixels.
   * @param width - The real width of the bitmap in pixels.
   */
  public Bitmap(int width, int height) {
    this.height = height;
    this.width = width;
    this.rowStride = (width + 7) >> 3;

    byteArray = new byte[this.height * this.rowStride];
  }

  /**
   * Returns the value of a pixel specified by the given coordinates.
   * <p>
   * By default, the value is {@code 0} for a white pixel and {@code 1} for a black pixel. The value
   * is placed in the rightmost bit in the byte.
   * 
   * @param x - The x coordinate of the pixel.
   * @param y - The y coordinate of the pixel.
   * @return The value of a pixel.
   */
  public byte getPixel(int x, int y) {
    int byteIndex = this.getByteIndex(x, y);
    int bitOffset = this.getBitOffset(x);

    int toShift = 7 - bitOffset;
    return (byte) ((this.getByte(byteIndex) >> toShift) & 0x01);
  }

  /**
   * This method combines a given bitmap with the current instance.
   * <p>
   * Parts of the bitmap to blit that are outside of the target bitmap will be ignored.
   * 
   * @param bitmapToBlit - The bitmap that should be combined with the one of the current instance.
   * @param targetStartX - The x coordinate where the upper left corner of the bitmap to blit should
   *          be positioned.
   * @param targetStartY - The y coordinate where the upper left corner of the bitmap to blit should
   *          be positioned.
   * @param combinationOperator - The combination operator for combining two pixels.
   */
  public void blit(Bitmap bitmapToBlit, int targetStartX, int targetStartY, CombinationOperator combinationOperator) {

    int startLine = 0;
    int sourceStartByteIndex = 0;
    int sourceEndByteIndex = (bitmapToBlit.getRowStride() - 1);

    // Ignore those parts of the source bitmap which would be placed outside the target bitmap.
    if (targetStartX < 0) {
      sourceStartByteIndex = -targetStartX;
      targetStartX = 0;
    } else if (targetStartX + bitmapToBlit.width > this.width) {
      sourceEndByteIndex -= (bitmapToBlit.width + targetStartX - this.width);
    }

    if (targetStartY < 0) {
      startLine = -targetStartY;
      targetStartY = 0;
      sourceStartByteIndex += bitmapToBlit.getRowStride();
      sourceEndByteIndex += bitmapToBlit.getRowStride();
    } else if (targetStartY + bitmapToBlit.height > this.height) {
      startLine = bitmapToBlit.getHeight() + targetStartY - this.height;
    }

    int shiftVal1 = targetStartX & 0x07;
    int shiftVal2 = 8 - shiftVal1;

    int padding = bitmapToBlit.getWidth() & 0x07;
    int toShift = shiftVal2 - padding;

    boolean useShift = (shiftVal2 & 0x07) != 0;
    boolean specialCase = bitmapToBlit.getWidth() <= ((sourceEndByteIndex - sourceStartByteIndex) << 3) + shiftVal2;

    int targetStartByte = this.getByteIndex(targetStartX, targetStartY);

    if (!useShift) {
      blitWithoutShift(startLine, bitmapToBlit, targetStartByte, sourceStartByteIndex, sourceEndByteIndex,
          combinationOperator);
    } else if (specialCase) {
      blitWithShiftInSpecialCase(startLine, bitmapToBlit, targetStartByte, sourceStartByteIndex, sourceEndByteIndex,
          toShift, shiftVal1, shiftVal2, combinationOperator);
    } else {
      blitWithShift(startLine, bitmapToBlit, targetStartByte, sourceStartByteIndex, sourceEndByteIndex, toShift,
          shiftVal1, shiftVal2, combinationOperator, padding);
    }
  }

  private void blitWithoutShift(int startLine, Bitmap bitmapToBlit, int targetStartByte, int sourceStartByteIndex,
      int sourceEndByteIndex, CombinationOperator op) {

    for (int line = startLine; line < bitmapToBlit.getHeight(); line++, targetStartByte += this.getRowStride(), sourceStartByteIndex += bitmapToBlit.getRowStride(), sourceEndByteIndex += bitmapToBlit.getRowStride()) {
      int targetByteIndex = targetStartByte;

      // Go through the bytes in a line of the Symbol
      for (int sourceByteIndex = sourceStartByteIndex; sourceByteIndex <= sourceEndByteIndex; sourceByteIndex++) {
        byte oldByte = this.getByte(targetByteIndex);
        byte newByte = bitmapToBlit.getByte(sourceByteIndex);
        this.setByte(targetByteIndex++, combineBytes(oldByte, newByte, op));
      }
    }
  }

  private void blitWithShiftInSpecialCase(int startLine, Bitmap bitmapToBlit, int targetStartByte,
      int sourceStartByteIndex, int sourceEndByteIndex, int toShift, int shiftVal1, int shiftVal2,
      CombinationOperator op) {

    for (int line = startLine; line < bitmapToBlit.getHeight(); line++, targetStartByte += this.getRowStride(), sourceStartByteIndex += bitmapToBlit.getRowStride(), sourceEndByteIndex += bitmapToBlit.getRowStride()) {
      short register = 0;
      int targetByteIndex = targetStartByte;

      // Go through the bytes in a line of the Symbol
      for (int sourceByteIndex = sourceStartByteIndex; sourceByteIndex <= sourceEndByteIndex; sourceByteIndex++) {
        byte oldByte = this.getByte(targetByteIndex);
        register = (short) ((register | bitmapToBlit.getByte(sourceByteIndex) & 0xff) << shiftVal2);
        byte newByte = (byte) (register >> 8);

        if (sourceByteIndex == sourceEndByteIndex) {
          newByte = removeBytePadding(toShift, newByte);
        }

        this.setByte(targetByteIndex++, combineBytes(oldByte, newByte, op));
        register <<= shiftVal1;
      }
    }
  }

  private void blitWithShift(int startLine, Bitmap bitmapToBlit, int targetStartByte, int sourceStartByteIndex,
      int sourceEndByteIndex, int toShift, int shiftVal1, int shiftVal2, CombinationOperator op, int padding) {

    for (int line = startLine; line < bitmapToBlit.getHeight(); line++, targetStartByte += this.getRowStride(), sourceStartByteIndex += bitmapToBlit.getRowStride(), sourceEndByteIndex += bitmapToBlit.getRowStride()) {
      short register = 0;
      int targetByteIndex = targetStartByte;

      // Go through the bytes in a line of the Symbol
      for (int sourceByteIndex = sourceStartByteIndex; sourceByteIndex <= sourceEndByteIndex; sourceByteIndex++) {
        byte oldByte = this.getByte(targetByteIndex);
        register = (short) ((register | bitmapToBlit.getByte(sourceByteIndex) & 0xff) << shiftVal2);

        byte newByte = (byte) (register >> 8);
        this.setByte(targetByteIndex++, combineBytes(oldByte, newByte, op));

        register <<= shiftVal1;

        if (sourceByteIndex == sourceEndByteIndex) {
          newByte = (byte) (register >> (8 - shiftVal2));

          if (padding != 0) {
            newByte = removeBytePadding(8 + toShift, newByte);
          }

          oldByte = this.getByte(targetByteIndex);
          this.setByte(targetByteIndex, combineBytes(oldByte, newByte, op));
        }
      }
    }
  }

  /**
   * Returns the specified rectangle area of the bitmap.
   * 
   * @param regionOfInterest - A {@link Rectangle} that specifies the requested image section.
   * @return A {@code Bitmap} that represents the requested image section.
   */
  public Bitmap getRegionOfInterest(Rectangle regionOfInterest) {

    Bitmap targetBitmap = new Bitmap(regionOfInterest.width, regionOfInterest.height);

    int sourceUpShift = regionOfInterest.x & 0x07;
    int sourceDownShift = 8 - sourceUpShift;
    int firstTargetByteOfLine = 0;

    int padding = (8 - targetBitmap.getWidth() & 0x07);
    int firstSourceByteOfLine = this.getByteIndex(regionOfInterest.x, regionOfInterest.y);
    int lastSourceByteOfLine = this.getByteIndex(regionOfInterest.x + regionOfInterest.width - 1, regionOfInterest.y);
    boolean usePadding = targetBitmap.rowStride == lastSourceByteOfLine + 1 - firstSourceByteOfLine;
    byte value;

    for (int y = regionOfInterest.y; y < regionOfInterest.getMaxY(); y++) {

      int sourceOffset = firstSourceByteOfLine;
      int targetOffset = firstTargetByteOfLine;

      if (firstSourceByteOfLine == lastSourceByteOfLine) {
        value = (byte) (this.getByte(sourceOffset) << sourceUpShift);
        value = removeBytePadding(padding, value);
        targetBitmap.setByte(targetOffset, value);
      } else if (sourceUpShift == 0) {
        for (int x = firstSourceByteOfLine; x <= lastSourceByteOfLine; x++) {
          value = this.getByte(sourceOffset++);

          if (x == lastSourceByteOfLine && usePadding) {
            value = removeBytePadding(padding, value);
          }
          targetBitmap.setByte(targetOffset++, value);
        }
      } else {
        copyLine(targetBitmap, sourceUpShift, sourceDownShift, padding, firstSourceByteOfLine, lastSourceByteOfLine,
            usePadding, sourceOffset, targetOffset);
      }

      firstSourceByteOfLine += this.rowStride;
      lastSourceByteOfLine += this.rowStride;
      firstTargetByteOfLine += targetBitmap.rowStride;
    }

    return targetBitmap;
  }

  private void copyLine(Bitmap targetBitmap, int sourceUpShift, int sourceDownShift, int padding,
      int firstSourceByteOfLine, int lastSourceByteOfLine, boolean usePadding, int sourceOffset, int targetOffset) {

    byte value;

    for (int x = firstSourceByteOfLine; x < lastSourceByteOfLine; x++) {

      if (sourceOffset + 1 < this.byteArray.length) {
        boolean isLastByte = x + 1 == lastSourceByteOfLine;

        value = (byte) (this.getByte(sourceOffset++) << sourceUpShift | (this.getByte(sourceOffset) & 0xff) >>> sourceDownShift);

        if (isLastByte && !usePadding) {
          value = removeBytePadding(padding, value);
        }

        targetBitmap.setByte(targetOffset++, value);

        if (isLastByte && usePadding) {
          value = removeBytePadding(padding, (byte) ((this.getByte(sourceOffset) & 0xff) << sourceUpShift));
          targetBitmap.setByte(targetOffset, value);
        }

      } else {
        value = (byte) (this.getByte(sourceOffset++) << sourceUpShift & 0xff);
        targetBitmap.setByte(targetOffset++, value);
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
  private byte removeBytePadding(int padding, byte value) {
    return (byte) (value >> padding << padding);
  }

  /**
   * 
   * <p>
   * Returns the index of the byte that contains the pixel, specified by the pixel's x and y
   * coordinates.
   * 
   * @param x - The pixel's x coordinate.
   * @param y - The pixel's y coordinate.
   * @return The index of the byte that contains the specified pixel.
   */
  public int getByteIndex(int x, int y) {
    return y * this.rowStride + (x >> 3);
  }

  /**
   * Simply returns the byte array of this bitmap.
   * 
   * @return The byte array of this bitmap.
   */
  public byte[] getByteArray() {
    return byteArray;
  }

  /**
   * Simply returns a byte from the bitmap byte array. Throws an {@link IndexOutOfBoundsException}
   * if the given index is out of bound.
   * 
   * @param index - The array index that specifies the position of the wanted byte.
   * @return The byte at the {@code index}-position.
   * 
   * @throws IndexOutOfBoundsException if the index is out of bound.
   */
  public byte getByte(int index) {
    return this.byteArray[index];
  }

  /**
   * Simply sets the given value at the given array index position. Throws an
   * {@link IndexOutOfBoundsException} if the given index is out of bound.
   * 
   * @param index - The array index that specifies the position of a byte.
   * @param value - The byte that should be set.
   * 
   * @throws IndexOutOfBoundsException if the index is out of bound.
   */
  public void setByte(int index, byte value) {
    this.byteArray[index] = value;
  }

  /**
   * Converts the byte at specified index into an integer and returns the value. Throws an
   * {@link IndexOutOfBoundsException} if the given index is out of bound.
   * 
   * @param index - The array index that specifies the position of the wanted byte.
   * @return The converted byte at the {@code index}-position as an integer.
   * 
   * @throws IndexOutOfBoundsException if the index is out of bound.
   */
  public int getByteAsInteger(int index) {
    return (this.byteArray[index] & 0xff);
  }


  /**
   * Computes the offset of the given x coordinate in its byte. The method uses optimized modulo
   * operation for a better performance.
   * 
   * @param x - The x coordinate of a pixel.
   * @return The bit offset of a pixel in its byte.
   */
  public int getBitOffset(int x) {
    // The same like x % 8.
    // The rightmost three bits are 1. The value masks all bits upon the value "7".
    return (x & 0x07);
  }

  /**
   * Simply returns the height of this bitmap.
   * 
   * @return The height of this bitmap.
   */
  public int getHeight() {
    return height;
  }

  /**
   * Simply returns the width of this bitmap.
   * 
   * @return The width of this bitmap.
   */
  public int getWidth() {
    return width;
  }

  /**
   * Simply returns the row stride of this bitmap. <br>
   * (Row stride means the amount of bytes per line.)
   * 
   * @return The row stride of this bitmap.
   */
  public int getRowStride() {
    return rowStride;
  }

  /**
   * Creates a {@link Raster} concerning the given read parameters.
   * 
   * @param param - The read parameters keeping specific information about the request.
   * 
   * @return A new {@link Raster} with
   */
  public Raster getRaster(JBIG2ReadParam param) {
    ResultImageData resultImageData = new ResultImageData(param);

    return createRaster(resultImageData);
  }

  private WritableRaster createRaster(ResultImageData resultImageData) {
    DataBufferByte dbb = new DataBufferByte(resultImageData.resultByteArray, resultImageData.resultByteArray.length);

    return WritableRaster.createInterleavedRaster( //
        dbb, //
        resultImageData.targetWidth, //
        resultImageData.targetHeight, //
        resultImageData.targetWidth, //
        1, //
        new int[1], //
        new Point(0, 0));
  }

  /**
   * Creates a {@link BufferedImage} concerning the given read parameters.
   * 
   * @param param - The read parameters keeping specific information about the request.
   * 
   * @return A new {@link BufferedImage}.
   */
  public BufferedImage getBufferedImage(JBIG2ReadParam param) {
    if (pixelArray == null) {
      initPixelArray();
      byteArray = null;
    }

    ResultImageData resultImageData = new ResultImageData(param);

    ColorModel colorModel;

    if (resultImageData.isScaled) {
      // using bucket-filter to scale down the image
      colorModel = createGrayscaleColorModel(resultImageData.spreadFactorX, resultImageData.spreadFactorY);
    } else {
      colorModel = createIndexColorModel();
    }

    return createBufferedImage(resultImageData, colorModel);
  }

  private BufferedImage createBufferedImage(ResultImageData resultImageData, ColorModel colorModel) {
    return new BufferedImage(colorModel, createRaster(resultImageData), colorModel.isAlphaPremultiplied(), null);
  }

  private boolean isScaled(Dimension sourceRenderSize) {
    return (sourceRenderSize != null && (this.width > sourceRenderSize.width || this.height > sourceRenderSize.height));
  }

  private Rectangle scaleSourceRegion(Rectangle sr, Dimension srs) {

    int x = sr.x * this.width / srs.width;

    int y = sr.y * this.height / srs.height;

    int width = (sr.width * this.width) / srs.width;

    int height = (sr.height * this.height) / srs.height;

    return (new Rectangle(x, y, width, height));
  }

  private int calculateSpreadFactorY(Dimension srs) {
    return this.height != srs.height ? (this.height / srs.height) + 1 : 1;
  }

  private int calculateSpreadFactorX(Dimension srs) {
    return this.width != srs.width ? (this.width / srs.width) + 1 : 1;
  }

  private byte[] subSample(byte[] startByteArray, JBIG2ReadParam param, int targetWidth, int targetHeight,
      int sourceWidth, int sourceHeight) {

    byte[] newByteArray = new byte[targetWidth * targetHeight];
    int newByteIndex = 0;

    for (int y = param.getSubsamplingYOffset(); y < sourceHeight; y += param.getSourceYSubsampling()) {

      int firstSourceByteOfLine = y * sourceWidth;
      for (int x = param.getSubsamplingXOffset(); x < sourceWidth; x += param.getSourceXSubsampling()) {
        newByteArray[newByteIndex++] = startByteArray[firstSourceByteOfLine + x];
      }

    }

    return newByteArray;
  }

  private byte[] getSourceRegion(byte[] sourceData, Rectangle roi) {

    byte[] newData = new byte[roi.width * roi.height];

    int sourceByteIndex = roi.y * this.width + roi.x;
    int skipBytes = this.width - roi.width;

    int height = (roi.y + roi.height) >= this.height ? this.height - roi.y : roi.height;

    int width = (roi.x + roi.width) >= this.width ? this.width - roi.x : roi.width;

    for (int y = 0, targetByteIndex = 0; y < height; y++, sourceByteIndex += skipBytes) {
      for (int x = 0; x < width; x++, targetByteIndex++, sourceByteIndex++) {
        newData[targetByteIndex] = sourceData[sourceByteIndex];
      }
    }
    return newData;
  }

  private void initPixelArray() {
    this.pixelArray = new byte[this.width * this.height];

    int byteIndex = 0;
    int newByteIndex = 0;

    for (int y = 0; y < this.height; y++) {

      for (int x = 0; x < this.width; x += 8) {
        byte oldByte = (byte) ~this.getByte(byteIndex++);
        int minorWidth = this.width - x > 8 ? 8 : this.width - x;

        for (int minorX = 0; minorX < minorWidth; minorX++) {
          this.pixelArray[newByteIndex++] = (byte) (oldByte >> (7 - minorX) & 0x1);
        }
      }
    }
  }

  private final byte[] scaleDownBiLevelToGrayscale(Rectangle sourceROI, int spreadFactorX, int spreadFactorY,
      int targetWidth, int targetHeight, byte[] sourceData) {

    // set up the target data buffer
    byte imageData[] = new byte[targetWidth * targetHeight];

    if (spreadFactorX * spreadFactorY >= 256) {
      return scaleUsingConsolidation(imageData, 0, sourceROI.height, sourceROI.width, targetHeight >> 1,
          sourceROI.height - 1, 0, sourceROI.width, spreadFactorX, spreadFactorY, targetWidth, targetHeight, sourceData);
    } else {
      return scaleToByteData(imageData, 0, sourceROI.height, sourceROI.width, targetHeight >> 1, sourceROI.height - 1,
          0, sourceROI.width, spreadFactorX, spreadFactorY, targetWidth, targetHeight, sourceData);
    }
  }

  private final byte[] scaleToByteData(byte[] imageData, int lineOffset, int lineDX, int pixelDX, int lineEpsilon,
      int lastSourceLine, int firstSourceByteOfLine, int lastSourceByteOfLine, int spreadFactorX, int spreadFactorY,
      int targetWidth, int targetHeight, byte[] sourceData) {

    for (int sourceLine = 0, lineAggregate = spreadFactorY; sourceLine < lineDX; sourceLine++, lineAggregate--) {

      // bresenham for source/target interpolation
      if (lineEpsilon >= lineDX) {
        lineEpsilon -= lineDX;
        lineOffset += targetWidth;
        lineAggregate = spreadFactorY;
      }
      lineEpsilon += targetHeight;

      // check whether the next line would go to the next line or the
      // current one is the last one and the line aggregate is not yet
      // full.
      int lineFactor = 1;
      if ((lineEpsilon >= lineDX || sourceLine == lastSourceLine) && lineAggregate != 1) {
        lineFactor = lineAggregate;
      }
      int pixelEpsilon = targetWidth >> 1;
      int targetOffset = lineOffset;

      for (int sourceByte = firstSourceByteOfLine, pixelAggregate = spreadFactorX; sourceByte < lastSourceByteOfLine; sourceByte++, pixelAggregate--) {

        if (pixelEpsilon >= pixelDX) {
          pixelEpsilon -= pixelDX;
          pixelAggregate = spreadFactorX;
          targetOffset++;
        }
        pixelEpsilon += targetWidth;

        // check whether the next pixel would go to the next bucket
        // and the pixel aggregate is not yet full.
        int pixelFactor = lineFactor;
        if ((pixelEpsilon >= pixelDX || sourceByte == pixelDX) && pixelAggregate != 1) {
          pixelFactor <<= 1;
        }
        if (targetOffset < imageData.length && sourceData[sourceByte] != 0) {
          imageData[targetOffset] += pixelFactor;
        }
      }
      firstSourceByteOfLine += pixelDX;
      lastSourceByteOfLine += pixelDX;
    }
    return imageData;
  }

  private final byte[] scaleUsingConsolidation(byte[] imageData, int lineOffset, int lineDX, int pixelDX,
      int lineEpsilon, int lastSourceLine, int firstSourceByteOfLine, int lastSourceByteOfLine, int spreadFactorX,
      int spreadFactorY, int targetWidth, int targetHeight, byte[] sourceData) {

    int divisor = spreadFactorX * spreadFactorY * 10000 / 255;
    int[] tempArray = new int[imageData.length];

    for (int sourceLine = 0, lineAggregate = spreadFactorY; sourceLine < lineDX; sourceLine++, lineAggregate--) {

      // bresenham for source/target interpolation
      if (lineEpsilon >= lineDX) {
        lineEpsilon -= lineDX;
        lineOffset += targetWidth;
        lineAggregate = spreadFactorY;
      }
      lineEpsilon += targetHeight;

      // check whether the next line would go to the next line or the
      // current one is the last one and the line aggregate is not yet
      // full.
      int lineFactor = 1;
      if ((lineEpsilon >= lineDX || sourceLine == lastSourceLine) && lineAggregate != 1) {
        lineFactor = lineAggregate;
      }
      int pixelEpsilon = targetWidth >> 1;
      int targetOffset = lineOffset;

      for (int sourceByte = firstSourceByteOfLine, pixelAggregate = spreadFactorX; sourceByte < lastSourceByteOfLine; sourceByte++, pixelAggregate--) {

        if (pixelEpsilon >= pixelDX) {
          pixelEpsilon -= pixelDX;
          pixelAggregate = spreadFactorX;
          targetOffset++;
        }
        pixelEpsilon += targetWidth;

        // check whether the next pixel would go to the next bucket
        // and the pixel aggregate is not yet full.
        int pixelFactor = lineFactor;
        if ((pixelEpsilon >= pixelDX || sourceByte == pixelDX) && pixelAggregate != 1) {
          pixelFactor <<= 1;
        }
        if (targetOffset < imageData.length && sourceData[sourceByte] != 0) {
          tempArray[targetOffset] += pixelFactor;
        }
      }
      firstSourceByteOfLine += pixelDX;
      lastSourceByteOfLine += pixelDX;
    }
    for (int i = 0; i < tempArray.length; i++) {
      imageData[i] = (byte) (tempArray[i] * 10000 / divisor);
    }
    return imageData;
  }

  private ColorModel createGrayscaleColorModel(int spreadFactorX, int spreadFactorY) {

    int size = spreadFactorX * spreadFactorY + 1;

    // Performance depends on JVM:
    // With JIT compiler the Math method calls are faster.
    // Without JIT compiler the conditional operators are faster.
    // size = Math.min(255, Math.max(1, size));
    size = size < 1 ? 1 : size > 255 ? 255 : size;
    int divisor = (size - 1) == 0 ? 1 : (size - 1);

    byte[] gray = new byte[size];

    for (int i = 0; i < size; i++) {
      gray[i] = (byte) (255 - i * 255 / divisor);
    }
    return new IndexColorModel(8, size, gray, gray, gray);
  }

  private ColorModel createIndexColorModel() {
    return new IndexColorModel(8, 2, //
        new byte[]{
            0x00, (byte) 0xff
        }, new byte[]{
            0x00, (byte) 0xff
        }, new byte[]{
            0x00, (byte) 0xff
        });
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
}
