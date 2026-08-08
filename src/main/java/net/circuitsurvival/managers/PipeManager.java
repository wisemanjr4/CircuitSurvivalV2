package net.circuitsurvival.managers;

import net.circuitsurvival.CircuitSurvivalPlugin;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Container;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * アイテムパイプ・フィルターパイプ管理
 *
 * パイプ種別:
 *   PIPE        - 通常パイプ (全アイテムを転送、能動的に起動)
 *   INPUT_PIPE  - 入力パイプ (隣接コンテナから引き出す、能動起動)
 *   OUTPUT_PIPE - 出力パイプ (受動。隣接コンテナへ押し込む先として機能。自ら起動しない)
 *   FILTER      - フィルターパイプ (特定マテリアルのみ転送)
 *   WIRELESS_TX - ワイヤレス送信機
 *   WIRELESS_RX - ワイヤレス受信機
 *
 * パイプはFence (IRON_BARS) または CHAIN (INPUT=GOLDEN_CHAIN/OUTPUT=IRON_INGOT) で表現。
 */
public class PipeManager {

    public enum PipeType {
        PIPE,
        INPUT_PIPE,
        OUTPUT_PIPE,
        FILTER,
        WIRELESS_TX,
        WIRELESS_RX,
        FAST_PIPE,       // Phase 4: 2x転送
        FAST_INPUT_PIPE, // Phase 4: 2x引き込み
        FAST_OUTPUT_PIPE  // Phase 4: 高速終端
    }

    public static class PipeData {
        public PipeType type;
        public Material filterMaterial; // FILTERのとき使用
        public String channel;          // WIRELESSのとき使用

        public PipeData(PipeType type) { this.type = type; }
    }

    private static final BlockFace[] FACES = {
            BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST,
            BlockFace.UP, BlockFace.DOWN
    };

    // findDestinationRef の再帰上限 (スタックオーバーフロー防止)
    private static final int MAX_RECURSION_DEPTH = 256;

    private final CircuitSurvivalPlugin plugin;
    private final MachineManager machineManager;
    private final Map<Location, PipeData> pipes = new HashMap<>();
    private final Map<String, Set<Location>> wirelessTx = new HashMap<>();
    private final Map<String, Set<Location>> wirelessRx = new HashMap<>();

    private final File dataFile;
    private final int transferAmount;

    public PipeManager(CircuitSurvivalPlugin plugin, MachineManager machineManager) {
        this.plugin          = plugin;
        this.machineManager  = machineManager;
        this.dataFile        = new File(plugin.getDataFolder(), "pipes.yml");
        this.transferAmount  = plugin.getConfig().getInt("pipes.transfer_amount", 4);
        loadData();
    }

    // ---- 登録 / 削除 -----------------------------------------------------------

    public void registerPipe(Location loc, PipeData data) {
        Location bl = loc.toBlockLocation();
        // チャンネル変更時は古いエントリを削除
        PipeData existing = pipes.get(bl);
        if (existing != null && existing.channel != null && !existing.channel.equals(data.channel)) {
            wirelessTx.getOrDefault(existing.channel, Collections.emptySet()).remove(bl);
            wirelessRx.getOrDefault(existing.channel, Collections.emptySet()).remove(bl);
        }
        pipes.put(bl, data);
        if (data.type == PipeType.WIRELESS_TX && data.channel != null && !data.channel.isEmpty())
            wirelessTx.computeIfAbsent(data.channel, k -> new HashSet<>()).add(bl);
        if (data.type == PipeType.WIRELESS_RX && data.channel != null && !data.channel.isEmpty())
            wirelessRx.computeIfAbsent(data.channel, k -> new HashSet<>()).add(bl);
        saveData();
    }

    public PipeData removePipe(Location loc) {
        PipeData data = pipes.remove(loc.toBlockLocation());
        if (data != null) {
            if (data.channel != null) {
                if (wirelessTx.containsKey(data.channel)) wirelessTx.get(data.channel).remove(loc.toBlockLocation());
                if (wirelessRx.containsKey(data.channel)) wirelessRx.get(data.channel).remove(loc.toBlockLocation());
            }
            saveData();
        }
        return data;
    }

    public boolean isPipe(Location loc) {
        return pipes.containsKey(loc.toBlockLocation());
    }

