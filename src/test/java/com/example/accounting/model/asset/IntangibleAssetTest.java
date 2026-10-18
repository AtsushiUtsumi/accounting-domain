package com.example.accounting.model.asset;

import com.example.accounting.model.account.StandardAccounts;
import com.example.accounting.model.journal.Journal;
import com.example.accounting.model.period.AccountingPeriod;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static com.example.accounting.support.Journals.cr;
import static com.example.accounting.support.Journals.dr;
import static com.example.accounting.support.Journals.normal;
import static com.example.accounting.support.Journals.yen;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 無形固定資産の償却(直接法・定額法・残存0・月割)。期待値は問題集の解答例ではなく、
 * 一般的なテキストの処理にもとづく(未検証)。会計期間は4月1日〜3月31日。
 */
@Tag("unverified")
class IntangibleAssetTest {

    private final LocalDate acquired = LocalDate.of(2026, 7, 1);
    private final IntangibleAsset software = new IntangibleAsset(
            FixedAssetId.newId(), "ソフトウェア", StandardAccounts.SOFTWARE, StandardAccounts.SOFTWARE_AMORTIZATION,
            yen(500_000), acquired, 5);
    private final Journal purchase = normal(acquired,
            dr(StandardAccounts.SOFTWARE, 500_000), cr(StandardAccounts.CASH, 500_000));

    /** 7月1日取得の500,000円(5年)。初年度は 500,000 × 9/60 = 75,000円を直接減額する。 */
    @Test
    void firstPeriod_isProratedAndCreditedDirectlyToTheAsset() {
        AccountingPeriod period = AccountingPeriod.startingAt(LocalDate.of(2026, 4, 1));

        Journal entry = software.amortizationEntry(List.of(purchase), period).orElseThrow();

        assertThat(entry.lines()).containsExactlyInAnyOrder(
                dr(StandardAccounts.SOFTWARE_AMORTIZATION, 75_000),
                cr(StandardAccounts.SOFTWARE, 75_000));
    }

    /** 耐用年数の最後の期(2031年度、4〜6月)で残り25,000円を償却し、帳簿価額がゼロになる。 */
    @Test
    void lastPeriod_amortizesTheRemainingBookValue() {
        List<Journal> journals = new ArrayList<>(List.of(purchase));
        AccountingPeriod period = AccountingPeriod.startingAt(LocalDate.of(2026, 4, 1));
        List<Long> amounts = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            Journal entry = software.amortizationEntry(journals, period).orElseThrow();
            amounts.add(entry.debitTotal().amountInYen());
            journals.add(entry);
            period = period.nextPeriod();
        }

        assertThat(amounts).containsExactly(75_000L, 100_000L, 100_000L, 100_000L, 100_000L, 25_000L);
        assertThat(software.bookValue(journals)).isEqualTo(yen(0));
        assertThat(software.amortizationEntry(journals, period)).isEmpty();
    }
}
