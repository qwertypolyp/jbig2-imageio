/**
 * <pre>
 * Copyright (c) 1995-2012 levigo holding gmbh. All Rights Reserved.
 *
 * This software is the proprietary information of levigo holding gmbh.
 * Use is subject to license terms.
 * </pre>
 */
package com.levigo.jbig2.image;


/**
 * A FilterType enum for defining certain downscale filters to apply.
 *
 * @author <a href="mailto:c.koehler@levigo.de">Carolin Koehler</a>
 */
public enum FilterType {
  Bessel,
  Blackman,
  Box,
  Catrom,
  Cubic,
  Gaussian,
  Hamming,
  Hanning,
  Hermite,
  Lanczos,
  Mitchell,
  Point,
  Quadratic,
  Sinc,
  Triangle;

  private static FilterType defaultFilter = Triangle;

  public static void setDefaultFilterType(FilterType defaultFilter) {
    FilterType.defaultFilter = defaultFilter;
  }

  public static FilterType getDefaultFilterType() {
    return defaultFilter;
  }
}