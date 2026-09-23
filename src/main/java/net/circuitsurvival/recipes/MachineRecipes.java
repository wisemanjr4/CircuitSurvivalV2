package net.circuitsurvival.recipes;

import net.circuitsurvival.items.IntermediateMaterials;
import net.circuitsurvival.machines.MachineType;
import net.circuitsurvival.managers.PipeManager.PipeType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.List;

/**
 * カスタムデバイス (パイプ・論理ゲート・マシン) のクラフトレシピ登録
 *
 * ■ パイプ系
 *   アイテムパイプ (8)    : 鉄格子 + レッドストーン
 *   フィルターパイプ (4) : 鉄インゴット + コンパレーター + ホッパー
 *   ワイヤレス送信機 (1) : オブザーバー + エンダーパール4 + 金インゴット4
 *   ワイヤレス受信機 (1) : レッドストーンランプ + エンダーパール4 + 金インゴット4
 *
 * ■ ワイヤレスRS
 *   RS送信機 (1) : オブザーバー + レッドストーンブロック + 金インゴット4
 *   RS受信機 (1) : レッドストーンランプ + レッドストーンブロック + 金インゴット4
 *
 * ■ 論理ゲート
 *   ANDゲート (1) : コンパレーター + レッドストーントーチ4 + 石 + 海のランタン
 *   ORゲート  (1) : リピーター + レッドストーン4 + 石 + 海のランタン
 *   NOTゲート (1) : レバー + レッドストーントーチ + 石5 + 海のランタン
 *   XORゲート (1) : コンパレーター2 + リピーター + 石4 + 海のランタン
 *
 * ■ マシン
 *   粉砕機   (1) : ピストン2 + 鉄インゴット4 + 砂利 + 石切り台
 *   圧縮機   (1) : ピストン + 鉄格子4 + 石切り台 + レッドストーン
 *   タイマー (1) : 時計1 + リピーター2 + 日照センサー
 */
public class MachineRecipes {

    private final Plugin plugin;
    private final NamespacedKey pipeTypeKey;
    private final NamespacedKey gateTypeKey;
    private final NamespacedKey machineTypeKey;
    private final NamespacedKey rsBlockTypeKey;

    public MachineRecipes(Plugin plugin,
                          NamespacedKey pipeTypeKey,
                          NamespacedKey gateTypeKey,
                          NamespacedKey machineTypeKey,
                          NamespacedKey rsBlockTypeKey) {
        this.plugin        = plugin;
        this.pipeTypeKey   = pipeTypeKey;
        this.gateTypeKey   = gateTypeKey;
        this.machineTypeKey = machineTypeKey;
        this.rsBlockTypeKey = rsBlockTypeKey;
    }

    public void register() {
        registerPipeRecipes();
        registerWirelessRSRecipes();
        registerGateRecipes();
        registerMachineRecipes();
    }

    // =====================================================================
    // パイプ系
    // =====================================================================

    private void registerPipeRecipes() {

        // ── アイテムパイプ x8 ───────────────────────────────────────────
        plugin.getServer().addRecipe(new ShapedRecipe(
                new NamespacedKey(plugin, "item_pipe"),
                pipeItem(PipeType.PIPE, 8, "アイテムパイプ",
                        "双方向パイプ。能動的に起動", "隣接するコンテナを自動認識"))
                .shape("IRI", "R R", "IRI")
                .setIngredient('I', Material.IRON_BARS)
                .setIngredient('R', Material.REDSTONE));

        // ── 入力パイプ x4 ──────────────────────────────────────────────
        plugin.getServer().addRecipe(new ShapedRecipe(
                new NamespacedKey(plugin, "input_pipe"),
                pipeItem(PipeType.INPUT_PIPE, 4, "入力パイプ",
                        "隣接コンテナからアイテムを引き出す起点",
                        "チェーンの始端に使う"))
                .shape("IRI", "G G", "IRI")
                .setIngredient('I', Material.IRON_BARS)
                .setIngredient('R', Material.REDSTONE)
                .setIngredient('G', Material.GOLD_INGOT));

        // ── 出力パイプ x4 ──────────────────────────────────────────────
        plugin.getServer().addRecipe(new ShapedRecipe(
                new NamespacedKey(plugin, "output_pipe"),
                pipeItem(PipeType.OUTPUT_PIPE, 4, "出力パイプ",
                        "パイプチェーン終端。隣接コンテナへ押し込む先",
                        "自ら起動しない受動型パイプ"))
                .shape("IRI", "L L", "IRI")
                .setIngredient('I', Material.IRON_BARS)
                .setIngredient('R', Material.REDSTONE)
                .setIngredient('L', Material.LAPIS_LAZULI));

        // ── フィルターパイプ x4 ─────────────────────────────────────────
        plugin.getServer().addRecipe(new ShapedRecipe(
                new NamespacedKey(plugin, "filter_pipe"),
                pipeItem(PipeType.FILTER, 4, "フィルターパイプ",
                        "右クリックでフィルター設定", "手に持ったアイテムのみ通過"))
                .shape("ICI", "CHC", "ICI")
                .setIngredient('I', Material.IRON_INGOT)
                .setIngredient('C', Material.COMPARATOR)
                .setIngredient('H', Material.HOPPER));

        // ── 高速パイプ x8 ───────────────────────────────────────────
        plugin.getServer().addRecipe(new ShapedRecipe(
                new NamespacedKey(plugin, "fast_pipe"),
                pipeItem(PipeType.FAST_PIPE, 8, "高速パイプ",
                        "2倍速でアイテムを転送する強化パイプ"))
                .shape("SPS", "PPP", "SPS")
                .setIngredient('S', new RecipeChoice.ExactChoice(
                        IntermediateMaterials.build(IntermediateMaterials.STEEL_INGOT)))
                .setIngredient('P', new RecipeChoice.ExactChoice(
                        IntermediateMaterials.build(IntermediateMaterials.PIPE_CONNECTOR))));

        // ── 高速入力パイプ x4 ─────────────────────────────────────────
        plugin.getServer().addRecipe(new ShapedRecipe(
                new NamespacedKey(plugin, "fast_input_pipe"),
                pipeItem(PipeType.FAST_INPUT_PIPE, 4, "高速入力パイプ",
                        "2倍速でコンテナからアイテムを引き出す"))
                .shape("SPS", "G G", "SPS")
                .setIngredient('S', new RecipeChoice.ExactChoice(
                        IntermediateMaterials.build(IntermediateMaterials.STEEL_INGOT)))
                .setIngredient('P', new RecipeChoice.ExactChoice(
                        IntermediateMaterials.build(IntermediateMaterials.PIPE_CONNECTOR)))
                .setIngredient('G', Material.GOLD_INGOT));

        // ── 高速出力パイプ x4 ─────────────────────────────────────────
        plugin.getServer().addRecipe(new ShapedRecipe(
                new NamespacedKey(plugin, "fast_output_pipe"),
                pipeItem(PipeType.FAST_OUTPUT_PIPE, 4, "高速出力パイプ",
                        "高速パイプ網の終端。隣接コンテナへ高速排出"))
                .shape("SPS", "L L", "SPS")
                .setIngredient('S', new RecipeChoice.ExactChoice(
                        IntermediateMaterials.build(IntermediateMaterials.STEEL_INGOT)))
                .setIngredient('P', new RecipeChoice.ExactChoice(
                        IntermediateMaterials.build(IntermediateMaterials.PIPE_CONNECTOR)))
                .setIngredient('L', Material.LAPIS_LAZULI));

        // ── ワイヤレス送信機 x1 ─────────────────────────────────────────
        plugin.getServer().addRecipe(new ShapedRecipe(
                new NamespacedKey(plugin, "wireless_tx"),
                pipeItem(PipeType.WIRELESS_TX, 1, "ワイヤレス送信機",
                        "同チャンネルの受信機へアイテムを送信",
                        "/cs pipe channel <名> でチャンネル設定"))
                .shape("GEG", "EOE", "GEG")
                .setIngredient('G', Material.GOLD_INGOT)
                .setIngredient('E', Material.ENDER_PEARL)
                .setIngredient('O', Material.OBSERVER));

        // ── ワイヤレス受信機 x1 ─────────────────────────────────────────
        plugin.getServer().addRecipe(new ShapedRecipe(
                new NamespacedKey(plugin, "wireless_rx"),
                pipeItem(PipeType.WIRELESS_RX, 1, "ワイヤレス受信機",
                        "同チャンネルの送信機からアイテムを受信",
                        "/cs pipe channel <名> でチャンネル設定"))
                .shape("GEG", "EKE", "GEG")
                .setIngredient('G', Material.GOLD_INGOT)
                .setIngredient('E', Material.ENDER_PEARL)
                .setIngredient('K', Material.ENDER_EYE));
    }

