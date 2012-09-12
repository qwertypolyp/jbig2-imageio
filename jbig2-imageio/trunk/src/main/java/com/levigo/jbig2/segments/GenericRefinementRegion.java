/**
 * Copyright (C) 1995-2012 levigo holding gmbh.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.levigo.jbig2.segments;

import java.io.IOException;

import com.levigo.jbig2.Bitmap;
import com.levigo.jbig2.JBIG2ImageReader;
import com.levigo.jbig2.Region;
import com.levigo.jbig2.SegmentHeader;
import com.levigo.jbig2.decoder.arithmetic.ArithmeticDecoder;
import com.levigo.jbig2.decoder.arithmetic.CX;
import com.levigo.jbig2.io.SubInputStream;
import com.levigo.jbig2.util.IntegerMaxValueException;
import com.levigo.jbig2.util.InvalidHeaderValueException;
import com.levigo.jbig2.util.log.Logger;
import com.levigo.jbig2.util.log.LoggerFactory;

/**
 * This class represents a generic refinement region and implements the procedure described in JBIG2
 * ISO standard, 6.3 and 7.4.7.
 * 
 * @author <a href="mailto:m.krzikalla@levigo.de">Matthäus Krzikalla</a>
 */
public class GenericRefinementRegion implements Region {
  private static final Logger log = LoggerFactory.getLogger(GenericRefinementRegion.class);
  
  private SubInputStream subInputStream;

  private SegmentHeader segmentHeader;

  /** Region segment information flags, 7.4.1 */
  private RegionSegmentInformation regionInfo;

  /** Generic refinement region segment flags, 7.4.7.2 */
  private boolean isTPGROn;
  private short template;

  /** Generic refinement region segment AT flags, 7.4.7.3 */
  private short grAtX[];
  private short grAtY[];

  /** Decoded data as pixel values (use row stride/width to wrap line) */
  private Bitmap regionBitmap;

  /** Variables for decoding */
  private Bitmap referenceBitmap;
  private int referenceDX;
  private int referenceDY;

  private ArithmeticDecoder arithDecoder;
  private CX cx;

  /**
   * If true, AT pixels are not on their nominal location and have to be overridden.
   */
  private boolean override;
  private boolean[] grAtOverride;

  public GenericRefinementRegion() {
  }

  public GenericRefinementRegion(SubInputStream subInputStream) {
    this.subInputStream = subInputStream;
    this.regionInfo = new RegionSegmentInformation(subInputStream);
  }

  public GenericRefinementRegion(SubInputStream subInputStream, SegmentHeader segmentHeader) {
    this.subInputStream = subInputStream;
    this.segmentHeader = segmentHeader;
    this.regionInfo = new RegionSegmentInformation(subInputStream);
  }

  /**
   * Parses the flags described in JBIG2 ISO standard:
   * <ul>
   * <li>7.4.7.2 Generic refinement region segment flags</li>
   * <li>7.4.7.3 Generic refinement refion segment AT flags</li>
   * </ul>
   * 
   * @throws IOException
   */
  private void parseHeader() throws IOException {
    regionInfo.parseHeader();

    /* Bit 2-7 */
    subInputStream.readBits(6); // Dirty read...

    /* Bit 1 */
    if (subInputStream.readBit() == 1) {
      isTPGROn = true;
    }

    /* Bit 0 */
    template = (short) subInputStream.readBit();

    if (template == 0) {
      readAtPixels();
    }
  }

  private void readAtPixels() throws IOException {
    grAtX = new short[2];
    grAtY = new short[2];

    /* Byte 0 */
    grAtX[0] = subInputStream.readByte();
    /* Byte 1 */
    grAtY[0] = subInputStream.readByte();
    /* Byte 2 */
    grAtX[1] = subInputStream.readByte();
    /* Byte 3 */
    grAtY[1] = subInputStream.readByte();
  }

