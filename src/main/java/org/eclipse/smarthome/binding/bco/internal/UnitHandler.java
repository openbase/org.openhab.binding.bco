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

import com.google.protobuf.Message;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.smarthome.core.library.types.OnOffType;
import org.eclipse.smarthome.core.thing.Channel;
import org.eclipse.smarthome.core.thing.ChannelUID;
import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.thing.ThingStatus;
import org.eclipse.smarthome.core.thing.binding.BaseThingHandler;
import org.eclipse.smarthome.core.thing.binding.builder.ChannelBuilder;
import org.eclipse.smarthome.core.thing.binding.builder.ThingBuilder;
import org.eclipse.smarthome.core.types.Command;
import org.eclipse.smarthome.core.types.RefreshType;
import org.eclipse.smarthome.core.types.State;
import org.openbase.bco.device.openhab.manager.transform.PowerStateOnOffTypeTransformer;
import org.openbase.bco.device.openhab.manager.transform.ServiceStateCommandTransformer;
import org.openbase.bco.device.openhab.manager.transform.ServiceStateCommandTransformerPool;
import org.openbase.bco.device.openhab.manager.transform.ServiceTypeCommandMapping;
import org.openbase.bco.device.openhab.registry.synchronizer.OpenHABItemProcessor;
import org.openbase.bco.authentication.lib.SessionManager;
import org.openbase.bco.dal.lib.layer.service.Services;
import org.openbase.bco.dal.lib.layer.unit.MultiUnit;
import org.openbase.bco.dal.lib.layer.unit.UnitRemote;
import org.openbase.bco.dal.remote.layer.unit.Units;
import org.openbase.bco.dal.remote.layer.unit.location.LocationRemote;
import org.openbase.bco.registry.remote.Registries;
import org.openbase.bco.registry.remote.login.BCOLogin;
import org.openbase.jul.exception.CouldNotPerformException;
import org.openbase.jul.exception.CouldNotTransformException;
import org.openbase.jul.exception.NotAvailableException;
import org.openbase.jul.exception.TypeNotSupportedException;
import org.openbase.jul.extension.type.processing.LabelProcessor;
import org.openbase.jul.pattern.Observer;
import org.openbase.jul.pattern.controller.Remote;
import org.openbase.type.domotic.state.ConnectionStateType.ConnectionState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.openbase.type.domotic.service.ServiceConfigType.ServiceConfig;
import org.openbase.type.domotic.service.ServiceDescriptionType.ServiceDescription;
import org.openbase.type.domotic.service.ServiceTemplateType.ServiceTemplate.ServiceType;
import org.openbase.type.domotic.unit.UnitConfigType.UnitConfig;
import org.openbase.type.domotic.unit.UnitTemplateType.UnitTemplate.UnitType;

import java.util.HashSet;
import java.util.Set;

/**
 * The {@link UnitHandler} is responsible for handling commands, which are
 * sent to one of the channels.
 *
 * @author Tamino Huxohl - Initial contribution
 */
@NonNullByDefault
public class UnitHandler extends BaseThingHandler {

    private final Logger logger = LoggerFactory.getLogger(UnitHandler.class);

    private final Observer<Remote, ConnectionState.State> connectionStateObserver;
    private final Observer unitDataObserver, unitConfigObserver;

    private UnitRemote<?> unitRemote;

    public UnitHandler(Thing thing) {
        super(thing);
        logger.debug("Create unit handler for thing {}", thing.getUID().toString());
        connectionStateObserver = (observable, connectionState) -> {
            logger.debug("Unit {} switched to connection state {}", unitRemote.getLabel(), connectionState.name());
            switch (connectionState) {
                case CONNECTED:
                    updateStatus(ThingStatus.ONLINE);
                    break;
                default:
                    updateStatus(ThingStatus.OFFLINE);
                    break;
            }
        };
        unitConfigObserver = (source, config) -> updateThingConfig();
        unitDataObserver = (source, data) -> updateChannels();
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        logger.debug("Receive command {} for channel {} of unit {}", command.getClass().getSimpleName(), channelUID.getId(), getThing().getUID().getId());
        //TODO: login, when yes which user?
        if (!SessionManager.getInstance().isLoggedIn()) {
            try {
                SessionManager.getInstance().login(Registries.getUnitRegistry(true).getUserUnitIdByUserName("admin"), "admin");
            } catch (CouldNotPerformException | InterruptedException ex) {
                logger.error("Could not login bco user", ex);
            }
        }

        if (command instanceof RefreshType) {
            //type is not supported
            return;
        }

        try {
            final ServiceType serviceType = getServiceType(channelUID);
            final ServiceStateCommandTransformer<Message, Command> transformer;
            try {
                transformer = ServiceStateCommandTransformerPool.getInstance().getTransformer(serviceType, command.getClass());
            } catch (CouldNotPerformException e) {
                logger.error("Transformer from service {} to command {} is not available", serviceType.name(), command.getClass().getSimpleName());
                return;
            }

            try {
                Services.invokeOperationServiceMethod(serviceType, unitRemote, transformer.transform(command));
            } catch (CouldNotPerformException ex) {
                logger.warn("Could not update channel {} to value {}", channelUID, command, ex);
                try {
                    updateState(channelUID.getId(), (State) transformer.transform(Services.invokeProviderServiceMethod(serviceType, unitRemote)));
                } catch (CouldNotPerformException exx) {
                    logger.warn("Could not reset channel", exx);
                } catch (ClassCastException exxx) {
                    // command is not a state, is the case e.g. for StopMoveType, just ignore these values
                }
            }
        } catch (IllegalArgumentException ex) {
            try {
                if (channelUID.getId().equalsIgnoreCase(BCOBindingConstants.CHANNEL_POWER_LIGHT)) {
                    PowerStateOnOffTypeTransformer transformer = ServiceStateCommandTransformerPool.getInstance().getTransformer(PowerStateOnOffTypeTransformer.class);
                    ((LocationRemote) unitRemote).setPowerState(transformer.transform((OnOffType) command), UnitType.LIGHT);
                } else {
                    logger.error("Receive command for unknown channel {}", channelUID.getId());
                }
            } catch (CouldNotPerformException exx) {
                logger.warn("Could not update channel {} to value {}", channelUID, command, ex);
            }
        }
    }

