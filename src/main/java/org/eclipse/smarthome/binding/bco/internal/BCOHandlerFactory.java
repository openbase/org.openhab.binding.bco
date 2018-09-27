package org.eclipse.smarthome.binding.bco.internal;

/*-
 * #%L
 * BCO Binding
 * %%
 * Copyright (C) 2018 openbase.org
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Lesser Public License for more details.
 * 
 * You should have received a copy of the GNU General Lesser Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/lgpl-3.0.html>.
 * #L%
 */

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.thing.ThingTypeUID;
import org.eclipse.smarthome.core.thing.binding.BaseThingHandlerFactory;
import org.eclipse.smarthome.core.thing.binding.ThingHandler;
import org.eclipse.smarthome.core.thing.binding.ThingHandlerFactory;
import org.openbase.jps.core.JPService;
import org.openbase.jps.exception.JPNotAvailableException;
import org.openbase.jul.extension.rsb.com.jp.JPRSBHost;
import org.openbase.jul.extension.rsb.com.jp.JPRSBPort;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Dictionary;

/**
 * The {@link BCOHandlerFactory} is responsible for creating things and thing
 * handlers.
 *
 * @author Tamino Huxohl - Initial contribution
 */
@NonNullByDefault
@Component(configurationPid = "binding.bco", service = ThingHandlerFactory.class)
public class BCOHandlerFactory extends BaseThingHandlerFactory {

    private final Logger logger = LoggerFactory.getLogger(BCOHandlerFactory.class);

    @Override
    public boolean supportsThingType(final ThingTypeUID thingTypeUID) {
        return BCOBindingConstants.THING_TYPES.contains(thingTypeUID);
    }

    @Override
    protected @Nullable ThingHandler createHandler(final Thing thing) {
        return new UnitHandler(thing);
    }

    @Override
    protected void activate(ComponentContext componentContext) {
        super.activate(componentContext);

        final Dictionary<String, Object> properties = componentContext.getProperties();
        final Object rsbHost = properties.get("rsbHost");
        if (rsbHost instanceof String) {
            JPService.registerProperty(JPRSBHost.class, (String) rsbHost);
        }

        final Object rsbPort = properties.get("rsbPort");
        if (rsbPort instanceof Integer) {
            JPService.registerProperty(JPRSBPort.class, (Integer) rsbPort);
        }

        try {
            logger.warn("Activate handler factory with host {} and port {}. In properties: {} and {}", rsbHost, rsbPort,
                    JPService.getProperty(JPRSBHost.class).getValue(), JPService.getProperty(JPRSBPort.class).getValue());
        } catch (JPNotAvailableException e) {
            // do nothing
        }
    }

    @Override
    protected void deactivate(ComponentContext componentContext) {
        super.deactivate(componentContext);
        logger.warn("Deactivate handler factory");
    }
}
