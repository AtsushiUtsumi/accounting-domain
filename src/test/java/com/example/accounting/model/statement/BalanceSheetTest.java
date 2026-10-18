package com.example.accounting.model.statement;

import com.example.accounting.model.account.StandardAccounts;
import com.example.accounting.model.journal.Journal;
import com.example.accounting.model.journal.JournalId;
import com.example.accounting.model.journal.JournalLine;
import com.example.accounting.model.journal.JournalType;
import com.example.accounting.model.money.Money;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BalanceSheetTest {

    @Test
    void of_classifiesAccountsIntoAssetsLiabilitiesAndEquity() {
        List<Journal> journals = List.of(
                Journal.of(
                        JournalId.newId(), LocalDate.of(2026, 4, 1), "出資による開業",
                        JournalType.NORMAL,
                        List.of(
                                JournalLine.debit(StandardAccounts.CASH, Money.ofYen(600_000)),
                                JournalLine.credit(StandardAccounts.CAPITAL_STOCK, Money.ofYen(600_000))
                        )
                ),
                Journal.of(
                        JournalId.newId(), LocalDate.of(2026, 4, 5), "銀行からの借入",
                        JournalType.NORMAL,
                        List.of(
                                JournalLine.debit(StandardAccounts.CASH, Money.ofYen(200_000)),
                                JournalLine.credit(StandardAccounts.LOANS_PAYABLE, Money.ofYen(200_000))
                        )
                )
        );

        BalanceSheet balanceSheet = BalanceSheet.of(journals);

        assertThat(balanceSheet.totalAssets()).isEqualTo(Money.ofYen(800_000));
        assertThat(balanceSheet.totalLiabilities()).isEqualTo(Money.ofYen(200_000));
        assertThat(balanceSheet.totalEquity()).isEqualTo(Money.ofYen(600_000));
        assertThat(balanceSheet.isBalanced()).isTrue();
    }

    @Test
    void of_excludesRevenueAndExpenseAccounts() {
        List<Journal> journals = List.of(
                Journal.of(
                        JournalId.newId(), LocalDate.of(2026, 4, 15), "商品の掛け売上",
                        JournalType.NORMAL,
                        List.of(
                                JournalLine.debit(StandardAccounts.ACCOUNTS_RECEIVABLE, Money.ofYen(280_000)),
                                JournalLine.credit(StandardAccounts.SALES, Money.ofYen(280_000))
                        )
                )
        );

        BalanceSheet balanceSheet = BalanceSheet.of(journals);

        assertThat(balanceSheet.assets()).containsOnlyKeys(StandardAccounts.ACCOUNTS_RECEIVABLE);
        assertThat(balanceSheet.liabilities()).isEmpty();
        assertThat(balanceSheet.equity()).isEmpty();
    }
}