  /**
   * Decode using a template and arithmetic coding, as described in 6.3.5.6
   * 
   * @throws IOException
   * @throws InvalidHeaderValueException
   * @throws IntegerMaxValueException
   */
  public Bitmap getRegionBitmap() throws IOException, IntegerMaxValueException, InvalidHeaderValueException {
    if (null == regionBitmap) {
      /* 6.3.5.6 - 1) */
      int isLineTypicalPredicted = 0;

      if (referenceBitmap == null) {
        // Get the reference bitmap, which is the base of refinement process
        referenceBitmap = getGrReference();
      }

      if (arithDecoder == null) {
        arithDecoder = new ArithmeticDecoder(subInputStream);
      }

      if (cx == null) {
        cx = new CX(8192, 1);
      }

      /* 6.3.5.6 - 2) */
      regionBitmap = new Bitmap(regionInfo.getBitmapWidth(), regionInfo.getBitmapHeight());

      if (template == 0) {
        // AT pixel may only occur in template 0
        updateOverride();
      }

      final int paddedWidth = (regionBitmap.getWidth() + 7) & -8;
      final int deltaRefStride = isTPGROn ? -referenceDY * referenceBitmap.getRowStride() : 0;
      final int yOffset = deltaRefStride + 1;

      /* 6.3.5.6 - 3 */
      for (int y = 0; y < regionBitmap.getHeight(); y++) {
        /* 6.3.5.6 - 3 b) */
        if (isTPGROn) {
          isLineTypicalPredicted ^= decodeSLTP();
        }

        if (isLineTypicalPredicted == 0) {
          /* 6.3.5.6 - 3 c) */
          decodeOptimized(y, regionBitmap.getWidth(), regionBitmap.getRowStride(), referenceBitmap.getRowStride(),
              paddedWidth, deltaRefStride, yOffset);
        } else {
          /* 6.3.5.6 - 3 d) */
          decodeTypicalPredictedLine(y, regionBitmap.getWidth(), regionBitmap.getRowStride(),
              referenceBitmap.getRowStride(), paddedWidth, deltaRefStride);
        }
      }
    }
    /* 6.3.5.6 - 4) */
    return regionBitmap;
  }

  private int decodeSLTP() throws IOException {
    switch (template){
      case 0 :
        // Figure 14, page 22
        cx.setIndex(0x100);
        break;
      case 1 :
        // Figure 15, page 22
        cx.setIndex(0x080);
        break;
    }
    return arithDecoder.decode(cx);
  }

  private Bitmap getGrReference() throws IntegerMaxValueException, InvalidHeaderValueException, IOException {
    SegmentHeader[] segments = segmentHeader.getRtSegments();
    Region region = (Region) segments[0].getSegmentData();

    return region.getRegionBitmap();
  }

  private void decodeOptimized(int lineNumber, int width, int rowStride, int refRowStride, int paddedWidth,
      int deltaRefStride, int lineOffset) throws IOException {

    // Offset of the reference bitmap with respect to the bitmap being decoded
    // For example: if referenceDY = -1, y is 1 HIGHER that currY
    final int currentLine = lineNumber - referenceDY;
    int referenceByteIndex = referenceBitmap.getByteIndex(0, currentLine);

    int byteIndex = regionBitmap.getByteIndex(0, lineNumber);

    switch (template){
      case 0 :
        decodeTemplate0(lineNumber, width, rowStride, refRowStride, paddedWidth, deltaRefStride, lineOffset, byteIndex,
            currentLine, referenceByteIndex);
        break;
      case 1 :
        decodeTemplate1(lineNumber, width, rowStride, refRowStride, paddedWidth, deltaRefStride, lineOffset, byteIndex,
            currentLine, referenceByteIndex);
        break;
    }
  }

