package com.levigo.jbig2;

import java.awt.Dimension;
import java.awt.Rectangle;

import javax.imageio.ImageReadParam;

public class PreconfiguredImageReadParam extends ImageReadParam {
  public PreconfiguredImageReadParam(Rectangle sourceRegion) {
    this.sourceRegion = sourceRegion;
  }

  public PreconfiguredImageReadParam(Dimension sourceRenderSize) {
    this.sourceRenderSize = sourceRenderSize;
  }

  public PreconfiguredImageReadParam(int sourceXSubsampling, int sourceYSubsampling, int subsamplingXOffset,
      int subsamplingYOffset) {
    this.sourceXSubsampling = sourceXSubsampling;
    this.sourceYSubsampling = sourceYSubsampling;
    this.subsamplingXOffset = subsamplingXOffset;
    this.subsamplingYOffset = subsamplingYOffset;
  }

  public PreconfiguredImageReadParam(Rectangle sourceRegion, Dimension sourceRenderSize) {
    this.sourceRegion = sourceRegion;
    this.sourceRenderSize = sourceRenderSize;
  }

  public PreconfiguredImageReadParam(Rectangle sourceRegion, Dimension sourceRenderSize, int sourceXSubsampling,
      int sourceYSubsampling, int subsamplingXOffset, int subsamplingYOffset) {
    this.sourceRegion = sourceRegion;
    this.sourceRenderSize = sourceRenderSize;
    this.sourceXSubsampling = sourceXSubsampling;
    this.sourceYSubsampling = sourceYSubsampling;
    this.subsamplingXOffset = subsamplingXOffset;
    this.subsamplingYOffset = subsamplingYOffset;
  }
}