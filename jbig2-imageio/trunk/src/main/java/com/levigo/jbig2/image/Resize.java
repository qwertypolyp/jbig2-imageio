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

package com.levigo.jbig2.image;

import java.awt.Rectangle;
import java.awt.geom.Rectangle2D;
import java.awt.image.Raster;

import com.levigo.jbig2.util.Utils;

public abstract class Resize {

  static final class Mapping {
    final double scale; /* x and y scales */
    // final double translate; /* x and y translations */
    final double offset = .5; /* x and y offset used by MAP, private fields */

    private final double a0;
    private final double b0;

    Mapping(double a0, double aw, double b0, double bw) {
      this.a0 = a0;
      this.b0 = b0;
      scale = bw / aw;
      // translate = b0 - a0;
      /* compute offsets for MAP (these will be .5 if zoom() routine was called) */
      // offset = b0 - scale * (a0 - .5) - translate;

      if (scale <= 0.)
        throw new IllegalArgumentException("Negative scales are not allowed");
    }

    Mapping(double scaleX) {
      scale = scaleX;
      // translate = -.5 - scale * -.5;
      a0 = b0 = 0;
    }

    double mapPixelCenter(final int b) {
      return (b + offset - b0) / scale + a0;
    }

    double dstToSrc(final double b) {
      return (b - b0) / scale + a0;
    }

    double srcToDst(final double a) {
      return (a - a0) * scale + b0;
    }
  }

  /**
   * Order in which to apply filter
   */
  private enum Order {
    AUTO, XY, YX
  }

  private static final double EPSILON = 1e-7; // error tolerance

  private int weightBits = 14; // # bits in filter coefficients

  private int weightOne = 1 << weightBits;

  private int bitsPerChannel[] = new int[]{
      8, 8, 8
  }; // # bits per channel

  private static final int NO_SHIFT[] = new int[16];

  private int finalShift[] = new int[]{
      2 * weightBits - bitsPerChannel[0], 2 * weightBits - bitsPerChannel[1], 2 * weightBits - bitsPerChannel[2]
  };

  /**
   * is x an integer?
   * 
   * @param x
   * @return
   */
  private static boolean isInteger(final double x) {
    return Math.abs(x - Math.floor(x + .5)) < EPSILON;
  }

  static final boolean debug = false;

  /**
   * simplify filters if possible?
   */
  private final boolean coerce = true;

  /**
   * filter x before y (1) or vice versa (0)?
   */
  private final Order order = Order.AUTO;

  /**
   * trim zeros in x filter weight tables?
   */
  private final boolean trimZeros = true;

  private final Mapping mappingX;
  private final Mapping mappingY;

  public Resize(Rectangle2D srcBounds, Rectangle2D dstBounds) {
    mappingX = new Mapping(srcBounds.getX(), srcBounds.getWidth(), dstBounds.getX(), dstBounds.getWidth());
    mappingY = new Mapping(srcBounds.getY(), srcBounds.getHeight(), dstBounds.getY(), dstBounds.getHeight());
  }

  public Resize(double scaleX, double scaleY) {
    mappingX = new Mapping(scaleX);
    mappingY = new Mapping(scaleY);
  }

  private Weighttab[] createXWeights(Rectangle srcBounds, final Rectangle dstBounds, final ParameterizedFilter filter) {
    final int ax0 = srcBounds.x;
    final int ax1 = srcBounds.x + srcBounds.width;

    final int bx0 = dstBounds.x;
    final int bx1 = dstBounds.x + dstBounds.width;
    final Weighttab tabs[] = new Weighttab[dstBounds.width];

    // System.out.println("Creating xWeights for [" + bx0 + ".." + bx1 + "]");
    for (int bx = bx0; bx < bx1; bx++) {
      final double center = mappingX.mapPixelCenter(bx);
      tabs[bx - bx0] = new Weighttab(filter, weightOne, center, ax0, ax1 - 1, trimZeros);
      // System.out.println("bx: " + bx + " center: " + center + " -> " + tabs[bx - bx0].i0
      // + Arrays.toString(tabs[bx - bx0].weights) + tabs[bx - bx0].i1);
    }

    return tabs;
  }

