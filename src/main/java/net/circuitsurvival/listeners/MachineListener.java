package net.circuitsurvival.listeners;

import net.circuitsurvival.CircuitSurvivalPlugin;
import net.circuitsurvival.items.CustomItems;
import net.circuitsurvival.items.IntermediateMaterials;
import net.circuitsurvival.machines.MachineType;
import net.circuitsurvival.managers.EnergyManager;
import net.circuitsurvival.managers.MachineManager;
import net.circuitsurvival.managers.MachineManager.MachineInvHolder;
import net.circuitsurvival.recipes.CustomCrafterRecipe;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Barrel;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.type.Piston;
import org.bukkit.block.data.type.Stairs;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.inventory.FurnaceBurnEvent;
import org.bukkit.event.inventory.FurnaceSmeltEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionType;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * カスタムマシンの設置・破壊・GUI操作
 *
 * CRUSHER      : 鉱石→粉砕 (2倍ドロップ)
 * COMPRESSOR   : 素材↔ブロック一括変換
 * DUPLICATOR   : アイテム複製 (ダート16個コスト)
 * TIMER        : RS信号パルス出力 (tick周期設定可)
 * BLOCK_PLACER : インベントリ内のブロックを前面に自動設置 (RS立ち上がりで動作)
 *                ※バレル(BARREL)の実インベントリをそのまま使用 → ホッパーから搬入可能
 * BLOCK_BREAKER: 前面のブロックを自動破壊しワールドにドロップ (RS立ち上がりで動作)
 * AUTO_CRAFTER : 3x3グリッドでレシピ設定 → ボタンで自動クラフト
 */
public class MachineListener implements Listener {

    /** GUIアクセスモード — クリック許可・保存スロットを一元管理 */
    private enum GuiMode {
        FULL,       // 全27スロット自由操作
        SMELTER,    // 0=入力, 26=出力のみ
        TWO_SLOT,   // 0, 26のみ (COOKING_STATION)
        THREE_SLOT, // 0, 1, 26のみ (PIXEL_FORGE)
        AUTO_CRAFT, // 内蔵レシピグリッド+素材バッファ (AUTO_CRAFTER)
        STORAGE_CTRL,// ストレージコントローラー(仮想)
        DRUM,       // 54スロット (STORAGE_DRUM)
        TIMER;      // タイマー(特殊)
    }

    /** 機械種別ごとのGUIモード */
    private static GuiMode guiMode(MachineType type) {
        if (type == null) return GuiMode.TWO_SLOT;
        return switch (type) {
            case TIMER -> GuiMode.TIMER;
            case AUTO_CRAFTER -> GuiMode.AUTO_CRAFT;
            case STORAGE_CONTROLLER -> GuiMode.STORAGE_CTRL;
            case STORAGE_DRUM -> GuiMode.DRUM;
            case AUTO_SMELTER -> GuiMode.SMELTER;
            // 全27スロット自由操作
            case CRUSHER, COMPRESSOR, MINER, VACUUM_HOPPER,
                 ITEM_SORTER, AUTO_FARMER, AUTO_BREWER,
                 ITEM_ROUTER, FLUID_COLLECTOR, BONE_MEALER,
                 VACUUM_HOPPER_CTRL, WOODCUTTER, XP_CONVERTER,
                 FAST_HOPPER, BULK_DROPPER, AUTO_FISHER,
                 BLOCK_TRANSMUTER, CUSTOM_CRAFTER, PULVERIZER,
                 ELECTRIC_FURNACE, AUTO_ANVIL,
                 INDUCTION_FURNACE, CENTRIFUGE, COMBUSTION_GENERATOR,
                 ORE_PROCESSOR, MATERIALIZER, RECYCLER,
                 AUTO_DISENCHANTER, GENERATOR, AUTO_ENCHANTER,
                 VOID_MINER, AUTO_SHEARER,
                  VERT_FAST_HOPPER, ADVANCED_ASSEMBLER, NEUTRON_COMPRESSOR,
                  TRASH_CAN, WASHING_MACHINE,
                  DISTILLATION_TOWER, CHEMICAL_REACTOR,
                  VACUUM_FURNACE, HIGH_PRESSURE_PRESS -> GuiMode.FULL;
            case PIXEL_FORGE, CHARGER -> GuiMode.THREE_SLOT;
            case COOKING_STATION, ENERGY_CELL, HV_CELL -> GuiMode.TWO_SLOT;
            default -> GuiMode.TWO_SLOT;
        };
    }

    // ---- フィールド ----------------------------------------------------------

    private final CircuitSurvivalPlugin plugin;
    private final MachineManager machineManager;
    private final EnergyManager energyManager;
    private final NamespacedKey machineTypeKey;

    // タイマー状態
    private final Map<String, Integer> timerPeriods = new HashMap<>();
    private final Map<String, Integer> timerCount   = new HashMap<>();
    private final Map<String, Boolean> timerState   = new HashMap<>();

    // RS立ち上がり検出 (BLOCK_PLACER / BLOCK_BREAKER 共通)
    private final Map<String, Boolean> prevPowered = new HashMap<>();

    // 機械が設置したRSブロック位置 (MINER/BLOCK_BREAKERの誤採掘防止)
    private final Set<Location> machineOwnedRSBlocks = new HashSet<>();
    // 複数プレイヤーがGUIを共有するためのキャッシュ
    private final Map<Location, Inventory> openGuis = new HashMap<>();
    // ストレージコントローラーGUI用テンプレートキャッシュ (locKey → 全アイテム種別リスト)
    private final Map<String, List<ItemStack>> controllerTemplates = new HashMap<>();

    // 機器制御アタッチメントのオーバーライド方向キャッシュ (machineLocKey → 動作方向)
    private final Map<String, BlockFace> controlAttachmentOverrides = new HashMap<>();

    private static final java.util.Random RANDOM = new java.util.Random();


    // 経験値変換炉: 素材 → XP量マップ
    private static final java.util.Map<Material, Integer> XP_VALUES;
    static {
        java.util.Map<Material, Integer> m = new java.util.HashMap<>();
        // モブドロップ
        m.put(Material.ROTTEN_FLESH, 1); m.put(Material.BONE, 1);
        m.put(Material.SPIDER_EYE, 1);   m.put(Material.GUNPOWDER, 1);
        m.put(Material.STRING, 1);       m.put(Material.SLIME_BALL, 2);
        m.put(Material.MAGMA_CREAM, 4);  m.put(Material.PHANTOM_MEMBRANE, 8);
        m.put(Material.ENDER_PEARL, 5);  m.put(Material.SHULKER_SHELL, 20);
        m.put(Material.BLAZE_POWDER, 4); m.put(Material.BLAZE_ROD, 8);
        m.put(Material.NETHER_WART, 2);  m.put(Material.GHAST_TEAR, 15);
        // 鉱石・素材
        m.put(Material.QUARTZ, 1);          m.put(Material.AMETHYST_SHARD, 2);
        m.put(Material.LAPIS_LAZULI, 2);    m.put(Material.RAW_COPPER, 1);
        m.put(Material.COPPER_INGOT, 2);    m.put(Material.RAW_IRON, 2);
        m.put(Material.IRON_INGOT, 3);      m.put(Material.RAW_GOLD, 3);
        m.put(Material.GOLD_INGOT, 5);
        // 貴重素材
        m.put(Material.DIAMOND, 30);        m.put(Material.EMERALD, 15);
        m.put(Material.ANCIENT_DEBRIS, 50); m.put(Material.NETHERITE_SCRAP, 40);
        m.put(Material.NETHERITE_INGOT, 120);
        // ブロック
        m.put(Material.LAPIS_BLOCK, 20);    m.put(Material.IRON_BLOCK, 30);
        m.put(Material.GOLD_BLOCK, 50);     m.put(Material.DIAMOND_BLOCK, 280);
        m.put(Material.EMERALD_BLOCK, 140); m.put(Material.NETHERITE_BLOCK, 1100);
        // 特殊
        m.put(Material.NETHER_STAR, 1500);  m.put(Material.EXPERIENCE_BOTTLE, 7);
        m.put(Material.ENCHANTED_GOLDEN_APPLE, 100);
        // 種
        m.put(Material.WHEAT_SEEDS,    1);
        m.put(Material.BEETROOT_SEEDS, 1);
        m.put(Material.MELON_SEEDS,    2);
        m.put(Material.PUMPKIN_SEEDS,  2);
        XP_VALUES = java.util.Collections.unmodifiableMap(m);
    }

    // ブロック破壊ツール: ダイヤピッケル
    private static final ItemStack DIAMOND_PICK = new ItemStack(Material.DIAMOND_PICKAXE);

    // 種 → 作物ブロック のマッピング (Block Placer 拡張)
    private static final Map<Material, Material> SEED_TO_CROP = new EnumMap<>(Material.class);
    private static final Map<Material, Material> SEED_SOIL    = new EnumMap<>(Material.class);
    static {
        SEED_TO_CROP.put(Material.WHEAT_SEEDS,        Material.WHEAT);
        SEED_TO_CROP.put(Material.CARROT,             Material.CARROTS);
        SEED_TO_CROP.put(Material.POTATO,             Material.POTATOES);
        SEED_TO_CROP.put(Material.BEETROOT_SEEDS,     Material.BEETROOTS);
        SEED_TO_CROP.put(Material.MELON_SEEDS,        Material.MELON_STEM);
        SEED_TO_CROP.put(Material.PUMPKIN_SEEDS,      Material.PUMPKIN_STEM);
        SEED_TO_CROP.put(Material.SWEET_BERRIES,      Material.SWEET_BERRY_BUSH);
        SEED_TO_CROP.put(Material.NETHER_WART,        Material.NETHER_WART);

        SEED_SOIL.put(Material.WHEAT_SEEDS,       Material.FARMLAND);
        SEED_SOIL.put(Material.CARROT,            Material.FARMLAND);
        SEED_SOIL.put(Material.POTATO,            Material.FARMLAND);
        SEED_SOIL.put(Material.BEETROOT_SEEDS,    Material.FARMLAND);
        SEED_SOIL.put(Material.MELON_SEEDS,       Material.FARMLAND);
        SEED_SOIL.put(Material.PUMPKIN_SEEDS,     Material.FARMLAND);
        SEED_SOIL.put(Material.SWEET_BERRIES,     Material.GRASS_BLOCK);
        SEED_SOIL.put(Material.NETHER_WART,       Material.SOUL_SAND);
    }

    private static final String GUI_PREFIX = "§8[CS-Machine] ";

    // ---- コンストラクタ -------------------------------------------------------

    public MachineListener(CircuitSurvivalPlugin plugin,
                           MachineManager machineManager,
                           EnergyManager energyManager,
                           NamespacedKey machineTypeKey) {
        this.plugin         = plugin;
        this.machineManager = machineManager;
        this.energyManager  = energyManager;
        this.machineTypeKey = machineTypeKey;
        startTimerTask();
        startPlacerBreakerTask();
        startAutoSmelterTask();
        startVacuumHopperTask();
        startAutoCrafterTask();
        startItemSorterTask();
        startAutoFarmerTask();
        startEntityDetectorTask();
        startCrusherCompressorTask();
        startAutoBrewerTask();
        initChunkLoaders();
        startHopperSimulationTask();
        startItemRouterTask();
        startBlockTransmuterTask();
        startFluidCollectorTask();
        startBoneMealerTask();
        startElevatorTask();
        startWoodcutterTask();
        startCustomCrafterTask();
        startPulverizerTask();
        startElectricFurnaceTask();
        startAutoAnvilTask();
        startXpConverterTask();
        startFastHopperTask();
        startBulkDropperTask();
        startAutoFisherTask();
        startSolarPanelTask();
        startAutoShearerTask();
        startGeneratorTask();
        startEnergyCellTask();
        startChargerTask();
        startAutoEnchanterTask();
        startVoidMinerTask();
        startHvCellTask();
        startInductionFurnaceTask();
        startCentrifugeTask();
        startCombustionGeneratorTask();
        startOreProcessorTask();
        startMaterializerTask();
        startRecyclerTask();
        startWashingMachineTask();
        startCrafterControllerTask();
        startWaterGeneratorTask();
        startWindGeneratorTask();
        startDistillationTowerTask();
        startChemicalReactorTask();
        startVacuumFurnaceTask();
        startHighPressurePressTask();
        startWirelessChargerTask();
        startAutoDisenchanterTask();
        startThermalGeneratorTask();
        startAdvancedAssemblerTask();
        startNeutronCompressorTask();
        startTrashCanCleanupTask();
    }

    // ==========================================================================
    // イベントハンドラ
    // ==========================================================================

