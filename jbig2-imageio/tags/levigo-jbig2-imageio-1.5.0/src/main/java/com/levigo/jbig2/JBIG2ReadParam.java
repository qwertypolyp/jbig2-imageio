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

package com.levigo.jbig2;

import java.awt.Dimension;
import java.awt.Rectangle;

import javax.imageio.ImageReadParam;

import com.levigo.jbig2.util.log.Logger;
import com.levigo.jbig2.util.log.LoggerFactory;

/**
 * This class extends {@code ImageReadParam} and contains region of interest and scale / subsampling
 * functionality
 * 
 * @author <a href="mailto:m.krzikalla@levigo.de">Matthäus Krzikalla</a>
 */
public class JBIG2ReadParam extends ImageReadParam {
  private static final Logger log = LoggerFactory.getLogger(JBIG2ReadParam.class);

  public JBIG2ReadParam(int sourceXSubsampling, int sourceYSubsampling, int subsamplingXOffset, int subsamplingYOffset,
      Rectangle sourceRegion, Dimension sourceRenderSize) {

    this.setSubsampleValues(sourceXSubsampling, sourceYSubsampling, subsamplingXOffset, subsamplingYOffset);
    this.setSourceRegion(sourceRegion);

    canSetSourceRenderSize = true;

    if (sourceRenderSize != null) {
      this.setSourceRenderSize(sourceRenderSize);
    }
  }

  private void setSubsampleValues(int sourceXSubsampling, int sourceYSubsampling, int subsamplingXOffset,
      int subsamplingYOffset) {

    if (sourceXSubsampling < 1 || sourceYSubsampling < 1) {
      log.info("Illegal subsampling factor: must be 1 or greater. Scaling ignored" + " sourceXSubsampling="
          + sourceXSubsampling + ", sourceYSubsampling=" + sourceYSubsampling);

      sourceXSubsampling = sourceYSubsampling = 1;
    }

    this.setSourceSubsampling(sourceXSubsampling, sourceYSubsampling, subsamplingXOffset, subsamplingYOffset);
  }
}