    // =====================================================================
    // ワイヤレスレッドストーン
    // =====================================================================

    private void registerWirelessRSRecipes() {

        // ── RS送信機 x1 ─────────────────────────────────────────────────
        plugin.getServer().addRecipe(new ShapedRecipe(
                new NamespacedKey(plugin, "rs_transmitter"),
                rsItem("TRANSMITTER", Material.OBSERVER, 1, "ワイヤレスRS送信機",
                        "隣接するRS信号をチャンネルで送信",
                        "/cs pipe channel <名> でチャンネル設定"))
                .shape("GRG", "ROG", "GRG")
                .setIngredient('G', Material.GOLD_INGOT)
                .setIngredient('R', Material.REDSTONE)
                .setIngredient('O', Material.OBSERVER));

        // ── RS受信機 x1 ─────────────────────────────────────────────────
        plugin.getServer().addRecipe(new ShapedRecipe(
                new NamespacedKey(plugin, "rs_receiver"),
                rsItem("RECEIVER", Material.REDSTONE_LAMP, 1, "ワイヤレスRS受信機",
                        "チャンネルの信号を南面に出力",
                        "/cs pipe channel <名> でチャンネル設定"))
                .shape("GRG", "RLR", "GRG")
                .setIngredient('G', Material.GOLD_INGOT)
                .setIngredient('R', Material.REDSTONE)
                .setIngredient('L', Material.REDSTONE_LAMP));
    }

    // =====================================================================
    // 論理ゲート
    // =====================================================================

    private void registerGateRecipes() {

        // ── ANDゲート x1 ────────────────────────────────────────────────
        plugin.getServer().addRecipe(new ShapedRecipe(
                new NamespacedKey(plugin, "gate_and"),
                gateItem("AND", 1, "ANDゲート", "全入力ON → 出力ON", "北・東・西が入力、南が出力"))
                .shape("STS", "TLT", "SCS")
                .setIngredient('S', Material.STONE_BRICKS)
                .setIngredient('T', Material.REDSTONE_TORCH)
                .setIngredient('L', Material.SEA_LANTERN)
                .setIngredient('C', Material.COMPARATOR));

        // ── ORゲート x1 ─────────────────────────────────────────────────
        plugin.getServer().addRecipe(new ShapedRecipe(
                new NamespacedKey(plugin, "gate_or"),
                gateItem("OR", 1, "ORゲート", "いずれかの入力ON → 出力ON", "北・東・西が入力、南が出力"))
                .shape("SRS", "RLR", "STS")
                .setIngredient('S', Material.STONE_BRICKS)
                .setIngredient('R', Material.REDSTONE)
                .setIngredient('L', Material.SEA_LANTERN)
                .setIngredient('T', Material.REDSTONE_TORCH));

        // ── NOTゲート x1 ────────────────────────────────────────────────
        plugin.getServer().addRecipe(new ShapedRecipe(
                new NamespacedKey(plugin, "gate_not"),
                gateItem("NOT", 1, "NOTゲート", "入力OFF → 出力ON", "北・東・西が入力、南が出力"))
                .shape("SSS", "VLT", "SSS")
                .setIngredient('S', Material.STONE_BRICKS)
                .setIngredient('V', Material.LEVER)
                .setIngredient('L', Material.SEA_LANTERN)
                .setIngredient('T', Material.REDSTONE_TORCH));

        // ── XORゲート x1 ────────────────────────────────────────────────
        plugin.getServer().addRecipe(new ShapedRecipe(
                new NamespacedKey(plugin, "gate_xor"),
                gateItem("XOR", 1, "XORゲート", "奇数個の入力がON → 出力ON", "北・東・西が入力、南が出力"))
                .shape("SCS", "CLC", "SRS")
                .setIngredient('S', Material.STONE_BRICKS)
                .setIngredient('C', Material.COMPARATOR)
                .setIngredient('L', Material.SEA_LANTERN)
                .setIngredient('R', Material.REPEATER));
    }

    // =====================================================================
    // マシン
    // =====================================================================

