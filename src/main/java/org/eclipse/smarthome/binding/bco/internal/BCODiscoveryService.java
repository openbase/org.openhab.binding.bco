package org.eclipse.smarthome.binding.bco.internal;

/*-
 * #%L
 * BCO Binding
 * %%
 * Copyright (C) 2018 - 2020 openbase.org
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
import org.openbase.jul.extension.type.processing.LabelProcessor;
import org.openbase.jul.pattern.Observer;
import org.openbase.jul.pattern.provider.DataProvider;
import org.openbase.jul.schedule.GlobalCachedExecutorService;
import org.openbase.jul.schedule.SyncObject;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.openbase.type.domotic.registry.UnitRegistryDataType.UnitRegistryData;
import org.openbase.type.domotic.unit.UnitConfigType.UnitConfig;
import org.openbase.type.domotic.unit.UnitTemplateType.UnitTemplate.UnitType;
import org.openbase.type.domotic.unit.device.DeviceClassType.DeviceClass;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * @author <a href="mailto:pleminoq@openbase.org">Tamino Huxohl</a>
 */
@Component(service = DiscoveryService.class, immediate = true, configurationPid = "discovery.bco")
public class BCODiscoveryService extends AbstractDiscoveryService {

    // in seconds
    private static final int TIMEOUT = 30;

    // in milliseconds
    private static final long OPENHAB_PREPERATION_TIME = TimeUnit.SECONDS.toMillis(5);

    private final Logger logger = LoggerFactory.getLogger(BCODiscoveryService.class);
    private final Observer<DataProvider<UnitRegistryData>, UnitRegistryData> unitRegistryObserver;
    private final ProtobufListDiff<String, UnitConfig, UnitConfig.Builder> diff;

    private boolean initialDiscovery;

    // discovery task and lock
    private Future<Void> discoveryTask;
    private final SyncObject discoveryTaskLock = new SyncObject("DiscoveryTaskLock");

    public BCODiscoveryService() throws IllegalArgumentException {
        super(BCOBindingConstants.THING_TYPES, TIMEOUT);

        this.diff = new ProtobufListDiff<>();
        this.initialDiscovery = true;

        // prepare registry observation
        unitRegistryObserver = (observable, unitRegistryData) -> {
            triggerDiscovery();
        };
    }

    /**
     * Method adds all units to the openhab inbox and removes outdated ones.
     */
    private void triggerDiscovery() {

        synchronized (discoveryTaskLock) {
            if (discoveryTask != null && !discoveryTask.isDone()) {
                logger.info("Discovery still running, skip request...");
                return;
            }

            discoveryTask = GlobalCachedExecutorService.submit(() -> {
                try {

                    if (!Registries.isDataAvailable()) {
                        logger.info("Discovery will be started after the bco registry is available...");
                        Registries.waitForData();
                    }

                    // initial waiting required until openhab is ready and knows the thing types bco can handle.
                    if(initialDiscovery) {
                        logger.info("Waiting for openhab to prepare the binding registration...");
                        Thread.sleep(OPENHAB_PREPERATION_TIME);
                        this.initialDiscovery = false;
                    }

                    logger.info("Start discovery...");

                    diff.diffMessages(getHandledUnitConfigList());

                    // add new units to discovery
                    for (final UnitConfig unitConfig : diff.getNewMessageMap().getMessages()) {
                        thingDiscovered(getDiscoveryResult(unitConfig));
                    }

                    // remove units from discovery
                    for (final UnitConfig unitConfig : diff.getRemovedMessageMap().getMessages()) {
                        thingRemoved(getThingUID(unitConfig));
                    }

                    logger.info("Discovery successful.");
                } catch (CouldNotPerformException ex) {
                    logger.error("Could not discover things", ex);
                }
                return null;
            });
        }
    }

    /**
     * Discovery started by user.
     */
    @Override
    protected void startScan() {
        logger.info("Start scan for new unis...");
        triggerDiscovery();
    }

    @Override
    protected synchronized void stopScan() {
        synchronized (discoveryTaskLock) {
            discoveryTask.cancel(true);
            discoveryTask = null;
            super.stopScan();
        }
    }

    /**
     * Generates a list with all units that are handled by the bco binding.
     * @return the list of supported units.
     * @throws CouldNotPerformException is thrown in case the list could not be generated.
     * @throws InterruptedException is thrown if the thread was externally interrupted.
     */
    private List<UnitConfig> getHandledUnitConfigList() throws CouldNotPerformException, InterruptedException {

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

    @Override
    protected void startBackgroundDiscovery() {
        logger.info("Start background discovery");
        try {
            Registries.getUnitRegistry().addDataObserver(unitRegistryObserver);
        } catch (NotAvailableException ex) {
            logger.warn("Could not start background discovery", ex);
        }

        // initial discovery
        this.initialDiscovery = true;
        triggerDiscovery();
    }

    @Override
    protected void stopBackgroundDiscovery() {
        logger.info("Stop background discovery");
        try {
            Registries.getUnitRegistry().removeDataObserver(unitRegistryObserver);
        } catch (NotAvailableException ex) {
            logger.warn("Could not stop background discovery", ex);
        }
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
