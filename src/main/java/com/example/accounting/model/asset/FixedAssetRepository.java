package com.example.accounting.model.asset;


import java.util.List;
import java.util.Optional;

/** 固定資産台帳の保存先。累計償却額は保存せず、仕訳から導出する({@link FixedAsset} を参照)。 */
public interface FixedAssetRepository {

    Optional<FixedAsset> findById(FixedAssetId id);

    List<FixedAsset> findAll();

    void save(FixedAsset asset);
}
