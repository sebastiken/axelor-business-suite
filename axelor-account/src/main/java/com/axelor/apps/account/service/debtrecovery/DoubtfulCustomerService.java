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
package com.axelor.apps.account.service.debtrecovery;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import javax.persistence.Query;

import org.joda.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.axelor.apps.account.db.Account;
import com.axelor.apps.account.db.AccountConfig;
import com.axelor.apps.account.db.Invoice;
import com.axelor.apps.account.db.Move;
import com.axelor.apps.account.db.MoveLine;
import com.axelor.apps.account.db.Reconcile;
import com.axelor.apps.account.db.repo.MoveLineRepository;
import com.axelor.apps.account.db.repo.MoveRepository;
import com.axelor.apps.account.service.ReconcileService;
import com.axelor.apps.account.service.config.AccountConfigService;
import com.axelor.apps.account.service.move.MoveLineService;
import com.axelor.apps.account.service.move.MoveService;
import com.axelor.apps.base.db.Company;
import com.axelor.apps.base.db.Partner;
import com.axelor.apps.base.service.administration.GeneralService;
import com.axelor.db.JPA;
import com.axelor.exception.AxelorException;
import com.axelor.inject.Beans;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;

public class DoubtfulCustomerService {

	private final Logger log = LoggerFactory.getLogger( getClass() );

	protected MoveService moveService;
	protected MoveRepository moveRepo;
	protected MoveLineService moveLineService;
	protected MoveLineRepository moveLineRepo;
	protected ReconcileService reconcileService;
	protected AccountConfigService accountConfigService;
	protected LocalDate today;

	@Inject
	public DoubtfulCustomerService(MoveService moveService, MoveRepository moveRepo, MoveLineService moveLineService, MoveLineRepository moveLineRepo,
			ReconcileService reconcileService, AccountConfigService accountConfigService) {

		this.moveService = moveService;
		this.moveRepo = moveRepo;
		this.moveLineService = moveLineService;
		this.moveLineRepo = moveLineRepo;
		this.reconcileService = reconcileService;
		this.accountConfigService = accountConfigService;
		this.today = Beans.get(GeneralService.class).getTodayDate();
	}


	/**
	 * Procédure permettant de vérifier le remplissage des champs dans la société, nécessaire au traitement du passage en client douteux
	 * @param company
	 * 			Une société
	 * @throws AxelorException
	 */
	public void testCompanyField(Company company) throws AxelorException  {

		AccountConfig accountConfig = accountConfigService.getAccountConfig(company);

		accountConfigService.getDoubtfulCustomerAccount(accountConfig);
		accountConfigService.getMiscOperationJournal(accountConfig);
		accountConfigService.getSixMonthDebtPassReason(accountConfig);
		accountConfigService.getThreeMonthDebtPassReason(accountConfig);

	}


	/**
	 *
	 * Procédure permettant de créer les écritures de passage en client douteux pour chaque écriture de facture
	 * @param moveLineList
	 * 			Une liste d'écritures de facture
	 * @param doubtfulCustomerAccount
	 * 			Un compte client douteux
	 * @param debtPassReason
	 * 			Un motif de passage en client douteux
	 * @throws AxelorException
	 */
	public void createDoubtFulCustomerMove(List<Move> moveList, Account doubtfulCustomerAccount, String debtPassReason) throws AxelorException  {

		for(Move move : moveList)  {

			this.createDoubtFulCustomerMove(move, doubtfulCustomerAccount, debtPassReason);

		}
	}


	/**
	 *
	 * Procédure permettant de créer les écritures de passage en client douteux pour chaque écriture de facture
	 * @param moveLineList
	 * 			Une liste d'écritures de facture
	 * @param doubtfulCustomerAccount
	 * 			Un compte client douteux
	 * @param debtPassReason
	 * 			Un motif de passage en client douteux
	 * @throws AxelorException
	 */
	@Transactional(rollbackOn = {AxelorException.class, Exception.class})
	public void createDoubtFulCustomerMove(Move move, Account doubtfulCustomerAccount, String debtPassReason) throws AxelorException  {

		log.debug("Ecriture concernée : {} ",move.getReference());

		BigDecimal totalAmountRemaining = BigDecimal.ZERO;
		Company company = move.getCompany();
		Partner partner = move.getPartner();
		Move newMove = moveService.getMoveCreateService().createMove(company.getAccountConfig().getMiscOperationJournal(), company, move.getInvoice(), partner, move.getPaymentMode());

		int ref = 1;
		List<Reconcile> reconcileList = new ArrayList<Reconcile>();
		List<MoveLine> moveLineList = move.getMoveLineList();
		for(MoveLine moveLine : moveLineList)  {
			if(moveLine.getAccount().getReconcileOk()
					&& moveLine.getAmountRemaining().compareTo(BigDecimal.ZERO) > 0
					&& moveLine.getAccount() != doubtfulCustomerAccount
					&& moveLine.getDebit().compareTo(BigDecimal.ZERO) > 0)  {

					BigDecimal amountRemaining = moveLine.getAmountRemaining();
					// Ecriture au crédit sur le 411
					MoveLine creditMoveLine = moveLineService.createMoveLine(newMove , moveLine.getPartner(), moveLine.getAccount(), amountRemaining, false, today, ref, null);
					newMove.getMoveLineList().add(creditMoveLine);

					Reconcile reconcile = reconcileService.createReconcile(moveLine, creditMoveLine, amountRemaining, false);
					reconcileList.add(reconcile);

					totalAmountRemaining = totalAmountRemaining.add(amountRemaining);

					ref++;
			}
		}

		// Ecriture au débit sur le 416 (client douteux)
		MoveLine debitMoveLine = moveLineService.createMoveLine(newMove , newMove.getPartner(), doubtfulCustomerAccount, totalAmountRemaining, true, today, ref, null);
		newMove.getMoveLineList().add(debitMoveLine);

		debitMoveLine.setPassageReason(debtPassReason);

		moveService.getMoveValidateService().validateMove(newMove);
		moveRepo.save(newMove);

		for(Reconcile reconcile : reconcileList)  {
			reconcileService.confirmReconcile(reconcile);
		}

		this.invoiceProcess(newMove, doubtfulCustomerAccount, debtPassReason);

	}



