# CircuitSurvivalV2 設計書

## 現状の課題
- 中間素材が少なく、ヴォイドマイナーまで1日で到達できる
- 自動化が「任意」で終わっている（やらなくても困らない）
- レシピが単純（バニラ素材の組み合わせのみ）
- 発電方法が少ない
- 移動手段が乏しい

## 設計方針（V2の中核思想）

### 「自動化は任意だが、やらない理由がない」を目指す

**間違ったアプローチ:**
「自動化しないと先に進めない」→ 強制されてる感、作業感

**正しいアプローチ:**
- 手動でも生産自体は可能。ただし**効率が圧倒的に違う**
- 自動化のセットアップ自体がパズルとして面白い
- 一度組んだラインは自動で動き続ける → 達成感
- 工場を拡張するほどリターンが加速度的に増える
- 結果として**自動化したくなる**、**自動化が楽しい**

### 効率設計の考え方

| 生产方式 | 効率倍率 | 手間 |
|---------|---------|------|
| 手動クラフト | 1x | 毎回素材を揃えてクラフト |
| 手動機械(CRUSHER等) | 1-2x | クリック毎に処理、その場を離れられない |
| 単一自動化 | 4-8x | セットアップ後は放置可能 |
| 連鎖自動化(パイプ接続) | 16x+ | 大規模工場でさらに加速 |
| 完全自動化(全行程) | 32x+ | 素材投入→完成品が自動出力 |

**重要:** 手動でも1個くらいは作れる。でも大量生産しようと思ったら、どう考えても自動化したほうが早い。そして自動化ラインを構築する工程そのものがゲームプレイになる。

### 長期プレイのための設計
- 機械1台では足りない → 増設が必要になる生産量設計
- EN供給がボトルネック → 発電所を拡張する必要
- 大量生産にパイプ輸送が必須 → 物流設計が発生
- 後半になるほど中間素材の種類が増える → 工場が自然に大規模化

---

## 素材チェーン設計

### Chain 0: 入門 (バニラ周辺)
手動クラフトで作れる最初の機械たち。
```
素材                                 -> 機械
COBBLESTONE + STICK + IRON_INGOT     -> CRUSHER        (粉砕機)
COBBLESTONE + IRON_BLOCK             -> COMPRESSOR     (圧縮機)
FURNACE + HOPPER                     -> AUTO_SMELTER   (自動精錬機)
```
この時点では手動で機械をポチポチ操作。めんどくさいけどなんとかなる。

### Chain 1: ベーシック中間素材
CRUSHER/COMPRESSORで生産。ワークベンチでは不可能。
```
CRUSHER:  IRON_ORE          -> CRUSHED_IRON x3          (手動: 3個)
CRUSHER:  GOLD_ORE          -> CRUSHED_GOLD x3
CRUSHER:  COAL_ORE          -> CRUSHED_COAL x2
CRUSHER:  COBBLESTONE       -> CRUSHED_STONE x2
CRUSHER:  NETHERRACK        -> CRUSHED_NETHERRACK x2
COMPRESSOR: IRON_INGOT x9   -> COMPRESSED_IRON x1       (9:1圧縮)
COMPRESSOR: GOLD_INGOT x9   -> COMPRESSED_GOLD x1
```
CRUSHERは手動/RSの両対応（RS繋げば自動）。ただし1回の処理で3個しか出ない。
後半のPULVERIZER(自動+EN)は1回で12個処理→差が如実に。

AUTO_SMELTERでの精錬チェーン:
```
CRUSHED_IRON + 燃料(CRUSHED_COAL) -> REFINED_IRON_INGOT x1
CRUSHED_GOLD + 燃料(CRUSHED_COAL) -> REFINED_GOLD_INGOT x1
```
燃料としてCRUSHED_COALを使うと効率2倍（普通の石炭の2倍燃える）。
→ 「粉砕→精錬」の自動化ラインを組みたくなる設計。

