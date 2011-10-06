/**
 * Copyright (C) 1995-2010 levigo holding gmbh.
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

package com.levigo.jbig2.util.cache;

import java.util.Iterator;

import javax.imageio.spi.ServiceRegistry;

/**
 * Retrieves a {@link Cache} via registered {@link CacheBridge} through <code>META-INF/services</code> lookup.
 * 
 * @author <a href="mailto:m.krzikalla@levigo.de">Matthäus Krzikalla</a>
 */
public class CacheFactory {
  private static CacheBridge cacheBridge;

  public static Cache getCache() {
    if (null == cacheBridge) {
      Iterator<CacheBridge> cacheBridgeServices = ServiceRegistry.lookupProviders(CacheBridge.class);
      if (!cacheBridgeServices.hasNext()) {
        throw new IllegalStateException("No implementation of " + CacheBridge.class
            + " was avaliable using META-INF/services lookup");
      }
      cacheBridge = cacheBridgeServices.next();
    }
    return cacheBridge.getCache();
  }
}