  private void decodeTemplate0(int lineNumber, int width, int rowStride, int refRowStride, int paddedWidth,
      int deltaRefStride, int lineOffset, int byteIndex, int currentLine, int refByteIndex) throws IOException {
    int context;
    int overriddenContext;

    // Registers
    int previousReferenceLine;
    int currentReferenceLine;
    int nextReferenceLine;
    int linePrev;

    if (lineNumber > 0) {
      linePrev = regionBitmap.getByteAsInteger(byteIndex - rowStride);
    } else {
      linePrev = 0;
    }

    if (currentLine > 0 && currentLine <= referenceBitmap.getHeight()) {
      previousReferenceLine = referenceBitmap.getByteAsInteger(refByteIndex - refRowStride + deltaRefStride) << 4;
    } else {
      previousReferenceLine = 0;
    }

    if (currentLine >= 0 && currentLine < referenceBitmap.getHeight()) {
      currentReferenceLine = referenceBitmap.getByteAsInteger(refByteIndex + deltaRefStride) << 1;
    } else {
      currentReferenceLine = 0;
    }

    if (currentLine > -2 && currentLine < (referenceBitmap.getHeight() - 1)) {
      nextReferenceLine = referenceBitmap.getByteAsInteger(refByteIndex + refRowStride + deltaRefStride);
    } else {
      nextReferenceLine = 0;
    }

    context = ((linePrev >> 5) & 0x6) | ((nextReferenceLine >> 2) & 0x30) | (currentReferenceLine & 0x180)
        | (previousReferenceLine & 0xc00);

    int nextByte;
    for (int x = 0; x < paddedWidth; x = nextByte) {
      byte result = 0;
      nextByte = x + 8;
      final int minorWidth = width - x > 8 ? 8 : width - x;
      final boolean readNextByte = nextByte < width;
      final boolean readNextRefByte = nextByte < referenceBitmap.getWidth();

      if (lineNumber > 0) {
        linePrev = (linePrev << 8) | (readNextByte ? regionBitmap.getByteAsInteger(byteIndex - rowStride + 1) : 0);
      }

      if (currentLine > 0 && currentLine <= referenceBitmap.getHeight()) {
        previousReferenceLine = (previousReferenceLine << 8)
            | (readNextRefByte ? referenceBitmap.getByteAsInteger(refByteIndex - refRowStride + lineOffset) << 4 : 0);
      }

      if (currentLine >= 0 && currentLine < referenceBitmap.getHeight()) {
        currentReferenceLine = (currentReferenceLine << 8)
            | (readNextRefByte ? referenceBitmap.getByteAsInteger(refByteIndex + lineOffset) << 1 : 0);
      }

      if (currentLine > -2 && currentLine < (referenceBitmap.getHeight() - 1)) {
        nextReferenceLine = (nextReferenceLine << 8)
            | (readNextRefByte ? referenceBitmap.getByteAsInteger(refByteIndex + refRowStride + lineOffset) : 0);
      }

      for (int minorX = 0; minorX < minorWidth; minorX++) {

        if (override) {
          overriddenContext = overrideAtTemplate0(context, x + minorX, lineNumber, result, minorX);
          cx.setIndex(overriddenContext);
        } else {
          cx.setIndex(context);
        }

        final int bit = arithDecoder.decode(cx);

        final int toShift = 7 - minorX;
        result |= bit << toShift;

        context = ((context & 0xdb6) << 1) | bit | ((linePrev >> toShift + 5) & 0x002)
            | ((nextReferenceLine >> toShift + 2) & 0x010) | ((currentReferenceLine >> toShift) & 0x080)
            | ((previousReferenceLine >> toShift) & 0x400);

      }
      regionBitmap.setByte(byteIndex++, result);
      refByteIndex++;
    }
  }

