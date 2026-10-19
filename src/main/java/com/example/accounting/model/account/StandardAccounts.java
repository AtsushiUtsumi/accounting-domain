package com.example.accounting.model.account;

import java.util.List;
import java.util.Set;

/**
 * 初期セットとしての標準科目。標準科目は変更・無効化できない。
 * 会社ごとに独自の科目を追加してよく、このクラスは簿記3級〜2級(商業簿記)で使う科目の初期セット。
 *
 * <p>減価償却累計額のように資産ごとに専用にすべき勘定は、同じ種類の資産が複数あるなら
 * 利用側で資産ごとに科目を作ること({@link com.example.accounting.model.asset.FixedAsset} を参照)。
 *
 * <p>貸倒引当金・備品減価償却累計額は資産の控除項目(contra-asset)だが、type は控除対象と
 * 同じ ASSET とする。通常は貸方残高になるため {@code netBalance}/{@code balance} は負の値になり、
 * B/S の資産合計から自動的に差し引かれる(損益勘定が純損失時に負の値になるのと同じ扱い)。
 */
public final class StandardAccounts {

    public static final Account CASH =
            standard("101", "現金", AccountType.ASSET, AccountCategory.CURRENT_ASSET);

    public static final Account ACCOUNTS_RECEIVABLE =
            standard("102", "売掛金", AccountType.ASSET, AccountCategory.CURRENT_ASSET);

    public static final Account MERCHANDISE_INVENTORY =
            standard("103", "繰越商品", AccountType.ASSET, AccountCategory.CURRENT_ASSET);

    /** 貸倒引当金。売掛金等の控除項目(contra-asset)。 */
    public static final Account ALLOWANCE_FOR_DOUBTFUL_ACCOUNTS =
            standard("104", "貸倒引当金", AccountType.ASSET, AccountCategory.CURRENT_ASSET);

    public static final Account PREPAID_EXPENSE =
            standard("105", "前払費用", AccountType.ASSET, AccountCategory.CURRENT_ASSET);

    public static final Account ACCRUED_REVENUE =
            standard("106", "未収収益", AccountType.ASSET, AccountCategory.CURRENT_ASSET);

    public static final Account CONSUMPTION_TAX_REFUNDABLE =
            standard("107", "未収消費税", AccountType.ASSET, AccountCategory.CURRENT_ASSET);

    public static final Account EQUIPMENT =
            standard("108", "備品", AccountType.ASSET, AccountCategory.FIXED_ASSET);

    /** 備品減価償却累計額。備品の控除項目(contra-asset)。間接法で使う。 */
    public static final Account ACCUMULATED_DEPRECIATION_EQUIPMENT =
            standard("109", "備品減価償却累計額", AccountType.ASSET, AccountCategory.FIXED_ASSET);

    public static final Account CONSUMPTION_TAX_PAID =
            standard("110", "仮払消費税", AccountType.ASSET, AccountCategory.CURRENT_ASSET);

    public static final Account PREPAID_CORPORATE_TAX =
            standard("111", "仮払法人税等", AccountType.ASSET, AccountCategory.CURRENT_ASSET);

    public static final Account OTHER_RECEIVABLES =
            standard("112", "未収入金", AccountType.ASSET, AccountCategory.CURRENT_ASSET);

    /** 受取手形(約束手形)。為替手形は対象外。 */
    public static final Account NOTES_RECEIVABLE =
            standard("113", "受取手形", AccountType.ASSET, AccountCategory.CURRENT_ASSET);

    /** 売買目的有価証券。期末は時価で評価する(切放方式)。 */
    public static final Account TRADING_SECURITIES =
            standard("114", "売買目的有価証券", AccountType.ASSET, AccountCategory.CURRENT_ASSET);

    public static final Account ACCOUNTS_PAYABLE =
            standard("201", "買掛金", AccountType.LIABILITY, AccountCategory.CURRENT_LIABILITY);

    public static final Account LOANS_PAYABLE =
            standard("202", "借入金", AccountType.LIABILITY, AccountCategory.CURRENT_LIABILITY);

    /** 支払手形(約束手形)。為替手形は対象外。 */
    public static final Account NOTES_PAYABLE =
            standard("208", "支払手形", AccountType.LIABILITY, AccountCategory.CURRENT_LIABILITY);

    /** 未払配当金。株主総会で配当を決議した時点で計上し、実際の支払時に取り崩す。 */
    public static final Account DIVIDENDS_PAYABLE =
            standard("209", "未払配当金", AccountType.LIABILITY, AccountCategory.CURRENT_LIABILITY);

    public static final Account UNEARNED_REVENUE =
            standard("203", "前受収益", AccountType.LIABILITY, AccountCategory.CURRENT_LIABILITY);