### Chain 2: エネルギー基盤
RS信号に加えてENが必要な時代に突入。
```
素材                                             -> 機械
REFINED_IRON_INGOT x3 + FURNACE + PISTON + REDSTONE -> GENERATOR (燃焼発電機)
GLASS x4 + ENERGY_CABLE + REDSTONE_BLOCK x2        -> ENERGY_CELL (蓄電池)
IRON_BARS x6 + REDSTONE x2                         -> ENERGY_CABLE (送電線)
GLASS x4 + REFINED_IRON_INGOT x3 + DAYLIGHT_DETECTOR -> SOLAR_PANEL (太陽光)
REFINED_IRON_INGOT x4 + FURNACE + PISTON + REDSTONE_BLOCK x2 -> COMBUSTION_GENERATOR (高効率)
```

**処理速度の差をここで見せる例:**
- CRUSHER (手動): 1回3秒、3個出力
- CRUSHER (RS自動): 1回5tick、3個出力 → 手動より早いけどENは使わない
- PULVERIZER (EN自動): 1回10tick、12個出力 → EN使うけど量が圧倒的

同じ「粉砕」でも、ENを使う機械のほうが桁違いに効率が良い。
→ EN発電所を作って、EN貯めて、EN機械に繋ぎたくなる。

#### 発電バリエーション

| 発電機 | 燃料 | EN/tick | 解放時期 | 特徴 |
|--------|------|---------|---------|------|
| GENERATOR | 石炭/木炭/溶岩 | 1 | Chain 2 | 最初の発電機。とにかく動く |
| SOLAR_PANEL | 日光 | 2(昼)/0(夜) | Chain 2 | タダだけど不安定 |
| WATER_GENERATOR | 水流+設置 | 3 | Chain 3 | 安定してるが設置条件きつい |
| WIND_GENERATOR | 風(高さ依存) | 1-5 | Chain 3 | 高出力だが変動が大きい |
| COMBUSTION_GENERATOR | 石炭/CRUSHED_COAL/溶岩/ブレイズロッド | 5 | Chain 3-4 | 中盤の主力。CRUSHED_COAL使うと効率1.5倍 |
| THERMAL_GENERATOR | 溶岩(ネザー永続) | 10 | Chain 5 | エンドゲーム。ネザー設置で半永続 |

**ポイント:** 序盤はGENERATOR+ENERGY_CELLでしのぎ、中盤でCOMBUSTION_GENERATORに移行。
大型工場には複数台の発電機が必要 → 発電所を作る楽しみ。

### Chain 3: 機械部品製造ライン
ここから**自動化の面白さが本格化**する。
```
PULVERIZER: COMPRESSED_IRON x2 + REDSTONE x4 + FURNACE + REFINED_IRON_INGOT x3
  → CRUSHED_ORE x12 / 回 (CRUSHERの4倍効率! ただしEN消費)
  → パルス受信で起動、EN消費

CENTRIFUGE: REFINED_IRON_INGOT x4 + PISTON x2 + REDSTONE_BLOCK x2
  → CRUSHED_OREを純金属抽出物に分離
  → CRUSHED_IRON x4 -> PURE_IRON_EXTRACT x1 (4:1、かなり効率悪い)
  → 後段でORE_PROCESSORに繋ぐと化ける

INDUCTION_FURNACE: REFINED_IRON_INGOT x4 + FURNACE + BLAST_FURNACE + CRUSHED_COAL x4
  → REFINED_INGOT + CRUSHED_COAL -> STEEL_INGOT x2
  → PURE_EXTRACT同士を合金化
```

**ここで作れる中間素材たち:**
```
PURE_IRON_EXTRACT      (CENTRIFUGE: CRUSHED_IRON x4 から)
PURE_GOLD_EXTRACT      (CENTRIFUGE: CRUSHED_GOLD x4 から)
STEEL_INGOT            (INDUCTION_FURNACE: REFINED_IRON x2 + CRUSHED_COAL x1 → STEEL_INGOT x2)
CIRCUIT_BOARD          (手動クラフト可だけど大量消費: PURE_GOLD_EXTRACT + REDSTONE x4 + REFINED_IRON_INGOT)
                          → AUTO_CRAFTERにやらせると圧倒的に早い
ADVANCED_ALLOY         (INDUCTION_FURNACE: STEEL_INGOT x2 + PURE_IRON_EXTRACT x1 → x2)
EN_CELL_COMPONENT      (ENERGY_CELLをレシピ消費して生産)
PIPE_CONNECTOR         (手動: STEEL_INGOT + REDSTONE x4、16個生産)
```