  private void decodeTemplate1(int lineNumber, int width, int rowStride, int refRowStride, int paddedWidth,
      int deltaRefStride, int lineOffset, int byteIndex, int currentLine, int refByteIndex) throws IOException {
    int context;

    // Registers
    int linePrev;
    int previousReferenceLine;
    int currentReferenceLine;
    int nextReferenceLine;

    if (lineNumber > 0) {
      linePrev = regionBitmap.getByteAsInteger(byteIndex - rowStride);
    } else {
      linePrev = 0;
    }

    if (currentLine > 0 && currentLine <= referenceBitmap.getHeight()) {
      previousReferenceLine = referenceBitmap.getByteAsInteger(refByteIndex - refRowStride + deltaRefStride) << 2;
    } else {
      previousReferenceLine = 0;
    }

    if (currentLine >= 0 && currentLine < referenceBitmap.getHeight()) {
      currentReferenceLine = referenceBitmap.getByteAsInteger(refByteIndex + deltaRefStride);
    } else {
      currentReferenceLine = 0;
    }

    if (currentLine > -2 && currentLine < (referenceBitmap.getHeight() - 1)) {
      nextReferenceLine = referenceBitmap.getByteAsInteger(refByteIndex + refRowStride + deltaRefStride);
    } else {
      nextReferenceLine = 0;
    }

    context = ((linePrev >> 5) & 0x6) | ((nextReferenceLine >> 2) & 0x30) | (currentReferenceLine & 0xc0)
        | (previousReferenceLine & 0x200);

    int nextByte;
    for (int x = 0; x < paddedWidth; x = nextByte) {
      byte result = 0;
      nextByte = x + 8;
      final int minorWidth = width - x > 8 ? 8 : width - x;
      final boolean readNextByte = nextByte < width;
      final boolean readNextRefByte = nextByte < referenceBitmap.getWidth();

      if (lineNumber > 0) {
        linePrev = (linePrev << 8) | (readNextByte ? regionBitmap.getByteAsInteger(byteIndex - rowStride + 1) : 0);
      }

      if (currentLine > 0 && currentLine <= referenceBitmap.getHeight()) {
        previousReferenceLine = (previousReferenceLine << 8)
            | (readNextRefByte ? referenceBitmap.getByteAsInteger(refByteIndex - refRowStride + lineOffset) << 2 : 0);
      }

      if (currentLine >= 0 && currentLine < referenceBitmap.getHeight()) {
        currentReferenceLine = (currentReferenceLine << 8)
            | (readNextRefByte ? referenceBitmap.getByteAsInteger(refByteIndex + lineOffset) : 0);
      }

      if (currentLine > -2 && currentLine < (referenceBitmap.getHeight() - 1)) {
        nextReferenceLine = (nextReferenceLine << 8)
            | (readNextRefByte ? referenceBitmap.getByteAsInteger(refByteIndex + refRowStride + lineOffset) : 0);
      }

      for (int minorX = 0; minorX < minorWidth; minorX++) {
        cx.setIndex(context);

        final int bit = arithDecoder.decode(cx);
        final int toShift = 7 - minorX;
        result |= bit << toShift;

        context = ((context & 0x0d6) << 1) | bit | ((linePrev >> toShift + 5) & 0x002)
            | ((nextReferenceLine >> toShift + 2) & 0x010) | ((currentReferenceLine >> toShift) & 0x040)
            | ((previousReferenceLine >> toShift) & 0x200);
      }
      regionBitmap.setByte(byteIndex++, result);
      refByteIndex++;
    }
  }

  private void updateOverride() {
    if (grAtX == null || grAtY == null) {
      log.info("AT pixels not set");
      return;
    }

    if (grAtX.length != grAtY.length) {
      log.info("AT pixel inconsistent");
      return;
    }

    grAtOverride = new boolean[grAtX.length];

    switch (template){
      case 0 :
        if (grAtX[0] != -1 && grAtY[0] != -1) {
          grAtOverride[0] = true;
          override = true;
        }

        if (grAtX[1] != -1 && grAtY[1] != -1) {
          grAtOverride[1] = true;
          override = true;
        }
        break;
      case 1 :
        override = false;
        break;
    }
  }

