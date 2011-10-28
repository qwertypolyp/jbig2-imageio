/**
 * Copyright (C) 1995-2010 levigo holding gmbh.
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

package com.levigo.jbig2.util;

import java.awt.Dimension;
import java.awt.Rectangle;
import java.util.List;

import com.levigo.jbig2.Bitmap;
import com.levigo.jbig2.JBIG2ReadParam;
import com.levigo.jbig2.TestImage;

/**
 * This class is for debug purpose only. The {@code DictionaryViewer} is able to show a single
 * bitmap or all symbol bitmaps.
 * 
 * @author <a href="mailto:m.krzikalla@levigo.de">Matthäus Krzikalla</a>
 * @author Benjamin Zindel
 */
class DictionaryViewer {

  public static void show(Bitmap b) {
    JBIG2ReadParam param = new JBIG2ReadParam(1, 1, 0, 0, new Rectangle(0, 0, b.getWidth(), b.getHeight()),
        new Dimension(b.getWidth(), b.getHeight()));

    new TestImage(b.getBufferedImage(param));
  }

  public static void show(List<Bitmap> symbols) {
    int width = 0;
    int height = 0;

    for (Bitmap b : symbols) {
      width += b.getWidth();

      if (b.getHeight() > height) {
        height = b.getHeight();
      }
    }

    Bitmap result = new Bitmap(width, height);

    JBIG2ReadParam param = new JBIG2ReadParam(1, 1, 0, 0, new Rectangle(0, 0, result.getWidth(), result.getHeight()),
        new Dimension(result.getWidth(), result.getHeight()));

    int xOffset = 0;

    for (Bitmap b : symbols) {
      result.blit(b, xOffset, 0, CombinationOperator.REPLACE);
      xOffset += b.getWidth();
    }

    new TestImage(result.getBufferedImage(param));
  }
}