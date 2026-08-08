package net.circuitsurvival;

import net.circuitsurvival.commands.SWECommand;
import net.circuitsurvival.guide.GuideManager;
import net.circuitsurvival.items.CustomItems;
import net.circuitsurvival.items.IntermediateMaterials;
import net.circuitsurvival.listeners.*;
import net.circuitsurvival.recipes.CustomCrafterRecipe;
import net.circuitsurvival.recipes.IntermediateRecipes;
import net.circuitsurvival.machines.MachineType;
import net.circuitsurvival.managers.*;
import net.circuitsurvival.recipes.CustomRecipes;
import net.circuitsurvival.recipes.MachineRecipes;
import net.circuitsurvival.recipes.RecipeKeyRegistry;
import net.circuitsurvival.utils.ItemBuilder;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Map;

public class CircuitSurvivalPlugin extends JavaPlugin {

    // give名 → 実際のレシピNamespacedKeyのローカル名 マッピング
    static final Map<String, String> RECIPE_KEY_MAP = Map.of(
            "pipe",   "item_pipe",
            "rs_tx",  "rs_transmitter",
            "rs_rx",  "rs_receiver"
    );

    static final List<String> ALL_GIVE_NAMES = List.of(
            "pipe","input_pipe","output_pipe","filter_pipe",
            "wireless_tx","wireless_rx",
            "rs_tx","rs_rx",
            "gate_and","gate_or","gate_not","gate_xor",
            "crusher","compressor","timer",
            "block_placer","block_breaker",
            "auto_crafter",
            "igniter","auto_smelter","miner",
            "vacuum_hopper","item_sorter","auto_farmer",
            "right_clicker","entity_detector",
            "chunk_loader","auto_brewer",
            "item_router","block_transmuter","fluid_collector",
            "bone_mealer","vacuum_hopper_ctrl",
            "storage_drum","storage_controller","auto_forester",
            "xp_converter","fast_hopper","vert_fast_hopper","bulk_dropper",
            "auto_fisher",
            "cooking_station","pixel_forge",
            "custom_crafter","pulverizer","electric_furnace","auto_anvil",
            "solar_panel","auto_shearer",
            "generator","energy_cell",
            "charger","battery",
            "energy_cable","hv_cell","auto_enchanter","void_miner",
            "induction_furnace","centrifuge","combustion_generator","ore_processor",
            "materializer","recycler","wireless_charger","auto_disenchanter",
            "thermal_generator","advanced_assembler","neutron_compressor",
            "mobile_platform",
            "block_control_attachment", "trash_can",
            // 料理/PixelGun系カスタムアイテム
            "explosive_croquette","chef_knife","spice_curry",
            "pixel_gun","dark_saber","grenade_launcher","sniper_rifle",
            // 中間素材
            "circuit_board","machine_gear","machine_casing",
            "processor_unit","motor","heating_coil","cooling_cell",
            "reinforced_casing","energy_core","stabilizer",
            "super_conductor","void_crystal",
            "advanced_circuit","high_gear",
            "quantum_chip","neutron_reflector",
            "molecular_circuit","coolant_cell",
            // 装備
            "nano_sword","mining_drill",
            "force_helmet","force_chestplate","force_leggings","force_boots"
    );

    // PDCキー
    private NamespacedKey pipeTypeKey;
    private NamespacedKey pipeFilterKey;
    private NamespacedKey pipeChannelKey;
    private NamespacedKey gateTypeKey;
    private NamespacedKey machineTypeKey;
    private NamespacedKey rsBlockTypeKey;
    private NamespacedKey enStorageKey;

    // マネージャー
    private SelectionManager selectionManager;
    private PipeManager pipeManager;
    private MachineManager machineManager;
    private WirelessRedstoneManager wirelessRedstoneManager;
    private EnergyManager energyManager;
    private MachineListener machineListener;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getDataFolder().mkdirs();

        // PDCキー初期化
        pipeTypeKey    = new NamespacedKey(this, "pipe_type");
        pipeFilterKey  = new NamespacedKey(this, "pipe_filter");
        pipeChannelKey = new NamespacedKey(this, "pipe_channel");
        gateTypeKey    = new NamespacedKey(this, "gate_type");
        machineTypeKey = new NamespacedKey(this, "machine_type");
        rsBlockTypeKey = new NamespacedKey(this, "rs_block_type");
        // カスタムアイテムキー初期化
        CustomItems.KEY           = new NamespacedKey(this, "custom_item");
        CustomItems.PROJECTILE_KEY = new NamespacedKey(this, "custom_projectile");
        IntermediateMaterials.KEY = new NamespacedKey(this, "intermediate_material");
        enStorageKey = new NamespacedKey(this, "en_storage");

        // マネージャー初期化 (MachineManager を先に生成して PipeManager/EnergyManager に渡す)
        selectionManager        = new SelectionManager();
        machineManager          = new MachineManager(this);
        pipeManager             = new PipeManager(this, machineManager);
        energyManager           = new EnergyManager(this, machineManager);
        wirelessRedstoneManager = new WirelessRedstoneManager(this);

