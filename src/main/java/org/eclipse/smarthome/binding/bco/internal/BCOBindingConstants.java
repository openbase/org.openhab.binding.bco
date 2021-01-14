package org.eclipse.smarthome.binding.bco.internal;

/*-
 * #%L
 * BCO Binding
 * %%
 * Copyright (C) 2018 - 2021 openbase.org
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
import org.eclipse.smarthome.core.thing.ThingTypeUID;

import java.util.HashSet;
import java.util.Set;

/**
 * The {@link BCOBindingConstants} class defines common constants, which are
 * used across the whole binding.
 *
 * @author Tamino Huxohl - Initial contribution
 */
@NonNullByDefault
public class BCOBindingConstants {

    public static final String BINDING_ID = "bco";
    public static final String UNIT_THING_TYPE = "unit";

    // Custom Channels
    public static final String CHANNEL_POWER_LIGHT = "power_state_light";

    static Set<ThingTypeUID> THING_TYPES = new HashSet<>();

    static {
        THING_TYPES.add(new ThingTypeUID(BINDING_ID, UNIT_THING_TYPE));
    }
}