    public static final Account ACCRUED_EXPENSE =
            standard("204", "未払費用", AccountType.LIABILITY, AccountCategory.CURRENT_LIABILITY);

    public static final Account CONSUMPTION_TAX_RECEIVED =
            standard("205", "仮受消費税", AccountType.LIABILITY, AccountCategory.CURRENT_LIABILITY);

    public static final Account CONSUMPTION_TAX_PAYABLE =
            standard("206", "未払消費税", AccountType.LIABILITY, AccountCategory.CURRENT_LIABILITY);

    public static final Account ACCRUED_CORPORATE_TAX =
            standard("207", "未払法人税等", AccountType.LIABILITY, AccountCategory.CURRENT_LIABILITY);

    public static final Account CAPITAL_STOCK =
            standard("301", "資本金", AccountType.EQUITY, AccountCategory.CAPITAL);

    /** 繰越利益剰余金。保護される標準科目(最低限)の一つ。 */
    public static final Account RETAINED_EARNINGS =
            standard("302", "繰越利益剰余金", AccountType.EQUITY, AccountCategory.RETAINED_EARNINGS);

    /** 資本準備金。新株発行時、発行価額のうち資本金に組み入れない部分。 */
    public static final Account CAPITAL_SURPLUS =
            standard("303", "資本準備金", AccountType.EQUITY, AccountCategory.CAPITAL);

    /** 利益準備金。剰余金の配当時、会社法の規定に従って積み立てる。 */
    public static final Account STATUTORY_RESERVE =
            standard("304", "利益準備金", AccountType.EQUITY, AccountCategory.RETAINED_EARNINGS);

    public static final Account SALES =
            standard("401", "売上", AccountType.REVENUE, AccountCategory.SALES);

    public static final Account ALLOWANCE_FOR_DOUBTFUL_ACCOUNTS_REVERSAL =
            standard("402", "貸倒引当金戻入", AccountType.REVENUE, AccountCategory.NON_OPERATING_INCOME);

    /** 固定資産売却益。特別利益(非反復的な損益)として扱う。 */
    public static final Account GAIN_ON_DISPOSAL_OF_FIXED_ASSETS =
            standard("403", "固定資産売却益", AccountType.REVENUE, AccountCategory.EXTRAORDINARY_INCOME);

    /** 雑収入。原因不明の現金過不足のうち過剰だった分の決算時の振替先。 */
    public static final Account MISCELLANEOUS_INCOME =
            standard("404", "雑収入", AccountType.REVENUE, AccountCategory.NON_OPERATING_INCOME);

    /** 有価証券売却益。売買目的有価証券の売却時、売却価額が帳簿価額を上回った差額。 */
    public static final Account GAIN_ON_SALE_OF_SECURITIES =
            standard("405", "有価証券売却益", AccountType.REVENUE, AccountCategory.NON_OPERATING_INCOME);

    /** 有価証券評価益。売買目的有価証券の期末の時価評価(切放方式)による差額。 */
    public static final Account UNREALIZED_GAIN_ON_SECURITIES =
            standard("406", "有価証券評価益", AccountType.REVENUE, AccountCategory.NON_OPERATING_INCOME);

    public static final Account PURCHASES =
            standard("501", "仕入", AccountType.EXPENSE, AccountCategory.COST_OF_SALES);

    public static final Account DEPRECIATION_EXPENSE =
            standard("502", "減価償却費", AccountType.EXPENSE, AccountCategory.SG_AND_A);

    public static final Account BAD_DEBT_EXPENSE =
            standard("503", "貸倒引当金繰入", AccountType.EXPENSE, AccountCategory.SG_AND_A);

    public static final Account CORPORATE_TAX_EXPENSE =
            standard("504", "法人税等", AccountType.EXPENSE, AccountCategory.INCOME_TAX_EXPENSE);

    public static final Account INSURANCE_EXPENSE =
            standard("505", "保険料", AccountType.EXPENSE, AccountCategory.SG_AND_A);

    /** 固定資産売却損。特別損失(非反復的な損益)として扱う。 */
    public static final Account LOSS_ON_DISPOSAL_OF_FIXED_ASSETS =
            standard("506", "固定資産売却損", AccountType.EXPENSE, AccountCategory.EXTRAORDINARY_LOSS);

    /** 手形売却損。受取手形の割引料。営業外費用として扱う。 */
    public static final Account LOSS_ON_NOTE_DISCOUNTING =
            standard("507", "手形売却損", AccountType.EXPENSE, AccountCategory.NON_OPERATING_EXPENSE);

