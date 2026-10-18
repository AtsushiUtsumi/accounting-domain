package com.example.accounting.service;

import com.example.accounting.model.account.Account;
import com.example.accounting.model.journal.Journal;
import com.example.accounting.model.journal.JournalType;
import com.example.accounting.model.money.Money;

import java.time.LocalDate;
import java.util.List;

/**
 * 引当金の期中の取崩し(賞与の支給・修繕の実施・保証による修理・退職金の支払)。保存は行わない。
 * 決算での繰入・戻入は {@link AdjustingEntryService#adjustProvision} などで行う。
 */
public final class ProvisionService {

    /**
     * 支払額のうち、引当金の残高までを引当金で充当し、足りない分を費用({@link ProvisionType#expenseOnUseAccount()})
     * とする。
     *
     * @param amount         支払額
     * @param paymentAccount 支払う勘定(現金・当座預金など)
     */
    public Journal use(ProvisionType type, Money amount, Account paymentAccount,
                       List<Journal> journalsSoFar, LocalDate date) {
        return ProvisionCalculator.use(
                amount, type.provisionAccount(), type.expenseOnUseAccount(), paymentAccount,
                type.provisionAccount().name() + "の取崩し", journalsSoFar, date, JournalType.NORMAL);
    }
}
