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
import org.eclipse.smarthome.core.thing.ThingTypeUID;

/**
 * The {@link BCOBindingConstants} class defines common constants, which are
 * used across the whole binding.
 *
 * @author Tamino Huxohl - Initial contribution
 */
@NonNullByDefault
public class BCOBindingConstants {

    private static final String BINDING_ID = "bco";

    // List of all Thing Type UIDs
    public static final ThingTypeUID THING_TYPE_LOCATION = new ThingTypeUID(BINDING_ID, "location");

    // List of all Channel ids
    public static final String CHANNEL_POWER = "power_state";
    public static final String CHANNEL_POWER_LIGHT = "power_state_light";
    public static final String CHANNEL_STANDBY = "standby_state";
    public static final String CHANNEL_BRIGHTNESS = "brightness_state";
    public static final String CHANNEL_COLOR = "color_state";
    public static final String CHANNEL_TARGET_TEMPERATURE = "target_temperature_state";
    public static final String CHANNEL_POWER_CONSUMPTION = "power_consumption_state";
    public static final String CHANNEL_MOTION = "motion_state";
    public static final String CHANNEL_ILLUMINATION = "illumination_state";
    public static final String CHANNEL_PRESENCE = "presence_state";
    public static final String CHANNEL_TEMPERATURE = "temperature_state";
}