    /** 雑損。原因不明の現金過不足のうち不足だった分の決算時の振替先。 */
    public static final Account MISCELLANEOUS_LOSS =
            standard("508", "雑損", AccountType.EXPENSE, AccountCategory.NON_OPERATING_EXPENSE);

    /** 有価証券売却損。売買目的有価証券の売却時、売却価額が帳簿価額を下回った差額。 */
    public static final Account LOSS_ON_SALE_OF_SECURITIES =
            standard("509", "有価証券売却損", AccountType.EXPENSE, AccountCategory.NON_OPERATING_EXPENSE);

    /** 有価証券評価損。売買目的有価証券の期末の時価評価(切放方式)による差額。 */
    public static final Account UNREALIZED_LOSS_ON_SECURITIES =
            standard("510", "有価証券評価損", AccountType.EXPENSE, AccountCategory.NON_OPERATING_EXPENSE);

    // ---- 3級の基本科目(フェーズ5) ----

    /** 当座預金。残高が貸方(借越)のまま期末を迎えたら当座借越へ振り替える。 */
    public static final Account CHECKING_ACCOUNT =
            standard("115", "当座預金", AccountType.ASSET, AccountCategory.CURRENT_ASSET);

    public static final Account ORDINARY_DEPOSIT =
            standard("116", "普通預金", AccountType.ASSET, AccountCategory.CURRENT_ASSET);

    /** 定期預金。満期が1年を超えるものは利用側で固定資産の科目を別に作ること。 */
    public static final Account TIME_DEPOSIT =
            standard("117", "定期預金", AccountType.ASSET, AccountCategory.CURRENT_ASSET);

    public static final Account PETTY_CASH =
            standard("118", "小口現金", AccountType.ASSET, AccountCategory.CURRENT_ASSET);

    /** 前払金。商品の代金の一部を前もって支払ったときの債権。 */
    public static final Account ADVANCE_PAYMENTS =
            standard("119", "前払金", AccountType.ASSET, AccountCategory.CURRENT_ASSET);

    /** 仮払金。内容・金額が未確定の支出。確定時に正しい科目へ振り替える。 */
    public static final Account SUSPENSE_PAYMENTS =
            standard("120", "仮払金", AccountType.ASSET, AccountCategory.CURRENT_ASSET);

    public static final Account REIMBURSABLE_ADVANCES =
            standard("121", "立替金", AccountType.ASSET, AccountCategory.CURRENT_ASSET);

    /** 貸付金(短期)。返済期限が1年を超えるものは利用側で固定資産の科目を別に作ること。 */
    public static final Account LOANS_RECEIVABLE =
            standard("122", "貸付金", AccountType.ASSET, AccountCategory.CURRENT_ASSET);

    public static final Account ELECTRONICALLY_RECORDED_RECEIVABLES =
            standard("123", "電子記録債権", AccountType.ASSET, AccountCategory.CURRENT_ASSET);

    public static final Account CREDIT_CARD_RECEIVABLES =
            standard("124", "クレジット売掛金", AccountType.ASSET, AccountCategory.CURRENT_ASSET);

    public static final Account GIFT_CERTIFICATES_RECEIVED =
            standard("125", "受取商品券", AccountType.ASSET, AccountCategory.CURRENT_ASSET);

    /** 貯蔵品。期末に未使用の収入印紙・切手など。決算整理で振り替え、翌期首に再振替する。 */
    public static final Account SUPPLIES =
            standard("126", "貯蔵品", AccountType.ASSET, AccountCategory.CURRENT_ASSET);

    public static final Account BUILDINGS =
            standard("127", "建物", AccountType.ASSET, AccountCategory.FIXED_ASSET);

    /** 建物減価償却累計額。建物の控除項目(contra-asset)。 */
    public static final Account ACCUMULATED_DEPRECIATION_BUILDINGS =
            standard("128", "建物減価償却累計額", AccountType.ASSET, AccountCategory.FIXED_ASSET);

    public static final Account VEHICLES =
            standard("129", "車両運搬具", AccountType.ASSET, AccountCategory.FIXED_ASSET);

    /** 車両運搬具減価償却累計額。車両運搬具の控除項目(contra-asset)。 */
    public static final Account ACCUMULATED_DEPRECIATION_VEHICLES =
            standard("130", "車両運搬具減価償却累計額", AccountType.ASSET, AccountCategory.FIXED_ASSET);

    /** 土地。減価償却しない。 */
    public static final Account LAND =
            standard("131", "土地", AccountType.ASSET, AccountCategory.FIXED_ASSET);

    public static final Account GUARANTEE_DEPOSITS =
            standard("132", "差入保証金", AccountType.ASSET, AccountCategory.FIXED_ASSET);