    private void registerMachineRecipes() {

        // ── 粉砕機 x1 ──────────────────────────────────────────────────
        plugin.getServer().addRecipe(new ShapedRecipe(
                new NamespacedKey(plugin, "machine_crusher"),
                machineItem(MachineType.CRUSHER, 1))
                .shape("IPI", "PGP", "ISI")
                .setIngredient('I', Material.IRON_INGOT)
                .setIngredient('P', Material.PISTON)
                .setIngredient('G', Material.GRAVEL)
                .setIngredient('S', Material.STONECUTTER));

        // ── 圧縮機 x1 ───────────────────────────────────────────────────
        plugin.getServer().addRecipe(new ShapedRecipe(
                new NamespacedKey(plugin, "machine_compressor"),
                machineItem(MachineType.COMPRESSOR, 1))
                .shape("BIB", "ISI", "BRB")
                .setIngredient('B', Material.IRON_BARS)
                .setIngredient('I', Material.IRON_INGOT)
                .setIngredient('S', Material.STONECUTTER)
                .setIngredient('R', Material.REDSTONE));

        // ── タイマー x1 ─────────────────────────────────────────────────
        plugin.getServer().addRecipe(new ShapedRecipe(
                new NamespacedKey(plugin, "machine_timer"),
                machineItem(MachineType.TIMER, 1))
                .shape("CRC", "RDR", "CRC")
                .setIngredient('C', Material.CLOCK)
                .setIngredient('R', Material.REPEATER)
                .setIngredient('D', Material.DAYLIGHT_DETECTOR));

        // ── ブロック設置装置 x1 ─────────────────────────────────────────
        plugin.getServer().addRecipe(new ShapedRecipe(
                new NamespacedKey(plugin, "machine_block_placer"),
                machineItem(MachineType.BLOCK_PLACER, 1))
                .shape("IDI", "P P", "IRI")
                .setIngredient('I', Material.IRON_INGOT)
                .setIngredient('D', Material.DISPENSER)
                .setIngredient('P', Material.PISTON)
                .setIngredient('R', Material.REDSTONE));

        // ── ブロック破壊装置 x1 ─────────────────────────────────────────
        plugin.getServer().addRecipe(new ShapedRecipe(
                new NamespacedKey(plugin, "machine_block_breaker"),
                machineItem(MachineType.BLOCK_BREAKER, 1))
                .shape("IPI", "PKP", "IRI")
                .setIngredient('I', Material.IRON_INGOT)
                .setIngredient('P', Material.PISTON)
                .setIngredient('K', Material.IRON_PICKAXE)
                .setIngredient('R', Material.REDSTONE));

        // ── 自動クラフター x1 ───────────────────────────────────────────
        plugin.getServer().addRecipe(new ShapedRecipe(
                new NamespacedKey(plugin, "machine_auto_crafter"),
                machineItem(MachineType.AUTO_CRAFTER, 1))
                .shape("IRI", "RCR", "IRI")
                .setIngredient('I', Material.IRON_INGOT)
                .setIngredient('R', Material.REDSTONE)
                .setIngredient('C', Material.CRAFTING_TABLE));

        // ── アイテムソーター x1 ─────────────────────────────────────────
        plugin.getServer().addRecipe(new ShapedRecipe(
                new NamespacedKey(plugin, "machine_item_sorter"),
                machineItem(MachineType.ITEM_SORTER, 1))
                .shape("ICI", "CDC", "IRI")
                .setIngredient('I', Material.IRON_INGOT)
                .setIngredient('C', Material.COMPARATOR)
                .setIngredient('D', Material.DROPPER)
                .setIngredient('R', Material.REDSTONE));

        // ── 自動農場 x1 ─────────────────────────────────────────────────
        plugin.getServer().addRecipe(new ShapedRecipe(
                new NamespacedKey(plugin, "machine_auto_farmer"),
                machineItem(MachineType.AUTO_FARMER, 1))
                .shape("HHH", "HMH", "HRH")
                .setIngredient('H', Material.HAY_BLOCK)
                .setIngredient('M', Material.COMPOSTER)
                .setIngredient('R', Material.REDSTONE));

        // ── 着火装置 x1 ─────────────────────────────────────────────────
        plugin.getServer().addRecipe(new ShapedRecipe(
                new NamespacedKey(plugin, "machine_igniter"),
                machineItem(MachineType.IGNITER, 1))
                .shape("IFI", "FCF", "IRI")
                .setIngredient('I', Material.IRON_INGOT)
                .setIngredient('F', Material.FLINT)
                .setIngredient('C', Material.CAMPFIRE)
                .setIngredient('R', Material.REDSTONE));

        // ── 自動精錬炉 x1 ───────────────────────────────────────────────
        plugin.getServer().addRecipe(new ShapedRecipe(
                new NamespacedKey(plugin, "machine_auto_smelter"),
                machineItem(MachineType.AUTO_SMELTER, 1))
                .shape("ICI", "CFC", "IRI")
                .setIngredient('I', Material.IRON_INGOT)
                .setIngredient('C', Material.COPPER_INGOT)
                .setIngredient('F', Material.FURNACE)
                .setIngredient('R', Material.REDSTONE_BLOCK));

        // ── 採掘機 x1 ──────────────────────────────────────────────────
        plugin.getServer().addRecipe(new ShapedRecipe(
                new NamespacedKey(plugin, "machine_miner"),
                machineItem(MachineType.MINER, 1))
                .shape("IPI", "PKP", "DDD")
                .setIngredient('I', Material.IRON_INGOT)
                .setIngredient('P', Material.PISTON)
                .setIngredient('K', Material.IRON_PICKAXE)
                .setIngredient('D', Material.COBBLED_DEEPSLATE));

        // ── バキュームホッパー x1 ───────────────────────────────────────
        plugin.getServer().addRecipe(new ShapedRecipe(
                new NamespacedKey(plugin, "machine_vacuum_hopper"),
                machineItem(MachineType.VACUUM_HOPPER, 1))
                .shape("AHA", "HRH", "AHA")
                .setIngredient('A', Material.AMETHYST_SHARD)
                .setIngredient('H', Material.HOPPER)
                .setIngredient('R', Material.REDSTONE_BLOCK));

        // ── 右クリック代行装置 x1 ────────────────────────────────────────
        plugin.getServer().addRecipe(new ShapedRecipe(
                new NamespacedKey(plugin, "machine_right_clicker"),
                machineItem(MachineType.RIGHT_CLICKER, 1))
                .shape("IHI", "HDH", "IRI")
                .setIngredient('I', Material.IRON_INGOT)
                .setIngredient('H', Material.HOPPER)
                .setIngredient('D', Material.DISPENSER)
                .setIngredient('R', Material.REDSTONE));

        // ── エンティティ検知機 x1 ────────────────────────────────────────
        plugin.getServer().addRecipe(new ShapedRecipe(
                new NamespacedKey(plugin, "machine_entity_detector"),
                machineItem(MachineType.ENTITY_DETECTOR, 1))
                .shape("QAQ", "ASA", "QRQ")
                .setIngredient('Q', Material.QUARTZ)
                .setIngredient('A', Material.AMETHYST_SHARD)
                .setIngredient('S', Material.SCULK_SENSOR)
                .setIngredient('R', Material.REDSTONE));

        // ── チャンクローダー x1 ──────────────────────────────────────────
        plugin.getServer().addRecipe(new ShapedRecipe(
                new NamespacedKey(plugin, "machine_chunk_loader"),
                machineItem(MachineType.CHUNK_LOADER, 1))
                .shape("OOO", "OLO", "ORO")
                .setIngredient('O', Material.OBSIDIAN)
                .setIngredient('L', Material.LODESTONE)
                .setIngredient('R', Material.REDSTONE_BLOCK));

        // ── 自動醸造機 x1 ────────────────────────────────────────────────
        plugin.getServer().addRecipe(new ShapedRecipe(
                new NamespacedKey(plugin, "machine_auto_brewer"),
                machineItem(MachineType.AUTO_BREWER, 1))
                .shape("BAB", "ACA", "III")
                .setIngredient('B', Material.BLAZE_ROD)
                .setIngredient('A', Material.AMETHYST_SHARD)
                .setIngredient('C', Material.CAULDRON)
                .setIngredient('I', Material.IRON_INGOT));

        // ── アイテムルーター x1 ──────────────────────────────────────────
        plugin.getServer().addRecipe(new ShapedRecipe(
                new NamespacedKey(plugin, "machine_item_router"),
                machineItem(MachineType.ITEM_ROUTER, 1))
                .shape("HHH", "HTH", "HRH")
                .setIngredient('H', Material.HOPPER)
                .setIngredient('T', Material.TARGET)
                .setIngredient('R', Material.COMPARATOR));

        // ── ブロック変換炉 x1 ─────────────────────────────────────────────
        plugin.getServer().addRecipe(new ShapedRecipe(
                new NamespacedKey(plugin, "machine_block_transmuter"),
                machineItem(MachineType.BLOCK_TRANSMUTER, 1))
                .shape("NSN", "BSB", "NBN")
                .setIngredient('N', Material.NETHER_BRICK)
                .setIngredient('S', Material.SMOKER)
                .setIngredient('B', Material.BLAZE_ROD));

        // ── 流体コレクター x1 ────────────────────────────────────────────
        plugin.getServer().addRecipe(new ShapedRecipe(
                new NamespacedKey(plugin, "machine_fluid_collector"),
                machineItem(MachineType.FLUID_COLLECTOR, 1))
                .shape("CAC", "BAB", "CIC")
                .setIngredient('C', Material.COPPER_INGOT)
                .setIngredient('A', Material.POINTED_DRIPSTONE)
                .setIngredient('B', Material.BUCKET)
                .setIngredient('I', Material.IRON_INGOT));

        // ── 自動骨粉散布機 x1 ────────────────────────────────────────────
        plugin.getServer().addRecipe(new ShapedRecipe(
                new NamespacedKey(plugin, "machine_bone_mealer"),
                machineItem(MachineType.BONE_MEALER, 1))
                .shape("HMH", "MCM", "HMH")
                .setIngredient('H', Material.HOPPER)
                .setIngredient('M', Material.BONE_MEAL)
                .setIngredient('C', Material.COMPOSTER));

        // ── バキュームフィルター x1 ───────────────────────────────────────
        plugin.getServer().addRecipe(new ShapedRecipe(
                new NamespacedKey(plugin, "machine_vacuum_hopper_ctrl"),
                machineItem(MachineType.VACUUM_HOPPER_CTRL, 1))
                .shape("IAI", "ACA", "IAI")
                .setIngredient('I', Material.IRON_INGOT)
                .setIngredient('A', Material.AMETHYST_SHARD)
                .setIngredient('C', Material.CHISELED_STONE_BRICKS));

        // ── 大容量ストレージ x1 ─────────────────────────────────────────────
        plugin.getServer().addRecipe(new ShapedRecipe(
                new NamespacedKey(plugin, "machine_storage_drum"),
                machineItem(MachineType.STORAGE_DRUM, 1))
                .shape("IDI", "DCD", "IDI")
                .setIngredient('I', Material.IRON_INGOT)
                .setIngredient('D', Material.DEEPSLATE_BRICKS)
                .setIngredient('C', Material.CHEST));

        // ── ストレージコントローラー x1 ─────────────────────────────────────
        plugin.getServer().addRecipe(new ShapedRecipe(
                new NamespacedKey(plugin, "machine_storage_controller"),
                machineItem(MachineType.STORAGE_CONTROLLER, 1))
                .shape("OBO", "OGO", "OOO")
                .setIngredient('O', Material.OBSIDIAN)
                .setIngredient('B', Material.WRITABLE_BOOK)
                .setIngredient('G', Material.GOLD_BLOCK));

        // ── 製材機 x1 ──────────────────────────────────────────────────────
        //   I A I     I=鉄インゴット, A=鉄の斧, P=ピストン, W=オーク板材
        //   P W P
        //   I P I
        plugin.getServer().addRecipe(new ShapedRecipe(
                new NamespacedKey(plugin, "machine_woodcutter"),
                machineItem(MachineType.WOODCUTTER, 1))
                .shape("IAI", "PWP", "IPI")
                .setIngredient('I', Material.IRON_INGOT)
                .setIngredient('A', Material.IRON_AXE)
                .setIngredient('P', Material.PISTON)
                .setIngredient('W', Material.OAK_PLANKS));

        // ── 経験値変換炉 x1 ──────────────────────────────────────────────────
        plugin.getServer().addRecipe(new ShapedRecipe(
                new NamespacedKey(plugin, "machine_xp_converter"),
                machineItem(MachineType.XP_CONVERTER, 1))
                .shape("ABA", "BCB", "ABA")
                .setIngredient('A', Material.AMETHYST_BLOCK)
                .setIngredient('B', Material.BLAZE_ROD)
                .setIngredient('C', Material.SCULK_CATALYST));

        // ── 高速ホッパー (横型) x1 ────────────────────────────────────────────
        plugin.getServer().addRecipe(new ShapedRecipe(
                new NamespacedKey(plugin, "machine_fast_hopper"),
                machineItem(MachineType.FAST_HOPPER, 1))
                .shape("HCH", "CGC", "HCH")
                .setIngredient('H', Material.HOPPER)
                .setIngredient('C', Material.COPPER_INGOT)
                .setIngredient('G', Material.GOLD_BLOCK));

        // ── 縦型高速ホッパー x1 ──────────────────────────────────────────────
        plugin.getServer().addRecipe(new ShapedRecipe(
                new NamespacedKey(plugin, "machine_vert_fast_hopper"),
                machineItem(MachineType.VERT_FAST_HOPPER, 1))
                .shape("IHI", "HGH", "IHI")
                .setIngredient('I', Material.IRON_INGOT)
                .setIngredient('H', Material.HOPPER)
                .setIngredient('G', Material.GOLD_BLOCK));

        // ── 高速排出装置 x1 ──────────────────────────────────────────────────
        plugin.getServer().addRecipe(new ShapedRecipe(
                new NamespacedKey(plugin, "machine_bulk_dropper"),
                machineItem(MachineType.BULK_DROPPER, 1))
                .shape("PCP", "CDC", "PCP")
                .setIngredient('P', Material.PISTON)
                .setIngredient('C', Material.COPPER_INGOT)
                .setIngredient('D', Material.DROPPER));

        // ── 自動釣り機 x1 ─────────────────────────────────────────────────
        plugin.getServer().addRecipe(new ShapedRecipe(
                new NamespacedKey(plugin, "machine_auto_fisher"),
                machineItem(MachineType.AUTO_FISHER, 1))
                .shape("SFS", "HPH", "SFS")
                .setIngredient('S', Material.STRING)
                .setIngredient('F', Material.FISHING_ROD)
                .setIngredient('H', Material.HOPPER)
                .setIngredient('P', Material.PRISMARINE));

        // ── 料理台 x1 ────────────────────────────────────────────────────
        plugin.getServer().addRecipe(new ShapedRecipe(
                new NamespacedKey(plugin, "machine_cooking_station"),
                machineItem(MachineType.COOKING_STATION, 1))
                .shape("SSS", "IFI", "SSS")
                .setIngredient('S', Material.SMOOTH_STONE)
                .setIngredient('I', Material.IRON_INGOT)
                .setIngredient('F', Material.FURNACE));

        // ── ピクセル鍛冶炉 x1 ────────────────────────────────────────────
        plugin.getServer().addRecipe(new ShapedRecipe(
                new NamespacedKey(plugin, "machine_pixel_forge"),
                machineItem(MachineType.PIXEL_FORGE, 1))
                .shape("III", "DAD", "III")
                .setIngredient('I', Material.IRON_INGOT)
                .setIngredient('D', Material.DIAMOND)
                .setIngredient('A', Material.AMETHYST_BLOCK));

        // ── 垂直エレベーター x1 ─────────────────────────────────────────────
        //   I C I     I=鉄格子, C=銅ブロック, P=ピストン, R=レッドストーンブロック
        //   P R P
        //   I C I
        plugin.getServer().addRecipe(new ShapedRecipe(
                new NamespacedKey(plugin, "machine_vertical_elevator"),
                machineItem(MachineType.VERTICAL_ELEVATOR, 1))
                .shape("ICI", "PRP", "ICI")
                .setIngredient('I', Material.IRON_BARS)
                .setIngredient('C', Material.COPPER_BLOCK)
                .setIngredient('P', Material.PISTON)
                .setIngredient('R', Material.REDSTONE_BLOCK));

        // ── カスタムクラフター x1 ─────────────────────────────────────────────
        //   I C I     I=鉄インゴット, C=コンパレーター, R=レッドストーン
        //   C W C     W=作業台
        //   I R I
        plugin.getServer().addRecipe(new ShapedRecipe(
                new NamespacedKey(plugin, "machine_custom_crafter"),
                machineItem(MachineType.CUSTOM_CRAFTER, 1))
                .shape("ICI", "CWC", "IRI")
                .setIngredient('I', Material.IRON_INGOT)
                .setIngredient('C', Material.COMPARATOR)
                .setIngredient('W', Material.CRAFTING_TABLE)
                .setIngredient('R', Material.REDSTONE));

        // ── 粉砕精錬機 x1 ────────────────────────────────────────────────────
        //   I D I     I=鉄インゴット, D=ダイヤモンド, P=ピストン, C=銅ブロック
        //   P C P     C=銅ブロック
        //   I P I
        plugin.getServer().addRecipe(new ShapedRecipe(
                new NamespacedKey(plugin, "machine_pulverizer"),
                machineItem(MachineType.PULVERIZER, 1))
                .shape("IDI", "PCP", "IPI")
                .setIngredient('I', Material.IRON_INGOT)
                .setIngredient('D', Material.DIAMOND)
                .setIngredient('P', Material.PISTON)
                .setIngredient('C', Material.COPPER_BLOCK));

        // ── 電気精錬炉 x1 ────────────────────────────────────────────────────
        //   B C B     B=ブレイズロッド, C=銅ブロック, F=かまど, R=RSブロック
        //   C F C
        //   B R B
        plugin.getServer().addRecipe(new ShapedRecipe(
                new NamespacedKey(plugin, "machine_electric_furnace"),
                machineItem(MachineType.ELECTRIC_FURNACE, 1))
                .shape("BCB", "CFC", "BRB")
                .setIngredient('B', Material.BLAZE_ROD)
                .setIngredient('C', Material.COPPER_BLOCK)
                .setIngredient('F', Material.FURNACE)
                .setIngredient('R', Material.REDSTONE_BLOCK));

        // ── 自動金床 x1 ──────────────────────────────────────────────────────
        //   I I I     I=鉄ブロック, A=金床, P=ピストン
        //   A P A
        //   I P I
        plugin.getServer().addRecipe(new ShapedRecipe(
                new NamespacedKey(plugin, "machine_auto_anvil"),
                machineItem(MachineType.AUTO_ANVIL, 1))
                .shape("III", "APA", "IPI")
                .setIngredient('I', new RecipeChoice.ExactChoice(
                        IntermediateMaterials.build(IntermediateMaterials.MACHINE_CASING)))
                .setIngredient('A', Material.ANVIL)
                .setIngredient('P', Material.PISTON));

        // ── 日照発電機 x1 ───────────────────────────────────────────────────
        //   R D R     R=精錬鉄インゴット, D=日照センサー, C=銅ブロック, G=ガラス
        //   G C G
        //   R G R
        plugin.getServer().addRecipe(new ShapedRecipe(
                new NamespacedKey(plugin, "machine_solar_panel"),
                machineItem(MachineType.SOLAR_PANEL, 1))
                .shape("RDR", "GCG", "RGR")
                .setIngredient('R', new RecipeChoice.ExactChoice(
                        IntermediateMaterials.build(IntermediateMaterials.REFINED_IRON_INGOT)))
                .setIngredient('D', Material.DAYLIGHT_DETECTOR)
                .setIngredient('C', Material.COPPER_BLOCK)
                .setIngredient('G', Material.GLASS));

        // ── 自動羊毛刈り機 x1 ─────────────────────────────────────────────────
        //   I S I     I=鉄インゴット, S=ハサミ, P=ピストン, R=レッドストーン
        //   P O P     O=オブザーバー
        //   I R I
        plugin.getServer().addRecipe(new ShapedRecipe(
                new NamespacedKey(plugin, "machine_auto_shearer"),
                machineItem(MachineType.AUTO_SHEARER, 1))
                .shape("ISI", "POP", "IRI")
                .setIngredient('I', Material.IRON_INGOT)
                .setIngredient('S', Material.SHEARS)
                .setIngredient('P', Material.PISTON)
                .setIngredient('O', Material.OBSERVER)
                .setIngredient('R', Material.REDSTONE));

        // ── 発電機 x1 ─────────────────────────────────────────────────────────
        //   R C R     R=精錬鉄インゴット, C=銅ブロック, F=かまど, P=ピストン
        //   F P F
        //   R C R
        plugin.getServer().addRecipe(new ShapedRecipe(
                new NamespacedKey(plugin, "machine_generator"),
                machineItem(MachineType.GENERATOR, 1))
                .shape("RCR", "FPF", "RCR")
                .setIngredient('R', new RecipeChoice.ExactChoice(
                        IntermediateMaterials.build(IntermediateMaterials.REFINED_IRON_INGOT)))
                .setIngredient('C', Material.COPPER_BLOCK)
                .setIngredient('F', Material.FURNACE)
                .setIngredient('P', Material.PISTON));

        // ── 蓄電機 x1 ─────────────────────────────────────────────────────────
        //   G E G     G=ガラス, E=送電ワイヤー(ENERGY_CABLE), C=銅ブロック, R=RSブロック, I=精錬鉄
        //   C R C
        //   I C I
        plugin.getServer().addRecipe(new ShapedRecipe(
                new NamespacedKey(plugin, "machine_energy_cell"),
                machineItem(MachineType.ENERGY_CELL, 1))
                .shape("GEG", "CRC", "ICI")
                .setIngredient('G', Material.GLASS)
                .setIngredient('E', Material.IRON_BARS)
                .setIngredient('C', Material.COPPER_BLOCK)
                .setIngredient('R', Material.REDSTONE_BLOCK)
                .setIngredient('I', new RecipeChoice.ExactChoice(
                        IntermediateMaterials.build(IntermediateMaterials.REFINED_IRON_INGOT))));

        // ── 充電器 x1 ─────────────────────────────────────────────────────────
        //   I C I     I=鉄インゴット, C=銅ブロック, R=レッドストーン, B=バッテリー(circuit_board+RS相当)
        //   R B R
        //   I C I
        plugin.getServer().addRecipe(new ShapedRecipe(
                new NamespacedKey(plugin, "machine_charger"),
                machineItem(MachineType.CHARGER, 1))
                .shape("ICI", "RBR", "ICI")
                .setIngredient('I', Material.IRON_INGOT)
                .setIngredient('C', Material.COPPER_BLOCK)
                .setIngredient('R', Material.REDSTONE)
                .setIngredient('B', Material.GLOWSTONE_DUST));

        // ── 送電ワイヤー x8 ───────────────────────────────────────────────────
        //   I R I     I=鉄格子, R=レッドストーン, C=銅インゴット (中央のみ銅でパイプと差別化)
        //   R C R
        //   I R I
        plugin.getServer().addRecipe(new ShapedRecipe(
                new NamespacedKey(plugin, "machine_energy_cable"),
                machineItem(MachineType.ENERGY_CABLE, 8))
                .shape("IRI", "RCR", "IRI")
                .setIngredient('I', Material.IRON_BARS)
                .setIngredient('R', Material.REDSTONE)
                .setIngredient('C', Material.COPPER_INGOT));

        // ── 高圧蓄電機 x1 ──────────────────────────────────────────────────────
        //   D L D     D=ダイヤブロック, L=ラピスブロック, C=銅ブロック, S=超伝導体
        //   C S C
        //   D L D
        plugin.getServer().addRecipe(new ShapedRecipe(
                new NamespacedKey(plugin, "machine_hv_cell"),
                machineItem(MachineType.HV_CELL, 1))
                .shape("DLD", "CSC", "DLD")
                .setIngredient('D', Material.DIAMOND_BLOCK)
                .setIngredient('L', Material.LAPIS_BLOCK)
                .setIngredient('C', Material.COPPER_BLOCK)
                .setIngredient('S', Material.LIGHT_BLUE_DYE));

        // ── 自動エンチャンター x1 ──────────────────────────────────────────────
        //   O B O     O=黒曜石, B=本棚, E=エンダーアイ, R=RSブロック
        //   B E B
        //   O R O
        plugin.getServer().addRecipe(new ShapedRecipe(
                new NamespacedKey(plugin, "machine_auto_enchanter"),
                machineItem(MachineType.AUTO_ENCHANTER, 1))
                .shape("OBO", "BEB", "ORO")
                .setIngredient('O', Material.OBSIDIAN)
                .setIngredient('B', Material.BOOKSHELF)
                .setIngredient('E', Material.ENDER_EYE)
                .setIngredient('R', Material.REDSTONE_BLOCK));

        // ── 誘導溶解炉 x1 ─────────────────────────────────────────────────────
        //   B C B     B=ブレイズロッド, C=銅ブロック, F=かまど, R=RSブロック
        //   C F C
        //   B R B
        plugin.getServer().addRecipe(new ShapedRecipe(
                new NamespacedKey(plugin, "machine_induction_furnace"),
                machineItem(MachineType.INDUCTION_FURNACE, 1))
                .shape("BCB", "CFC", "BRB")
                .setIngredient('B', Material.BLAZE_ROD)
                .setIngredient('C', Material.COPPER_BLOCK)
                .setIngredient('F', Material.FURNACE)
                .setIngredient('R', Material.REDSTONE_BLOCK));

        // ── 遠心分離機 x1 ─────────────────────────────────────────────────────
        //   I M I     I=鉄インゴット, M=ピストン, C=銅ブロック, P=RSブロック
        //   M C M
        //   I P I
        plugin.getServer().addRecipe(new ShapedRecipe(
                new NamespacedKey(plugin, "machine_centrifuge"),
                machineItem(MachineType.CENTRIFUGE, 1))
                .shape("IMI", "MCM", "IPI")
                .setIngredient('I', Material.IRON_INGOT)
                .setIngredient('M', Material.PISTON)
                .setIngredient('C', Material.COPPER_BLOCK)
                .setIngredient('P', Material.REDSTONE_BLOCK));

        // ── 燃焼発電機 x1 ─────────────────────────────────────────────────────
        //   C B C     C=圧縮鉄ブロック, B=ブレイズロッド, G=精錬金インゴット, R=RSブロック
        //   B G B
        //   C R C
        plugin.getServer().addRecipe(new ShapedRecipe(
                new NamespacedKey(plugin, "machine_combustion_generator"),
                machineItem(MachineType.COMBUSTION_GENERATOR, 1))
                .shape("CBC", "BGB", "CRC")
                .setIngredient('C', new RecipeChoice.ExactChoice(
                        IntermediateMaterials.build(IntermediateMaterials.COMPRESSED_IRON)))
                .setIngredient('B', Material.BLAZE_ROD)
                .setIngredient('G', new RecipeChoice.ExactChoice(
                        IntermediateMaterials.build(IntermediateMaterials.REFINED_GOLD_INGOT)))
                .setIngredient('R', Material.REDSTONE_BLOCK));

        // ── 鉱石三段加工機 x1 ─────────────────────────────────────────────────
        //   D C D     D=ダイヤモンド, C=銅ブロック, P=ピストン, R=RSブロック
        //   C P C
        //   D R D
        plugin.getServer().addRecipe(new ShapedRecipe(
                new NamespacedKey(plugin, "machine_ore_processor"),
                machineItem(MachineType.ORE_PROCESSOR, 1))
                .shape("DCD", "CPC", "DRD")
                .setIngredient('D', Material.DIAMOND)
                .setIngredient('C', Material.COPPER_BLOCK)
                .setIngredient('P', Material.PISTON)
                .setIngredient('R', Material.REDSTONE_BLOCK));

        // ── 物質生成機 x1 ─────────────────────────────────────────────────────
        //   Q V Q     Q=量子チップ(VOID_CRYSTAL), V=虚空結晶, C=高度回路, R=強化筐体
        //   C A C     A=金床
        //   R R R
        plugin.getServer().addRecipe(new ShapedRecipe(
                new NamespacedKey(plugin, "machine_materializer"),
                machineItem(MachineType.MATERIALIZER, 1))
                .shape("QVQ", "CAC", "RRR")
                .setIngredient('Q', Material.QUARTZ_BLOCK)
                .setIngredient('V', Material.CRYING_OBSIDIAN)
                .setIngredient('C', Material.COMPARATOR)
                .setIngredient('A', Material.ANVIL)
                .setIngredient('R', Material.REDSTONE_BLOCK));

        // ── リサイクル機 x1 ──────────────────────────────────────────────────
        //   I C I     I=鉄インゴット, C=高度回路, P=ピストン, R=RSブロック
        //   P R P
        //   I C I
        plugin.getServer().addRecipe(new ShapedRecipe(
                new NamespacedKey(plugin, "machine_recycler"),
                machineItem(MachineType.RECYCLER, 1))
                .shape("ICI", "PRP", "ICI")
                .setIngredient('I', Material.IRON_INGOT)
                .setIngredient('C', Material.COMPARATOR)
                .setIngredient('P', Material.PISTON)
                .setIngredient('R', Material.REDSTONE_BLOCK));

        // ── ワイヤレス充電器 x1 ──────────────────────────────────────────────
        //   E C E     E=エンダーパール, C=銅ブロック, S=超伝導体(SHULKER_SHELL代用)
        //   C S C
        //   E C E
        plugin.getServer().addRecipe(new ShapedRecipe(
                new NamespacedKey(plugin, "machine_wireless_charger"),
                machineItem(MachineType.WIRELESS_CHARGER, 1))
                .shape("ECE", "CSC", "ECE")
                .setIngredient('E', Material.ENDER_PEARL)
                .setIngredient('C', Material.COPPER_BLOCK)
                .setIngredient('S', Material.SHULKER_SHELL));

        // ── 自動解呪機 x1 ──────────────────────────────────────────────────
        //   O B O     O=黒曜石, B=本棚, E=エメラルド, R=RSブロック
        //   B E B
        //   O R O
        plugin.getServer().addRecipe(new ShapedRecipe(
                new NamespacedKey(plugin, "machine_auto_disenchanter"),
                machineItem(MachineType.AUTO_DISENCHANTER, 1))
                .shape("OBO", "BEB", "ORO")
                .setIngredient('O', Material.OBSIDIAN)
                .setIngredient('B', Material.BOOKSHELF)
                .setIngredient('E', Material.EMERALD)
                .setIngredient('R', Material.REDSTONE_BLOCK));

        // ── 熱発電機 x1 ──────────────────────────────────────────────────────
        //   H C H     H=HEATING_COIL(Copper Ingot), C=COOLANT_CELL(Packed Ice)
        //   M C M     M=MACHINE_CASING(Iron Block)
        //   H R H     R=REDSTONE_BLOCK
        plugin.getServer().addRecipe(new ShapedRecipe(
                new NamespacedKey(plugin, "machine_thermal_generator"),
                machineItem(MachineType.THERMAL_GENERATOR, 1))
                .shape("HCH", "MCM", "HRH")
                .setIngredient('H', Material.COPPER_INGOT)
                .setIngredient('C', Material.PACKED_ICE)
                .setIngredient('M', new RecipeChoice.ExactChoice(
                        IntermediateMaterials.build(IntermediateMaterials.MACHINE_CASING)))
                .setIngredient('R', Material.REDSTONE_BLOCK));

        // ── 高級組立機 x1 ─────────────────────────────────────────────────────
        //   G A G     G=HIGH_GEAR(Iron Block), A=ADVANCED_CIRCUIT(Repeater)
        //   Q C Q     Q=CHISELED_QUARTZ_BLOCK, C=MACHINE_CASING(Iron Block)
        //   G R G     R=REDSTONE_BLOCK
        plugin.getServer().addRecipe(new ShapedRecipe(
                new NamespacedKey(plugin, "machine_advanced_assembler"),
                machineItem(MachineType.ADVANCED_ASSEMBLER, 1))
                .shape("GAG", "QCQ", "GRG")
                .setIngredient('G', new RecipeChoice.ExactChoice(
                        IntermediateMaterials.build(IntermediateMaterials.HIGH_GEAR)))
                .setIngredient('A', Material.REPEATER)
                .setIngredient('Q', Material.CHISELED_QUARTZ_BLOCK)
                .setIngredient('C', new RecipeChoice.ExactChoice(
                        IntermediateMaterials.build(IntermediateMaterials.MACHINE_CASING)))
                .setIngredient('R', Material.REDSTONE_BLOCK));

        // ── 中性子圧縮機 x1 ──────────────────────────────────────────────────
        //   N R N     N=NEUTRON_REFLECTOR(Nether Bricks), R=REINFORCED_CASING(Obsidian)
        //   G D G     G=GOLD_BLOCK, D=STABILIZER(Diamond)
        //   N R N
        plugin.getServer().addRecipe(new ShapedRecipe(
                new NamespacedKey(plugin, "machine_neutron_compressor"),
                machineItem(MachineType.NEUTRON_COMPRESSOR, 1))
                .shape("NRN", "GDG", "NRN")
                .setIngredient('N', Material.NETHER_BRICKS)
                .setIngredient('R', Material.OBSIDIAN)
                .setIngredient('G', Material.GOLD_BLOCK)
                .setIngredient('D', Material.DIAMOND));

        // ── 移動プラットフォーム x1 ──────────────────────────────────────────
        //   I I I     I=鉄インゴット, P=ピストン, R=RSブロック
        //   P R P
        //   I I I
        plugin.getServer().addRecipe(new ShapedRecipe(
                new NamespacedKey(plugin, "machine_mobile_platform"),
                machineItem(MachineType.MOBILE_PLATFORM, 1))
                .shape("III", "PRP", "III")
                .setIngredient('I', Material.IRON_INGOT)
                .setIngredient('P', Material.PISTON)
                .setIngredient('R', Material.REDSTONE_BLOCK));

        // ── 機器制御アタッチメント x1 ──────────────────────────────────────────
        plugin.getServer().addRecipe(new ShapedRecipe(
                new NamespacedKey(plugin, "machine_block_control_attachment"),
                machineItem(MachineType.BLOCK_CONTROL_ATTACHMENT, 1))
                .shape(" R ", "ICI", " I ")
                .setIngredient('R', Material.REDSTONE)
                .setIngredient('I', Material.IRON_INGOT)
                .setIngredient('C', Material.COMPARATOR));

        // ── ゴミ箱 x1 ──────────────────────────────────────────────────────────
        plugin.getServer().addRecipe(new ShapedRecipe(
                new NamespacedKey(plugin, "machine_trash_can"),
                machineItem(MachineType.TRASH_CAN, 1))
                .shape("III", "IBI", "ILI")
                .setIngredient('I', Material.IRON_INGOT)
                .setIngredient('B', Material.BARREL)
                .setIngredient('L', Material.LAVA_BUCKET));

        // ── 洗浄機 x1 ──────────────────────────────────────────────────────────
        //   I B I     I=鉄インゴット, B=鉄格子, P=ピストン, H=ホッパー
        //   P H P
        //   I B I
        plugin.getServer().addRecipe(new ShapedRecipe(
                new NamespacedKey(plugin, "machine_washing_machine"),
                machineItem(MachineType.WASHING_MACHINE, 1))
                .shape("IBI", "PHP", "IBI")
                .setIngredient('I', Material.IRON_INGOT)
                .setIngredient('B', Material.IRON_BARS)
                .setIngredient('P', Material.PISTON)
                .setIngredient('H', Material.HOPPER));

        // ── クラフター制御装置 x1 ──────────────────────────────────────────
        //   Q R Q     Q=クォーツ, R=レッドストーン, C=回路基板
        //   R C R
        //   Q R Q
        plugin.getServer().addRecipe(new ShapedRecipe(
                new NamespacedKey(plugin, "machine_crafter_controller"),
                machineItem(MachineType.CRAFTER_CONTROLLER, 1))
                .shape("QRQ", "RCR", "QRQ")
                .setIngredient('Q', Material.QUARTZ)
                .setIngredient('R', Material.REDSTONE)
                .setIngredient('C', Material.REPEATER));

        // ── 水力発電機 x1 ─────────────────────────────────────────────────
        //   I B I     I=鉄インゴット, B=鉄格子, P=ピストン, C=バケツ
        //   P C P
        //   I B I
        plugin.getServer().addRecipe(new ShapedRecipe(
                new NamespacedKey(plugin, "machine_water_generator"),
                machineItem(MachineType.WATER_GENERATOR, 1))
                .shape("IBI", "PCP", "IBI")
                .setIngredient('I', Material.IRON_INGOT)
                .setIngredient('B', Material.IRON_BARS)
                .setIngredient('P', Material.PISTON)
                .setIngredient('C', Material.BUCKET));

        // ── 風力発電機 x1 ─────────────────────────────────────────────────
        //   I B I     I=鉄インゴット, B=鉄格子, P=ピストン
        //   B P B
        //   I B I
        plugin.getServer().addRecipe(new ShapedRecipe(
                new NamespacedKey(plugin, "machine_wind_generator"),
                machineItem(MachineType.WIND_GENERATOR, 1))
                .shape("IBI", "BPB", "IBI")
                .setIngredient('I', Material.IRON_INGOT)
                .setIngredient('B', Material.IRON_BARS)
                .setIngredient('P', Material.PISTON));

        // =====================================================================
        // 一括生産レシピ (やりこみ要素: 中間素材を使うと4個同時クラフト可能)
        // =====================================================================

        // ── 粉砕機 x4 ────────────────────────────────────────────────────
        plugin.getServer().addRecipe(new ShapedRecipe(
                new NamespacedKey(plugin, "bulk_crusher"),
                machineItem(MachineType.CRUSHER, 4))
                .shape("SAS", "PGP", "SAS")
                .setIngredient('S', new RecipeChoice.ExactChoice(
                        IntermediateMaterials.build(IntermediateMaterials.STEEL_INGOT)))
                .setIngredient('A', new RecipeChoice.ExactChoice(
                        IntermediateMaterials.build(IntermediateMaterials.ADVANCED_ALLOY)))
                .setIngredient('P', Material.PISTON)
                .setIngredient('G', Material.GRINDSTONE));

        // ── 圧縮機 x4 ────────────────────────────────────────────────────
        plugin.getServer().addRecipe(new ShapedRecipe(
                new NamespacedKey(plugin, "bulk_compressor"),
                machineItem(MachineType.COMPRESSOR, 4))
                .shape("SBS", "BSB", "SBS")
                .setIngredient('S', new RecipeChoice.ExactChoice(
                        IntermediateMaterials.build(IntermediateMaterials.STEEL_INGOT)))
                .setIngredient('B', Material.IRON_BLOCK));

        // ── 自動精錬炉 x4 ─────────────────────────────────────────────────
        plugin.getServer().addRecipe(new ShapedRecipe(
                new NamespacedKey(plugin, "bulk_auto_smelter"),
                machineItem(MachineType.AUTO_SMELTER, 4))
                .shape("SHS", "HFH", "SHS")
                .setIngredient('S', new RecipeChoice.ExactChoice(
                        IntermediateMaterials.build(IntermediateMaterials.STEEL_INGOT)))
                .setIngredient('H', new RecipeChoice.ExactChoice(
                        IntermediateMaterials.build(IntermediateMaterials.HEATING_COIL)))
                .setIngredient('F', Material.FURNACE));

        // ── パイプ x16 ────────────────────────────────────────────────────
        plugin.getServer().addRecipe(new ShapedRecipe(
                new NamespacedKey(plugin, "bulk_pipe"),
                pipeItem(PipeType.PIPE, 16, "アイテムパイプ",
                        "双方向パイプ。能動的に起動", "隣接するコンテナを自動認識"))
                .shape("SPS", "P P", "SPS")
                .setIngredient('S', new RecipeChoice.ExactChoice(
                        IntermediateMaterials.build(IntermediateMaterials.STEEL_INGOT)))
                .setIngredient('P', new RecipeChoice.ExactChoice(
                        IntermediateMaterials.build(IntermediateMaterials.PIPE_CONNECTOR))));

        // ── 送電ワイヤー x16 ──────────────────────────────────────────────
        plugin.getServer().addRecipe(new ShapedRecipe(
                new NamespacedKey(plugin, "bulk_energy_cable"),
                machineItem(MachineType.ENERGY_CABLE, 16))
                .shape("SRS", "RCR", "SRS")
                .setIngredient('S', new RecipeChoice.ExactChoice(
                        IntermediateMaterials.build(IntermediateMaterials.STEEL_INGOT)))
                .setIngredient('R', Material.REDSTONE)
                .setIngredient('C', Material.COPPER_INGOT));

        // ── ブロック設置装置 x4 ───────────────────────────────────────────
        plugin.getServer().addRecipe(new ShapedRecipe(
                new NamespacedKey(plugin, "bulk_block_placer"),
                machineItem(MachineType.BLOCK_PLACER, 4))
                .shape("SDS", "PMP", "SRS")
                .setIngredient('S', new RecipeChoice.ExactChoice(
                        IntermediateMaterials.build(IntermediateMaterials.STEEL_INGOT)))
                .setIngredient('D', Material.DISPENSER)
                .setIngredient('P', Material.PISTON)
                .setIngredient('M', new RecipeChoice.ExactChoice(
                        IntermediateMaterials.build(IntermediateMaterials.MOTOR)))
                .setIngredient('R', Material.REDSTONE));

        // ── ブロック破壊装置 x4 ───────────────────────────────────────────
        plugin.getServer().addRecipe(new ShapedRecipe(
                new NamespacedKey(plugin, "bulk_block_breaker"),
                machineItem(MachineType.BLOCK_BREAKER, 4))
                .shape("SPS", "PKP", "SRS")
                .setIngredient('S', new RecipeChoice.ExactChoice(
                        IntermediateMaterials.build(IntermediateMaterials.STEEL_INGOT)))
                .setIngredient('P', Material.PISTON)
                .setIngredient('K', Material.DIAMOND_PICKAXE)
                .setIngredient('R', Material.REDSTONE));

        // ── 高速パイプ x16 ────────────────────────────────────────────────
        plugin.getServer().addRecipe(new ShapedRecipe(
                new NamespacedKey(plugin, "bulk_fast_pipe"),
                pipeItem(PipeType.FAST_PIPE, 16, "高速パイプ",
                        "2倍速でアイテムを転送する強化パイプ", "一括生産 x16"))
                .shape("SPS", "PPP", "SPS")
                .setIngredient('S', new RecipeChoice.ExactChoice(
                        IntermediateMaterials.build(IntermediateMaterials.ADVANCED_ALLOY)))
                .setIngredient('P', new RecipeChoice.ExactChoice(
                        IntermediateMaterials.build(IntermediateMaterials.PIPE_CONNECTOR))));

        // ── 蒸留塔 x1 ──────────────────────────────────────────────────────
        plugin.getServer().addRecipe(new ShapedRecipe(
                new NamespacedKey(plugin, "machine_distillation_tower"),
                machineItem(MachineType.DISTILLATION_TOWER, 1))
                .shape("SGS", "GBG", "SHS")
                .setIngredient('S', new RecipeChoice.ExactChoice(
                        IntermediateMaterials.build(IntermediateMaterials.STEEL_INGOT)))
                .setIngredient('G', Material.GLASS)
                .setIngredient('B', Material.BUCKET)
                .setIngredient('H', new RecipeChoice.ExactChoice(
                        IntermediateMaterials.build(IntermediateMaterials.HEATING_COIL))));

        // ── 化学反応炉 x1 ──────────────────────────────────────────────────
        plugin.getServer().addRecipe(new ShapedRecipe(
                new NamespacedKey(plugin, "machine_chemical_reactor"),
                machineItem(MachineType.CHEMICAL_REACTOR, 1))
                .shape("SGS", "GBG", "SCS")
                .setIngredient('S', new RecipeChoice.ExactChoice(
                        IntermediateMaterials.build(IntermediateMaterials.STEEL_INGOT)))
                .setIngredient('G', Material.GLASS)
                .setIngredient('B', Material.BLAZE_ROD)
                .setIngredient('C', new RecipeChoice.ExactChoice(
                        IntermediateMaterials.build(IntermediateMaterials.CIRCUIT_BOARD))));

        // ── 真空溶解炉 x1 ──────────────────────────────────────────────────
        plugin.getServer().addRecipe(new ShapedRecipe(
                new NamespacedKey(plugin, "machine_vacuum_furnace"),
                machineItem(MachineType.VACUUM_FURNACE, 1))
                .shape("SAS", "OHO", "SAS")
                .setIngredient('S', new RecipeChoice.ExactChoice(
                        IntermediateMaterials.build(IntermediateMaterials.STEEL_INGOT)))
                .setIngredient('A', new RecipeChoice.ExactChoice(
                        IntermediateMaterials.build(IntermediateMaterials.ADVANCED_ALLOY)))
                .setIngredient('O', Material.OBSIDIAN)
                .setIngredient('H', new RecipeChoice.ExactChoice(
                        IntermediateMaterials.build(IntermediateMaterials.HEATING_COIL))));

        // ── 高圧プレス機 x1 ──────────────────────────────────────────────
        plugin.getServer().addRecipe(new ShapedRecipe(
                new NamespacedKey(plugin, "machine_high_pressure_press"),
                machineItem(MachineType.HIGH_PRESSURE_PRESS, 1))
                .shape("SPS", "DRD", "SCS")
                .setIngredient('S', new RecipeChoice.ExactChoice(
                        IntermediateMaterials.build(IntermediateMaterials.STEEL_INGOT)))
                .setIngredient('P', Material.PISTON)
                .setIngredient('D', Material.DIAMOND)
                .setIngredient('R', new RecipeChoice.ExactChoice(
                        IntermediateMaterials.build(IntermediateMaterials.REINFORCED_CASING)))
                .setIngredient('C', new RecipeChoice.ExactChoice(
                        IntermediateMaterials.build(IntermediateMaterials.CIRCUIT_BOARD))));
    }

