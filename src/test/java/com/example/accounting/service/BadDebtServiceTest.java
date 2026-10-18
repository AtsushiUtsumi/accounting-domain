package com.example.accounting.service;

import com.example.accounting.model.account.StandardAccounts;
import com.example.accounting.model.journal.Journal;
import com.example.accounting.model.journal.JournalType;
import com.example.accounting.model.statement.Ledger;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static com.example.accounting.support.Journals.cr;
import static com.example.accounting.support.Journals.dr;
import static com.example.accounting.support.Journals.journal;
import static com.example.accounting.support.Journals.yen;
import static org.assertj.core.api.Assertions.assertThat;

/** 貸倒れ。期待値は問題集の解答例ではなく、一般的なテキストの処理にもとづく(未検証)。 */
@Tag("unverified")
class BadDebtServiceTest {

    private final BadDebtService service = new BadDebtService();
    private final LocalDate date = LocalDate.of(2026, 6, 1);

    /** 前期発生の売掛金5,000円が貸倒れ。貸倒引当金3,000円を取り崩し、不足2,000円は貸倒損失。 */
    @Test
    void writeOff_priorPeriodReceivable_usesAllowanceFirst() {
        Journal allowance = journal(LocalDate.of(2026, 3, 31), JournalType.ADJUSTING,
                dr(StandardAccounts.BAD_DEBT_EXPENSE, 3_000), cr(StandardAccounts.ALLOWANCE_FOR_DOUBTFUL_ACCOUNTS, 3_000));

        Journal writeOff = service.writeOff(
                StandardAccounts.ACCOUNTS_RECEIVABLE, yen(5_000), false, List.of(allowance), date);

        assertThat(writeOff.lines()).containsExactlyInAnyOrder(
                dr(StandardAccounts.ALLOWANCE_FOR_DOUBTFUL_ACCOUNTS, 3_000),
                dr(StandardAccounts.BAD_DEBT_LOSS, 2_000),
                cr(StandardAccounts.ACCOUNTS_RECEIVABLE, 5_000));
        assertThat(Ledger.of(StandardAccounts.ALLOWANCE_FOR_DOUBTFUL_ACCOUNTS, List.of(allowance, writeOff)).balance())
                .isEqualTo(yen(0));
    }

    /** 前期発生でも、引当金が十分なら貸倒損失は出ない。 */
    @Test
    void writeOff_priorPeriodReceivable_withinAllowance() {
        Journal allowance = journal(LocalDate.of(2026, 3, 31), JournalType.ADJUSTING,
                dr(StandardAccounts.BAD_DEBT_EXPENSE, 10_000), cr(StandardAccounts.ALLOWANCE_FOR_DOUBTFUL_ACCOUNTS, 10_000));

        Journal writeOff = service.writeOff(
                StandardAccounts.ACCOUNTS_RECEIVABLE, yen(4_000), false, List.of(allowance), date);

        assertThat(writeOff.lines()).containsExactlyInAnyOrder(
                dr(StandardAccounts.ALLOWANCE_FOR_DOUBTFUL_ACCOUNTS, 4_000),
                cr(StandardAccounts.ACCOUNTS_RECEIVABLE, 4_000));
    }

    /** 当期発生の売掛金4,000円が貸倒れ。引当金があっても使わず、全額を貸倒損失。 */
    @Test
    void writeOff_currentPeriodReceivable_isAllLoss() {
        Journal allowance = journal(LocalDate.of(2026, 3, 31), JournalType.ADJUSTING,
                dr(StandardAccounts.BAD_DEBT_EXPENSE, 10_000), cr(StandardAccounts.ALLOWANCE_FOR_DOUBTFUL_ACCOUNTS, 10_000));

        Journal writeOff = service.writeOff(
                StandardAccounts.ACCOUNTS_RECEIVABLE, yen(4_000), true, List.of(allowance), date);

        assertThat(writeOff.lines()).containsExactlyInAnyOrder(
                dr(StandardAccounts.BAD_DEBT_LOSS, 4_000),
                cr(StandardAccounts.ACCOUNTS_RECEIVABLE, 4_000));
    }

    /** 前期に貸倒れ処理した売掛金1,000円を現金で回収。償却債権取立益とする。 */
    @Test
    void recoverWrittenOff_isRecordedAsRecovery() {
        Journal recovery = service.recoverWrittenOff(StandardAccounts.CASH, yen(1_000), date);

        assertThat(recovery.lines()).containsExactlyInAnyOrder(
                dr(StandardAccounts.CASH, 1_000),
                cr(StandardAccounts.RECOVERY_OF_BAD_DEBTS, 1_000));
    }
}