    /** 当座借越。期末に当座預金の貸方残高を振り替える先。 */
    public static final Account BANK_OVERDRAFT =
            standard("210", "当座借越", AccountType.LIABILITY, AccountCategory.CURRENT_LIABILITY);

    /** 未払金。商品以外(固定資産・費用)の代金の未払い。 */
    public static final Account OTHER_PAYABLES =
            standard("211", "未払金", AccountType.LIABILITY, AccountCategory.CURRENT_LIABILITY);

    public static final Account ADVANCES_RECEIVED =
            standard("212", "前受金", AccountType.LIABILITY, AccountCategory.CURRENT_LIABILITY);

    /** 仮受金。内容が未確定の入金。確定時に正しい科目へ振り替える。 */
    public static final Account SUSPENSE_RECEIPTS =
            standard("213", "仮受金", AccountType.LIABILITY, AccountCategory.CURRENT_LIABILITY);

    public static final Account INCOME_TAX_WITHHELD =
            standard("214", "所得税預り金", AccountType.LIABILITY, AccountCategory.CURRENT_LIABILITY);

    public static final Account SOCIAL_INSURANCE_WITHHELD =
            standard("215", "社会保険料預り金", AccountType.LIABILITY, AccountCategory.CURRENT_LIABILITY);

    public static final Account ELECTRONICALLY_RECORDED_PAYABLES =
            standard("216", "電子記録債務", AccountType.LIABILITY, AccountCategory.CURRENT_LIABILITY);

    public static final Account INTEREST_INCOME =
            standard("407", "受取利息", AccountType.REVENUE, AccountCategory.NON_OPERATING_INCOME);

    public static final Account DIVIDEND_INCOME =
            standard("408", "受取配当金", AccountType.REVENUE, AccountCategory.NON_OPERATING_INCOME);

    public static final Account RENT_INCOME =
            standard("409", "受取家賃", AccountType.REVENUE, AccountCategory.NON_OPERATING_INCOME);

    /** 償却債権取立益。前期以前に貸倒れ処理した債権を回収したときの収益。 */
    public static final Account RECOVERY_OF_BAD_DEBTS =
            standard("410", "償却債権取立益", AccountType.REVENUE, AccountCategory.NON_OPERATING_INCOME);

    public static final Account SALARIES =
            standard("511", "給料", AccountType.EXPENSE, AccountCategory.SG_AND_A);

    public static final Account LEGAL_WELFARE_EXPENSE =
            standard("512", "法定福利費", AccountType.EXPENSE, AccountCategory.SG_AND_A);

    public static final Account RENT_EXPENSE =
            standard("513", "支払家賃", AccountType.EXPENSE, AccountCategory.SG_AND_A);

    public static final Account COMMUNICATION_EXPENSE =
            standard("514", "通信費", AccountType.EXPENSE, AccountCategory.SG_AND_A);

    public static final Account UTILITIES_EXPENSE =
            standard("515", "水道光熱費", AccountType.EXPENSE, AccountCategory.SG_AND_A);

    public static final Account SUPPLIES_EXPENSE =
            standard("516", "消耗品費", AccountType.EXPENSE, AccountCategory.SG_AND_A);

    public static final Account TAXES_AND_DUES =
            standard("517", "租税公課", AccountType.EXPENSE, AccountCategory.SG_AND_A);

    public static final Account COMMISSION_EXPENSE =
            standard("518", "支払手数料", AccountType.EXPENSE, AccountCategory.SG_AND_A);

    public static final Account SHIPPING_EXPENSE =
            standard("519", "発送費", AccountType.EXPENSE, AccountCategory.SG_AND_A);

    public static final Account ADVERTISING_EXPENSE =
            standard("520", "広告宣伝費", AccountType.EXPENSE, AccountCategory.SG_AND_A);

    public static final Account TRAVEL_EXPENSE =
            standard("521", "旅費交通費", AccountType.EXPENSE, AccountCategory.SG_AND_A);

    /** 貸倒損失。貸倒引当金で賄えない貸倒れ(当期発生の債権、または引当金の不足分)。 */
    public static final Account BAD_DEBT_LOSS =
            standard("522", "貸倒損失", AccountType.EXPENSE, AccountCategory.SG_AND_A);

    public static final Account INTEREST_EXPENSE =
            standard("523", "支払利息", AccountType.EXPENSE, AccountCategory.NON_OPERATING_EXPENSE);

    // ---- 2級の決算整理(フェーズ7・8) ----

    /** 商品。売上原価対立法で使う(三分法では繰越商品を使う)。 */
    public static final Account MERCHANDISE =
            standard("133", "商品", AccountType.ASSET, AccountCategory.CURRENT_ASSET);

