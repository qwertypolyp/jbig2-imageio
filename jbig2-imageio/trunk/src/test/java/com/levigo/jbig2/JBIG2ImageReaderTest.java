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
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;

import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

import junit.framework.Assert;

import org.junit.Ignore;
import org.junit.Test;

import com.levigo.jbig2.io.DefaultInputStreamFactory;
import com.levigo.jbig2.util.IntegerMaxValueException;
import com.levigo.jbig2.util.InvalidHeaderValueException;

public class JBIG2ImageReaderTest {

  @Test
  public void testGetDefaultReadParams() throws Exception {
    ImageReader reader = new JBIG2ImageReader(new JBIG2ImageReaderSpi(), false);
    ImageReadParam param = reader.getDefaultReadParam();
    Assert.assertNotNull(param);

    Rectangle sourceRegion = param.getSourceRegion();
    Assert.assertEquals(1, sourceRegion.width);
    Assert.assertEquals(1, sourceRegion.height);

    Dimension srs = param.getSourceRenderSize();
    Assert.assertEquals(1, srs.width);
    Assert.assertEquals(1, srs.height);

    Assert.assertEquals(1, param.getSourceXSubsampling());
    Assert.assertEquals(1, param.getSourceYSubsampling());

  }

  // TESTS WITH TESTOUTPUT
  // Ignore in build process

  @Ignore
  @Test
  public void testRead() throws IOException, InvalidHeaderValueException, IntegerMaxValueException {

    String filepath = "/images/jbig2-stream.jb2";
    int imageIndex = 0;

    InputStream inputStream = getClass().getResourceAsStream(filepath);
    DefaultInputStreamFactory disf = new DefaultInputStreamFactory();
    ImageInputStream imageInputStream = disf.getInputStream(inputStream);

    JBIG2ImageReader imageReader = new JBIG2ImageReader(new JBIG2ImageReaderSpi(), true);

    imageReader.setInput(imageInputStream);
    JBIG2ReadParam param = new JBIG2ReadParam(1, 1, 0, 0, new Rectangle(0, 0, 500, 800), new Dimension(500, 800));

    long timeStamp = System.currentTimeMillis();
    BufferedImage bufferedImage = imageReader.read(imageIndex, param);
    long duration = System.currentTimeMillis() - timeStamp;
    System.out.println(filepath + " decoding took " + duration + " ms");

    new TestImage(bufferedImage);
  }

  @Ignore
  @Test
  public void testReadIntImageReadParamNotEmbedded() throws IOException, InvalidHeaderValueException,
      IntegerMaxValueException {

    InputStream inputStream = getClass().getResourceAsStream("/images/042_1.jb2");
    DefaultInputStreamFactory disf = new DefaultInputStreamFactory();
    ImageInputStream imageInputStream = disf.getInputStream(inputStream);
    JBIG2ImageReader imageReader = new JBIG2ImageReader(new JBIG2ImageReaderSpi(), false);
    imageReader.setInput(imageInputStream);

    new TestImage(imageReader.read(3, null));
  }
}
