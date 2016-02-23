/*
 * 3D City Database - The Open Source CityGML Database
 * http://www.3dcitydb.org/
 * 
 * (C) 2013 - 2016,
 * Chair of Geoinformatics,
 * Technische Universitaet Muenchen, Germany
 * http://www.gis.bgu.tum.de/
 * 
 * The 3D City Database is jointly developed with the following
 * cooperation partners:
 * 
 * virtualcitySYSTEMS GmbH, Berlin <http://www.virtualcitysystems.de/>
 * M.O.S.S. Computer Grafik Systeme GmbH, Muenchen <http://www.moss.de/>
 * 
 * The 3D City Database Importer/Exporter program is free software:
 * you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 */
package org.citydb.event;

import java.util.Locale;

import org.citydb.api.event.Event;
import org.citydb.api.event.global.GlobalEvents;
import org.citydb.api.event.global.SwitchLocaleEvent;

public class SwitchLocaleEventImpl extends Event implements SwitchLocaleEvent {
	private final Locale locale;
	
	public SwitchLocaleEventImpl(Locale locale, Object source) {
		super(GlobalEvents.SWITCH_LOCALE, GLOBAL_CHANNEL, source);
		this.locale = locale;
	}
	
	@Override
	public Locale getLocale() {
		return locale;
	}

}
