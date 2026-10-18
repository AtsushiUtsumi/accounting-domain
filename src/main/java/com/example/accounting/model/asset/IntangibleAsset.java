package com.example.accounting.model.asset;

import com.example.accounting.exception.AccountingException;
import com.example.accounting.model.account.Account;
import com.example.accounting.model.journal.Journal;
import com.example.accounting.model.journal.JournalId;
import com.example.accounting.model.journal.JournalLine;
import com.example.accounting.model.journal.JournalType;
import com.example.accounting.model.money.Money;
import com.example.accounting.model.period.AccountingPeriod;
import com.example.accounting.model.period.MonthCount;
import com.example.accounting.model.statement.Ledger;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * 無形固定資産(ソフトウェア・のれんなど)。残存価額ゼロの定額法で、直接法(資産の勘定から直接減らす)で償却する。
 * 帳簿価額は保存せず、資産の勘定の残高から導出する。
 *
 * <p>そのため {@code assetAccount} は、この資産専用の勘定(他の資産と共有しない)を指定すること。
 * ID は有形固定資産と同じ {@link FixedAssetId} を使う。
 */
public record IntangibleAsset(
        FixedAssetId id,
        String name,
        Account assetAccount,
        Account amortizationExpenseAccount,
        Money acquisitionCost,
        LocalDate acquisitionDate,
        int usefulLifeYears
) {

    public IntangibleAsset {
        if (id == null) {
            throw new AccountingException("id is required");
        }
        if (name == null || name.isBlank()) {
            throw new AccountingException("name is required");
        }
        if (assetAccount == null) {
            throw new AccountingException("assetAccount is required");
        }
        if (amortizationExpenseAccount == null) {
            throw new AccountingException("amortizationExpenseAccount is required");
        }
        if (acquisitionCost == null || !acquisitionCost.isPositive()) {
            throw new AccountingException("acquisitionCost must be positive");
        }
        if (acquisitionDate == null) {
            throw new AccountingException("acquisitionDate is required");
        }
        if (usefulLifeYears <= 0) {
            throw new AccountingException("usefulLifeYears must be positive");
        }
    }

    /** 帳簿価額。資産の勘定の残高(取得の仕訳を含むこと)。 */
    public Money bookValue(List<Journal> journalsSoFar) {
        return Ledger.of(assetAccount, journalsSoFar).balance();
    }

    /**
     * 当期の償却仕訳(直接法)。取得原価 × 当期の月数 ÷(耐用年数 × 12)(円未満切り捨て)を償却する。
     * 耐用年数の最後の期は、残っている帳簿価額をすべて償却する。償却済みなら空を返す。
     */
    public Optional<Journal> amortizationEntry(List<Journal> journalsSoFar, AccountingPeriod period) {
        if (period == null) {
            throw new AccountingException("period is required");
        }
        Money remaining = bookValue(journalsSoFar);
        if (!remaining.isPositive()) {
            return Optional.empty();
        }
        LocalDate endOfLife = acquisitionDate.plusYears(usefulLifeYears).minusDays(1);
        int months = MonthCount.withinPeriod(period, acquisitionDate, endOfLife);
        if (months == 0 && period.endDate().isBefore(acquisitionDate)) {
            return Optional.empty();
        }

        Money amortization;
        if (!period.endDate().isBefore(endOfLife)) {
            amortization = remaining;
        } else {
            long totalMonths = usefulLifeYears * 12L;
            amortization = Money.ofYen(Math.floorDiv(acquisitionCost.amountInYen() * months, totalMonths));
            if (amortization.compareTo(remaining) > 0) {
                amortization = remaining;
            }
        }
        if (!amortization.isPositive()) {
            return Optional.empty();
        }

        return Optional.of(Journal.of(
                JournalId.newId(), period.endDate(), name + "の償却(直接法)", JournalType.ADJUSTING,
                List.of(
                        JournalLine.debit(amortizationExpenseAccount, amortization),
                        JournalLine.credit(assetAccount, amortization))));
    }
}
