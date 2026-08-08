package net.circuitsurvival.managers;

import net.circuitsurvival.CircuitSurvivalPlugin;
import net.circuitsurvival.machines.MachineType;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * カスタムマシンの設置位置とインベントリを管理
 */
public class MachineManager {

    // ---- MachineInvHolder : マシンGUI用 InventoryHolder ----------------------

    public static class MachineInvHolder implements InventoryHolder {
        public final Location loc;
        public final MachineType type;
        public int page = 0; // ストレージコントローラー用ページ（プレイヤーごとに独立）
        private Inventory inventory;

        public MachineInvHolder(Location loc, MachineType type) {
            this.loc  = loc;
            this.type = type;
        }

        public void setInventory(Inventory inv) { this.inventory = inv; }

        @Override
        public @NotNull Inventory getInventory() { return inventory; }
    }

    // ---- フィールド ----------------------------------------------------------

    private final CircuitSurvivalPlugin plugin;
    private final Map<Location, MachineType> machines = new HashMap<>();
    /** タイプ別インデックス: getAll()の代わりにgetByType()で絞り込みアクセス可能 */
    private final EnumMap<MachineType, Set<Location>> machinesByType = new EnumMap<>(MachineType.class);
    private final File dataFile;

    /**
     * マシンの内部インベントリ (CRUSHER/COMPRESSOR/DUPLICATOR/AUTO_CRAFTER 用)
     * キー: "world:x:y:z"  値: 27スロットのItemStack[]
     */
    private final Map<String, ItemStack[]> machineContents = new HashMap<>();
    /** 現在プレイヤーが開いているGUI (タスクからのリアルタイム反映用) */
    private final Map<String, Inventory> liveInventories = new HashMap<>();