    // =====================================================================
    // アイテム生成ヘルパー
    // =====================================================================

    private ItemStack pipeItem(PipeType type, int amount, String name, String... lore) {
        Material mat = switch (type) {
            case PIPE        -> Material.IRON_BARS;
            case INPUT_PIPE  -> Material.IRON_BARS;
            case OUTPUT_PIPE -> Material.IRON_BARS;
            case FAST_PIPE   -> Material.CHAIN;
            case FAST_INPUT_PIPE -> Material.CHAIN;
            case FAST_OUTPUT_PIPE -> Material.CHAIN;
            case FILTER      -> Material.CHAIN;
            case WIRELESS_TX, WIRELESS_RX -> Material.ENDER_CHEST;
        };
        ItemStack item = new ItemStack(mat, amount);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(colored(name, NamedTextColor.AQUA));
        meta.lore(loreList(lore));
        meta.getPersistentDataContainer().set(pipeTypeKey, PersistentDataType.STRING, type.name());
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack rsItem(String type, Material mat, int amount, String name, String... lore) {
        ItemStack item = new ItemStack(mat, amount);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(colored(name, NamedTextColor.RED));
        meta.lore(loreList(lore));
        meta.getPersistentDataContainer().set(rsBlockTypeKey, PersistentDataType.STRING, type);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack gateItem(String type, int amount, String name, String... lore) {
        ItemStack item = new ItemStack(Material.SEA_LANTERN, amount);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(colored(name, NamedTextColor.GREEN));
        meta.lore(loreList(lore));
        meta.getPersistentDataContainer().set(gateTypeKey, PersistentDataType.STRING, type);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack machineItem(MachineType type, int amount) {
        ItemStack item = new ItemStack(type.blockMaterial, amount);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(colored(type.displayName, NamedTextColor.GOLD));
        meta.lore(loreList(type.description, "右クリックでGUIを開く"));
        meta.getPersistentDataContainer().set(machineTypeKey, PersistentDataType.STRING, type.name());
        item.setItemMeta(meta);
        return item;
    }

    private Component colored(String text, NamedTextColor color) {
        return Component.text(text, color).decoration(TextDecoration.ITALIC, false);
    }

    private List<Component> loreList(String... lines) {
        return java.util.Arrays.stream(lines)
                .map(l -> (Component) Component.text(l, NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false))
                .toList();
    }
}
