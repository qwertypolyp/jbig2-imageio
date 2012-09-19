package com.levigo.jbig2;

import java.io.IOException;

import javax.imageio.stream.ImageInputStream;

import com.levigo.jbig2.err.JBIG2Exception;

public class JBIG2DocumentFacade extends JBIG2Document {

  public JBIG2DocumentFacade(ImageInputStream input) throws IOException {
    super(input);
  }

  public JBIG2DocumentFacade(ImageInputStream input, JBIG2Globals globals) throws IOException {
    super(input, globals);
  }

  public JBIG2Page getPage(int pageNumber) {
    return super.getPage(pageNumber);
  }

  public Bitmap getPageBitmap(int pageNumber) throws JBIG2Exception, IOException {
    return getPage(pageNumber).getBitmap();
  }

}