    /** 売上原価。売上原価対立法で、販売のつど商品から振り替える。 */
    public static final Account COST_OF_GOODS_SOLD =
            standard("524", "売上原価", AccountType.EXPENSE, AccountCategory.COST_OF_SALES);

    /** 棚卸減耗損。帳簿数量と実地数量の差。既定は販売費及び一般管理費(売上原価に含める場合は利用側で科目を作る)。 */
    public static final Account INVENTORY_SHRINKAGE_LOSS =
            standard("525", "棚卸減耗損", AccountType.EXPENSE, AccountCategory.SG_AND_A);

    /** 商品評価損。正味売却価額が原価を下回った分。売上原価の内訳とする。 */
    public static final Account INVENTORY_VALUATION_LOSS =
            standard("526", "商品評価損", AccountType.EXPENSE, AccountCategory.COST_OF_SALES);

    public static final Account PROVISION_FOR_BONUSES =
            standard("218", "賞与引当金", AccountType.LIABILITY, AccountCategory.CURRENT_LIABILITY);

    public static final Account PROVISION_FOR_REPAIRS =
            standard("219", "修繕引当金", AccountType.LIABILITY, AccountCategory.CURRENT_LIABILITY);

    public static final Account PROVISION_FOR_PRODUCT_WARRANTIES =
            standard("220", "商品保証引当金", AccountType.LIABILITY, AccountCategory.CURRENT_LIABILITY);

    public static final Account PROVISION_FOR_RETIREMENT_BENEFITS =
            standard("221", "退職給付引当金", AccountType.LIABILITY, AccountCategory.FIXED_LIABILITY);

    public static final Account BONUS_PROVISION_EXPENSE =
            standard("527", "賞与引当金繰入", AccountType.EXPENSE, AccountCategory.SG_AND_A);

    public static final Account REPAIR_PROVISION_EXPENSE =
            standard("528", "修繕引当金繰入", AccountType.EXPENSE, AccountCategory.SG_AND_A);

    public static final Account WARRANTY_PROVISION_EXPENSE =
            standard("529", "商品保証引当金繰入", AccountType.EXPENSE, AccountCategory.SG_AND_A);

    public static final Account RETIREMENT_BENEFIT_EXPENSE =
            standard("530", "退職給付費用", AccountType.EXPENSE, AccountCategory.SG_AND_A);

    public static final Account BONUSES =
            standard("531", "賞与", AccountType.EXPENSE, AccountCategory.SG_AND_A);

    public static final Account REPAIR_EXPENSE =
            standard("532", "修繕費", AccountType.EXPENSE, AccountCategory.SG_AND_A);

    public static final Account WARRANTY_EXPENSE =
            standard("533", "商品保証費", AccountType.EXPENSE, AccountCategory.SG_AND_A);

    public static final Account BONUS_PROVISION_REVERSAL =
            standard("415", "賞与引当金戻入", AccountType.REVENUE, AccountCategory.NON_OPERATING_INCOME);

    public static final Account REPAIR_PROVISION_REVERSAL =
            standard("416", "修繕引当金戻入", AccountType.REVENUE, AccountCategory.NON_OPERATING_INCOME);

    public static final Account WARRANTY_PROVISION_REVERSAL =
            standard("417", "商品保証引当金戻入", AccountType.REVENUE, AccountCategory.NON_OPERATING_INCOME);

    // ---- 固定資産の諸論点(フェーズ9) ----

    /** 建設仮勘定。完成前の建物などへの支払い。完成時に本勘定へ振り替える。減価償却しない。 */
    public static final Account CONSTRUCTION_IN_PROGRESS =
            standard("136", "建設仮勘定", AccountType.ASSET, AccountCategory.FIXED_ASSET);

    /** 前払利息。割賦購入の利息相当額のうち、まだ支払っていない回の分。 */
    public static final Account PREPAID_INTEREST =
            standard("137", "前払利息", AccountType.ASSET, AccountCategory.CURRENT_ASSET);

    /** ソフトウェア(無形固定資産)。直接法で償却する。 */
    public static final Account SOFTWARE =
            standard("138", "ソフトウェア", AccountType.ASSET, AccountCategory.FIXED_ASSET);

    /** のれん(無形固定資産)。合併の対価が受け入れた純資産を超える額。直接法で償却する。 */
    public static final Account GOODWILL =
            standard("139", "のれん", AccountType.ASSET, AccountCategory.FIXED_ASSET);

    /** 営業外支払手形。固定資産の購入など、商品売買以外で振り出した手形。 */
    public static final Account NON_TRADE_NOTES_PAYABLE =
            standard("217", "営業外支払手形", AccountType.LIABILITY, AccountCategory.CURRENT_LIABILITY);