  /*
   * filter_simplify: check if our discrete sampling of an arbitrary continuous filter,
   * parameterized by the filter spacing (a->scale), its radius (a->supp), and the scale and offset
   * of the coordinate mapping (s and u), causes the filter to reduce to point sampling.
   * 
   * It reduces if support is less than 1 pixel or if integer scale and translation, and filter is
   * cardinal
   */
  private ParameterizedFilter simplifyFilter(final ParameterizedFilter a, final double s, final double u) {
    if (coerce
        && (a.support <= .5 || a.filter.cardinal && isInteger(1. / a.scale) && isInteger(1. / (s * a.scale))
            && isInteger((u / s - .5) / a.scale)))
      return new ParameterizedFilter(new Filter.Point(), 1., .5, 1);

    return a;
  }

  /*
   * zoom_filtered_xy: filtered zoom, xfilt before yfilt
   * 
   * note: when calling make_weighttab, we can trim leading and trailing zeros from the x weight
   * buffers as an optimization, but not for y weight buffers since the split formula is
   * anticipating a constant amount of buffering of source scanlines; trimming zeros in yweight
   * could cause feedback.
   */
  private void resizeXfirst(final Object src, final Rectangle srcBounds, final Object dst, final Rectangle dstBounds,
      final ParameterizedFilter xFilter, final ParameterizedFilter yFilter) {
    // source scanline buffer
    final Scanline buffer = createScanline(src, dst, srcBounds.width);

    // accumulator buffer
    final Scanline accumulator = createScanline(src, dst, dstBounds.width);

    // a sampled filter for source pixels for each dest x position
    final Weighttab xweights[] = createXWeights(srcBounds, dstBounds, xFilter);

    // Circular buffer of active lines
    final int yBufSize = yFilter.width + 2;
    final Scanline linebuf[] = new Scanline[yBufSize];
    for (int y = 0; y < yBufSize; y++) {
      linebuf[y] = createScanline(src, dst, dstBounds.width);
      linebuf[y].y = -1; /* mark scanline as unread */
    }

    // range of source and destination scanlines in regions
    final int ay0 = srcBounds.y;
    final int ay1 = srcBounds.y + srcBounds.height;
    final int by0 = dstBounds.y;
    final int by1 = dstBounds.y + dstBounds.height;

    int yFetched = -1; // used to assert no backtracking

    // loop over dest scanlines
    for (int by = by0; by < by1; by++) {
      // a sampled filter for source pixels for each dest x position
      final Weighttab yweight = new Weighttab(yFilter, weightOne, mappingY.mapPixelCenter(by), ay0, ay1 - 1, true);

      accumulator.clear();

      // loop over source scanlines that contribute to this dest scanline
      for (int ay = yweight.i0; ay <= yweight.i1; ay++) {
        final Scanline abuf = linebuf[ay % yBufSize];
        if (debug)
          System.out.println("  abuf.y / ayf " + abuf.y + " / " + ay);
        if (abuf.y != ay) {
          // scanline needs to be fetched from src raster
          abuf.y = ay;

          if (ay0 + ay <= yFetched)
            throw new AssertionError("Backtracking from line " + yFetched + " to " + (ay0 + ay));

          buffer.fetch(srcBounds.x, ay0 + ay);

          yFetched = ay0 + ay;

          // filter it into the appropriate line of linebuf (xfilt)
          buffer.filter(NO_SHIFT, bitsPerChannel, xweights, abuf);
        }

        // add weighted tbuf into accum (these do yfilt)
        abuf.accumulate(yweight.weights[ay - yweight.i0], accumulator);
      }

      accumulator.shift(finalShift);
      accumulator.store(dstBounds.x, by);
      if (debug)
        System.out.printf("\n");
    }
  }

