package com.levigo.jbig2.util;

import static org.junit.Assert.assertNotNull;

import org.junit.Test;

import com.levigo.jbig2.util.cache.CacheBridge;
import com.levigo.jbig2.util.cache.CacheFactory;

public class CacheFactoryTest {

  @Test
  public void testWithDefaultClassLoader() {
    CacheFactory.setClassLoader(CacheBridge.class.getClassLoader());
    assertNotNull(CacheFactory.getCache());
  }

  @Test
  public void testWithContextClassLoader() {
    CacheFactory.setClassLoader(Thread.currentThread().getContextClassLoader());
    assertNotNull(CacheFactory.getCache());
  }


}
