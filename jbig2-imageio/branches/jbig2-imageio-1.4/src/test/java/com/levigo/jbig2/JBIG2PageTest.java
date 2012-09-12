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
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;

import javax.imageio.stream.ImageInputStream;

import org.junit.Ignore;
import org.junit.Test;

import com.levigo.jbig2.io.DefaultInputStreamFactory;
import com.levigo.jbig2.util.JBIG2Exception;

public class JBIG2PageTest {

  // TESTS WITH TESTOUTPUT
  // Ignore in build process

  @Ignore
  @Test
  public void composeDisplayTest() throws IOException, JBIG2Exception {

    String filepath = "/images/amb_1.jb2";
    int pageNumber = 1;

    InputStream is = getClass().getResourceAsStream(filepath);
    DefaultInputStreamFactory disf = new DefaultInputStreamFactory();
    ImageInputStream iis = disf.getInputStream(is);
    JBIG2Document doc = new JBIG2Document(iis);

    BufferedImage b = doc.getPage(pageNumber).getBitmap().getBufferedImage(
        new JBIG2ReadParam(1, 1, 0, 0, new Rectangle(166, 333, 555, 444), null));
    // Bitmap bitmap = doc.getPage(pageNumber).getBitmap();
    // BufferedImage b = bitmap.getROI(new Rectangle(666, 333, 555, 444))
    // .getBufferedImage();
    new TestImage(b);
  }

  @Ignore
  @Test
  public void composeTestWithDurationCalc() throws IOException, JBIG2Exception {
    int runs = 40;
    long avg = 0;

    String path = "/images/042_8.jb2";
    int pageNumber = 1;

    System.out.println("File: " + path);

    InputStream is = getClass().getResourceAsStream(path);
    DefaultInputStreamFactory disf = new DefaultInputStreamFactory();
    ImageInputStream iis = disf.getInputStream(is);

    for (int i = 0; i < runs; i++) {

      long time = System.currentTimeMillis();
      JBIG2Document doc = new JBIG2Document(iis);
      JBIG2ReadParam param = new JBIG2ReadParam(1, 1, 0, 0, new Rectangle(0, 0, doc.getPage(pageNumber).getWidth(),
          doc.getPage(pageNumber).getHeight()), new Dimension(doc.getPage(pageNumber).getWidth(), doc.getPage(
          pageNumber).getHeight()));
      doc.getPage(pageNumber).getBitmap().getBufferedImage(param);
      long duration = System.currentTimeMillis() - time;

      System.out.println((i + 1) + ": " + duration + " ms");
      avg += duration;
    }
    System.out.println("Average: " + avg / runs);
  }

  @Ignore
  @Test
  public void composeTestWithDurationCalcAggregate() throws IOException, JBIG2Exception {
    int runs = 40;
    long avg = 0;
    String path = "/images/002.jb2";
    int pages = 17;

    System.out.println("File: " + path);

    InputStream is = getClass().getResourceAsStream(path);
    DefaultInputStreamFactory disf = new DefaultInputStreamFactory();
    ImageInputStream iis = disf.getInputStream(is);

    for (int j = 1; j <= pages; j++) {
      avg = 0;

      for (int i = 0; i < runs; i++) {
        long time = System.currentTimeMillis();
        JBIG2Document doc = new JBIG2Document(iis);
        doc.getPage(j).getBitmap().getBufferedImage(new JBIG2ReadParam(1, 1, 0, 0, null, null));
        long duration = System.currentTimeMillis() - time;
        System.out.print((i + 1) + ": " + duration + " ms ");
        avg += duration;
      }
      System.out.println();
      System.out.println("Page " + j + " Average: " + avg / runs);
    }
  }

}
