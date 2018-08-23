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
import org.eclipse.smarthome.config.core.Configuration;
import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.thing.ThingTypeUID;
import org.eclipse.smarthome.core.thing.ThingUID;
import org.eclipse.smarthome.core.thing.binding.BaseThingHandlerFactory;
import org.eclipse.smarthome.core.thing.binding.ThingHandler;
import org.eclipse.smarthome.core.thing.binding.ThingHandlerFactory;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.Set;

import static org.eclipse.smarthome.binding.bco.internal.BCOBindingConstants.THING_TYPE_LOCATION;

/**
 * The {@link BCOHandlerFactory} is responsible for creating things and thing
 * handlers.
 *
 * @author Tamino Huxohl - Initial contribution
 */
@NonNullByDefault
@Component(configurationPid = "binding.bco", service = ThingHandlerFactory.class)
public class BCOHandlerFactory extends BaseThingHandlerFactory {

    public static final Set<ThingTypeUID> SUPPORTED_THING_TYPES_UIDS = Collections.singleton(THING_TYPE_LOCATION);

    private final Logger logger = LoggerFactory.getLogger(BCOHandlerFactory.class);

    public BCOHandlerFactory() {
        logger.warn("BCO Handler factory created");
    }

    @Override
    public boolean supportsThingType(ThingTypeUID thingTypeUID) {
        logger.warn("BCO supported things returned {}, {}", thingTypeUID, SUPPORTED_THING_TYPES_UIDS.contains(thingTypeUID));
        return SUPPORTED_THING_TYPES_UIDS.contains(thingTypeUID);
    }

    @Override
    protected @Nullable Thing createThing(ThingTypeUID thingTypeUID, Configuration configuration, ThingUID thingUID) {
        logger.warn("Create thing {}, {}, {}", thingUID, thingTypeUID, configuration);
        return super.createThing(thingTypeUID, configuration, thingUID);
    }

    @Override
    protected @Nullable ThingHandler createHandler(Thing thing) {
        logger.warn("Create handler called");
        ThingTypeUID thingTypeUID = thing.getThingTypeUID();

        logger.warn("Create thing for type {}, location type {}", thing.getThingTypeUID(), THING_TYPE_LOCATION);
        if (THING_TYPE_LOCATION.equals(thingTypeUID)) {
            return new UnitHandler(thing);
        }

        return null;
    }
}