    /** 参加時に全カスタムレシピを強制アンロックしてレシピ帳に表示 */
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        discoverAllRecipes(event.getPlayer());
    }

    /** このプラグインの全レシピNamespacedKeyを収集 */
    private void ensureRecipeKeys() {
        // レジストリが空なら手動で補充
        if (net.circuitsurvival.recipes.RecipeKeyRegistry.getAllKeys().isEmpty()) {
            String ns = new org.bukkit.NamespacedKey(plugin, "_discover_check").getNamespace();
            java.util.Iterator<org.bukkit.inventory.Recipe> it = plugin.getServer().recipeIterator();
            while (it.hasNext()) {
                org.bukkit.inventory.Recipe r = it.next();
                if (r instanceof org.bukkit.Keyed k && k.getKey().getNamespace().equals(ns))
                    net.circuitsurvival.recipes.RecipeKeyRegistry.register(k.getKey());
            }
        }
    }

    /** このプラグインの全レシピを指定プレイヤーに強制Discover */
    public void discoverAllRecipes(org.bukkit.entity.Player player) {
        ensureRecipeKeys();
        java.util.List<org.bukkit.NamespacedKey> keys = net.circuitsurvival.recipes.RecipeKeyRegistry.getAllKeys();
        for (org.bukkit.NamespacedKey k : keys) {
            player.discoverRecipe(k);
        }
        if (!keys.isEmpty()) player.sendMessage(Component.text("§7[CS] " + keys.size() + "個のレシピを解放しました"));
    }

    /** 現在オンラインの全プレイヤーにレシピを強制アンロック */
    public void discoverAllRecipesForAll() {
        ensureRecipeKeys();
        java.util.List<org.bukkit.NamespacedKey> keys = net.circuitsurvival.recipes.RecipeKeyRegistry.getAllKeys();
        for (org.bukkit.entity.Player p : plugin.getServer().getOnlinePlayers()) {
            for (org.bukkit.NamespacedKey k : keys) p.discoverRecipe(k);
        }
        if (!keys.isEmpty())
            plugin.getLogger().info(keys.size() + " recipes auto-discovered for " + plugin.getServer().getOnlinePlayers().size() + " online players");
    }

    @EventHandler
    public void onPlace(BlockPlaceEvent event) {
        ItemStack item = event.getItemInHand();
        if (!item.hasItemMeta()) return;
        var pdc = item.getItemMeta().getPersistentDataContainer();
        if (!pdc.has(machineTypeKey, PersistentDataType.STRING)) return;

        MachineType type;
        try { type = MachineType.valueOf(pdc.get(machineTypeKey, PersistentDataType.STRING)); }
        catch (IllegalArgumentException e) { return; }

        Location placed = event.getBlock().getLocation();

        // 重力落下するブロック(金床など)は、下が不健全だと落下して二重化バグになる
        if (event.getBlock().getType().hasGravity()) {
            org.bukkit.block.Block below = event.getBlock().getRelative(org.bukkit.block.BlockFace.DOWN);
            if (!below.getType().isSolid()) {
                event.setCancelled(true);
                event.getPlayer().sendMessage(Component.text(
                        "§cこの" + type.displayName + "は落下します。下に固体ブロックを設置してください。"));
                return;
            }
        }

        machineManager.register(placed, type);
        energyManager.initData(placed, type);

        // 機器制御アタッチメント: 設置した面に機械があればオーバーライド登録
        if (type == MachineType.BLOCK_CONTROL_ATTACHMENT) {
            registerControlOverride(placed);
        }

        if (type == MachineType.CHUNK_LOADER) {
            event.getBlock().getChunk().setForceLoaded(true);
            event.getPlayer().sendMessage(Component.text(
                    "[ChunkLoader] チャンクを常時ロード状態に設定しました。", NamedTextColor.GREEN));
        } else {
            event.getPlayer().sendMessage(Component.text(
                    "[Machine] " + type.displayName + " を設置しました。右クリックでGUIを開きます。",
                    NamedTextColor.GREEN));
        }
    }

    @EventHandler
    public void onBreak(BlockBreakEvent event) {
        Location loc = event.getBlock().getLocation();
        if (!machineManager.isMachine(loc)) return;

        MachineType type = machineManager.remove(loc);
        energyManager.removeData(loc);

        // キャッシュクリーンアップ
        controlAttachmentOverrides.remove(locKey(loc));
        // BLOCK_CONTROL_ATTACHMENT破壊時: 隣接機械のオーバーライドを削除
        if (type == MachineType.BLOCK_CONTROL_ATTACHMENT) {
            for (BlockFace face : ADJACENT_FACES) {
                controlAttachmentOverrides.remove(locKey(loc.clone().add(face.getDirection())));
            }
        }

        // liveInventories をクリーンアップ (GUI開封中に破壊された場合の対策)
        machineManager.unregisterLiveInventory(loc);

        event.setDropItems(false);
        event.getBlock().getWorld().dropItemNaturally(loc, buildMachineItem(type));
        // 自然ドロップを完全に防止するためブロックを即時空気に
        event.getBlock().setType(Material.AIR);

        // インベントリ内容をドロップ
        if (type == MachineType.BLOCK_PLACER) {
            // BLOCK_PLACER はバレルの実インベントリを使用
            prevPowered.remove(locKey(loc));
            if (event.getBlock().getState() instanceof Barrel barrel) {
                for (ItemStack stack : barrel.getInventory().getContents()) {
                    if (stack != null && !stack.getType().isAir()) {
                        event.getBlock().getWorld().dropItemNaturally(loc, stack);
                    }
                }
                barrel.getInventory().clear();
            }
        } else if (type == MachineType.BLOCK_BREAKER || type == MachineType.IGNITER
                || type == MachineType.RIGHT_CLICKER) {
            prevPowered.remove(locKey(loc));
            // RIGHT_CLICKER: ディスペンサーの実インベントリをドロップ
            if (type == MachineType.RIGHT_CLICKER
                    && event.getBlock().getState() instanceof org.bukkit.block.Dispenser disp) {
                for (ItemStack stack : disp.getInventory().getContents()) {
                    if (stack != null && !stack.getType().isAir())
                        event.getBlock().getWorld().dropItemNaturally(loc, stack);
                }
                disp.getInventory().clear();
            }
        } else if (type == MachineType.MINER) {
            prevPowered.remove(locKey(loc));
            ItemStack[] contents = machineManager.removeStoredContents(loc);
            if (contents != null) {
                for (ItemStack stack : contents) {
                    if (stack != null && !stack.getType().isAir())
                        event.getBlock().getWorld().dropItemNaturally(loc, stack);
                }
            }
        } else if (type == MachineType.TIMER || type == MachineType.ENTITY_DETECTOR || type == MachineType.SOLAR_PANEL) {
            // 南面のRSブロックを回収
            Block south = loc.getBlock().getRelative(BlockFace.SOUTH);
            Location sl = south.getLocation().toBlockLocation();
            if (machineOwnedRSBlocks.remove(sl)) south.setType(Material.AIR);
            // TIMERの内部状態クリア
            if (type == MachineType.TIMER) {
                String key = locKey(loc);
                timerState.remove(key);
                timerPeriods.remove(key);
                timerCount.remove(key);
            }
        } else if (type == MachineType.CHUNK_LOADER) {
            loc.getBlock().getChunk().setForceLoaded(false);
        } else if (type == MachineType.BLOCK_TRANSMUTER || type == MachineType.VERTICAL_ELEVATOR) {
            prevPowered.remove(locKey(loc));
            ItemStack[] contents = machineManager.removeStoredContents(loc);
            if (contents != null) {
                for (ItemStack stack : contents) {
                    if (stack != null && !stack.getType().isAir())
                        event.getBlock().getWorld().dropItemNaturally(loc, stack);
                }
            }
        } else {
            // CRUSHER / COMPRESSOR / AUTO_CRAFTER / AUTO_SMELTER / VACUUM_HOPPER / AUTO_BREWER 等
            ItemStack[] contents = machineManager.removeStoredContents(loc);
            if (contents != null) {
                for (ItemStack stack : contents) {
                    if (stack != null && !stack.getType().isAir()) {
                        event.getBlock().getWorld().dropItemNaturally(loc, stack);
                    }
                }
            }
        }

        // Close any open GUIs for this machine
        Inventory openInv = openGuis.remove(loc.toBlockLocation());
        if (openInv != null) {
            for (org.bukkit.entity.HumanEntity viewer : new ArrayList<>(openInv.getViewers())) viewer.closeInventory();
        }

        // ネイティブブロックインベントリ（FURNACE/BARREL等）の残りアイテムをクリーンアップ
        if (event.getBlock().getState() instanceof org.bukkit.block.Container container) {
            boolean hadItems = false;
            for (ItemStack stack : container.getInventory().getContents()) {
                if (stack != null && !stack.getType().isAir()) {
                    event.getBlock().getWorld().dropItemNaturally(loc, stack);
                    hadItems = true;
                }
            }
            if (hadItems) container.getInventory().clear();
        }
        // STORAGE_CONTROLLERはopenGuisに登録されないため、全プレイヤーを個別に確認して閉じる
        if (type == MachineType.STORAGE_CONTROLLER) {
            Location bl = loc.toBlockLocation();
            for (Player p : plugin.getServer().getOnlinePlayers()) {
                if (p.getOpenInventory().getTopInventory().getHolder() instanceof MachineInvHolder mh
                        && mh.type == MachineType.STORAGE_CONTROLLER
                        && mh.loc.toBlockLocation().equals(bl)) {
                    p.closeInventory();
                }
            }
        }

        event.getPlayer().sendMessage(Component.text(
                "[Machine] " + type.displayName + " を回収しました。", NamedTextColor.YELLOW));
    }

    /** エンダーマン等による機械ブロックの持去りを防止 */
    @EventHandler(ignoreCancelled = true)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        if (machineManager.isMachine(event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    /** 爆発による機械ブロック破壊を防止 */
    @EventHandler(ignoreCancelled = true)
    public void onEntityExplode(org.bukkit.event.entity.EntityExplodeEvent event) {
        event.blockList().removeIf(b -> machineManager.isMachine(b.getLocation()));
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockExplode(org.bukkit.event.block.BlockExplodeEvent event) {
        event.blockList().removeIf(b -> machineManager.isMachine(b.getLocation()));
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getClickedBlock() == null) return;
        Block block = event.getClickedBlock();
        if (!machineManager.isMachine(block.getLocation())) return;

        Player player = event.getPlayer();
        if (player.isSneaking()) return; // スニーク中はブロック設置を優先

        event.setCancelled(true);
        MachineType type = machineManager.getType(block.getLocation());

        // バッテリー放電: 手持ちにバッテリーがある → 機械にEN転送
        ItemStack handItem = player.getInventory().getItemInMainHand();
        if (CircuitSurvivalPlugin.isBattery(handItem)) {
            int batEn = CircuitSurvivalPlugin.getBatteryEn(handItem);
            if (batEn > 0 && energyManager.needsEnergy(type)) {
                int give = Math.min(batEn, 100);
                energyManager.generate(block.getLocation(), give);
                CircuitSurvivalPlugin.setBatteryEn(handItem, batEn - give);
                player.sendActionBar(Component.text("§bバッテリー → §e" + give + "EN 供給 (残 " + (batEn - give) + "EN)"));
                player.updateInventory();
            } else if (batEn <= 0) {
                player.sendActionBar(Component.text("§cバッテリーが空です"));
            }
            return;
        }

        String key = locKey(block);

        switch (type) {
            case BLOCK_PLACER -> {
                // バレルの実インベントリを直接開く
                if (block.getState() instanceof Barrel barrel) {
                    player.openInventory(barrel.getInventory());
                }
            }
            case BLOCK_BREAKER -> {
                BlockFace facing = getMachineFacing(block.getLocation());
                player.sendMessage(Component.text(
                        "[ブロック破壊装置] 向き: " + facingName(facing)
                        + " | RSの立ち上がり信号で前面ブロックを破壊・ドロップします。",
                        NamedTextColor.AQUA));
            }
            case AUTO_CRAFTER -> player.openInventory(createAutoCrafterGui(block.getLocation()));
            case ITEM_SORTER -> player.openInventory(createFullInvGui(type, block.getLocation()));
            case AUTO_FARMER -> player.openInventory(createFullInvGui(type, block.getLocation()));
            case IGNITER -> {
                BlockFace igFacing = getMachineFacing(block.getLocation());
                player.sendMessage(Component.text(
                        "[着火装置] 向き: " + facingName(igFacing)
                        + " | RS立ち上がり信号で前面ブロックに着火します。", NamedTextColor.AQUA));
            }
            case AUTO_SMELTER -> player.openInventory(createSmelterGui(key, block.getLocation()));
            case MINER -> {
                BlockFace mnFacing = getMachineFacing(block.getLocation());
                player.sendMessage(Component.text(
                        "[採掘機] 向き: " + facingName(mnFacing)
                        + " | RS立ち上がり信号で前面ブロックを採掘します。", NamedTextColor.AQUA));
                player.openInventory(createFullInvGui(type, block.getLocation()));
            }
            case VACUUM_HOPPER -> player.openInventory(createFullInvGui(type, block.getLocation()));
            case RIGHT_CLICKER -> {
                if (block.getState() instanceof org.bukkit.block.Dispenser disp) {
                    player.openInventory(disp.getInventory());
                }
            }
            case ENTITY_DETECTOR -> {
                BlockFace edFacing = getMachineFacing(block.getLocation());
                player.sendMessage(Component.text(
                        "[エンティティ検知機] 半径8ブロック内のエンティティを検知してRS信号を南面に出力します。"
                        + " 向き: " + facingName(edFacing), NamedTextColor.AQUA));
            }
            case SOLAR_PANEL -> {
                long time = block.getWorld().getTime();
                boolean day = time >= 0 && time < 12000;
                boolean sky = block.getLightFromSky() >= 15
                        || block.getY() >= block.getWorld().getHighestBlockYAt(block.getX(), block.getZ());
                player.sendMessage(Component.text(
                        "[日照発電機] 時刻: " + time + "tick"
                        + " | 日照: " + (day ? "昼" : "夜")
                        + " | 天空: " + (sky ? "○" : "×")
                        + " | " + (day && sky ? "§a発電中(10EN/tick)" : "§7停止中"),
                        NamedTextColor.AQUA));
            }
            case WATER_GENERATOR -> {
                boolean waterNear = hasAdjacentWater(block);
                int wCur = energyManager.getEnergy(block.getLocation());
                int wMax = energyManager.getMaxEnergy(block.getLocation());
                player.sendMessage(Component.text(
                        "[水力発電機] EN: " + wCur + " / " + wMax
                        + " | 水源: " + (waterNear ? "§a◎(3EN/tick)" : "§7×")
                        + (waterNear ? " §a発電中" : " §7停止中"),
                        NamedTextColor.AQUA));
            }
            case WIND_GENERATOR -> {
                int y = block.getY();
                int power = Math.min(6, Math.max(1, y / 32));
                int wCur = energyManager.getEnergy(block.getLocation());
                int wMax = energyManager.getMaxEnergy(block.getLocation());
                player.sendMessage(Component.text(
                        "[風力発電機] EN: " + wCur + " / " + wMax
                        + " | 高さ: Y=" + y
                        + " | 風力: §a" + power + " EN/tick",
                        NamedTextColor.AQUA));
            }
            case AUTO_SHEARER -> player.openInventory(createFullInvGui(type, block.getLocation()));
            case CRUSHER, COMPRESSOR -> player.openInventory(createFullInvGui(type, block.getLocation()));
            case CHUNK_LOADER -> {
                boolean loaded = block.getChunk().isForceLoaded();
                player.sendMessage(Component.text(
                        "[チャンクローダー] このチャンクは常時ロード" + (loaded ? "済み" : "解除中") + "。",
                        NamedTextColor.AQUA));
            }
            case AUTO_BREWER -> player.openInventory(createBrewerGui(block.getLocation()));
            case ITEM_ROUTER -> player.openInventory(createRouterGui(block.getLocation()));
            case FLUID_COLLECTOR -> player.openInventory(createFullInvGui(type, block.getLocation()));
            case BLOCK_TRANSMUTER -> player.openInventory(createFullInvGui(type, block.getLocation()));
            case BONE_MEALER -> player.openInventory(createFullInvGui(type, block.getLocation()));
            case VACUUM_HOPPER_CTRL    -> player.openInventory(createVacuumCtrlGui(block.getLocation()));
            case XP_CONVERTER, FAST_HOPPER, VERT_FAST_HOPPER, BULK_DROPPER, AUTO_FISHER ->
                player.openInventory(createFullInvGui(type, block.getLocation()));
            case STORAGE_DRUM          -> player.openInventory(createDrumGui(block.getLocation()));
            case STORAGE_CONTROLLER    -> player.openInventory(openStorageControllerGui(block.getLocation()));
            case WOODCUTTER           -> player.openInventory(createFullInvGui(type, block.getLocation()));
            case VERTICAL_ELEVATOR    -> player.openInventory(createGui(type, key, block.getLocation()));
            case CUSTOM_CRAFTER       -> player.openInventory(createCustomCrafterGui(block.getLocation()));
            case PULVERIZER           -> player.openInventory(createFullInvGui(type, block.getLocation()));
            case ELECTRIC_FURNACE     -> player.openInventory(createFullInvGui(type, block.getLocation()));
            case AUTO_ANVIL           -> player.openInventory(createFullInvGui(type, block.getLocation()));
            case GENERATOR            -> player.openInventory(createFullInvGui(type, block.getLocation()));
            case ENERGY_CELL          -> player.openInventory(createEnergyCellGui(block.getLocation()));
            case CHARGER              -> player.openInventory(createChargerGui(block.getLocation()));
            case HV_CELL              -> player.openInventory(createHvCellGui(block.getLocation()));
            case AUTO_ENCHANTER       -> player.openInventory(createFullInvGui(type, block.getLocation()));
            case VOID_MINER           -> player.openInventory(createFullInvGui(type, block.getLocation()));
            case ENERGY_CABLE         -> player.sendMessage(Component.text(
                    "[送電ワイヤー] ENを隣接ブロック間で転送します。RS不要。",
                    NamedTextColor.AQUA));
            case INDUCTION_FURNACE    -> player.openInventory(createFullInvGui(type, block.getLocation()));
            case CENTRIFUGE           -> player.openInventory(createFullInvGui(type, block.getLocation()));
            case COMBUSTION_GENERATOR -> player.openInventory(createFullInvGui(type, block.getLocation()));
            case ORE_PROCESSOR        -> player.openInventory(createFullInvGui(type, block.getLocation()));
            case MATERIALIZER         -> player.openInventory(createFullInvGui(type, block.getLocation()));
            case RECYCLER             -> player.openInventory(createFullInvGui(type, block.getLocation()));
            case WIRELESS_CHARGER     -> {
                int wcCur = energyManager.getEnergy(block.getLocation());
                int wcMax = energyManager.getMaxEnergy(block.getLocation());
                player.sendMessage(Component.text(
                        "[ワイヤレス充電器] EN: " + wcCur + " / " + wcMax
                        + " | 半径5ブロック内の機械に自動給電",
                        NamedTextColor.AQUA));
            }
            case AUTO_DISENCHANTER    -> player.openInventory(createFullInvGui(type, block.getLocation()));
            case THERMAL_GENERATOR     -> {
                int tgCur = energyManager.getEnergy(block.getLocation());
                int tgMax = energyManager.getMaxEnergy(block.getLocation());
                String biome = block.getBiome().name();
                player.sendMessage(Component.text(
                        "[熱発電機] EN: " + tgCur + " / " + tgMax
                        + " | 環境: " + biome
                        + " | §a受動発電中",
                        NamedTextColor.AQUA));
            }
            case ADVANCED_ASSEMBLER   -> player.openInventory(createFullInvGui(type, block.getLocation()));
            case NEUTRON_COMPRESSOR   -> player.openInventory(createFullInvGui(type, block.getLocation()));
            case COOKING_STATION, PIXEL_FORGE, TIMER -> player.openInventory(createFullInvGui(type, block.getLocation()));
            case BLOCK_CONTROL_ATTACHMENT -> {
                BlockFace ctrlDir = getControlFacing(block);
                player.sendMessage(Component.text(
                        "[機器制御アタッチメント] 向き: " + (ctrlDir != null ? facingName(ctrlDir) : "未設定")
                        + " | 隣接機械の動作方向を変更します。", NamedTextColor.AQUA));
            }
            case TRASH_CAN             -> player.openInventory(createFullInvGui(type, block.getLocation()));
            case WASHING_MACHINE      -> player.openInventory(createWashingMachineGui(block.getLocation()));
            case CRAFTER_CONTROLLER   -> player.openInventory(createFullInvGui(type, block.getLocation()));
            case DISTILLATION_TOWER   -> player.openInventory(createFullInvGui(type, block.getLocation()));
            case CHEMICAL_REACTOR     -> player.openInventory(createFullInvGui(type, block.getLocation()));
            case VACUUM_FURNACE      -> player.openInventory(createFullInvGui(type, block.getLocation()));
            case HIGH_PRESSURE_PRESS -> player.openInventory(createFullInvGui(type, block.getLocation()));
            case MOBILE_PLATFORM      -> {
                Block below = block.getRelative(BlockFace.DOWN);
                String belowInfo = machineManager.isMachine(below.getLocation())
                        ? "§a" + machineManager.getType(below.getLocation()).displayName + " 搭載中"
                        : "§7空 (下に機械を設置すると搭載)";
                player.sendMessage(Component.text(
                        "[移動プラットフォーム] " + belowInfo
                        + " | §eピストンで押すと下の機械ごと移動",
                        NamedTextColor.AQUA));
            }
            default -> player.openInventory(createFullInvGui(type, block.getLocation()));
        }
    }

    // ---- ホッパーからBLAST_FURNACE(BLOCK_BREAKER)へのアイテム消失防止 ----------

    /**
     * BLOCK_BREAKER (BLAST_FURNACE) がアイテムを精錬してしまうのを防ぐ
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFurnaceBurn(FurnaceBurnEvent event) {
        if (machineManager.isMachine(event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFurnaceSmelt(FurnaceSmeltEvent event) {
        if (machineManager.isMachine(event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    /**
     * ホッパーがBLAST_FURNACE(BLOCK_BREAKER)のバニラインベントリにアイテムを
     * 移動しようとした場合にキャンセル (アイテム消失防止)
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryMove(InventoryMoveItemEvent event) {
        // 搬入先がバニラコンテナの場合のみチェック
        Inventory dest = event.getDestination();
        Location destLoc = getInventoryBlockLocation(dest);
        if (destLoc == null) return;
        if (!machineManager.isMachine(destLoc)) return;

        MachineType destType = machineManager.getType(destLoc);
        // 仮想インベントリを持つ機械 (=hasMachineInventory) へのバニラコンテナ搬入をブロック
        // (BLOCK_PLACER等 実インベントリ使用の機械は許可)
        if (MachineManager.hasMachineInventory(destType)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        if (!(event.getInventory().getHolder() instanceof MachineInvHolder holder)) return;

        MachineType type = holder.type;

        // プレイヤーのGUI操作後に machineContents を同期 (ゴミ箱は実インベントリなしでスキップ)
        if (MachineManager.hasMachineInventory(type)) scheduleGuiSync(holder.loc);

        // ゴミ箱: 即時消去 (トップインベントリへの配置は即座に消える)
        if (type == MachineType.TRASH_CAN) {
            int raw = event.getRawSlot();
            if (raw >= 0 && raw < 27) {
                event.setCancelled(true);
                player.setItemOnCursor(null);
            }
            // raw >= 27: プレイヤーインベントリ側の操作は許可 (Shift+Clickで投入 → 定期タスクが消去)
            return;
        }

        if (type == MachineType.AUTO_CRAFTER) {
            // スロット0-8=レシピグリッド, 9-17=素材バッファ, 26=出力
            // スロット18-25は装飾固定
            int s = event.getRawSlot();
            if (s >= 18 && s <= 25) { event.setCancelled(true); return; }
            // 万が一装飾PDCが18-25外に出た場合に備えてここでもチェック
            ItemStack cl = event.getCurrentItem();
            if (cl != null && cl.hasItemMeta()
                    && cl.getItemMeta().getPersistentDataContainer()
                        .has(org.bukkit.NamespacedKey.fromString("circuitsurvival:decoration"),
                                org.bukkit.persistence.PersistentDataType.BOOLEAN)) {
                event.setCancelled(true);
            }
            return;
        }

        // ---- STORAGE_CONTROLLER: 仮想ビュー操作 ----
        if (type == MachineType.STORAGE_CONTROLLER) {
            Inventory ctrlGui = event.getInventory();
            event.setCancelled(true);
            int rawSlot = event.getRawSlot();
            if (rawSlot >= ctrlGui.getSize()) return; // プレイヤーインベントリ側

            // ナビゲーション行 (スロット45-53)
            if (rawSlot == 45) { // ← 前ページ
                if (holder.page > 0) {
                    holder.page--;
                    refreshControllerGui(ctrlGui, holder.loc);
                }
                return;
            }
            if (rawSlot == 53) { // → 次ページ
                if (holder.page < getControllerMaxPage(holder.loc)) {
                    holder.page++;
                    refreshControllerGui(ctrlGui, holder.loc);
                }
                return;
            }
            if (rawSlot >= 45) return; // その他ナビスロット

            // アイテムスロット (0-44)
            ItemStack cursor = event.getCursor();
            if (cursor != null && !cursor.getType().isAir()) {
                // カーソルアイテムをドラムへ
                pushItemToDrums(holder.loc, cursor.clone());
                event.getWhoClicked().setItemOnCursor(new ItemStack(Material.AIR));
                plugin.getServer().getScheduler().runTask(plugin, () -> refreshControllerGui(ctrlGui, holder.loc));
                return;
            }
            ItemStack displayed = ctrlGui.getItem(rawSlot);
            if (displayed == null || displayed.getType().isAir()) return;
            // テンプレートキャッシュからlore未付加の元アイテムを取得 (isSimilar比較を正確にするため)
            List<ItemStack> tplList = controllerTemplates.get(machineManager.locKey(holder.loc));
            int idx = holder.page * 45 + rawSlot;
            if (tplList == null || idx >= tplList.size()) {
                plugin.getServer().getScheduler().runTask(plugin, () -> refreshControllerGui(ctrlGui, holder.loc));
                return;
            }
            ItemStack template = tplList.get(idx);
            int total = getTotalInDrums(holder.loc, template);
            if (total <= 0) { refreshControllerGui(ctrlGui, holder.loc); return; }

            int takeAmount = event.isShiftClick() ? total
                    : event.isRightClick() ? Math.max(1, Math.min(16, total))
                    : Math.min(32, total);
            ItemStack pulled = pullFromDrums(holder.loc, template, takeAmount);
            if (pulled == null) return;
            if (event.isShiftClick()) {
                event.getWhoClicked().getInventory().addItem(pulled);
            } else {
                ItemStack cur = event.getWhoClicked().getItemOnCursor();
                if (cur == null || cur.getType().isAir()) {
                    event.getWhoClicked().setItemOnCursor(pulled);
                } else if (cur.isSimilar(pulled)
                        && cur.getAmount() + pulled.getAmount() <= cur.getMaxStackSize()) {
                    cur.setAmount(cur.getAmount() + pulled.getAmount());
                } else {
                    event.getWhoClicked().getInventory().addItem(pulled);
                }
            }
            plugin.getServer().getScheduler().runTask(plugin, () -> refreshControllerGui(ctrlGui, holder.loc));
            return;
        }

        // ---- 実行ボタン処理 (decorationチェックより先に判定) --------------------
        int rawSlot = event.getRawSlot();
        if (rawSlot == 8) {
            if (type == MachineType.WASHING_MACHINE || type == MachineType.PIXEL_FORGE
                    || type == MachineType.COOKING_STATION) {
                event.setCancelled(true);
                processMachine(player, type, null, event.getInventory(), holder.loc);
                return;
            }
        }

        // ---- 全モード共通: 装飾PDCアイテムは絶対に取らせない --------------------

        ItemStack clicked = event.getCurrentItem();
        if (clicked != null && clicked.hasItemMeta()
                && clicked.getItemMeta().getPersistentDataContainer()
                    .has(org.bukkit.NamespacedKey.fromString("circuitsurvival:decoration"),
                            org.bukkit.persistence.PersistentDataType.BOOLEAN)) {
            event.setCancelled(true);
            return;
        }

        // ---- GuiModeによる統一クリック制御 ------------------------------------------

        GuiMode mode = guiMode(type);

        switch (mode) {
            case FULL -> {
                return;
            }
            case SMELTER, TWO_SLOT -> {
                if (rawSlot != 0 && rawSlot != 26 && rawSlot < 27) event.setCancelled(true);
                return;
            }
            case THREE_SLOT -> {
                if (rawSlot != 0 && rawSlot != 1 && rawSlot != 26 && rawSlot < 27) event.setCancelled(true);
                return;
            }
            case TIMER -> {
                String locKey = machineManager.locKey(holder.loc);
                if (rawSlot == 13) {
                    event.setCancelled(true);
                    int period = timerPeriods.getOrDefault(locKey, 20);
                    int delta  = event.isShiftClick() ? 20 : 1;
                    if (event.isLeftClick())       period = Math.max(2, period - delta);
                    else if (event.isRightClick()) period = period + delta;
                    timerPeriods.put(locKey, period);
                    timerCount.put(locKey, period);
                    event.getView().close();
                    player.sendMessage(Component.text("[Timer] 周期を " + period + " tick に設定しました。",
                            NamedTextColor.AQUA));
                }
                return;
            }
            case AUTO_CRAFT -> { return; } // 全スロット許可
            case STORAGE_CTRL -> { return; } // 既に上部で処理済み
            case DRUM -> { return; } // 全スロット許可(54)
        }

    }

    @EventHandler
    public void onInventoryDrag(org.bukkit.event.inventory.InventoryDragEvent event) {
        if (!(event.getInventory().getHolder() instanceof MachineInvHolder holder)) return;
        // 装飾スロットへのドラッグを禁止 (ガラス板が持ち上がるのを防止)
        for (int slot : event.getRawSlots()) {
            ItemStack item = event.getInventory().getItem(slot);
            if (item != null && item.hasItemMeta()
                    && item.getItemMeta().getPersistentDataContainer()
                        .has(org.bukkit.NamespacedKey.fromString("circuitsurvival:decoration"),
                                org.bukkit.persistence.PersistentDataType.BOOLEAN)) {
                event.setCancelled(true);
                break;
            }
        }
        scheduleGuiSync(holder.loc);
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof MachineInvHolder holder)) return;

        MachineType type = holder.type;
        Inventory inv = event.getInventory();

        if (event.getInventory().getViewers().size() > 1) return; // other players still viewing
        openGuis.remove(holder.loc.toBlockLocation());
        machineManager.unregisterLiveInventory(holder.loc);

        // ゴミ箱: 閉じた時に全アイテム消去
        if (type == MachineType.TRASH_CAN) {
            for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, null);
            return;
        }

        // ストレージコントローラー: hasMachineInventory=false だがテンプレートキャッシュ後始末が必要
        if (type == MachineType.STORAGE_CONTROLLER) {
            boolean otherOpen = plugin.getServer().getOnlinePlayers().stream().anyMatch(p ->
                    p.getOpenInventory().getTopInventory().getHolder() instanceof MachineInvHolder mh
                    && mh.type == MachineType.STORAGE_CONTROLLER
                    && machineManager.locKey(mh.loc).equals(machineManager.locKey(holder.loc)));
            if (!otherOpen) controllerTemplates.remove(machineManager.locKey(holder.loc));
            return;
        }

        // ---- GuiModeによる統一クローズ保存 -----------------------------------------

        if (!MachineManager.hasMachineInventory(type)) return; // 実インベントリなし(=特殊GUI)はスキップ

        GuiMode cmode = guiMode(type);
        switch (cmode) {
            case DRUM -> {
                // 54スロット全保存
                ItemStack[] saved = new ItemStack[54];
                for (int i = 0; i < 54; i++) saved[i] = inv.getItem(i) != null ? inv.getItem(i).clone() : null;
                machineManager.setStoredContents(holder.loc, saved);
            }
            case FULL -> {
                // 27スロット全保存
                ItemStack[] saved = new ItemStack[27];
                for (int i = 0; i < 27; i++) saved[i] = inv.getItem(i) != null ? inv.getItem(i).clone() : null;
                machineManager.setStoredContents(holder.loc, saved);
            }
            case THREE_SLOT -> {
                // 0, 1, 26のみ保存 (PIXEL_FORGE)
                ItemStack[] saved = new ItemStack[27];
                saved[0]  = inv.getItem(0)  != null ? inv.getItem(0).clone()  : null;
                saved[1]  = inv.getItem(1)  != null ? inv.getItem(1).clone()  : null;
                saved[26] = inv.getItem(26) != null ? inv.getItem(26).clone() : null;
                machineManager.setStoredContents(holder.loc, saved);
            }
            case SMELTER, TWO_SLOT -> {
                // 0と26のみ保存
                ItemStack[] saved = new ItemStack[27];
                saved[0]  = inv.getItem(0)  != null ? inv.getItem(0).clone()  : null;
                saved[26] = inv.getItem(26) != null ? inv.getItem(26).clone() : null;
                machineManager.setStoredContents(holder.loc, saved);
            }
            case STORAGE_CTRL -> {
                // 仮想ビューのためデータ保存不要 (上で先処理済み)
            }
            case TIMER, AUTO_CRAFT -> {
                // 全27スロット保存 (AUTO_CRAFTERはバッファ+出力)
                ItemStack[] saved = new ItemStack[27];
                for (int i = 0; i < 27; i++) saved[i] = inv.getItem(i) != null ? inv.getItem(i).clone() : null;
                machineManager.setStoredContents(holder.loc, saved);
            }
        }
    }

    // ==========================================================================
    // マシン処理 (CRUSHER / COMPRESSOR / DUPLICATOR)
    // ==========================================================================

    private void processMachine(Player player, MachineType type, String locKey, Inventory inv, Location loc) {
        ItemStack inputA = inv.getItem(0);

        switch (type) {
            case CRUSHER -> {
                if (isEmpty(inputA)) { player.sendMessage(err("[Crusher] 入力がありません。")); return; }
                ItemStack output = crushOre(inputA.getType());
                if (output == null) { player.sendMessage(err("[Crusher] このブロックは粉砕できません。")); return; }
                if (!mergeOutput(inv, 26, output)) { player.sendMessage(err("[Crusher] 出力スロットが満杯です。")); return; }
                consumeInput(inv, 0, 1);
                player.sendMessage(Component.text("[Crusher] 粉砕完了！", NamedTextColor.GREEN));
            }
            case COMPRESSOR -> {
                if (isEmpty(inputA)) { player.sendMessage(err("[Compressor] 入力がありません。")); return; }
                ItemStack output = compress(inputA);
                if (output == null) { player.sendMessage(err("[Compressor] この素材は変換できません。")); return; }
                ItemStack curOut = inv.getItem(26);
                if (curOut != null && !curOut.getType().isAir() && curOut.getType() != output.getType()) {
                    player.sendMessage(err("[Compressor] 出力スロットに別のアイテムがあります。")); return;
                }
                int needed = (output.getType().getMaxStackSize() == 1) ? 1 : 9;
                int have   = inputA.getAmount();
                if (have < needed) { player.sendMessage(err("[Compressor] 素材が足りません。必要: " + needed)); return; }
                int batches = have / needed;
                consumeInput(inv, 0, batches * needed);
                int outAmt = (curOut != null && !curOut.getType().isAir() ? curOut.getAmount() : 0) + batches;
                output.setAmount(outAmt);
                inv.setItem(26, output);
                player.sendMessage(Component.text("[Compressor] 変換完了！", NamedTextColor.GREEN));
            }
            case COOKING_STATION -> processCookingStation(player, inv);
            case PIXEL_FORGE     -> processPixelForge(player, inv);
            case WASHING_MACHINE -> processWashingMachine(player, inv);
            default -> {}
        }
    }

    // ---- 料理台 ---------------------------------------------------------------

    private void processCookingStation(Player player, Inventory inv) {
        ItemStack input = inv.getItem(0);
        if (isEmpty(input)) { player.sendMessage(err("[料理台] 食材がありません。")); return; }

        ItemStack output = switch (input.getType()) {
            case POTATO         -> CustomItems.build(CustomItems.EXPLOSIVE_CROQUETTE); // じゃがいも→爆発コロッケ
            case GOLDEN_CARROT,
                 RABBIT_STEW,
                 BEETROOT_SOUP  -> CustomItems.build(CustomItems.SPICE_CURRY);          // 特製スパイスカレー
            default -> null;
        };
        if (output == null) {
            player.sendMessage(err("[料理台] このアイテムは料理できません。POTATO/GOLDEN_CARROT/RABBIT_STEW/BEETROOT_SOUPを使用。"));
            return;
        }
        if (!isEmpty(inv.getItem(26))) { player.sendMessage(err("[料理台] 出力スロットが埋まっています。")); return; }
        consumeInput(inv, 0, 1);
        inv.setItem(26, output);
        player.sendMessage(Component.text("[料理台] 料理完了！", NamedTextColor.GREEN));
    }

    // ---- ピクセル鍛冶炉 -------------------------------------------------------

    private void processPixelForge(Player player, Inventory inv) {
        ItemStack base     = inv.getItem(0);
        ItemStack modifier = inv.getItem(1);
        if (isEmpty(base)) { player.sendMessage(err("[ピクセル鍛冶炉] スロット0にベース武器を入れてください。")); return; }
        if (isEmpty(modifier)) { player.sendMessage(err("[ピクセル鍛冶炉] スロット1に改造素材を入れてください。")); return; }

        Material b = base.getType();
        Material m = modifier.getType();

        String id = null;
        if      (b == Material.IRON_SWORD        && m == Material.GOLD_INGOT)        id = CustomItems.CHEF_KNIFE;
        else if (b == Material.CROSSBOW          && m == Material.AMETHYST_SHARD)    id = CustomItems.PIXEL_GUN;
        else if (b == Material.CROSSBOW          && m == Material.GLASS)             id = CustomItems.SNIPER_RIFLE;
        else if (b == Material.NETHERITE_SWORD   && m == Material.NETHER_STAR)       id = CustomItems.DARK_SABER;
        else if (b == Material.BOW               && m == Material.FIRE_CHARGE)       id = CustomItems.GRENADE_LAUNCHER;

        if (id == null) {
            player.sendMessage(err("[ピクセル鍛冶炉] 対応レシピが見つかりません。" +
                    "例: 鉄剣+金インゴット, クロスボウ+アメジストの欠片, NETHERITE剣+ネザースター, 弓+火の玉, クロスボウ+ガラス"));
            return;
        }
        if (!isEmpty(inv.getItem(26))) { player.sendMessage(err("[ピクセル鍛冶炉] 出力スロットが埋まっています。")); return; }
        consumeInput(inv, 0, 1);
        consumeInput(inv, 1, 1);
        inv.setItem(26, CustomItems.build(id));
        player.sendMessage(Component.text("[ピクセル鍛冶炉] 鍛造完了！", NamedTextColor.GREEN));
    }

    // ---- 洗浄機 (手動) ---------------------------------------------------------

    private void processWashingMachine(Player player, Inventory inv) {
        ItemStack input = inv.getItem(0);
        ItemStack water = inv.getItem(1);
        if (isEmpty(input)) { player.sendMessage(err("[洗浄機] 洗浄素材(スロット0)がありません。")); return; }
        if (isEmpty(water) || water.getType() != Material.WATER_BUCKET) {
            player.sendMessage(err("[洗浄機] 水入りバケツ(スロット1)が必要です。")); return;
        }
        WashResult recipe = WASH_MAP.get(input.getType());
        if (recipe == null) { player.sendMessage(err("[洗浄機] この素材は洗浄できません。砂利/丸石/砂/赤砂/ソウルサンドのみ。")); return; }
        // 出力スロット空き確認 (チャンス成功時に備える)
        ItemStack outItem = inv.getItem(26);
        if (outItem != null && !outItem.getType().isAir()
                && (outItem.getType() != recipe.output() || outItem.getAmount() >= outItem.getMaxStackSize())) {
            player.sendMessage(err("[洗浄機] 出力スロットが満杯です。")); return;
        }
        // 水バケツ消費
        if (water.getAmount() > 1) { water.setAmount(water.getAmount() - 1); }
        else { inv.setItem(1, null); }
        consumeInput(inv, 0, 1);
        // 処理
        boolean success = RANDOM.nextInt(100) < recipe.chancePercent();
        if (success) {
            mergeOutput(inv, 26, new ItemStack(recipe.output()));
            player.sendMessage(Component.text("[洗浄機] 副産物が取り出せました！", NamedTextColor.GREEN));
        } else {
            player.sendMessage(Component.text("[洗浄機] 洗浄しましたが何も取れませんでした…", NamedTextColor.YELLOW));
        }
        // 空バケツ返却 (slot 26が空ならそこへ、なければslot 1へ)
        ItemStack emptyBucket = new ItemStack(Material.BUCKET);
        if (isEmpty(inv.getItem(26))) {
            inv.setItem(26, emptyBucket);
        } else {
            inv.setItem(1, emptyBucket);
        }
    }

    // ==========================================================================
    // タイマータスク
    // ==========================================================================

    private void startTimerTask() {
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            for (Location loc : new ArrayList<>(machineManager.getByType(MachineType.TIMER))) {
                String key = locKey(loc);
                int period = timerPeriods.getOrDefault(key, 20);
                int count  = timerCount.getOrDefault(key, period) - 1;
                if (count <= 0) {
                    boolean state = !timerState.getOrDefault(key, false);
                    timerState.put(key, state);
                    Block out = loc.getBlock().getRelative(BlockFace.SOUTH);
                    if (state  && out.getType() == Material.AIR) {
                        out.setType(Material.REDSTONE_BLOCK);
                        machineOwnedRSBlocks.add(out.getLocation().toBlockLocation());
                    }
                    if (!state && out.getType() == Material.REDSTONE_BLOCK) {
                        out.setType(Material.AIR);
                        machineOwnedRSBlocks.remove(out.getLocation().toBlockLocation());
                    }
                    timerCount.put(key, period);
                } else {
                    timerCount.put(key, count);
                }
            }
        }, 1L, 1L);
    }

    // ==========================================================================
    // BLOCK_PLACER / BLOCK_BREAKER タスク
    // ==========================================================================

    private void startPlacerBreakerTask() {
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            for (Map.Entry<Location, MachineType> e : new HashMap<>(machineManager.getAll()).entrySet()) {
                MachineType type = e.getValue();
                if (type != MachineType.BLOCK_PLACER && type != MachineType.BLOCK_BREAKER
                        && type != MachineType.IGNITER && type != MachineType.MINER
                        && type != MachineType.RIGHT_CLICKER) continue;

                Location loc = e.getKey();
                String key   = locKey(loc);

                boolean powered = loc.getBlock().isBlockPowered()
                        || loc.getBlock().isBlockIndirectlyPowered();
                boolean prev = prevPowered.getOrDefault(key, false);
                prevPowered.put(key, powered);
                if (!powered || prev) continue;
                if (!energyManager.tryConsume(loc, type)) continue;

                BlockFace override = getControlOverride(key);
                BlockFace facing = override != null ? override : getMachineFacing(loc);
                switch (type) {
                    case BLOCK_PLACER -> tryPlaceBlock(loc, facing);
                    case BLOCK_BREAKER -> tryBreakBlock(loc, facing);
                    case IGNITER -> tryIgnite(loc, facing);
                    case MINER -> tryMineBlock(loc, facing);
                    case RIGHT_CLICKER -> tryRightClick(loc, facing);
                    default -> {}
                }
            }
        }, 1L, 1L);
    }

    // ---- ブロック設置 --------------------------------------------------------

    /**
     * BLOCK_PLACER: バレルの実インベントリからブロックを取り出して前面に設置。
     * ホッパーからの搬入が自然に動作する。
     */
    private void tryPlaceBlock(Location machineLoc, BlockFace facing) {
        // バレルの実インベントリを直接使用
        if (!(machineLoc.getBlock().getState() instanceof Barrel barrel)) return;
        Inventory inv = barrel.getInventory();

        Block target  = machineLoc.getBlock().getRelative(facing);
        Block below   = target.getRelative(BlockFace.DOWN);
        Material targetM = target.getType();

        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack item = inv.getItem(i);
            if (item == null || item.getType() == Material.AIR) continue;
            Material mat = item.getType();

            // 水バケツ
            if (mat == Material.WATER_BUCKET && isReplaceable(targetM)) {
                target.setType(Material.WATER);
                inv.setItem(i, new ItemStack(Material.BUCKET));
                playPlaceEffect(target, Material.WATER);
                break;
            }

            // 溶岩バケツ
            if (mat == Material.LAVA_BUCKET && isReplaceable(targetM)) {
                target.setType(Material.LAVA);
                inv.setItem(i, new ItemStack(Material.BUCKET));
                playPlaceEffect(target, Material.LAVA);
                break;
            }

            // 種・農作物
            if (SEED_TO_CROP.containsKey(mat)) {
                Material cropBlock  = SEED_TO_CROP.get(mat);
                Material soilNeeded = SEED_SOIL.getOrDefault(mat, Material.FARMLAND);
                boolean canPlant = below.getType() == soilNeeded
                        || (mat == Material.SWEET_BERRIES
                                && (below.getType() == Material.GRASS_BLOCK
                                        || below.getType() == Material.DIRT
                                        || below.getType() == Material.PODZOL
                                        || below.getType() == Material.COARSE_DIRT));
                if (canPlant && isReplaceable(targetM)) {
                    target.setType(cropBlock);
                    consumeInventorySlot(inv, i, 1);
                    playPlaceEffect(target, cropBlock);
                    break;
                }
                continue;
            }

            // 苗木
            if (isSapling(mat) && isReplaceable(targetM)
                    && (below.getType() == Material.GRASS_BLOCK
                            || below.getType() == Material.DIRT
                            || below.getType() == Material.PODZOL
                            || below.getType() == Material.COARSE_DIRT
                            || below.getType() == Material.ROOTED_DIRT)) {
                target.setType(mat);
                consumeInventorySlot(inv, i, 1);
                playPlaceEffect(target, mat);
                break;
            }

            // 通常ブロック
            if (mat.isBlock() && isReplaceable(targetM)) {
                target.setType(mat);
                applyFacing(target, facing);
                target.getState().update(true, false);
                consumeInventorySlot(inv, i, 1);
                playPlaceEffect(target, mat);
                break;
            }
        }
    }

    private void playPlaceEffect(Block block, Material mat) {
        try {
            Sound sound = mat.createBlockData().getSoundGroup().getPlaceSound();
            block.getWorld().playSound(block.getLocation(), sound, 1.0f, 1.0f);
        } catch (Exception ignored) {}
        block.getWorld().spawnParticle(Particle.BLOCK_CRACK, block.getLocation().add(0.5, 0.5, 0.5),
                10, 0.3, 0.3, 0.3, 0.05, block.getBlockData());
    }

    // ---- ブロック破壊 --------------------------------------------------------

    private void tryBreakBlock(Location machineLoc, BlockFace facing) {
        Block target = machineLoc.getBlock().getRelative(facing);
        if (machineOwnedRSBlocks.contains(target.getLocation().toBlockLocation())) return;
        Material mat = target.getType();
        if (mat == Material.AIR || mat == Material.CAVE_AIR || mat == Material.VOID_AIR) return;
        if (mat.getHardness() < 0) return;

        target.getWorld().spawnParticle(Particle.BLOCK_CRACK,
                target.getLocation().add(0.5, 0.5, 0.5),
                25, 0.4, 0.4, 0.4, 0.1, target.getBlockData());
        target.breakNaturally(DIAMOND_PICK);
    }

    // ---- 着火 ----------------------------------------------------------------

    private void tryIgnite(Location machineLoc, BlockFace facing) {
        Block target = machineLoc.getBlock().getRelative(facing);
        Material mat = target.getType();

        // TNT点火
        if (mat == Material.TNT) {
            target.setType(Material.AIR);
            target.getWorld().spawn(
                    target.getLocation().add(0.5, 0, 0.5),
                    org.bukkit.entity.TNTPrimed.class);
            return;
        }

        // Lightable (キャンプファイア・ろうそく)
        if (target.getBlockData() instanceof org.bukkit.block.data.Lightable lightable) {
            if (!lightable.isLit()) {
                lightable.setLit(true);
                target.setBlockData(lightable);
            }
            return;
        }

        // 空気など置き換え可能 → 火を置く
        if (isReplaceable(mat)) {
            target.setType(Material.FIRE);
            return;
        }

        // 上面が空気なら着火
        Block above = target.getRelative(BlockFace.UP);
        if (above.getType() == Material.AIR) {
            above.setType(Material.FIRE);
        }
    }

    // ---- 右クリック代行 (RIGHT_CLICKER) ----------------------------------------

    /**
     * ディスペンサーの実インベントリから先頭アイテムを取り出し、前面ブロックへ右クリック動作を適用する。
     * ホッパーからのアイテム補充はディスペンサーのネイティブ機能でそのまま動作する。
     */
    private void tryRightClick(Location machineLoc, BlockFace facing) {
        if (!(machineLoc.getBlock().getState() instanceof org.bukkit.block.Dispenser disp)) return;
        Inventory inv = disp.getInventory();
        Block target = machineLoc.getBlock().getRelative(facing);

        // 先にターゲットブロック自体の右クリック操作を試す (レッドストーン系)
        if (interactTargetBlock(target)) return;

        // アイテムを使った右クリック操作
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack item = inv.getItem(i);
            if (item == null || item.getType().isAir()) continue;
            if (applyRightClickItem(item, target, i, inv)) break;
        }
    }

    private boolean applyRightClickItem(ItemStack item, Block target, int slot, Inventory inv) {
        Material mat = item.getType();

        // 骨粉 → 作物・芝生等に適用
        if (mat == Material.BONE_MEAL) {
            if (target.applyBoneMeal(BlockFace.UP)) {
                consumeInventorySlot(inv, slot, 1);
                return true;
            }
            return false;
        }

        // 水バケツ → 水設置
        if (mat == Material.WATER_BUCKET && isReplaceable(target.getType())) {
            target.setType(Material.WATER);
            inv.setItem(slot, new ItemStack(Material.BUCKET));
            return true;
        }

        // 溶岩バケツ → 溶岩設置
        if (mat == Material.LAVA_BUCKET && isReplaceable(target.getType())) {
            target.setType(Material.LAVA);
            inv.setItem(slot, new ItemStack(Material.BUCKET));
            return true;
        }

        // 空バケツ → 水・溶岩を回収
        if (mat == Material.BUCKET) {
            if (target.getType() == Material.WATER) {
                target.setType(Material.AIR);
                inv.setItem(slot, new ItemStack(Material.WATER_BUCKET));
                return true;
            }
            if (target.getType() == Material.LAVA) {
                target.setType(Material.AIR);
                inv.setItem(slot, new ItemStack(Material.LAVA_BUCKET));
                return true;
            }
            return false;
        }

        // 火打石と打ち金 → 着火
        if (mat == Material.FLINT_AND_STEEL) {
            if (target.getType() == Material.TNT) {
                target.setType(Material.AIR);
                target.getWorld().spawn(target.getLocation().add(0.5, 0, 0.5), org.bukkit.entity.TNTPrimed.class);
            } else if (isReplaceable(target.getType())) {
                target.setType(Material.FIRE);
            } else {
                Block abv = target.getRelative(BlockFace.UP);
                if (abv.getType() == Material.AIR) abv.setType(Material.FIRE);
            }
            return true;
        }

        // Lightable (キャンプファイア・ろうそく) → 点火
        if (target.getBlockData() instanceof org.bukkit.block.data.Lightable lightable && !lightable.isLit()) {
            if (mat == Material.FLINT_AND_STEEL || mat == Material.FIRE_CHARGE) {
                lightable.setLit(true);
                target.setBlockData(lightable);
                consumeInventorySlot(inv, slot, 1);
                return true;
            }
        }

        // 種・農作物 → BLOCK_PLACERと同じ植え付けロジックを流用
        if (SEED_TO_CROP.containsKey(mat)) {
            Material cropBlock  = SEED_TO_CROP.get(mat);
            Material soilNeeded = SEED_SOIL.getOrDefault(mat, Material.FARMLAND);
            Block below = target.getRelative(BlockFace.DOWN);
            boolean canPlant = below.getType() == soilNeeded
                    || (mat == Material.SWEET_BERRIES
                            && (below.getType() == Material.GRASS_BLOCK
                                    || below.getType() == Material.DIRT
                                    || below.getType() == Material.PODZOL
                                    || below.getType() == Material.COARSE_DIRT));
            if (canPlant && isReplaceable(target.getType())) {
                target.setType(cropBlock);
                consumeInventorySlot(inv, slot, 1);
                return true;
            }
        }

        return false;
    }

    /** ターゲットブロックが右クリック対象なら処理する (レッドストーン系 + CS装置) */
    private boolean interactTargetBlock(Block target) {
        if (machineManager.isMachine(target.getLocation())) return false;

        Material mt = target.getType();
        BlockData data = target.getBlockData();

        switch (mt) {
            case REPEATER -> {
                if (data instanceof org.bukkit.block.data.type.Repeater rp) {
                    rp.setDelay(Math.max(1, (rp.getDelay() + 1) % 5));
                    target.setBlockData(rp);
                    return true;
                }
            }
            case COMPARATOR -> {
                if (data instanceof org.bukkit.block.data.type.Comparator cmp) {
                    cmp.setMode(cmp.getMode() == org.bukkit.block.data.type.Comparator.Mode.COMPARE
                            ? org.bukkit.block.data.type.Comparator.Mode.SUBTRACT
                            : org.bukkit.block.data.type.Comparator.Mode.COMPARE);
                    target.setBlockData(cmp);
                    return true;
                }
            }
            case DAYLIGHT_DETECTOR -> {
                if (data instanceof org.bukkit.block.data.type.DaylightDetector dd) {
                    dd.setInverted(!dd.isInverted());
                    target.setBlockData(dd);
                    return true;
                }
            }
            case NOTE_BLOCK -> {
                if (data instanceof org.bukkit.block.data.type.NoteBlock nb) {
                    int next = (nb.getNote().getId() + 1) % 25;
                    nb.setNote(new org.bukkit.Note(next));
                    target.setBlockData(nb);
                    return true;
                }
            }
            case LEVER -> {
                if (data instanceof org.bukkit.block.data.type.Switch sw) {
                    boolean wasOn = sw.isPowered();
                    sw.setPowered(!wasOn);
                    target.setBlockData(sw);
                    // 自己トリガー防止: ONにしたら遅延でOFFに戻す (立ち上がりエッジ再検出のため)
                    if (!wasOn) {
                        Location tloc = target.getLocation().clone();
                        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                            BlockData cur = tloc.getBlock().getBlockData();
                            if (cur instanceof org.bukkit.block.data.type.Switch cs) {
                                cs.setPowered(false);
                                tloc.getBlock().setBlockData(cs);
                            }
                        }, 4L);
                    }
                    return true;
                }
            }
            case OAK_BUTTON, SPRUCE_BUTTON, BIRCH_BUTTON, JUNGLE_BUTTON,
                 ACACIA_BUTTON, DARK_OAK_BUTTON, MANGROVE_BUTTON,
                 CRIMSON_BUTTON, WARPED_BUTTON, STONE_BUTTON,
                 POLISHED_BLACKSTONE_BUTTON -> {
                // ボタン: 押す(一瞬ONにしてバニラ任せで戻る)
                if (data instanceof org.bukkit.block.data.type.Switch sw && !sw.isPowered()) {
                    sw.setPowered(true);
                    target.setBlockData(sw);
                    return true;
                }
            }
            default -> {}
        }
        return false;
    }

    // ---- エンティティ検知 (ENTITY_DETECTOR) -----------------------------------

    private void startEntityDetectorTask() {
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            for (Location loc : new ArrayList<>(machineManager.getByType(MachineType.ENTITY_DETECTOR))) {

                boolean detected = !loc.getWorld().getNearbyEntities(loc, 8, 8, 8,
                        entity -> entity instanceof org.bukkit.entity.LivingEntity).isEmpty();

                Block south = loc.getBlock().getRelative(BlockFace.SOUTH);
                if (detected) {
                    if (south.getType() == Material.AIR) {
                        south.setType(Material.REDSTONE_BLOCK);
                        machineOwnedRSBlocks.add(south.getLocation().toBlockLocation());
                    }
                } else {
                    if (south.getType() == Material.REDSTONE_BLOCK) {
                        south.setType(Material.AIR);
                        machineOwnedRSBlocks.remove(south.getLocation().toBlockLocation());
                    }
                }
            }
        }, 5L, 20L);
    }

    // BlockDispenseEventをキャンセル (RIGHT_CLICKERのディスペンサー誤作動防止)
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockDispense(org.bukkit.event.block.BlockDispenseEvent event) {
        if (machineManager.isMachine(event.getBlock().getLocation())
                && machineManager.getType(event.getBlock().getLocation()) == MachineType.RIGHT_CLICKER) {
            event.setCancelled(true);
        }
    }

    // ---- ブロック採掘 (MINER) -----------------------------------------------

    private void tryMineBlock(Location machineLoc, BlockFace facing) {
        Block target = machineLoc.getBlock().getRelative(facing);
        if (machineOwnedRSBlocks.contains(target.getLocation().toBlockLocation())) return;
        Material mat = target.getType();
        if (mat == Material.AIR || mat == Material.CAVE_AIR || mat == Material.VOID_AIR) return;
        if (mat.getHardness() < 0) return;

        ItemStack[] contents = machineManager.getStoredContents(machineLoc);
        if (contents == null) return;

        // ドロップを取得して内部インベントリに格納
        Collection<ItemStack> drops = target.getDrops(DIAMOND_PICK);
        // 空き容量チェック
        Inventory tempInv = plugin.getServer().createInventory(null, 27);
        for (int i = 0; i < contents.length; i++) {
            if (contents[i] != null) tempInv.setItem(i, contents[i].clone());
        }
        for (ItemStack drop : drops) {
            if (!tempInv.addItem(drop.clone()).isEmpty()) return; // スペース不足
        }

        // パーティクルエフェクト (破壊前に取得)
        BlockData bdata = target.getBlockData();
        target.setType(Material.AIR);
        target.getWorld().spawnParticle(Particle.BLOCK_CRACK,
                machineLoc.getBlock().getRelative(facing).getLocation().add(0.5, 0.5, 0.5),
                25, 0.4, 0.4, 0.4, 0.1, bdata);

        // 内部インベントリ更新
        for (int i = 0; i < 27; i++) contents[i] = tempInv.getItem(i);
        machineManager.setStoredContents(machineLoc, contents);
    }

    // ---- 自動精錬 (AUTO_SMELTER) --------------------------------------------

    private void startAutoSmelterTask() {
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            for (Location loc : new ArrayList<>(machineManager.getByType(MachineType.AUTO_SMELTER))) {
                if (!loc.getChunk().isLoaded()) continue;
                ItemStack[] contents = machineManager.getStoredContents(loc);
                if (contents == null) continue;

                ItemStack input = contents[0];
                if (input == null || input.getType().isAir()) continue;

                // Chain 1: 粉砕鉱石→精錬インゴット
                ItemStack result = null;
                String matId = IntermediateMaterials.getId(input);
                if (matId != null) {
                    result = switch (matId) {
                        case IntermediateMaterials.CRUSHED_IRON -> IntermediateMaterials.build(IntermediateMaterials.REFINED_IRON_INGOT);
                        case IntermediateMaterials.CRUSHED_GOLD -> IntermediateMaterials.build(IntermediateMaterials.REFINED_GOLD_INGOT);
                        default -> null;
                    };
                }
                if (result == null) {
                    // バニラ精錬レシピ
                    result = getFurnaceResult(input.getType());
                }
                if (result == null) continue;

                ItemStack output = contents[26];
                if (output != null && !output.getType().isAir()) {
                    if (!output.isSimilar(result)) continue;
                    if (output.getAmount() >= output.getMaxStackSize()) continue;
                }

                // 1個精錬
                if (input.getAmount() <= 1) contents[0] = null;
                else input.setAmount(input.getAmount() - 1);

                if (output == null || output.getType().isAir()) {
                    contents[26] = result.clone();
                } else {
                    output.setAmount(output.getAmount() + result.getAmount());
                }
                machineManager.setStoredContents(loc, contents);
            }
        }, 40L, 40L); // 2秒ごと
    }

    private ItemStack getFurnaceResult(Material mat) {
        ItemStack dummy = new ItemStack(mat);
        java.util.Iterator<Recipe> it = plugin.getServer().recipeIterator();
        while (it.hasNext()) {
            Recipe recipe = it.next();
            if (recipe instanceof FurnaceRecipe fr && fr.getInputChoice().test(dummy)) {
                return fr.getResult().clone();
            }
        }
        return null;
    }

    // ---- バキュームホッパー (VACUUM_HOPPER) ----------------------------------

    private void startVacuumHopperTask() {
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            for (Location loc : new ArrayList<>(machineManager.getByType(MachineType.VACUUM_HOPPER))) {
                // RS通電でのみ動作
                if (!loc.getBlock().isBlockPowered() && !loc.getBlock().isBlockIndirectlyPowered()) continue;
                if (!energyManager.tryConsume(loc, MachineType.VACUUM_HOPPER)) continue;
                ItemStack[] contents = machineManager.getStoredContents(loc);
                if (contents == null) continue;

                // 隣接するバキュームフィルターを検索
                Set<Material> filter = null;
                outer:
                for (BlockFace face : new BlockFace[]{
                        BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST,
                        BlockFace.WEST, BlockFace.UP, BlockFace.DOWN}) {
                    Location adjLoc = loc.getBlock().getRelative(face).getLocation();
                    if (!machineManager.isMachine(adjLoc)) continue;
                    if (machineManager.getType(adjLoc) != MachineType.VACUUM_HOPPER_CTRL) continue;
                    ItemStack[] ctrl = machineManager.getStoredContents(adjLoc);
                    if (ctrl == null) break;
                    Set<Material> f = new HashSet<>();
                    for (ItemStack fi : ctrl) {
                        if (fi != null && !fi.getType().isAir()) f.add(fi.getType());
                    }
                    filter = f;
                    break outer;
                }

                for (org.bukkit.entity.Entity entity : loc.getWorld().getNearbyEntities(loc, 4, 4, 4)) {
                    if (!(entity instanceof org.bukkit.entity.Item itemEntity)) continue;
                    if (itemEntity.getTicksLived() < 20) continue; // 吸引遅延: 1秒後から収集
                    ItemStack drop = itemEntity.getItemStack().clone();
                    // フィルターが設定されている場合は対象外アイテムをスキップ
                    if (filter != null && !filter.isEmpty() && !filter.contains(drop.getType())) continue;
                    int remaining = drop.getAmount();

                    for (int i = 0; i < 27 && remaining > 0; i++) {
                        if (contents[i] == null || contents[i].getType().isAir()) {
                            ItemStack take = drop.clone();
                            take.setAmount(remaining);
                            contents[i] = take;
                            remaining = 0;
                        } else if (contents[i].isSimilar(drop)
                                && contents[i].getAmount() < contents[i].getMaxStackSize()) {
                            int space = contents[i].getMaxStackSize() - contents[i].getAmount();
                            int take = Math.min(space, remaining);
                            contents[i].setAmount(contents[i].getAmount() + take);
                            remaining -= take;
                        }
                    }

                    if (remaining <= 0) {
                        itemEntity.remove();
                    } else if (remaining < drop.getAmount()) {
                        drop.setAmount(remaining);
                        itemEntity.setItemStack(drop);
                    }
                }

                machineManager.setStoredContents(loc, contents);

            }
        }, 10L, 10L); // 0.5秒ごと
    }

    // ---- 経験値変換炉 (XP_CONVERTER) -----------------------------------------

    private void startXpConverterTask() {
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            for (Location loc : new ArrayList<>(machineManager.getByType(MachineType.XP_CONVERTER))) {
                if (!loc.getChunk().isLoaded()) continue;
                // SCULK_CATALYSTはRS検出に隣接ブロックを確認
                boolean powered = loc.getBlock().isBlockPowered() || loc.getBlock().isBlockIndirectlyPowered();
                if (!powered) {
                    for (BlockFace face : ADJACENT_FACES) {
                        if (loc.getBlock().getRelative(face).isBlockPowered()
                                || loc.getBlock().getRelative(face).isBlockIndirectlyPowered()) {
                            powered = true; break;
                        }
                    }
                }
                if (!powered) continue;
                if (!energyManager.tryConsume(loc, MachineType.XP_CONVERTER)) continue;
                ItemStack[] contents = machineManager.getStoredContents(loc);
                if (contents == null) continue;
                for (int i = 0; i < contents.length; i++) {
                    if (contents[i] == null || contents[i].getType().isAir()) continue;
                    Integer xp = XP_VALUES.get(contents[i].getType());
                    if (xp == null) continue;
                    // エンチャントボトル出力 (1ボトル≒7XP)
                    int bottles = Math.max(1, xp / 7);
                    // インベントリに空きがあるか確認
                    int remaining = bottles;
                    for (int j = 0; j < contents.length && remaining > 0; j++) {
                        if (contents[j] == null || contents[j].getType().isAir()) {
                            contents[j] = new ItemStack(Material.EXPERIENCE_BOTTLE,
                                    Math.min(remaining, 64));
                            remaining -= Math.min(remaining, 64);
                        } else if (contents[j].getType() == Material.EXPERIENCE_BOTTLE
                                && contents[j].getAmount() < 64) {
                            int space = 64 - contents[j].getAmount();
                            int add = Math.min(space, remaining);
                            contents[j].setAmount(contents[j].getAmount() + add);
                            remaining -= add;
                        }
                    }
                    if (remaining < bottles) {
                        // 少なくとも1本は入った → 素材を消費して保存
                        consumeContentsSlot(contents, i, 1);
                        machineManager.setStoredContents(loc, contents);
                    }
                    break;
                }
            }
        }, 40L, 40L);
    }

    // ---- 自動釣り機 (AUTO_FISHER) --------------------------------------------

    // 自動釣り機の釣果テーブル (バニラ準拠)
    private static final Material[] FISH_TABLE = {
        Material.COD, Material.COD, Material.COD, Material.COD,
        Material.SALMON, Material.SALMON, Material.TROPICAL_FISH, Material.PUFFERFISH
    };
    private static final Material[] TREASURE_TABLE = {
        Material.ENCHANTED_BOOK, Material.BOW, Material.FISHING_ROD,
        Material.NAME_TAG, Material.SADDLE, Material.NAUTILUS_SHELL
    };
    private static final Material[] JUNK_TABLE = {
        Material.LILY_PAD, Material.BOWL, Material.LEATHER, Material.LEATHER_BOOTS,
        Material.ROTTEN_FLESH, Material.BONE, Material.STRING, Material.STICK
    };

    private void startAutoFisherTask() {
        java.util.Random rand = new java.util.Random();
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            for (Location loc : new ArrayList<>(machineManager.getByType(MachineType.AUTO_FISHER))) {
                if (!loc.getChunk().isLoaded()) continue;
                if (!loc.getBlock().isBlockPowered() && !loc.getBlock().isBlockIndirectlyPowered()) continue;
                if (!energyManager.tryConsume(loc, MachineType.AUTO_FISHER)) continue;
                ItemStack[] contents = machineManager.getStoredContents(loc);
                if (contents == null) continue;

                // 半径2ブロック以内に水ブロックが必要
                boolean hasWater = false;
                outer:
                for (int dx = -2; dx <= 2; dx++)
                    for (int dy = -1; dy <= 1; dy++)
                        for (int dz = -2; dz <= 2; dz++)
                            if (loc.getWorld().getBlockAt(
                                    loc.getBlockX() + dx, loc.getBlockY() + dy, loc.getBlockZ() + dz)
                                    .getType() == Material.WATER) { hasWater = true; break outer; }
                if (!hasWater) continue;

                // 釣り竿の耐久消費とLuck of the Sea確認 (スロット0)
                boolean dirty = false;
                int luckLevel = 0;
                if (contents[0] != null && contents[0].getType() == Material.FISHING_ROD) {
                    luckLevel = contents[0].getEnchantmentLevel(
                            org.bukkit.enchantments.Enchantment.getByKey(
                                    org.bukkit.NamespacedKey.minecraft("luck_of_the_sea")));
                    ItemMeta m = contents[0].getItemMeta();
                    if (m instanceof org.bukkit.inventory.meta.Damageable dm) {
                        int dmg = dm.getDamage() + 1;
                        if (dmg >= contents[0].getType().getMaxDurability()) {
                            contents[0] = null;
                        } else {
                            dm.setDamage(dmg);
                            contents[0].setItemMeta(m);
                        }
                        dirty = true;
                    }
                }

                // 釣果をランダム生成 (バニラ準拠: 魚85% / ゴミ10% / 宝5%)
                int luckBonus = Math.min(luckLevel * 2, 10);
                int treasureChance = 5 + luckBonus;
                int junkChance = Math.max(0, 10 - luckBonus);
                int roll = rand.nextInt(100);
                Material[] pool;
                if (roll < treasureChance)                    pool = TREASURE_TABLE;
                else if (roll < treasureChance + junkChance)  pool = JUNK_TABLE;
                else                                           pool = FISH_TABLE;

                ItemStack catchItem = buildFishingTreasure(pool[rand.nextInt(pool.length)]);
                if (addItemToFisher(contents, catchItem, FISHER_OUTPUT_SLOTS) > 0) dirty = true;
                if (dirty) machineManager.setStoredContents(loc, contents);
            }
        }, 100L, 100L);
    }

    /** 釣り宝アイテムにエンチャントを付与する */
    private ItemStack buildFishingTreasure(Material mat) {
        ItemStack item = new ItemStack(mat);
        if (mat == Material.ENCHANTED_BOOK) {
            var enchants = getRandomEnchants(Material.ENCHANTED_BOOK);
            if (!enchants.isEmpty() && item.getItemMeta() instanceof org.bukkit.inventory.meta.EnchantmentStorageMeta esm) {
                for (var entry : enchants.entrySet())
                    esm.addStoredEnchant(entry.getKey(), entry.getValue(), true);
                item.setItemMeta(esm);
            }
        } else if (mat == Material.BOW || mat == Material.FISHING_ROD) {
            ItemMeta im = item.getItemMeta();
            var enchants = getRandomEnchants(mat);
            if (!enchants.isEmpty()) {
                for (var entry : enchants.entrySet())
                    im.addEnchant(entry.getKey(), entry.getValue(), true);
                item.setItemMeta(im);
            }
        }
        return item;
    }

    /** AUTO_FISHERの出力スロット一覧 (非装飾スロット) */
    private static final int[] FISHER_OUTPUT_SLOTS = {8, 10, 11, 12, 13, 14, 15, 16, 26};

    /** 指定スロットのみにアイテムを追加 (装飾スロットへの混入防止) */
    private int addItemToFisher(ItemStack[] arr, ItemStack item, int[] slots) {
        int remaining = item.getAmount();
        for (int i : slots) {
            if (arr[i] != null && !arr[i].getType().isAir()
                    && arr[i].isSimilar(item) && arr[i].getAmount() < arr[i].getMaxStackSize()) {
                int take = Math.min(remaining, arr[i].getMaxStackSize() - arr[i].getAmount());
                arr[i].setAmount(arr[i].getAmount() + take);
                remaining -= take;
                if (remaining <= 0) return item.getAmount();
            }
        }
        for (int i : slots) {
            if (arr[i] == null || arr[i].getType().isAir()) {
                arr[i] = item.clone();
                arr[i].setAmount(remaining);
                return item.getAmount();
            }
        }
        return item.getAmount() - remaining;
    }

    // ---- 高速ホッパー (FAST_HOPPER) ------------------------------------------

    private void startFastHopperTask() {
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            for (MachineType mtype : new MachineType[]{MachineType.FAST_HOPPER, MachineType.VERT_FAST_HOPPER}) {
                for (Location loc : new ArrayList<>(machineManager.getByType(mtype))) {
                    if (!loc.getChunk().isLoaded()) continue;
                    if (loc.getBlock().isBlockPowered() || loc.getBlock().isBlockIndirectlyPowered()) continue;
                    if (!energyManager.tryConsume(loc, mtype)) continue;
                    ItemStack[] buf = machineManager.getStoredContents(loc);
                    if (buf == null) continue;
                    boolean dirty = false;
                    final BlockFace pullFace, pushFace;
                    if (mtype == MachineType.VERT_FAST_HOPPER) {
                        // 縦型: 上から吸引 → 下へ排出
                        pullFace = BlockFace.UP;
                        pushFace = BlockFace.DOWN;
                    } else {
                        // 横型: 前面へ排出 (LECTERN の向き)、背面から吸引
                        pushFace = getMachineFacing(loc);
                        pullFace = pushFace.getOppositeFace();
                    }
                    dirty |= hopperPullFrom(loc.getBlock().getRelative(pullFace), buf);
                    dirty |= hopperPushTo(loc.getBlock().getRelative(pushFace), buf);
                    if (dirty) machineManager.setStoredContents(loc, buf);
                }
            }
        }, 2L, 2L);
    }

    private boolean hopperPullFrom(Block src, ItemStack[] buf) {
        Location srcLoc = src.getLocation().toBlockLocation();
        if (src.getState() instanceof org.bukkit.block.Container c) {
            Inventory inv = c.getInventory();
            for (int i = 0; i < inv.getSize(); i++) {
                ItemStack is = inv.getItem(i);
                if (is == null || is.getType().isAir()) continue;
                int moved = addItemToArray(buf, is);
                if (moved > 0) {
                    is.setAmount(is.getAmount() - moved);
                    inv.setItem(i, is.getAmount() <= 0 ? null : is);
                    return true;
                }
            }
        } else if (machineManager.isMachine(srcLoc)
                && MachineManager.hasMachineInventory(machineManager.getType(srcLoc))) {
            ItemStack[] srcArr = machineManager.getStoredContents(srcLoc);
            if (srcArr == null) return false;
            for (int i = 0; i < srcArr.length; i++) {
                if (srcArr[i] == null || srcArr[i].getType().isAir()) continue;
                int moved = addItemToArray(buf, srcArr[i]);
                if (moved > 0) {
                    srcArr[i].setAmount(srcArr[i].getAmount() - moved);
                    if (srcArr[i].getAmount() <= 0) srcArr[i] = null;
                    machineManager.setStoredContents(srcLoc, srcArr);
                    return true;
                }
            }
        }
        return false;
    }

    private boolean hopperPushTo(Block dst, ItemStack[] buf) {
        Location dstLoc = dst.getLocation().toBlockLocation();
        for (int i = 0; i < buf.length; i++) {
            if (buf[i] == null || buf[i].getType().isAir()) continue;
            ItemStack one = buf[i].clone(); one.setAmount(1);
            if (dst.getState() instanceof org.bukkit.block.Container c) {
                var leftover = c.getInventory().addItem(one);
                if (leftover.isEmpty()) {
                    buf[i].setAmount(buf[i].getAmount() - 1);
                    if (buf[i].getAmount() <= 0) buf[i] = null;
                    return true;
                }
            } else if (machineManager.isMachine(dstLoc)
                    && MachineManager.hasMachineInventory(machineManager.getType(dstLoc))) {
                ItemStack[] dstArr = machineManager.getStoredContents(dstLoc);
                if (dstArr != null && addItemToArray(dstArr, one) > 0) {
                    machineManager.setStoredContents(dstLoc, dstArr);
                    buf[i].setAmount(buf[i].getAmount() - 1);
                    if (buf[i].getAmount() <= 0) buf[i] = null;
                    return true;
                }
            }
            break;
        }
        return false;
    }

    // ---- 高速排出装置 (BULK_DROPPER) -----------------------------------------

    private void startBulkDropperTask() {
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            for (Location loc : new ArrayList<>(machineManager.getByType(MachineType.BULK_DROPPER))) {
                if (!loc.getChunk().isLoaded()) continue;
                if (!loc.getBlock().isBlockPowered() && !loc.getBlock().isBlockIndirectlyPowered()) continue;
                if (!energyManager.tryConsume(loc, MachineType.BULK_DROPPER)) continue;
                ItemStack[] contents = machineManager.getStoredContents(loc);
                if (contents == null) continue;
                Block below = loc.getBlock().getRelative(BlockFace.DOWN);
                Location belowLoc = below.getLocation().toBlockLocation();
                boolean dirty = false;
                int pushed = 0;
                for (int i = 0; i < contents.length && pushed < 4; i++) {
                    if (contents[i] == null || contents[i].getType().isAir()) continue;
                    boolean sent = false;
                    if (below.getState() instanceof org.bukkit.block.Container c) {
                        var leftover = c.getInventory().addItem(contents[i].clone());
                        int moved = contents[i].getAmount()
                                - (leftover.isEmpty() ? 0 : leftover.get(0).getAmount());
                        if (moved > 0) {
                            contents[i].setAmount(contents[i].getAmount() - moved);
                            if (contents[i].getAmount() <= 0) contents[i] = null;
                            dirty = true; pushed++; sent = true;
                        }
                    } else if (machineManager.isMachine(belowLoc)
                            && MachineManager.hasMachineInventory(machineManager.getType(belowLoc))) {
                        ItemStack[] dstArr = machineManager.getStoredContents(belowLoc);
                        if (dstArr != null) {
                            int moved = addItemToArray(dstArr, contents[i]);
                            if (moved > 0) {
                                machineManager.setStoredContents(belowLoc, dstArr);
                                contents[i].setAmount(contents[i].getAmount() - moved);
                                if (contents[i].getAmount() <= 0) contents[i] = null;
                                dirty = true; pushed++; sent = true;
                            }
                        }
                    }
                    if (!sent) {
                        loc.getWorld().dropItem(below.getLocation().clone().add(0.5, 0.5, 0.5), contents[i].clone());
                        contents[i] = null;
                        dirty = true; pushed++;
                    }
                }
                if (dirty) machineManager.setStoredContents(loc, contents);
            }
        }, 2L, 2L);
    }

    // ---- 日照発電機 (SOLAR_PANEL) ----------------------------------------------

    /**
     * SOLAR_PANEL: 昼間(時刻0-12000)かつ天空が見えるとき、南面にRS信号を出力する。
     * パッシブマシン: インベントリ不要。
     */
    private void startSolarPanelTask() {
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            for (Location loc : new ArrayList<>(machineManager.getByType(MachineType.SOLAR_PANEL))) {
                if (!loc.getChunk().isLoaded()) continue;
                long time = loc.getWorld().getTime();
                boolean day = time >= 0 && time < 12000;
                boolean sky = loc.getBlock().getLightFromSky() >= 15
                        || loc.getBlockY() >= loc.getWorld().getHighestBlockYAt(loc.getBlockX(), loc.getBlockZ());
                boolean shouldEmit = day && sky;
                Location blockKey = loc.toBlockLocation();
                Location rsBlockLoc = loc.getBlock().getRelative(BlockFace.SOUTH).getLocation().toBlockLocation();
                boolean currentlyEmitting = machineOwnedRSBlocks.contains(rsBlockLoc);
                if (shouldEmit) {
                    if (!currentlyEmitting) {
                        Block south = loc.getBlock().getRelative(BlockFace.SOUTH);
                        if (south.getType() == Material.AIR) {
                            south.setType(Material.REDSTONE_BLOCK);
                            machineOwnedRSBlocks.add(rsBlockLoc);
                        }
                    }
                    energyManager.generate(loc, 10);
                    energyManager.distributeFrom(loc);
                } else if (!shouldEmit && currentlyEmitting) {
                    Block south = loc.getBlock().getRelative(BlockFace.SOUTH);
                    if (south.getType() == Material.REDSTONE_BLOCK) {
                        south.setType(Material.AIR);
                        machineOwnedRSBlocks.remove(rsBlockLoc);
                    }
                }
            }
        }, 10L, 20L);
    }

    // ---- 自動羊毛刈り機 (AUTO_SHEARER) -----------------------------------------

    /**
     * AUTO_SHEARER: RS通電中、周囲3ブロック以内の羊を自動で刈り、羊毛を内部に収集する。
     * スロット0: ハサミ (耐久消費あり)
     * スロット1-26: 羊毛収納
     */
    private void startAutoShearerTask() {
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            for (Location loc : new ArrayList<>(machineManager.getByType(MachineType.AUTO_SHEARER))) {
                if (!loc.getChunk().isLoaded()) continue;
                if (!loc.getBlock().isBlockPowered() && !loc.getBlock().isBlockIndirectlyPowered()) continue;
                if (!energyManager.tryConsume(loc, MachineType.AUTO_SHEARER)) continue;
                ItemStack[] contents = machineManager.getStoredContents(loc);
                if (contents == null) continue;
                ItemStack shears = contents[0];
                if (shears == null || shears.getType() != Material.SHEARS) continue;
                if (shears.getItemMeta() instanceof org.bukkit.inventory.meta.Damageable d && d.getDamage() >= Material.SHEARS.getMaxDurability()) {
                    contents[0] = null;
                    machineManager.setStoredContents(loc, contents);
                    continue;
                }
                boolean dirty = false;
                for (org.bukkit.entity.Entity e : loc.getWorld().getNearbyEntities(loc, 3, 3, 3,
                        entity -> entity instanceof org.bukkit.entity.Sheep s && !s.isSheared())) {
                    org.bukkit.entity.Sheep sheep = (org.bukkit.entity.Sheep) e;
                    Material woolMat;
                    try { woolMat = Material.valueOf(sheep.getColor().name() + "_WOOL"); }
                    catch (IllegalArgumentException ignored) { continue; }
                    ItemStack wool = new ItemStack(woolMat, 1);
                    int added = addItemToArray(contents, wool);
                    if (added > 0) {
                        sheep.setSheared(true);
                        if (shears.getItemMeta() instanceof org.bukkit.inventory.meta.Damageable dm) {
                            dm.setDamage(dm.getDamage() + 1);
                            shears.setItemMeta((ItemMeta) dm);
                        }
                        dirty = true;
                    }
                }
                if (dirty) machineManager.setStoredContents(loc, contents);
            }
        }, 10L, 10L);
    }

    // ==========================================================================
    // GUI 生成
    // ==========================================================================

    /** バキュームフィルター GUI: 全27スロットにフィルターアイテムを設置 */
    private Inventory createVacuumCtrlGui(Location loc) {
        Location key = loc.toBlockLocation();
        Inventory existing = openGuis.get(key);
        if (existing != null) return existing;
        ItemStack[] saved = machineManager.getStoredContents(loc);
        MachineInvHolder holder = new MachineInvHolder(loc, MachineType.VACUUM_HOPPER_CTRL);
        Inventory gui = plugin.getServer().createInventory(holder, 27,
                Component.text(GUI_PREFIX + "バキュームフィルター"));
        holder.setInventory(gui);
        decorateGuiFrame(gui, MachineType.VACUUM_HOPPER_CTRL);
        // 情報本
        if (isEmpty(gui.getItem(4))) {
            ItemStack vcBook = new ItemStack(Material.KNOWLEDGE_BOOK);
            ItemMeta vc = vcBook.getItemMeta();
            vc.displayName(Component.text("フィルター設定", NamedTextColor.GOLD));
            vc.lore(List.of(
                    Component.text("スロットに入れたアイテム種のみ吸引します。", NamedTextColor.GRAY),
                    Component.text("隣接するバキュームホッパーに設置して使用。", NamedTextColor.GRAY),
                    Component.empty(),
                    Component.text("■ 空欄: その種別は吸引しない", NamedTextColor.YELLOW),
                    Component.text("■ RS信号: 不要", NamedTextColor.YELLOW),
                    Component.text("■ EN消費: なし", NamedTextColor.YELLOW)
            ));
            vc.getPersistentDataContainer().set(
                    org.bukkit.NamespacedKey.fromString("circuitsurvival:decoration"),
                    org.bukkit.persistence.PersistentDataType.BOOLEAN, true);
            vcBook.setItemMeta(vc);
            gui.setItem(4, vcBook);
        }
        if (saved != null) {
            for (int i = 0; i < 27; i++) {
                if (saved[i] != null && !saved[i].getType().isAir()) gui.setItem(i, saved[i].clone());
            }
        }
        openGuis.put(key, gui);
        machineManager.registerLiveInventory(key, gui);
        return gui;
    }

    private Inventory createGui(MachineType type, String locKey, Location loc) {
        // 既に開いているGUIがあれば再利用
        Location blockKey = loc.toBlockLocation();
        Inventory existing = openGuis.get(blockKey);
        if (existing != null) return existing;

        Inventory inv = machineManager.createGui(loc, type, GUI_PREFIX + type.displayName);

        // インベントリを持つマシンはライブ同期に登録
        if (MachineManager.hasMachineInventory(type)) {
            openGuis.put(blockKey, inv);
            machineManager.registerLiveInventory(blockKey, inv);
        }

        ItemStack info = new ItemStack(Material.BOOK);
        ItemMeta im    = info.getItemMeta();
        im.displayName(Component.text(type.displayName, NamedTextColor.GOLD));
        List<Component> lore = new java.util.ArrayList<>();
        lore.add(Component.text(type.description, NamedTextColor.YELLOW));
        lore.add(Component.empty());
        if (type == MachineType.PIXEL_FORGE) {
            lore.add(Component.text("スロット0: ベース武器", NamedTextColor.GRAY));
            lore.add(Component.text("スロット1: 改造素材", NamedTextColor.GRAY));
            lore.add(Component.text("スロット26: 完成品", NamedTextColor.GRAY));
        } else {
            lore.add(Component.text("■ 入力: スロット0 (左端)", NamedTextColor.GRAY));
            lore.add(Component.text("■ 出力: スロット26 (右端)", NamedTextColor.GRAY));
            lore.add(Component.text("■ 中央スロット: バッファ/設定", NamedTextColor.GRAY));
        }
        lore.add(Component.empty());
        lore.add(Component.text("▶ 右上(8番)のボタンで実行", NamedTextColor.GREEN));
        // RS電源状態を表示
        boolean powered = loc.getBlock().isBlockPowered() || loc.getBlock().isBlockIndirectlyPowered();
        lore.add(Component.text(powered ? "⚡ RS: ON" : "⚡ RS: OFF",
                powered ? NamedTextColor.GREEN : NamedTextColor.RED));
        im.lore(lore);
        im.getPersistentDataContainer().set(
                org.bukkit.NamespacedKey.fromString("circuitsurvival:decoration"),
                org.bukkit.persistence.PersistentDataType.BOOLEAN, true);
        info.setItemMeta(im);
        inv.setItem(4, info);

        ItemStack btn = new ItemStack(Material.LIME_DYE);
        ItemMeta bm   = btn.getItemMeta();
        bm.displayName(Component.text("▶ 実行", NamedTextColor.GREEN));
        bm.lore(List.of(
                Component.text("左クリックでこのマシンを実行", NamedTextColor.GRAY)
        ));
        bm.getPersistentDataContainer().set(
                org.bukkit.NamespacedKey.fromString("circuitsurvival:decoration"),
                org.bukkit.persistence.PersistentDataType.BOOLEAN, true);
        btn.setItemMeta(bm);
        inv.setItem(8, btn);

        // 装飾フレーム (機械種別に応じて使用スロットを保護)
        decorateGuiFrame(inv, type);

        if (type == MachineType.TIMER) {
            int period = timerPeriods.getOrDefault(locKey, 20);
            ItemStack ti = new ItemStack(Material.CLOCK);
            ItemMeta tm  = ti.getItemMeta();
            tm.displayName(Component.text("周期: " + period + " tick (" + (period / 20.0) + "秒)", NamedTextColor.AQUA));
            tm.lore(List.of(
                    Component.text("左クリック: -1tick  右クリック: +1tick", NamedTextColor.GRAY),
                    Component.text("Shift+左: -20tick  Shift+右: +20tick", NamedTextColor.GRAY)
            ));
            ti.setItemMeta(tm);
            inv.setItem(13, ti);
        }

        return inv;
    }

    /** 27スロットGUIにフレーム装飾を追加 (機械種別に応じて使用スロットを保護) */
    private void decorateGuiFrame(Inventory inv) { decorateGuiFrame(inv, null); }

    private void decorateGuiFrame(Inventory inv, MachineType type) {
        ItemStack frame = makePane(Material.GRAY_STAINED_GLASS_PANE, " ");

        // 全スロット使用 or 無装飾の機械はパネルを一切置かない
        if (type != null) switch (type) {
            case ADVANCED_ASSEMBLER, AUTO_ENCHANTER, VOID_MINER, TRASH_CAN -> {
                return; // 全スロット使用 → パネル不要
            }
            case INDUCTION_FURNACE -> { fillSlots(inv, keep(1,2)); return; }
            case AUTO_ANVIL, PIXEL_FORGE, CHARGER -> { fillSlots(inv, keep(1)); return; }
            case WASHING_MACHINE -> { fillSlots(inv, keep(1)); return; }
            case CUSTOM_CRAFTER -> {
                fillSlots(inv, keep(1,2, 9,10,11, 18,19,20));
                return;
            }
            case AUTO_CRAFTER -> {
                // スロット0-8は3x3グリッド、9-25はバッファ、26出力
                java.util.Set<Integer> k = new java.util.HashSet<>();
                for (int i = 0; i < 9; i++) k.add(i);
                k.add(26);
                fillSlots(inv, k);
                return;
            }
            case AUTO_BREWER -> { fillSlots(inv, keep(25,26)); return; }
            default -> {} // 標準 → 後続の旧来処理
        }

        // 標準レイアウト (旧来互換): バッファ領域(10-16)は開けておく
        for (int i : new int[]{1,2,3,5,6,7,9,17,18,19,20,21,22,23,24,25}) {
            if (isEmpty(inv.getItem(i))) inv.setItem(i, frame);
        }
    }

    private void fillSlots(Inventory inv, java.util.Set<Integer> keep) {
        ItemStack frame = makePane(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 27; i++) {
            if (!keep.contains(i) && isEmpty(inv.getItem(i)))
                inv.setItem(i, frame);
        }
    }

    private static java.util.Set<Integer> keep(int... slots) {
        java.util.Set<Integer> s = new java.util.HashSet<>();
        s.add(0); s.add(4); s.add(8); s.add(26); // 共通保護
        for (int sl : slots) s.add(sl);
        return s;
    }

    private Inventory createSmelterGui(String locKey, Location loc) {
        Location key = loc.toBlockLocation();
        Inventory existing = openGuis.get(key);
        if (existing != null) return existing;
        Inventory inv = machineManager.createGui(loc, MachineType.AUTO_SMELTER,
                GUI_PREFIX + MachineType.AUTO_SMELTER.displayName);

        ItemStack info = new ItemStack(Material.BOOK);
        ItemMeta im    = info.getItemMeta();
        im.displayName(Component.text("自動精錬炉", NamedTextColor.GOLD));
        im.lore(List.of(
                Component.text("燃料不要でアイテムを自動精錬", NamedTextColor.YELLOW),
                Component.empty(),
                Component.text("■ 入力: スロット0", NamedTextColor.GRAY),
                Component.text("■ 出力: スロット26", NamedTextColor.GRAY),
                Component.text("■ スロット1-25: バッファ", NamedTextColor.GRAY),
                Component.empty(),
                Component.text("ホッパーで搬入・搬出可能", NamedTextColor.DARK_GRAY)
        ));
        im.getPersistentDataContainer().set(
                org.bukkit.NamespacedKey.fromString("circuitsurvival:decoration"),
                org.bukkit.persistence.PersistentDataType.BOOLEAN, true);
        info.setItemMeta(im);
        inv.setItem(4, info);

        decorateGuiFrame(inv, MachineType.AUTO_SMELTER);
        openGuis.put(key, inv);
        machineManager.registerLiveInventory(key, inv);
        return inv;
    }

    /** ストレージドラム: 装飾なしの54スロット純粋ストレージ */
    private Inventory createDrumGui(Location loc) {
        Location key = loc.toBlockLocation();
        Inventory existing = openGuis.get(key);
        if (existing != null) return existing;
        Inventory inv = machineManager.createGui(loc, MachineType.STORAGE_DRUM,
                GUI_PREFIX + MachineType.STORAGE_DRUM.displayName);
        openGuis.put(key, inv);
        machineManager.registerLiveInventory(key, inv);
        return inv;
    }

    private Inventory createFullInvGui(MachineType type, Location loc) {
        Location key = loc.toBlockLocation();
        Inventory existing = openGuis.get(key);
        if (existing != null) return existing;
        Inventory inv = machineManager.createGui(loc, type, GUI_PREFIX + type.displayName);
        decorateGuiFrame(inv, type);
        // スロット4に情報本
        if (isEmpty(inv.getItem(4))) {
            inv.setItem(4, makeInfoBook(type));
        }
        // 実行ボタンが必要な機種 (PIXEL_FORGE/COOKING_STATION)
        if (type == MachineType.PIXEL_FORGE || type == MachineType.COOKING_STATION) {
            ItemStack btn = new ItemStack(Material.LIME_DYE);
            ItemMeta bm = btn.getItemMeta();
            bm.displayName(Component.text("▶ 実行", NamedTextColor.GREEN));
            bm.lore(List.of(Component.text("クリックで実行", NamedTextColor.GRAY)));
            bm.getPersistentDataContainer().set(
                    org.bukkit.NamespacedKey.fromString("circuitsurvival:decoration"),
                    org.bukkit.persistence.PersistentDataType.BOOLEAN, true);
            btn.setItemMeta(bm);
            inv.setItem(8, btn);
        }
        openGuis.put(key, inv);
        machineManager.registerLiveInventory(key, inv);
        return inv;
    }

    private Inventory createWashingMachineGui(Location loc) {
        Location key = loc.toBlockLocation();
        Inventory existing = openGuis.get(key);
        if (existing != null) return existing;
        Inventory inv = machineManager.createGui(loc, MachineType.WASHING_MACHINE,
                GUI_PREFIX + MachineType.WASHING_MACHINE.displayName);
        // スロット0=素材, 1=水バケツ, 4=情報本, 8=実行ボタン, 26=出力
        ItemStack info = new ItemStack(Material.BOOK);
        ItemMeta im = info.getItemMeta();
        im.displayName(Component.text("洗浄機", NamedTextColor.GOLD));
        im.lore(List.of(
                Component.text("水+ENで素材を洗浄し副産物を確率で取り出す", NamedTextColor.YELLOW),
                Component.empty(),
                Component.text("■ スロット0: 洗浄素材 (砂利・砂など)", NamedTextColor.GRAY),
                Component.text("■ スロット1: 水入りバケツ", NamedTextColor.GRAY),
                Component.text("■ スロット26: 出力", NamedTextColor.GRAY),
                Component.empty(),
                Component.text("▶ 右上(8番)のボタンで手動実行", NamedTextColor.GREEN),
                Component.text("⚡ RS通電で自動稼働", NamedTextColor.YELLOW),
                Component.text("水バケツは消費され空バケツが残る", NamedTextColor.DARK_GRAY)
        ));
        im.getPersistentDataContainer().set(
                org.bukkit.NamespacedKey.fromString("circuitsurvival:decoration"),
                org.bukkit.persistence.PersistentDataType.BOOLEAN, true);
        info.setItemMeta(im);
        inv.setItem(4, info);
        ItemStack btn = new ItemStack(Material.LIME_DYE);
        ItemMeta bm = btn.getItemMeta();
        bm.displayName(Component.text("▶ 洗浄", NamedTextColor.GREEN));
        bm.lore(List.of(Component.text("左クリックで手動洗浄実行", NamedTextColor.GRAY)));
        bm.getPersistentDataContainer().set(
                org.bukkit.NamespacedKey.fromString("circuitsurvival:decoration"),
                org.bukkit.persistence.PersistentDataType.BOOLEAN, true);
        btn.setItemMeta(bm);
        inv.setItem(8, btn);
        // スロット1(水バケツ)以外を装飾パネルで埋める
        ItemStack frame = makePane(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i : new int[]{2,3,5,6,7}) { if (isEmpty(inv.getItem(i))) inv.setItem(i, frame); }
        for (int i : new int[]{9,17}) { if (isEmpty(inv.getItem(i))) inv.setItem(i, frame); }
        for (int i : new int[]{18,19,20,21,22,23,24,25}) { if (isEmpty(inv.getItem(i))) inv.setItem(i, frame); }
        openGuis.put(key, inv);
        machineManager.registerLiveInventory(key, inv);
        return inv;
    }

    private ItemStack makeInfoBook(MachineType type) {
        ItemStack book = new ItemStack(Material.BOOK);
        ItemMeta meta = book.getItemMeta();
        meta.displayName(Component.text(type.displayName, NamedTextColor.GOLD));
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(type.description, NamedTextColor.GRAY));
        lore.add(Component.empty());
        if (energyManager.needsEnergy(type)) {
            int cost = energyManager.getEnergyCost(type);
            lore.add(Component.text("■ EN消費: " + cost + "/tick", NamedTextColor.AQUA));
        }
        if (energyManager.isEnergySource(type)) {
            lore.add(Component.text("■ EN生成源", NamedTextColor.GREEN));
        }
        lore.add(Component.text("■ RS信号で動作", NamedTextColor.YELLOW));
        lore.add(Component.empty());
        lore.add(Component.text("スロット0〜25: 入出力バッファ", NamedTextColor.DARK_GRAY));
        lore.add(Component.text("スロット26: 出力", NamedTextColor.DARK_GRAY));
        meta.lore(lore);
        meta.getPersistentDataContainer().set(
                org.bukkit.NamespacedKey.fromString("circuitsurvival:decoration"),
                org.bukkit.persistence.PersistentDataType.BOOLEAN, true);
        book.setItemMeta(meta);
        return book;
    }

    private Inventory createBrewerGui(Location loc) {
        Location key = loc.toBlockLocation();
        Inventory existing = openGuis.get(key);
        if (existing != null) return existing;
        Inventory inv = machineManager.createGui(loc, MachineType.AUTO_BREWER,
                GUI_PREFIX + MachineType.AUTO_BREWER.displayName);
        // スロット25と26に説明パネル
        ItemStack ingLabel = makePane(Material.NETHER_WART, "§e材料スロット (ネザーウォート等)");
        ItemStack fuelLabel = makePane(Material.BLAZE_POWDER, "§e燃料スロット (ブレイズパウダー)");
        // 既に保存済みアイテムがあれば上書きしない
        if (isEmpty(inv.getItem(25))) inv.setItem(25, ingLabel);
        if (isEmpty(inv.getItem(26))) inv.setItem(26, fuelLabel);
        decorateGuiFrame(inv, MachineType.AUTO_BREWER);
        openGuis.put(key, inv);
        machineManager.registerLiveInventory(key, inv);
        return inv;
    }



    // ==========================================================================
    // AUTO_CRAFTER GUI / 自動クラフタータスク
    // ==========================================================================

    /**
     * 自動クラフターのGUI
     * 左: スロット0-8 = レシピグリッド (3x3)
     * 中: スロット9-17 = 素材バッファ (3x3)
     * 右: スロット18-25 = 装飾, スロット26 = 出力
     */
    private Inventory createAutoCrafterGui(Location loc) {
        Location key = loc.toBlockLocation();
        Inventory existing = openGuis.get(key);
        if (existing != null) return existing;
        Inventory gui = machineManager.createGui(loc, MachineType.AUTO_CRAFTER,
                GUI_PREFIX + MachineType.AUTO_CRAFTER.displayName);
        ItemStack gray = makePane(Material.GRAY_STAINED_GLASS_PANE, " ");
        // 右3列(18-25)を装飾でロック
        for (int i = 18; i <= 25; i++) {
            if (gui.getItem(i) == null || gui.getItem(i).getType().isAir())
                gui.setItem(i, gray);
        }
        if (isEmpty(gui.getItem(22))) {
            ItemStack book = new ItemStack(Material.KNOWLEDGE_BOOK);
            ItemMeta m = book.getItemMeta();
            m.displayName(Component.text("自動クラフターの使い方", NamedTextColor.GOLD));
            m.lore(List.of(
                    Component.text("■ 左3x3グリッド(0-8): レシピ配置", NamedTextColor.AQUA),
                    Component.text("■ 中央3x3(9-17): 素材バッファ", NamedTextColor.AQUA),
                    Component.text("■ スロット26: 完成品出力", NamedTextColor.AQUA),
                    Component.text("■ RS信号: 通電中は連続動作", NamedTextColor.YELLOW),
                    Component.text("■ EN消費: 20/tick", NamedTextColor.YELLOW)
            ));
            m.getPersistentDataContainer().set(
                    org.bukkit.NamespacedKey.fromString("circuitsurvival:decoration"),
                    org.bukkit.persistence.PersistentDataType.BOOLEAN, true);
            book.setItemMeta(m);
            gui.setItem(22, book);
        }
        openGuis.put(key, gui);
        machineManager.registerLiveInventory(key, gui);
        return gui;
    }

    /**
     * カスタムクラフターのGUI
     * 左: スロット0-8 = レシピグリッド (3x3)
     * 中: スロット9-25 = 素材バッファ
     * 右: スロット26 = 出力
     * スロット22に使い方説明本を配置
     */
    private Inventory createCustomCrafterGui(Location loc) {
        Location key = loc.toBlockLocation();
        Inventory existing = openGuis.get(key);
        if (existing != null) return existing;
        Inventory gui = machineManager.createGui(loc, MachineType.CUSTOM_CRAFTER,
                GUI_PREFIX + MachineType.CUSTOM_CRAFTER.displayName);
        ItemStack gray = makePane(Material.GRAY_STAINED_GLASS_PANE, " ");
        ItemStack green = makePane(Material.LIME_STAINED_GLASS_PANE, "§a入力グリッド");
        ItemStack orange = makePane(Material.ORANGE_STAINED_GLASS_PANE, "§6出力");
        // 3x3入力グリッド: 左3列を各段に
        int[] gridSlots = {0, 1, 2, 9, 10, 11, 18, 19, 20};
        for (int i : gridSlots) {
            if (isEmpty(gui.getItem(i))) gui.setItem(i, green);
        }
        // 出力スロット装飾
        if (isEmpty(gui.getItem(26))) gui.setItem(26, orange);
        // バッファ領域: グリッド以外の全スロットを灰色で埋める
        java.util.Set<Integer> gset = new java.util.HashSet<>();
        for (int i : gridSlots) gset.add(i);
        for (int i = 0; i < 26; i++) {
            if (!gset.contains(i) && isEmpty(gui.getItem(i))) gui.setItem(i, gray);
        }
        if (isEmpty(gui.getItem(22))) {
            ItemStack book = new ItemStack(Material.KNOWLEDGE_BOOK);
            ItemMeta m = book.getItemMeta();
            m.displayName(Component.text("カスタムクラフターの使い方", NamedTextColor.GOLD));
            m.lore(List.of(
                    Component.text("■ 左3x3(上0/1/2 中9/10/11 下18/19/20): 材料配置", NamedTextColor.AQUA),
                    Component.text("  §7レシピブック(Guide)で配置を確認", NamedTextColor.GRAY),
                    Component.text("■ 中央右(3-8/12-17/21-25): 素材バッファ", NamedTextColor.AQUA),
                    Component.text("■ スロット26: 完成品出力", NamedTextColor.AQUA),
                    Component.text("■ RS信号: 通電中は連続動作", NamedTextColor.YELLOW),
                    Component.text("■ EN消費: 20/tick", NamedTextColor.YELLOW),
                    Component.empty(),
                    Component.text("§e利用可能なレシピ一覧は", NamedTextColor.GRAY),
                    Component.text("§e/cs guide で確認できます", NamedTextColor.GRAY)
            ));
            m.getPersistentDataContainer().set(
                    org.bukkit.NamespacedKey.fromString("circuitsurvival:decoration"),
                    org.bukkit.persistence.PersistentDataType.BOOLEAN, true);
            book.setItemMeta(m);
            gui.setItem(22, book);
        }
        openGuis.put(key, gui);
        machineManager.registerLiveInventory(key, gui);
        return gui;
    }

    /**
     * AUTO_CRAFTERタスク: RS通電中に4tickごと自動クラフト。
     * 内蔵レシピグリッド(スロット0-8)を読み、素材バッファ(9-17)から
     * 材料を消費してスロット26に出力。
     */
    private void startAutoCrafterTask() {
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            for (Location loc : new ArrayList<>(machineManager.getByType(MachineType.AUTO_CRAFTER))) {
                Block mb = loc.getBlock();
                if (!mb.isBlockPowered() && !mb.isBlockIndirectlyPowered()) continue;
                if (!energyManager.tryConsume(loc, MachineType.AUTO_CRAFTER)) continue;

                ItemStack[] contents = machineManager.getStoredContents(loc);
                if (contents == null) continue;

                // 内蔵レシピグリッド(0-8)からレシピ取得
                ItemStack[] recipe = getOwnRecipe(contents);
                if (recipe == null) continue;

                tryCraftFromBuffer(loc, recipe, contents);
                machineManager.setStoredContents(loc, contents);
            }
        }, 4L, 4L);
    }

    /** 自身のスロット0-8をレシピマトリクスとして読み込む */
    private ItemStack[] getOwnRecipe(ItemStack[] contents) {
        ItemStack[] matrix = new ItemStack[9];
        boolean hasItems = false;
        for (int i = 0; i < 9; i++) {
            if (contents[i] != null && !contents[i].getType().isAir()) {
                matrix[i] = contents[i].clone();
                matrix[i].setAmount(1);
                hasItems = true;
            }
        }
        return hasItems ? matrix : null;
    }

    /** 素材バッファ(9-17)からレシピ分消費し、slot 26に出力する */
    private void tryCraftFromBuffer(Location loc, ItemStack[] recipe, ItemStack[] buffer) {
        ItemStack[] matrix = recipe; // already flat 9-element array from getOwnRecipe

        Recipe matched = plugin.getServer().getCraftingRecipe(matrix, loc.getWorld());
        if (matched == null) return;

        ItemStack result = matched.getResult().clone();
        if (result.getType().isAir()) return;

        // 出力スロット(26)の空き確認
        ItemStack outSlot = buffer[26];
        if (outSlot != null && !outSlot.getType().isAir()) {
            if (outSlot.getType() != result.getType()) return;
            if (outSlot.getAmount() + result.getAmount() > outSlot.getMaxStackSize()) return;
        }

        // 必要材料をカウント
        Map<Material, Integer> required = new EnumMap<>(Material.class);
        for (ItemStack item : matrix) {
            if (item != null) required.merge(item.getType(), 1, Integer::sum);
        }

        // 素材バッファ(9-17)の在庫確認
        for (Map.Entry<Material, Integer> req : required.entrySet()) {
            int avail = 0;
            for (int i = 9; i < 18; i++) {
                if (buffer[i] != null && buffer[i].getType() == req.getKey())
                    avail += buffer[i].getAmount();
            }
            if (avail < req.getValue()) return;
        }

        // 材料消費 (9-17)
        for (Map.Entry<Material, Integer> req : required.entrySet()) {
            int toConsume = req.getValue();
            for (int i = 9; i < 18 && toConsume > 0; i++) {
                if (buffer[i] != null && buffer[i].getType() == req.getKey()) {
                    int take = Math.min(toConsume, buffer[i].getAmount());
                    toConsume -= take;
                    if (buffer[i].getAmount() <= take) buffer[i] = null;
                    else buffer[i].setAmount(buffer[i].getAmount() - take);
                }
            }
        }

        // 出力
        if (buffer[26] == null || buffer[26].getType().isAir()) {
            buffer[26] = result;
        } else {
            buffer[26].setAmount(buffer[26].getAmount() + result.getAmount());
        }
    }

    // ==========================================================================
    // ITEM_SORTER タスク
    // ==========================================================================

    /**
     * アイテムソーター: RS通電中に4tickごと、バッファ内のアイテムを
     * 「同じ種類のアイテムを既に持つ隣接チェスト」へルーティング。
     * 一致するチェストがなければ、任意の隣接コンテナへ（オーバーフロー）。
     */
    private void startItemSorterTask() {
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            for (Location loc : new ArrayList<>(machineManager.getByType(MachineType.ITEM_SORTER))) {

                if (!loc.getBlock().isBlockPowered() && !loc.getBlock().isBlockIndirectlyPowered()) continue;
                if (!energyManager.tryConsume(loc, MachineType.ITEM_SORTER)) continue;

                ItemStack[] contents = machineManager.getStoredContents(loc);
                if (contents == null) continue;

                for (int i = 0; i < 27; i++) {
                    if (contents[i] == null || contents[i].getType().isAir()) continue;
                    Material mat = contents[i].getType();

                    // 同種アイテムがある隣接コンテナを探す
                    Inventory target = findAdjacentContainerWith(loc, mat);
                    // なければ任意の隣接コンテナ
                    if (target == null) target = findAnyAdjacentContainer(loc);
                    if (target == null) break;

                    // スタック丸ごと移送
                    int amount = contents[i].getAmount();
                    Map<Integer, ItemStack> leftover = target.addItem(new ItemStack(mat, amount));
                    int notMoved = leftover.values().stream().mapToInt(ItemStack::getAmount).sum();
                    int moved = amount - notMoved;
                    if (moved > 0) {
                        if (contents[i].getAmount() <= moved) contents[i] = null;
                        else contents[i].setAmount(contents[i].getAmount() - moved);
                    }
                    break; // 1スロットずつ処理
                }
            }
        }, 4L, 4L);
    }

    /** 指定素材を既に含む隣接コンテナを返す */
    private Inventory findAdjacentContainerWith(Location loc, Material mat) {
        for (BlockFace face : ADJACENT_FACES) {
            Block adj = loc.getBlock().getRelative(face);
            if (adj.getState() instanceof InventoryHolder holder) {
                for (ItemStack item : holder.getInventory().getContents()) {
                    if (item != null && item.getType() == mat) return holder.getInventory();
                }
            }
        }
        return null;
    }

    /** 任意の隣接コンテナを返す (オーバーフロー用) */
    private Inventory findAnyAdjacentContainer(Location loc) {
        for (BlockFace face : ADJACENT_FACES) {
            Block adj = loc.getBlock().getRelative(face);
            if (adj.getState() instanceof InventoryHolder holder
                    && !(adj.getState() instanceof org.bukkit.block.Hopper)) {
                return holder.getInventory();
            }
        }
        return null;
    }

    // ==========================================================================
    // AUTO_FARMER タスク
    // ==========================================================================

    /**
     * 自動農場: RS通電中に40tickごと真下3x3の成熟作物を収穫し、
     * 自身のインベントリにある種で再植え付けする。
     * 収穫物はワールドにドロップ。
     */
    private void startAutoFarmerTask() {
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            for (Location loc : new ArrayList<>(machineManager.getByType(MachineType.AUTO_FARMER))) {

                if (!loc.getBlock().isBlockPowered() && !loc.getBlock().isBlockIndirectlyPowered()) continue;
                if (!energyManager.tryConsume(loc, MachineType.AUTO_FARMER)) continue;

                ItemStack[] contents = machineManager.getStoredContents(loc);
                if (contents == null) continue;

                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        Block crop = loc.getBlock().getRelative(dx, -1, dz);
                        harvestAndReplant(crop, contents);
                    }
                }
            }
        }, 40L, 40L);
    }

    private void harvestAndReplant(Block crop, ItemStack[] farmerContents) {
        if (!(crop.getBlockData() instanceof org.bukkit.block.data.Ageable ageable)) return;
        if (ageable.getAge() < ageable.getMaximumAge()) return;

        // 収穫前に素材タイプを記録
        Material originalCropType = crop.getType();

        // 収穫ドロップをワールドに
        for (ItemStack drop : crop.getDrops(DIAMOND_PICK)) {
            crop.getWorld().dropItemNaturally(crop.getLocation(), drop);
        }
        crop.setType(Material.AIR);

        // 収穫前の作物タイプから対応する種を検索
        Material seedMat = null;
        for (Map.Entry<Material, Material> entry : SEED_TO_CROP.entrySet()) {
            if (entry.getValue() == originalCropType) { seedMat = entry.getKey(); break; }
        }
        if (seedMat == null) return;

        Material soilNeeded = SEED_SOIL.getOrDefault(seedMat, Material.FARMLAND);
        Block below = crop.getRelative(BlockFace.DOWN);
        if (below.getType() != soilNeeded) return;

        for (int i = 0; i < 27; i++) {
            if (farmerContents[i] != null && farmerContents[i].getType() == seedMat) {
                crop.setType(originalCropType);
                if (farmerContents[i].getAmount() <= 1) farmerContents[i] = null;
                else farmerContents[i].setAmount(farmerContents[i].getAmount() - 1);
                break;
            }
        }
    }

    // ==========================================================================
    // ホッパーシミュレーションタスク (全カスタムマシン用)
    // ==========================================================================

    /**
     * バニラホッパーとカスタムマシンを連携させる。
     * 4tickごとに実行 (バニラホッパーと同速)。
     *
     * 搬入: マシンを向いているホッパー → マシンの入力スロットへ
     * 搬出: マシンの出力スロット(26) → 真下のホッパーへ
     */
    private static final BlockFace[] ADJACENT_FACES = {
            BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST,
            BlockFace.UP, BlockFace.DOWN
    };
    private static final BlockFace[] HORIZONTAL_FACES = {
            BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST
    };

    // ---- 粉砕機・圧縮機 自動処理 (CRUSHER / COMPRESSOR) -------------------------

    private void startCrusherCompressorTask() {
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            List<Location> crusherList = new ArrayList<>(machineManager.getByType(MachineType.CRUSHER));
            crusherList.addAll(machineManager.getByType(MachineType.COMPRESSOR));
            for (Location loc : crusherList) {
                if (!loc.getChunk().isLoaded()) continue;
                MachineType type = machineManager.getType(loc);
                if (type == null) continue;
                if (!loc.getBlock().isBlockPowered() && !loc.getBlock().isBlockIndirectlyPowered()) continue;
                if (!energyManager.tryConsume(loc, type)) continue;
                ItemStack[] contents = machineManager.getStoredContents(loc);
                if (contents == null) continue;
                boolean changed = (type == MachineType.CRUSHER)
                        ? autoCrush(contents) : autoCompress(contents);
                if (changed) machineManager.setStoredContents(loc, contents);
            }
        }, 10L, 40L);
    }

    private boolean autoCrush(ItemStack[] contents) {
        for (int i = 0; i < 26; i++) {
            if (isEmpty(contents[i])) continue;
            ItemStack output = crushOre(contents[i].getType());
            if (output == null) continue;
            int out = findAddSlot(contents, output, 26);
            if (out < 0) continue;
            consumeContentsSlot(contents, i, 1);
            addToContents(contents, out, output);
            return true;
        }
        return false;
    }

    private boolean autoCompress(ItemStack[] contents) {
        for (int i = 0; i < 26; i++) {
            if (isEmpty(contents[i])) continue;
            ItemStack out = compress(contents[i]);
            if (out == null) continue;
            int needed = 9;
            int total = 0;
            Material mat = contents[i].getType();
            for (int j = 0; j < 26; j++) {
                if (!isEmpty(contents[j]) && contents[j].getType() == mat) total += contents[j].getAmount();
            }
            if (total < needed) continue;
            int outSlot = findAddSlot(contents, out, 26);
            if (outSlot < 0) continue;
            int rem = needed;
            for (int j = 0; j < 26 && rem > 0; j++) {
                if (isEmpty(contents[j]) || contents[j].getType() != mat) continue;
                int take = Math.min(contents[j].getAmount(), rem);
                contents[j].setAmount(contents[j].getAmount() - take);
                if (contents[j].getAmount() <= 0) contents[j] = null;
                rem -= take;
            }
            addToContents(contents, outSlot, out);
            return true;
        }
        return false;
    }

    private void consumeContentsSlot(ItemStack[] contents, int slot, int amount) {
        if (isEmpty(contents[slot])) return;
        contents[slot].setAmount(contents[slot].getAmount() - amount);
        if (contents[slot].getAmount() <= 0) contents[slot] = null;
    }

    private int findAddSlot(ItemStack[] contents, Material mat, int preferred) {
        for (int i = 0; i < 27; i++) {
            if (!isEmpty(contents[i]) && contents[i].getType() == mat
                    && contents[i].getAmount() < mat.getMaxStackSize()) return i;
        }
        if (preferred >= 0 && preferred < 27 && isEmpty(contents[preferred])) return preferred;
        for (int i = 0; i < 27; i++) {
            if (isEmpty(contents[i])) return i;
        }
        return -1;
    }

    /** PDCを考慮したスロット検索 (中間素材対応) */
    private int findAddSlot(ItemStack[] contents, ItemStack item, int preferred) {
        for (int i = 0; i < 27; i++) {
            if (!isEmpty(contents[i]) && contents[i].isSimilar(item)
                    && contents[i].getAmount() < contents[i].getMaxStackSize()) return i;
        }
        if (preferred >= 0 && preferred < 27 && isEmpty(contents[preferred])) return preferred;
        for (int i = 0; i < 27; i++) {
            if (isEmpty(contents[i])) return i;
        }
        return -1;
    }

    private void addToContents(ItemStack[] contents, int slot, ItemStack item) {
        if (isEmpty(contents[slot])) {
            contents[slot] = item.clone();
        } else {
            contents[slot].setAmount(Math.min(
                    contents[slot].getAmount() + item.getAmount(),
                    contents[slot].getMaxStackSize()));
        }
    }

    // ---- 自動醸造機 (AUTO_BREWER) ------------------------------------------------

    private static final Map<Material, PotionType> BREW_EFFECT = Map.of(
            Material.SUGAR,                   PotionType.SPEED,
            Material.BLAZE_POWDER,            PotionType.STRENGTH,
            Material.GLISTERING_MELON_SLICE,  PotionType.INSTANT_HEAL,
            Material.SPIDER_EYE,              PotionType.POISON,
            Material.GHAST_TEAR,              PotionType.REGEN,
            Material.MAGMA_CREAM,             PotionType.FIRE_RESISTANCE,
            Material.GOLDEN_CARROT,           PotionType.NIGHT_VISION,
            Material.PUFFERFISH,              PotionType.WATER_BREATHING,
            Material.FERMENTED_SPIDER_EYE,    PotionType.WEAKNESS
    );

    private void startAutoBrewerTask() {
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            for (Location loc : new ArrayList<>(machineManager.getByType(MachineType.AUTO_BREWER))) {
                if (!loc.getBlock().isBlockPowered() && !loc.getBlock().isBlockIndirectlyPowered()) continue;
                if (!energyManager.tryConsume(loc, MachineType.AUTO_BREWER)) continue;
                ItemStack[] contents = machineManager.getStoredContents(loc);
                if (contents == null) continue;
                if (brewOnce(contents)) machineManager.setStoredContents(loc, contents);
            }
        }, 10L, 60L);
    }

    private boolean brewOnce(ItemStack[] contents) {
        ItemStack ingredient = contents[25];
        ItemStack fuel       = contents[26];
        if (isEmpty(ingredient) || isEmpty(fuel)) return false;
        if (fuel.getType() != Material.BLAZE_POWDER) return false;

        Material ingMat = ingredient.getType();
        boolean brewed = false;

        if (ingMat == Material.NETHER_WART) {
            for (int i = 0; i < 25; i++) {
                if (isEmpty(contents[i]) || contents[i].getType() != Material.POTION) continue;
                if (!(contents[i].getItemMeta() instanceof org.bukkit.inventory.meta.PotionMeta pm)) continue;
                PotionType base = pm.getBasePotionData().getType();
                if (base != null && base != PotionType.WATER) continue;
                pm.setBasePotionData(new org.bukkit.potion.PotionData(PotionType.AWKWARD));
                contents[i].setItemMeta(pm);
                brewed = true;
            }
        }

        PotionType target = BREW_EFFECT.get(ingMat);
        if (!brewed && target != null) {
            for (int i = 0; i < 25; i++) {
                if (isEmpty(contents[i]) || contents[i].getType() != Material.POTION) continue;
                if (!(contents[i].getItemMeta() instanceof org.bukkit.inventory.meta.PotionMeta pm)) continue;
                if (pm.getBasePotionData().getType() != PotionType.AWKWARD) continue;
                pm.setBasePotionData(new org.bukkit.potion.PotionData(target));
                contents[i].setItemMeta(pm);
                brewed = true;
            }
        }

        if (brewed) {
            consumeContentsSlot(contents, 25, 1);
            consumeContentsSlot(contents, 26, 1);
        }
        return brewed;
    }

    // ---- アイテムルーター (ITEM_ROUTER) -----------------------------------------

    /**
     * ITEM_ROUTER: スロット0-3 に設定されたフィルターアイテムに従い
     * バッファ(スロット4-26)のアイテムを N/S/E/W 方向の隣接コンテナへ振り分ける。
     * RS通電時のみ動作。
     */
    private void startItemRouterTask() {
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            for (Location loc : new ArrayList<>(machineManager.getByType(MachineType.ITEM_ROUTER))) {
                if (!loc.getBlock().isBlockPowered() && !loc.getBlock().isBlockIndirectlyPowered()) continue;
                if (!energyManager.tryConsume(loc, MachineType.ITEM_ROUTER)) continue;
                ItemStack[] contents = machineManager.getStoredContents(loc);
                if (contents == null) continue;

                BlockFace[] dirs = {BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST};
                boolean changed = false;
                for (int fi = 0; fi < 4; fi++) {
                    ItemStack filter = contents[fi];
                    if (filter == null || filter.getType().isAir()) continue;
                    Material filterMat = filter.getType();

                    // バッファからフィルターに一致するアイテムを探す
                    for (int bi = 4; bi < 27; bi++) {
                        ItemStack buf = contents[bi];
                        if (buf == null || buf.getType().isAir()) continue;
                        if (buf.getType() != filterMat) continue;

                        // 対応方向のコンテナへ搬出
                        Block adj = loc.getBlock().getRelative(dirs[fi]);
                        if (!(adj.getState() instanceof org.bukkit.block.Container container)) break;
                        Inventory adjInv = container.getInventory();
                        Map<Integer, ItemStack> leftover = adjInv.addItem(new ItemStack(buf.getType(), 1));
                        if (leftover.isEmpty()) {
                            consumeContentsSlot(contents, bi, 1);
                            changed = true;
                        }
                        break;
                    }
                }
                if (changed) machineManager.setStoredContents(loc, contents);
            }
        }, 4L, 4L);
    }

    // ---- ブロック変換炉 (BLOCK_TRANSMUTER) -------------------------------------

    private static final Map<Material, Material> TRANSMUTE_MAP = new EnumMap<>(Material.class);
    static {
        TRANSMUTE_MAP.put(Material.SAND,          Material.GLASS);
        TRANSMUTE_MAP.put(Material.RED_SAND,      Material.GLASS);
        TRANSMUTE_MAP.put(Material.COBBLESTONE,   Material.STONE);
        TRANSMUTE_MAP.put(Material.STONE,         Material.SMOOTH_STONE);
        TRANSMUTE_MAP.put(Material.GRAVEL,        Material.STONE);
        TRANSMUTE_MAP.put(Material.SNOW_BLOCK,    Material.ICE);
        TRANSMUTE_MAP.put(Material.ICE,           Material.PACKED_ICE);
        TRANSMUTE_MAP.put(Material.COBBLED_DEEPSLATE, Material.DEEPSLATE);
        TRANSMUTE_MAP.put(Material.NETHERRACK,    Material.NETHER_BRICK);
        TRANSMUTE_MAP.put(Material.BASALT,        Material.SMOOTH_BASALT);
    }

    /**
     * BLOCK_TRANSMUTER: RS立ち上がりで前面ブロックを変換する。
     */
    private void startBlockTransmuterTask() {
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            for (Location loc : new ArrayList<>(machineManager.getByType(MachineType.BLOCK_TRANSMUTER))) {
                boolean powered = loc.getBlock().isBlockPowered()
                        || loc.getBlock().isBlockIndirectlyPowered();
                String key = locKey(loc);
                boolean prev = prevPowered.getOrDefault(key, false);
                prevPowered.put(key, powered);
                if (!powered || prev) continue; // RS立ち上がりのみ
                if (!energyManager.tryConsume(loc, MachineType.BLOCK_TRANSMUTER)) continue;

                BlockFace facing = getMachineFacing(loc);
                Block target = loc.getBlock().getRelative(facing);
                Material result = TRANSMUTE_MAP.get(target.getType());
                if (result == null) continue;
                // 燃料確認 (スロット0: 石炭/木炭/ブレイズロッド)
                ItemStack[] contents = machineManager.getStoredContents(loc);
                if (contents == null) continue;
                int fuelSlot = -1;
                for (int i = 0; i < contents.length; i++) {
                    if (contents[i] == null || contents[i].getType().isAir()) continue;
                    Material fm = contents[i].getType();
                    if (fm == Material.COAL || fm == Material.CHARCOAL
                            || fm == Material.BLAZE_ROD || fm == Material.LAVA_BUCKET) {
                        fuelSlot = i; break;
                    }
                }
                if (fuelSlot < 0) continue; // 燃料なし
                consumeContentsSlot(contents, fuelSlot, 1);
                if (contents[fuelSlot] != null && contents[fuelSlot].getType() == Material.LAVA_BUCKET) {
                    contents[fuelSlot] = new ItemStack(Material.BUCKET); // 溶岩バケツ → バケツ返却
                }
                machineManager.setStoredContents(loc, contents);
                target.setType(result);
            }
        }, 1L, 1L);
    }

    // ---- 流体コレクター (FLUID_COLLECTOR) ----------------------------------------

    /**
     * FLUID_COLLECTOR: RS通電中、スロット0の空バケツを消費して
     * 隣接する水源または溶岩源を水入りバケツ・溶岩バケツとして収集する。
     */
    private void startFluidCollectorTask() {
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            for (Location loc : new ArrayList<>(machineManager.getByType(MachineType.FLUID_COLLECTOR))) {
                if (!loc.getBlock().isBlockPowered() && !loc.getBlock().isBlockIndirectlyPowered()) continue;
                if (!energyManager.tryConsume(loc, MachineType.FLUID_COLLECTOR)) continue;
                ItemStack[] contents = machineManager.getStoredContents(loc);
                if (contents == null) continue;

                // スロット0 = 空バケツ
                ItemStack bucket = contents[0];
                if (bucket == null || bucket.getType() != Material.BUCKET) continue;

                // 隣接ブロックを検索
                for (BlockFace face : new BlockFace[]{
                        BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST,
                        BlockFace.WEST, BlockFace.UP, BlockFace.DOWN}) {
                    Block adj = loc.getBlock().getRelative(face);
                    Material fluidType = adj.getType();
                    Material filledBucket;
                    if (fluidType == Material.WATER) {
                        filledBucket = Material.WATER_BUCKET;
                    } else if (fluidType == Material.LAVA) {
                        filledBucket = Material.LAVA_BUCKET;
                    } else {
                        continue;
                    }
                    // 出力スロット(1-26)に空きを探す
                    int outSlot = -1;
                    for (int i = 1; i < 27; i++) {
                        if (contents[i] == null || contents[i].getType().isAir()) { outSlot = i; break; }
                    }
                    if (outSlot < 0) break; // 出力満杯
                    // バケツ消費 & 液体ブロック除去 & バケツ生成
                    consumeContentsSlot(contents, 0, 1);
                    adj.setType(Material.AIR);
                    contents[outSlot] = new ItemStack(filledBucket, 1);
                    machineManager.setStoredContents(loc, contents);
                    break;
                }
            }
        }, 10L, 20L);
    }

    // ---- 自動骨粉散布機 (BONE_MEALER) ------------------------------------------

    /**
     * BONE_MEALER: RS通電中、下Y-1の5×5範囲の育成可能ブロックに骨粉を自動散布。
     * 4tickごとに範囲内をシャッフルして順に applyBoneMeal → 成功したブロックだけ骨粉を消費。
     * AUTO_FARMERの3×3より広い5×5をカバー。
     */
    private void startBoneMealerTask() {
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            for (Location loc : new ArrayList<>(machineManager.getByType(MachineType.BONE_MEALER))) {
                if (!loc.getBlock().isBlockPowered() && !loc.getBlock().isBlockIndirectlyPowered()) continue;
                if (!energyManager.tryConsume(loc, MachineType.BONE_MEALER)) continue;
                ItemStack[] contents = machineManager.getStoredContents(loc);
                if (contents == null) continue;

                // 対象ブロックを収集してシャッフル (5x5 at Y-1)
                List<Block> targets = new ArrayList<>();
                for (int dx = -2; dx <= 2; dx++) {
                    for (int dz = -2; dz <= 2; dz++) {
                        targets.add(loc.getBlock().getRelative(dx, -1, dz));
                    }
                }
                java.util.Collections.shuffle(targets);

                // 骨粉が1個もなければスキップ
                boolean hasBM = false;
                for (ItemStack is : contents) {
                    if (is != null && is.getType() == Material.BONE_MEAL && is.getAmount() > 0) { hasBM = true; break; }
                }
                if (!hasBM) continue;

                boolean dirty = false;
                for (Block target : targets) {
                    // 骨粉残量確認 (量0のスタックを除外)
                    int boneMealSlot = -1;
                    for (int i = 0; i < 27; i++) {
                        if (contents[i] != null && contents[i].getType() == Material.BONE_MEAL
                                && contents[i].getAmount() > 0) {
                            boneMealSlot = i; break;
                        }
                    }
                    if (boneMealSlot < 0) break; // 骨粉切れ

                    if (target.applyBoneMeal(BlockFace.UP)) {
                        consumeContentsSlot(contents, boneMealSlot, 1);
                        dirty = true;
                        loc.getWorld().spawnParticle(Particle.VILLAGER_HAPPY,
                                target.getLocation().add(0.5, 1.0, 0.5), 4, 0.3, 0.2, 0.3, 0);
                    }
                }
                if (dirty) machineManager.setStoredContents(loc, contents);
            }
        }, 4L, 4L);
    }

    // ---- 自動植林機 (AUTO_FORESTER) ------------------------------------------

    /**
     * VERTICAL_ELEVATOR: RS立ち上がりでインベントリのアイテムを
     * 1ブロック上のコンテナ (VERTICAL_ELEVATOR / チェスト等) へ移送する。
     * 積み重ねて使用することで垂直輸送を構築できる。
     */
    private void startElevatorTask() {
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            for (Location loc : new ArrayList<>(machineManager.getByType(MachineType.VERTICAL_ELEVATOR))) {
                if (!loc.getChunk().isLoaded()) continue;
                String key = locKey(loc);
                Block block = loc.getBlock();

                boolean powered = block.isBlockPowered() || block.isBlockIndirectlyPowered();
                boolean prev = prevPowered.getOrDefault(key, false);
                prevPowered.put(key, powered);
                if (!powered || prev) continue;
                if (!energyManager.tryConsume(loc, MachineType.VERTICAL_ELEVATOR)) continue;

                Block above = block.getRelative(BlockFace.UP);
                ItemStack[] contents = machineManager.getStoredContents(loc);
                if (contents == null) continue;

                // 上のブロックがVERTICAL_ELEVATORならそのバッファへ移動
                if (machineManager.isMachine(above.getLocation()) && machineManager.getType(above.getLocation()) == MachineType.VERTICAL_ELEVATOR) {
                    ItemStack[] aboveContents = machineManager.getStoredContents(above.getLocation());
                    if (aboveContents == null) continue;
                    boolean moved = false;
                    for (int i = 0; i < contents.length; i++) {
                        if (contents[i] == null || contents[i].getType().isAir()) continue;
                        ItemStack item = contents[i].clone();
                        item.setAmount(1);
                        int added = addItemToArray(aboveContents, item);
                        if (added > 0) {
                            consumeContentsSlot(contents, i, 1);
                            moved = true;
                        }
                    }
                    if (moved) {
                        machineManager.setStoredContents(loc, contents);
                        machineManager.setStoredContents(above.getLocation(), aboveContents);
                    }
                } else {
                    // 上のブロックが通常コンテナの場合
                    org.bukkit.block.BlockState state = above.getState();
                    if (state instanceof InventoryHolder holder) {
                        Inventory inv = holder.getInventory();
                        boolean moved = false;
                        for (int i = 0; i < contents.length; i++) {
                            if (contents[i] == null || contents[i].getType().isAir()) continue;
                            ItemStack item = contents[i].clone();
                            item.setAmount(1);
                            java.util.HashMap<Integer, ItemStack> leftover = inv.addItem(item);
                            if (leftover.isEmpty()) {
                                consumeContentsSlot(contents, i, 1);
                                moved = true;
                            }
                        }
                        if (moved) machineManager.setStoredContents(loc, contents);
                    }
                }
            }
        }, 1L, 1L);
    }

    /**
     * WOODCUTTER: スロット0の原木を板材に、板材を棒に加工する。
     * RS通電で4tickごとに1回処理。
     *   原木/木/樹皮剥ぎ原木 → 板材 6枚
     *   板材 2枚 → 棒 8本
     */
    private void startWoodcutterTask() {
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            for (Location loc : new ArrayList<>(machineManager.getByType(MachineType.WOODCUTTER))) {
                if (!loc.getChunk().isLoaded()) continue;
                Block block = loc.getBlock();
                if (!block.isBlockPowered() && !block.isBlockIndirectlyPowered()) continue;
                if (!energyManager.tryConsume(loc, MachineType.WOODCUTTER)) continue;

                ItemStack[] contents = machineManager.getStoredContents(loc);
                if (contents == null || contents.length < 2) continue;
                if (contents[0] == null || contents[0].getType().isAir()) continue;

                Material input = contents[0].getType();
                String inputName = input.name();
                ItemStack output = null;

                if (inputName.endsWith("_LOG") || inputName.endsWith("_WOOD")
                        || inputName.endsWith("_STEM") || inputName.endsWith("_HYPHAE")) {
                    output = new ItemStack(getPlankMaterial(input), 6);
                } else if (inputName.endsWith("_PLANKS")) {
                    if (contents[0].getAmount() >= 2) {
                        output = new ItemStack(Material.STICK, 8);
                    }
                }

                if (output == null) continue;

                // 出力先を探す (スロット1-26)
                int consume = inputName.endsWith("_PLANKS") ? 2 : 1;
                for (int i = 1; i < contents.length; i++) {
                    if (contents[i] == null || contents[i].getType().isAir()) {
                        contents[i] = output;
                        consumeContentsSlot(contents, 0, consume);
                        machineManager.setStoredContents(loc, contents);
                        break;
                    } else if (contents[i].isSimilar(output) && contents[i].getAmount() + output.getAmount() <= output.getMaxStackSize()) {
                        contents[i].setAmount(contents[i].getAmount() + output.getAmount());
                        consumeContentsSlot(contents, 0, consume);
                        machineManager.setStoredContents(loc, contents);
                        break;
                    }
                }
            }
        }, 20L, 20L);
    }

    /** 原木Materialから対応する板材を返す */
    private Material getPlankMaterial(Material log) {
        return switch (log) {
            case OAK_LOG, OAK_WOOD, STRIPPED_OAK_LOG, STRIPPED_OAK_WOOD -> Material.OAK_PLANKS;
            case SPRUCE_LOG, SPRUCE_WOOD, STRIPPED_SPRUCE_LOG, STRIPPED_SPRUCE_WOOD -> Material.SPRUCE_PLANKS;
            case BIRCH_LOG, BIRCH_WOOD, STRIPPED_BIRCH_LOG, STRIPPED_BIRCH_WOOD -> Material.BIRCH_PLANKS;
            case JUNGLE_LOG, JUNGLE_WOOD, STRIPPED_JUNGLE_LOG, STRIPPED_JUNGLE_WOOD -> Material.JUNGLE_PLANKS;
            case ACACIA_LOG, ACACIA_WOOD, STRIPPED_ACACIA_LOG, STRIPPED_ACACIA_WOOD -> Material.ACACIA_PLANKS;
            case DARK_OAK_LOG, DARK_OAK_WOOD, STRIPPED_DARK_OAK_LOG, STRIPPED_DARK_OAK_WOOD -> Material.DARK_OAK_PLANKS;
            case MANGROVE_LOG, MANGROVE_WOOD, STRIPPED_MANGROVE_LOG, STRIPPED_MANGROVE_WOOD -> Material.MANGROVE_PLANKS;
            case BAMBOO_BLOCK, STRIPPED_BAMBOO_BLOCK -> Material.BAMBOO_PLANKS;
            case CRIMSON_STEM, CRIMSON_HYPHAE, STRIPPED_CRIMSON_STEM, STRIPPED_CRIMSON_HYPHAE -> Material.CRIMSON_PLANKS;
            case WARPED_STEM, WARPED_HYPHAE, STRIPPED_WARPED_STEM, STRIPPED_WARPED_HYPHAE -> Material.WARPED_PLANKS;
            default -> Material.OAK_PLANKS;
        };
    }

    // ---- カスタムクラフター ---------------------------------------------------

    /** 3x3グリッド: 左3列を各段に割り当て (上段0/1/2, 中段9/10/11, 下段18/19/20) */
    private static final int[] GRID_SLOTS = {0, 1, 2, 9, 10, 11, 18, 19, 20};

    /**
     * CUSTOM_CRAFTER: 3x3グリッドの材料を確認し、
     * 一致するカスタムレシピがあれば自動クラフトする。
     * RS通電で8tickごとにチェック。結果はスロット26を優先。
     */
    private void startCustomCrafterTask() {
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            for (Location loc : new ArrayList<>(machineManager.getByType(MachineType.CUSTOM_CRAFTER))) {
                if (!loc.getChunk().isLoaded()) continue;
                Block block = loc.getBlock();
                if (!block.isBlockPowered() && !block.isBlockIndirectlyPowered()) continue;
                if (!energyManager.tryConsume(loc, MachineType.CUSTOM_CRAFTER)) continue;

                ItemStack[] contents = machineManager.getStoredContents(loc);
                if (contents == null || contents.length < 9) continue;

                // 左3列(0,1,2 / 9,10,11 / 18,19,20)をレシピグリッドとして取り出す
                ItemStack[] grid = new ItemStack[9];
                for (int i = 0; i < 9; i++) grid[i] = contents[GRID_SLOTS[i]];

                CustomCrafterRecipe recipe = CustomCrafterRecipe.match(grid);
                if (recipe == null) continue;

                // 出力スロットの空き確認 (prefer slot 26)
                ItemStack result = recipe.result().clone();
                int outSlot = -1;
                for (int si : new int[]{26, 25, 24, 23, 22, 21, 20, 19, 18, 17, 16, 15, 14, 13, 12, 11, 10, 9}) {
                    if (si >= contents.length) continue;
                    if (contents[si] == null || contents[si].getType().isAir()
                            || (contents[si].isSimilar(result) && contents[si].getAmount() + result.getAmount() <= result.getMaxStackSize())) {
                        outSlot = si;
                        break;
                    }
                }
                if (outSlot < 0) continue;

                CustomCrafterRecipe.consumeAndGet(recipe, grid);
                // 消費結果を元のスロットに書き戻す
                for (int i = 0; i < 9; i++) contents[GRID_SLOTS[i]] = grid[i];
                if (contents[outSlot] == null || contents[outSlot].getType().isAir()) {
                    contents[outSlot] = result;
                } else {
                    contents[outSlot].setAmount(contents[outSlot].getAmount() + result.getAmount());
                }
                machineManager.setStoredContents(loc, contents);
            }
        }, 8L, 8L);
    }

    // ---- 粉砕精錬機 (PULVERIZER: 3倍処理) ----------------------------------------

    /**
     * PULVERIZER: CRUSHERの上位互換。鉱石を3倍の効率で処理する。
     * RS通電で40tickごとに動作。
     * スロット0-25から投入、スロット26に出力優先。
     */
    private void startPulverizerTask() {
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            for (Location loc : new ArrayList<>(machineManager.getByType(MachineType.PULVERIZER))) {
                if (!loc.getChunk().isLoaded()) continue;
                if (!loc.getBlock().isBlockPowered() && !loc.getBlock().isBlockIndirectlyPowered()) continue;
                if (!energyManager.tryConsume(loc, MachineType.PULVERIZER)) continue;

                ItemStack[] contents = machineManager.getStoredContents(loc);
                if (contents == null) continue;
                boolean changed = autoPulverize(contents);
                if (changed) machineManager.setStoredContents(loc, contents);
            }
        }, 10L, 40L);
    }

    private boolean autoPulverize(ItemStack[] contents) {
        for (int i = 0; i < 26; i++) {
            if (isEmpty(contents[i])) continue;
            ItemStack output = pulverizeOre(contents[i].getType());
            if (output == null) continue;
            int out = findAddSlot(contents, output.getType(), 26);
            if (out < 0) continue;
            consumeContentsSlot(contents, i, 1);
            addToContents(contents, out, output);
            return true;
        }
        return false;
    }

    /** PULVERIZER用: 3倍ドロップ + α */
    private ItemStack pulverizeOre(Material input) {
        return switch (input) {
            // 通常鉱石 → 3個
            case COAL_ORE, DEEPSLATE_COAL_ORE        -> new ItemStack(Material.COAL, 3);
            case IRON_ORE, DEEPSLATE_IRON_ORE        -> new ItemStack(Material.RAW_IRON, 3);
            case COPPER_ORE, DEEPSLATE_COPPER_ORE    -> new ItemStack(Material.RAW_COPPER, 3);
            case GOLD_ORE, DEEPSLATE_GOLD_ORE        -> new ItemStack(Material.RAW_GOLD, 3);
            case LAPIS_ORE, DEEPSLATE_LAPIS_ORE      -> new ItemStack(Material.LAPIS_LAZULI, 9);
            case REDSTONE_ORE, DEEPSLATE_REDSTONE_ORE -> new ItemStack(Material.REDSTONE, 9);
            case DIAMOND_ORE, DEEPSLATE_DIAMOND_ORE  -> new ItemStack(Material.DIAMOND, 3);
            case EMERALD_ORE, DEEPSLATE_EMERALD_ORE  -> new ItemStack(Material.EMERALD, 3);
            case NETHER_GOLD_ORE                     -> new ItemStack(Material.GOLD_NUGGET, 6);
            case NETHER_QUARTZ_ORE                   -> new ItemStack(Material.QUARTZ, 6);
            // 追加以太
            case RAW_IRON_BLOCK   -> new ItemStack(Material.RAW_IRON, 27);
            case RAW_GOLD_BLOCK   -> new ItemStack(Material.RAW_GOLD, 27);
            case RAW_COPPER_BLOCK -> new ItemStack(Material.RAW_COPPER, 27);
            default               -> null;
        };
    }

    // ---- 電気精錬炉 (ELECTRIC_FURNACE: 高速精錬) --------------------------------

    /**
     * ELECTRIC_FURNACE: スロット0のアイテムを1tickで精錬。
     * スロット1-25に精錬結果を出力。RS通電で連続稼働。
     * 燃料不要 (電気駆動の設定)。
     */
    private void startElectricFurnaceTask() {
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            for (Location loc : new ArrayList<>(machineManager.getByType(MachineType.ELECTRIC_FURNACE))) {
                if (!loc.getChunk().isLoaded()) continue;
                if (!loc.getBlock().isBlockPowered() && !loc.getBlock().isBlockIndirectlyPowered()) continue;
                if (!energyManager.tryConsume(loc, MachineType.ELECTRIC_FURNACE)) continue;

                ItemStack[] contents = machineManager.getStoredContents(loc);
                if (contents == null || contents.length < 2) continue;
                if (isEmpty(contents[0])) continue;

                ItemStack input = contents[0];
                ItemStack smelted = getSmeltedResult(input.getType());
                if (smelted == null) continue;

                int outSlot = findAddSlot(contents, smelted.getType(), 1);
                if (outSlot < 0) continue;

                consumeContentsSlot(contents, 0, 1);
                addToContents(contents, outSlot, smelted);
                machineManager.setStoredContents(loc, contents);
            }
        }, 2L, 2L);
    }

    /** 精錬結果を返す (バニラかまどレシピの主要なもの) */
    private ItemStack getSmeltedResult(Material input) {
        return switch (input) {
            case RAW_IRON       -> new ItemStack(Material.IRON_INGOT);
            case RAW_GOLD       -> new ItemStack(Material.GOLD_INGOT);
            case RAW_COPPER     -> new ItemStack(Material.COPPER_INGOT);
            case IRON_ORE, DEEPSLATE_IRON_ORE -> new ItemStack(Material.IRON_INGOT);
            case GOLD_ORE, DEEPSLATE_GOLD_ORE -> new ItemStack(Material.GOLD_INGOT);
            case COPPER_ORE, DEEPSLATE_COPPER_ORE -> new ItemStack(Material.COPPER_INGOT);
            case NETHER_GOLD_ORE -> new ItemStack(Material.GOLD_INGOT);
            case ANCIENT_DEBRIS -> new ItemStack(Material.NETHERITE_SCRAP);
            case SAND           -> new ItemStack(Material.GLASS);
            case COBBLESTONE    -> new ItemStack(Material.STONE);
            case STONE          -> new ItemStack(Material.SMOOTH_STONE);
            case CLAY_BALL      -> new ItemStack(Material.TERRACOTTA);
            case NETHERRACK     -> new ItemStack(Material.NETHER_BRICK);
            case CACTUS         -> new ItemStack(Material.GREEN_DYE);
            case CHORUS_FRUIT   -> new ItemStack(Material.POPPED_CHORUS_FRUIT);
            case OAK_LOG, SPRUCE_LOG, BIRCH_LOG, JUNGLE_LOG, ACACIA_LOG, DARK_OAK_LOG,
                 MANGROVE_LOG -> new ItemStack(Material.CHARCOAL);
            default -> null;
        };
    }

    // ---- 自動金床 (AUTO_ANVIL) ------------------------------------------------

    /**
     * AUTO_ANVIL: RS通電でスロット0のアイテムをスロット1の素材で修復する。
     * 素材が同名アイテムなら結合、鉱石/エンチャ本なら修復(エンチャ維持)。
     */
    private void startAutoAnvilTask() {
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            for (Location loc : new ArrayList<>(machineManager.getByType(MachineType.AUTO_ANVIL))) {
                if (!loc.getChunk().isLoaded()) continue;
                if (!loc.getBlock().isBlockPowered() && !loc.getBlock().isBlockIndirectlyPowered()) continue;
                if (!energyManager.tryConsume(loc, MachineType.AUTO_ANVIL)) continue;

                ItemStack[] contents = machineManager.getStoredContents(loc);
                if (contents == null || contents.length < 2) continue;
                if (isEmpty(contents[0]) || isEmpty(contents[1])) continue;

                ItemStack tool = contents[0];
                ItemStack material = contents[1];

                // 同じ素材なら結合 (ダメージ軽減)
                if (tool.getType() == material.getType()
                        && tool.getItemMeta().getClass() == material.getItemMeta().getClass()) {
                    int totalDmg = getDamage(tool) + getDamage(material);
                    int totalCount = tool.getAmount() + material.getAmount();
                    int maxDmg = tool.getType().getMaxDurability();
                    int avgDmg = totalDmg / totalCount;

                    ItemStack merged = tool.clone();
                    merged.setAmount(1);
                    setDamage(merged, Math.min(avgDmg, maxDmg - 1));
                    if (totalCount > 1) merged.setAmount(totalCount - 1);

                    contents[0] = merged;
                    consumeContentsSlot(contents, 1, 1);
                    machineManager.setStoredContents(loc, contents);
                    continue;
                }

                // エンチャ本 → ツールに適用
                if (material.getType() == Material.ENCHANTED_BOOK
                        && tool.getItemMeta() instanceof org.bukkit.inventory.meta.Damageable
                        && material.getItemMeta() instanceof org.bukkit.inventory.meta.EnchantmentStorageMeta esm) {
                    ItemMeta toolMeta = tool.getItemMeta();
                    for (var entry : esm.getStoredEnchants().entrySet()) {
                        toolMeta.addEnchant(entry.getKey(), entry.getValue(), true);
                    }
                    tool.setItemMeta(toolMeta);
                    consumeContentsSlot(contents, 1, 1);
                    machineManager.setStoredContents(loc, contents);
                    continue;
                }

                // エンチャ本 + エンチャ本 → 合成
                if (tool.getType() == Material.ENCHANTED_BOOK && material.getType() == Material.ENCHANTED_BOOK
                        && tool.getItemMeta() instanceof org.bukkit.inventory.meta.EnchantmentStorageMeta tEsm
                        && material.getItemMeta() instanceof org.bukkit.inventory.meta.EnchantmentStorageMeta mEsm) {
                    for (var entry : mEsm.getStoredEnchants().entrySet()) {
                        tEsm.addStoredEnchant(entry.getKey(), entry.getValue(), true);
                    }
                    tool.setItemMeta((ItemMeta) tEsm);
                    consumeContentsSlot(contents, 1, 1);
                    machineManager.setStoredContents(loc, contents);
                    continue;
                }

                // 修復素材 (鉄/金/ダイヤ/ネザライト ingot)
                boolean repairable = isRepairMaterialFor(tool.getType(), material.getType());
                if (repairable) {
                    int dmg = getDamage(tool);
                    if (dmg <= 0) continue;
                    int repair = tool.getType().getMaxDurability() / 4;
                    int newDmg = Math.max(0, dmg - repair);
                    setDamage(tool, newDmg);
                    consumeContentsSlot(contents, 1, 1);
                    machineManager.setStoredContents(loc, contents);
                }
            }
        }, 4L, 4L);
    }

    private int getDamage(ItemStack item) {
        if (!(item.getItemMeta() instanceof org.bukkit.inventory.meta.Damageable d)) return 0;
        return d.getDamage();
    }

    private void setDamage(ItemStack item, int damage) {
        if (item.getItemMeta() instanceof org.bukkit.inventory.meta.Damageable d) {
            d.setDamage(damage);
            item.setItemMeta((ItemMeta) d);
        }
    }

    private boolean isRepairMaterialFor(Material tool, Material mat) {
        String t = tool.name();
        String m = mat.name();
        if (t.startsWith("IRON_") || t.startsWith("CHAINMAIL_")) return m.equals("IRON_INGOT");
        if (t.startsWith("GOLDEN_") || t.startsWith("GOLD_")) return m.equals("GOLD_INGOT");
        if (t.startsWith("DIAMOND_")) return m.equals("DIAMOND");
        if (t.startsWith("NETHERITE_")) return m.equals("NETHERITE_INGOT");
        if (t.startsWith("TURTLE_")) return m.equals("SCUTE");
        if (t.startsWith("LEATHER_")) return m.equals("LEATHER");
        if (t.startsWith("WOODEN_")) return m.equals("OAK_PLANKS");
        if (t.startsWith("STONE_")) return m.equals("COBBLESTONE");
        return false;
    }

    /** アイテムを配列に追加（既存スタックへ積み上げ→空スロット）。追加できた量を返す */
    private int addItemToArray(ItemStack[] arr, ItemStack item) {
        int remaining = item.getAmount();
        int size = arr.length;
        // スタック可能スロットに積む
        for (int i = 0; i < size && remaining > 0; i++) {
            if (arr[i] != null && !arr[i].getType().isAir()
                    && arr[i].isSimilar(item) && arr[i].getAmount() < arr[i].getMaxStackSize()) {
                int take = Math.min(remaining, arr[i].getMaxStackSize() - arr[i].getAmount());
                arr[i].setAmount(arr[i].getAmount() + take);
                remaining -= take;
            }
        }
        // 空スロットに置く
        for (int i = 0; i < size && remaining > 0; i++) {
            if (arr[i] == null || arr[i].getType().isAir()) {
                arr[i] = item.clone();
                arr[i].setAmount(remaining);
                remaining = 0;
            }
        }
        return item.getAmount() - remaining;
    }

    // ---- ストレージコントローラー ヘルパー ------------------------------------

    /** コントローラー開放時: 周囲ドラムからアイテムをコントローラーへ引き込んでGUI生成 */
    /** ストレージコントローラーを開く (仮想ビュー) */
    private Inventory openStorageControllerGui(Location loc) {
        MachineInvHolder holder = new MachineInvHolder(loc, MachineType.STORAGE_CONTROLLER);
        Inventory gui = plugin.getServer().createInventory(holder, 54,
                Component.text(GUI_PREFIX + "ストレージコントローラー"));
        holder.setInventory(gui);
        refreshControllerGui(gui, loc);
        return gui;
    }

    /** コントローラーGUIを現在のドラム内容で再描画する */
    private void refreshControllerGui(Inventory gui, Location ctrlLoc) {
        int page = (gui.getHolder() instanceof MachineInvHolder h) ? h.page : 0;

        // ドラムから全アイテムを収集し、isSimilarでグルーピング
        List<ItemStack> templates = new ArrayList<>();
        List<Integer> totals     = new ArrayList<>();

        for (Location drumLoc : findNearbyDrums(ctrlLoc)) {
            ItemStack[] drum = machineManager.getStoredContents(drumLoc);
            if (drum == null) continue;
            for (ItemStack is : drum) {
                if (is == null || is.getType().isAir() || is.getAmount() <= 0) continue;
                boolean found = false;
                for (int i = 0; i < templates.size(); i++) {
                    if (templates.get(i).isSimilar(is)) {
                        totals.set(i, totals.get(i) + is.getAmount());
                        found = true; break;
                    }
                }
                if (!found) { templates.add(is.clone()); totals.add(is.getAmount()); }
            }
        }

        // テンプレートキャッシュを更新 (クリック時のisSimilar比較用)
        controllerTemplates.put(machineManager.locKey(ctrlLoc), new ArrayList<>(templates));

        int itemsPerPage = 45;
        int start   = page * itemsPerPage;
        int maxPage = templates.isEmpty() ? 0 : (templates.size() - 1) / itemsPerPage;

        // アイテムスロットをクリアして再描画
        for (int i = 0; i < 45; i++) gui.setItem(i, null);
        for (int i = 0; i < itemsPerPage && (start + i) < templates.size(); i++) {
            ItemStack disp = templates.get(start + i).clone();
            int total = totals.get(start + i);
            disp.setAmount(Math.min(disp.getMaxStackSize(), total));
            ItemMeta meta = disp.getItemMeta();
            if (meta != null) {
                List<net.kyori.adventure.text.Component> lore = new ArrayList<>(
                        meta.lore() != null ? meta.lore() : List.of());
                lore.add(net.kyori.adventure.text.Component.text("合計: " + total + "個",
                        net.kyori.adventure.text.format.NamedTextColor.YELLOW)
                        .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
                lore.add(net.kyori.adventure.text.Component.text("左:32個 右:16個 Shift:全部",
                        net.kyori.adventure.text.format.NamedTextColor.GRAY)
                        .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
                meta.lore(lore);
                disp.setItemMeta(meta);
            }
            gui.setItem(i, disp);
        }

        // ナビゲーション行 (スロット45-53)
        ItemStack gray = makePane(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 45; i < 54; i++) gui.setItem(i, gray.clone());

        if (page > 0) {
            ItemStack prev = new ItemStack(Material.ARROW);
            ItemMeta pm = prev.getItemMeta();
            if (pm != null) { pm.displayName(net.kyori.adventure.text.Component.text("← 前のページ",
                    net.kyori.adventure.text.format.NamedTextColor.AQUA)
                    .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false)); prev.setItemMeta(pm); }
            gui.setItem(45, prev);
        }
        ItemStack info = new ItemStack(Material.PAPER);
        ItemMeta im = info.getItemMeta();
        if (im != null) {
            im.displayName(net.kyori.adventure.text.Component.text(
                    (page + 1) + " / " + (maxPage + 1) + " ページ (" + templates.size() + "種)",
                    net.kyori.adventure.text.format.NamedTextColor.WHITE)
                    .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
            info.setItemMeta(im);
        }
        gui.setItem(49, info);

        if (page < maxPage) {
            ItemStack next = new ItemStack(Material.ARROW);
            ItemMeta nm = next.getItemMeta();
            if (nm != null) { nm.displayName(net.kyori.adventure.text.Component.text("→ 次のページ",
                    net.kyori.adventure.text.format.NamedTextColor.AQUA)
                    .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false)); next.setItemMeta(nm); }
            gui.setItem(53, next);
        }
    }

    private int getControllerMaxPage(Location ctrlLoc) {
        int typeCount = 0;
        List<ItemStack> seen = new ArrayList<>();
        for (Location drumLoc : findNearbyDrums(ctrlLoc)) {
            ItemStack[] drum = machineManager.getStoredContents(drumLoc);
            if (drum == null) continue;
            for (ItemStack is : drum) {
                if (is == null || is.getType().isAir()) continue;
                boolean found = false;
                for (ItemStack s : seen) if (s.isSimilar(is)) { found = true; break; }
                if (!found) { seen.add(is.clone()); typeCount++; }
            }
        }
        return Math.max(0, (typeCount - 1) / 45);
    }

    private int getTotalInDrums(Location ctrlLoc, ItemStack template) {
        int total = 0;
        for (Location drumLoc : findNearbyDrums(ctrlLoc)) {
            ItemStack[] drum = machineManager.getStoredContents(drumLoc);
            if (drum == null) continue;
            for (ItemStack is : drum)
                if (is != null && is.isSimilar(template)) total += is.getAmount();
        }
        return total;
    }

    private ItemStack pullFromDrums(Location ctrlLoc, ItemStack template, int amount) {
        int remaining = amount;
        ItemStack result = null;
        for (Location drumLoc : findNearbyDrums(ctrlLoc)) {
            ItemStack[] drum = machineManager.getStoredContents(drumLoc);
            if (drum == null) continue;
            boolean changed = false;
            for (int i = 0; i < drum.length && remaining > 0; i++) {
                if (drum[i] == null || !drum[i].isSimilar(template)) continue;
                int take = Math.min(remaining, drum[i].getAmount());
                if (result == null) { result = drum[i].clone(); result.setAmount(take); }
                else result.setAmount(result.getAmount() + take);
                remaining -= take;
                drum[i].setAmount(drum[i].getAmount() - take);
                if (drum[i].getAmount() <= 0) drum[i] = null;
                changed = true;
            }
            if (changed) machineManager.setStoredContents(drumLoc, drum);
            if (remaining <= 0) break;
        }
        return result;
    }

    private void pushItemToDrums(Location ctrlLoc, ItemStack item) {
        ItemStack copy = item.clone();
        for (Location drumLoc : findNearbyDrums(ctrlLoc)) {
            if (copy.getAmount() <= 0) break;
            ItemStack[] drum = machineManager.getStoredContents(drumLoc);
            if (drum == null) continue;
            int moved = addItemToArray(drum, copy);
            if (moved > 0) {
                copy.setAmount(copy.getAmount() - moved);
                machineManager.setStoredContents(drumLoc, drum);
            }
        }
    }

    /** コントローラー閉鎖時: 余剰アイテムを周囲ドラムへプッシュ */
    private void pushToDrums(Location ctrlLoc, ItemStack[] saved) {
        for (Location drumLoc : findNearbyDrums(ctrlLoc)) {
            ItemStack[] drum = machineManager.getStoredContents(drumLoc);
            if (drum == null) drum = new ItemStack[54];
            boolean changed = false;
            for (int i = 0; i < saved.length; i++) {
                if (saved[i] == null || saved[i].getType().isAir()) continue;
                int moved = addItemToArray(drum, saved[i]);
                if (moved > 0) {
                    int rem = saved[i].getAmount() - moved;
                    saved[i] = rem <= 0 ? null : saved[i].clone();
                    if (saved[i] != null) saved[i].setAmount(rem);
                    changed = true;
                }
            }
            if (changed) machineManager.setStoredContents(drumLoc, drum);
        }
    }

    /** 半径8ブロック内のSTORAGE_DRUM座標リストを返す */
    private List<Location> findNearbyDrums(Location center) {
        List<Location> result = new ArrayList<>();
        for (int dx = -8; dx <= 8; dx++)
            for (int dy = -8; dy <= 8; dy++)
                for (int dz = -8; dz <= 8; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) continue;
                    Location check = center.clone().add(dx, dy, dz).toBlockLocation();
                    if (machineManager.isMachine(check)
                            && machineManager.getType(check) == MachineType.STORAGE_DRUM)
                        result.add(check);
                }
        return result;
    }

    // ---- GUI生成 (ITEM_ROUTER) ------------------------------------------------

    private Inventory createRouterGui(Location loc) {
        ItemStack[] saved = machineManager.getStoredContents(loc);
        MachineInvHolder holder = new MachineInvHolder(loc, MachineType.ITEM_ROUTER);
        Inventory gui = plugin.getServer().createInventory(holder, 27,
                Component.text(GUI_PREFIX + "アイテムルーター"));
        holder.setInventory(gui);

        // スロット0-3にフィルター枠(説明パネル)を設定、アイテムがなければ灰色パネル
        String[] dirLabels = {"北(N)フィルター", "南(S)フィルター", "東(E)フィルター", "西(W)フィルター"};
        for (int i = 0; i < 4; i++) {
            if (saved != null && saved[i] != null && !saved[i].getType().isAir()) {
                gui.setItem(i, saved[i].clone());
            } else {
                gui.setItem(i, makePane(Material.GRAY_STAINED_GLASS_PANE, dirLabels[i]));
            }
        }
        // バッファスロット 4-26
        if (saved != null) {
            for (int i = 4; i < 27; i++) {
                if (saved[i] != null && !saved[i].getType().isAir()) gui.setItem(i, saved[i].clone());
            }
        }
        return gui;
    }

    // ---- チャンクローダー 初期化 (CHUNK_LOADER) ----------------------------------

    private void initChunkLoaders() {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            for (Location loc : machineManager.getByType(MachineType.CHUNK_LOADER)) {
                {
                    loc.getChunk().setForceLoaded(true);
                }
            }
        });
    }

    private void startHopperSimulationTask() {
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            for (Map.Entry<Location, MachineType> e : new HashMap<>(machineManager.getAll()).entrySet()) {
                Location loc = e.getKey();
                MachineType type = e.getValue();

                // BLOCK_PLACER はバレル実インベントリなのでスキップ
                if (!MachineManager.hasMachineInventory(type)) continue;
                ItemStack[] contents = machineManager.getStoredContents(loc);
                if (contents == null) continue;

                // ── 搬入: ホッパー→マシン ──────────────────────────────────
                // マシンを向いているホッパー (UP/N/S/E/W から)
                for (BlockFace face : new BlockFace[]{
                        BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST,
                        BlockFace.WEST, BlockFace.UP}) {
                    Block adj = loc.getBlock().getRelative(face);
                    if (adj.getType() != Material.HOPPER) continue;
                    if (!(adj.getBlockData() instanceof org.bukkit.block.data.type.Hopper hd)) continue;
                    // ホッパーの出力方向がマシン側を向いているか
                    if (hd.getFacing() != face.getOppositeFace()) continue;
                    if (!(adj.getState() instanceof org.bukkit.block.Hopper hs)) continue;

                    transferHopperToMachine(hs.getInventory(), contents, getInputSlotRange(type), type);
                    machineManager.setStoredContents(loc, contents);
                    break; // 1ホッパーずつ
                }

                // ── 搬出: マシン→ホッパー ──────────────────────────────────
                int inputRange = getInputSlotRange(type);
                int outSlot = -1;
                if (inputRange >= 27) {
                    // 全スロットが入力 → ホッパー出力なし (機械タスクが内部処理)
                    outSlot = -1;
                } else if (inputRange == 26) {
                    // スロット0-25が入力 → スロット26のみ出力
                    if (contents.length > 26 && contents[26] != null && !contents[26].getType().isAir())
                        outSlot = 26;
                } else {
                    // 入力範囲外のスロットから出力を探す
                    for (int i = inputRange; i < 27; i++) {
                        if (contents[i] != null && !contents[i].getType().isAir()) { outSlot = i; break; }
                    }
                }
                if (outSlot < 0) continue;

                // 真下のホッパーにのみ搬出 (横向きホッパーへの意図せぬ搬出を防止)
                Block below = loc.getBlock().getRelative(BlockFace.DOWN);
                if (below.getType() == Material.HOPPER
                        && below.getState() instanceof org.bukkit.block.Hopper belowHopper) {
                    transferMachineToHopper(contents, outSlot, belowHopper.getInventory());
                    machineManager.setStoredContents(loc, contents);
                }
            }
        }, 4L, 4L);
    }

    /** ホッパーから1個マシン入力スロットへ移動 */
    private void transferHopperToMachine(Inventory hopperInv, ItemStack[] contents, int inputMax, MachineType type) {
        for (int hi = 0; hi < hopperInv.getSize(); hi++) {
            ItemStack from = hopperInv.getItem(hi);
            if (from == null || from.getType().isAir()) continue;

            // 洗浄機: WATER_BUCKETはslot 1優先、それ以外はslot 0
            if (type == MachineType.WASHING_MACHINE && inputMax >= 2) {
                int targetSlot = (from.getType() == Material.WATER_BUCKET) ? 1 : 0;
                if (contents[targetSlot] == null || contents[targetSlot].getType().isAir()) {
                    ItemStack one = from.clone();
                    one.setAmount(1);
                    contents[targetSlot] = one;
                    reduceHopper(hopperInv, hi, from, 1);
                    return;
                } else if (contents[targetSlot].isSimilar(from)
                        && contents[targetSlot].getAmount() < contents[targetSlot].getMaxStackSize()) {
                    contents[targetSlot].setAmount(contents[targetSlot].getAmount() + 1);
                    reduceHopper(hopperInv, hi, from, 1);
                    return;
                }
                // 指定スロットが埋まってたらもう一方を試す
                int fallback = (targetSlot == 0) ? 1 : 0;
                if (contents[fallback] == null || contents[fallback].getType().isAir()) {
                    ItemStack one = from.clone();
                    one.setAmount(1);
                    contents[fallback] = one;
                    reduceHopper(hopperInv, hi, from, 1);
                    return;
                } else if (contents[fallback].isSimilar(from)
                        && contents[fallback].getAmount() < contents[fallback].getMaxStackSize()) {
                    contents[fallback].setAmount(contents[fallback].getAmount() + 1);
                    reduceHopper(hopperInv, hi, from, 1);
                    return;
                }
                return; // 両方満杯
            }

            for (int mi = 0; mi < inputMax; mi++) {
                if (contents[mi] == null || contents[mi].getType().isAir()) {
                    // NBTを保持してクローン (new ItemStack(type,1) はNBTを失う)
                    ItemStack one = from.clone();
                    one.setAmount(1);
                    contents[mi] = one;
                    reduceHopper(hopperInv, hi, from, 1);
                    return;
                } else if (contents[mi].isSimilar(from)
                        && contents[mi].getAmount() < contents[mi].getMaxStackSize()) {
                    contents[mi].setAmount(contents[mi].getAmount() + 1);
                    reduceHopper(hopperInv, hi, from, 1);
                    return;
                }
            }
            return; // マシン側が満杯
        }
    }

    /** マシン出力スロットから1個ホッパーへ移動 */
    private void transferMachineToHopper(ItemStack[] contents, int outSlot, Inventory hopperInv) {
        ItemStack out = contents[outSlot];
        if (out == null || out.getType().isAir()) return;
        // NBTを保持してクローン
        ItemStack one = out.clone();
        one.setAmount(1);
        Map<Integer, ItemStack> leftover = hopperInv.addItem(one);
        if (leftover.isEmpty()) {
            if (out.getAmount() <= 1) contents[outSlot] = null;
            else out.setAmount(out.getAmount() - 1);
        }
    }

    private void reduceHopper(Inventory inv, int slot, ItemStack item, int amount) {
        if (item.getAmount() <= amount) {
            inv.setItem(slot, null);
        } else {
            // NBTを保持してクローン
            ItemStack reduced = item.clone();
            reduced.setAmount(item.getAmount() - amount);
            inv.setItem(slot, reduced);
        }
    }

    /** マシン種別ごとの入力スロット上限 (0 〜 N-1 がバッファ) */
    private int getInputSlotRange(MachineType type) {
        return switch (type) {
            case AUTO_CRAFTER -> 18;           // スロット9-17が素材バッファ
            case STORAGE_DRUM -> 54;           // 54スロット全入力
            case ITEM_SORTER, AUTO_FARMER,
                 MINER, VACUUM_HOPPER,
                 BONE_MEALER -> 27;
             case ITEM_ROUTER -> 4;
            case CRUSHER, COMPRESSOR -> 26;    // スロット0-25が入力、26が出力
            case AUTO_BREWER -> 25;            // スロット0-24がポーション、25=材料, 26=燃料
            case FLUID_COLLECTOR -> 1;         // スロット0=空バケツ入力
            case PIXEL_FORGE -> 2;             // スロット0=武器, 1=素材
            case AUTO_ENCHANTER -> 27;         // スロット0=ツール, 1-26=ラピス/バッファ
            case VOID_MINER -> 0;              // 入力なし、全スロット出力用
            case INDUCTION_FURNACE -> 3;    // スロット0-2が入力
            case CENTRIFUGE -> 1;           // スロット0のみ入力
            case COMBUSTION_GENERATOR -> 1; // スロット0のみ燃料
            case ORE_PROCESSOR -> 1;        // スロット0のみ入力
            case MATERIALIZER -> 1;        // スロット0のみ入力
            case RECYCLER -> 1;            // スロット0のみ入力
            case AUTO_DISENCHANTER -> 1;   // スロット0のみ入力
            case ADVANCED_ASSEMBLER -> 26;   // スロット0-25=入力(グリッド+バッファ), 26=出力
            case NEUTRON_COMPRESSOR -> 1;    // スロット0のみ入力
            case WASHING_MACHINE -> 2;       // スロット0=素材, 1=水バケツ
            case CRAFTER_CONTROLLER -> 54;     // 全スロット公開
            default -> 1;                      // スロット0のみ入力
        };
    }

    /**
     * プレイヤーがGUIを操作した後、次tickでGUI→machineContentsを同期する。
     * openGuisに登録されている機械のみ対象。
     * getStoredContentsがliveInventoriesから直接読み取るため、
     * このメソッドはmachineContentsのバックアップ更新のみを担う。
     */
    private void scheduleGuiSync(Location loc) {
        Location key = loc.toBlockLocation();
        if (!openGuis.containsKey(key)) return;
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            Inventory gui = openGuis.get(key);
            if (gui == null) return;
            int size = gui.getSize();
            ItemStack[] snap = new ItemStack[size];
            for (int i = 0; i < size; i++) {
                ItemStack it = gui.getItem(i);
                if (it != null && it.hasItemMeta()
                        && it.getItemMeta().getPersistentDataContainer()
                            .has(org.bukkit.NamespacedKey.fromString("circuitsurvival:decoration"),
                                    org.bukkit.persistence.PersistentDataType.BOOLEAN)) {
                    snap[i] = null; // 装飾パネル/ボタン類は保存しない
                } else {
                    snap[i] = it != null ? it.clone() : null;
                }
            }
            machineManager.syncContentsFromGui(key, snap);
        });
    }

    private ItemStack makePane(Material mat, String name) {
        ItemStack pane = new ItemStack(mat);
        ItemMeta m = pane.getItemMeta();
        if (m != null) {
            m.displayName(Component.text(name));
            m.getPersistentDataContainer().set(
                    org.bukkit.NamespacedKey.fromString("circuitsurvival:decoration"),
                    org.bukkit.persistence.PersistentDataType.BOOLEAN, true);
            pane.setItemMeta(m);
        }
        return pane;
    }

    // ==========================================================================
    // 粉砕 / 圧縮レシピ
    // ==========================================================================

    private static final Map<Material, Material> ORE_CRUSH_MAP = new EnumMap<>(Material.class);
    static {
        ORE_CRUSH_MAP.put(Material.IRON_ORE,               Material.RAW_IRON);
        ORE_CRUSH_MAP.put(Material.DEEPSLATE_IRON_ORE,     Material.RAW_IRON);
        ORE_CRUSH_MAP.put(Material.GOLD_ORE,               Material.RAW_GOLD);
        ORE_CRUSH_MAP.put(Material.DEEPSLATE_GOLD_ORE,     Material.RAW_GOLD);
        ORE_CRUSH_MAP.put(Material.COPPER_ORE,             Material.RAW_COPPER);
        ORE_CRUSH_MAP.put(Material.DEEPSLATE_COPPER_ORE,   Material.RAW_COPPER);
        ORE_CRUSH_MAP.put(Material.REDSTONE_ORE,           Material.REDSTONE);
        ORE_CRUSH_MAP.put(Material.DEEPSLATE_REDSTONE_ORE, Material.REDSTONE);
        ORE_CRUSH_MAP.put(Material.LAPIS_ORE,              Material.LAPIS_LAZULI);
        ORE_CRUSH_MAP.put(Material.DEEPSLATE_LAPIS_ORE,    Material.LAPIS_LAZULI);
        ORE_CRUSH_MAP.put(Material.DIAMOND_ORE,            Material.DIAMOND);
        ORE_CRUSH_MAP.put(Material.DEEPSLATE_DIAMOND_ORE,  Material.DIAMOND);
        ORE_CRUSH_MAP.put(Material.EMERALD_ORE,            Material.EMERALD);
        ORE_CRUSH_MAP.put(Material.DEEPSLATE_EMERALD_ORE,  Material.EMERALD);
        ORE_CRUSH_MAP.put(Material.COAL_ORE,               Material.COAL);
        ORE_CRUSH_MAP.put(Material.DEEPSLATE_COAL_ORE,     Material.COAL);
        ORE_CRUSH_MAP.put(Material.NETHER_QUARTZ_ORE,      Material.QUARTZ);
        ORE_CRUSH_MAP.put(Material.NETHER_GOLD_ORE,        Material.GOLD_NUGGET);
        ORE_CRUSH_MAP.put(Material.GLOWSTONE,              Material.GLOWSTONE_DUST);
        ORE_CRUSH_MAP.put(Material.QUARTZ_BLOCK,           Material.QUARTZ);
        ORE_CRUSH_MAP.put(Material.RAW_IRON_BLOCK,         Material.RAW_IRON);
        ORE_CRUSH_MAP.put(Material.RAW_GOLD_BLOCK,         Material.RAW_GOLD);
        ORE_CRUSH_MAP.put(Material.RAW_COPPER_BLOCK,       Material.RAW_COPPER);
        // (石材→鉄ナゲットは洗浄機に移行)
    }

    // ---- 洗浄機レシピ ----------------------------------------------------------

    private static final Map<Material, WashResult> WASH_MAP = new EnumMap<>(Material.class);
    static {
        WASH_MAP.put(Material.GRAVEL,   new WashResult(Material.IRON_NUGGET,  20)); // 20%
        WASH_MAP.put(Material.COBBLESTONE, new WashResult(Material.IRON_NUGGET, 10)); // 10%
        WASH_MAP.put(Material.SAND,     new WashResult(Material.CLAY_BALL,    30)); // 30%
        WASH_MAP.put(Material.RED_SAND, new WashResult(Material.GOLD_NUGGET,  15)); // 15%
        WASH_MAP.put(Material.SOUL_SAND,new WashResult(Material.QUARTZ,       20)); // 20%
    }
    private record WashResult(Material output, int chancePercent) {}

    private ItemStack crushOre(Material m) {
        // Chain 1: 粉砕物 → 中間素材
        if (m == Material.IRON_ORE || m == Material.DEEPSLATE_IRON_ORE)
            return IntermediateMaterials.build(IntermediateMaterials.CRUSHED_IRON, 3);
        if (m == Material.GOLD_ORE || m == Material.DEEPSLATE_GOLD_ORE)
            return IntermediateMaterials.build(IntermediateMaterials.CRUSHED_GOLD, 3);
        if (m == Material.COAL_ORE || m == Material.DEEPSLATE_COAL_ORE)
            return IntermediateMaterials.build(IntermediateMaterials.CRUSHED_COAL, 2);
        if (m == Material.COBBLESTONE || m == Material.STONE || m == Material.COBBLED_DEEPSLATE)
            return new ItemStack(Material.GRAVEL, 2);
        if (m == Material.NETHERRACK)
            return IntermediateMaterials.build(IntermediateMaterials.CRUSHED_NETHERRACK, 2);
        Material out = ORE_CRUSH_MAP.get(m);
        if (out == null) return null;
        int amt = switch (m) {
            case REDSTONE_ORE, DEEPSLATE_REDSTONE_ORE -> 8;
            case LAPIS_ORE, DEEPSLATE_LAPIS_ORE       -> 9;
            case NETHER_GOLD_ORE                       -> 6;
            case GLOWSTONE                             -> 4;
            case RAW_IRON_BLOCK, RAW_GOLD_BLOCK, RAW_COPPER_BLOCK -> 9;
            default -> 2;
        };
        return new ItemStack(out, amt);
    }

    private ItemStack compress(ItemStack input) {
        Material mat = input.getType();
        // Chain 1: インゴット→圧縮ブロック (中間素材)
        if (mat == Material.IRON_INGOT)
            return IntermediateMaterials.build(IntermediateMaterials.COMPRESSED_IRON, 1);
        if (mat == Material.GOLD_INGOT)
            return IntermediateMaterials.build(IntermediateMaterials.COMPRESSED_GOLD, 1);
        Map<Material, Material> toBlock = Map.of(
                Material.REDSTONE,       Material.REDSTONE_BLOCK,
                Material.GLOWSTONE_DUST, Material.GLOWSTONE,
                Material.DIAMOND,        Material.DIAMOND_BLOCK,
                Material.EMERALD,        Material.EMERALD_BLOCK,
                Material.LAPIS_LAZULI,   Material.LAPIS_BLOCK,
                Material.COAL,           Material.COAL_BLOCK,
                Material.QUARTZ,         Material.QUARTZ_BLOCK
        );
        Map<Material, Material> fromBlock = new HashMap<>();
        toBlock.forEach((k, v) -> fromBlock.put(v, k));
        if (toBlock.containsKey(mat))   return new ItemStack(toBlock.get(mat), 1);
        if (fromBlock.containsKey(mat)) return new ItemStack(fromBlock.get(mat), 9);
        return null;
    }

    // ==========================================================================
    // ユーティリティ
    // ==========================================================================

    /** 指定機械に対して機器制御アタッチメントによるオーバーライド方向があれば返す */
    private BlockFace getControlOverride(String machineKey) {
        return controlAttachmentOverrides.get(machineKey);
    }

    /** BLOCK_CONTROL_ATTACHMENT の向き（隣接機械がある方向）を返す */
    private BlockFace getControlFacing(Block controlBlock) {
        Location loc = controlBlock.getLocation();
        for (BlockFace face : ADJACENT_FACES) {
            Location adj = loc.clone().add(face.getDirection());
            if (machineManager.isMachine(adj)) {
                MachineType t = machineManager.getType(adj);
                if (t != null && t != MachineType.BLOCK_CONTROL_ATTACHMENT) {
                    return face;
                }
            }
        }
        return null;
    }

    /** BLOCK_CONTROL_ATTACHMENT 設置時に隣接機械を検出してキャッシュに登録 */
    private void registerControlOverride(Location controlLoc) {
        for (BlockFace face : ADJACENT_FACES) {
            Location adj = controlLoc.clone().add(face.getDirection());
            String adjKey = locKey(adj);
            if (machineManager.isMachine(adj)) {
                MachineType t = machineManager.getType(adj);
                if (t != null && t != MachineType.BLOCK_CONTROL_ATTACHMENT) {
                    controlAttachmentOverrides.put(adjKey, face);
                    return;
                }
            }
        }
    }

    private BlockFace getMachineFacing(Location loc) {
        BlockData data = loc.getBlock().getBlockData();
        return (data instanceof Directional dir) ? dir.getFacing() : BlockFace.SOUTH;
    }

    private void applyFacing(Block block, BlockFace facing) {
        BlockData data = block.getBlockData();
        if (data instanceof Stairs stair) {
            if (facing.getModY() == 0) stair.setFacing(facing);
            block.setBlockData(stair);
        } else if (data instanceof Directional dir) {
            dir.setFacing(facing);
            block.setBlockData(dir);
        }
    }

    private boolean isReplaceable(Material m) {
        return m == Material.AIR || m == Material.CAVE_AIR || m == Material.VOID_AIR
                || m == Material.WATER || m == Material.LAVA
                || m == Material.GRASS || m == Material.TALL_GRASS
                || m == Material.FERN || m == Material.LARGE_FERN
                || m == Material.DEAD_BUSH
                || m == Material.VINE || m == Material.SNOW;
    }

    private boolean isSapling(Material m) {
        return m == Material.OAK_SAPLING || m == Material.SPRUCE_SAPLING
                || m == Material.BIRCH_SAPLING || m == Material.JUNGLE_SAPLING
                || m == Material.ACACIA_SAPLING || m == Material.DARK_OAK_SAPLING
                || m == Material.MANGROVE_PROPAGULE
                || m == Material.BAMBOO;
    }

    private boolean isEmpty(ItemStack s) { return s == null || s.getType().isAir() || s.getAmount() <= 0; }

    private Component err(String msg) { return Component.text(msg, NamedTextColor.RED); }

    private boolean mergeOutput(Inventory inv, int slot, ItemStack output) {
        ItemStack cur = inv.getItem(slot);
        int existing = (cur != null && cur.getType() == output.getType()) ? cur.getAmount() : 0;
        if (cur != null && !cur.getType().isAir() && cur.getType() != output.getType()) return false;
        int total = output.getAmount() + existing;
        if (total > output.getMaxStackSize()) return false;
        output.setAmount(total);
        inv.setItem(slot, output);
        return true;
    }

    private void consumeInput(Inventory inv, int slot, int amount) {
        ItemStack item = inv.getItem(slot);
        if (item == null) return;
        if (item.getAmount() <= amount) inv.setItem(slot, null);
        else item.setAmount(item.getAmount() - amount);
    }

    /** Inventory の特定スロットを指定量消費 */
    private void consumeInventorySlot(Inventory inv, int slot, int amount) {
        ItemStack item = inv.getItem(slot);
        if (item == null || item.getType().isAir()) return;
        if (item.getAmount() <= amount) inv.setItem(slot, null);
        else {
            ItemStack clone = item.clone();
            clone.setAmount(item.getAmount() - amount);
            inv.setItem(slot, clone);
        }
    }

    private String locKey(Block b)      { return locKey(b.getLocation()); }
    private String locKey(Location loc) {
        return loc.getWorld().getName() + ":" + loc.getBlockX() + ":" + loc.getBlockY() + ":" + loc.getBlockZ();
    }

    private String facingName(BlockFace f) {
        return switch (f) {
            case NORTH -> "北"; case SOUTH -> "南";
            case EAST  -> "東"; case WEST  -> "西";
            case UP    -> "上"; case DOWN  -> "下";
            default    -> f.name();
        };
    }

    /**
     * バニラ BlockInventory から座標を取得する (InventoryMoveItemEvent 用)
     */
    private Location getInventoryBlockLocation(Inventory inv) {
        if (inv.getHolder() instanceof org.bukkit.block.BlockState state) {
            return state.getLocation();
        }
        return null;
    }

    // ---- ENERGY_CELL GUI (EN表示) --------------------------------------------

    private Inventory createEnergyCellGui(Location loc) {
        MachineInvHolder holder = new MachineInvHolder(loc, MachineType.ENERGY_CELL);
        Inventory gui = plugin.getServer().createInventory(holder, 27,
                Component.text(GUI_PREFIX + "蓄電機"));
        holder.setInventory(gui);

        int cur = energyManager.getEnergy(loc);
        int max = energyManager.getMaxEnergy(loc);

        ItemStack display = new ItemStack(Material.LAPIS_LAZULI);
        ItemMeta meta = display.getItemMeta();
        meta.displayName(Component.text("§bEN: " + cur + " / " + max));
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("§7蓄電量が50%を超えると隣接機械に自動分配"));
        lore.add(Component.text("§7隣接GENERATOR/SOLAR_PANELからも自動受電"));
        lore.add(Component.text("§7スロット0: バッテリー投入 / スロット26: 放電"));
        meta.lore(lore);
        meta.getPersistentDataContainer().set(
                org.bukkit.NamespacedKey.fromString("circuitsurvival:decoration"),
                org.bukkit.persistence.PersistentDataType.BOOLEAN, true);
        display.setItemMeta(meta);
        gui.setItem(13, display);

        // 保存済みアイテムを復元
        ItemStack[] saved = machineManager.getStoredContents(loc);
        if (saved != null) {
            if (saved[0] != null && !saved[0].getType().isAir()) gui.setItem(0, saved[0].clone());
            if (saved[26] != null && !saved[26].getType().isAir()) gui.setItem(26, saved[26].clone());
        }

        openGuis.put(loc.toBlockLocation(), gui);
        machineManager.registerLiveInventory(loc.toBlockLocation(), gui);
        return gui;
    }

    // ---- CHARGER GUI -------------------------------------------------------

    private Inventory createChargerGui(Location loc) {
        MachineInvHolder holder = new MachineInvHolder(loc, MachineType.CHARGER);
        Inventory gui = plugin.getServer().createInventory(holder, 27,
                Component.text(GUI_PREFIX + "充電器"));
        holder.setInventory(gui);

        ItemStack[] saved = machineManager.getStoredContents(loc);
        // スロット0: 充電中バッテリー
        if (saved != null && saved[0] != null && !saved[0].getType().isAir()) {
            gui.setItem(0, saved[0].clone());
        } else {
            gui.setItem(0, makePane(Material.GRAY_STAINED_GLASS_PANE, "§7バッテリー投入"));
        }
        // スロット1: 充電完了バッテリー
        if (saved != null && saved[1] != null && !saved[1].getType().isAir()) {
            gui.setItem(1, saved[1].clone());
        } else {
            gui.setItem(1, makePane(Material.GRAY_STAINED_GLASS_PANE, "§7充電完了品"));
        }
        // スロット2-26: 保存バッファ
        if (saved != null) {
            for (int i = 2; i < 27; i++) {
                if (saved[i] != null && !saved[i].getType().isAir()) gui.setItem(i, saved[i].clone());
            }
        }
        // 情報表示 (ENレベル)
        int cur = energyManager.getEnergy(loc);
        int max = energyManager.getMaxEnergy(loc);
        gui.setItem(8, makeInfoItem(Material.REDSTONE, "§cEN: " + cur + " / " + max));
        return gui;
    }

    private ItemStack makeInfoItem(Material mat, String displayText) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(displayText));
        meta.getPersistentDataContainer().set(
                org.bukkit.NamespacedKey.fromString("circuitsurvival:decoration"),
                org.bukkit.persistence.PersistentDataType.BOOLEAN, true);
        item.setItemMeta(meta);
        return item;
    }

    // ---- GENERATOR / ENERGY_CELL タスク --------------------------------------

    /**
     * GENERATOR: RS通電で燃料スロットのアイテムを消費してENを生成。
     * 燃料種別ごとに決まったEN量をgenerate()でバッファに加算。
     */
    private void startGeneratorTask() {
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            for (Location loc : new ArrayList<>(machineManager.getByType(MachineType.GENERATOR))) {
                if (!loc.getChunk().isLoaded()) continue;
                if (!loc.getBlock().isBlockPowered() && !loc.getBlock().isBlockIndirectlyPowered()) continue;
                if (energyManager.isFull(loc)) continue;

                ItemStack[] contents = machineManager.getStoredContents(loc);
                if (contents == null || contents.length < 1) continue;
                if (isEmpty(contents[0])) continue;

                int energyPerItem = getFuelEnergy(contents[0].getType());
                if (energyPerItem <= 0) continue;

                energyManager.generate(loc, energyPerItem);
                energyManager.distributeFrom(loc);
                consumeContentsSlot(contents, 0, 1);
                machineManager.setStoredContents(loc, contents);
            }
        }, 10L, 10L);
    }

    /** 燃料種別ごとのEN生成量 */
    private static int getFuelEnergy(Material mat) {
        return switch (mat) {
            case COAL, CHARCOAL -> 80;
            case COAL_BLOCK -> 720;
            case DRIED_KELP_BLOCK -> 200;
            case BLAZE_ROD -> 120;
            case LAVA_BUCKET -> 2000;
            case BAMBOO -> 4;
            case OAK_PLANKS, SPRUCE_PLANKS, BIRCH_PLANKS, JUNGLE_PLANKS, ACACIA_PLANKS,
                 DARK_OAK_PLANKS, MANGROVE_PLANKS,
                 CRIMSON_PLANKS, WARPED_PLANKS -> 30;
            case OAK_LOG, SPRUCE_LOG, BIRCH_LOG, JUNGLE_LOG, ACACIA_LOG,
                 DARK_OAK_LOG, MANGROVE_LOG -> 60;
            default -> 0;
        };
    }

    /**
     * ENERGY_CELL: 定期的にdistributeFrom()を呼び出してENを隣接機械に分配。
     * RS信号不要 (常時動作)。
     */
    private void startEnergyCellTask() {
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            for (Location loc : new ArrayList<>(machineManager.getByType(MachineType.ENERGY_CELL))) {
                if (!loc.getChunk().isLoaded()) continue;
                energyManager.distributeFrom(loc);
            }
        }, 10L, 20L);
    }

    /**
     * CHARGER: RS通電でスロット0のバッテリーを充電する。
     * 充電完了 (10000EN) したらスロット1に移動。
     * 1tickあたり20ENをチャージャーのENから消費。
     */
    private void startChargerTask() {
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            for (Location loc : new ArrayList<>(machineManager.getByType(MachineType.CHARGER))) {
                if (!loc.getChunk().isLoaded()) continue;
                if (!loc.getBlock().isBlockPowered() && !loc.getBlock().isBlockIndirectlyPowered()) continue;
                if (!energyManager.tryConsume(loc, MachineType.CHARGER)) continue;

                ItemStack[] contents = machineManager.getStoredContents(loc);
                if (contents == null || contents.length < 2) continue;
                if (isEmpty(contents[0])) continue;
                if (!CircuitSurvivalPlugin.isBattery(contents[0])) continue;

                int batEn = CircuitSurvivalPlugin.getBatteryEn(contents[0]);
                if (batEn >= 10000) {
                    if (contents[1] == null || contents[1].getType().isAir()) {
                        contents[1] = contents[0].clone();
                        contents[0] = null;
                        machineManager.setStoredContents(loc, contents);
                    }
                    continue;
                }
                int charge = Math.min(20, 10000 - batEn);
                CircuitSurvivalPlugin.setBatteryEn(contents[0], batEn + charge);
                machineManager.setStoredContents(loc, contents);
            }
        }, 10L, 10L);
    }

    // ---- HV_CELL / ENERGY_CELL 分配タスク ------------------------------------
    // ENERGY_CELL と HV_CELL の distributeFrom を呼ぶ

    private void startHvCellTask() {
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            for (Location loc : new ArrayList<>(machineManager.getByType(MachineType.HV_CELL))) {
                if (!loc.getChunk().isLoaded()) continue;
                energyManager.distributeFrom(loc);
            }
        }, 10L, 20L);
    }

    // ---- HV_CELL GUI (EN表示) -----------------------------------------------

    private Inventory createHvCellGui(Location loc) {
        MachineInvHolder holder = new MachineInvHolder(loc, MachineType.HV_CELL);
        Inventory gui = plugin.getServer().createInventory(holder, 27,
                Component.text(GUI_PREFIX + "高圧蓄電機"));
        holder.setInventory(gui);

        int cur = energyManager.getEnergy(loc);
        int max = energyManager.getMaxEnergy(loc);

        ItemStack display = new ItemStack(Material.DIAMOND);
        ItemMeta meta = display.getItemMeta();
        meta.displayName(Component.text("§bEN: " + cur + " / " + max));
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("§7蓄電量が50%を超えると隣接機械に自動分配"));
        lore.add(Component.text("§7送電ワイヤー経由で遠距離転送可能"));
        lore.add(Component.text("§7スロット0: バッテリー投入 / スロット26: 放電"));
        meta.lore(lore);
        meta.getPersistentDataContainer().set(
                org.bukkit.NamespacedKey.fromString("circuitsurvival:decoration"),
                org.bukkit.persistence.PersistentDataType.BOOLEAN, true);
        display.setItemMeta(meta);
        gui.setItem(13, display);

        // 保存済みアイテムを復元
        ItemStack[] saved = machineManager.getStoredContents(loc);
        if (saved != null) {
            if (saved[0] != null && !saved[0].getType().isAir()) gui.setItem(0, saved[0].clone());
            if (saved[26] != null && !saved[26].getType().isAir()) gui.setItem(26, saved[26].clone());
        }

        openGuis.put(loc.toBlockLocation(), gui);
        machineManager.registerLiveInventory(loc.toBlockLocation(), gui);
        return gui;
    }

    // ---- 自動エンチャンター (AUTO_ENCHANTER) ---------------------------------

    /**
     * AUTO_ENCHANTER: RS通電でスロット0のツールにエンチャントを付与。
     * スロット1以降のラピスラズリを消費し、ENを消費。
     * 付与候補: ツール種別に応じたランダムエンチャント (1-3個)。
     */
    private void startAutoEnchanterTask() {
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            for (Location loc : new ArrayList<>(machineManager.getByType(MachineType.AUTO_ENCHANTER))) {
                if (!loc.getChunk().isLoaded()) continue;
                if (!loc.getBlock().isBlockPowered() && !loc.getBlock().isBlockIndirectlyPowered()) continue;
                if (!energyManager.tryConsume(loc, MachineType.AUTO_ENCHANTER)) continue;

                ItemStack[] contents = machineManager.getStoredContents(loc);
                if (contents == null || contents.length < 2) continue;
                if (isEmpty(contents[0])) continue;
                if (isEmpty(contents[1]) || contents[1].getType() != Material.LAPIS_LAZULI) continue;

                ItemStack tool = contents[0];
                if (tool.getItemMeta().hasEnchants()) continue; // エンチャ済みはスキップ

                // ランダムエンチャントを付与 (1-3個)
                var enchants = getRandomEnchants(tool.getType());
                if (enchants.isEmpty()) continue;

                consumeContentsSlot(contents, 1, 1);
                for (var entry : enchants.entrySet()) {
                    tool.addUnsafeEnchantment(entry.getKey(), entry.getValue());
                }
                machineManager.setStoredContents(loc, contents);
            }
        }, 20L, 20L);
    }

    /** ツール種別に応じたランダムエンチャントを1-3個生成 */
    private Map<org.bukkit.enchantments.Enchantment, Integer> getRandomEnchants(Material mat) {
        var ench = org.bukkit.enchantments.Enchantment.class;
        Map<org.bukkit.enchantments.Enchantment, Integer> result = new HashMap<>();
        String name = mat.name();
        java.util.List<org.bukkit.enchantments.Enchantment> pool = new java.util.ArrayList<>();

        if (name.endsWith("_PICKAXE") || name.endsWith("_SHOVEL") || name.endsWith("_AXE") || name.endsWith("_HOE")) {
            addEnch(pool, "efficiency"); addEnch(pool, "unbreaking");
            addEnch(pool, "silk_touch"); addEnch(pool, "fortune");
        } else if (name.endsWith("_SWORD")) {
            addEnch(pool, "sharpness"); addEnch(pool, "unbreaking");
            addEnch(pool, "looting"); addEnch(pool, "fire_aspect");
            addEnch(pool, "knockback");
        } else if (name.endsWith("_HELMET") || name.endsWith("_CHESTPLATE") || name.endsWith("_LEGGINGS") || name.endsWith("_BOOTS")) {
            addEnch(pool, "protection"); addEnch(pool, "unbreaking");
            if (name.endsWith("_BOOTS")) { addEnch(pool, "feather_falling"); addEnch(pool, "depth_strider"); }
            if (name.endsWith("_HELMET")) { addEnch(pool, "respiration"); addEnch(pool, "aqua_affinity"); }
        } else if (name.contains("BOW")) {
            addEnch(pool, "power"); addEnch(pool, "unbreaking");
            addEnch(pool, "flame"); addEnch(pool, "infinity");
        } else if (name.contains("FISHING") || name.contains("ROD")) {
            addEnch(pool, "luck_of_the_sea"); addEnch(pool, "lure"); addEnch(pool, "unbreaking");
        } else if (name.contains("TRIDENT")) {
            addEnch(pool, "impaling"); addEnch(pool, "loyalty");
            addEnch(pool, "unbreaking"); addEnch(pool, "riptide");
        } else if (name.contains("ENCHANTED_BOOK") || name.contains("BOOK")) {
            addEnch(pool, "protection"); addEnch(pool, "sharpness");
            addEnch(pool, "power"); addEnch(pool, "unbreaking");
            addEnch(pool, "efficiency"); addEnch(pool, "silk_touch");
            addEnch(pool, "mending"); addEnch(pool, "luck_of_the_sea");
            addEnch(pool, "lure");
        }

        if (pool.isEmpty()) return result;
        java.util.Collections.shuffle(pool, new java.util.Random());
        int count = 1 + new java.util.Random().nextInt(3);
        for (int i = 0; i < count && i < pool.size(); i++) {
            org.bukkit.enchantments.Enchantment e = pool.get(i);
            int max = e.getMaxLevel();
            int level = 1 + new java.util.Random().nextInt(Math.min(max, 4));
            result.put(e, level);
        }
        return result;
    }

    private void addEnch(java.util.List<org.bukkit.enchantments.Enchantment> list, String key) {
        org.bukkit.enchantments.Enchantment e = org.bukkit.enchantments.Enchantment.getByKey(
                org.bukkit.NamespacedKey.fromString("minecraft:" + key));
        if (e != null) list.add(e);
    }

    // ---- 虚空採掘機 (VOID_MINER) ---------------------------------------------

    /**
     * VOID_MINER: 大量EN (200EN/回) を消費して鉱石を生成する。
     * 生成確率: 石炭40% / 鉄30% / 金15% / ラピス8% / レッドストーン5% /
     *           ダイヤ1.5% / エメラルド0.5%
     * RS通電で10tickごとに動作。
     */
    private void startVoidMinerTask() {
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            for (Location loc : new ArrayList<>(machineManager.getByType(MachineType.VOID_MINER))) {
                if (!loc.getChunk().isLoaded()) continue;
                if (!loc.getBlock().isBlockPowered() && !loc.getBlock().isBlockIndirectlyPowered()) continue;
                if (!energyManager.tryConsume(loc, MachineType.VOID_MINER)) continue;

                ItemStack[] contents = machineManager.getStoredContents(loc);
                if (contents == null) continue;

                double roll = Math.random();
                Material ore;
                if (roll < 0.40)      ore = Material.COAL;
                else if (roll < 0.70) ore = Material.RAW_IRON;
                else if (roll < 0.85) ore = Material.RAW_GOLD;
                else if (roll < 0.93) ore = Material.LAPIS_LAZULI;
                else if (roll < 0.98) ore = Material.REDSTONE;
                else if (roll < 0.995) ore = Material.DIAMOND;
                else                  ore = Material.EMERALD;

                int amount = 1 + (ore == Material.LAPIS_LAZULI || ore == Material.REDSTONE ? new java.util.Random().nextInt(4) : 0);
                int slot = findAddSlot(contents, ore, 0);
                if (slot < 0) continue;
                addToContents(contents, slot, new ItemStack(ore, amount));
                machineManager.setStoredContents(loc, contents);
            }
        }, 10L, 10L);
    }

    // ---- 誘導溶解炉 (INDUCTION_FURNACE) --------------------------------------

    private void startInductionFurnaceTask() {
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            for (Location loc : new ArrayList<>(machineManager.getByType(MachineType.INDUCTION_FURNACE))) {
                if (!loc.getChunk().isLoaded()) continue;
                if (!loc.getBlock().isBlockPowered() && !loc.getBlock().isBlockIndirectlyPowered()) continue;
                if (!energyManager.tryConsume(loc, MachineType.INDUCTION_FURNACE)) continue;

                ItemStack[] contents = machineManager.getStoredContents(loc);
                if (contents == null || contents.length < 4) continue;

                boolean changed = false;

                // 合金化モード: slot0 + slot1 の組み合わせ処理
                if (!isEmpty(contents[0]) && !isEmpty(contents[1])) {
                    String id0 = IntermediateMaterials.getId(contents[0]);
                    String id1 = IntermediateMaterials.getId(contents[1]);
                    ItemStack alloyResult = null;
                    if (IntermediateMaterials.REFINED_IRON_INGOT.equals(id0)
                            && IntermediateMaterials.CRUSHED_COAL.equals(id1))
                        alloyResult = IntermediateMaterials.build(IntermediateMaterials.STEEL_INGOT, 2);
                    else if (IntermediateMaterials.STEEL_INGOT.equals(id0)
                            && IntermediateMaterials.PURE_IRON_EXTRACT.equals(id1))
                        alloyResult = IntermediateMaterials.build(IntermediateMaterials.ADVANCED_ALLOY, 2);

                    if (alloyResult != null) {
                        int outSlot = findAddSlot(contents, alloyResult.getType(), 3);
                        if (outSlot >= 0) {
                            consumeContentsSlot(contents, 0, 1);
                            consumeContentsSlot(contents, 1, 1);
                            addToContents(contents, outSlot, alloyResult);
                            machineManager.setStoredContents(loc, contents);
                            continue;
                        }
                    }
                }

                // 通常精錬モード (既存)
                for (int i = 0; i < 3; i++) {
                    if (isEmpty(contents[i])) continue;
                    ItemStack result = getSmeltedResult(contents[i].getType());
                    if (result == null) continue;
                    int outSlot = findAddSlot(contents, result.getType(), 3);
                    if (outSlot < 0) break;
                    consumeContentsSlot(contents, i, 1);
                    addToContents(contents, outSlot, result);
                    changed = true;
                }
                if (changed) machineManager.setStoredContents(loc, contents);
            }
        }, 4L, 4L);
    }

    // ---- 遠心分離機 (CENTRIFUGE) ---------------------------------------------

    private void startCentrifugeTask() {
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            for (Location loc : new ArrayList<>(machineManager.getByType(MachineType.CENTRIFUGE))) {
                if (!loc.getChunk().isLoaded()) continue;
                if (!loc.getBlock().isBlockPowered() && !loc.getBlock().isBlockIndirectlyPowered()) continue;
                if (!energyManager.tryConsume(loc, MachineType.CENTRIFUGE)) continue;

                ItemStack[] contents = machineManager.getStoredContents(loc);
                if (contents == null) continue;
                if (isEmpty(contents[0])) continue;

                boolean processed = false;

                // 粉砕物→抽出液 (4:1変換)
                if (contents[0].getAmount() >= 4) {
                    String midId = IntermediateMaterials.getId(contents[0]);
                    ItemStack extractResult = null;
                    if (IntermediateMaterials.CRUSHED_IRON.equals(midId))
                        extractResult = IntermediateMaterials.build(IntermediateMaterials.PURE_IRON_EXTRACT);
                    else if (IntermediateMaterials.CRUSHED_GOLD.equals(midId))
                        extractResult = IntermediateMaterials.build(IntermediateMaterials.PURE_GOLD_EXTRACT);

                    if (extractResult != null) {
                        int slot = findAddSlot(contents, extractResult.getType(), 1);
                        if (slot >= 0) {
                            consumeContentsSlot(contents, 0, 4);
                            addToContents(contents, slot, extractResult);
                            machineManager.setStoredContents(loc, contents);
                            processed = true;
                        }
                    }
                }
                if (processed) continue;

                // ブロック分解 (既存)
                ItemStack[] outputs = getCentrifugeOutput(contents[0].getType());
                if (outputs == null) continue;

                boolean canFit = true;
                for (ItemStack out : outputs) {
                    if (findAddSlot(contents, out.getType(), 1) < 0) { canFit = false; break; }
                }
                if (!canFit) continue;

                consumeContentsSlot(contents, 0, 1);
                for (ItemStack out : outputs) {
                    int slot = findAddSlot(contents, out.getType(), 1);
                    if (slot >= 0) addToContents(contents, slot, out);
                }
                machineManager.setStoredContents(loc, contents);
            }
        }, 20L, 20L);
    }

    private ItemStack[] getCentrifugeOutput(Material input) {
        return switch (input) {
            case REDSTONE_BLOCK -> new ItemStack[]{new ItemStack(Material.REDSTONE, 9), new ItemStack(Material.GLOWSTONE_DUST, 4)};
            case COPPER_BLOCK -> new ItemStack[]{new ItemStack(Material.COPPER_INGOT, 6), new ItemStack(Material.GOLD_NUGGET, 3)};
            case IRON_BLOCK -> new ItemStack[]{new ItemStack(Material.IRON_INGOT, 6), new ItemStack(Material.IRON_NUGGET, 8)};
            case GOLD_BLOCK -> new ItemStack[]{new ItemStack(Material.GOLD_INGOT, 6), new ItemStack(Material.GOLD_NUGGET, 12)};
            case LAPIS_BLOCK -> new ItemStack[]{new ItemStack(Material.LAPIS_LAZULI, 9), new ItemStack(Material.IRON_NUGGET, 3)};
            case DIAMOND_BLOCK -> new ItemStack[]{new ItemStack(Material.DIAMOND, 6), new ItemStack(Material.COAL, 4)};
            case EMERALD_BLOCK -> new ItemStack[]{new ItemStack(Material.EMERALD, 6), new ItemStack(Material.IRON_NUGGET, 2)};
            case NETHERITE_BLOCK -> new ItemStack[]{new ItemStack(Material.NETHERITE_INGOT, 6), new ItemStack(Material.ANCIENT_DEBRIS, 3)};
            default -> null;
        };
    }

    // ---- 燃焼発電機 (COMBUSTION_GENERATOR) -----------------------------------

    private void startCombustionGeneratorTask() {
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            for (Location loc : new ArrayList<>(machineManager.getByType(MachineType.COMBUSTION_GENERATOR))) {
                if (!loc.getChunk().isLoaded()) continue;
                if (!loc.getBlock().isBlockPowered() && !loc.getBlock().isBlockIndirectlyPowered()) continue;
                if (energyManager.isFull(loc)) continue;

                ItemStack[] contents = machineManager.getStoredContents(loc);
                if (contents == null || contents.length < 1) continue;
                if (isEmpty(contents[0])) continue;

                int energyPerItem = getFuelEnergy(contents[0].getType());
                if (energyPerItem <= 0) continue;

                energyManager.generate(loc, energyPerItem * 2);
                energyManager.distributeFrom(loc);
                consumeContentsSlot(contents, 0, 1);
                machineManager.setStoredContents(loc, contents);
            }
        }, 10L, 10L);
    }

    // ---- 鉱石三段加工機 (ORE_PROCESSOR) ---------------------------------------

    private void startOreProcessorTask() {
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            for (Location loc : new ArrayList<>(machineManager.getByType(MachineType.ORE_PROCESSOR))) {
                if (!loc.getChunk().isLoaded()) continue;
                if (!loc.getBlock().isBlockPowered() && !loc.getBlock().isBlockIndirectlyPowered()) continue;
                if (!energyManager.tryConsume(loc, MachineType.ORE_PROCESSOR)) continue;

                ItemStack[] contents = machineManager.getStoredContents(loc);
                if (contents == null) continue;

                if (isEmpty(contents[0])) continue;

                Material input = contents[0].getType();
                ItemStack output = getOreProcessorOutput(input);
                if (output == null) continue;

                int slot = findAddSlot(contents, output.getType(), 1);
                if (slot < 0) continue;

                consumeContentsSlot(contents, 0, 1);
                addToContents(contents, slot, output);
                machineManager.setStoredContents(loc, contents);
            }
        }, 20L, 20L);
    }

    private ItemStack getOreProcessorOutput(Material input) {
        return switch (input) {
            case RAW_IRON, IRON_ORE, DEEPSLATE_IRON_ORE -> new ItemStack(Material.IRON_INGOT, 4);
            case RAW_GOLD, GOLD_ORE, DEEPSLATE_GOLD_ORE -> new ItemStack(Material.GOLD_INGOT, 4);
            case RAW_COPPER, COPPER_ORE, DEEPSLATE_COPPER_ORE -> new ItemStack(Material.COPPER_INGOT, 4);
            case NETHER_GOLD_ORE -> new ItemStack(Material.GOLD_INGOT, 6);
            case ANCIENT_DEBRIS -> new ItemStack(Material.NETHERITE_SCRAP, 4);
            default -> null;
        };
    }

    // ---- 物質生成機 (MATERIALIZER) -------------------------------------------

    private void startMaterializerTask() {
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            for (Location loc : new ArrayList<>(machineManager.getByType(MachineType.MATERIALIZER))) {
                if (!loc.getChunk().isLoaded()) continue;
                if (!loc.getBlock().isBlockPowered() && !loc.getBlock().isBlockIndirectlyPowered()) continue;
                if (!energyManager.tryConsume(loc, MachineType.MATERIALIZER)) continue;

                ItemStack[] contents = machineManager.getStoredContents(loc);
                if (contents == null) continue;
                if (isEmpty(contents[0])) continue;

                ItemStack input = contents[0];
                ItemStack output = getMaterializerOutput(input);
                if (output == null) continue;

                int slot = findAddSlot(contents, output.getType(), 1);
                if (slot < 0) continue;

                consumeContentsSlot(contents, 0, 1);
                addToContents(contents, slot, output);
                machineManager.setStoredContents(loc, contents);
            }
        }, 20L, 20L);
    }

    private ItemStack getMaterializerOutput(ItemStack input) {
        return switch (input.getType()) {
            case COAL -> new ItemStack(Material.DIAMOND, 1);
            case IRON_NUGGET -> new ItemStack(Material.GOLD_INGOT, 1);
            case QUARTZ -> new ItemStack(Material.EMERALD, 1);
            case BLAZE_ROD -> new ItemStack(Material.NETHERITE_SCRAP, 1);
            case AMETHYST_SHARD -> new ItemStack(Material.ECHO_SHARD, 1);
            case PRISMARINE_CRYSTALS -> new ItemStack(Material.HEART_OF_THE_SEA, 1);
            case PHANTOM_MEMBRANE -> new ItemStack(Material.ELYTRA, 1);
            default -> null;
        };
    }

    // ---- リサイクル機 (RECYCLER) ---------------------------------------------

    private void startRecyclerTask() {
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            for (Location loc : new ArrayList<>(machineManager.getByType(MachineType.RECYCLER))) {
                if (!loc.getChunk().isLoaded()) continue;
                if (!loc.getBlock().isBlockPowered() && !loc.getBlock().isBlockIndirectlyPowered()) continue;
                if (!energyManager.tryConsume(loc, MachineType.RECYCLER)) continue;

                ItemStack[] contents = machineManager.getStoredContents(loc);
                if (contents == null) continue;
                if (isEmpty(contents[0])) continue;

                ItemStack recycled = getRecycledOutput(contents[0]);
                if (recycled == null) continue;

                int slot = findAddSlot(contents, recycled.getType(), 1);
                if (slot < 0) continue;

                consumeContentsSlot(contents, 0, 1);
                addToContents(contents, slot, recycled);
                machineManager.setStoredContents(loc, contents);
            }
        }, 20L, 20L);
    }

    // ---- 洗浄機 (WASHING_MACHINE: 自動処理) -------------------------------

    private void startWashingMachineTask() {
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            for (Location loc : new ArrayList<>(machineManager.getByType(MachineType.WASHING_MACHINE))) {
                if (!loc.getChunk().isLoaded()) continue;
                if (!loc.getBlock().isBlockPowered() && !loc.getBlock().isBlockIndirectlyPowered()) continue;
                if (!energyManager.tryConsume(loc, MachineType.WASHING_MACHINE)) continue;
                ItemStack[] contents = machineManager.getStoredContents(loc);
                if (contents == null) continue;
                if (isEmpty(contents[0])) continue;
                ItemStack water = contents[1];
                if (isEmpty(water) || water.getType() != Material.WATER_BUCKET) continue;
                WashResult recipe = WASH_MAP.get(contents[0].getType());
                if (recipe == null) continue;
                int slot = findAddSlot(contents, recipe.output(), 26);
                if (slot < 0) continue;
                // 水バケツ消費
                if (water.getAmount() > 1) { water.setAmount(water.getAmount() - 1); }
                else { contents[1] = null; }
                consumeContentsSlot(contents, 0, 1);
                if (RANDOM.nextInt(100) < recipe.chancePercent()) {
                    addToContents(contents, slot, new ItemStack(recipe.output()));
                }
                // 空バケツ返却 (出力先または空きスロットへ)
                ItemStack emptyBucket = new ItemStack(Material.BUCKET);
                int bucketOut = findAddSlot(contents, Material.BUCKET, 26);
                if (bucketOut >= 0) {
                    addToContents(contents, bucketOut, emptyBucket);
                } else {
                    contents[1] = emptyBucket;
                }
                machineManager.setStoredContents(loc, contents);
            }
        }, 20L, 20L);
    }

    // ---- クラフター制御装置 (CRAFTER_CONTROLLER) --------------------------

    private void startCrafterControllerTask() {
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            for (Location loc : new ArrayList<>(machineManager.getByType(MachineType.CRAFTER_CONTROLLER))) {
                if (!loc.getChunk().isLoaded()) continue;
                if (!loc.getBlock().isBlockPowered() && !loc.getBlock().isBlockIndirectlyPowered()) continue;
                // 半径8ブロック内のAUTO_CRAFTERを1回分強制クラフト
                int x = loc.getBlockX(), y = loc.getBlockY(), z = loc.getBlockZ();
                for (int dx = -8; dx <= 8; dx++) {
                    for (int dy = -8; dy <= 8; dy++) {
                        for (int dz = -8; dz <= 8; dz++) {
                            Location target = new Location(loc.getWorld(), x + dx, y + dy, z + dz);
                            if (!target.getChunk().isLoaded()) continue;
                            if (!target.equals(loc) && machineManager.isMachine(target)
                                    && machineManager.getType(target) == MachineType.AUTO_CRAFTER) {
                                autoCraftOnePass(target);
                            }
                        }
                    }
                }
            }
        }, 10L, 10L);
    }

    private void autoCraftOnePass(Location crafterLoc) {
        ItemStack[] contents = machineManager.getStoredContents(crafterLoc);
        if (contents == null || contents.length < 10) return;
        ItemStack result = matchCraft(contents);
        if (result == null) return;
        if (!isEmpty(contents[9]) && (!contents[9].isSimilar(result)
                || contents[9].getAmount() + result.getAmount() > result.getMaxStackSize())) return;
        for (int i = 0; i < 9; i++) {
            if (!isEmpty(contents[i])) consumeContentsSlot(contents, i, 1);
        }
        if (isEmpty(contents[9])) contents[9] = result.clone();
        else contents[9].setAmount(contents[9].getAmount() + result.getAmount());
        machineManager.setStoredContents(crafterLoc, contents);
    }

    private ItemStack matchCraft(ItemStack[] grid) {
        if (grid == null || grid.length < 9) return null;
        java.util.List<org.bukkit.inventory.Recipe> recipes = new java.util.ArrayList<>();
        Bukkit.recipeIterator().forEachRemaining(recipes::add);
        for (org.bukkit.inventory.Recipe r : recipes) {
            if (r instanceof org.bukkit.inventory.ShapedRecipe shaped) {
                String[] shape = shaped.getShape();
                if (shape == null || shape.length == 0) continue;
                Map<Character, org.bukkit.inventory.RecipeChoice> ingMap = shaped.getChoiceMap();
                for (int rowOff = 0; rowOff <= 3 - shape.length; rowOff++) {
                    for (int colOff = 0; colOff <= 3 - shape[0].length(); colOff++) {
                        boolean matches = true;
                        for (int rw = 0; rw < shape.length && matches; rw++) {
                            for (int cl = 0; cl < shape[rw].length() && matches; cl++) {
                                char c = shape[rw].charAt(cl);
                                int idx = (rowOff + rw) * 3 + (colOff + cl);
                                org.bukkit.inventory.RecipeChoice expected = ingMap.get(c);
                                ItemStack actual = grid[idx];
                                if (c == ' ') {
                                    if (!isEmpty(actual)) matches = false;
                                } else if (expected == null) {
                                    if (!isEmpty(actual)) matches = false;
                                } else if (isEmpty(actual)) {
                                    matches = false;
                                } else if (!expected.test(actual)) {
                                    matches = false;
                                }
                            }
                        }
                        if (matches) return shaped.getResult().clone();
                    }
                }
            } else if (r instanceof org.bukkit.inventory.ShapelessRecipe shapeless) {
                java.util.List<org.bukkit.inventory.RecipeChoice> choices = shapeless.getChoiceList();
                java.util.List<ItemStack> gridItems = new java.util.ArrayList<>();
                for (int i = 0; i < 9; i++) if (!isEmpty(grid[i])) gridItems.add(grid[i].clone());
                if (gridItems.size() != choices.size()) continue;
                boolean[] used = new boolean[gridItems.size()];
                boolean allMatch = true;
                outer:
                for (org.bukkit.inventory.RecipeChoice choice : choices) {
                    for (int j = 0; j < gridItems.size(); j++) {
                        if (!used[j] && choice.test(gridItems.get(j))) { used[j] = true; continue outer; }
                    }
                    allMatch = false;
                }
                if (allMatch) return shapeless.getResult().clone();
            }
        }
        return null;
    }

    // ---- 水力発電機 (WATER_GENERATOR) ------------------------------------

    private void startWaterGeneratorTask() {
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            for (Location loc : new ArrayList<>(machineManager.getByType(MachineType.WATER_GENERATOR))) {
                if (!loc.getChunk().isLoaded()) continue;
                if (!hasAdjacentWater(loc.getBlock())) continue;
                energyManager.generate(loc, 3);
                energyManager.distributeFrom(loc);
            }
        }, 20L, 20L);
    }

    private boolean hasAdjacentWater(Block block) {
        for (BlockFace face : BlockFace.values()) {
            if (face == BlockFace.SELF) continue;
            if (block.getRelative(face).getType() == Material.WATER) return true;
        }
        return false;
    }

    // ---- 風力発電機 (WIND_GENERATOR) ------------------------------------

    private void startWindGeneratorTask() {
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            for (Location loc : new ArrayList<>(machineManager.getByType(MachineType.WIND_GENERATOR))) {
                if (!loc.getChunk().isLoaded()) continue;
                int y = loc.getBlockY();
                // 高さ32ごとに+1EN (最低1、最大6)
                int power = Math.min(6, Math.max(1, y / 32));
                // 屋外チェック (上空にブロックが無い)
                boolean sky = loc.getBlock().getLightFromSky() >= 15
                        || y >= loc.getWorld().getHighestBlockYAt(loc.getBlockX(), loc.getBlockZ());
                if (!sky) power /= 2;
                energyManager.generate(loc, power);
                energyManager.distributeFrom(loc);
            }
        }, 20L, 20L);
    }

    private ItemStack getRecycledOutput(ItemStack input) {
        Material m = input.getType();
        if (m == Material.ROTTEN_FLESH || m == Material.BONE || m == Material.STRING || m == Material.SPIDER_EYE) {
            return new ItemStack(Material.IRON_NUGGET, 1);
        }
        return switch (m) {
            case IRON_INGOT -> new ItemStack(Material.IRON_NUGGET, 3);
            case GOLD_INGOT -> new ItemStack(Material.GOLD_NUGGET, 3);
            case COPPER_INGOT -> new ItemStack(Material.IRON_NUGGET, 2);
            case DIAMOND -> new ItemStack(Material.IRON_INGOT, 2);
            case NETHERITE_SCRAP -> new ItemStack(Material.DIAMOND, 1);
            case OAK_LOG, SPRUCE_LOG, BIRCH_LOG, JUNGLE_LOG, ACACIA_LOG, DARK_OAK_LOG,
                 OAK_PLANKS, SPRUCE_PLANKS, BIRCH_PLANKS, JUNGLE_PLANKS, ACACIA_PLANKS, DARK_OAK_PLANKS ->
                    new ItemStack(Material.COAL, 1);
            case COBBLESTONE, STONE, ANDESITE, DIORITE, GRANITE, DEEPSLATE, TUFF ->
                    new ItemStack(Material.FLINT, 1);
            case GRAVEL -> new ItemStack(Material.SAND, 2);
            case SAND, RED_SAND -> new ItemStack(Material.COBBLESTONE, 1);
            default -> null;
        };
    }

    // ---- ワイヤレス充電器 (WIRELESS_CHARGER) ---------------------------------

    private void startWirelessChargerTask() {
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            for (Location loc : new ArrayList<>(machineManager.getByType(MachineType.WIRELESS_CHARGER))) {
                if (!loc.getChunk().isLoaded()) continue;
                if (energyManager.getEnergy(loc) <= energyManager.getPassiveDrain()) continue;

                // 半径5ブロックの機械を検索して給電
                int x = loc.getBlockX();
                int y = loc.getBlockY();
                int z = loc.getBlockZ();
                int givePerTick = 10;
                int chargerEn = energyManager.getEnergy(loc);
                int budget = Math.min(givePerTick, chargerEn - energyManager.getPassiveDrain());
                if (budget <= 0) continue;

                for (int dx = -5; dx <= 5; dx++) {
                    for (int dy = -5; dy <= 5; dy++) {
                        for (int dz = -5; dz <= 5; dz++) {
                            if (dx == 0 && dy == 0 && dz == 0) continue;
                            if (budget <= 0) break;
                            Location target = new Location(loc.getWorld(), x + dx, y + dy, z + dz);
                            if (!target.getChunk().isLoaded()) continue;
                            if (!machineManager.isMachine(target)) continue;
                            MachineType t = machineManager.getType(target);
                            if (!energyManager.needsEnergy(t)) continue;

                            int targetEn = energyManager.getEnergy(target);
                            int targetMax = energyManager.getMaxEnergy(target);
                            int space = targetMax - targetEn;
                            if (space <= 0) continue;

                            int xfer = Math.min(budget, space);
                            energyManager.setEnergy(loc, energyManager.getEnergy(loc) - xfer);
                            energyManager.generate(target, xfer);
                            budget -= xfer;
                        }
                        if (budget <= 0) break;
                    }
                    if (budget <= 0) break;
                }
            }
        }, 10L, 10L);
    }

    // ---- 自動解呪機 (AUTO_DISENCHANTER) -------------------------------------

    private void startAutoDisenchanterTask() {
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            for (Location loc : new ArrayList<>(machineManager.getByType(MachineType.AUTO_DISENCHANTER))) {
                if (!loc.getChunk().isLoaded()) continue;
                if (!loc.getBlock().isBlockPowered() && !loc.getBlock().isBlockIndirectlyPowered()) continue;
                if (!energyManager.tryConsume(loc, MachineType.AUTO_DISENCHANTER)) continue;

                ItemStack[] contents = machineManager.getStoredContents(loc);
                if (contents == null) continue;
                if (isEmpty(contents[0])) continue;

                ItemStack tool = contents[0];
                if (!tool.hasItemMeta()) continue;
                var enchants = tool.getItemMeta().getEnchants();
                if (enchants.isEmpty()) continue;

                // エンチャを本に抽出
                var entry = enchants.entrySet().iterator().next();
                ItemStack book = new ItemStack(Material.ENCHANTED_BOOK);
                ItemMeta bm = book.getItemMeta();
                if (bm instanceof org.bukkit.inventory.meta.EnchantmentStorageMeta esm) {
                    esm.addStoredEnchant(entry.getKey(), entry.getValue(), true);
                    esm.displayName(Component.text("§e抽出エンチャント本"));
                    book.setItemMeta(esm);
                }

                int slot = findAddSlot(contents, Material.ENCHANTED_BOOK, 1);
                if (slot < 0) continue;

                // ツールから1エンチャ削除
                var newMeta = tool.getItemMeta();
                newMeta.removeEnchant(entry.getKey());
                tool.setItemMeta(newMeta);
                addToContents(contents, slot, book);
                machineManager.setStoredContents(loc, contents);
            }
        }, 20L, 20L);
    }

    // ---- 熱発電機 (THERMAL_GENERATOR) ----------------------------------------

    private void startThermalGeneratorTask() {
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            for (Location loc : new ArrayList<>(machineManager.getByType(MachineType.THERMAL_GENERATOR))) {
                if (!loc.getChunk().isLoaded()) continue;
                if (energyManager.isFull(loc)) continue;
                // バイオーム温度に応じて発電量変動
                double temp = loc.getBlock().getTemperature();
                int rate;
                if (temp >= 2.0) rate = 30;   // ネザー
                else if (temp >= 1.0) rate = 20; // 砂漠・サバンナ
                else if (temp >= 0.5) rate = 10; // 平原・森林
                else if (temp >= 0.0) rate = 5;  // タイガ・雪原
                else rate = 3;                    // 凍った海等
                energyManager.generate(loc, rate);
                energyManager.distributeFrom(loc);
            }
        }, 10L, 10L);
    }

    // ---- 高級組立機 (ADVANCED_ASSEMBLER) ---------------------------------------

    private void startAdvancedAssemblerTask() {
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            for (Location loc : new ArrayList<>(machineManager.getByType(MachineType.ADVANCED_ASSEMBLER))) {
                if (!loc.getChunk().isLoaded()) continue;
                if (!loc.getBlock().isBlockPowered() && !loc.getBlock().isBlockIndirectlyPowered()) continue;
                if (!energyManager.tryConsume(loc, MachineType.ADVANCED_ASSEMBLER)) continue;

                ItemStack[] contents = machineManager.getStoredContents(loc);
                if (contents == null || contents.length < 27) continue;

                // スロット0-8から3x3グリッドを構築
                ItemStack[] grid = new ItemStack[9];
                System.arraycopy(contents, 0, grid, 0, 9);
                CustomCrafterRecipe recipe = CustomCrafterRecipe.match(grid);
                if (recipe == null) continue;

                // バッファ(9-25)に足りない補充材料があるか確認&消費
                boolean canCraft = true;
                for (int i = 0; i < 9; i++) {
                    if (grid[i] == null || grid[i].getType().isAir()) continue;
                    boolean found = false;
                    for (int b = 9; b < 26; b++) {
                        if (contents[b] != null && contents[b].isSimilar(grid[i])
                                && contents[b].getAmount() > 0) {
                            found = true;
                            break;
                        }
                    }
                    if (!found) { canCraft = false; break; }
                }
                if (!canCraft) continue;

                // バッファから消費
                for (int i = 0; i < 9; i++) {
                    if (grid[i] == null || grid[i].getType().isAir()) continue;
                    for (int b = 9; b < 26; b++) {
                        if (contents[b] != null && contents[b].isSimilar(grid[i])
                                && contents[b].getAmount() > 0) {
                            consumeContentsSlot(contents, b, 1);
                            break;
                        }
                    }
                }

                // グリッド材料はbuffer消費で既に処理済み。consumeAndGetはグリッドテンプレートを壊すので使わない
                ItemStack result = recipe.result().clone();
                int outSlot = findAddSlot(contents, result.getType(), 26);
                if (outSlot >= 0) addToContents(contents, outSlot, result);
                machineManager.setStoredContents(loc, contents);
            }
        }, 10L, 20L);
    }

    // ---- 中性子圧縮機 (NEUTRON_COMPRESSOR) ------------------------------------

    private void startNeutronCompressorTask() {
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            for (Location loc : new ArrayList<>(machineManager.getByType(MachineType.NEUTRON_COMPRESSOR))) {
                if (!loc.getChunk().isLoaded()) continue;
                if (!loc.getBlock().isBlockPowered() && !loc.getBlock().isBlockIndirectlyPowered()) continue;
                if (!energyManager.tryConsume(loc, MachineType.NEUTRON_COMPRESSOR)) continue;

                ItemStack[] contents = machineManager.getStoredContents(loc);
                if (contents == null) continue;
                if (isEmpty(contents[0])) continue;

                Material input = contents[0].getType();
                ItemStack output = getCompressedOutput(input, contents[0].getAmount());
                if (output == null) continue;

                int slot = findAddSlot(contents, output.getType(), 1);
                if (slot < 0) continue;

                int consumeAmt = (input == Material.IRON_BLOCK || input == Material.GOLD_BLOCK || input == Material.DIAMOND_BLOCK) ? 9
                        : (input == Material.COBBLESTONE || input == Material.COBBLED_DEEPSLATE) ? 64 : 4;
                consumeContentsSlot(contents, 0, consumeAmt);
                addToContents(contents, slot, output);
                machineManager.setStoredContents(loc, contents);
            }
        }, 20L, 20L);
    }

    private ItemStack getCompressedOutput(Material input, int amount) {
        return switch (input) {
            case IRON_BLOCK -> amount >= 9 ? new ItemStack(Material.DIAMOND_BLOCK, 1) : null;
            case GOLD_BLOCK -> amount >= 9 ? new ItemStack(Material.EMERALD_BLOCK, 1) : null;
            case DIAMOND_BLOCK -> amount >= 9 ? new ItemStack(Material.NETHERITE_BLOCK, 1) : null;
            case COBBLESTONE -> amount >= 64 ? new ItemStack(Material.IRON_INGOT, 1) : null;
            case COBBLED_DEEPSLATE -> amount >= 64 ? new ItemStack(Material.GOLD_INGOT, 1) : null;
            default -> null;
        };
    }

    // ---- ゴミ箱自動クリーンアップ ---------------------------------------------

    private void startTrashCanCleanupTask() {
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            for (Location loc : new ArrayList<>(machineManager.getByType(MachineType.TRASH_CAN))) {
                if (!loc.getChunk().isLoaded()) continue;
                // 実インベントリ（BARREL）をクリア (ホッパー等の自動搬入に対応)
                if (loc.getBlock().getState() instanceof org.bukkit.block.Container container) {
                    container.getInventory().clear();
                }
                // GUI仮想インベントリのクリア (他プレイヤーが開いている場合)
                Inventory openInv = openGuis.get(loc.toBlockLocation());
                if (openInv != null) openInv.clear();
            }
        }, 10L, 2L);
    }

    private ItemStack buildMachineItem(MachineType type) {
        ItemStack item = new ItemStack(type.blockMaterial);
        ItemMeta meta  = item.getItemMeta();
        meta.displayName(Component.text(type.displayName, NamedTextColor.GOLD)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Component.text(type.description, NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                Component.text("右クリックでGUIを開く", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false)));
        meta.getPersistentDataContainer().set(machineTypeKey, PersistentDataType.STRING, type.name());
        item.setItemMeta(meta);
        return item;
    }

    // ---- 全機械のピストン移動対応 ---------------------------------------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonPushMachine(org.bukkit.event.block.BlockPistonExtendEvent event) {
        for (Block block : event.getBlocks()) {
            Location loc = block.getLocation();
            if (!machineManager.isMachine(loc)) continue;
            MachineType type = machineManager.getType(loc);
            if (type == null) continue;

            event.setCancelled(true);
            if (type == MachineType.MOBILE_PLATFORM) {
                movePlatformAndBelow(block, event.getDirection());
            } else {
                moveMachine(block, event.getDirection());
            }
            return;
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonPullMachine(org.bukkit.event.block.BlockPistonRetractEvent event) {
        if (!event.isSticky()) return;
        Block pulled = event.getBlock().getRelative(event.getDirection());
        Location loc = pulled.getLocation();
        if (!machineManager.isMachine(loc)) return;
        MachineType type = machineManager.getType(loc);
        if (type == null) return;

        // ピストンにRS信号が入っている → 引きつけをキャンセル、機械はそのまま
        if (event.getBlock().isBlockPowered() || event.getBlock().isBlockIndirectlyPowered()) {
            event.setCancelled(true);
            // ピストンを強制収縮 (機械は剥がれて残る)
            forceRetractPiston(event.getBlock(), event.getDirection());
            return;
        }

        // RS信号なし → 通常引きつけ許可、機械データを追従させる
        if (type == MachineType.MOBILE_PLATFORM) {
            movePlatformAndBelow(pulled, event.getDirection().getOppositeFace());
        } else {
            // 1tick後に位置を再確認してregisterを更新
            Location newLoc = loc.clone().add(event.getDirection().getOppositeFace().getDirection());
            machineManager.remove(loc);
            energyManager.removeData(loc);
            prevPowered.remove(locKey(loc));
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (machineManager.getType(newLoc) == null
                        && newLoc.getBlock().getType() == type.blockMaterial) {
                    machineManager.register(newLoc, type);
                    energyManager.moveData(loc, newLoc);
                }
            });
        }
    }

    /** ピストンを強制的に収縮させる (機械ブロックは剥がれて残る) */
    private void forceRetractPiston(Block pistonBlock, BlockFace dir) {
        Block headBlock = pistonBlock.getRelative(dir);
        // ピストンヘッドを除去
        if (headBlock.getType() == org.bukkit.Material.PISTON_HEAD
                || headBlock.getType() == org.bukkit.Material.MOVING_PISTON) {
            headBlock.setType(org.bukkit.Material.AIR);
        }
        // ピストン基部を収縮状態に
        if (pistonBlock.getBlockData() instanceof Piston pd) {
            pd.setExtended(false);
            pistonBlock.setBlockData(pd);
        }
    }

    /** 汎用機械移動: データ・インベントリを旧位置→新位置に移す */
    private void moveMachine(Block block, BlockFace dir) {
        Location oldLoc = block.getLocation().toBlockLocation();
        Location newLoc = oldLoc.clone().add(dir.getDirection());
        if (!newLoc.getBlock().getType().isAir()) return;

        MachineType type = machineManager.getType(oldLoc);
        if (type == null) return;

        ItemStack[] contents = null;
        if (MachineManager.hasMachineInventory(type)) {
            contents = machineManager.getStoredContents(oldLoc);
        }

        boolean isNative = false;
        org.bukkit.inventory.Inventory nativeInv = null;
        if (block.getState() instanceof org.bukkit.block.Container container) {
            isNative = true;
            nativeInv = container.getSnapshotInventory();
        }

        machineManager.remove(oldLoc);
        energyManager.removeData(oldLoc);
        prevPowered.remove(locKey(oldLoc));
        block.setType(Material.AIR);

        newLoc.getBlock().setType(type.blockMaterial);
        machineManager.register(newLoc, type);
        energyManager.moveData(oldLoc, newLoc);

        if (contents != null) machineManager.setStoredContents(newLoc, contents);
        if (isNative && nativeInv != null
                && newLoc.getBlock().getState() instanceof org.bukkit.block.Container nc) {
            for (int i = 0; i < nativeInv.getSize() && i < nc.getInventory().getSize(); i++) {
                nc.getInventory().setItem(i, nativeInv.getItem(i));
            }
            nc.update();
        }
    }

    private void movePlatformAndBelow(Block platform, BlockFace dir) {
        Location oldPlat = platform.getLocation().toBlockLocation();
        Location newPlat = oldPlat.clone().add(dir.getDirection());

        Block below = platform.getRelative(BlockFace.DOWN);
        Location oldBelow = below.getLocation().toBlockLocation();
        Location newBelow = oldBelow.clone().add(dir.getDirection());

        // 下のブロックがCS機械かチェック
        boolean hasMachine = machineManager.isMachine(oldBelow);
        MachineType belowType = hasMachine ? machineManager.getType(oldBelow) : null;

        // ★ 全ての空間チェックを先に行う (何も削除する前に)
        // 下方向: newPlatが空気でなければ移動不可
        if (!newPlat.getBlock().getType().isAir()) return;
        // 下の機械も移動する場合、その移動先も空気である必要がある
        if (hasMachine && belowType != null && !newBelow.getBlock().getType().isAir()) return;

        // ここから実際の移動を実行 (安全に移動できることが確定)
        ItemStack[] contents = null;
        boolean isNativeContainer = false;
        org.bukkit.inventory.Inventory nativeInv = null;

        if (hasMachine && belowType != null) {
            if (MachineManager.hasMachineInventory(belowType)) {
                contents = machineManager.getStoredContents(oldBelow);
            }
            if (below.getState() instanceof org.bukkit.block.Container container) {
                nativeInv = container.getSnapshotInventory();
                isNativeContainer = true;
            }
            machineManager.remove(oldBelow);
            energyManager.removeData(oldBelow);
            below.setType(Material.AIR);
        }

        // プラットフォームを移動
        machineManager.remove(oldPlat);
        energyManager.removeData(oldPlat);
        String platOldKey = locKey(oldPlat);
        prevPowered.remove(platOldKey);
        platform.setType(Material.AIR);

        newPlat.getBlock().setType(MachineType.MOBILE_PLATFORM.blockMaterial);
        machineManager.register(newPlat, MachineType.MOBILE_PLATFORM);
        energyManager.initData(newPlat, MachineType.MOBILE_PLATFORM);

        // 下の機械を復元
        if (hasMachine && belowType != null) {
            newBelow.getBlock().setType(belowType.blockMaterial);
            machineManager.register(newBelow, belowType);
            energyManager.moveData(oldBelow, newBelow);

            if (contents != null) machineManager.setStoredContents(newBelow, contents);
            if (isNativeContainer && nativeInv != null
                    && newBelow.getBlock().getState() instanceof org.bukkit.block.Container nc) {
                for (int i = 0; i < nativeInv.getSize() && i < nc.getInventory().getSize(); i++) {
                    nc.getInventory().setItem(i, nativeInv.getItem(i));
                }
                nc.update();
            }
        }
    }

    // ---- 蒸留塔 (DISTILLATION_TOWER) --------------------------------------

    private static final Map<String, DistillResult> DISTILL_MAP = new HashMap<>();
    static {
        DISTILL_MAP.put(IntermediateMaterials.CRUDE_OIL,
                new DistillResult(IntermediateMaterials.CHEMICAL_OIL, 2,
                        IntermediateMaterials.RUBBER, 30, IntermediateMaterials.SULFUR, 15));
    }

    private record DistillResult(String main, int mainAmt, String byproduct, int byChance,
                                 String second, int secChance) {}

    private void startDistillationTowerTask() {
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            for (Location loc : new ArrayList<>(machineManager.getByType(MachineType.DISTILLATION_TOWER))) {
                if (!loc.getChunk().isLoaded()) continue;
                if (!loc.getBlock().isBlockPowered() && !loc.getBlock().isBlockIndirectlyPowered()) continue;
                if (!energyManager.tryConsume(loc, MachineType.DISTILLATION_TOWER)) continue;
                ItemStack[] contents = machineManager.getStoredContents(loc);
                if (contents == null) continue;
                if (isEmpty(contents[0])) continue;
                String midId = IntermediateMaterials.getId(contents[0]);
                if (midId == null) continue;
                DistillResult recipe = DISTILL_MAP.get(midId);
                if (recipe == null) continue;
                ItemStack mainOut = IntermediateMaterials.build(recipe.main(), recipe.mainAmt());
                int slot = findAddSlot(contents, mainOut.getType(), 26);
                if (slot < 0) continue;
                consumeContentsSlot(contents, 0, 1);
                addToContents(contents, slot, mainOut);
                if (RANDOM.nextInt(100) < recipe.byChance()) {
                    ItemStack bp = IntermediateMaterials.build(recipe.byproduct());
                    int bSlot = findAddSlot(contents, bp.getType(), 1);
                    if (bSlot >= 0) addToContents(contents, bSlot, bp);
                }
                if (recipe.second() != null && RANDOM.nextInt(100) < recipe.secChance()) {
                    ItemStack sp = IntermediateMaterials.build(recipe.second());
                    int sSlot = findAddSlot(contents, sp.getType(), 1);
                    if (sSlot >= 0) addToContents(contents, sSlot, sp);
                }
                machineManager.setStoredContents(loc, contents);
            }
        }, 40L, 40L);
    }

    // ---- 化学反応炉 (CHEMICAL_REACTOR) -----------------------------------

    private static record ReactRecipe(String id0, String id1, String result, int amount) {}

    private static final List<ReactRecipe> REACT_RECIPES = List.of(
            new ReactRecipe(IntermediateMaterials.CHEMICAL_OIL, IntermediateMaterials.SULFUR,
                    IntermediateMaterials.SULFURIC_ACID, 2),
            new ReactRecipe(IntermediateMaterials.CHEMICAL_OIL, IntermediateMaterials.RUBBER,
                    IntermediateMaterials.PLASTIC, 3),
            new ReactRecipe("_MAT:CLAY_BALL", IntermediateMaterials.SULFUR,
                    IntermediateMaterials.HEAT_RESISTANT_CERAMIC, 2)
    );

    private boolean matchesReactRecipe(ReactRecipe rr, ItemStack a, ItemStack b) {
        String idA = IntermediateMaterials.getId(a);
        String idB = IntermediateMaterials.getId(b);
        return matchesReactId(rr.id0(), a, idA) && matchesReactId(rr.id1(), b, idB)
                || matchesReactId(rr.id0(), b, idB) && matchesReactId(rr.id1(), a, idA);
    }

    private boolean matchesReactId(String recipeId, ItemStack item, String itemId) {
        if (recipeId.startsWith("_MAT:")) {
            return item.getType().name().equals(recipeId.substring(5));
        }
        return recipeId.equals(itemId);
    }

    private void startChemicalReactorTask() {
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            for (Location loc : new ArrayList<>(machineManager.getByType(MachineType.CHEMICAL_REACTOR))) {
                if (!loc.getChunk().isLoaded()) continue;
                if (!loc.getBlock().isBlockPowered() && !loc.getBlock().isBlockIndirectlyPowered()) continue;
                if (!energyManager.tryConsume(loc, MachineType.CHEMICAL_REACTOR)) continue;
                ItemStack[] contents = machineManager.getStoredContents(loc);
                if (contents == null) continue;
                if (isEmpty(contents[0]) || isEmpty(contents[1])) continue;
                ReactRecipe match = null;
                for (ReactRecipe rr : REACT_RECIPES) {
                    if (matchesReactRecipe(rr, contents[0], contents[1])) {
                        match = rr; break;
                    }
                }
                if (match == null) continue;
                ItemStack result = IntermediateMaterials.build(match.result(), match.amount());
                int slot = findAddSlot(contents, result.getType(), 26);
                if (slot < 0) continue;
                consumeContentsSlot(contents, 0, 1);
                consumeContentsSlot(contents, 1, 1);
                addToContents(contents, slot, result);
                machineManager.setStoredContents(loc, contents);
            }
        }, 30L, 30L);
    }

    // ---- 真空溶解炉 (VACUUM_FURNACE) ------------------------------------

    private void startVacuumFurnaceTask() {
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            for (Location loc : new ArrayList<>(machineManager.getByType(MachineType.VACUUM_FURNACE))) {
                if (!loc.getChunk().isLoaded()) continue;
                if (!loc.getBlock().isBlockPowered() && !loc.getBlock().isBlockIndirectlyPowered()) continue;
                if (!energyManager.tryConsume(loc, MachineType.VACUUM_FURNACE)) continue;
                ItemStack[] contents = machineManager.getStoredContents(loc);
                if (contents == null) continue;
                if (isEmpty(contents[0]) || isEmpty(contents[1])) continue;
                String id0 = IntermediateMaterials.getId(contents[0]);
                String id1 = IntermediateMaterials.getId(contents[1]);
                ItemStack result = null;
                if (IntermediateMaterials.ADVANCED_ALLOY.equals(id0)
                        && IntermediateMaterials.PURE_IRON_EXTRACT.equals(id1))
                    result = IntermediateMaterials.build(IntermediateMaterials.HARDENED_ALLOY, 2);
                if (result == null) continue;
                int slot = findAddSlot(contents, result.getType(), 26);
                if (slot < 0) continue;
                consumeContentsSlot(contents, 0, 1);
                consumeContentsSlot(contents, 1, 1);
                addToContents(contents, slot, result);
                machineManager.setStoredContents(loc, contents);
            }
        }, 40L, 40L);
    }

    // ---- 高圧プレス機 (HIGH_PRESSURE_PRESS) -----------------------------

    private void startHighPressurePressTask() {
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            for (Location loc : new ArrayList<>(machineManager.getByType(MachineType.HIGH_PRESSURE_PRESS))) {
                if (!loc.getChunk().isLoaded()) continue;
                if (!loc.getBlock().isBlockPowered() && !loc.getBlock().isBlockIndirectlyPowered()) continue;
                if (!energyManager.tryConsume(loc, MachineType.HIGH_PRESSURE_PRESS)) continue;
                ItemStack[] contents = machineManager.getStoredContents(loc);
                if (contents == null) continue;
                if (isEmpty(contents[0]) || isEmpty(contents[1])) continue;
                // 石炭x8 + 硫酸 → 工業用ダイヤx1
                if (contents[0].getType() != Material.COAL || contents[0].getAmount() < 8) continue;
                if (!IntermediateMaterials.SULFURIC_ACID.equals(IntermediateMaterials.getId(contents[1]))) continue;
                ItemStack result = IntermediateMaterials.build(IntermediateMaterials.INDUSTRIAL_DIAMOND);
                int slot = findAddSlot(contents, result.getType(), 26);
                if (slot < 0) continue;
                consumeContentsSlot(contents, 0, 8);
                consumeContentsSlot(contents, 1, 1);
                addToContents(contents, slot, result);
                machineManager.setStoredContents(loc, contents);
            }
        }, 60L, 60L);
    }

    // ---- 原油採掘ドロップ ------------------------------------------------------

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMineCrudeOil(BlockBreakEvent event) {
        if (event.getPlayer().getGameMode() == org.bukkit.GameMode.CREATIVE) return;
        Material block = event.getBlock().getType();
        // 深層岩系をY=0以下で採掘時に低確率で原油ドロップ
        boolean isDeepSlate = block == Material.DEEPSLATE || block == Material.COBBLED_DEEPSLATE
                || block == Material.BLACKSTONE;
        if (!isDeepSlate || event.getBlock().getY() > 0) return;
        if (RANDOM.nextInt(200) != 0) return; // 0.5% ~ 200個採掘で1個
        event.getBlock().getWorld().dropItemNaturally(event.getBlock().getLocation(),
                IntermediateMaterials.build(IntermediateMaterials.CRUDE_OIL));
    }
}