    @Override
    public void initialize() {
        try {
            unitRemote = Units.getUnit(getThing().getUID().getId(), false);

            unitRemote.addConnectionStateObserver(connectionStateObserver);
            unitRemote.addConfigObserver(unitConfigObserver);
            unitRemote.addDataObserver(unitDataObserver);

            // perform initial update
            updateThingConfig();
            if (unitRemote.isDataAvailable()) {
                updateChannels();
            }
        } catch (CouldNotPerformException | InterruptedException ex) {
            logger.error("Could not initialize thing for unit remote", ex);
        }
    }

    private void updateChannels() throws CouldNotPerformException {
        for (final ServiceType serviceType : unitRemote.getAvailableServiceTypes()) {
            final Message serviceState = unitRemote.getServiceState(serviceType);
            Set<Class<Command>> commandClasses;
            try {
                commandClasses = ServiceTypeCommandMapping.getCommandClasses(serviceType);
            } catch (NotAvailableException ex) {
                logger.warn("Skip applying channel update for service {} because no command classes are available", serviceType.name());
                continue;
            }

            for (final Class<Command> commandClass : commandClasses) {
                try {
                    final ServiceStateCommandTransformer transformer = ServiceStateCommandTransformerPool.getInstance().getTransformer(serviceState.getClass(), commandClass);
                    try {
                        final State state = (State) transformer.transform(serviceState);
                        updateState(getChannelId(serviceType), state);
                    } catch (ClassCastException ex) {
                        // command is not a state, is the case e.g. for StopMoveType, just ignore these values
                    }
                } catch (TypeNotSupportedException | CouldNotTransformException ex) {
                    // skip transformation
                    logger.warn("Skip transformation of {} to command {}", serviceState, commandClass.getSimpleName());
                }
            }
        }

        if (unitRemote instanceof LocationRemote && ((LocationRemote) unitRemote).getSupportedServiceTypes().contains(ServiceType.POWER_STATE_SERVICE)) {
            PowerStateOnOffTypeTransformer transformer = ServiceStateCommandTransformerPool.getInstance().getTransformer(PowerStateOnOffTypeTransformer.class);
            updateState(BCOBindingConstants.CHANNEL_POWER_LIGHT, transformer.transform(((LocationRemote) unitRemote).getPowerState(UnitType.LIGHT)));
        }
    }

    private void updateThingConfig() throws CouldNotPerformException {
        final ThingBuilder thingBuilder = editThing();
        final UnitConfig unitConfig = unitRemote.getConfig();

        // update thing label
        thingBuilder.withLabel(unitRemote.getLabel());
        // update thing location
        UnitConfig location = Registries.getUnitRegistry().getUnitConfigById(unitConfig.getPlacementConfig().getLocationId());
        thingBuilder.withLocation(LabelProcessor.getBestMatch(location.getLabel()));

        // update available channels
        // clear channels
        thingBuilder.withChannels();
        // add all channels
        for (ServiceType serviceType : unitRemote.getAvailableServiceTypes()) {
            ChannelUID channelUID = new ChannelUID(getThing().getUID(), getChannelId(serviceType));
            try {
                Channel channel = ChannelBuilder.create(channelUID, OpenHABItemProcessor.getItemType(serviceType)).build();
                thingBuilder.withChannel(channel);
            } catch (NotAvailableException ex) {
                logger.warn("Skip service {} of unit {} because item type not available", serviceType.name(), unitConfig.getAlias(0));
            }
        }

        if (unitRemote instanceof LocationRemote) {
            if (((LocationRemote) unitRemote).getSupportedServiceTypes().contains(ServiceType.POWER_STATE_SERVICE)) {
                ChannelUID channelUID = new ChannelUID(getThing().getUID(), getChannelId(ServiceType.POWER_STATE_SERVICE) + "_light");
                try {
                    Channel channel = ChannelBuilder.create(channelUID, OpenHABItemProcessor.getItemType(ServiceType.POWER_STATE_SERVICE)).build();
                    thingBuilder.withChannel(channel);
                } catch (NotAvailableException ex) {
                    // this should not happen
                }
            }
        }

        updateThing(thingBuilder.build());
    }

    @Override
    public void dispose() {
        unitRemote.removeConnectionStateObserver(connectionStateObserver);
        unitRemote.removeConfigObserver(unitConfigObserver);
        unitRemote.removeDataObserver(unitDataObserver);

        updateStatus(ThingStatus.OFFLINE);
    }

    private ServiceType getServiceType(final ChannelUID channelUID) {
        return ServiceType.valueOf(channelUID.getId().toUpperCase() + "_SERVICE");
    }

    private String getChannelId(final ServiceType serviceType) {
        return serviceType.name().replace("_SERVICE", "").toLowerCase();
    }
}
