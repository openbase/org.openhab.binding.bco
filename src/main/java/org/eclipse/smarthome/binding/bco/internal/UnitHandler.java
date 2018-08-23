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
import org.eclipse.smarthome.core.library.types.OnOffType;
import org.eclipse.smarthome.core.thing.ChannelUID;
import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.thing.ThingStatus;
import org.eclipse.smarthome.core.thing.binding.BaseThingHandler;
import org.eclipse.smarthome.core.types.Command;
import org.openbase.bco.authentication.lib.SessionManager;
import org.openbase.bco.dal.lib.layer.service.Services;
import org.openbase.bco.dal.lib.layer.unit.UnitRemote;
import org.openbase.bco.dal.remote.unit.Units;
import org.openbase.bco.dal.remote.unit.location.LocationRemote;
import org.openbase.bco.registry.login.SystemLogin;
import org.openbase.jul.exception.CouldNotPerformException;
import org.openbase.jul.exception.NotAvailableException;
import org.openbase.jul.pattern.Observer;
import org.openbase.jul.pattern.Remote.ConnectionState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rst.domotic.service.ServiceTemplateType.ServiceTemplate.ServiceType;
import rst.domotic.state.PowerStateType.PowerState;
import rst.domotic.unit.UnitTemplateType.UnitTemplate.UnitType;

/**
 * The {@link UnitHandler} is responsible for handling commands, which are
 * sent to one of the channels.
 *
 * @author Tamino Huxohl - Initial contribution
 */
@NonNullByDefault
public class UnitHandler extends BaseThingHandler {

    private final Logger logger = LoggerFactory.getLogger(UnitHandler.class);

    private final Observer<ConnectionState> connectionStateObserver;
    private final Observer unitDataObserver;

    private UnitRemote unitRemote;
//
//    @Nullable
//    private BCOConfiguration config;

    public UnitHandler(Thing thing) {
        super(thing);
        logger.info("Create unit handler for thing {}", thing.getUID().toString());
        connectionStateObserver = (observable, connectionState) -> {
            switch (connectionState) {
                case CONNECTED:
                    updateStatus(ThingStatus.ONLINE);
                    break;
                default:
                    updateStatus(ThingStatus.OFFLINE);
                    break;
            }
        };
        unitDataObserver = (observable, o) -> {
            PowerState powerState = (PowerState) Services.invokeProviderServiceMethod(ServiceType.POWER_STATE_SERVICE, unitRemote);
            updateState(BCOBindingConstants.CHANNEL_POWER, PowerStateTransformer.transform(powerState));
        };
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        logger.info("Receive command {} for channel {} of unit {}", command.getClass().getSimpleName(), channelUID.getId(), getThing().getUID().getId());
        if (!SessionManager.getInstance().isLoggedIn()) {
            try {
                SystemLogin.loginBCOUser();
            } catch (CouldNotPerformException | InterruptedException ex) {
                logger.error("Could not login bco user", ex);
            }
        }

        // TODO: maybe if action fails updateState to last one so that failure is clear
        try {
            switch (channelUID.getId()) {
                case BCOBindingConstants.CHANNEL_POWER:
                    if (command instanceof OnOffType) {
                        logger.info("Invoke power state for unit");
                        Services.invokeOperationServiceMethod(ServiceType.POWER_STATE_SERVICE, unitRemote, PowerStateTransformer.transform((OnOffType) command));
                    }
                    break;
                case BCOBindingConstants.CHANNEL_POWER_LIGHT:
                    // handle special case of setting power state for lights in a location
                    if (command instanceof OnOffType) {
                        ((LocationRemote) unitRemote).setPowerState(PowerStateTransformer.transform((OnOffType) command), UnitType.LIGHT);
                    }
                    break;
            }
        } catch (CouldNotPerformException ex) {
            logger.error("Could not handle command", ex);
        }
    }

    @Override
    public void initialize() {
//        config = getConfigAs(BCOConfiguration.class);

        try {
            unitRemote = Units.getUnit(getThing().getUID().getId(), false);
            unitRemote.addConnectionStateObserver(connectionStateObserver);
            unitRemote.addDataObserver(unitDataObserver);
        } catch (NotAvailableException | InterruptedException ex) {
            logger.error("Could not initialize thing for unit remote", ex);
        }
    }

    @Override
    public void dispose() {
        unitRemote.removeConnectionStateObserver(connectionStateObserver);
        unitRemote.removeDataObserver(unitDataObserver);
        updateStatus(ThingStatus.OFFLINE);
    }
}