    public static final Account GAIN_ON_GOVERNMENT_SUBSIDIES =
            standard("412", "国庫補助金受贈益", AccountType.REVENUE, AccountCategory.EXTRAORDINARY_INCOME);

    public static final Account LOSS_ON_RETIREMENT_OF_FIXED_ASSETS =
            standard("534", "固定資産除却損", AccountType.EXPENSE, AccountCategory.EXTRAORDINARY_LOSS);

    public static final Account LOSS_ON_ABANDONMENT_OF_FIXED_ASSETS =
            standard("535", "固定資産廃棄損", AccountType.EXPENSE, AccountCategory.EXTRAORDINARY_LOSS);

    /** 固定資産圧縮損。圧縮記帳(直接減額方式)で取得価額を減らした額。 */
    public static final Account LOSS_ON_COMPRESSION_OF_FIXED_ASSETS =
            standard("536", "固定資産圧縮損", AccountType.EXPENSE, AccountCategory.EXTRAORDINARY_LOSS);

    public static final Account SOFTWARE_AMORTIZATION =
            standard("537", "ソフトウェア償却", AccountType.EXPENSE, AccountCategory.SG_AND_A);

    public static final Account GOODWILL_AMORTIZATION =
            standard("538", "のれん償却", AccountType.EXPENSE, AccountCategory.SG_AND_A);

    // ---- 有価証券の残りの区分(フェーズ10) ----

    /** 満期保有目的債券。期末は償却原価法(定額法)で評価する。 */
    public static final Account HELD_TO_MATURITY_BONDS =
            standard("140", "満期保有目的債券", AccountType.ASSET, AccountCategory.FIXED_ASSET);

    /** 子会社株式。期末は取得原価のまま評価しない。 */
    public static final Account SUBSIDIARY_STOCKS =
            standard("141", "子会社株式", AccountType.ASSET, AccountCategory.FIXED_ASSET);

    /** 関連会社株式。期末は取得原価のまま評価しない。 */
    public static final Account AFFILIATE_STOCKS =
            standard("142", "関連会社株式", AccountType.ASSET, AccountCategory.FIXED_ASSET);

    /** その他有価証券。期末は時価で評価し、差額は純資産に直接計上する(全部純資産直入法・洗替方式)。 */
    public static final Account AVAILABLE_FOR_SALE_SECURITIES =
            standard("143", "その他有価証券", AccountType.ASSET, AccountCategory.FIXED_ASSET);

    /** その他有価証券評価差額金(評価・換算差額等)。翌期首に再振替で戻す。 */
    public static final Account VALUATION_DIFFERENCE_ON_AVAILABLE_FOR_SALE_SECURITIES =
            standard("307", "その他有価証券評価差額金", AccountType.EQUITY, AccountCategory.VALUATION_AND_TRANSLATION_ADJUSTMENTS);

    /** 有価証券利息。債券の利息と、償却原価法による帳簿価額の調整額。 */
    public static final Account INTEREST_ON_SECURITIES =
            standard("411", "有価証券利息", AccountType.REVENUE, AccountCategory.NON_OPERATING_INCOME);

    // ---- 手形・電子記録債権の諸論点(フェーズ11) ----

    /** 不渡手形。支払いを拒絶された手形の金額と償還請求の費用。 */
    public static final Account DISHONORED_NOTES =
            standard("134", "不渡手形", AccountType.ASSET, AccountCategory.CURRENT_ASSET);

    /** 営業外受取手形。固定資産の売却など、商品売買以外で受け取った手形。 */
    public static final Account NON_TRADE_NOTES_RECEIVABLE =
            standard("135", "営業外受取手形", AccountType.ASSET, AccountCategory.CURRENT_ASSET);

    public static final Account LOSS_ON_SALE_OF_ELECTRONIC_RECEIVABLES =
            standard("539", "電子記録債権売却損", AccountType.EXPENSE, AccountCategory.NON_OPERATING_EXPENSE);

    // ---- 純資産(フェーズ12) ----

    /** その他資本剰余金。資本剰余金のうち資本準備金以外のもの。配当の原資にできる。 */
    public static final Account OTHER_CAPITAL_SURPLUS =
            standard("305", "その他資本剰余金", AccountType.EQUITY, AccountCategory.CAPITAL);

    /** 別途積立金(任意積立金)。繰越利益剰余金から積み立てる。 */
    public static final Account GENERAL_RESERVE =
            standard("306", "別途積立金", AccountType.EQUITY, AccountCategory.RETAINED_EARNINGS);