    public MachineManager(CircuitSurvivalPlugin plugin) {
        this.plugin   = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "machines.yml");
        loadData();
    }

    // ---- マシン登録 / 削除 --------------------------------------------------

    public void register(Location loc, MachineType type) {
        Location bl = loc.toBlockLocation();
        machines.put(bl, type);
        machinesByType.computeIfAbsent(type, k -> new HashSet<>()).add(bl);
        if (hasMachineInventory(type)) {
            machineContents.putIfAbsent(locKey(loc), new ItemStack[getInventorySize(type)]);
        }
        saveData();
    }

    /** マシン種別ごとのインベントリスロット数 */
    public static int getInventorySize(MachineType type) {
        return switch (type) {
            case STORAGE_DRUM -> 54;
            default -> 27;
        };
    }

    public MachineType remove(Location loc) {
        Location bl = loc.toBlockLocation();
        MachineType t = machines.remove(bl);
        if (t != null) {
            Set<Location> set = machinesByType.get(t);
            if (set != null) set.remove(bl);
            saveData();
        }
        return t;
    }

    /** 指定タイプのマシン座標セットを返す (空セットを返す、null なし) */
    public Set<Location> getByType(MachineType type) {
        return machinesByType.getOrDefault(type, Collections.emptySet());
    }

    public boolean isMachine(Location loc) {
        return machines.containsKey(loc.toBlockLocation());
    }

    public MachineType getType(Location loc) {
        return machines.get(loc.toBlockLocation());
    }

    public Map<Location, MachineType> getAll() {
        return Collections.unmodifiableMap(machines);
    }

    // ---- マシンインベントリ --------------------------------------------------

    /**
     * マシン内部インベントリの中身を返す。
     * GUIが開かれている場合はライブGUIの現在状態をスナップショットとして返す。
     * これにより機械タスクが常にプレイヤーの見ている状態と同期して動作する。
     * GUIが閉じている場合はmachineContentsを返す。
     */
    public ItemStack[] getStoredContents(Location loc) {
        String key = locKey(loc);
        ItemStack[] raw;
        Inventory live = liveInventories.get(key);
        if (live != null) {
            int size = live.getSize();
            raw = new ItemStack[size];
            for (int i = 0; i < size; i++) raw[i] = live.getItem(i);
        } else {
            raw = machineContents.get(key);
        }
        if (raw == null) return null;
        // 装飾PDCタグ付きアイテムをnull化 (ホッパー等で吸われないように)
        ItemStack[] result = raw.clone();
        for (int i = 0; i < result.length; i++) {
            if (result[i] != null && result[i].hasItemMeta()
                    && result[i].getItemMeta().getPersistentDataContainer()
                        .has(org.bukkit.NamespacedKey.fromString("circuitsurvival:decoration"),
                                org.bukkit.persistence.PersistentDataType.BOOLEAN)) {
                result[i] = null;
            }
        }
        return result;
    }

    /**
     * マシン内部インベントリの中身を上書き保存する。
     * GUIが開かれている場合はGUIにも反映する (リアルタイム同期)。
     * ガラスペインのデコレーションスロットはnullで上書きしない。
     */
    public void setStoredContents(Location loc, ItemStack[] contents) {
        // 装飾PDCタグ付きアイテムをnull化してから保存
        if (contents != null) {
            for (int i = 0; i < contents.length; i++) {
                if (contents[i] != null && contents[i].hasItemMeta()
                        && contents[i].getItemMeta().getPersistentDataContainer()
                            .has(org.bukkit.NamespacedKey.fromString("circuitsurvival:decoration"),
                                    org.bukkit.persistence.PersistentDataType.BOOLEAN)) {
                    contents[i] = null;
                }
            }
        }
        machineContents.put(locKey(loc), contents);
        Inventory live = liveInventories.get(locKey(loc));
        if (live != null) {
            int n = Math.min(contents == null ? 0 : contents.length, live.getSize());
            for (int i = 0; i < n; i++) {
                // nullで装飾アイテムを上書きしない (PDCで判定)
                if (contents[i] == null) {
                    ItemStack existing = live.getItem(i);
                    if (existing != null && existing.hasItemMeta()
                            && existing.getItemMeta().getPersistentDataContainer()
                                .has(org.bukkit.NamespacedKey.fromString("circuitsurvival:decoration"),
                                        org.bukkit.persistence.PersistentDataType.BOOLEAN)) continue;
                }
                live.setItem(i, contents[i]);
            }
        }
    }

    /** GUIを開いた際に登録してリアルタイム同期を有効化する */
    public void registerLiveInventory(Location loc, Inventory inv) {
        liveInventories.put(locKey(loc), inv);
    }

    /** GUIを閉じた際に登録解除する */
    public void unregisterLiveInventory(Location loc) {
        liveInventories.remove(locKey(loc));
    }

    /**
     * scheduleGuiSyncからの呼び出し用:
     * liveInventoriesが存在しない（GUIが閉じた後）場合のみmachineContentsを更新する。
     * GUIが開いている間はgetStoredContentsが直接liveInventoriesから読み取るため更新不要。
     */
    public void syncContentsFromGui(Location loc, ItemStack[] snap) {
        String key = locKey(loc);
        if (liveInventories.containsKey(key)) return; // GUIが開いている → スキップ
        machineContents.put(key, snap);
    }

    /**
     * マシン破壊時: 内容を取り出して削除
     */
    public ItemStack[] removeStoredContents(Location loc) {
        ItemStack[] raw = machineContents.remove(locKey(loc));
        if (raw == null) return null;
        for (int i = 0; i < raw.length; i++) {
            if (raw[i] != null && raw[i].hasItemMeta()
                    && raw[i].getItemMeta().getPersistentDataContainer()
                        .has(org.bukkit.NamespacedKey.fromString("circuitsurvival:decoration"),
                                org.bukkit.persistence.PersistentDataType.BOOLEAN)) {
                raw[i] = null;
            }
        }
        return raw;
    }

    /**
     * GUIを開く際に使う Inventory を生成し、保存中の内容を反映して返す。
     * 毎回新しい Inventory を生成するため、呼び出し側で close 時に setStoredContents すること。
     */
    public Inventory createGui(Location loc, MachineType type, String title) {
        MachineInvHolder holder = new MachineInvHolder(loc, type);
        int size = getInventorySize(type);
        Inventory inv = plugin.getServer().createInventory(holder, size, Component.text(title));
        holder.setInventory(inv);

        ItemStack[] saved = machineContents.get(locKey(loc));
        if (saved != null) {
            for (int i = 0; i < Math.min(size, saved.length); i++) {
                if (saved[i] != null) {
                    // decorationアイテムは復元しない (パネル/ボタン/説明本)
                    if (!saved[i].hasItemMeta()
                            || !saved[i].getItemMeta().getPersistentDataContainer()
                                .has(NamespacedKey.fromString("circuitsurvival:decoration"),
                                        PersistentDataType.BOOLEAN)) {
                        inv.setItem(i, saved[i].clone());
                    }
                }
            }
        }
        return inv;
    }

    // ---- ユーティリティ ------------------------------------------------------

    /** このマシン種別は内部インベントリを持つか */
    public static boolean hasMachineInventory(MachineType type) {
        return switch (type) {
            case CRUSHER, COMPRESSOR,
                 AUTO_CRAFTER,
                 AUTO_SMELTER, MINER, VACUUM_HOPPER,
                 ITEM_SORTER, AUTO_FARMER,
                 AUTO_BREWER, ITEM_ROUTER, FLUID_COLLECTOR,
                 BONE_MEALER, VACUUM_HOPPER_CTRL,
                 STORAGE_DRUM, WOODCUTTER, VERTICAL_ELEVATOR,
                 XP_CONVERTER, FAST_HOPPER, VERT_FAST_HOPPER, BULK_DROPPER,
                 AUTO_FISHER, BLOCK_TRANSMUTER,
                 COOKING_STATION, PIXEL_FORGE,
                 CUSTOM_CRAFTER, PULVERIZER, ELECTRIC_FURNACE, AUTO_ANVIL,
                 AUTO_SHEARER,
                 GENERATOR, ENERGY_CELL, CHARGER,
                 AUTO_ENCHANTER, VOID_MINER,
                 INDUCTION_FURNACE, CENTRIFUGE, COMBUSTION_GENERATOR, ORE_PROCESSOR,
                 MATERIALIZER, RECYCLER, AUTO_DISENCHANTER,
                 ADVANCED_ASSEMBLER, NEUTRON_COMPRESSOR, HV_CELL, WASHING_MACHINE,
                 CRAFTER_CONTROLLER, DISTILLATION_TOWER, CHEMICAL_REACTOR,
                 VACUUM_FURNACE, HIGH_PRESSURE_PRESS -> true;
            default -> false;
        };
    }

    public String locKey(Location loc) {
        return loc.getWorld().getName()
                + ":" + loc.getBlockX()
                + ":" + loc.getBlockY()
                + ":" + loc.getBlockZ();
    }

    // ---- データ永続化 ---------------------------------------------------------

    private void saveData() {
        YamlConfiguration cfg = new YamlConfiguration();
        int i = 0;
        for (Map.Entry<Location, MachineType> e : machines.entrySet()) {
            Location loc = e.getKey();
            MachineType type = e.getValue();
            String base = "machines." + i;
            cfg.set(base + ".world", loc.getWorld().getName());
            cfg.set(base + ".x", loc.getBlockX());
            cfg.set(base + ".y", loc.getBlockY());
            cfg.set(base + ".z", loc.getBlockZ());
            cfg.set(base + ".type", type.name());

            // インベントリ保存
            if (hasMachineInventory(type)) {
                ItemStack[] contents = machineContents.get(locKey(loc));
                if (contents != null) {
                    for (int s = 0; s < contents.length; s++) {
                        if (contents[s] != null && !contents[s].getType().isAir()) {
                            cfg.set(base + ".inv." + s, contents[s]);
                        }
                    }
                }
            }
            i++;
        }
        try { cfg.save(dataFile); } catch (IOException ex) { ex.printStackTrace(); }
    }

    private void loadData() {
        if (!dataFile.exists()) return;
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(dataFile);

        if (!cfg.contains("machines")) return;
        for (String key : cfg.getConfigurationSection("machines").getKeys(false)) {
            String base = "machines." + key;
            org.bukkit.World world = plugin.getServer().getWorld(cfg.getString(base + ".world"));
            if (world == null) continue;
            int x = cfg.getInt(base + ".x"),
                y = cfg.getInt(base + ".y"),
                z = cfg.getInt(base + ".z");
            MachineType type;
            try { type = MachineType.valueOf(cfg.getString(base + ".type")); }
            catch (IllegalArgumentException ignored) { continue; }

            Location loc = new Location(world, x, y, z);
            machines.put(loc, type);
            machinesByType.computeIfAbsent(type, k -> new HashSet<>()).add(loc);

            // インベントリ復元
            int invSize = getInventorySize(type);
            if (hasMachineInventory(type) && cfg.contains(base + ".inv")) {
                ItemStack[] contents = new ItemStack[invSize];
                var invSec = cfg.getConfigurationSection(base + ".inv");
                if (invSec != null) {
                    for (String slot : invSec.getKeys(false)) {
                        try {
                            int s = Integer.parseInt(slot);
                            ItemStack item = cfg.getItemStack(base + ".inv." + slot);
                            if (item != null && s < invSize) contents[s] = item;
                        } catch (NumberFormatException ignored) {}
                    }
                }
                machineContents.put(locKey(loc), contents);
            } else if (hasMachineInventory(type)) {
                machineContents.put(locKey(loc), new ItemStack[invSize]);
            }
        }
    }

    /** onDisable 時に全データを保存 */
    public void saveAll() {
        saveData();
    }
}