    public PipeData getPipe(Location loc) {
        return pipes.get(loc.toBlockLocation());
    }

    // ---- 定期転送タスク ---------------------------------------------------------

    public void startTransferTask(int intervalTicks) {
        new BukkitRunnable() {
            @Override
            public void run() {
                tickAllPipes();
                tickWireless();
            }
        }.runTaskTimer(plugin, intervalTicks, intervalTicks);
    }

    /**
     * 全パイプをtick:
     *   PIPE / INPUT_PIPE / FILTER は能動的に起動 (SOURCE → DEST)
     *   OUTPUT_PIPE は受動 (起動しない)
     */
    private void tickAllPipes() {
        for (Map.Entry<Location, PipeData> entry : new HashMap<>(pipes).entrySet()) {
            Location loc = entry.getKey();
            if (!loc.getWorld().isChunkLoaded(loc.getBlockX() >> 4, loc.getBlockZ() >> 4)) continue;
            PipeData data = entry.getValue();
            if (data.type == PipeType.PIPE
                    || data.type == PipeType.INPUT_PIPE
                    || data.type == PipeType.FILTER) {
                transferFromPipe(loc, data);
            } else if (data.type == PipeType.FAST_PIPE
                    || data.type == PipeType.FAST_INPUT_PIPE) {
                transferFromPipe(loc, data);
                transferFromPipe(loc, data);
            }
        }
    }

    /**
     * ワイヤレス: 送信機 → 受信機へ転送
     */
    private void tickWireless() {
        // スナップショット反復 (並行変更対策)
        for (Map.Entry<String, Set<Location>> txEntry : new HashMap<>(wirelessTx).entrySet()) {
            String channel = txEntry.getKey();
            Set<Location> rxSet = wirelessRx.get(channel);
            if (rxSet == null || rxSet.isEmpty()) continue;

            for (Location txLoc : new HashSet<>(txEntry.getValue())) {
                if (!txLoc.getWorld().isChunkLoaded(txLoc.getBlockX() >> 4, txLoc.getBlockZ() >> 4)) continue;
                Inventory txInv = findAdjacentInventory(txLoc.getBlock(), null, false);
                if (txInv == null) continue;

                for (Location rxLoc : new HashSet<>(rxSet)) {
                    if (!rxLoc.getWorld().isChunkLoaded(rxLoc.getBlockX() >> 4, rxLoc.getBlockZ() >> 4)) continue;
                    Inventory rxInv = findAdjacentInventory(rxLoc.getBlock(), null, true);
                    if (rxInv == null) continue;
                    transferBetween(txInv, rxInv, null, transferAmount);
                    break;
                }
            }
        }
    }

    /**
     * パイプ1個のアイテム転送ロジック
     */
    private void transferFromPipe(Location pipeLoc, PipeData data) {
        Block pipeBlock = pipeLoc.getBlock();
        // フィルターパイプでフィルター未設定の場合は転送しない
        if (data.type == PipeType.FILTER && data.filterMaterial == null) return;
        Material filter = (data.type == PipeType.FILTER) ? data.filterMaterial : null;

        // -- ソース取得 (パイプに隣接するコンテナ/マシン) --
        InventoryRef sourceRef = findAdjacentRef(pipeBlock, filter, false);
        if (sourceRef == null) return;

        // -- 宛先取得 (パイプチェーンを辿る) --
        // 訪問済みパイプセット + ソース座標除外 でループ防止と誤宛先防止
        Set<Location> visited = new HashSet<>();
        visited.add(pipeLoc.toBlockLocation());
        InventoryRef destRef = findDestinationRef(pipeLoc, visited, filter, sourceRef.containerLocation());
        if (destRef == null) return;

        transferBetween(sourceRef.inventory(), destRef.inventory(), filter, transferAmount);

        // マシンインベントリはデータを書き戻す
        sourceRef.flush(machineManager);
        destRef.flush(machineManager);
    }

    // ---- コンテナ検索 ---------------------------------------------------------

    /**
     * パイプに隣接するコンテナ/マシンを探して InventoryRef を返す
     */
    private InventoryRef findAdjacentRef(Block pipeBlock, Material filter, boolean needSpace) {
        for (BlockFace face : FACES) {
            Block neighbor = pipeBlock.getRelative(face);
            InventoryRef ref = getRefAt(neighbor.getLocation(), filter, needSpace);
            if (ref != null) return ref;
        }
        return null;
    }

