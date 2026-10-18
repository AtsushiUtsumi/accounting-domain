package com.example.accounting.model.statement;

import com.example.accounting.model.account.StandardAccounts;
import com.example.accounting.model.journal.Journal;
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
 * 銀行勘定調整表(両者区分調整法)。期待値は問題集の解答例ではなく、一般的なテキストの処理にもとづく(未検証)。
 *
 * <p>銀行残高450,000円、帳簿残高400,000円。銀行側: 時間外預入30,000円(+)、未取付小切手40,000円(−)。
 * 帳簿側: 売掛金の入金の連絡未通知20,000円(+)、買掛金支払の未渡小切手25,000円(+)、
 * 水道光熱費8,000円を3,000円と誤記入(−5,000円)。調整後はどちらも440,000円。
 */
@Tag("unverified")
class BankReconciliationTest {

    private final LocalDate date = LocalDate.of(2027, 3, 31);
    private final List<Journal> journals = List.of(normal(LocalDate.of(2026, 4, 1),
            dr(StandardAccounts.CHECKING_ACCOUNT, 400_000), cr(StandardAccounts.CAPITAL_STOCK, 400_000)));

    private BankReconciliation reconciliation() {
        return BankReconciliation.of(StandardAccounts.CHECKING_ACCOUNT, yen(450_000), journals, List.of(
                new BankReconciliation.DepositInTransit(yen(30_000)),
                new BankReconciliation.OutstandingCheck(yen(40_000)),
                new BankReconciliation.UnrecordedDeposit(yen(20_000), StandardAccounts.ACCOUNTS_RECEIVABLE),
                new BankReconciliation.UnissuedCheck(yen(25_000), StandardAccounts.ACCOUNTS_PAYABLE),
                new BankReconciliation.RecordingError(yen(-5_000), StandardAccounts.UTILITIES_EXPENSE)));
    }

    @Test
    void adjustedBalancesAgree() {
        BankReconciliation reconciliation = reconciliation();

        assertThat(reconciliation.adjustedBankBalance()).isEqualTo(yen(440_000));
        assertThat(reconciliation.adjustedBookBalance()).isEqualTo(yen(440_000));
        assertThat(reconciliation.reconciles()).isTrue();
    }

    @Test
    void correctingEntriesAreOnlyForBookSideItems() {
        List<Journal> corrections = reconciliation().correctingEntries(date);

        assertThat(corrections).hasSize(3);
        assertThat(corrections.get(0).lines()).containsExactlyInAnyOrder(
                dr(StandardAccounts.CHECKING_ACCOUNT, 20_000), cr(StandardAccounts.ACCOUNTS_RECEIVABLE, 20_000));
        assertThat(corrections.get(1).lines()).containsExactlyInAnyOrder(
                dr(StandardAccounts.CHECKING_ACCOUNT, 25_000), cr(StandardAccounts.ACCOUNTS_PAYABLE, 25_000));
        assertThat(corrections.get(2).lines()).containsExactlyInAnyOrder(
                dr(StandardAccounts.UTILITIES_EXPENSE, 5_000), cr(StandardAccounts.CHECKING_ACCOUNT, 5_000));

        List<Journal> corrected = new ArrayList<>(journals);
        corrected.addAll(corrections);
        assertThat(Ledger.of(StandardAccounts.CHECKING_ACCOUNT, corrected).balance()).isEqualTo(yen(440_000));
    }
}
