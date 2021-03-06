/**
 * Axelor Business Solutions
 *
 * Copyright (C) 2016 Axelor (<http://axelor.com>).
 *
 * This program is free software: you can redistribute it and/or  modify
 * it under the terms of the GNU Affero General Public License, version 3,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.axelor.apps.base.service;

import java.io.IOException;
import java.util.Map;

import com.axelor.apps.base.db.Address;
import com.axelor.apps.base.db.Country;

public interface AddressService {
	
	
	public boolean check(String wsdlUrl);
	
	public Map<String,Object> validate(String wsdlUrl, String search);
	
	public com.qas.web_2005_02.Address select(String wsdlUrl, String moniker);
	
	public int export(String path) throws IOException;
	
	public Address createAddress(String addressL2, String addressL3, String addressL4, String addressL5, String addressL6, 	Country addressL7Country); 
		
	
	public Address getAddress(String addressL2, String addressL3, String addressL4, String addressL5, String addressL6, Country addressL7Country); 
	
	public boolean checkAddressUsed(Long addressId);
	
	public Address checkLatLang(Address address, boolean forceUpdate);
	
	public String computeFullName(Address address);
	
}
