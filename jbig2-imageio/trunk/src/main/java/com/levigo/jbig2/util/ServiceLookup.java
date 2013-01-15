package com.levigo.jbig2.util;

import java.util.Iterator;

import javax.imageio.spi.ServiceRegistry;

public class ServiceLookup<B> {

  public Iterator<B> getServices(Class<B> cls) {
    return getServices(cls, null);
  }

  public Iterator<B> getServices(Class<B> cls, ClassLoader clsLoader) {
    Iterator<B> services = ServiceRegistry.lookupProviders(cls);

    if (!services.hasNext()) {
      services = ServiceRegistry.lookupProviders(cls, cls.getClass().getClassLoader());
    }

    if (!services.hasNext() && clsLoader != null) {
      services = ServiceRegistry.lookupProviders(cls, clsLoader);
    }

    return services;
  }

}