  private void decodeTypicalPredictedLine(int lineNumber, int width, int rowStride, int refRowStride, int paddedWidth,
      int deltaRefStride) throws IOException {

    // Offset of the reference bitmap with respect to the bitmap being decoded
    // For example: if grReferenceDY = -1, y is 1 HIGHER that currY
    final int currentLine = lineNumber - referenceDY;
    int refByteIndex = referenceBitmap.getByteIndex(0, currentLine);

    int byteIndex = regionBitmap.getByteIndex(0, lineNumber);

    switch (template){
      case 0 :
        decodeTypicalPredictedLineTemplate0(lineNumber, width, rowStride, refRowStride, paddedWidth, deltaRefStride,
            byteIndex, currentLine, refByteIndex);
        break;
      case 1 :
        decodeTypicalPredictedLineTemplate1(lineNumber, width, rowStride, refRowStride, paddedWidth, deltaRefStride,
            byteIndex, currentLine, refByteIndex);
        break;
    }
  }

  private void decodeTypicalPredictedLineTemplate0(int lineNumber, int width, int rowStride, int refRowStride,
      int paddedWidth, int deltaRefStride, int byteIndex, int currentLine, int refByteIndex) throws IOException {
    int context;
    int overriddenContext;

    int previousLine;
    int previousReferenceLine;
    int currentReferenceLine;
    int nextReferenceLine;

    if (lineNumber > 0) {
      previousLine = regionBitmap.getByteAsInteger(byteIndex - rowStride);
    } else {
      previousLine = 0;
    }

    if (currentLine > 0 && currentLine <= referenceBitmap.getHeight()) {
      previousReferenceLine = referenceBitmap.getByteAsInteger(refByteIndex - refRowStride + deltaRefStride) << 4;
    } else {
      previousReferenceLine = 0;
    }

    if (currentLine >= 0 && currentLine < referenceBitmap.getHeight()) {
      currentReferenceLine = referenceBitmap.getByteAsInteger(refByteIndex + deltaRefStride) << 1;
    } else {
      currentReferenceLine = 0;
    }

    if (currentLine > -2 && currentLine < (referenceBitmap.getHeight() - 1)) {
      nextReferenceLine = referenceBitmap.getByteAsInteger(refByteIndex + refRowStride + deltaRefStride);
    } else {
      nextReferenceLine = 0;
    }

    context = ((previousLine >> 5) & 0x6) | ((nextReferenceLine >> 2) & 0x30) | (currentReferenceLine & 0x180)
        | (previousReferenceLine & 0xc00);

    int nextByte;
    for (int x = 0; x < paddedWidth; x = nextByte) {
      byte result = 0;
      nextByte = x + 8;
      final int minorWidth = width - x > 8 ? 8 : width - x;
      final boolean readNextByte = nextByte < width;
      final boolean refReadNextByte = nextByte < referenceBitmap.getWidth();

      final int yOffset = deltaRefStride + 1;

      if (lineNumber > 0) {
        previousLine = (previousLine << 8)
            | (readNextByte ? regionBitmap.getByteAsInteger(byteIndex - rowStride + 1) : 0);
      }

      if (currentLine > 0 && currentLine <= referenceBitmap.getHeight()) {
        previousReferenceLine = (previousReferenceLine << 8)
            | (refReadNextByte ? referenceBitmap.getByteAsInteger(refByteIndex - refRowStride + yOffset) << 4 : 0);
      }

      if (currentLine >= 0 && currentLine < referenceBitmap.getHeight()) {
        currentReferenceLine = (currentReferenceLine << 8)
            | (refReadNextByte ? referenceBitmap.getByteAsInteger(refByteIndex + yOffset) << 1 : 0);
      }

      if (currentLine > -2 && currentLine < (referenceBitmap.getHeight() - 1)) {
        nextReferenceLine = (nextReferenceLine << 8)
            | (refReadNextByte ? referenceBitmap.getByteAsInteger(refByteIndex + refRowStride + yOffset) : 0);
      }

      for (int minorX = 0; minorX < minorWidth; minorX++) {
        boolean isPixelTypicalPredicted = false;
        int bit = 0;

        // i)
        final int bitmapValue = (context >> 4) & 0x1FF;

        if (bitmapValue == 0x1ff) {
          isPixelTypicalPredicted = true;
          bit = 1;
        } else if (bitmapValue == 0x00) {
          isPixelTypicalPredicted = true;
          bit = 0;
        }

        if (!isPixelTypicalPredicted) {
          // iii) - is like 3 c) but for one pixel only

          if (override) {
            overriddenContext = overrideAtTemplate0(context, x + minorX, lineNumber, result, minorX);
            cx.setIndex(overriddenContext);
          } else {
            cx.setIndex(context);
          }
          bit = arithDecoder.decode(cx);
        }

        final int toShift = 7 - minorX;
        result |= bit << toShift;

        context = ((context & 0xdb6) << 1) | bit | ((previousLine >> toShift + 5) & 0x002)
            | ((nextReferenceLine >> toShift + 2) & 0x010) | ((currentReferenceLine >> toShift) & 0x080)
            | ((previousReferenceLine >> toShift) & 0x400);
      }
      regionBitmap.setByte(byteIndex++, result);
      refByteIndex++;
    }
  }

