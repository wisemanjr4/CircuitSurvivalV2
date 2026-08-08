package net.circuitsurvival.guide;

import net.circuitsurvival.CircuitSurvivalPlugin;
import net.circuitsurvival.items.CustomItems;
import net.circuitsurvival.items.IntermediateMaterials;
import net.circuitsurvival.machines.MachineType;
import net.circuitsurvival.recipes.CustomCrafterRecipe;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class GuideManager implements Listener {

    private static final String PREFIX = "§8[§6CSガイド§8] ";
    private static GuideManager instance;
    private static org.bukkit.plugin.Plugin pluginRef;
    public static org.bukkit.NamespacedKey GUIDE_BOOK_KEY;
    private static org.bukkit.NamespacedKey PROCESS_RECIPE_KEY;

    public static GuideManager init(org.bukkit.plugin.Plugin plugin) {
        if (instance == null) {
            instance = new GuideManager();
            pluginRef = plugin;
            GUIDE_BOOK_KEY = new org.bukkit.NamespacedKey(plugin, "guide_book");
            PROCESS_RECIPE_KEY = new org.bukkit.NamespacedKey(plugin, "process_recipe");
            Bukkit.getPluginManager().registerEvents(instance, plugin);
        }
        return instance;
    }

    public static void open(Player player) { openMain(player); }

    /** ガイドブックアイテムを生成 */
    public static ItemStack createGuideBook() {
        ItemStack book = new ItemStack(Material.KNOWLEDGE_BOOK);
        ItemMeta m = book.getItemMeta();
        m.displayName(Component.text("§6§lCircuitSurvival ガイド"));
        m.lore(List.of(
                Component.text("§7右クリックでガイドGUIを開く"),
                Component.text("§8/cs guide book でも再取得可")
        ));
        m.getPersistentDataContainer().set(GUIDE_BOOK_KEY, org.bukkit.persistence.PersistentDataType.BOOLEAN, true);
        book.setItemMeta(m);
        return book;
    }

    /** ガイドブックか判定 */
    public static boolean isGuideBook(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer()
                .has(GUIDE_BOOK_KEY, org.bukkit.persistence.PersistentDataType.BOOLEAN);
    }

    // ---- 初回Join配布 ---------------------------------------------------------

    @EventHandler
    public void onJoin(org.bukkit.event.player.PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (player.hasPlayedBefore()) return;
        player.getInventory().addItem(createGuideBook());
        player.sendMessage(Component.text("§6[CS] §eCircuitSurvival ガイド§7を受け取りました！ §f/cs guide §7で再表示"));
    }

    // ---- ガイドブック右クリック -------------------------------------------------

    @EventHandler
    public void onBookInteract(org.bukkit.event.player.PlayerInteractEvent event) {
        if (event.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) return;
        ItemStack item = event.getItem();
        if (!isGuideBook(item)) return;
        event.setCancelled(true);
        open(event.getPlayer());
    }

    // ---- Holder ---------------------------------------------------------------

    private record GuideHolder(String page, Object data) implements InventoryHolder {
        @Override public @NotNull Inventory getInventory() { return null; }
    }

    // ---- Utility --------------------------------------------------------------

    private static ItemStack icon(Material mat, String name, String... lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta m = item.getItemMeta();
        m.displayName(Component.text(name));
        if (lore.length > 0) m.lore(Arrays.stream(lore).map(l -> (Component) Component.text(l, NamedTextColor.GRAY)).toList());
        item.setItemMeta(m);
        return item;
    }

    private static ItemStack pane(Component name) {
        ItemStack item = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta m = item.getItemMeta();
        m.displayName(name);
        item.setItemMeta(m);
        return item;
    }

    private static void fill(Inventory inv, int start, int end, ItemStack item) {
        for (int i = start; i <= end; i++) {
            if (inv.getItem(i) == null) inv.setItem(i, item.clone());
        }
    }

    // ==========================================================================
    // メインメニュー
    // ==========================================================================

    private static void openMain(Player player) {
        Inventory gui = Bukkit.createInventory(new GuideHolder("main", null), 27, "§6CircuitSurvival 進行ガイド");
        gui.setItem(4, icon(Material.ENCHANTED_BOOK, "§6§lCircuitSurvival ガイド",
                "§7全装置・レシピ・進行を確認できます",
                "§7クリックで各項目へ移動"));

gui.setItem(10, icon(Material.LADDER, "§a§lTier進行表",
                "§7Tier1→4までの進行フロー",
                "§7何から作ればいいか分かる"));
        gui.setItem(12, icon(Material.CRAFTING_TABLE, "§b§l中間素材レシピ",
                "§7カスタムクラフター用レシピ一覧",
                "§7素材の組み合わせを確認"));
        gui.setItem(14, icon(Material.REDSTONE_BLOCK, "§c§lエネルギー解説",
                "§7EN(エネルギー)システムの仕組み",
                "§7発電・蓄電・転送・消費フロー"));
        gui.setItem(16, icon(Material.FURNACE, "§e§l装置一覧",
                "§7全" + MachineType.values().length + "種の機械をTier別に表示",
                "§7機能・EN消費・レシピを確認"));
        gui.setItem(22, icon(Material.COMPASS, "§d§l初心者ガイド",
                "§7「何から始めればいい？」",
                "§7軌道に乗るまでの手順を解説"));

        fill(gui, 0, 26, pane(Component.text("")));
        player.openInventory(gui);
    }

    // ---- 初心者ガイド ---------------------------------------------------------

    private static void openGettingStarted(Player player, int page) {
        Inventory gui = Bukkit.createInventory(new GuideHolder("guide_steps", page), 54, "§6§l初心者ガイド (p" + (page + 1) + "/7)");
        fill(gui, 0, 53, pane(Component.text("")));
        gui.setItem(0, icon(Material.ARROW, "§c← 戻る"));
        gui.setItem(4, icon(Material.COMPASS, "§d§l軌道に乗るまでの手順"));

        switch (page) {
            case 0 -> {
                gui.setItem(19, icon(Material.CRAFTING_TABLE, "§6§lStep 1: 基本素材を集めよう",
                        "§7木・石・鉄を十分に集めてから始めよう"));
                gui.setItem(20, icon(Material.IRON_INGOT, "§6§lStep 2: 最初の装置を作ろう",
                        "§7粉砕機(CRUSHER): 鉱石→粉砕で2倍",
                        "§7圧縮機(COMPRESSOR): 素材↔ブロック変換",
                        "§7洗浄機(WASHING_MACHINE): 砂利→確率で鉄塊",
                        "§7丸石→粉砕→砂利→洗浄で無限鉄ライン"));
                gui.setItem(21, icon(Material.FURNACE, "§6§lStep 3: 自動精錬",
                        "§7自動精錬炉(AUTO_SMELTER)で燃料不要の精錬",
                        "§7粉砕鉄→かまど精錬で精錬鉄インゴット"));
                gui.setItem(22, icon(Material.HOPPER, "§6§lStep 4: 自動化の基礎",
                        "§7タイマー: RS信号を定期的に出力",
                        "§7ホッパー/パイプ: アイテムを搬送",
                        "§7バキュームホッパー: ドロップ品を回収"));
                gui.setItem(28, icon(Material.REPEATER, "§a§l中間素材の作成",
                        "§7回路基板/歯車/筐体(Tier1)→",
                        "§7演算処理/モーター/コイル(Tier2)→",
                        "§7高度回路/高級歯車(Tier2.5)→",
                        "§7ENコア/安定化装置(Tier3)→上位装置へ"));
                gui.setItem(34, icon(Material.GOLD_INGOT, "§e§l中間素材の進化",
                        "§7Tier1: §7回路基板 / 機械歯車 / 機械筐体",
                        "§7Tier2: §7演算処理 / モーター / 加熱コイル",
                        "§7Tier2.5: §7高度回路 / 高級歯車",
                        "§7Tier3: §7ENコア / 安定化装置"));
            }
            case 1 -> {
                gui.setItem(19, icon(Material.IRON_BLOCK, "§c§lStep 5: エネルギー(EN)導入",
                        "§7発電機(GENERATOR): 石炭を燃料にEN生成",
                        "§7水力発電機(WATER): 隣接水源で3EN/tick",
                        "§7風力発電機(WIND): 高所で1-6EN/tick",
                        "§7蓄電機(ENERGY_CELL): ENを貯蔵・分配"));
                gui.setItem(20, icon(Material.REDSTONE_BLOCK, "§c§lStep 6: 発電の自動化",
                        "§7発電機+ホッパー(燃料自動供給)→",
                        "§7蓄電機→送電ワイヤー→各機械",
                        "§7日照発電機(SOLAR_PANEL): 昼間10EN/tick"));
                gui.setItem(21, icon(Material.LAPIS_BLOCK, "§c§lStep 7: 中盤の自動化ライン",
                        "§7自動農場(AUTO_FARMER): 食料/資源の自動化",
                        "§7採掘機(MINER): ブロック自動採掘",
                        "§7アイテムソーター(ITEM_SORTER): 仕分け",
                        "§8すべてRS信号＋EN＋ホッパーで完全自動化"));
                gui.setItem(22, icon(Material.COMPARATOR, "§c§lStep 8: 中間素材の量産",
                        "§7粉砕精錬機(PULVERIZER): 3倍粉砕",
                        "§7遠心分離機(CENTRIFUGE): 粉砕鉱石→純抽出液",
                        "§7誘導溶解炉(INDUCTION): 合金(鋼鉄/先進合金)"));
                gui.setItem(28, icon(Material.DIAMOND_BLOCK, "§5§lStep 9: 高度な装置",
                        "§7電気精錬炉(ELECTRIC_FURNACE): 高速精錬",
                        "§7自動金床(AUTO_ANVIL): 修復/結合/エンチャ本合成",
                        "§7クラフター制御装置: 周囲のクラフターを遠隔制御"));
                gui.setItem(34, icon(Material.NETHERITE_INGOT, "§5§lStep 10: 上位発電",
                        "§7燃焼発電機(COMBUSTION_GENERATOR): 燃料2倍効率",
                        "§7熱発電機(THERMAL_GENERATOR): 熱落差で受動発電",
                        "§7高圧蓄電機(HV_CELL): 200,000EN貯蔵"));
            }
            case 2 -> {
                gui.setItem(19, icon(Material.OBSIDIAN, "§d§lStep 11: 高度素材の生産",
                        "§7中和子圧縮機: 64丸石→鉄、ブロック→塊",
                        "§7鉱石三段加工機(ORE_PROCESSOR): 4倍インゴット",
                        "§7物質生成機: EN消費で特定リソースを生成"));
                gui.setItem(20, icon(Material.END_STONE, "§d§lStep 12: ワイヤレス化",
                        "§7ワイヤレス充電器: 半径5に無線給電",
                        "§7高級組立機: バッファ素材を自動クラフト"));
                gui.setItem(21, icon(Material.GOLD_BLOCK, "§d§lStep 13: 物質変換",
                        "§7リサイクル機: 不要品を素材に分解",
                        "§7化学反応炉: ケミカルオイル→プラ/硫酸",
                        "§7蒸留塔: 原油→ケミカルオイル+ゴム+硫黄"));
                gui.setItem(22, icon(Material.DRAGON_BREATH, "§d§lStep 14: 大規模工場化",
                        "§7高速パイプで物流を2倍加速",
                        "§7一括生産レシピで装置の大量生産",
                        "§7マルチブロック配置で効率UPを目指せ"));
            }
            case 3 -> {
                gui.setItem(19, icon(Material.CRAFTING_TABLE, "§e§l参考: 鉄無限ライン",
                        "§7ブロック設置装置(水+溶岩)→丸石生成",
                        "§7粉砕機→砂利に変換",
                        "§7洗浄機→砂利を洗浄(20%で鉄塊)",
                        "§7ホッパー+水バケツ自動供給で完全自動化"));
                gui.setItem(20, icon(Material.WHEAT, "§e§l参考: 食料自動化",
                        "§7自動農場(畑の上に設置+RS)",
                        "§7+ 自動骨粉散布機で高速化",
                        "§7+ ホッパーで収穫物回収"));
                gui.setItem(21, icon(Material.CAULDRON, "§e§l参考: 化学製品ライン",
                        "§7深層岩採掘→原油(0.5%ドロップ)",
                        "§7蒸留塔→ケミカルオイル+ゴム+硫黄",
                        "§7化学反応炉→プラスチック+硫酸"));
                gui.setItem(22, icon(Material.EXPERIENCE_BOTTLE, "§e§l参考: 経験値自動化",
                        "§7経験値変換炉: 不要素材→経験値オーブ",
                        "§7+ 自動エンチャンター: エンチャント付与"));
            }
            case 4 -> {
                gui.setItem(19, icon(Material.CHAIN, "§b§lPhase 4: 物流拡張",
                        "§7高速パイプ: 通常の2倍速で搬送",
                        "§7高速入力パイプ: 2倍速で引き込み",
                        "§7高速出力パイプ: 高速終端",
                        "§7鋼鉄+パイプコネクタで作成"));
                gui.setItem(20, icon(Material.CRAFTING_TABLE, "§b§l一括生産レシピ",
                        "§7粉砕機x4: 鋼鉄+先進合金",
                        "§7パイプx16: 鋼鉄+パイプコネクタ",
                        "§7送電ワイヤーx16: 鋼鉄+銅",
                        "§8中間素材があると大量生産が可能！"));
                gui.setItem(21, icon(Material.HOPPER, "§b§lホッパー方向自動判別",
                        "§7洗浄機: 水バケツ→自動でスロット1へ",
                        "§7通常アイテム→スロット0へ",
                        "§7ホッパーで完全自動化が簡単に"));
                gui.setItem(22, icon(Material.COMPARATOR, "§b§l右クリック代行装置 強化",
                        "§7リピーター遅延/コンパレーター切替",
                        "§7日照センサー反転/音符ブロック/レバー",
                        "§7ボタン押下も対応"));
            }
            case 5 -> {
                gui.setItem(19, icon(Material.SLIME_BALL, "§2§lPhase 5: 化学処理",
                        "§7原油: 深層岩採掘で0.5%ドロップ",
                        "§7蒸留塔: 原油→ケミカルオイルx2",
                        "§7  +ゴム(30%) +硫黄(15%)"));
                gui.setItem(20, icon(Material.BREWING_STAND, "§2§l化学反応炉",
                        "§7ケミカルオイル+硫黄→硫酸x2",
                        "§7ケミカルオイル+ゴム→プラスチックx3",
                        "§7鋼鉄+ガラス+回路基板で作成"));
                gui.setItem(21, icon(Material.CAULDRON, "§2§l蒸留塔",
                        "§7原油を蒸留して化学素材に変換",
                        "§7鋼鉄+ガラス+加熱コイルで作成",
                        "§740EN/回, RS通電で自動稼働"));
                gui.setItem(22, icon(Material.LIME_DYE, "§2§l化学素材の活用",
                        "§7硫酸: 強力な触媒、上位装置に使用",
                        "§7プラスチック: 合成樹脂、絶縁部品に",
                        "§7ゴム: 弾力素材、パイプの強化に"));
            }
            case 6 -> {
                gui.setItem(19, icon(Material.ANVIL, "§5§lアップデート情報",
                        "§7自動金床: エンチャ本合成に対応",
                        "§7中間素材レシピを全面的に修正",
                        "§7  (バニラ素材→中間素材で本格進行)"));
                gui.setItem(20, icon(Material.COMPASS, "§5§lヒント: 次にやること",
                        "§7まずは粉砕機+圧縮機+精錬炉を作ろう",
                        "§7次に発電機+蓄電機+送電ワイヤーでEN網",
                        "§7中間素材で上位装置を解放していく"));
                gui.setItem(21, icon(Material.BOOK, "§5§lもっと詳しく",
                        "§7メインメニュー→機械一覧→各機械の",
                        "§7「処理レシピ一覧」で使えるレシピを確認",
                        "§7素材の作り方は「素材レシピ」を参照"));
                gui.setItem(22, icon(Material.ENDER_EYE, "§5§l将来のアップデート",
                        "§7Phase 6: 高度冶金(真空溶解炉・超硬合金)",
                        "§7Phase 7: 自動化深化(アップグレードモジュール)",
                        "§7Phase 8: 大規模発電(原子力・地熱)...続く"));
            }
        }

        if (page > 0) gui.setItem(45, icon(Material.ARROW, "§e← 前へ"));
        if (page < 6) gui.setItem(53, icon(Material.ARROW, "§e次へ →"));
        player.openInventory(gui);
    }

    // ==========================================================================
    // Tier進行表
    // ==========================================================================

    private static void openTierProgression(Player player) {
        Inventory gui = Bukkit.createInventory(new GuideHolder("tier", null), 54, "§6§lTier進行表");
        // タイトル行
        gui.setItem(4, icon(Material.LADDER, "§a§lTier進行フロー",
                "§7左から右へ進むほど高度な素材・装置"));

        // Tier1 (slot 9-11)
        gui.setItem(9,  icon(Material.GOLD_NUGGET, "§eTier 1 - 基本素材",
                "§7回路基板 / 機械歯車 / 機械筐体"));
        gui.setItem(10, IntermediateMaterials.build(IntermediateMaterials.CIRCUIT_BOARD));
        gui.setItem(11, IntermediateMaterials.build(IntermediateMaterials.MACHINE_GEAR));

        // Tier2 (slot 18-20)
        gui.setItem(18, icon(Material.GOLD_INGOT, "§eTier 2 - 応用部品",
                "§7演算処理装置 / モーター / 加熱コイル"));
        gui.setItem(19, IntermediateMaterials.build(IntermediateMaterials.PROCESSOR_UNIT));
        gui.setItem(20, IntermediateMaterials.build(IntermediateMaterials.MOTOR));

        // Tier2.5 (slot 27-29)
        gui.setItem(27, icon(Material.REPEATER, "§eTier 2.5 - 高度部品",
                "§7高度回路基板 / 高級歯車"));
        gui.setItem(28, IntermediateMaterials.build(IntermediateMaterials.ADVANCED_CIRCUIT));
        gui.setItem(29, IntermediateMaterials.build(IntermediateMaterials.HIGH_GEAR));

        // Tier3 (slot 36-38)
        gui.setItem(36, icon(Material.REDSTONE_BLOCK, "§eTier 3 - 先進部品",
                "§7エネルギーコア / 安定化装置"));
        gui.setItem(37, IntermediateMaterials.build(IntermediateMaterials.ENERGY_CORE));
        gui.setItem(38, IntermediateMaterials.build(IntermediateMaterials.STABILIZER));

        // 矢印
        gui.setItem(15, icon(Material.ARROW, "§7→"));
        gui.setItem(24, icon(Material.ARROW, "§7→"));
        gui.setItem(33, icon(Material.ARROW, "§7→"));

        // Tier3.5 (slot 20-22 を使う位置調整)
        gui.setItem(39, icon(Material.ECHO_SHARD, "§eTier 3.5 - 超先進部品",
                "§7量子チップ / 中性子反射材"));
        gui.setItem(40, IntermediateMaterials.build(IntermediateMaterials.QUANTUM_CHIP));
        gui.setItem(41, IntermediateMaterials.build(IntermediateMaterials.NEUTRON_REFLECTOR));

        // Tier4 (slot 47-49)
        gui.setItem(47, icon(Material.NETHERITE_SCRAP, "§dTier 4 - 究極素材",
                "§7超伝導体 / 虚空結晶"));
        gui.setItem(48, IntermediateMaterials.build(IntermediateMaterials.SUPER_CONDUCTOR));
        gui.setItem(49, IntermediateMaterials.build(IntermediateMaterials.VOID_CRYSTAL));

        gui.setItem(42, icon(Material.ARROW, "§7→"));
        gui.setItem(51, icon(Material.ARROW, "§7→"));

        // 戻る
        gui.setItem(0, icon(Material.ARROW, "§c← 戻る"));
        fill(gui, 0, 53, pane(Component.text("")));
        gui.setItem(0, icon(Material.ARROW, "§c← 戻る"));

        player.openInventory(gui);
    }

    // ==========================================================================
    // 中間素材レシピ一覧 → 詳細
    // ==========================================================================

    private static void openMaterialRecipes(Player player, int page) {
        List<CustomCrafterRecipe> allRecipes = CustomCrafterRecipe.getAll();
        if (allRecipes.isEmpty()) {
            player.sendMessage(Component.text("§cレシピが登録されていません。サーバー管理者に報告してください。"));
            return;
        }
        // 全レシピを表示 (フィルタ無し)
        List<CustomCrafterRecipe> recipes = allRecipes;
        int total = recipes.size();
        int maxPage = Math.max(0, (total - 1) / 36);
        if (page > maxPage) page = maxPage;

        Inventory gui = Bukkit.createInventory(new GuideHolder("mat_list", page), 54, "§6§l中間素材レシピ (p" + (page + 1) + "/" + (maxPage + 1) + ")");
        int start = page * 36;
        for (int i = 0; i < 36 && start + i < total; i++) {
            CustomCrafterRecipe r = recipes.get(start + i);
            gui.setItem(i, makeRecipePreview(r));
        }
        gui.setItem(0, icon(Material.ARROW, "§c← 戻る"));
        if (page > 0) gui.setItem(45, icon(Material.ARROW, "§e← 前ページ"));
        if (page < maxPage) gui.setItem(53, icon(Material.ARROW, "§e次ページ →"));
        fill(gui, 0, 53, pane(Component.text("")));
        gui.setItem(0, icon(Material.ARROW, "§c← 戻る"));
        if (page > 0) gui.setItem(45, icon(Material.ARROW, "§e← 前ページ"));
        if (page < maxPage) gui.setItem(53, icon(Material.ARROW, "§e次ページ →"));

        player.openInventory(gui);
    }

    private static ItemStack makeRecipePreview(CustomCrafterRecipe r) {
        ItemStack preview = r.result().clone();
        ItemMeta m = preview.getItemMeta();
        String id = (m != null) ? m.getPersistentDataContainer().get(IntermediateMaterials.KEY, org.bukkit.persistence.PersistentDataType.STRING) : null;
        List<Component> lore = (m != null && m.lore() != null) ? new ArrayList<>(m.lore()) : new ArrayList<>();
        lore.add(Component.text("§eクリックでレシピ詳細"));
        if (id != null) lore.add(Component.text("§8ID: " + id));
        if (m != null) {
            m.lore(lore);
            preview.setItemMeta(m);
        }
        return preview;
    }

    private static void openMaterialRecipeDetail(Player player, String recipeId) {
        CustomCrafterRecipe recipe = CustomCrafterRecipe.getAll().stream()
                .filter(r -> r.id().equals(recipeId))
                .findFirst().orElse(null);
        if (recipe == null) { player.sendMessage("§cレシピが見つかりません"); return; }

        Inventory gui = Bukkit.createInventory(new GuideHolder("mat_detail", recipeId), 54, "§6§l" + recipe.id());
        // 全面ガラスで埋めてから上書き
        fill(gui, 0, 53, pane(Component.text("")));

        gui.setItem(0, icon(Material.ARROW, "§c← 戻る"));
        gui.setItem(4, icon(Material.ENCHANTED_BOOK, "§6§l" + recipe.id(), "§73x3グリッドでクラフト"));

        // 3x3 grid centered: row0=11,12,13 / row1=20,21,22 / row2=29,30,31
        int[] gridSlots = {11,12,13, 20,21,22, 29,30,31};
        String[] shape = recipe.shape();
        Map<Character, CustomCrafterRecipe.Ingredient> ingredients = recipe.ingredients();
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                int slot = gridSlots[row * 3 + col];
                char c = (row < shape.length && col < shape[row].length()) ? shape[row].charAt(col) : ' ';
                CustomCrafterRecipe.Ingredient ing = ingredients.getOrDefault(c, CustomCrafterRecipe.Ingredient.air());
                ItemStack display = resolveIngredient(ing);
                if (display != null) gui.setItem(slot, display);
            }
        }
        // 矢印と結果
        gui.setItem(25, icon(Material.ARROW, "§e→"));
        gui.setItem(34, makeResultItem(recipe.result()));

        player.openInventory(gui);
    }

    private static ItemStack resolveIngredient(CustomCrafterRecipe.Ingredient ing) {
        if (ing.materialId() != null) {
            ItemStack item = IntermediateMaterials.build(ing.materialId());
            if (item != null) {
                ItemMeta m = item.getItemMeta();
                List<Component> lore = m.lore() != null ? new ArrayList<>(m.lore()) : new ArrayList<>();
                lore.add(Component.text("§8x1"));
                m.lore(lore);
                item.setItemMeta(m);
                return item;
            }
            // fallback: create a placeholder
            return icon(Material.BARRIER, "§c" + ing.materialId(), "§7中間素材");
        }
        if (ing.vanilla() != null && ing.vanilla() != Material.AIR) {
            ItemStack item = new ItemStack(ing.vanilla(), 1);
            ItemMeta m = item.getItemMeta();
            m.displayName(Component.text("§f" + formatMaterialName(ing.vanilla())));
            m.lore(List.of(Component.text("§8x1")));
            item.setItemMeta(m);
            return item;
        }
        return null;
    }

    private static ItemStack makeResultItem(ItemStack result) {
        ItemStack r = result.clone();
        ItemMeta m = r.getItemMeta();
        List<Component> lore = m.lore() != null ? new ArrayList<>(m.lore()) : new ArrayList<>();
        lore.add(Component.text(""));
        lore.add(Component.text("§e§l=== 完成品 ==="));
        m.lore(lore);
        r.setItemMeta(m);
        return r;
    }

    // ==========================================================================
    // エネルギー解説
    // ==========================================================================

    private static void openEnergyGuide(Player player, int page) {
        Inventory gui = Bukkit.createInventory(new GuideHolder("energy", page), 54, "§6§lエネルギー解説 (p" + (page + 1) + "/2)");
        gui.setItem(0, icon(Material.ARROW, "§c← 戻る"));

        if (page == 0) {
            gui.setItem(4, icon(Material.REDSTONE_BLOCK, "§c§lEN(エネルギー)システム",
                    "§7全機械はENがないと動作しません",
                    "§7発電・蓄電・送電の3要素で管理"));

            gui.setItem(19, icon(Material.IRON_BLOCK, "§6発電機",
                    "§7石炭/木炭/溶岩等を燃料に発電",
                    "§c最大EN: 2000"));
            gui.setItem(20, icon(Material.NETHER_BRICKS, "§6燃焼発電機",
                    "§7発電機の上位版。燃料効率2倍",
                    "§c最大EN: 3000"));
            gui.setItem(21, icon(Material.SHROOMLIGHT, "§6日照発電機",
                    "§7昼+天空アクセス時 10EN/tick",
                    "§c最大EN: 2000"));

            gui.setItem(23, icon(Material.LAPIS_BLOCK, "§9蓄電機",
                    "§7最大50,000ENを貯蔵",
                    "§750%超で隣接機械に自動分配"));
            gui.setItem(24, icon(Material.DIAMOND_BLOCK, "§9高圧蓄電機",
                    "§7最大200,000ENを貯蔵",
                    "§750%超で自動分配"));
            gui.setItem(25, icon(Material.END_STONE, "§dワイヤレス充電器",
                    "§7半径5ブロックの機械に無線給電",
                    "§c最大EN: 5000"));

            gui.setItem(28, icon(Material.IRON_BARS, "§7送電ワイヤー",
                    "§7ENを隣接ブロック間で転送",
                    "§7チェーン接続で遠距離可能",
                    "§c最大EN: 100"));

            gui.setItem(31, icon(Material.CARTOGRAPHY_TABLE, "§6充電器",
                    "§7RS通電でバッテリーを充電",
                    "§cEN消費: 20/回"));

            gui.setItem(34, icon(Material.GLOWSTONE_DUST, "§eバッテリー",
                    "§7携帯ENストレージ",
                    "§c最大EN: 10000",
                    "§7機械右クリックで放電"));

            gui.setItem(53, icon(Material.ARROW, "§e次ページ →"));
        } else {
            gui.setItem(4, icon(Material.COMPARATOR, "§c§lENの流れ"));
            gui.setItem(10, icon(Material.GREEN_STAINED_GLASS_PANE, "§a発電",
                    "§7発電機/GENERATOR/日照でEN生成"));
            gui.setItem(13, icon(Material.LIGHT_BLUE_STAINED_GLASS_PANE, "§b送電",
                    "§7送電ワイヤーで隣接ブロックへ転送",
                    "§7蓄電機/高圧蓄電機で貯蔵+分配"));
            gui.setItem(16, icon(Material.RED_STAINED_GLASS_PANE, "§c消費",
                    "§7全機械がENを消費して動作",
                    "§7EN不足=動作停止"));

            gui.setItem(22, icon(Material.REDSTONE, "§cEN消費量(主要)",
                    "§7虚空採掘機 200EN",
                    "§7自動エンチャンター 50EN",
                    "§7物質生成機 50EN",
                    "§7鉱石三段加工機 40EN",
                    "§7自動解呪機 40EN",
                    "§7リサイクル機 30EN",
                    "§7粉砕精錬機 30EN",
                    "§7遠心分離機 25EN",
                    "§7カスタムクラフター 25EN",
                    "§8その他は /cs guide 装置一覧で確認"));

            gui.setItem(28, icon(Material.LADDER, "§e§l推奨進行",
                    "§71. 日照発電機 or 発電機",
                    "§72. 蓄電機(EN貯蔵)",
                    "§73. 送電ワイヤー(配線)",
                    "§74. 各機械にEN配給",
                    "§7  → 充電器+バッテリーで携帯充電"));

            gui.setItem(45, icon(Material.ARROW, "§e← 前ページ"));
        }

        fill(gui, 0, 53, pane(Component.text("")));
        gui.setItem(0, icon(Material.ARROW, "§c← 戻る"));
        if (page > 0) gui.setItem(45, icon(Material.ARROW, "§e← 前ページ"));
        if (page < 1) gui.setItem(53, icon(Material.ARROW, "§e次ページ →"));
        player.openInventory(gui);
    }

    // ==========================================================================
    // 装置一覧
    // ==========================================================================

    private static void openMachineList(Player player, int page) {
        MachineType[] all = MachineType.values();
        int total = all.length;
        int maxPage = Math.max(0, (total - 1) / 27);
        if (page > maxPage) page = maxPage;

        Inventory gui = Bukkit.createInventory(new GuideHolder("machine", page), 54, "§6§l装置一覧 (p" + (page + 1) + "/" + (maxPage + 1) + ")");
        int start = page * 27;
        for (int i = 0; i < 27 && start + i < total; i++) {
            MachineType type = all[start + i];
            ItemStack item = new ItemStack(type.blockMaterial);
            ItemMeta m = item.getItemMeta();
            m.displayName(Component.text("§e" + type.displayName));

int enCost = net.circuitsurvival.managers.EnergyManager.getEnergyCost(type);
                int maxEn = net.circuitsurvival.managers.EnergyManager.getDefaultMaxForType(type);
            boolean needsEn = net.circuitsurvival.managers.EnergyManager.needsEnergy(type);

            var lore = new ArrayList<Component>();
            lore.add(Component.text("§7" + type.description));
            lore.add(Component.text(""));
            if (needsEn) {
                lore.add(Component.text("§cEN消費: " + enCost + "/回"));
                lore.add(Component.text("§c最大EN: " + maxEn));
            } else if (net.circuitsurvival.managers.EnergyManager.isEnergySource(type)) {
                lore.add(Component.text("§aEN供給源"));
                lore.add(Component.text("§c最大EN: " + maxEn));
            } else {
                lore.add(Component.text("§7EN不要(パッシブ/RS制御)"));
            }
            String tiers = getTierLabel(type);
            if (tiers != null) lore.add(Component.text(tiers));
            m.lore(lore);
            item.setItemMeta(m);
            gui.setItem(i, item);
        }

        gui.setItem(0, icon(Material.ARROW, "§c← 戻る"));
        if (page > 0) gui.setItem(45, icon(Material.ARROW, "§e← 前ページ"));
        if (page < maxPage) gui.setItem(53, icon(Material.ARROW, "§e次ページ →"));
        fill(gui, 0, 53, pane(Component.text("")));
        gui.setItem(0, icon(Material.ARROW, "§c← 戻る"));
        if (page > 0) gui.setItem(45, icon(Material.ARROW, "§e← 前ページ"));
        if (page < maxPage) gui.setItem(53, icon(Material.ARROW, "§e次ページ →"));
        player.openInventory(gui);
    }

    private static String getTierLabel(MachineType type) {
        return switch (type) {
            case TIMER, BLOCK_PLACER, BLOCK_BREAKER, IGNITER, RIGHT_CLICKER,
                 ENTITY_DETECTOR, CHUNK_LOADER, SOLAR_PANEL, ENERGY_CABLE -> "§8Tier 0 - パッシブ";
            case CRUSHER, COMPRESSOR, AUTO_SMELTER, AUTO_CRAFTER,
                 ITEM_SORTER, AUTO_FARMER, MINER, VACUUM_HOPPER, AUTO_BREWER,
                 ITEM_ROUTER, BLOCK_TRANSMUTER, FLUID_COLLECTOR, BONE_MEALER,
                 STORAGE_DRUM, STORAGE_CONTROLLER, WOODCUTTER, XP_CONVERTER,
                 FAST_HOPPER, VERT_FAST_HOPPER, BULK_DROPPER, AUTO_FISHER,
                 COOKING_STATION, AUTO_SHEARER, VERTICAL_ELEVATOR -> "§8Tier 1 - 基礎機械";
            case PIXEL_FORGE, CUSTOM_CRAFTER, PULVERIZER, ELECTRIC_FURNACE, AUTO_ANVIL -> "§8Tier 2 - 高度機械";
            case GENERATOR, COMBUSTION_GENERATOR, ENERGY_CELL, HV_CELL, CHARGER, WIRELESS_CHARGER -> "§8Tier 3 - エネルギー系";
            case AUTO_ENCHANTER, VOID_MINER, INDUCTION_FURNACE, CENTRIFUGE, ORE_PROCESSOR,
                 MATERIALIZER, RECYCLER, AUTO_DISENCHANTER -> "§8Tier 4 - エンドゲーム";
            default -> null;
        };
    }

    // ---- 機械レシピ詳細 (BukkitレシピAPIで3x3表示) --------------------------------

    private static void openMachineRecipeDetail(Player player, MachineType type) {
        openMachineRecipeDetail(player, type, 0);
    }

    private static void openMachineRecipeDetail(Player player, MachineType type, int outPage) {
        String dataKey = type.name() + "|" + outPage;
        Inventory gui = Bukkit.createInventory(new GuideHolder("machine_detail", dataKey), 54, "§6§l" + type.displayName);
        fill(gui, 0, 53, pane(Component.text("")));

        gui.setItem(0, icon(Material.ARROW, "§c← 戻る"));
        gui.setItem(4, icon(Material.ENCHANTED_BOOK, "§6§l" + type.displayName,
                "§7" + type.description,
                "§8ブロック: " + type.blockMaterial.name()));

        // Bukkitの登録済みレシピから3x3グリッドを表示
        org.bukkit.NamespacedKey recipeKey = pluginRef != null
                ? new org.bukkit.NamespacedKey(pluginRef, "machine_" + type.name().toLowerCase())
                : org.bukkit.NamespacedKey.fromString("circuitsurvivalv2:machine_" + type.name().toLowerCase());
        org.bukkit.inventory.Recipe bukkitRecipe = recipeKey != null ? Bukkit.getRecipe(recipeKey) : null;
        if (bukkitRecipe instanceof org.bukkit.inventory.ShapedRecipe shaped) {
            String[] shape = shaped.getShape();
            Map<Character, ItemStack> ingMap = shaped.getIngredientMap();
            int[] gridSlots = {11,12,13, 20,21,22, 29,30,31};
            for (int row = 0; row < 3; row++) {
                for (int col = 0; col < 3; col++) {
                    int slot = gridSlots[row * 3 + col];
                    char c = (row < shape.length && col < shape[row].length()) ? shape[row].charAt(col) : ' ';
                    ItemStack ing = ingMap.get(c);
                    if (ing != null && ing.getType() != Material.AIR) {
                        ItemStack display = ing.clone();
                        display.setAmount(1);
                        ItemMeta im = display.getItemMeta();
                        if (im != null && im.getPersistentDataContainer()
                                .has(IntermediateMaterials.KEY, org.bukkit.persistence.PersistentDataType.STRING)) {
                            // PDC付き中間素材はカスタム名とloreを保持
                            List<Component> lore = im.lore() != null ? new ArrayList<>(im.lore()) : new ArrayList<>();
                            lore.add(Component.text("§8x1"));
                            im.lore(lore);
                        } else {
                            im.displayName(Component.text("§f" + formatMaterialName(ing.getType())));
                            im.lore(List.of(Component.text("§8x1")));
                        }
                        display.setItemMeta(im);
                        gui.setItem(slot, display);
                    }
                }
            }
            gui.setItem(25, icon(Material.ARROW, "§e→"));

            // 完成品
            ItemStack result = shaped.getResult().clone();
            ItemMeta rm = result.getItemMeta();
            rm.displayName(Component.text("§6§l" + type.displayName));
            rm.lore(List.of(
                    Component.text("§7" + type.description),
                    Component.text(""),
                    Component.text("§eクラフトして入手"),
                    Component.text("§8設置後、右クリックでGUI")
            ));
            result.setItemMeta(rm);
            gui.setItem(34, result);
        } else {
            // レシピが見つからない場合 (パイプ/ゲートなど)
            gui.setItem(22, icon(Material.BARRIER, "§cレシピ情報がありません",
                    "§7この装置はバニラ作業台では",
                    "§7クラフトできません",
                    "§8/cs give で入手"));
        }

        // EN情報
        int enCost = net.circuitsurvival.managers.EnergyManager.getEnergyCost(type);
        int maxEn = net.circuitsurvival.managers.EnergyManager.getDefaultMaxForType(type);
        boolean needsEn = net.circuitsurvival.managers.EnergyManager.needsEnergy(type);
        String enInfo;
        if (needsEn) enInfo = "§cEN消費: " + enCost + "/回  | 最大EN: " + maxEn;
        else if (net.circuitsurvival.managers.EnergyManager.isEnergySource(type)) enInfo = "§aEN供給源  | 最大EN: " + maxEn;
        else enInfo = "§7EN不要 (パッシブ/RS制御)";
        gui.setItem(48, icon(Material.REDSTONE, enInfo));

        // 処理レシピ一覧ボタン
        gui.setItem(50, icon(Material.KNOWLEDGE_BOOK, "§b§l処理レシピ一覧",
                "§7この機械で処理できる",
                "§7全アイテム変換レシピを表示",
                "§8クリックで開く"));

        // 各機械が作れるカスタムレシピ表示
        addCustomOutputs(gui, type, outPage);

        player.openInventory(gui);
    }

    /** 機械ごとに作れるカスタムアウトプットを表示 */
    private static void addCustomOutputs(Inventory gui, MachineType type, int outPage) {
        List<ItemStack> outputs = new ArrayList<>();
        switch (type) {
            case CUSTOM_CRAFTER -> {
                for (CustomCrafterRecipe r : CustomCrafterRecipe.getAll()) {
                    if (r.id().startsWith("redstone_") || r.id().startsWith("copper_")) continue;
                    ItemStack out = r.result().clone();
                    ItemMeta om = out.getItemMeta();
                    List<Component> olore = new ArrayList<>(om.lore() != null ? om.lore() : new ArrayList<>());
                    olore.add(Component.text(""));
                    olore.add(Component.text("§e§l=== レシピ ==="));
                    printRecipeIngredients(olore, r);
                    om.lore(olore);
                    out.setItemMeta(om);
                    outputs.add(out);
                }
            }
            case PIXEL_FORGE -> {
                outputs.add(net.circuitsurvival.items.CustomItems.build(net.circuitsurvival.items.CustomItems.PIXEL_GUN));
                outputs.add(net.circuitsurvival.items.CustomItems.build(net.circuitsurvival.items.CustomItems.DARK_SABER));
                outputs.add(net.circuitsurvival.items.CustomItems.build(net.circuitsurvival.items.CustomItems.GRENADE_LAUNCHER));
                outputs.add(net.circuitsurvival.items.CustomItems.build(net.circuitsurvival.items.CustomItems.SNIPER_RIFLE));
            }
            case COOKING_STATION -> {
                outputs.add(net.circuitsurvival.items.CustomItems.build(net.circuitsurvival.items.CustomItems.EXPLOSIVE_CROQUETTE));
                outputs.add(net.circuitsurvival.items.CustomItems.build(net.circuitsurvival.items.CustomItems.CHEF_KNIFE));
                outputs.add(net.circuitsurvival.items.CustomItems.build(net.circuitsurvival.items.CustomItems.SPICE_CURRY));
            }
            case CRUSHER -> outputs.addAll(List.of(
                    icon(Material.RAW_IRON, "§f鉄原鉱 x2"),
                    icon(Material.RAW_GOLD, "§f金原鉱 x2"),
                    icon(Material.RAW_COPPER, "§f銅原鉱 x2"),
                    icon(Material.GRAVEL, "§f丸石→砂利"),
                    icon(Material.SAND, "§f砂利→砂"),
                    icon(Material.GUNPOWDER, "§f砂→火薬")));
            case PULVERIZER -> outputs.addAll(List.of(
                    icon(Material.RAW_IRON, "§f鉄原鉱 x3"),
                    icon(Material.RAW_GOLD, "§f金原鉱 x3"),
                    icon(Material.RAW_COPPER, "§f銅原鉱 x3")));
            case COMPRESSOR -> outputs.addAll(List.of(
                    icon(Material.IRON_BLOCK, "§f鉄ブロック"),
                    icon(Material.REDSTONE_BLOCK, "§fRSブロック"),
                    icon(Material.COAL_BLOCK, "§f石炭ブロック")));
            case AUTO_SMELTER, INDUCTION_FURNACE, ELECTRIC_FURNACE -> outputs.addAll(List.of(
                    icon(Material.IRON_INGOT, "§f鉄インゴット"),
                    icon(Material.GOLD_INGOT, "§f金インゴット"),
                    icon(Material.GLASS, "§fガラス")));
            case GENERATOR -> outputs.addAll(List.of(
                    icon(Material.REDSTONE, "§cEN生成"),
                    icon(Material.COAL, "§7燃料: 石炭/木炭/溶岩等")));
            case COMBUSTION_GENERATOR -> outputs.addAll(List.of(
                    icon(Material.REDSTONE_BLOCK, "§cEN生成(燃料効率2倍)"),
                    icon(Material.BLAZE_ROD, "§7燃料効率2倍")));
            case SOLAR_PANEL -> outputs.add(icon(Material.DAYLIGHT_DETECTOR, "§a10EN/tick (昼+天空)"));
            case ENERGY_CELL -> outputs.add(icon(Material.LAPIS_BLOCK, "§9最大50000EN貯蔵"));
            case HV_CELL -> outputs.add(icon(Material.DIAMOND_BLOCK, "§b最大200000EN貯蔵"));
            case VOID_MINER -> outputs.addAll(List.of(
                    icon(Material.COAL, "§7石炭 40%"),
                    icon(Material.RAW_IRON, "§7鉄 30%"),
                    icon(Material.DIAMOND, "§bダイヤ 1.5%"),
                    icon(Material.EMERALD, "§aエメラルド 0.5%")));
            case ORE_PROCESSOR -> outputs.addAll(List.of(
                    icon(Material.IRON_INGOT, "§f鉄 x4"),
                    icon(Material.GOLD_INGOT, "§f金 x4"),
                    icon(Material.COPPER_INGOT, "§f銅 x4")));
            case AUTO_ENCHANTER -> outputs.add(icon(Material.ENCHANTED_BOOK, "§dランダムエンチャント1-3個"));
            case CENTRIFUGE -> outputs.addAll(List.of(
                    icon(Material.REDSTONE, "§cRSブロック→赤石9"),
                    icon(Material.COPPER_INGOT, "§6銅ブロック→銅6")));
            case MATERIALIZER -> outputs.addAll(List.of(
                    icon(Material.DIAMOND, "§b石炭→ダイヤ"),
                    icon(Material.GOLD_INGOT, "§e鉄ナゲット→金"),
                    icon(Material.EMERALD, "§a水晶→エメラルド")));
            case RECYCLER -> outputs.addAll(List.of(
                    icon(Material.IRON_NUGGET, "§7不要品→鉄"),
                    icon(Material.COAL, "§8木材→石炭")));
            case AUTO_DISENCHANTER -> outputs.add(icon(Material.ENCHANTED_BOOK, "§eエンチャ抽出"));
            case CHARGER -> outputs.add(icon(Material.GLOWSTONE_DUST, "§eバッテリー充電"));
            case XP_CONVERTER -> outputs.add(icon(Material.EXPERIENCE_BOTTLE, "§a経験値生成"));
            case BONE_MEALER -> outputs.add(icon(Material.BONE_MEAL, "§f骨粉散布"));
            case AUTO_FISHER -> outputs.add(icon(Material.COD, "§7釣りルート生成"));
            case WOODCUTTER -> outputs.addAll(List.of(
                    icon(Material.OAK_PLANKS, "§f原木→板材"),
                    icon(Material.STICK, "§f板材→棒")));
            case BLOCK_TRANSMUTER -> outputs.addAll(List.of(
                    icon(Material.GLASS, "§f砂→ガラス"),
                    icon(Material.STONE, "§f丸石→石")));
            case FLUID_COLLECTOR -> outputs.addAll(List.of(
                    icon(Material.WATER_BUCKET, "§9水源回収"),
                    icon(Material.LAVA_BUCKET, "§6溶岩回収")));
            case AUTO_BREWER -> outputs.add(icon(Material.POTION, "§d自動醸造"));
            case ITEM_SORTER, ITEM_ROUTER -> outputs.add(icon(Material.HOPPER, "§7アイテム仕分け"));
            case AUTO_CRAFTER -> outputs.add(icon(Material.CRAFTING_TABLE, "§f自動クラフト"));
        }
        if (!outputs.isEmpty()) {
            int itemsPerPage = 8;
            int totalPages = Math.max(1, (outputs.size() + itemsPerPage - 1) / itemsPerPage);
            int page = Math.min(outPage, totalPages - 1);
            int start = page * itemsPerPage;

            gui.setItem(36, icon(Material.GREEN_STAINED_GLASS_PANE, "§a§l出力品 (p" + (page + 1) + "/" + totalPages + ")"));
            for (int i = 0; i < itemsPerPage && start + i < outputs.size(); i++) {
                gui.setItem(37 + i, outputs.get(start + i));
            }
            if (page > 0) gui.setItem(45, icon(Material.ARROW, "§e← 前の出力"));
            if (page < totalPages - 1) gui.setItem(53, icon(Material.ARROW, "§e次の出力 →"));
        }
    }

    /** CustomCrafterレシピの素材をloreに追記 */
    private static void printRecipeIngredients(List<Component> lore, CustomCrafterRecipe r) {
        String[] shape = r.shape();
        Map<Character, CustomCrafterRecipe.Ingredient> ings = r.ingredients();
        java.util.Set<Character> printed = new java.util.HashSet<>();
        for (int row = 0; row < shape.length; row++) {
            StringBuilder line = new StringBuilder("§7");
            for (int col = 0; col < shape[row].length(); col++) {
                char c = shape[row].charAt(col);
                CustomCrafterRecipe.Ingredient ing = ings.get(c);
                if (ing == null || ing.vanilla() == Material.AIR) { line.append("  ·  "); continue; }
                if (printed.contains(c)) { line.append("  ·  "); continue; }
                printed.add(c);
                if (ing.materialId() != null) {
                    String name = ing.materialId().replace("_", " ");
                    line.append(name).append("  ");
                } else if (ing.vanilla() != null) {
                    line.append(formatMaterialName(ing.vanilla())).append("  ");
                }
            }
            if (line.length() > 2) lore.add(Component.text(line.toString()));
        }
    }

    // ==========================================================================
    // 機械ごとの処理レシピ一覧
    // ==========================================================================

    private static record ProcessEntry(ItemStack input, ItemStack output, String info) {}

    private static List<ProcessEntry> getProcessRecipes(MachineType type) {
        List<ProcessEntry> list = new ArrayList<>();
        switch (type) {
            case CRUSHER -> {
                list.add(e(Material.IRON_ORE,              IntermediateMaterials.build(IntermediateMaterials.CRUSHED_IRON, 3), "x3"));
                list.add(e(Material.DEEPSLATE_IRON_ORE,     IntermediateMaterials.build(IntermediateMaterials.CRUSHED_IRON, 3), "x3"));
                list.add(e(Material.GOLD_ORE,               IntermediateMaterials.build(IntermediateMaterials.CRUSHED_GOLD, 3), "x3"));
                list.add(e(Material.DEEPSLATE_GOLD_ORE,     IntermediateMaterials.build(IntermediateMaterials.CRUSHED_GOLD, 3), "x3"));
                list.add(e(Material.COAL_ORE,               IntermediateMaterials.build(IntermediateMaterials.CRUSHED_COAL, 2), "x2"));
                list.add(e(Material.DEEPSLATE_COAL_ORE,     IntermediateMaterials.build(IntermediateMaterials.CRUSHED_COAL, 2), "x2"));
                list.add(e(Material.COBBLESTONE,            new ItemStack(Material.GRAVEL, 2), "x2"));
                list.add(e(Material.STONE,                  new ItemStack(Material.GRAVEL, 2), "x2"));
                list.add(e(Material.COBBLED_DEEPSLATE,      new ItemStack(Material.GRAVEL, 2), "x2"));
                list.add(e(Material.NETHERRACK,             IntermediateMaterials.build(IntermediateMaterials.CRUSHED_NETHERRACK, 2), "x2"));
                list.add(e(Material.DIAMOND_ORE,            new ItemStack(Material.DIAMOND, 3), "x3"));
                list.add(e(Material.DEEPSLATE_DIAMOND_ORE,  new ItemStack(Material.DIAMOND, 3), "x3"));
                list.add(e(Material.EMERALD_ORE,            new ItemStack(Material.EMERALD, 2), "x2"));
                list.add(e(Material.REDSTONE_ORE,           new ItemStack(Material.REDSTONE, 8), "x8"));
                list.add(e(Material.LAPIS_ORE,              new ItemStack(Material.LAPIS_LAZULI, 9), "x9"));
                list.add(e(Material.COPPER_ORE,             new ItemStack(Material.RAW_COPPER, 2), "x2"));
                list.add(e(Material.NETHER_QUARTZ_ORE,      new ItemStack(Material.QUARTZ, 2), "x2"));
                list.add(e(Material.NETHER_GOLD_ORE,        new ItemStack(Material.GOLD_NUGGET, 6), "x6"));
                list.add(e(Material.GLOWSTONE,              new ItemStack(Material.GLOWSTONE_DUST, 4), "x4"));
                list.add(e(Material.QUARTZ_BLOCK,           new ItemStack(Material.QUARTZ, 2), "x2"));
                list.add(e(Material.RAW_IRON_BLOCK,         new ItemStack(Material.RAW_IRON, 9), "x9"));
                list.add(e(Material.RAW_GOLD_BLOCK,         new ItemStack(Material.RAW_GOLD, 9), "x9"));
                list.add(e(Material.RAW_COPPER_BLOCK,       new ItemStack(Material.RAW_COPPER, 9), "x9"));
            }
            case COMPRESSOR -> {
                list.add(e(item(Material.IRON_INGOT, "§f鉄インゴット"), IntermediateMaterials.build(IntermediateMaterials.COMPRESSED_IRON, 1), "x1"));
                list.add(e(item(Material.GOLD_INGOT, "§f金インゴット"), IntermediateMaterials.build(IntermediateMaterials.COMPRESSED_GOLD, 1), "x1"));
                list.add(e(item(Material.IRON_BLOCK, "§f鉄ブロック"), IntermediateMaterials.build(IntermediateMaterials.COMPRESSED_IRON, 9), "x9"));
                list.add(e(item(Material.GOLD_BLOCK, "§f金ブロック"), IntermediateMaterials.build(IntermediateMaterials.COMPRESSED_GOLD, 9), "x9"));
                list.add(e(item(Material.COBBLESTONE, "§f丸石"), new ItemStack(Material.GRAVEL, 4), "x4"));
                list.add(e(item(Material.STONE, "§f石"), new ItemStack(Material.STONE_BRICKS, 4), "x4"));
                list.add(e(item(Material.NETHERRACK, "§fネザーラック"), new ItemStack(Material.NETHER_BRICKS, 2), "x2"));
                list.add(e(item(Material.SAND, "§f砂"), new ItemStack(Material.SANDSTONE, 2), "x2"));
            }
            case RECYCLER -> {
                list.add(e(item(Material.ROTTEN_FLESH, "§f腐肉"), new ItemStack(Material.IRON_NUGGET, 1), "x1"));
                list.add(e(item(Material.BONE, "§f骨"), new ItemStack(Material.IRON_NUGGET, 1), "x1"));
                list.add(e(item(Material.STRING, "§f糸"), new ItemStack(Material.IRON_NUGGET, 1), "x1"));
                list.add(e(item(Material.SPIDER_EYE, "§f蜘蛛の目"), new ItemStack(Material.IRON_NUGGET, 1), "x1"));
                list.add(e(item(Material.IRON_INGOT, "§f鉄インゴット"), new ItemStack(Material.IRON_NUGGET, 3), "x3"));
                list.add(e(item(Material.GOLD_INGOT, "§f金インゴット"), new ItemStack(Material.GOLD_NUGGET, 3), "x3"));
                list.add(e(item(Material.COPPER_INGOT, "§f銅インゴット"), new ItemStack(Material.IRON_NUGGET, 2), "x2"));
                list.add(e(item(Material.DIAMOND, "§fダイヤ"), new ItemStack(Material.IRON_INGOT, 2), "x2"));
                list.add(e(item(Material.NETHERITE_SCRAP, "§fネザライトの欠片"), new ItemStack(Material.DIAMOND, 1), "x1"));
                list.add(e(item(Material.OAK_LOG, "§f原木"), new ItemStack(Material.COAL, 1), "x1"));
                list.add(e(item(Material.COBBLESTONE, "§f丸石"), new ItemStack(Material.FLINT, 1), "x1"));
                list.add(e(item(Material.GRAVEL, "§f砂利"), new ItemStack(Material.SAND, 2), "x2"));
                list.add(e(item(Material.SAND, "§f砂"), new ItemStack(Material.COBBLESTONE, 1), "x1"));
            }
            case WASHING_MACHINE -> {
                list.add(e(item(Material.GRAVEL, "§f砂利"), new ItemStack(Material.IRON_NUGGET, 1), "§e20%"));
                list.add(e(item(Material.COBBLESTONE, "§f丸石"), new ItemStack(Material.IRON_NUGGET, 1), "§e10%"));
                list.add(e(item(Material.SAND, "§f砂"), new ItemStack(Material.CLAY_BALL, 1), "§e30%"));
                list.add(e(item(Material.RED_SAND, "§f赤砂"), new ItemStack(Material.GOLD_NUGGET, 1), "§e15%"));
                list.add(e(item(Material.SOUL_SAND, "§fソウルサンド"), new ItemStack(Material.QUARTZ, 1), "§e20%"));
            }
            case BLOCK_TRANSMUTER -> {
                list.add(e(item(Material.COBBLESTONE, "§f丸石"), new ItemStack(Material.STONE, 1), "§7→石"));
                list.add(e(item(Material.STONE, "§f石"), new ItemStack(Material.SMOOTH_STONE, 1), "§7→滑石"));
                list.add(e(item(Material.SAND, "§f砂"), new ItemStack(Material.GLASS, 1), "§7→ガラス"));
                list.add(e(item(Material.GRAVEL, "§f砂利"), new ItemStack(Material.COBBLESTONE, 1), "§7→丸石"));
                list.add(e(item(Material.ANDESITE, "§f安山岩"), new ItemStack(Material.COBBLESTONE, 1), "§7→丸石"));
                list.add(e(item(Material.DIORITE, "§f閃緑岩"), new ItemStack(Material.COBBLESTONE, 1), "§7→丸石"));
                list.add(e(item(Material.GRANITE, "§f花崗岩"), new ItemStack(Material.COBBLESTONE, 1), "§7→丸石"));
            }
            case COOKING_STATION -> {
                list.add(e(item(Material.POTATO, "§fじゃがいも"),
                        CustomItems.build(CustomItems.EXPLOSIVE_CROQUETTE), "§7爆発コロッケ"));
                list.add(e(item(Material.GOLDEN_CARROT, "§f金のニンジン"),
                        CustomItems.build(CustomItems.SPICE_CURRY), "§7スパイスカレー"));
                list.add(e(item(Material.RABBIT_STEW, "§fウサギシチュー"),
                        CustomItems.build(CustomItems.SPICE_CURRY), "§7スパイスカレー"));
                list.add(e(item(Material.BEETROOT_SOUP, "§fビートルートスープ"),
                        CustomItems.build(CustomItems.SPICE_CURRY), "§7スパイスカレー"));
            }
            case PIXEL_FORGE -> {
                list.add(e(makePair(Material.IRON_SWORD, "§f鉄の剣"),
                        makePair(Material.GOLD_INGOT, "§f金インゴット"),
                        CustomItems.build(CustomItems.CHEF_KNIFE), "§7包丁"));
                list.add(e(makePair(Material.CROSSBOW, "§fクロスボウ"),
                        makePair(Material.AMETHYST_SHARD, "§fアメジスト"),
                        CustomItems.build(CustomItems.PIXEL_GUN), "§7ピクセルガン"));
                list.add(e(makePair(Material.CROSSBOW, "§fクロスボウ"),
                        makePair(Material.GLASS, "§fガラス"),
                        CustomItems.build(CustomItems.SNIPER_RIFLE), "§7スナイパー"));
                list.add(e(makePair(Material.NETHERITE_SWORD, "§fネザライト剣"),
                        makePair(Material.NETHER_STAR, "§fネザースター"),
                        CustomItems.build(CustomItems.DARK_SABER), "§7ダークセイバー"));
                list.add(e(makePair(Material.BOW, "§f弓"),
                        makePair(Material.FIRE_CHARGE, "§f火炎玉"),
                        CustomItems.build(CustomItems.GRENADE_LAUNCHER), "§7グレネード"));
            }
            case NEUTRON_COMPRESSOR -> {
                list.add(e(item(Material.COBBLESTONE, "§f丸石 x64"), new ItemStack(Material.IRON_INGOT, 1), "§764個→1"));
                list.add(e(item(Material.COBBLED_DEEPSLATE, "§f深層岩x64"), new ItemStack(Material.GOLD_INGOT, 1), "§764個→1"));
                list.add(e(item(Material.IRON_BLOCK, "§f鉄ブロック x9"), new ItemStack(Material.IRON_INGOT, 9), "§7圧縮→塊"));
                list.add(e(item(Material.GOLD_BLOCK, "§f金ブロック x9"), new ItemStack(Material.GOLD_INGOT, 9), "§7圧縮→塊"));
                list.add(e(item(Material.DIAMOND_BLOCK, "§fダイヤブロック x9"), new ItemStack(Material.DIAMOND, 9), "§7圧縮→塊"));
            }
            case CENTRIFUGE -> {
                list.add(e(item(Material.REDSTONE_BLOCK, "§fRSブロック"), new ItemStack(Material.REDSTONE, 9), "§7+輝石x4"));
                list.add(e(item(Material.COPPER_BLOCK, "§f銅ブロック"), new ItemStack(Material.COPPER_INGOT, 6), "§7+金塊x3"));
                list.add(e(item(Material.IRON_BLOCK, "§f鉄ブロック"), new ItemStack(Material.IRON_INGOT, 6), "§7+鉄塊x8"));
                list.add(e(item(Material.GOLD_BLOCK, "§f金ブロック"), new ItemStack(Material.GOLD_INGOT, 6), "§7+金塊x12"));
                list.add(e(item(Material.LAPIS_BLOCK, "§fラピスブロック"), new ItemStack(Material.LAPIS_LAZULI, 9), "§7+鉄塊x3"));
                list.add(e(item(Material.DIAMOND_BLOCK, "§fダイヤブロック"), new ItemStack(Material.DIAMOND, 6), "§7+石炭x4"));
                list.add(e(IntermediateMaterials.build(IntermediateMaterials.CRUSHED_IRON, 4),
                        IntermediateMaterials.build(IntermediateMaterials.PURE_IRON_EXTRACT), "§74:1 抽出"));
                list.add(e(IntermediateMaterials.build(IntermediateMaterials.CRUSHED_GOLD, 4),
                        IntermediateMaterials.build(IntermediateMaterials.PURE_GOLD_EXTRACT), "§74:1 抽出"));
            }
            case INDUCTION_FURNACE -> {
                list.add(e(IntermediateMaterials.build(IntermediateMaterials.REFINED_IRON_INGOT),
                        IntermediateMaterials.build(IntermediateMaterials.CRUSHED_COAL),
                        IntermediateMaterials.build(IntermediateMaterials.STEEL_INGOT, 2), "§7合金 x2"));
                list.add(e(IntermediateMaterials.build(IntermediateMaterials.STEEL_INGOT),
                        IntermediateMaterials.build(IntermediateMaterials.PURE_IRON_EXTRACT),
                        IntermediateMaterials.build(IntermediateMaterials.ADVANCED_ALLOY, 2), "§7合金 x2"));
            }
            case CRAFTER_CONTROLLER -> {
                list.add(e(item(Material.CRAFTING_TABLE, "§f作業台"), item(Material.DISPENSER, "§f制御装置"), "§7周囲8ブロックの自動作業台を遠隔制御"));
            }
            case WATER_GENERATOR -> {
                list.add(e(item(Material.WATER_BUCKET, "§f隣接水源"), item(Material.REDSTONE, "§fEN出力"), "§73 EN/tick §a安定"));
            }
            case WIND_GENERATOR -> {
                list.add(e(item(Material.LIGHTNING_ROD, "§f高所設置"), item(Material.REDSTONE, "§fEN出力"), "§71-6 EN/tick §e高さ依存"));
            }
            default -> {}
        }
        return list;
    }

    private static ProcessEntry e(Material input, ItemStack output, String info) {
        ItemStack in = new ItemStack(input);
        ItemMeta im = in.getItemMeta();
        if (im != null) { im.displayName(Component.text("§f" + formatMaterialName(input))); in.setItemMeta(im); }
        return new ProcessEntry(in, output, info);
    }

    private static ProcessEntry e(ItemStack input, ItemStack output, String info) {
        return new ProcessEntry(input, output, info);
    }

    private static ProcessEntry e(ItemStack input1, ItemStack input2, ItemStack output, String info) {
        ItemStack combined = input1.clone();
        ItemMeta cm = combined.getItemMeta();
        if (cm != null) {
            String dn1 = cm.hasDisplayName() ? cm.getDisplayName() : "§f" + formatMaterialName(input1.getType());
            cm.displayName(Component.text(dn1 + " §8+ §f" + formatMaterialName(input2.getType())));
            combined.setItemMeta(cm);
        }
        return new ProcessEntry(combined, output, info);
    }

    private static ItemStack item(Material mat, String name) {
        ItemStack is = new ItemStack(mat);
        ItemMeta im = is.getItemMeta();
        if (im != null) { im.displayName(Component.text(name)); is.setItemMeta(im); }
        return is;
    }

    private static ItemStack makePair(Material baseMat, String baseName) {
        ItemStack is = new ItemStack(baseMat);
        ItemMeta im = is.getItemMeta();
        if (im != null) { im.displayName(Component.text(baseName)); is.setItemMeta(im); }
        return is;
    }

    private static ItemStack tagIdx(ItemStack item, int idx) {
        if (item == null) return item;
        ItemMeta meta = item.hasItemMeta() ? item.getItemMeta() : Bukkit.getItemFactory().getItemMeta(item.getType());
        if (meta == null) return item;
        meta.getPersistentDataContainer().set(PROCESS_RECIPE_KEY, org.bukkit.persistence.PersistentDataType.INTEGER, idx);
        item.setItemMeta(meta);
        return item;
    }

    private static void openMachineProcessRecipes(Player player, MachineType type, int page) {
        List<ProcessEntry> recipes = getProcessRecipes(type);
        if (recipes.isEmpty()) {
            player.sendMessage(Component.text("§c" + type.displayName + " はアイテム変換レシピを持っていません。"));
            return;
        }
        int perPage = 8;
        int totalPages = (recipes.size() + perPage - 1) / perPage;
        if (page >= totalPages) page = totalPages - 1;
        if (page < 0) page = 0;

        Inventory gui = Bukkit.createInventory(
                new GuideHolder("machine_process", type.name() + "|" + page), 54,
                "§b§l" + type.displayName + " 処理レシピ");

        fill(gui, 0, 53, pane(Component.text("")));
        gui.setItem(0, icon(Material.ARROW, "§c← 戻る"));
        gui.setItem(4, icon(Material.KNOWLEDGE_BOOK, "§b§l処理レシピ一覧",
                "§7" + type.displayName,
                "§7" + type.description,
                "§8全 " + recipes.size() + " 件  |  " + (page + 1) + "/" + totalPages + " ページ"));

        // 2列×4行 = 8件/ページ
        // 入力(左) → 出力(右) のペア表示
        int[][] rowSlots = {{10,12, 14,16}, {19,21, 23,25}, {28,30, 32,34}, {37,39, 41,43}};
        for (int row = 0; row < 4; row++) {
            for (int col = 0; col < 2; col++) {
                int idx = page * perPage + row * 2 + col;
                if (idx >= recipes.size()) break;
                ProcessEntry e = recipes.get(idx);
                int inputSlot = rowSlots[row][col * 2];
                int outputSlot = rowSlots[row][col * 2 + 1];
                gui.setItem(inputSlot, tagIdx(e.input().clone(), idx));
                gui.setItem(inputSlot + 1, tagIdx(icon(Material.PAPER, "§e→", e.info()), idx));
                gui.setItem(outputSlot, tagIdx(e.output().clone(), idx));
            }
        }

        // ページング
        if (page > 0) gui.setItem(45, icon(Material.ARROW, "§e← 前のページ"));
        if (page < totalPages - 1) gui.setItem(53, icon(Material.ARROW, "§e次のページ →"));

        player.openInventory(gui);
    }

    private static void openMachineProcessRecipeDetail(Player player, MachineType type, int recipeIdx) {
        List<ProcessEntry> recipes = getProcessRecipes(type);
        if (recipeIdx < 0 || recipeIdx >= recipes.size()) return;
        ProcessEntry e = recipes.get(recipeIdx);

        Inventory gui = Bukkit.createInventory(
                new GuideHolder("machine_recipe_detail", type.name() + "|" + recipeIdx), 27,
                "§b§lレシピ詳細");

        fill(gui, 0, 26, pane(Component.text("")));
        gui.setItem(0, icon(Material.ARROW, "§c← 戻る"));
        gui.setItem(4, icon(Material.KNOWLEDGE_BOOK, "§b§lレシピ詳細",
                "§7" + type.displayName,
                "§8" + type.description));

        // 入力 → 出力 を中央に表示
        ItemStack inputDisplay = e.input().clone();
        ItemMeta im = inputDisplay.getItemMeta();
        if (im != null) {
            List<Component> lore = im.lore() != null ? new ArrayList<>(im.lore()) : new ArrayList<>();
            lore.add(Component.text("§e■ 入力素材"));
            im.lore(lore);
            inputDisplay.setItemMeta(im);
        }
        gui.setItem(11, inputDisplay);

        // 矢印 + 情報
        gui.setItem(13, icon(Material.PAPER, "§e→", e.info(), "§8クリックでリストに戻る"));

        // 出力
        ItemStack outputDisplay = e.output().clone();
        ItemMeta om = outputDisplay.getItemMeta();
        if (om != null) {
            List<Component> lore = om.lore() != null ? new ArrayList<>(om.lore()) : new ArrayList<>();
            lore.addAll(List.of(
                    Component.text(""),
                    Component.text("§e■ 出力"),
                    Component.text("§7" + type.displayName + " で処理")
            ));
            om.lore(lore);
            outputDisplay.setItemMeta(om);
        }
        gui.setItem(15, outputDisplay);

        // 追加情報
        gui.setItem(22, icon(Material.REDSTONE,
                "§6処理情報",
                "§7" + e.info(),
                net.circuitsurvival.managers.EnergyManager.needsEnergy(type)
                        ? "§c要EN: " + net.circuitsurvival.managers.EnergyManager.getEnergyCost(type) + "/回"
                        : "§7EN不要"));

        player.openInventory(gui);
    }

    // ==========================================================================
    // イベント処理
    // ==========================================================================

    private static String formatMaterialName(Material mat) {
        String name = mat.name().toLowerCase().replace('_', ' ');
        StringBuilder sb = new StringBuilder();
        boolean cap = true;
        for (char c : name.toCharArray()) {
            if (cap) { sb.append(Character.toUpperCase(c)); cap = false; }
            else if (c == ' ') { sb.append(c); cap = true; }
            else sb.append(c);
        }
        return sb.toString();
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof GuideHolder h)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player p)) return;
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;
        int slot = event.getRawSlot();

        switch (h.page()) {
            case "main" -> {
                switch (slot) {
                    case 10 -> openTierProgression(p);
                    case 12 -> openMaterialRecipes(p, 0);
                    case 14 -> openEnergyGuide(p, 0);
                    case 16 -> openMachineList(p, 0);
                    case 22 -> openGettingStarted(p, 0);
                }
            }
            case "tier" -> {
                if (slot == 0) openMain(p);
            }
            case "mat_list" -> {
                int page = h.data() instanceof Integer i ? i : 0;
                if (slot == 0) { openMain(p); return; }
                if (slot == 45 && page > 0) { openMaterialRecipes(p, page - 1); return; }
                if (slot == 53) { openMaterialRecipes(p, page + 1); return; }

                List<CustomCrafterRecipe> allRecipes = CustomCrafterRecipe.getAll();
                int idx = page * 36 + slot;
                if (idx >= 0 && idx < allRecipes.size()) {
                    openMaterialRecipeDetail(p, allRecipes.get(idx).id());
                }
            }
            case "mat_detail" -> {
                if (slot == 0) openMaterialRecipes(p, 0);
            }
            case "energy" -> {
                int page = h.data() instanceof Integer i ? i : 0;
                if (slot == 0) { openMain(p); return; }
                if (slot == 45 && page > 0) { openEnergyGuide(p, page - 1); return; }
                if (slot == 53) { openEnergyGuide(p, page + 1); return; }
            }
            case "machine" -> {
                int page = h.data() instanceof Integer i ? i : 0;
                if (slot == 0) { openMain(p); return; }
                if (slot == 45 && page > 0) { openMachineList(p, page - 1); return; }
                if (slot == 53) { openMachineList(p, page + 1); return; }
                // スロット内の機械をクリック → 詳細/レシピ
                int idx = page * 27 + slot;
                MachineType[] allTypes = MachineType.values();
                if (idx >= 0 && idx < allTypes.length) {
                    openMachineRecipeDetail(p, allTypes[idx]);
                }
            }
            case "machine_detail" -> {
                String raw = h.data() instanceof String s ? s : "";
                String[] parts = raw.split("\\|");
                if (parts.length < 1) { openMachineList(p, 0); return; }
                MachineType mt = MachineType.valueOf(parts[0]);
                int outPage = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
                if (slot == 0) { openMachineList(p, 0); return; }
                if (slot == 50) { openMachineProcessRecipes(p, mt, 0); return; }
                if (slot == 45 && outPage > 0) { openMachineRecipeDetail(p, mt, outPage - 1); return; }
                if (slot == 53) { openMachineRecipeDetail(p, mt, outPage + 1); return; }
            }
            case "machine_process" -> {
                String raw = h.data() instanceof String s ? s : "";
                String[] parts = raw.split("\\|");
                if (parts.length < 2) { openMachineList(p, 0); return; }
                MachineType mt = MachineType.valueOf(parts[0]);
                int pg = Integer.parseInt(parts[1]);
                if (slot == 0) { openMachineRecipeDetail(p, mt, 0); return; }
                if (slot == 45 && pg > 0) { openMachineProcessRecipes(p, mt, pg - 1); return; }
                if (slot == 53) { openMachineProcessRecipes(p, mt, pg + 1); return; }
                // レシピアイテムクリック → 詳細表示
                if (clicked.hasItemMeta()) {
                    Integer idx = clicked.getItemMeta().getPersistentDataContainer()
                            .get(PROCESS_RECIPE_KEY, org.bukkit.persistence.PersistentDataType.INTEGER);
                    if (idx != null) {
                        openMachineProcessRecipeDetail(p, mt, idx);
                    }
                }
            }
            case "machine_recipe_detail" -> {
                String raw = h.data() instanceof String s ? s : "";
                String[] parts = raw.split("\\|");
                if (parts.length < 2) { openMachineList(p, 0); return; }
                MachineType mt = MachineType.valueOf(parts[0]);
                int rcp = Integer.parseInt(parts[1]);
                if (slot == 0) { openMachineProcessRecipes(p, mt, rcp / 8); return; }
            }
            case "guide_steps" -> {
                int gPage = h.data() instanceof Integer i ? i : 0;
                if (slot == 0) { openMain(p); return; }
                if (slot == 45 && gPage > 0) { openGettingStarted(p, gPage - 1); return; }
                if (slot == 53 && gPage < 6) { openGettingStarted(p, gPage + 1); return; }
            }
        }
    }
}