  // loop over dest scanlines
  private void resizeYfirst(final Object src, final Rectangle srcROI, final Object dst, final Rectangle dstROI,
      final ParameterizedFilter fx, final ParameterizedFilter fy) {
    // destination scanline buffer
    final Scanline buffer = createScanline(src, dst, dstROI.width);

    // accumulator buffer
    final Scanline accumulator = createScanline(src, dst, srcROI.width);

    // a sampled filter for source pixels for each dest x position
    final Weighttab xweights[] = createXWeights(srcROI, dstROI, fx);

    // Circular buffer of active lines
    final int yBufSize = fy.width + 2;
    final Scanline linebuf[] = new Scanline[yBufSize];
    for (int y = 0; y < yBufSize; y++) {
      linebuf[y] = createScanline(src, dst, srcROI.width);
      linebuf[y].y = -1; /* mark scanline as unread */
    }

    // range of source and destination scanlines in regions
    final int ay0 = srcROI.y;
    final int ay1 = srcROI.y + srcROI.height;
    final int by0 = dstROI.y;
    final int by1 = dstROI.y + dstROI.height;

    int yFetched = -1; // used to assert no backtracking

    // loop over dest scanlines
    for (int by = by0; by < by1; by++) {
      // prepare a weighttab for dest y position by
      // a single sampled filter for current y pos
      final Weighttab yweight = new Weighttab(fy, weightOne, mappingY.mapPixelCenter(by), ay0, ay1 - 1, true);

      accumulator.clear();

      // loop over source scanlines that contribute to this dest scanline
      for (int ay = yweight.i0; ay <= yweight.i1; ay++) {
        final Scanline abuf = linebuf[ay % yBufSize];
        if (abuf.y != ay) {
          // scanline needs to be fetched from src raster
          abuf.y = ay;

          if (ay0 + ay <= yFetched)
            throw new AssertionError("Backtracking from line " + yFetched + " to " + (ay0 + ay));

          abuf.fetch(srcROI.x, ay0 + ay);

          yFetched = ay0 + ay;
        }

        if (debug)
          System.out.println(by + "[] += " + ay + "[] * " + yweight.weights[ay - yweight.i0]);

        // add weighted abuf into accum (these do yfilt)
        abuf.accumulate(yweight.weights[ay - yweight.i0], accumulator);
      }

      // and filter it into the appropriate line of linebuf (xfilt)
      accumulator.filter(bitsPerChannel, finalShift, xweights, buffer);

      // store dest scanline into dest raster
      buffer.store(dstROI.x, by);
      if (debug)
        System.out.printf("\n");
    }
  }

