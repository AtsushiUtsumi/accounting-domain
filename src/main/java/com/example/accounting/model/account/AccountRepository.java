package com.example.accounting.model.account;


import java.util.List;
import java.util.Optional;

/** 勘定科目の保存先。科目は削除せず無効化するため、削除の操作は持たない。 */
public interface AccountRepository {

    Optional<Account> findByCode(AccountCode code);

    List<Account> findAll();

    void save(Account account);

    boolean existsByCode(AccountCode code);
}
