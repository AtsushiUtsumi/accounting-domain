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

class IncomeStatementTest {

    @Test
    void of_computesFiveStageProfitThroughNetIncome() {
        List<Journal> journals = List.of(
                Journal.of(
                        JournalId.newId(), LocalDate.of(2026, 4, 10), "商品の掛け仕入",
                        JournalType.NORMAL,
                        List.of(
                                JournalLine.debit(StandardAccounts.PURCHASES, Money.ofYen(300_000)),
                                JournalLine.credit(StandardAccounts.ACCOUNTS_PAYABLE, Money.ofYen(300_000))
                        )
                ),
                Journal.of(
                        JournalId.newId(), LocalDate.of(2026, 4, 15), "商品の掛け売上",
                        JournalType.NORMAL,
                        List.of(
                                JournalLine.debit(StandardAccounts.ACCOUNTS_RECEIVABLE, Money.ofYen(280_000)),
                                JournalLine.credit(StandardAccounts.SALES, Money.ofYen(280_000))
                        )
                )
        );

        IncomeStatement incomeStatement = IncomeStatement.of(journals);

        assertThat(incomeStatement.sales()).isEqualTo(Money.ofYen(280_000));
        assertThat(incomeStatement.costOfSales()).isEqualTo(Money.ofYen(300_000));
        assertThat(incomeStatement.grossProfit()).isEqualTo(Money.ofYen(-20_000));
        assertThat(incomeStatement.operatingIncome()).isEqualTo(Money.ofYen(-20_000));
        assertThat(incomeStatement.ordinaryIncome()).isEqualTo(Money.ofYen(-20_000));
        assertThat(incomeStatement.incomeBeforeIncomeTax()).isEqualTo(Money.ofYen(-20_000));
        assertThat(incomeStatement.netIncome()).isEqualTo(Money.ofYen(-20_000));
    }
}
