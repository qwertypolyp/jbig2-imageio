package com.levigo.jbig2.util;

import static org.junit.Assert.*;

import java.util.Iterator;

import org.junit.Test;

public class ServiceLookupTest {

  @Test
  public void withDefaultClassLoader() {
    ServiceLookup<TestService> serviceLookup = new ServiceLookup<TestService>();
    Iterator<TestService> services = serviceLookup.getServices(TestService.class);
    assertTrue(services.hasNext());
    assertEquals(TestServiceImpl.class, services.next().getClass());
  }

}
