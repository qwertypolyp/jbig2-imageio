/**
 * Copyright (C) 1995-2012 levigo holding gmbh.
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

package com.levigo.jbig2.util.log;

import java.util.Iterator;

import javax.imageio.spi.ServiceRegistry;

/**
 * Retrieves a {@link Logger} via registered {@link LoggerBridge} through META-INF/services lookup.
 * 
 * @author <a href="mailto:m.krzikalla@levigo.de">Matthäus Krzikalla</a>
 */
public class LoggerFactory {

  private static LoggerBridge loggerBridge;


  public static Logger getLogger(Class<?> clazz) {
    if (null == loggerBridge) {
      Iterator<LoggerBridge> loggerBridgeServices = ServiceRegistry.lookupProviders(LoggerBridge.class);
      if (!loggerBridgeServices.hasNext()) {
        throw new IllegalStateException("No implementation of " + LoggerBridge.class
            + " was avaliable using META-INF/services lookup");
      }
      loggerBridge = loggerBridgeServices.next();
    }

    return loggerBridge.getLogger(clazz);
  }
}
