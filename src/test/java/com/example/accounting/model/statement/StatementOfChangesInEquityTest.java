package com.example.accounting.model.statement;

import com.example.accounting.model.account.StandardAccounts;
import com.example.accounting.model.journal.Journal;
import com.example.accounting.model.journal.JournalId;
import com.example.accounting.model.journal.JournalLine;
import com.example.accounting.model.journal.JournalType;
import com.example.accounting.model.money.Money;
import com.example.accounting.service.DividendService;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 株主資本等変動計算書。
 * 期首: 資本金1,000,000円・資本準備金100,000円・利益準備金50,000円・繰越利益剰余金200,000円。
 * 当期: 新株発行(資本金+500,000円)、配当300,000円(利益準備金+30,000円)、当期純利益150,000円。
 */
class StatementOfChangesInEquityTest {

    private static final Journal OPENING = Journal.of(
            JournalId.newId(), LocalDate.of(2026, 4, 1), "開始仕訳", JournalType.OPENING,
            List.of(
                    JournalLine.debit(StandardAccounts.CASH, Money.ofYen(1_350_000)),
                    JournalLine.credit(StandardAccounts.CAPITAL_STOCK, Money.ofYen(1_000_000)),
                    JournalLine.credit(StandardAccounts.CAPITAL_SURPLUS, Money.ofYen(100_000)),
                    JournalLine.credit(StandardAccounts.STATUTORY_RESERVE, Money.ofYen(50_000)),
                    JournalLine.credit(StandardAccounts.RETAINED_EARNINGS, Money.ofYen(200_000))
            ));

    private static final Journal SHARE_ISSUANCE = Journal.of(
            JournalId.newId(), LocalDate.of(2026, 5, 1), "新株の発行", JournalType.NORMAL,
            List.of(
                    JournalLine.debit(StandardAccounts.CASH, Money.ofYen(500_000)),
                    JournalLine.credit(StandardAccounts.CAPITAL_STOCK, Money.ofYen(500_000))
            ));

    private static final Journal NET_INCOME_TRANSFER = Journal.of(
            JournalId.newId(), LocalDate.of(2027, 3, 31), "当期純利益の繰越利益剰余金への振替", JournalType.CLOSING,
            List.of(
                    JournalLine.debit(StandardAccounts.PROFIT_AND_LOSS, Money.ofYen(150_000)),
                    JournalLine.credit(StandardAccounts.RETAINED_EARNINGS, Money.ofYen(150_000))
            ));

    private static Journal dividend() {
        return new DividendService().declareDividends(
                Money.ofYen(300_000), Money.ofYen(1_500_000), Money.ofYen(100_000), Money.ofYen(50_000),
                LocalDate.of(2026, 6, 1));
    }

    @Test
    void of_groupsChangesByReason() {
        StatementOfChangesInEquity statement = StatementOfChangesInEquity.of(
                List.of(OPENING, SHARE_ISSUANCE, dividend(), NET_INCOME_TRANSFER));

        assertThat(statement.openingBalances()).containsExactlyInAnyOrderEntriesOf(Map.of(
                StandardAccounts.CAPITAL_STOCK, Money.ofYen(1_000_000),
                StandardAccounts.CAPITAL_SURPLUS, Money.ofYen(100_000),
                StandardAccounts.STATUTORY_RESERVE, Money.ofYen(50_000),
                StandardAccounts.RETAINED_EARNINGS, Money.ofYen(200_000)
        ));

        assertThat(statement.changesByReason().get(EquityChangeReason.ISSUANCE_OF_SHARES))
                .containsExactlyInAnyOrderEntriesOf(Map.of(StandardAccounts.CAPITAL_STOCK, Money.ofYen(500_000)));

        assertThat(statement.changesByReason().get(EquityChangeReason.DIVIDENDS))
                .containsExactlyInAnyOrderEntriesOf(Map.of(
                        StandardAccounts.RETAINED_EARNINGS, Money.ofYen(-330_000),
                        StandardAccounts.STATUTORY_RESERVE, Money.ofYen(30_000)
                ));

        assertThat(statement.changesByReason().get(EquityChangeReason.NET_INCOME))
                .containsExactlyInAnyOrderEntriesOf(Map.of(StandardAccounts.RETAINED_EARNINGS, Money.ofYen(150_000)));

        assertThat(statement.closingBalances()).containsExactlyInAnyOrderEntriesOf(Map.of(
                StandardAccounts.CAPITAL_STOCK, Money.ofYen(1_500_000),
                StandardAccounts.CAPITAL_SURPLUS, Money.ofYen(100_000),
                StandardAccounts.STATUTORY_RESERVE, Money.ofYen(80_000),
                StandardAccounts.RETAINED_EARNINGS, Money.ofYen(20_000)
        ));

        assertThat(statement.totalEquityAtBeginning()).isEqualTo(Money.ofYen(1_350_000));
        assertThat(statement.totalEquityAtEnd()).isEqualTo(Money.ofYen(1_700_000));
        assertThat(statement.reconciles()).isTrue();
    }
}
