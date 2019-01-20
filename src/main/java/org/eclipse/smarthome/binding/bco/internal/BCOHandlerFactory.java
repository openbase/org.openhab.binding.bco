package org.eclipse.smarthome.binding.bco.internal;

/*-
 * #%L
 * BCO Binding
 * %%
 * Copyright (C) 2018 - 2019 openbase.org
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
import org.openbase.bco.authentication.lib.SessionManager;
import org.openbase.bco.registry.remote.Registries;
import org.openbase.bco.registry.unit.lib.UnitRegistry;
import org.openbase.jul.exception.CouldNotPerformException;
import org.openbase.jul.exception.NotAvailableException;
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

    private static boolean initialActivate = true;

    @Override
    protected void activate(ComponentContext componentContext) {
        super.activate(componentContext);

        final Dictionary<String, Object> properties = componentContext.getProperties();

        if (!SessionManager.getInstance().isLoggedIn()) {
            try {
                Object credentials = properties.get("credentials");
                if (!(credentials instanceof String)) {
                    throw new NotAvailableException("Credentials");
                }
                Registries.waitForData();
                SessionManager.getInstance().loginClient(Registries.getUnitRegistry().getUnitConfigByAlias(UnitRegistry.OPENHAB_USER_ALIAS).getId(), (String) credentials);
            } catch (Exception e) {
                logger.error("Could not login as openhab user", e);
                if (!SessionManager.getInstance().isLoggedIn()) {
                    try {
                        SessionManager.getInstance().login(Registries.getUnitRegistry(true).getUserUnitIdByUserName("admin"), "admin");
                    } catch (CouldNotPerformException | InterruptedException ex) {
                        logger.error("Could not login admin", ex);
                    }
                }
            }
        }

        //TODO: reactivate if reinit works
//        try {
//            final Integer oldPort = JPService.getProperty(JPRSBPort.class).getValue();
//            final String oldHost = JPService.getProperty(JPRSBHost.class).getValue();
//            logger.info("OldHost {}, oldPort {}, initAct {}", oldHost, oldPort, initialActivate);
//
//            final Object rsbHost = properties.get("rsbHost");
//            if (rsbHost instanceof String) {
//                JPService.registerProperty(JPRSBHost.class, (String) rsbHost);
//            }
//
//            final Object rsbPort = properties.get("rsbPort");
//            if (rsbPort instanceof String) {
//                JPService.registerProperty(JPRSBPort.class, Integer.parseInt((String) rsbPort));
//            }
//            final String[] args = {};
//            JPService.parse(args);
//
//            final Integer newPort = JPService.getProperty(JPRSBPort.class).getValue();
//            final String newHost = JPService.getProperty(JPRSBHost.class).getValue();
//
//            //TODO: remove this when reactivate works
//            logger.info("Activate with RSBHost {} and RSBPort {}", newHost, newPort);
//            // do not perform re-init on initial start, only update properties
//            if (initialActivate) {
//                RSBDefaultConfig.reload();
//                RSBSharedConnectionConfig.reload();
//                initialActivate = false;
//                return;
//            }
//
//            logger.info("OldHost {}, oldPort {}, chH {}, chP {}", oldHost, oldPort, !oldPort.equals(newPort) || !oldHost.equals(newHost));
//            if (!oldPort.equals(newPort) || !oldHost.equals(newHost)) {
//                logger.info("RSBHost changed from {} to {}", oldHost, newHost);
//                logger.info("RSBPort changed from {} to {}", oldPort, newPort);
//
//                RSBDefaultConfig.reload();
//                RSBSharedConnectionConfig.reload();
//
//                try {
//                    logger.info("Reinit registries");
//                    Registries.reinitialize();
//                    logger.info("Reinit units");
//                    Units.reinitialize();
//                } catch (CouldNotPerformException ex) {
//                    logger.error("Could not reinitialize remotes after host and/or port change!", ex);
//                } catch (InterruptedException ex) {
//                    Thread.currentThread().interrupt();
//                }
//            }
//        } catch (JPServiceException ex) {
//            logger.error("Could not read or update JPProperty", ex);
//        }
    }

    @Override
    protected void deactivate(ComponentContext componentContext) {
        super.deactivate(componentContext);
        logger.warn("Deactivate handler factory");
    }
}
