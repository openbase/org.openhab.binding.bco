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

import org.eclipse.smarthome.config.discovery.AbstractDiscoveryService;
import org.eclipse.smarthome.config.discovery.DiscoveryResult;
import org.eclipse.smarthome.config.discovery.DiscoveryResultBuilder;
import org.eclipse.smarthome.config.discovery.DiscoveryService;
import org.eclipse.smarthome.core.thing.ThingTypeUID;
import org.eclipse.smarthome.core.thing.ThingUID;
import org.openbase.bco.registry.remote.Registries;
import org.openbase.jul.exception.CouldNotPerformException;
import org.openbase.jul.exception.NotAvailableException;
import org.openbase.jul.extension.protobuf.ProtobufListDiff;
import org.openbase.jul.extension.rst.processing.LabelProcessor;
import org.openbase.jul.pattern.Observer;
import org.openbase.jul.pattern.provider.DataProvider;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rst.domotic.registry.UnitRegistryDataType.UnitRegistryData;
import rst.domotic.unit.UnitConfigType.UnitConfig;
import rst.domotic.unit.UnitTemplateType.UnitTemplate.UnitType;
import rst.domotic.unit.device.DeviceClassType.DeviceClass;

import java.util.ArrayList;
import java.util.List;

/**
 * @author <a href="mailto:pleminoq@openbase.org">Tamino Huxohl</a>
 */
@Component(service = DiscoveryService.class, immediate = true, configurationPid = "discovery.bco")
public class BCODiscoveryService extends AbstractDiscoveryService {

    private static final int TIMEOUT = 30;

    private final Logger logger = LoggerFactory.getLogger(BCODiscoveryService.class);
    private final Observer<DataProvider<UnitRegistryData>, UnitRegistryData> unitRegistryObserver;
    private final ProtobufListDiff<String, UnitConfig, UnitConfig.Builder> diff;

    public BCODiscoveryService() throws IllegalArgumentException {
        super(BCOBindingConstants.THING_TYPES, TIMEOUT);

        diff = new ProtobufListDiff<>();
        unitRegistryObserver = (observable, unitRegistryData) -> {
            try {
                diff.diff(getHandledUnitConfigList());

                // add new units to discovery
                for (final UnitConfig unitConfig : diff.getNewMessageMap().getMessages()) {
                    thingDiscovered(getDiscoveryResult(unitConfig));
                }

                // remove units from discovery
                for (final UnitConfig unitConfig : diff.getRemovedMessageMap().getMessages()) {
                    thingRemoved(getThingUID(unitConfig));
                }
            } catch (CouldNotPerformException ex) {
                logger.error("Could not discover things", ex);
            }
        };
    }

    @Override
    protected void startScan() {
        try {
            for (final UnitConfig unitConfig : getHandledUnitConfigList()) {
                thingDiscovered(getDiscoveryResult(unitConfig));
            }
        } catch (CouldNotPerformException ex) {
            logger.error("Could not scan for BCO things", ex);
        }
    }

    private List<UnitConfig> getHandledUnitConfigList() throws CouldNotPerformException {
        try {
            Registries.getUnitRegistry().waitForData();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new CouldNotPerformException("Interrupted", ex);
        }
        final List<UnitConfig> handledUnitConfigs = new ArrayList<>();
        for (UnitConfig unitConfig : Registries.getUnitRegistry().getUnitConfigs()) {
            // ignore all units without services
            if (unitConfig.getServiceConfigCount() == 0) {
                continue;
            }

            // ignore system users
            if (unitConfig.getUnitType() == UnitType.USER && unitConfig.getUserConfig().getSystemUser()) {
                continue;
            }

            // ignore all units from devices handled by the openhab app
            if (!unitConfig.getUnitHostId().isEmpty()) {
                UnitConfig unitHost = Registries.getUnitRegistry().getUnitConfigById(unitConfig.getUnitHostId());
                if (unitHost.getUnitType() == UnitType.DEVICE) {
                    if (!Registries.getClassRegistry().isDataAvailable()) {
                        try {
                            Registries.getClassRegistry().waitForData();
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            throw new CouldNotPerformException("Could not wait for class registry data");
                        }
                    }
                    DeviceClass deviceClass = Registries.getClassRegistry().getDeviceClassById(unitHost.getDeviceConfig().getDeviceClassId());
                    if (deviceClass.getBindingConfig().getBindingId().equalsIgnoreCase("openhab")) {
                        continue;
                    }
                }
            }

            handledUnitConfigs.add(unitConfig);
        }

        return handledUnitConfigs;
    }

    //TODO: re-activate if re-init works when changing host and port
    @Override
    protected void startBackgroundDiscovery() {


//        logger.info("Start background discovery");
//        try {
//            Registries.getUnitRegistry().addDataObserver(unitRegistryObserver);
//        } catch (NotAvailableException ex) {
//            logger.warn("Could not start background discovery", ex);
//        }
    }

    @Override
    protected void stopBackgroundDiscovery() {
//        logger.info("Stop background discovery");
//        try {
//            Registries.getUnitRegistry().removeDataObserver(unitRegistryObserver);
//        } catch (NotAvailableException ex) {
//            logger.warn("Could not stop background discovery", ex);
//        }
    }

    private ThingUID getThingUID(final UnitConfig unitConfig) {
        return new ThingUID(new ThingTypeUID(BCOBindingConstants.BINDING_ID, BCOBindingConstants.UNIT_THING_TYPE), unitConfig.getId());
    }

    private DiscoveryResult getDiscoveryResult(final UnitConfig unitConfig) throws NotAvailableException {
        final ThingUID thingUID = getThingUID(unitConfig);
        final String label = LabelProcessor.getBestMatch(unitConfig.getLabel());
        return DiscoveryResultBuilder.create(thingUID).withLabel(label).build();
    }
}
