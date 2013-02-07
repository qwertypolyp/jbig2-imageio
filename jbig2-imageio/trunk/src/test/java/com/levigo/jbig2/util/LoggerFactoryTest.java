package com.levigo.jbig2.util;

import static org.junit.Assert.assertNotNull;

import org.junit.Test;

import com.levigo.jbig2.util.log.LoggerBridge;
import com.levigo.jbig2.util.log.LoggerFactory;

public class LoggerFactoryTest {

  @Test
  public void testWithDefaultClassLoader() {
    LoggerFactory.setClassLoader(LoggerBridge.class.getClassLoader());
    assertNotNull(LoggerFactory.getLogger(LoggerFactoryTest.class));
  }
  
  @Test
  public void testWithContextClassLoader() {
    LoggerFactory.setClassLoader(Thread.currentThread().getContextClassLoader());
    assertNotNull(LoggerFactory.getLogger(LoggerFactoryTest.class));
  }
  

}