	public void createDoubtFulCustomerRejectMove(List<MoveLine> moveLineList, Account doubtfulCustomerAccount, String debtPassReason) throws AxelorException  {

		for(MoveLine moveLine : moveLineList)  {

			this.createDoubtFulCustomerRejectMove(moveLine, doubtfulCustomerAccount, debtPassReason);

		}
	}


	/**
	 * Procédure permettant de créer les écritures de passage en client douteux pour chaque ligne d'écriture de rejet de facture
	 * @param moveLineList
	 * 			Une liste de lignes d'écritures de rejet de facture
	 * @param doubtfulCustomerAccount
	 * 			Un compte client douteux
	 * @param debtPassReason
	 * 			Un motif de passage en client douteux
	 * @throws AxelorException
	 */
	@Transactional(rollbackOn = {AxelorException.class, Exception.class})
	public void createDoubtFulCustomerRejectMove(MoveLine moveLine, Account doubtfulCustomerAccount, String debtPassReason) throws AxelorException  {

		log.debug("Ecriture concernée : {} ",moveLine.getName());
		Company company = moveLine.getMove().getCompany();
		Partner partner = moveLine.getPartner();

		Move newMove = moveService.getMoveCreateService().createMove(company.getAccountConfig().getMiscOperationJournal(), company, null, partner, moveLine.getMove().getPaymentMode());

		List<Reconcile> reconcileList = new ArrayList<Reconcile>();

		BigDecimal amountRemaining = moveLine.getAmountRemaining();

		// Ecriture au crédit sur le 411
		MoveLine creditMoveLine = moveLineService.createMoveLine(newMove , partner, moveLine.getAccount(), amountRemaining, false, today, 1, null);
		newMove.addMoveLineListItem(creditMoveLine);

		Reconcile reconcile = reconcileService.createReconcile(moveLine, creditMoveLine, amountRemaining, false);
		reconcileList.add(reconcile);
		reconcileService.confirmReconcile(reconcile);

		// Ecriture au débit sur le 416 (client douteux)
		MoveLine debitMoveLine = moveLineService.createMoveLine(newMove , newMove.getPartner(), doubtfulCustomerAccount, amountRemaining, true, today, 2, null);
		newMove.getMoveLineList().add(debitMoveLine);

		debitMoveLine.setInvoiceReject(moveLine.getInvoiceReject());
		debitMoveLine.setPassageReason(debtPassReason);

		moveService.getMoveValidateService().validateMove(newMove);
		moveRepo.save(newMove);

		this.invoiceRejectProcess(debitMoveLine, doubtfulCustomerAccount, debtPassReason);

	}



	/**
	 * Procédure permettant de mettre à jour le motif de passage en client douteux, et créer l'évènement lié.
	 * @param moveList
	 * 			Une liste d'éciture de facture
	 * @param doubtfulCustomerAccount
	 * 			Un compte client douteux
	 * @param debtPassReason
	 * 			Un motif de passage en client douteux
	 */
	public void updateDoubtfulCustomerMove(List<Move> moveList, Account doubtfulCustomerAccount, String debtPassReason)  {

		for(Move move : moveList)  {

			for(MoveLine moveLine : move.getMoveLineList())  {

				if(moveLine.getAccount().equals(doubtfulCustomerAccount) && moveLine.getDebit().compareTo(BigDecimal.ZERO) > 0)  {

					moveLine.setPassageReason(debtPassReason);
					moveLineRepo.save(moveLine);

					break;
				}
			}
		}
	}