  private void decodeTypicalPredictedLineTemplate1(int lineNumber, int width, int rowStride, int refRowStride,
      int paddedWidth, int deltaRefStride, int byteIndex, int currentLine, int refByteIndex) throws IOException {
    int context;
    int grReferenceValue;

    int previousLine;
    int previousReferenceLine;
    int currentReferenceLine;
    int nextReferenceLine;

    if (lineNumber > 0) {
      previousLine = regionBitmap.getByteAsInteger(byteIndex - rowStride);
    } else {
      previousLine = 0;
    }

    if (currentLine > 0 && currentLine <= referenceBitmap.getHeight()) {
      previousReferenceLine = referenceBitmap.getByteAsInteger(byteIndex - refRowStride + deltaRefStride) << 2;
    } else {
      previousReferenceLine = 0;
    }

    if (currentLine >= 0 && currentLine < referenceBitmap.getHeight()) {
      currentReferenceLine = referenceBitmap.getByteAsInteger(byteIndex + deltaRefStride);
    } else {
      currentReferenceLine = 0;
    }

    if (currentLine > -2 && currentLine < (referenceBitmap.getHeight() - 1)) {
      nextReferenceLine = referenceBitmap.getByteAsInteger(byteIndex + refRowStride + deltaRefStride);
    } else {
      nextReferenceLine = 0;
    }

    context = ((previousLine >> 5) & 0x6) | ((nextReferenceLine >> 2) & 0x30) | (currentReferenceLine & 0xc0) | (previousReferenceLine & 0x200);

    grReferenceValue = ((nextReferenceLine >> 2) & 0x70) | (currentReferenceLine & 0xc0) | (previousReferenceLine & 0x700);

    int nextByte;
    for (int x = 0; x < paddedWidth; x = nextByte) {
      byte result = 0;
      nextByte = x + 8;
      final int minorWidth = width - x > 8 ? 8 : width - x;
      final boolean readNextByte = nextByte < width;
      final boolean refReadNextByte = nextByte < referenceBitmap.getWidth();

      final int yOffset = deltaRefStride + 1;

      if (lineNumber > 0) {
        previousLine = (previousLine << 8) | (readNextByte ? regionBitmap.getByteAsInteger(byteIndex - rowStride + 1) : 0);
      }

      if (currentLine > 0 && currentLine <= referenceBitmap.getHeight()) {
        previousReferenceLine = (previousReferenceLine << 8)
            | (refReadNextByte ? referenceBitmap.getByteAsInteger(refByteIndex - refRowStride + yOffset) << 2 : 0);
      }

      if (currentLine >= 0 && currentLine < referenceBitmap.getHeight()) {
        currentReferenceLine = (currentReferenceLine << 8)
            | (refReadNextByte ? referenceBitmap.getByteAsInteger(refByteIndex + yOffset) : 0);
      }

      if (currentLine > -2 && currentLine < (referenceBitmap.getHeight() - 1)) {
        nextReferenceLine = (nextReferenceLine << 8)
            | (refReadNextByte ? referenceBitmap.getByteAsInteger(refByteIndex + refRowStride + yOffset) : 0);
      }

      for (int minorX = 0; minorX < minorWidth; minorX++) {
        int bit = 0;

        // i)
        final int bitmapValue = (grReferenceValue >> 4) & 0x1ff;

        if (bitmapValue == 0x1ff) {
          bit = 1;
        } else if (bitmapValue == 0x00) {
          bit = 0;
        } else {
          cx.setIndex(context);
          bit = arithDecoder.decode(cx);
        }

        final int toShift = 7 - minorX;
        result |= bit << toShift;

        context = ((context & 0x0d6) << 1) | bit | ((previousLine >> toShift + 5) & 0x002)
            | ((nextReferenceLine >> toShift + 2) & 0x010) | ((currentReferenceLine >> toShift) & 0x040)
            | ((previousReferenceLine >> toShift) & 0x200);

        grReferenceValue = ((grReferenceValue & 0x0db) << 1) | ((nextReferenceLine >> toShift + 2) & 0x010)
            | ((currentReferenceLine >> toShift) & 0x080) | ((previousReferenceLine >> toShift) & 0x400);
      }
      regionBitmap.setByte(byteIndex++, result);
      refByteIndex++;
    }
  }

