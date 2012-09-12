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

package com.levigo.jbig2.decoder.mmr;

import java.io.IOException;
import java.io.InputStream;

import javax.imageio.stream.ImageInputStream;

import junit.framework.Assert;

import org.junit.Ignore;
import org.junit.Test;

import com.levigo.jbig2.Bitmap;
import com.levigo.jbig2.TestImage;
import com.levigo.jbig2.err.InvalidHeaderValueException;
import com.levigo.jbig2.io.DefaultInputStreamFactory;
import com.levigo.jbig2.io.SubInputStream;

@Ignore
public class MMRDecompressorTest {

  @Ignore
  @Test
  public void mmrDecodingTest() throws IOException, InvalidHeaderValueException {
    InputStream is = getClass().getResourceAsStream("/images/sampledata.jb2");
    DefaultInputStreamFactory disf = new DefaultInputStreamFactory();
    ImageInputStream iis = disf.getInputStream(is);
    // Sixth Segment (number 5)
    SubInputStream sis = new SubInputStream(iis, 252, 38);

    MMRDecompressor mmrd = new MMRDecompressor(16 * 4, 4, sis);

    Bitmap b = mmrd.uncompress();

    new TestImage(b.getByteArray(), (int) b.getWidth(), (int) b.getHeight(), b.getRowStride());
  }

}
