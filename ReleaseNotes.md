### 1.6.1 ###
**Release date: 2013-04-18**

Changes:
  * [Issue 11](https://code.google.com/p/jbig2-imageio/issues/detail?id=11): Added support for `GRREFERENCEDX` in generic refinement region coding.

### 1.6.0 ###
**Release date: 2013-03-28**

Changes:
  * [Issue 10](https://code.google.com/p/jbig2-imageio/issues/detail?id=10): Usability of `CacheFactory` and `LoggerFactory` improved. Both can be configured with a specific `ClassLoader` to work with.


---

### 1.5.2 ###
**Release date: 2012-10-09**

Changes:
  * FIX [Issue 9](https://code.google.com/p/jbig2-imageio/issues/detail?id=9): Transfer of bitmap's data into target raster went wrong if bitmap's line ends with a padded byte. The problem was fixed in `Bitmaps#buildRaster(..)`.


---

### 1.5.1 ###
**Release date: 2012-10-02**

Changes:
  * FIX [Issue 8](https://code.google.com/p/jbig2-imageio/issues/detail?id=8): The default read parameters changed. There will be no source region, no source render size (no scaling) and subsampling factors of 1 (no subsampling). ´Bitmaps´ can handle this correctly.


---

### 1.5.0 ###
**Release date: 2012-09-20**

Changes:
  * Moved exception classes to `com.levigo.jbig2.err` package.
  * Introduced new utility class `com.levigo.jbig2.image.Bitmaps` that provides a bunch of new features operating on a Bitmap instance. For example:
    * extracting a region of interest
    * scaling with high-quality filters
    * subsampling in either or both horizontal and vertical direction.


---

### 1.4.1 ###
**Release date: 2012-04-20**

Changes:
  * FIX: Fixed a problem when parsing a multi-page jbig2 file.
  * IMPROVEMENT: The `JBIG2Document` class doesn't need the isEmbedded flag anymore. The determination is done automatically and is simply used as an indicator whether the file header is present and should be parsed or not.


---

### 1.4 ###
**Release date: 2012-04-10**

Changes:
  * FIX [Issue 6](https://code.google.com/p/jbig2-imageio/issues/detail?id=6): The returned bitmap was too small in case of only one region. Solution is to check if we have only one region that forms the complete page. Only if width and height of region equals width and height of page use region's bitmap as the page's bitmap.
  * FIX [Issue 5](https://code.google.com/p/jbig2-imageio/issues/detail?id=5): A raster which was too small was created. AWT has thrown a `RasterFormatException`. (Provided by Chris Laws)
  * FIX [Issue 4](https://code.google.com/p/jbig2-imageio/issues/detail?id=4): `IndexOutOfBoundsException` indicates the end of the stream in `JBIG2Document#reachedEndOfStream()`
  * IMPROVEMENT [Issue 3](https://code.google.com/p/jbig2-imageio/issues/detail?id=3): Reader recognizes if a file header is present or not.


---

### 1.3 (Bugfix Release) ###
**Release date: 2011-10-28**

Changes:
  * FIX Untracked Issue: Fixed inverted color model for grayscale images.
  * FIX Untracked Issue: Fixed `IndexArrayOutOfBoundException` in handling requests with region of interests. The region of interest is clipped at image boundary.


---

### 1.2 (Bugfix Release) ###
**Release date: 2011-10-06**

Changes:
  * FIX [Issue 1](https://code.google.com/p/jbig2-imageio/issues/detail?id=1): The default read params will return a default image size of 1x1 without claiming the missing input.
  * FIX Untracked Issue: A black pixel was represented by 1 and a white pixel by 0. By convention, a black pixel should be the minimum (`0x0`) and the white pixel the maximum (`0xFF`). This corresponds to an additive color model. We turned the representation of white and black pixels for conformity.


---

### 1.1 ###
**Release date: 2010-12-13**

Changes:
  * IMPROVEMENT: raster creation optimized
  * FIX : potential NPE in cache


---

### 1.0 ###
**Release date: 2010-07-29**

Changes:
  * Initial upload of this project