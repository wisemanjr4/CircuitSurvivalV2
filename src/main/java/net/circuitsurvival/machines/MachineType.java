package net.circuitsurvival.machines;

import org.bukkit.Material;

public enum MachineType {
    CRUSHER    (Material.GRINDSTONE,        "粉砕機",    "鉱石を粉砕して製錬効率2倍"),
    COMPRESSOR (Material.STONECUTTER,       "圧縮機",    "素材とブロックを一括変換"),
    TIMER      (Material.DAYLIGHT_DETECTOR, "タイマー",  "周期設定可能なレッドストーンクロック"),
    BLOCK_PLACER       (Material.BARREL,              "ブロック設置装置",         "前面にブロックを自動設置 (RS信号で動作)"),
    BLOCK_BREAKER      (Material.BLAST_FURNACE,       "ブロック破壊装置",         "前面のブロックを自動破壊・収集 (RS信号で動作)"),
    AUTO_CRAFTER       (Material.CRAFTING_TABLE,      "自動クラフター",           "内蔵レシピグリッドで材料から自動クラフト (RS通電で連続動作)"),
    IGNITER            (Material.CAMPFIRE,             "着火装置",                "RS立ち上がりで前面ブロックに着火・点火"),
    AUTO_SMELTER       (Material.FURNACE,              "自動精錬炉",              "入力スロットのアイテムを自動精錬する"),
    MINER              (Material.CHISELED_DEEPSLATE,   "採掘機",                  "RS立ち上がりで前面を採掘し内部収集"),
    VACUUM_HOPPER      (Material.AMETHYST_BLOCK,       "バキュームホッパー",      "周辺の落ちアイテムを自動吸引・収集。RS通電で動作停止"),
    ITEM_SORTER        (Material.DROPPER,                    "アイテムソーター",        "内部バッファのアイテムを種別ごとに隣接チェストへ自動仕分け"),
    AUTO_FARMER        (Material.COMPOSTER,                  "自動農場",                "真下3x3の成熟作物を自動収穫・再植え (RS通電で連続動作)"),
    RIGHT_CLICKER      (Material.DISPENSER,                  "右クリック代行装置",  "インベントリのアイテムでRS立ち上がり時に前面ブロックへ右クリック動作"),
    ENTITY_DETECTOR    (Material.SCULK_SENSOR,               "エンティティ検知機",  "半径8ブロック内のエンティティを検知してRS信号を南面に出力"),
    CHUNK_LOADER       (Material.LODESTONE,                   "チャンクローダー",    "設置チャンクを常時ロード状態に保ち自動化ラインが止まらない"),
    AUTO_BREWER        (Material.CAULDRON,                    "自動醸造機",          "スロット25=材料, スロット26=燃料(ブレイズパウダー), RS通電で自動醸造"),
    ITEM_ROUTER        (Material.TARGET,                      "アイテムルーター",    "N/S/E/W方向別フィルタースロット(0-3)でアイテムを振り分ける。バッファ:4-26"),
    BLOCK_TRANSMUTER   (Material.SMOKER,                      "ブロック変換炉",      "RS通電で前面素材を変換(砂→ガラス, 丸石→石→滑石, 砂利→石等)"),
    FLUID_COLLECTOR    (Material.RESPAWN_ANCHOR,              "流体コレクター",      "RS通電で隣接する水源・溶岩源をバケツに自動収集(スロット0=空バケツ, 1-26=出力)"),
    BONE_MEALER        (Material.BONE_BLOCK,                  "自動骨粉散布機",      "RS通電中、真下5×5範囲の育成可能ブロックに骨粉を自動散布する"),
    VACUUM_HOPPER_CTRL (Material.CHISELED_STONE_BRICKS,      "バキュームフィルター","隣接するバキュームホッパーに設置。フィルタースロットに入れたアイテム種のみ吸引する"),
    STORAGE_DRUM       (Material.DEEPSLATE_BRICKS,           "大容量ストレージ",    "54スロットの大型保管庫。パイプと接続して大量アイテムを管理"),
    STORAGE_CONTROLLER (Material.ENCHANTING_TABLE,           "ストレージコントローラー","半径8ブロック内の大容量ストレージを開閉時に自動集約・分配する集積端末(54スロット)"),
    WOODCUTTER         (Material.SMITHING_TABLE,            "製材機",              "原木を効率的に板材へ加工する。RS通電で連続稼働"),
    ENERGY_CABLE       (Material.IRON_BARS,                "送電ワイヤー",        "EN(エネルギー)を隣接ブロック間で転送する導線。RS信号不要"),
    HV_CELL            (Material.DIAMOND_BLOCK,            "高圧蓄電機",          "EN(エネルギー)を最大200,000まで貯蔵する上位蓄電機"),
    AUTO_ENCHANTER     (Material.OBSIDIAN,                 "自動エンチャンター",  "経験値+ラピスラズリ+ENでツールにランダムエンチャントを付与"),
    VOID_MINER         (Material.CRYING_OBSIDIAN,          "虚空採掘機",          "膨大なENを消費して鉱石を生成するエンドゲーム採掘機"),
    XP_CONVERTER       (Material.SCULK_CATALYST,            "経験値変換炉",        "RS通電中、スロット内の素材を消費して経験値オーブを生成する"),
    FAST_HOPPER        (Material.LECTERN,                  "高速ホッパー",        "バニラの4倍速(2tick周期)で前面へ排出・背面から吸引(向き依存)。RS通電で停止"),
    VERT_FAST_HOPPER   (Material.CUT_COPPER,               "縦型高速ホッパー",    "バニラの4倍速(2tick周期)で上から吸引→下へ排出。RS通電で停止"),
    BULK_DROPPER       (Material.PISTON,                    "高速排出装置",        "RS通電中、スロット内のアイテムを高速で真下へ排出(下コンテナ優先→なければドロップ)"),
    AUTO_FISHER        (Material.PRISMARINE,                "自動釣り機",          "RS通電中、半径2ブロック以内に水があれば5秒ごとにバニラ釣りLTでアイテムを生成"),
    VERTICAL_ELEVATOR  (Material.COPPER_BLOCK,             "垂直エレベーター",    "RS信号で1ブロック上のコンテナへアイテムを搬送。積み重ねて高所輸送を構築"),
    COOKING_STATION    (Material.LOOM,                     "料理台",              "スロット0=食材を入れてボタンで特製料理アイテムを生成する"),
    PIXEL_FORGE        (Material.FLETCHING_TABLE,          "ピクセル鍛冶炉",      "スロット0=ベース武器, スロット1=改造素材を入れてボタンで特製武器を鍛造する"),
    CUSTOM_CRAFTER     (Material.CRAFTING_TABLE,             "カスタムクラフター",  "スロット0-8の3x3グリッドに中間素材を配置しRS通電で自動クラフト。複雑な部品を量産"),
    PULVERIZER         (Material.CHISELED_BOOKSHELF,       "粉砕精錬機",          "粉砕機の上位版。鉱石を3倍の効率で処理する。RS通電で連続稼働"),
    ELECTRIC_FURNACE   (Material.BLAST_FURNACE,            "電気精錬炉",          "自動精錬炉の高速版。1tickで精錬完了。RS通電で連続稼働"),
    AUTO_ANVIL         (Material.ANVIL,                    "自動金床",            "スロット0=ツール, スロット1=素材, RS通電で自動修復・結合"),
    SOLAR_PANEL        (Material.SHROOMLIGHT,              "日照発電機",          "昼間かつ屋外で10EN/tick発電。南面にRS信号出力"),
    AUTO_SHEARER       (Material.OBSERVER,                 "自動羊毛刈り機",      "RS通電中、周囲3ブロックの羊の毛を自動刈り取り内部収集する"),
    GENERATOR          (Material.IRON_BLOCK,               "発電機",              "石炭・溶岩などを燃料にEN(エネルギー)を生成する"),
    ENERGY_CELL        (Material.LAPIS_BLOCK,              "蓄電機",              "EN(エネルギー)を最大50,000まで貯蔵・隣接ブロックに分配する"),
    CHARGER            (Material.CARTOGRAPHY_TABLE,        "充電器",              "バッテリーのEN充電・放電を行う"),
    INDUCTION_FURNACE  (Material.RAW_IRON_BLOCK,           "誘導溶解炉",          "3倍同時精錬を行う上位電気炉。RS通電で連続稼働"),
    CENTRIFUGE         (Material.COPPER_BLOCK,             "遠心分離機",          "ブロックを遠心分離して副産物を取り出す。RS通電で連続稼働"),
    COMBUSTION_GENERATOR (Material.NETHER_BRICKS,          "燃焼発電機",          "発電機の上位版。燃料効率が2倍。RS通電で発電"),
    ORE_PROCESSOR      (Material.DARK_PRISMARINE,          "鉱石三段加工機",      "原鉱石を直接精錬し4倍のインゴットを生成。大量EN消費"),
    MATERIALIZER       (Material.RAW_COPPER_BLOCK,         "物質生成機",          "EN+中間素材で特定リソースを量産する"),
    RECYCLER           (Material.CHISELED_NETHER_BRICKS,   "リサイクル機",        "不要アイテムを素材に分解する"),
    WIRELESS_CHARGER   (Material.END_STONE,                "ワイヤレス充電器",    "半径5ブロック内の機械にENを無線転送"),
    AUTO_DISENCHANTER  (Material.JUKEBOX,                  "自動解呪機",          "エンチャントアイテムからエンチャを本に抽出"),
    THERMAL_GENERATOR  (Material.MUD_BRICKS,               "熱発電機",            "HEATING_COIL+COOLANT_CELLで熱落差発電。高温biomeほど高効率"),
    ADVANCED_ASSEMBLER (Material.CHISELED_QUARTZ_BLOCK,    "高級組立機",           "HIGH_GEAR駆動で中間素材を自動クラフト。バッファから材料を自動補充"),
    NEUTRON_COMPRESSOR (Material.GOLD_BLOCK,               "中性子圧縮機",         "NEUTRON_REFLECTORで物質を超高圧縮。ブロック→高級素材に変換"),
    MOBILE_PLATFORM           (Material.IRON_TRAPDOOR,     "移動プラットフォーム",  "ピストンで押すと下のブロックごと移動。機械の運搬に"),
    BLOCK_CONTROL_ATTACHMENT  (Material.CHAIN,             "機器制御アタッチメント","方向がある機械に隣接設置し動作方向を変更する"),
    TRASH_CAN                 (Material.BARREL,            "ゴミ箱",                "入れたアイテムを完全に消去する"),
    WASHING_MACHINE           (Material.POLISHED_BASALT,   "洗浄機",                "水+ENで砂利等を洗浄し鉄塊などの素材を確率で取り出す"),
    CRAFTER_CONTROLLER        (Material.COMPARATOR,         "クラフター制御装置",    "周囲の自動クラフターを一括制御・高速化する中枢装置"),
    WATER_GENERATOR           (Material.PRISMARINE_BRICKS,  "水力発電機",            "隣接する水源から安定したENを生成する"),
    WIND_GENERATOR            (Material.LIGHTNING_ROD,      "風力発電機",            "高所ほど高出力。上空の強風でENを生成する"),
    DISTILLATION_TOWER        (Material.CAULDRON,            "蒸留塔",                "原油からケミカルオイル・ゴム・硫黄に分離する化学プラント"),
    CHEMICAL_REACTOR          (Material.BREWING_STAND,       "化学反応炉",            "ケミカルオイルと素材を反応させプラスチック・硫酸等を生成"),
    VACUUM_FURNACE            (Material.BLAST_FURNACE,       "真空溶解炉",            "先進合金を真空純化し超硬合金を精錬する"),
    HIGH_PRESSURE_PRESS       (Material.PISTON,              "高圧プレス機",          "石炭+硫酸から工業用ダイヤを超高圧合成する");

    public final Material blockMaterial;
    public final String displayName;
    public final String description;

    MachineType(Material blockMaterial, String displayName, String description) {
        this.blockMaterial = blockMaterial;
        this.displayName   = displayName;
        this.description   = description;
    }
}