    public static final Account GAIN_ON_BARGAIN_PURCHASE =
            standard("413", "負ののれん発生益", AccountType.REVENUE, AccountCategory.EXTRAORDINARY_INCOME);

    // ---- 収益認識(フェーズ13) ----

    /** 仕掛品。サービス業で、収益計上前に発生した役務の費用を繰り延べる。 */
    public static final Account WORK_IN_PROCESS =
            standard("144", "仕掛品", AccountType.ASSET, AccountCategory.CURRENT_ASSET);

    /** 契約資産。履行済みだが、まだ請求できない対価(債権以外の権利)。 */
    public static final Account CONTRACT_ASSETS =
            standard("145", "契約資産", AccountType.ASSET, AccountCategory.CURRENT_ASSET);

    /** 契約負債。履行前に受け取った対価。 */
    public static final Account CONTRACT_LIABILITIES =
            standard("222", "契約負債", AccountType.LIABILITY, AccountCategory.CURRENT_LIABILITY);

    /** 返金負債。売上割戻しなど、顧客に返金すると見込まれる額。 */
    public static final Account REFUND_LIABILITIES =
            standard("223", "返金負債", AccountType.LIABILITY, AccountCategory.CURRENT_LIABILITY);

    public static final Account SERVICE_REVENUE =
            standard("414", "役務収益", AccountType.REVENUE, AccountCategory.SALES);

    public static final Account COST_OF_SERVICES =
            standard("540", "役務原価", AccountType.EXPENSE, AccountCategory.COST_OF_SALES);

    // ---- 連結会計(フェーズ14)。連結修正仕訳だけで使い、個別の帳簿には出てこない ----

    /** 非支配株主持分。子会社の純資産のうち、親会社以外の株主に帰属する部分。 */
    public static final Account NON_CONTROLLING_INTERESTS =
            standard("308", "非支配株主持分", AccountType.EQUITY, AccountCategory.NON_CONTROLLING_INTERESTS);

    /** 非支配株主に帰属する当期純利益。子会社の当期純利益のうち非支配株主の持分。純損失なら貸方に出る。 */
    public static final Account PROFIT_ATTRIBUTABLE_TO_NON_CONTROLLING_INTERESTS =
            standard("541", "非支配株主に帰属する当期純利益", AccountType.EXPENSE,
                    AccountCategory.NON_CONTROLLING_INTERESTS_PROFIT);

    /** 損益勘定。決算の損益振替専用で、B/S・P/L には出さない。保護される標準科目(最低限)の一つ。 */
    public static final Account PROFIT_AND_LOSS =
            standard("901", "損益", AccountType.EQUITY, AccountCategory.PROFIT_AND_LOSS);

    /**
     * 現金過不足。実際の手許現金と現金勘定の帳簿残高が一致しないときの一時的な勘定。
     * 原因判明時に正しい勘定へ振替え、決算までに原因不明の分を雑損・雑収入へ振替えて必ずゼロにする。
     * category は SUSPENSE なので B/S・P/L には出さない。
     */
    public static final Account CASH_OVER_SHORT =
            standard("902", "現金過不足", AccountType.ASSET, AccountCategory.SUSPENSE);

