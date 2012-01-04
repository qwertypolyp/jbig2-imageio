package com.levigo.jbig2;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;

import javax.imageio.stream.ImageInputStream;

import com.levigo.jbig2.io.DefaultInputStreamFactory;

public class JBIG2ImageReaderDemo {

  private String filepath;
  private int imageIndex;

  public JBIG2ImageReaderDemo(String filepath, int imageIndex) {
    this.filepath = filepath;
    this.imageIndex = imageIndex;
  }

  public void show() throws IOException {
    InputStream inputStream = getClass().getResourceAsStream(filepath);
    DefaultInputStreamFactory disf = new DefaultInputStreamFactory();
    ImageInputStream imageInputStream = disf.getInputStream(inputStream);

    JBIG2ImageReader imageReader = new JBIG2ImageReader(new JBIG2ImageReaderSpi(), true);

    imageReader.setInput(imageInputStream);
    JBIG2ReadParam param = imageReader.getDefaultReadParam();

    long timeStamp = System.currentTimeMillis();
    BufferedImage bufferedImage = imageReader.read(imageIndex, param);
    long duration = System.currentTimeMillis() - timeStamp;
    System.out.println(filepath + " decoding took " + duration + " ms");

    new TestImage(bufferedImage);
  }

  public static void main(String[] args) throws IOException {
    JBIG2ImageReaderDemo demo = new JBIG2ImageReaderDemo("/images/042_1.jb2", 0);
    demo.show();
  }

}