    /**
     * Inventory だけを返す簡易版 (ワイヤレス用)
     */
    private Inventory findAdjacentInventory(Block pipeBlock, Material filter, boolean needSpace) {
        InventoryRef ref = findAdjacentRef(pipeBlock, filter, needSpace);
        return ref != null ? ref.inventory() : null;
    }

    /**
     * パイプをたどって最終的なコンテナ/マシンを探す。
     * visited でループ防止、excludeLoc でソースコンテナを宛先候補から除外。
     */
    private InventoryRef findDestinationRef(Location current, Set<Location> visited,
                                            Material filter, Location excludeLoc) {
        return findDestinationRef(current, visited, filter, excludeLoc, 0);
    }

    private InventoryRef findDestinationRef(Location current, Set<Location> visited,
                                            Material filter, Location excludeLoc, int depth) {
        if (depth > MAX_RECURSION_DEPTH) return null;
        Block block = current.getBlock();

        for (BlockFace face : FACES) {
            Block neighbor = block.getRelative(face);
            Location neighborLoc = neighbor.getLocation().toBlockLocation();

            if (visited.contains(neighborLoc)) continue;
            if (excludeLoc != null && neighborLoc.equals(excludeLoc)) continue;

            if (pipes.containsKey(neighborLoc)) {
                // 並列パイプ防止: このパイプがソースコンテナ(excludeLoc)に直接隣接していれば
                // 別のパイプラインへの干渉を避けるためスキップする
                if (excludeLoc != null) {
                    Block nextPipe = neighborLoc.getBlock();
                    boolean isSiblingPipe = false;
                    for (BlockFace f : FACES) {
                        if (nextPipe.getRelative(f).getLocation().toBlockLocation().equals(excludeLoc)) {
                            isSiblingPipe = true;
                            break;
                        }
                    }
                    if (isSiblingPipe) continue;
                }
                visited.add(neighborLoc);
                InventoryRef result = findDestinationRef(neighborLoc, visited, filter, excludeLoc, depth + 1);
                if (result != null) return result;
            } else {
                InventoryRef ref = getRefAt(neighborLoc, filter, true);
                if (ref != null) return ref;
            }
        }
        return null;
    }

    /**
     * 指定座標にあるコンテナ/マシンから InventoryRef を生成。
     * 条件 (needSpace/hasItems) を満たさない場合は null。
     */
    private InventoryRef getRefAt(Location loc, Material filter, boolean needSpace) {
        Block block = loc.getBlock();

        // バニラコンテナ
        if (block.getState() instanceof Container container) {
            Inventory inv = container.getInventory();
            boolean ok = needSpace ? hasSpace(inv, filter) : hasItems(inv, filter);
            if (ok) return InventoryRef.ofVanilla(inv, loc);
        }

        // カスタムマシン
        if (machineManager.isMachine(loc)) {
            ItemStack[] contents = machineManager.getStoredContents(loc);
            if (contents != null) {
                // 一時Inventory を生成してチェック (54スロット機械にも対応)
                int size = contents.length;
                Inventory tempInv = plugin.getServer().createInventory(null, size);
                for (int i = 0; i < Math.min(contents.length, size); i++) {
                    if (contents[i] != null) tempInv.setItem(i, contents[i].clone());
                }
                boolean ok = needSpace ? hasSpace(tempInv, filter) : hasItems(tempInv, filter);
                if (ok) return InventoryRef.ofMachine(tempInv, loc);
            }
        }
        return null;
    }

    // ---- 転送 ---------------------------------------------------------------

    private void transferBetween(Inventory from, Inventory to, Material filter, int maxAmount) {
        int remaining = maxAmount;
        ItemStack[] contents = from.getContents();

        for (int i = 0; i < contents.length && remaining > 0; i++) {
            ItemStack item = contents[i];
            if (item == null || item.getType() == Material.AIR) continue;
            if (filter != null && item.getType() != filter) continue;

            int canMove = Math.min(item.getAmount(), remaining);
            ItemStack moving = item.clone();
            moving.setAmount(canMove);

            Map<Integer, ItemStack> leftover = to.addItem(moving);
            int notMoved = leftover.values().stream().mapToInt(ItemStack::getAmount).sum();
            int actualMoved = canMove - notMoved;

            if (actualMoved > 0) {
                remaining -= actualMoved;
                int newAmount = item.getAmount() - actualMoved;
                if (newAmount <= 0) {
                    from.setItem(i, null);
                } else {
                    ItemStack updated = item.clone();
                    updated.setAmount(newAmount);
                    from.setItem(i, updated);
                }
            }

            if (actualMoved < canMove) break;
        }
    }