  private int overrideAtTemplate0(int context, int x, int y, int result, int minorX) throws IOException {

    if (grAtOverride[0]) {
      context &= 0xfff7;
      if (grAtY[0] == 0 && grAtX[0] >= -minorX) {
        context |= (result >> (7 - (minorX + grAtX[0])) & 0x1) << 3;
      } else {
        context |= getPixel(regionBitmap, x + grAtX[0], y + grAtY[0]) << 3;
      }
    }

    if (grAtOverride[1]) {
      context &= 0xefff;
      if (grAtY[1] == 0 && grAtX[1] >= -minorX) {
        context |= (result >> (7 - (minorX + grAtX[1])) & 0x1) << 12;
      } else {
        context |= getPixel(referenceBitmap, x + grAtX[1] + referenceDX, y + grAtY[1] + referenceDY) << 12;
      }
    }
    return context;
  }

  private byte getPixel(Bitmap b, int x, int y) throws IOException {
    if (x < 0 || x >= b.getWidth()) {
      return 0;
    }
    if (y < 0 || y >= b.getHeight()) {
      return 0;
    }

    return b.getPixel(x, y);
  }

  public void init(SegmentHeader header, SubInputStream sis) throws IOException {
    this.segmentHeader = header;
    this.subInputStream = sis;
    this.regionInfo = new RegionSegmentInformation(subInputStream);
    parseHeader();
  }

  protected void setParameters(CX cx, ArithmeticDecoder arithmeticDecoder, short grTemplate, int regionWidth,
      int regionHeight, Bitmap grReference, int grReferenceDX, int grReferenceDY, boolean isTPGRon, short[] grAtX,
      short[] grAtY) {

    if (null != cx) {
      this.cx = cx;
    }

    if (null != arithmeticDecoder) {
      this.arithDecoder = arithmeticDecoder;
    }

    this.template = grTemplate;

    this.regionInfo.setBitmapWidth(regionWidth);
    this.regionInfo.setBitmapHeight(regionHeight);

    this.referenceBitmap = grReference;
    this.referenceDX = grReferenceDX;
    this.referenceDY = grReferenceDY;

    this.isTPGROn = isTPGRon;

    this.grAtX = grAtX;
    this.grAtY = grAtY;

    this.regionBitmap = null;
  }

  public RegionSegmentInformation getRegionInfo() {
    return regionInfo;
  }
}
