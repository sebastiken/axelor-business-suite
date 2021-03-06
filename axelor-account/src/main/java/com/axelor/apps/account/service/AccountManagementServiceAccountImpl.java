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
package com.axelor.apps.account.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.axelor.apps.account.db.Account;
import com.axelor.apps.account.db.AccountManagement;
import com.axelor.apps.base.db.Company;
import com.axelor.apps.base.db.Product;
import com.axelor.apps.account.exception.IExceptionMessage;
import com.axelor.apps.base.service.tax.AccountManagementServiceImpl;
import com.axelor.exception.AxelorException;
import com.axelor.exception.db.IException;
import com.axelor.i18n.I18n;

public class AccountManagementServiceAccountImpl extends AccountManagementServiceImpl {
	
	private final Logger log = LoggerFactory.getLogger( getClass() );


	/**
	 * Obtenir le compte comptable d'un produit.
	 * 
	 * @param product
	 * @param company
	 * @param isPurchase
	 * @return
	 * @throws AxelorException 
	 */
	public Account getProductAccount(Product product, Company company, boolean isPurchase) throws AxelorException{
		
		log.debug("Obtention du compte comptable pour le produit {} (société : {}, achat ? {})",
			new Object[]{product, company, isPurchase});
		
		return this.getProductAccount(
				this.getAccountManagement(product, company), 
				isPurchase);
			
	}
	
	
	/**
	 * Obtenir le compte comptable d'un produit.
	 * 
	 * @param product
	 * @param company
	 * @param isPurchase
	 * @return
	 */
	public Account getProductAccount(AccountManagement accountManagement, boolean isPurchase){
		
		if(isPurchase)  { return accountManagement.getPurchaseAccount(); }
		else { return accountManagement.getSaleAccount(); }
			
	}
	
	
	@Override
	public void generateAccountManagementException(Product product, Company company) throws AxelorException  {
		
		throw new AxelorException(String.format(I18n.get(IExceptionMessage.ACCOUNT_MANAGEMENT_1_ACCOUNT), product.getCode(), company.getName()), IException.CONFIGURATION_ERROR);
	
	}
	
	
	
	
}