    // ---- InventoryRef -------------------------------------------------------

    /**
     * コンテナまたはマシンのインベントリ参照。
     * マシンの場合は flush() でデータを MachineManager に書き戻す。
     */
    private record InventoryRef(Inventory inventory, Location machineLocation, Location containerLocation) {
        static InventoryRef ofVanilla(Inventory inv, Location loc) {
            return new InventoryRef(inv, null, loc.toBlockLocation());
        }
        static InventoryRef ofMachine(Inventory inv, Location loc) {
            Location bl = loc.toBlockLocation();
            return new InventoryRef(inv, bl, bl);
        }
        void flush(MachineManager mgr) {
            if (machineLocation != null && mgr != null) {
                mgr.setStoredContents(machineLocation, inventory.getContents());
            }
        }
    }

    // ---- ヘルパー -----------------------------------------------------------

    private boolean hasSpace(Inventory inv, Material filter) {
        for (ItemStack item : inv.getContents()) {
            if (item == null || item.getType() == Material.AIR) return true;
            if (filter != null && item.getType() == filter && item.getAmount() < item.getMaxStackSize()) return true;
        }
        return false;
    }

    private boolean hasItems(Inventory inv, Material filter) {
        for (ItemStack item : inv.getContents()) {
            if (item != null && item.getType() != Material.AIR) {
                if (filter == null || item.getType() == filter) return true;
            }
        }
        return false;
    }

    // ---- データ永続化 -----------------------------------------------------------

    private void saveData() {
        YamlConfiguration cfg = new YamlConfiguration();
        int i = 0;
        for (Map.Entry<Location, PipeData> entry : pipes.entrySet()) {
            Location loc = entry.getKey();
            PipeData d = entry.getValue();
            String base = "pipes." + i;
            cfg.set(base + ".world", loc.getWorld().getName());
            cfg.set(base + ".x", loc.getBlockX());
            cfg.set(base + ".y", loc.getBlockY());
            cfg.set(base + ".z", loc.getBlockZ());
            cfg.set(base + ".type", d.type.name());
            if (d.filterMaterial != null) cfg.set(base + ".filter", d.filterMaterial.name());
            if (d.channel != null) cfg.set(base + ".channel", d.channel);
            i++;
        }
        try { cfg.save(dataFile); } catch (IOException e) { e.printStackTrace(); }
    }

    private void loadData() {
        if (!dataFile.exists()) return;
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(dataFile);
        if (!cfg.contains("pipes")) return;
        for (String key : cfg.getConfigurationSection("pipes").getKeys(false)) {
            String base = "pipes." + key;
            String worldName = cfg.getString(base + ".world");
            org.bukkit.World world = plugin.getServer().getWorld(worldName);
            if (world == null) continue;
            int x = cfg.getInt(base + ".x"),
                y = cfg.getInt(base + ".y"),
                z = cfg.getInt(base + ".z");
            PipeType type;
            try { type = PipeType.valueOf(cfg.getString(base + ".type")); }
            catch (IllegalArgumentException ignored) { continue; }

            PipeData d = new PipeData(type);
            if (cfg.contains(base + ".filter")) {
                try { d.filterMaterial = Material.valueOf(cfg.getString(base + ".filter")); }
                catch (IllegalArgumentException ignored) {}
            }
            if (cfg.contains(base + ".channel")) d.channel = cfg.getString(base + ".channel");

            Location loc = new Location(world, x, y, z);
            pipes.put(loc, d);
            if (type == PipeType.WIRELESS_TX && d.channel != null)
                wirelessTx.computeIfAbsent(d.channel, k -> new HashSet<>()).add(loc);
            if (type == PipeType.WIRELESS_RX && d.channel != null)
                wirelessRx.computeIfAbsent(d.channel, k -> new HashSet<>()).add(loc);
        }
    }
}