	/**
	 * Procédure permettant de mettre à jour les champs de la facture avec la nouvelle écriture de débit sur le compte 416
	 * @param move
	 * 			La nouvelle écriture de débit sur le compte 416
	 * @param doubtfulCustomerAccount
	 * 			Un compte client douteux
	 * @param debtPassReason
	 * 			Un motif de passage en client douteux
	 * @throws AxelorException
	 */
	public Invoice invoiceProcess(Move move, Account doubtfulCustomerAccount, String debtPassReason) throws AxelorException  {

		Invoice invoice = move.getInvoice();

		if(invoice != null)  {

			invoice.setOldMove(invoice.getMove());
			invoice.setMove(move);
			invoice.setPartnerAccount(doubtfulCustomerAccount);
			invoice.setDoubtfulCustomerOk(true);
			// Recalcule du restant à payer de la facture
			invoice.setCompanyInTaxTotalRemaining(moveService.getMoveToolService().getInTaxTotalRemaining(invoice));
		}
		return invoice;
	}


	/**
	 * Procédure permettant de mettre à jour les champs d'une facture rejetée avec la nouvelle écriture de débit sur le compte 416
	 * @param move
	 * 			La nouvelle ligne d'écriture de débit sur le compte 416
	 * @param doubtfulCustomerAccount
	 * 			Un compte client douteux
	 * @param debtPassReason
	 * 			Un motif de passage en client douteux
	 */
	public Invoice invoiceRejectProcess(MoveLine moveLine, Account doubtfulCustomerAccount, String debtPassReason)  {

		Invoice invoice = moveLine.getInvoiceReject();

		invoice.setRejectMoveLine(moveLine);
//			invoice.setPartnerAccount(doubtfulCustomerAccount);
		invoice.setDoubtfulCustomerOk(true);

		return invoice;
	}


	/**
	 * Fonction permettant de récupérer les écritures de facture à transférer sur le compte client douteux
	 * @param rule
	 * 		Le règle à appliquer :
	 * 		<ul>
     *      <li>0 = Créance de + 6 mois</li>
     *      <li>1 = Créance de + 3 mois</li>
     *  	</ul>
	 * @param doubtfulCustomerAccount
	 * 		Le compte client douteux
	 * @param company
	 * 		La société
	 * @return
	 * 		Les écritures de facture à transférer sur le compte client douteux
	 */
	public List<Move> getMove(int rule, Account doubtfulCustomerAccount, Company company)  {

		LocalDate date = null;

		switch (rule) {

			//Créance de + 6 mois
			case 0 :
				date = this.today.minusMonths(company.getAccountConfig().getSixMonthDebtMonthNumber());
				break;

			//Créance de + 3 mois
			case 1 :
				date = this.today.minusMonths(company.getAccountConfig().getThreeMonthDebtMontsNumber());
				break;

			default:
				break;
		}

		log.debug("Date de créance prise en compte : {} ",date);

		String request = "SELECT DISTINCT m FROM MoveLine ml, Move m WHERE ml.move = m AND ml.company.id = "+ company.getId() +" AND ml.account.reconcileOk = 'true' " +
				"AND ml.invoice IS NOT NULL AND ml.amountRemaining > 0.00 AND ml.debit > 0.00 AND ml.dueDate < '"+ date.toString() +
				"' AND ml.account.id != "+doubtfulCustomerAccount.getId();

		log.debug("Requete : {} ",request);

		Query query = JPA.em().createQuery(request);

		@SuppressWarnings("unchecked")
		List<Move> moveList = query.getResultList();

		return moveList;
	}

	/**
	 * Fonction permettant de récupérer les lignes d'écriture de rejet de facture à transférer sur le compte client douteux
	 * @param rule
	 * 		Le règle à appliquer :
	 * 		<ul>
     *      <li>0 = Créance de + 6 mois</li>
     *      <li>1 = Créance de + 3 mois</li>
     *  	</ul>
	 * @param doubtfulCustomerAccount
	 * 		Le compte client douteux
	 * @param company
	 * 		La société
	 * @return
	 * 		Les lignes d'écriture de rejet de facture à transférer sur le comtpe client douteux
	 */
	public List<? extends MoveLine> getRejectMoveLine(int rule, Account doubtfulCustomerAccount, Company company)  {

		LocalDate date = null;
		List<? extends MoveLine> moveLineList = null;

		switch (rule) {

			//Créance de + 6 mois
			case 0 :
				date = this.today.minusMonths(company.getAccountConfig().getSixMonthDebtMonthNumber());
				moveLineList = moveLineRepo.all().filter("self.company = ?1 AND self.account.reconcileOk = 'true' " +
						"AND self.invoiceReject IS NOT NULL AND self.amountRemaining > 0.00 AND self.debit > 0.00 AND self.dueDate < ?2 " +
						"AND self.account != ?3",company, date, doubtfulCustomerAccount).fetch();
				break;

			//Créance de + 3 mois
			case 1 :
				date = this.today.minusMonths(company.getAccountConfig().getThreeMonthDebtMontsNumber());
				moveLineList = moveLineRepo.all().filter("self.company = ?1 AND self.account.reconcileOk = 'true' " +
						"AND self.invoiceReject IS NOT NULL AND self.amountRemaining > 0.00 AND self.debit > 0.00 AND self.dueDate < ?2 " +
						"AND self.account != ?3",company, date, doubtfulCustomerAccount).fetch();
				break;

			default:
				break;
		}

		log.debug("Date de créance prise en compte : {} ",date);

		return moveLineList;
	}

}
