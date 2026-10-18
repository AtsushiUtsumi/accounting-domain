package com.example.accounting.model.journal;

import com.example.accounting.model.period.AccountingPeriod;

import java.util.List;
import java.util.Optional;

/**
 * 仕訳帳の保存先。帳簿は追記のみのため、更新・削除の操作は持たない(誤りは赤黒処理で訂正する)。
 * 保存できるのは記帳済みの仕訳({@link PostedJournal})だけ。実装は利用側の責務。
 */
public interface JournalRepository {

    /** 1件を保存する。契約は {@link #saveAll} と同じ。 */
    void save(PostedJournal journal);

    /**
     * 複数の仕訳を保存する。全件保存するか、1件も保存しないか。途中まで保存された状態を残してはならない。
     * 実際のトランザクション制御は実装側の責務。
     *
     * <p>保存時点の会計期間の version が {@link PostedJournal#periodVersion()} と一致しない仕訳
     * (記帳の後に、その期間が締められた仕訳)を含むときは、1件も保存せずに失敗すること。
     * これにより、記帳と締めが同時に行われても、締めた期に仕訳が入らない。
     */
    void saveAll(List<PostedJournal> journals);

    Optional<PostedJournal> findById(JournalId id);

    /** その会計期間に記帳された仕訳をすべて返す(締めた期のものも含む)。 */
    List<PostedJournal> findByPeriod(AccountingPeriod period);
}
