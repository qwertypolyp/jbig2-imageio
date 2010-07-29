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

import java.lang.ref.SoftReference;
import java.util.HashMap;

/**
 * @author <a href="mailto:m.krzikalla@levigo.de">Matthäus Krzikalla</a>
 */
public class SoftReferenceCache implements Cache {

  private HashMap<Object, SoftReference<?>> cache = new HashMap<Object, SoftReference<?>>();

  public Object put(Object key, Object value) {
    SoftReference<Object> softReferenceToValue = new SoftReference<Object>(value);
    cache.put(key, softReferenceToValue);
    return softReferenceToValue.get();
  }

  public Object get(Object key) {
    SoftReference<?> softReference = cache.get(key);
    if (null == softReference) {
      return null;
    }

    return softReference.get();
  }

  public void clear() {
    cache.clear();
  }

  public Object remove(Object key) {
    return cache.remove(key).get();
  }

}