  /**
   * @param src
   * @param srcBounds
   * @param dst
   * @param dstBounds
   * @param m
   * @param ax
   * @param ay
   */
  public void resize(final Object src, final Rectangle srcBounds, final Object dst, Rectangle dstBounds,
      final Filter xfilt, final Filter yfilt) {
    /*
     * find scale of filter in a space (source space) when minifying, ascale=1/scale, but when
     * magnifying, ascale=1
     */
    ParameterizedFilter ax = new ParameterizedFilter(xfilt, mappingX.scale);
    ParameterizedFilter ay = new ParameterizedFilter(yfilt, mappingY.scale);

    /* find valid dest window (transformed source + support margin) */
    final Rectangle bc = new Rectangle();
    bc.setFrameFromDiagonal(//
        Utils.ceil(mappingX.srcToDst(srcBounds.x - ax.support) + EPSILON), //
        Utils.ceil(mappingY.srcToDst(srcBounds.y - ay.support) + EPSILON), //
        Utils.floor(mappingX.srcToDst(srcBounds.x + srcBounds.width + ax.support) - EPSILON), //
        Utils.floor(mappingY.srcToDst(srcBounds.y + srcBounds.height + ay.support) - EPSILON) //
    );

    if (dstBounds.x < bc.x || dstBounds.getMaxX() > bc.getMaxX() || dstBounds.y < bc.y
        || dstBounds.getMaxY() > bc.getMaxY())
      /* requested dest window lies outside the valid dest, so clip dest */
      dstBounds = dstBounds.intersection(bc);

    if (srcBounds.isEmpty() || dstBounds.width <= 0 || dstBounds.height <= 0)
      return;

    /* check for high-level simplifications of filter */
    ax = simplifyFilter(ax, mappingX.scale, mappingX.offset);
    ay = simplifyFilter(ay, mappingY.scale, mappingY.offset);

    /*
     * decide which filtering order (xy or yx) is faster for this mapping by counting convolution
     * multiplies
     */
    final boolean orderXY = order != Order.AUTO ? order == Order.XY : dstBounds.width
        * (srcBounds.height * ax.width + dstBounds.height * ay.width) < dstBounds.height
        * (dstBounds.width * ax.width + srcBounds.width * ay.width);

    // choose most efficient filtering order
    if (orderXY)
      resizeXfirst(src, srcBounds, dst, dstBounds, ax, ay);
    else
      resizeYfirst(src, srcBounds, dst, dstBounds, ax, ay);
  }

  protected abstract Scanline createScanline(final Object src, Object dst, final int length);

  public int getWeightBits() {
    return weightBits;
  }

  public void setWeightBits(int weightBits) {
    this.weightBits = weightBits;
    weightOne = 1 << weightBits;

    for (int i = 0; i < finalShift.length; i++)
      finalShift[i] = 2 * weightBits - bitsPerChannel[i];
  }

  public int[] getBitsPerChannel() {
    return bitsPerChannel;
  }

  protected void setBitsPerChannel(int bitsPerChannel[]) {
    this.bitsPerChannel = bitsPerChannel;

    finalShift = new int[bitsPerChannel.length];
    for (int i = 0; i < finalShift.length; i++)
      finalShift[i] = 2 * weightBits - bitsPerChannel[i];
  }

  protected double dstXtoSrcX(double x) {
    return mappingX.dstToSrc(x);
  }

  protected double dstYtoSrcY(double y) {
    return mappingY.dstToSrc(y);
  }

  protected Rectangle2D dstBoundsToSrcBounds(Rectangle2D bounds) {
    final Rectangle2D.Double srcBounds = new Rectangle2D.Double();
    srcBounds.setFrameFromDiagonal(mappingX.dstToSrc(bounds.getMinX()), mappingY.dstToSrc(bounds.getMinY()),
        mappingX.dstToSrc(bounds.getMaxX()), mappingY.dstToSrc(bounds.getMaxY()));
    return srcBounds;
  }

  protected Rectangle dstBoundsToSrcBoundsOnGrid(Rectangle2D bounds) {
    return Utils.enlargeRectToGrid(dstBoundsToSrcBounds(bounds));
  }

  protected double srcXtoDstX(double x) {
    return mappingX.srcToDst(x);
  }

  protected double srcYtoDstY(double y) {
    return mappingY.srcToDst(y);
  }

  protected Rectangle2D srcBoundsToDstBounds(Rectangle2D bounds) {
    final Rectangle2D.Double srcBounds = new Rectangle2D.Double();
    srcBounds.setFrameFromDiagonal(mappingX.srcToDst(bounds.getMinX()), mappingY.srcToDst(bounds.getMinY()),
        mappingX.srcToDst(bounds.getMaxX()), mappingY.srcToDst(bounds.getMaxY()));
    return srcBounds;
  }

  protected Rectangle srcBoundsToDstBoundsOnGrid(Rectangle2D bounds) {
    return Utils.enlargeRectToGrid(srcBoundsToDstBounds(bounds));
  }
}
