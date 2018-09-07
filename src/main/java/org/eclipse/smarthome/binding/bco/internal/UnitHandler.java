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
import org.openbase.bco.app.openhab.manager.transform.PowerStateOnOffTypeTransformer;
import org.openbase.bco.app.openhab.manager.transform.ServiceStateCommandTransformer;
import org.openbase.bco.app.openhab.manager.transform.ServiceStateCommandTransformerPool;
import org.openbase.bco.app.openhab.manager.transform.ServiceTypeCommandMapping;
import org.openbase.bco.app.openhab.registry.synchronizer.OpenHABItemProcessor;
import org.openbase.bco.authentication.lib.SessionManager;
import org.openbase.bco.dal.lib.layer.service.Services;
import org.openbase.bco.dal.lib.layer.unit.UnitRemote;
import org.openbase.bco.dal.remote.unit.Units;
import org.openbase.bco.dal.remote.unit.location.LocationRemote;
import org.openbase.bco.dal.remote.unit.unitgroup.UnitGroupRemote;
import org.openbase.bco.registry.login.SystemLogin;
import org.openbase.jul.exception.CouldNotPerformException;
import org.openbase.jul.exception.CouldNotTransformException;
import org.openbase.jul.pattern.Observer;
import org.openbase.jul.pattern.Remote.ConnectionState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rst.domotic.service.ServiceConfigType.ServiceConfig;
import rst.domotic.service.ServiceDescriptionType.ServiceDescription;
import rst.domotic.service.ServiceTemplateType.ServiceTemplate.ServiceType;
import rst.domotic.unit.UnitConfigType.UnitConfig;
import rst.domotic.unit.UnitTemplateType.UnitTemplate.UnitType;
import rst.domotic.unit.location.LocationDataType.LocationData;

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

    private final Observer<ConnectionState> connectionStateObserver;
    private final Observer unitDataObserver;

    private UnitRemote unitRemote;

    public UnitHandler(Thing thing) {
        super(thing);
        logger.info("Create unit handler for thing {}", thing.getUID().toString());
        connectionStateObserver = (observable, connectionState) -> {
            logger.info("Unit {} switched to connection state {}", unitRemote.getLabel(), connectionState.name());
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
            final Set<ServiceType> serviceTypeSet = new HashSet<>();
            for (final ServiceDescription serviceDescription : unitRemote.getUnitTemplate().getServiceDescriptionList()) {
                final ServiceType serviceType = serviceDescription.getServiceType();
                if (serviceTypeSet.contains(serviceType)) {
                    continue;
                }
                serviceTypeSet.add(serviceType);

                final Message serviceState = unitRemote.getServiceState(serviceType);
                for (final Class<Command> commandClass : ServiceTypeCommandMapping.getCommandClasses(serviceType)) {
                    try {
                        final ServiceStateCommandTransformer transformer = ServiceStateCommandTransformerPool.getInstance().getTransformer(serviceState.getClass(), commandClass);
                        try {
                            final State state = (State) transformer.transform(serviceState);
                            updateState(getChannelId(serviceType), state);
                        } catch (ClassCastException ex) {
                            // command is not a state, is the case e.g. for StopMoveType, just ignore these values
                        }
                    } catch (TypeNotPresentException | CouldNotTransformException ex) {
                        // skip transformation
                        logger.info("Skip transformation of {} to command {}", serviceState, commandClass.getSimpleName());
                    }
                }
            }

            if (o instanceof LocationData) {
                PowerStateOnOffTypeTransformer transformer = ServiceStateCommandTransformerPool.getInstance().getTransformer(PowerStateOnOffTypeTransformer.class);
                updateState(BCOBindingConstants.CHANNEL_POWER_LIGHT, transformer.transform(((LocationRemote) unitRemote).getPowerState(UnitType.LIGHT)));
            }
        };
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        logger.info("Receive command {} for channel {} of unit {}", command.getClass().getSimpleName(), channelUID.getId(), getThing().getUID().getId());
        //TODO: login, when yes which user?
        if (!SessionManager.getInstance().isLoggedIn()) {
            try {
                SystemLogin.loginBCOUser();
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
//        config = getConfigAs(BCOConfiguration.class);

        try {
            unitRemote = Units.getUnit(getThing().getUID().getId(), false);

            updateAvailableChannels();

            unitRemote.addConnectionStateObserver(connectionStateObserver);
            unitRemote.addDataObserver(unitDataObserver);
        } catch (CouldNotPerformException | InterruptedException ex) {
            logger.error("Could not initialize thing for unit remote", ex);
        }
    }

    private void updateAvailableChannels() throws CouldNotPerformException {
        try {
            ThingBuilder thingBuilder = editThing();
            // clear channels
            thingBuilder.withChannels();

            // add all channels
            UnitConfig config = (UnitConfig) unitRemote.getConfig();
            Set<ServiceType> serviceTypes = new HashSet<>();
            switch (config.getUnitType()) {
                case UNIT_GROUP:
                    serviceTypes = ((UnitGroupRemote) unitRemote).getSupportedServiceTypes();
                    break;
                case LOCATION:
                    serviceTypes = ((LocationRemote) unitRemote).getSupportedServiceTypes();
                    break;
                default:
                    for (ServiceConfig serviceConfig : config.getServiceConfigList()) {
                        serviceTypes.add(serviceConfig.getServiceDescription().getServiceType());
                    }
                    break;
            }

            for (ServiceType serviceType : serviceTypes) {
                ChannelUID channelUID = new ChannelUID(getThing().getUID(), getChannelId(serviceType));
                Channel channel = ChannelBuilder.create(channelUID, OpenHABItemProcessor.getItemType(serviceType)).build();
                thingBuilder.withChannel(channel);
            }

            updateThing(thingBuilder.build());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new CouldNotPerformException("Could not update available channels");
        }
    }

    @Override
    public void dispose() {
        unitRemote.removeConnectionStateObserver(connectionStateObserver);
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
