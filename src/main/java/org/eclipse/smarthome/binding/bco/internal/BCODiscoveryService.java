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
import org.eclipse.smarthome.core.thing.ThingUID;
import org.openbase.bco.registry.remote.Registries;
import org.openbase.jul.exception.CouldNotPerformException;
import org.openbase.jul.exception.NotAvailableException;
import org.openbase.jul.extension.protobuf.ProtobufListDiff;
import org.openbase.jul.extension.rst.processing.LabelProcessor;
import org.openbase.jul.pattern.Observer;
import org.openbase.jul.processing.StringProcessor;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rst.domotic.registry.UnitRegistryDataType.UnitRegistryData;
import rst.domotic.unit.UnitConfigType.UnitConfig;
import rst.domotic.unit.UnitTemplateType.UnitTemplate.UnitType;

/**
 * @author <a href="mailto:pleminoq@openbase.org">Tamino Huxohl</a>
 */
@Component(service = DiscoveryService.class, immediate = true, configurationPid = "discovery.bco")
public class BCODiscoveryService extends AbstractDiscoveryService {

    private static final int TIMEOUT = 30;

    private final Logger logger = LoggerFactory.getLogger(BCODiscoveryService.class);
    private final Observer<UnitRegistryData> unitRegistryObserver;
    private final ProtobufListDiff<String, UnitConfig, UnitConfig.Builder> diff;

    public BCODiscoveryService() throws IllegalArgumentException {
        super(BCOHandlerFactory.SUPPORTED_THING_TYPES_UIDS, TIMEOUT);

        diff = new ProtobufListDiff<>();
        unitRegistryObserver = (observable, unitRegistryData) -> {
            try {
                logger.info("UnitRegistryObserver triggered");
                if(!Registries.getTemplateRegistry().isDataAvailable()) {
                    Registries.getTemplateRegistry().waitForData();
                }
                diff.diff(Registries.getUnitRegistry().getUnitConfigs(UnitType.LOCATION));

                // add new locations to discovery
                for (final UnitConfig unitConfig : diff.getNewMessageMap().getMessages()) {
                    logger.info("Add thing for unit: " + unitConfig.getAlias(0));
                    final ThingUID thingUID = getThingUID(unitConfig);
                    final String label = LabelProcessor.getBestMatch(unitConfig.getLabel());
                    final DiscoveryResult discoveryResult = DiscoveryResultBuilder.create(thingUID).withLabel(label).
                            withRepresentationProperty(StringProcessor.transformUpperCaseToCamelCase(unitConfig.getUnitType().name())).build();

                    thingDiscovered(discoveryResult);
                }

                // remove discovered removed locations
                for (final UnitConfig unitConfig : diff.getRemovedMessageMap().getMessages()) {
                    final ThingUID thingUID = getThingUID(unitConfig);

                    thingRemoved(thingUID);
                }
            } catch (CouldNotPerformException ex) {
                logger.error("Could not discover things", ex);
            }
        };
    }

    @Override
    protected void startScan() {
        try {
            for (final UnitConfig unitConfig : Registries.getUnitRegistry().getUnitConfigs(UnitType.LOCATION)) {
                final ThingUID thingUID = getThingUID(unitConfig);
                final String label = LabelProcessor.getBestMatch(unitConfig.getLabel());
                final DiscoveryResult discoveryResult = DiscoveryResultBuilder.create(thingUID).withLabel(label).build();

                thingDiscovered(discoveryResult);
            }
        } catch (CouldNotPerformException ex) {
            logger.error("Could not scan for BCO things", ex);
        }
    }

    @Override
    protected void startBackgroundDiscovery() {
        try {
            logger.info("Start background discovery" + Registries.getUnitRegistry().isDataAvailable());
            Registries.getUnitRegistry().addDataObserver(unitRegistryObserver);
        } catch (NotAvailableException ex) {
            logger.warn("Could not start background discovery", ex);
        }
    }

    @Override
    protected void stopBackgroundDiscovery() {
        try {
            Registries.getUnitRegistry().removeDataObserver(unitRegistryObserver);
        } catch (NotAvailableException ex) {
            logger.warn("Could not stop background discovery", ex);
        }
    }

    private ThingUID getThingUID(final UnitConfig unitConfig) {
        //TODO: should depend on the unit type
        return new ThingUID(BCOBindingConstants.THING_TYPE_LOCATION, unitConfig.getId());
    }
}