    public static final List<Account> ALL = List.of(
            CASH, ACCOUNTS_RECEIVABLE, MERCHANDISE_INVENTORY, ALLOWANCE_FOR_DOUBTFUL_ACCOUNTS,
            PREPAID_EXPENSE, ACCRUED_REVENUE, CONSUMPTION_TAX_REFUNDABLE, EQUIPMENT,
            ACCUMULATED_DEPRECIATION_EQUIPMENT, CONSUMPTION_TAX_PAID, PREPAID_CORPORATE_TAX, OTHER_RECEIVABLES,
            NOTES_RECEIVABLE, TRADING_SECURITIES,
            ACCOUNTS_PAYABLE, LOANS_PAYABLE, UNEARNED_REVENUE, ACCRUED_EXPENSE,
            CONSUMPTION_TAX_RECEIVED, CONSUMPTION_TAX_PAYABLE, ACCRUED_CORPORATE_TAX, NOTES_PAYABLE,
            DIVIDENDS_PAYABLE,
            CAPITAL_STOCK, RETAINED_EARNINGS, CAPITAL_SURPLUS, STATUTORY_RESERVE,
            SALES, ALLOWANCE_FOR_DOUBTFUL_ACCOUNTS_REVERSAL, GAIN_ON_DISPOSAL_OF_FIXED_ASSETS, MISCELLANEOUS_INCOME,
            GAIN_ON_SALE_OF_SECURITIES, UNREALIZED_GAIN_ON_SECURITIES,
            PURCHASES, DEPRECIATION_EXPENSE, BAD_DEBT_EXPENSE, CORPORATE_TAX_EXPENSE, INSURANCE_EXPENSE,
            LOSS_ON_DISPOSAL_OF_FIXED_ASSETS, LOSS_ON_NOTE_DISCOUNTING, MISCELLANEOUS_LOSS,
            LOSS_ON_SALE_OF_SECURITIES, UNREALIZED_LOSS_ON_SECURITIES,
            PROFIT_AND_LOSS, CASH_OVER_SHORT,
            CHECKING_ACCOUNT, ORDINARY_DEPOSIT, TIME_DEPOSIT, PETTY_CASH, ADVANCE_PAYMENTS, SUSPENSE_PAYMENTS,
            REIMBURSABLE_ADVANCES, LOANS_RECEIVABLE, ELECTRONICALLY_RECORDED_RECEIVABLES, CREDIT_CARD_RECEIVABLES,
            GIFT_CERTIFICATES_RECEIVED, SUPPLIES, BUILDINGS, ACCUMULATED_DEPRECIATION_BUILDINGS, VEHICLES,
            ACCUMULATED_DEPRECIATION_VEHICLES, LAND, GUARANTEE_DEPOSITS, BANK_OVERDRAFT, OTHER_PAYABLES,
            ADVANCES_RECEIVED, SUSPENSE_RECEIPTS, INCOME_TAX_WITHHELD, SOCIAL_INSURANCE_WITHHELD,
            ELECTRONICALLY_RECORDED_PAYABLES, INTEREST_INCOME, DIVIDEND_INCOME, RENT_INCOME, RECOVERY_OF_BAD_DEBTS,
            SALARIES, LEGAL_WELFARE_EXPENSE, RENT_EXPENSE, COMMUNICATION_EXPENSE, UTILITIES_EXPENSE,
            SUPPLIES_EXPENSE, TAXES_AND_DUES, COMMISSION_EXPENSE, SHIPPING_EXPENSE, ADVERTISING_EXPENSE,
            TRAVEL_EXPENSE, BAD_DEBT_LOSS, INTEREST_EXPENSE,
            MERCHANDISE, COST_OF_GOODS_SOLD, INVENTORY_SHRINKAGE_LOSS, INVENTORY_VALUATION_LOSS,
            PROVISION_FOR_BONUSES, PROVISION_FOR_REPAIRS, PROVISION_FOR_PRODUCT_WARRANTIES,
            PROVISION_FOR_RETIREMENT_BENEFITS, BONUS_PROVISION_EXPENSE, REPAIR_PROVISION_EXPENSE,
            WARRANTY_PROVISION_EXPENSE, RETIREMENT_BENEFIT_EXPENSE, BONUSES, REPAIR_EXPENSE, WARRANTY_EXPENSE,
            BONUS_PROVISION_REVERSAL, REPAIR_PROVISION_REVERSAL, WARRANTY_PROVISION_REVERSAL,
            CONSTRUCTION_IN_PROGRESS, PREPAID_INTEREST, SOFTWARE, GOODWILL, NON_TRADE_NOTES_PAYABLE,
            GAIN_ON_GOVERNMENT_SUBSIDIES, LOSS_ON_RETIREMENT_OF_FIXED_ASSETS, LOSS_ON_ABANDONMENT_OF_FIXED_ASSETS,
            LOSS_ON_COMPRESSION_OF_FIXED_ASSETS, SOFTWARE_AMORTIZATION, GOODWILL_AMORTIZATION,
            HELD_TO_MATURITY_BONDS, SUBSIDIARY_STOCKS, AFFILIATE_STOCKS, AVAILABLE_FOR_SALE_SECURITIES,
            VALUATION_DIFFERENCE_ON_AVAILABLE_FOR_SALE_SECURITIES, INTEREST_ON_SECURITIES,
            DISHONORED_NOTES, NON_TRADE_NOTES_RECEIVABLE, LOSS_ON_SALE_OF_ELECTRONIC_RECEIVABLES,
            OTHER_CAPITAL_SURPLUS, GENERAL_RESERVE, GAIN_ON_BARGAIN_PURCHASE,
            WORK_IN_PROCESS, CONTRACT_ASSETS, CONTRACT_LIABILITIES, REFUND_LIABILITIES, SERVICE_REVENUE,
            COST_OF_SERVICES,
            NON_CONTROLLING_INTERESTS, PROFIT_ATTRIBUTABLE_TO_NON_CONTROLLING_INTERESTS
    );

    private StandardAccounts() {
    }

    private static Account standard(String code, String name, AccountType type, AccountCategory category) {
        return new Account(AccountCode.of(code), name, type, category, Set.of(), true, true);
    }
}