        // リスナー登録
        var pm = getServer().getPluginManager();
        pm.registerEvents(new WandListener(selectionManager), this);
        pm.registerEvents(new PipeListener(pipeManager, pipeTypeKey, pipeFilterKey, pipeChannelKey), this);
        pm.registerEvents(new LogicGateListener(this, gateTypeKey), this);
        machineListener = new MachineListener(this, machineManager, energyManager, machineTypeKey);
        pm.registerEvents(machineListener, this);
        pm.registerEvents(new SignalDisplayListener(), this);
        pm.registerEvents(new CustomItemListener(), this);
        GuideManager.init(this);

        // パイプ転送タスク開始
        int pipeInterval = getConfig().getInt("pipes.transfer_interval", 10);
        pipeManager.startTransferTask(pipeInterval);

        // ワイヤレスRS更新タスク開始
        wirelessRedstoneManager.startUpdateTask(5);

        // カスタムレシピ登録 (事前に自社レシピを全削除して重複防止)
        String selfNs = new NamespacedKey(this, "_discover_check").getNamespace();
        java.util.List<NamespacedKey> oldKeys = new java.util.ArrayList<>();
        java.util.Iterator<org.bukkit.inventory.Recipe> oldIt = getServer().recipeIterator();
        while (oldIt.hasNext()) {
            org.bukkit.inventory.Recipe r = oldIt.next();
            if (r instanceof org.bukkit.Keyed k && k.getKey().getNamespace().equals(selfNs))
                oldKeys.add(k.getKey());
        }
        oldKeys.forEach(k -> getServer().removeRecipe(k));
        if (!oldKeys.isEmpty())
            getLogger().info("Removed " + oldKeys.size() + " stale recipes before re-registration");

        new CustomRecipes(this).register();
        new MachineRecipes(this, pipeTypeKey, gateTypeKey, machineTypeKey, rsBlockTypeKey).register();
        CustomCrafterRecipe.init();
        new IntermediateRecipes(this).register();

        // レシピキーを収集 (recipeIterator + ハードコードフォールバック)
        RecipeKeyRegistry.populateFromIterator(this);
        // フォールバック: 明示的にキーを追加 (recipeIteratorが空の場合に備える)
        String[] fallbackIds = {
            // IntermediateRecipes (中間素材)
            "circuit_board", "machine_gear", "machine_casing",
            "processor_unit", "motor", "heating_coil", "cooling_cell", "reinforced_casing",
            "energy_core", "stabilizer",
            "refined_iron_smelt", "refined_gold_smelt",
            "compress_iron", "compress_gold", "decompress_iron", "decompress_gold",
            "advanced_circuit", "high_gear",
            "en_cell_component", "pipe_connector",
            // MachineRecipes (機械)
            "machine_crusher", "machine_compressor", "machine_timer", "machine_block_placer",
            "machine_block_breaker", "machine_auto_crafter", "machine_item_sorter",
            "machine_auto_farmer", "machine_igniter", "machine_auto_smelter", "machine_miner",
            "machine_vacuum_hopper", "machine_right_clicker", "machine_entity_detector",
            "machine_chunk_loader", "machine_auto_brewer", "machine_item_router",
            "machine_block_transmuter", "machine_fluid_collector", "machine_bone_mealer",
            "machine_vacuum_hopper_ctrl", "machine_storage_drum", "machine_storage_controller",
            "machine_woodcutter", "machine_xp_converter", "machine_fast_hopper",
            "machine_vert_fast_hopper", "machine_bulk_dropper", "machine_auto_fisher",
            "machine_cooking_station", "machine_pixel_forge", "machine_vertical_elevator",
            "machine_custom_crafter", "machine_pulverizer", "machine_electric_furnace",
            "machine_auto_anvil", "machine_solar_panel", "machine_auto_shearer",
            "machine_generator", "machine_energy_cell", "machine_charger", "machine_energy_cable",
            "machine_hv_cell", "machine_auto_enchanter",
            "machine_induction_furnace", "machine_centrifuge", "machine_combustion_generator",
            "machine_ore_processor", "machine_materializer", "machine_recycler",
            "machine_wireless_charger", "machine_auto_disenchanter", "machine_thermal_generator",
            "machine_advanced_assembler", "machine_neutron_compressor", "machine_mobile_platform",
            "machine_block_control_attachment", "machine_trash_can", "machine_washing_machine",
            "machine_crafter_controller",
            "machine_water_generator", "machine_wind_generator",
            "fast_pipe", "fast_input_pipe", "fast_output_pipe",
            "bulk_crusher", "bulk_compressor", "bulk_auto_smelter",
            "bulk_pipe", "bulk_energy_cable", "bulk_block_placer", "bulk_block_breaker",
            "bulk_fast_pipe",
            "machine_distillation_tower", "machine_chemical_reactor",
            "machine_vacuum_furnace", "machine_high_pressure_press",
            "reinforced_plate",
            // パイプ系
            "item_pipe", "input_pipe", "output_pipe", "filter_pipe",
            "wireless_tx", "wireless_rx",
            // RS系
            "rs_transmitter", "rs_receiver",
            // 論理ゲート
            "gate_and", "gate_or", "gate_not", "gate_xor"
        };
        for (String id : fallbackIds) {
            RecipeKeyRegistry.register(new NamespacedKey(this, id));
        }

        List<NamespacedKey> recipeKeys = RecipeKeyRegistry.getAllKeys();
        getLogger().info("Recipe discovery: " + recipeKeys.size() + " recipes registered for discovery");