**設計の肝:** CIRCUIT_BOARDは手動でも1個ずつ作れる。
でもAUTO_CRAFTER + CRAFTER_CONTROLLER で自動化すると、素材を突っ込むだけで連続生産。
「手動でも作れなくはないけど、自動化したほうが100倍ラク」という感覚をここで徹底する。

### Chain 4: 自動化機械の量産時代
Chain 3の中間素材を大量に消費する時代。
```
AUTO_CRAFTER:        DISPENSER + CRAFTING_TABLE + CIRCUIT_BOARD x2 + PISTON x2 + REDSTONE
CRAFTER_CONTROLLER:  AUTO_CRAFTER + REPEATER x2 + COMPARATOR x2 + CIRCUIT_BOARD x3
ADVANCED_ASSEMBLER:  AUTO_CRAFTER x2 + CIRCUIT_BOARD x3 + ADVANCED_ALLOY x3 + EN_CELL_COMPONENT x2
PIPE:                IRON_BARS x4 + PIPE_CONNECTOR x1 (8本)
FILTER_PIPE:         PIPE x2 + HOPPER + CIRCUIT_BOARD
ITEM_ROUTER:         DISPENSER + CIRCUIT_BOARD x4 + COMPARATOR + REPEATER + COMPRESSED_IRON x2
ORE_PROCESSOR:       PULVERIZER + CENTRIFUGE + INDUCTION_FURNACE + ADVANCED_ALLOY x3
                       → 鉱石1個からPURE_EXTRACTまで一貫処理 (4倍効率!)
MATERIALIZER:        DISPENSER + EN_CELL_COMPONENT x3 + CIRCUIT_BOARD x4 + ADVANCED_ALLOY x2 + DIAMOND
                       → ENを消費してアイテムを生成
RECYCLER:            DISPENSER + PISTON x2 + COMPRESSED_IRON x4 + CIRCUIT_BOARD x2 + OBSIDIAN
                       → 不要アイテムを素材に戻す
```

この段階で「とりあえずAUTO_CRAFTERを何台も並べて工場を作る」のが普通になる。
パイプで機械間を接続 → 素材の流れを設計する楽しさ。

### Chain 5: エンドゲーム素材
```
NEUTRON_COMPRESSOR:  ADVANCED_ALLOY x4 + EN_CELL_COMPONENT x3 + CIRCUIT_BOARD x4 + DIAMOND_BLOCK x2 + NETHER_STAR
VOID_MINER:          NEUTRON_COMPRESSOR + ADVANCED_ALLOY x4 + EN_CELL_COMPONENT x3 + DIAMOND_BLOCK x2 + OBSIDIAN x16
AUTO_ENCHANTER:      DISPENSER + CIRCUIT_BOARD x3 + EN_CELL_COMPONENT x2 + ADVANCED_ALLOY + BOOK
XP_CONVERTER:        EN_CELL_COMPONENT x2 + CIRCUIT_BOARD x2 + BOTTLE_O_ENCHANTING x4 + REFINED_GOLD_INGOT x2
```

NEUTRON_COMPRESSORのレシピ例（超圧縮）:
```
STEEL_INGOT x64 + PURE_GOLD_EXTRACT x32 -> NEUTRON_INGOT x1      (64+32→1!)
NETHER_STAR x4                          -> COMPRESSED_STAR x1     (4→1)
DIAMOND_BLOCK x8 + PURE_IRON_EXTRACT x16 -> NEUTRON_DIAMOND x1   (72→1)
```
大量の素材を消費する → 大規模工場が必要 → 自動化フル稼働

### Chain 6: 最強装備
```
DARK_SABER:      NETHERITE_SWORD + COMPRESSED_STAR + NEUTRON_INGOT x2
PIXEL_GUN:       CROSSBOW + AMETHYST_SHARD + CIRCUIT_BOARD x3 + ADVANCED_ALLOY x2
GRENADE_LAUNCHER: BOW + FIRE_CHARGE x4 + ADVANCED_ALLOY x2 + COMPRESSED_IRON x3
SNIPER_RIFLE:    CROSSBOW + GLASS x2 + PURE_IRON_EXTRACT x4 + ADVANCED_ALLOY x2 + EN_CELL_COMPONENT
CHEF_KNIFE:      IRON_SWORD + GOLD_INGOT x2 + PURE_GOLD_EXTRACT x2
```