        // 既にオンラインのプレイヤーに全レシピを強制Discover (リロード対策)
        for (org.bukkit.entity.Player p : getServer().getOnlinePlayers()) {
            for (NamespacedKey k : recipeKeys) p.discoverRecipe(k);
        }

        // コマンド登録
        registerCommands();

        getLogger().info("CircuitSurvival が有効になりました！");
    }

    @Override
    public void onDisable() {
        // 先にセーブ (タスクキャンセル後だとscheduleGuiSync等のワンショットタスクが消失するため)
        if (machineManager != null) {
            for (org.bukkit.Location loc : machineManager.getByType(net.circuitsurvival.machines.MachineType.CHUNK_LOADER)) {
                org.bukkit.Chunk chunk = loc.getChunk();
                if (chunk.isForceLoaded()) chunk.setForceLoaded(false);
            }
            machineManager.saveAll();
        }
        if (energyManager != null) energyManager.saveData();
        // タスク全停止
        getServer().getScheduler().cancelTasks(this);

        // カスタムレシピを全削除 (PlugmanX でのリロード時に重複しないよう)
        String ns = getName().toLowerCase(java.util.Locale.ROOT);
        java.util.Iterator<org.bukkit.inventory.Recipe> it = getServer().recipeIterator();
        java.util.List<NamespacedKey> toRemove = new java.util.ArrayList<>();
        while (it.hasNext()) {
            org.bukkit.inventory.Recipe r = it.next();
            if (r instanceof org.bukkit.Keyed k && k.getKey().getNamespace().equals(ns))
                toRemove.add(k.getKey());
        }
        toRemove.forEach(k -> getServer().removeRecipe(k));

        getLogger().info("CircuitSurvival が無効になりました。");
    }

    // ---- コマンド登録 ---------------------------------------------------------

    private void registerCommands() {
        getCommand("cs").setExecutor(new MainCommand());
        getCommand("cs").setTabCompleter(new MainTabCompleter());

        SWECommand sweCmd = new SWECommand(selectionManager);
        String[] sweCommands = {"swe_pos1","swe_pos2","swe_set","swe_replace","swe_fill",
                "swe_walls","swe_outline","swe_copy","swe_paste","swe_undo","swe_rotate","swe_sel"};
        for (String cmd : sweCommands) {
            if (getCommand(cmd) != null) getCommand(cmd).setExecutor(sweCmd);
        }
    }

    // ---- /cs メインコマンド ---------------------------------------------------

    private class MainCommand implements CommandExecutor {
        @Override
        public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("プレイヤーのみ使用可能。");
                return true;
            }

            if (args.length == 0) { showHelp(player); return true; }

            switch (args[0].toLowerCase()) {
                case "give"   -> handleGive(player, args);
                case "recipe" -> handleRecipe(player, args);
                case "discover" -> {
                    if (machineListener != null) {
                        machineListener.discoverAllRecipes(player);
                    } else {
                        player.sendMessage(Component.text("MachineListenerが利用できません", NamedTextColor.RED));
                    }
                }
                case "guide"  -> {
                    player.getInventory().addItem(GuideManager.createGuideBook());
                    player.sendMessage(Component.text("§6[CS] ガイドブックを受け取りました！ §e右クリック§7でガイドを開きます"));
                }
                case "pipe"   -> handlePipe(player, args);
                case "reload" -> {
                    if (!player.hasPermission("circuitsurvival.admin")) {
                        player.sendMessage(Component.text("権限がありません。", NamedTextColor.RED));
                        return true;
                    }
                    reloadConfig();
                    player.sendMessage(Component.text("[CS] 設定をリロードしました。", NamedTextColor.GREEN));
                }
                default -> showHelp(player);
            }
            return true;
        }

        private void handleGive(Player player, String[] args) {
            if (!player.hasPermission("circuitsurvival.admin")) {
                player.sendMessage(Component.text("権限がありません。", NamedTextColor.RED));
                return;
            }
            if (args.length < 2) {
                player.sendMessage(Component.text("使い方: /cs give <アイテム名>", NamedTextColor.YELLOW));
                player.sendMessage(Component.text(
                        "パイプ: pipe, input_pipe, output_pipe, filter_pipe, wireless_tx, wireless_rx",
                        NamedTextColor.GRAY));
                player.sendMessage(Component.text(
                        "RS: rs_tx, rs_rx, gate_and, gate_or, gate_not, gate_xor",
                        NamedTextColor.GRAY));
                player.sendMessage(Component.text(
                        "マシン: crusher, compressor, timer, "
                        + "block_placer, block_breaker, auto_crafter, "
                        + "igniter, auto_smelter, miner, vacuum_hopper, item_sorter, auto_farmer, "
                        + "right_clicker, entity_detector, chunk_loader, auto_brewer, "
                        + "item_router, block_transmuter, fluid_collector",
                        NamedTextColor.GRAY));
                return;
            }

            ItemStack item = buildItem(args[1].toLowerCase());
            if (item == null) {
                player.sendMessage(Component.text("不明なアイテム: " + args[1], NamedTextColor.RED));
                return;
            }
            player.getInventory().addItem(item);
            player.sendMessage(Component.text("[CS] " + args[1] + " を付与しました。", NamedTextColor.GREEN));
        }

        private void handleRecipe(Player player, String[] args) {
            if (args.length < 2) {
                player.sendMessage(Component.text("使い方: /cs recipe <アイテム名>  例: /cs recipe crusher", NamedTextColor.YELLOW));
                player.sendMessage(Component.text("全アイテム: /cs give のタブ補完参照 (全て同じ名前で検索可)", NamedTextColor.GRAY));
                return;
            }
            String name = args[1].toLowerCase();
            // give名 → レシピキー名に変換
            String keyName = RECIPE_KEY_MAP.getOrDefault(name, name);

            // 試行順: machine_XXX → XXX
            org.bukkit.NamespacedKey key = new org.bukkit.NamespacedKey(CircuitSurvivalPlugin.this, "machine_" + keyName);
            org.bukkit.inventory.Recipe recipe = getServer().getRecipe(key);
            if (recipe == null) {
                key = new org.bukkit.NamespacedKey(CircuitSurvivalPlugin.this, keyName);
                recipe = getServer().getRecipe(key);
            }
            if (recipe == null) {
                player.sendMessage(Component.text("レシピが見つかりません: " + name, NamedTextColor.RED));
                return;
            }
            player.sendMessage(Component.text("━━━ " + name + " のレシピ ━━━", NamedTextColor.GOLD));
            if (recipe instanceof org.bukkit.inventory.ShapedRecipe sr) {
                String[] shape = sr.getShape();
                // 3行に正規化
                String r0 = shape.length > 0 ? shape[0] : "   ";
                String r1 = shape.length > 1 ? shape[1] : "   ";
                String r2 = shape.length > 2 ? shape[2] : "   ";
                Map<Character, org.bukkit.inventory.RecipeChoice> choices = sr.getChoiceMap();
                player.sendMessage(renderRow(r0, choices));
                player.sendMessage(renderRow(r1, choices));
                player.sendMessage(renderRow(r2, choices));
            } else if (recipe instanceof org.bukkit.inventory.ShapelessRecipe sl) {
                player.sendMessage(Component.text("材料 (無形式):", NamedTextColor.YELLOW));
                for (org.bukkit.inventory.RecipeChoice c : sl.getChoiceList()) {
                    player.sendMessage(Component.text("  ・" + matShortName(choiceMaterial(c)), NamedTextColor.WHITE));
                }
            }
            ItemStack res = recipe.getResult();
            player.sendMessage(Component.text("→ 結果: " + matShortName(res.getType()) + " x" + res.getAmount(), NamedTextColor.GREEN));
        }

        private net.kyori.adventure.text.Component renderRow(String row, Map<Character, org.bukkit.inventory.RecipeChoice> choices) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 3; i++) {
                char c = i < row.length() ? row.charAt(i) : ' ';
                String mat = (c == ' ' || c == ' ') ? "　　" : matShortName(choiceMaterial(choices.get(c)));
                sb.append("[").append(mat).append("]");
            }
            return Component.text(sb.toString(), NamedTextColor.WHITE);
        }

        private Material choiceMaterial(org.bukkit.inventory.RecipeChoice choice) {
            if (choice instanceof org.bukkit.inventory.RecipeChoice.MaterialChoice mc
                    && !mc.getChoices().isEmpty()) return mc.getChoices().get(0);
            return null;
        }

        private String matShortName(Material m) {
            if (m == null) return "　　";
            return switch (m) {
                case IRON_INGOT          -> "鉄";
                case IRON_BARS           -> "鉄格子";
                case IRON_PICKAXE        -> "鉄ピッケル";
                case PISTON              -> "ピストン";
                case GRAVEL              -> "砂利";
                case STONECUTTER         -> "石切台";
                case REDSTONE            -> "RS";
                case REDSTONE_BLOCK      -> "RSブロック";
                case REDSTONE_TORCH      -> "RSトーチ";
                case STONE               -> "石";
                case COMPARATOR          -> "比較器";
                case GOLD_INGOT          -> "金";
                case ENDER_PEARL         -> "エンダーパール";
                case OBSERVER            -> "観察者";
                case DIAMOND             -> "ダイヤ";
                case CLOCK               -> "時計";
                case REPEATER            -> "遅延器";
                case DAYLIGHT_DETECTOR   -> "日照センサー";
                case DISPENSER           -> "ディスペンサー";
                case HOPPER              -> "ホッパー";
                case OAK_PLANKS          -> "オーク板材";
                case CRAFTING_TABLE      -> "作業台";
                case COMPOSTER           -> "堆肥箱";
                case HAY_BLOCK           -> "干し草";
                case FURNACE             -> "かまど";
                case CAMPFIRE            -> "焚き火";
                case FLINT               -> "火打石";
                case COBBLED_DEEPSLATE   -> "深層石";
                case AMETHYST_SHARD      -> "アメジスト";
                case QUARTZ              -> "クォーツ";
                case AMETHYST_BLOCK      -> "アメジストブロック";
                case DROPPER             -> "ドロッパー";
                case LODESTONE           -> "磁石石";
                case GRINDSTONE          -> "砥石";
                case BOW                 -> "弓";
                case CHEST               -> "チェスト";
                case BARREL              -> "樽";
                case BLAST_FURNACE       -> "溶鉱炉";
                case SEA_LANTERN         -> "海のランタン";
                case CALIBRATED_SCULK_SENSOR -> "調整スカルク";
                case LAPIS_LAZULI        -> "ラピスラズリ";
                case NOTE_BLOCK          -> "音符ブロック";
                case OBSIDIAN            -> "黒曜石";
                case CAULDRON            -> "大釜";
                case BLAZE_ROD           -> "ブレイズロッド";
                case TARGET              -> "ターゲット";
                case SMOKER              -> "スモーカー";
                case RESPAWN_ANCHOR      -> "復活アンカー";
                case BONE_BLOCK              -> "骨ブロック";
                case CHISELED_STONE_BRICKS   -> "石レンガ彫刻";
                case DEEPSLATE_BRICKS        -> "深層岩レンガ";
                case ENCHANTING_TABLE        -> "エンチャントテーブル";
                case FLETCHING_TABLE              -> "矢細工台";
                case SCULK_CATALYST               -> "スカルクカタリスト";
                case POLISHED_BLACKSTONE_BRICKS   -> "磨かれた黒石レンガ";
                case LECTERN -> "書見台";
                case PRISMARINE                   -> "プリズマリン";
                case PURPUR_BLOCK                 -> "パープル";
                case CRYING_OBSIDIAN              -> "泣く黒曜石";
                case ENDER_EYE                    -> "エンダーアイ";
                default -> {
                    String s = m.name().replace('_', ' ').toLowerCase();
                    yield s.length() > 8 ? s.substring(0, 8) : s;
                }
            };
        }

        private void handlePipe(Player player, String[] args) {
            if (args.length < 3 || !args[1].equalsIgnoreCase("channel")) {
                player.sendMessage(Component.text("使い方: /cs pipe channel <チャンネル名>", NamedTextColor.YELLOW));
                player.sendMessage(Component.text("見ているブロックがパイプなら、そのチャンネルを変更します。", NamedTextColor.GRAY));
                return;
            }
            org.bukkit.block.Block target = player.getTargetBlockExact(5);
            if (target == null || !pipeManager.isPipe(target.getLocation())) {
                player.sendMessage(Component.text("視線の先にパイプがありません。(5ブロック以内)", NamedTextColor.RED));
                return;
            }
            PipeManager.PipeData data = pipeManager.getPipe(target.getLocation());
            if (data == null) {
                player.sendMessage(Component.text("データエラー: パイプが見つかりません。", NamedTextColor.RED));
                return;
            }
            data.channel = args[2];
            pipeManager.registerPipe(target.getLocation(), data);
            player.sendMessage(Component.text("[Pipe] チャンネルを \"" + args[2] + "\" に設定しました。", NamedTextColor.GREEN));
        }

        private void showHelp(Player player) {
            player.sendMessage(Component.text("=== CircuitSurvival ===", NamedTextColor.GOLD));
            player.sendMessage(Component.text("/cs give <アイテム>  - カスタムアイテムを入手 (OP)", NamedTextColor.WHITE));
            player.sendMessage(Component.text("/cs pipe channel <名> - ワイヤレスパイプのチャンネル設定", NamedTextColor.WHITE));
            player.sendMessage(Component.text("/cs reload           - 設定リロード (OP)", NamedTextColor.WHITE));
            player.sendMessage(Component.text("--- パイプ種別 ---", NamedTextColor.YELLOW));
            player.sendMessage(Component.text("入力パイプ: コンテナからアイテムを能動的に引き出す起点", NamedTextColor.WHITE));
            player.sendMessage(Component.text("出力パイプ: パイプチェーン終端。コンテナへ押し込む", NamedTextColor.WHITE));
            player.sendMessage(Component.text("通常パイプ: どちらにもなれる双方向パイプ", NamedTextColor.WHITE));
            player.sendMessage(Component.text("--- 信号表示 ---", NamedTextColor.YELLOW));
            player.sendMessage(Component.text("Shift+右クリック(レッドストーン手持ち) → 信号強度を表示", NamedTextColor.WHITE));
        }
    }

    private class MainTabCompleter implements TabCompleter {
        @Override
        public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
            if (args.length == 1) return List.of("give", "recipe", "guide", "pipe", "reload");
            if (args.length == 2 && (args[0].equalsIgnoreCase("give") || args[0].equalsIgnoreCase("recipe")))
                return ALL_GIVE_NAMES;
            if (args.length == 2 && args[0].equalsIgnoreCase("pipe")) return List.of("channel");
            return List.of();
        }
    }

    // ---- カスタムアイテム生成 -------------------------------------------------

    private ItemStack buildItem(String name) {
        return switch (name) {
            // パイプ系
            case "pipe" -> new ItemBuilder(Material.IRON_BARS)
                    .name("アイテムパイプ", NamedTextColor.AQUA)
                    .lore("双方向パイプ。能動的に起動", "設置すると自動的に接続")
                    .pdc(pipeTypeKey, "PIPE")
                    .build();
            case "input_pipe" -> new ItemBuilder(Material.IRON_BARS)
                    .name("入力パイプ", NamedTextColor.GOLD)
                    .lore("隣接コンテナからアイテムを引き出す起点", "チェーンの始端に使う")
                    .pdc(pipeTypeKey, "INPUT_PIPE")
                    .build();
            case "output_pipe" -> new ItemBuilder(Material.IRON_BARS)
                    .name("出力パイプ", NamedTextColor.RED)
                    .lore("パイプチェーンの終端", "隣接コンテナへアイテムを押し込む先")
                    .pdc(pipeTypeKey, "OUTPUT_PIPE")
                    .build();
            case "filter_pipe" -> new ItemBuilder(Material.CHAIN)
                    .name("フィルターパイプ", NamedTextColor.YELLOW)
                    .lore("右クリックで通過させるアイテムを設定", "(手に持ったアイテムがフィルターになる)")
                    .pdc(pipeTypeKey, "FILTER")
                    .build();
            case "wireless_tx" -> new ItemBuilder(Material.ENDER_CHEST)
                    .name("ワイヤレス送信機", NamedTextColor.LIGHT_PURPLE)
                    .lore("チャンネルにアイテムを送信", "/cs pipe channel <名前> でチャンネル設定")
                    .pdc(pipeTypeKey, "WIRELESS_TX")
                    .build();
            case "wireless_rx" -> new ItemBuilder(Material.ENDER_CHEST)
                    .name("ワイヤレス受信機", NamedTextColor.LIGHT_PURPLE)
                    .lore("チャンネルからアイテムを受信", "/cs pipe channel <名前> でチャンネル設定")
                    .pdc(pipeTypeKey, "WIRELESS_RX")
                    .build();

            // ワイヤレスレッドストーン
            case "rs_tx" -> buildRSItem("TRANSMITTER", "ワイヤレスRS送信機",
                    "隣接信号をチャンネルで送信", Material.OBSERVER);
            case "rs_rx" -> buildRSItem("RECEIVER", "ワイヤレスRS受信機",
                    "チャンネルの信号を南面に出力", Material.REDSTONE_LAMP);

            // 論理ゲート
            case "gate_and"  -> buildGateItem("AND",  "ANDゲート",  "全入力がONで出力ON");
            case "gate_or"   -> buildGateItem("OR",   "ORゲート",   "どれか1つONで出力ON");
            case "gate_not"  -> buildGateItem("NOT",  "NOTゲート",  "入力がOFFで出力ON");
            case "gate_xor"  -> buildGateItem("XOR",  "XORゲート",  "奇数入力がONで出力ON");

            // マシン
            case "crusher"       -> buildMachineItem(MachineType.CRUSHER);
            case "compressor"    -> buildMachineItem(MachineType.COMPRESSOR);
            case "timer"         -> buildMachineItem(MachineType.TIMER);
            case "block_placer"         -> buildMachineItem(MachineType.BLOCK_PLACER);
            case "block_breaker"        -> buildMachineItem(MachineType.BLOCK_BREAKER);
            case "auto_crafter"         -> buildMachineItem(MachineType.AUTO_CRAFTER);
            case "igniter"              -> buildMachineItem(MachineType.IGNITER);
            case "auto_smelter"         -> buildMachineItem(MachineType.AUTO_SMELTER);
            case "miner"                -> buildMachineItem(MachineType.MINER);
            case "item_sorter"          -> buildMachineItem(MachineType.ITEM_SORTER);
            case "auto_farmer"          -> buildMachineItem(MachineType.AUTO_FARMER);
            case "vacuum_hopper"    -> buildMachineItem(MachineType.VACUUM_HOPPER);
            case "right_clicker"    -> buildMachineItem(MachineType.RIGHT_CLICKER);
            case "entity_detector"  -> buildMachineItem(MachineType.ENTITY_DETECTOR);
            case "chunk_loader"     -> buildMachineItem(MachineType.CHUNK_LOADER);
            case "auto_brewer"      -> buildMachineItem(MachineType.AUTO_BREWER);
            case "item_router"      -> buildMachineItem(MachineType.ITEM_ROUTER);
            case "block_transmuter" -> buildMachineItem(MachineType.BLOCK_TRANSMUTER);
            case "fluid_collector"  -> buildMachineItem(MachineType.FLUID_COLLECTOR);
            case "bone_mealer"         -> buildMachineItem(MachineType.BONE_MEALER);
            case "vacuum_hopper_ctrl"  -> buildMachineItem(MachineType.VACUUM_HOPPER_CTRL);
            case "storage_drum"        -> buildMachineItem(MachineType.STORAGE_DRUM);
            case "storage_controller"  -> buildMachineItem(MachineType.STORAGE_CONTROLLER);
            case "woodcutter"          -> buildMachineItem(MachineType.WOODCUTTER);
            case "xp_converter"        -> buildMachineItem(MachineType.XP_CONVERTER);
            case "fast_hopper"         -> buildMachineItem(MachineType.FAST_HOPPER);
            case "vert_fast_hopper"    -> buildMachineItem(MachineType.VERT_FAST_HOPPER);
            case "bulk_dropper"        -> buildMachineItem(MachineType.BULK_DROPPER);
            case "auto_fisher"         -> buildMachineItem(MachineType.AUTO_FISHER);
            case "vertical_elevator"   -> buildMachineItem(MachineType.VERTICAL_ELEVATOR);
            case "cooking_station"     -> buildMachineItem(MachineType.COOKING_STATION);
            case "pixel_forge"         -> buildMachineItem(MachineType.PIXEL_FORGE);
            case "custom_crafter"      -> buildMachineItem(MachineType.CUSTOM_CRAFTER);
            case "pulverizer"          -> buildMachineItem(MachineType.PULVERIZER);
            case "electric_furnace"    -> buildMachineItem(MachineType.ELECTRIC_FURNACE);
            case "auto_anvil"          -> buildMachineItem(MachineType.AUTO_ANVIL);
            case "solar_panel"         -> buildMachineItem(MachineType.SOLAR_PANEL);
            case "auto_shearer"        -> buildMachineItem(MachineType.AUTO_SHEARER);
            case "generator"           -> buildMachineItem(MachineType.GENERATOR);
            case "energy_cell"         -> buildMachineItem(MachineType.ENERGY_CELL);
            case "charger"             -> buildMachineItem(MachineType.CHARGER);
            case "battery"             -> buildBattery();
            case "energy_cable"        -> buildMachineItem(MachineType.ENERGY_CABLE);
            case "hv_cell"             -> buildMachineItem(MachineType.HV_CELL);
            case "auto_enchanter"      -> buildMachineItem(MachineType.AUTO_ENCHANTER);
            case "void_miner"          -> buildMachineItem(MachineType.VOID_MINER);
            case "induction_furnace"   -> buildMachineItem(MachineType.INDUCTION_FURNACE);
            case "centrifuge"          -> buildMachineItem(MachineType.CENTRIFUGE);
            case "combustion_generator" -> buildMachineItem(MachineType.COMBUSTION_GENERATOR);
            case "ore_processor"       -> buildMachineItem(MachineType.ORE_PROCESSOR);
            case "materializer"        -> buildMachineItem(MachineType.MATERIALIZER);
            case "recycler"            -> buildMachineItem(MachineType.RECYCLER);
            case "wireless_charger"    -> buildMachineItem(MachineType.WIRELESS_CHARGER);
            case "auto_disenchanter"   -> buildMachineItem(MachineType.AUTO_DISENCHANTER);
            case "thermal_generator"   -> buildMachineItem(MachineType.THERMAL_GENERATOR);
            case "advanced_assembler"  -> buildMachineItem(MachineType.ADVANCED_ASSEMBLER);
            case "neutron_compressor"  -> buildMachineItem(MachineType.NEUTRON_COMPRESSOR);
            case "mobile_platform"              -> buildMachineItem(MachineType.MOBILE_PLATFORM);
            case "block_control_attachment"     -> buildMachineItem(MachineType.BLOCK_CONTROL_ATTACHMENT);
            case "trash_can"                    -> buildMachineItem(MachineType.TRASH_CAN);
            // Chain 1 中間素材
            case "crushed_iron"        -> IntermediateMaterials.build(IntermediateMaterials.CRUSHED_IRON);
            case "crushed_gold"        -> IntermediateMaterials.build(IntermediateMaterials.CRUSHED_GOLD);
            case "crushed_coal"        -> IntermediateMaterials.build(IntermediateMaterials.CRUSHED_COAL);
            case "crushed_stone"       -> IntermediateMaterials.build(IntermediateMaterials.CRUSHED_STONE);
            case "crushed_netherrack"  -> IntermediateMaterials.build(IntermediateMaterials.CRUSHED_NETHERRACK);
            case "refined_iron_ingot"  -> IntermediateMaterials.build(IntermediateMaterials.REFINED_IRON_INGOT);
            case "refined_gold_ingot"  -> IntermediateMaterials.build(IntermediateMaterials.REFINED_GOLD_INGOT);
            case "compressed_iron"     -> IntermediateMaterials.build(IntermediateMaterials.COMPRESSED_IRON);
            case "compressed_gold"     -> IntermediateMaterials.build(IntermediateMaterials.COMPRESSED_GOLD);
            // 中間素材
            case "circuit_board"       -> IntermediateMaterials.build(IntermediateMaterials.CIRCUIT_BOARD);
            case "machine_gear"        -> IntermediateMaterials.build(IntermediateMaterials.MACHINE_GEAR);
            case "machine_casing"      -> IntermediateMaterials.build(IntermediateMaterials.MACHINE_CASING);
            case "processor_unit"      -> IntermediateMaterials.build(IntermediateMaterials.PROCESSOR_UNIT);
            case "motor"               -> IntermediateMaterials.build(IntermediateMaterials.MOTOR);
            case "heating_coil"        -> IntermediateMaterials.build(IntermediateMaterials.HEATING_COIL);
            case "cooling_cell"        -> IntermediateMaterials.build(IntermediateMaterials.COOLING_CELL);
            case "reinforced_casing"   -> IntermediateMaterials.build(IntermediateMaterials.REINFORCED_CASING);
            case "energy_core"         -> IntermediateMaterials.build(IntermediateMaterials.ENERGY_CORE);
            case "stabilizer"          -> IntermediateMaterials.build(IntermediateMaterials.STABILIZER);
            case "super_conductor"     -> IntermediateMaterials.build(IntermediateMaterials.SUPER_CONDUCTOR);
            case "void_crystal"        -> IntermediateMaterials.build(IntermediateMaterials.VOID_CRYSTAL);
            case "advanced_circuit"    -> IntermediateMaterials.build(IntermediateMaterials.ADVANCED_CIRCUIT);
            case "high_gear"           -> IntermediateMaterials.build(IntermediateMaterials.HIGH_GEAR);
            case "quantum_chip"        -> IntermediateMaterials.build(IntermediateMaterials.QUANTUM_CHIP);
            case "neutron_reflector"   -> IntermediateMaterials.build(IntermediateMaterials.NEUTRON_REFLECTOR);
            case "molecular_circuit"   -> IntermediateMaterials.build(IntermediateMaterials.MOLECULAR_CIRCUIT);
            case "coolant_cell"        -> IntermediateMaterials.build(IntermediateMaterials.COOLANT_CELL);
            // 料理/PixelGun系カスタムアイテム
            case "explosive_croquette" -> CustomItems.build(CustomItems.EXPLOSIVE_CROQUETTE);
            case "chef_knife"          -> CustomItems.build(CustomItems.CHEF_KNIFE);
            case "spice_curry"         -> CustomItems.build(CustomItems.SPICE_CURRY);
            case "pixel_gun"           -> CustomItems.build(CustomItems.PIXEL_GUN);
            case "dark_saber"          -> CustomItems.build(CustomItems.DARK_SABER);
            case "grenade_launcher"    -> CustomItems.build(CustomItems.GRENADE_LAUNCHER);
            case "sniper_rifle"        -> CustomItems.build(CustomItems.SNIPER_RIFLE);
            // 装備
            case "nano_sword"     -> CustomItems.build(CustomItems.NANO_SWORD);
            case "mining_drill"   -> CustomItems.build(CustomItems.MINING_DRILL);
            case "force_helmet"   -> CustomItems.build(CustomItems.FORCE_HELMET);
            case "force_chestplate" -> CustomItems.build(CustomItems.FORCE_CHESTPLATE);
            case "force_leggings" -> CustomItems.build(CustomItems.FORCE_LEGGINGS);
            case "force_boots"    -> CustomItems.build(CustomItems.FORCE_BOOTS);

            default -> null;
        };
    }

    private ItemStack buildGateItem(String type, String displayName, String desc) {
        return new ItemBuilder(Material.SEA_LANTERN)
                .name(displayName, NamedTextColor.GREEN)
                .lore(desc, "北・東・西が入力、南が出力")
                .pdc(gateTypeKey, type)
                .build();
    }

    private ItemStack buildRSItem(String type, String displayName, String desc, Material mat) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(displayName, NamedTextColor.RED));
        meta.lore(List.of(
                Component.text(desc, NamedTextColor.GRAY),
                Component.text("/cs pipe channel <名前> でチャンネル設定", NamedTextColor.GRAY)
        ));
        meta.getPersistentDataContainer().set(rsBlockTypeKey, PersistentDataType.STRING, type);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack buildMachineItem(MachineType type) {
        return new ItemBuilder(type.blockMaterial)
                .name(type.displayName, NamedTextColor.GOLD)
                .lore(type.description, "右クリックでGUIを開く")
                .pdc(machineTypeKey, type.name())
                .build();
    }

    /** バッテリー (EN保存可能アイテム) */
    private ItemStack buildBattery() {
        return buildBattery(0);
    }

    private ItemStack buildBattery(int storedEn) {
        ItemStack item = new ItemStack(Material.GLOWSTONE_DUST);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("乾電池", NamedTextColor.YELLOW));
        meta.lore(List.of(
                Component.text("§7ENを保管する携帯バッテリー"),
                Component.text("§7充電器で充電 / 機械に右クリックで放電"),
                Component.text("§eEN: " + storedEn + " / 10000")
        ));
        meta.getPersistentDataContainer().set(
                enStorageKey, PersistentDataType.INTEGER, Math.max(0, Math.min(storedEn, 10000)));
        item.setItemMeta(meta);
        return item;
    }

    /** バッテリーアイテムか判定 */
    public static boolean isBattery(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer()
                .has(NamespacedKey.fromString("circuitsurvival:en_storage"), PersistentDataType.INTEGER);
    }

    /** バッテリーのEN量を取得 */
    public static int getBatteryEn(ItemStack item) {
        if (!isBattery(item)) return 0;
        Integer val = item.getItemMeta().getPersistentDataContainer()
                .get(NamespacedKey.fromString("circuitsurvival:en_storage"), PersistentDataType.INTEGER);
        return val != null ? val : 0;
    }

    /** バッテリーのEN量を設定 */
    public static void setBatteryEn(ItemStack item, int amount) {
        if (!isBattery(item)) return;
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(
                NamespacedKey.fromString("circuitsurvival:en_storage"),
                PersistentDataType.INTEGER, Math.max(0, Math.min(amount, 10000)));
        item.setItemMeta(meta);
    }

    // ---- ゲッター -------------------------------------------------------------

    public SelectionManager getSelectionManager()               { return selectionManager; }
    public PipeManager getPipeManager()                         { return pipeManager; }
    public MachineManager getMachineManager()                   { return machineManager; }
    public WirelessRedstoneManager getWirelessRedstoneManager() { return wirelessRedstoneManager; }
    public EnergyManager getEnergyManager()                     { return energyManager; }
    public NamespacedKey getPipeTypeKey()                       { return pipeTypeKey; }
    public NamespacedKey getGateTypeKey()                       { return gateTypeKey; }
    public NamespacedKey getMachineTypeKey()                    { return machineTypeKey; }
    public NamespacedKey getRsBlockTypeKey()                    { return rsBlockTypeKey; }
    public NamespacedKey getEnStorageKey()                      { return enStorageKey; }
}