---

## 「自動化が面白い」を体現する具体施策

### 1. 倍率で魅せる
手動CRUSHER: 1回3個
PULVERIZER(自動+EN): 1回12個、さらにORE_PROCESSOR(完全自動): 1回24個+副産物

「手動でポチポチするより自動化したほうが圧倒的に儲かる」
→ 一度自動化を体験すると戻れなくなる

### 2. セットアップのパズル性
- パイプの向きを考えて配置する
- ENケーブルの引き回しを設計する
- どの機械にどの素材を流すか Routing を組む
- 赤石コンパレータで在庫管理する

### 3. 動く工場の達成感
- パイプをアイテムが流れる様子が視覚的に楽しい
- 複数機械が連鎖して動く様子が見える
- 「これ全部自分で設計した」という満足感

### 4. 規模の経済
- 同じ機械を2台並べると処理量2倍
- ただしEN消費も2倍 → 発電所の増強が必要
- パイプ1本では運びきれない → パイプの並列化
- 結果として「工場が成長していく」体験

---

## 移動手段

### 1. EN高速レール
- POWERED_RAIL + EN_CELL_COMPONENT + PURE_GOLD_EXTRACT x2 (8個)
- EN給電で速度3倍
- 拠点間の高速移動に。ただし線路の敷設は自分でやる

### 2. EN跳躍ブーツ
- IRON_BOOTS + EN_CELL_COMPONENT + ADVANCED_ALLOY + PISTON + SLIME_BLOCK
- EN内蔵（ENERGY_CELLで充電）
- スニーク+ジャンプでEN消費→高跳び
- 垂直移動に便利。工場の階移動とか

### 3. ホームアンカー（帰還装置）
- EN_CELL_COMPONENT x2 + ENDER_PEARL x2 + COMPRESSED_IRON x4 + REDSTONE_BLOCK x2
- 設置時にその場所を記憶
- どこからでも右クリックで帰還（エンダーパール1個 + EN200消費）
- 「遠くの鉱山から拠点に帰る」ユースケース

### 4. ENテレポーター
- EN_CELL_COMPONENT x4 + ENDER_PEARL x8 + CIRCUIT_BOARD x3 + DIAMOND_BLOCK x1
- 2台1組で設置。AからBへ瞬間移動
- 使用ごとにエンダーパール1個 + EN500消費
- エンドゲームの大規模移動手段
- 燃料(エンダーパール)の自動供給も自動化できる → 完全自動テレポート網も夢じゃない

---

## 実装優先順位

### Phase 1: 中間素材 + レシピ差し替え（Chain 0-2）
- CRUSHED_ORE x3種、REFINED_INGOT x2種、COMPRESSED_INGOT x2種の追加
- CRUSHER/COMPRESSOR/AUTO_SMELTER のレシピとタスクを一新
- 発電機3種のレシピに中間素材を要求するよう変更
- 既存全機械のレシピを中間素材ベースに置き換え

### Phase 2: 部品製造ライン（Chain 3-4）
- PULVERIZER/CENTRIFUGE/INDUCTION_FURNACE 実装
- CIRCUIT_BOARD/PIPE_CONNECTOR/ADVANCED_ALLOY/STEEL_INGOT 追加
- AUTO_CRAFTER/ADVANCED_ASSEMBLER のレシピ更新
- PIPE/FILTER_PIPE/ITEM_ROUTER のレシピ更新

### Phase 3: エンドゲーム（Chain 5-6）
- NEUTRON_COMPRESSOR の超圧縮レシピ実装
- VOID_MINER/AUTO_ENCHANTER/XP_CONVERTER レシピ更新
- 最強装備レシピ追加

### Phase 4: 移動 + 発電バリエーション
- 高速レール / 跳躍ブーツ / ホームアンカー / テレポーター
- WATER_GENERATOR / WIND_GENERATOR
