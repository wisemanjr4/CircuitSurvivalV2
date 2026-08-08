package net.circuitsurvival.managers;

import net.circuitsurvival.CircuitSurvivalPlugin;
import net.circuitsurvival.machines.MachineType;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * EN (エネルギー) 管理システム。
 * 全機械はENがないと動作しない。
 * ENは隣接するENERGY_CELL/GENERATOR/SOLAR_PANELから自動転送される。
 */
public class EnergyManager {

    private final CircuitSurvivalPlugin plugin;
    private final MachineManager machineManager;
    private final File dataFile;

    // 機械ごとの現在EN
    private final Map<String, Integer> energyStore = new HashMap<>();
    // 機械ごとの最大EN (未設定の場合はデフォルト値を使用)
    private final Map<String, Integer> maxEnergyStore = new HashMap<>();

    /** デフォルト最大EN */
    public static final int DEFAULT_MAX_ENERGY = 1000;

    /** 機械が隣接ブロックにENをプッシュする間隔(tick) */
    public static final int DISTRIBUTE_INTERVAL = 5;

    /** EN消費/生成のベース値 (config倍率) */
    private int energyMultiplier = 1;
    /** 1tickあたりのEN減衰 (送電ロス) */
    private int passiveDrain = 1;

    public EnergyManager(CircuitSurvivalPlugin plugin, MachineManager machineManager) {
        this.plugin = plugin;
        this.machineManager = machineManager;
        this.dataFile = new File(plugin.getDataFolder(), "energy.yml");
        this.energyMultiplier = plugin.getConfig().getInt("energy.multiplier", 1);
        this.passiveDrain = plugin.getConfig().getInt("energy.passive_drain", 1);
        loadData();
    }

    // ---- EN取得/設定 ---------------------------------------------------------

    /** 現在のEN量を取得 (デフォルト0) */
    public int getEnergy(Location loc) {
        return energyStore.getOrDefault(locKey(loc), 0);
    }

    /** 最大EN量を取得 */
    public int getMaxEnergy(Location loc) {
        return maxEnergyStore.getOrDefault(locKey(loc), getDefaultMax(loc));
    }

    /** 最大ENを設定 (蓄電機用) */
    public void setMaxEnergy(Location loc, int max) {
        maxEnergyStore.put(locKey(loc), max);
    }

    /** ENを直接設定 */
    public void setEnergy(Location loc, int amount) {
        setEnergy(loc, amount, true);
    }

    private void setEnergy(Location loc, int amount, boolean clamp) {
        int max = getMaxEnergy(loc);
        int val = clamp ? Math.max(0, Math.min(amount, max)) : amount;
        energyStore.put(locKey(loc), val);
    }

    /** 機械のENバッファがmaxに達したか */
    public boolean isFull(Location loc) {
        return getEnergy(loc) >= getMaxEnergy(loc);
    }

    /** ENを生成する (上限クリップ) */
    public void generate(Location loc, int amount) {
        String key = locKey(loc);
        int cur = energyStore.getOrDefault(key, 0);
        int max = getMaxEnergy(loc);
        int val = Math.min(cur + amount, max);
        if (val != cur) energyStore.put(key, val);
    }

    /**
     * ENを消費する。
     * 自身のバッファが不足する場合、隣接するENERGY_CELL/GENERATORから自動転送。
     * @return 消費成功=true (不足=false)
     */
    public boolean tryConsume(Location loc, int amount) {
        String key = locKey(loc);
        int cur = energyStore.getOrDefault(key, 0);
        if (cur >= amount) {
            energyStore.put(key, cur - amount);
            return true;
        }
        // 不足分を隣接から引き出す (pullFromAdjacentが直接 energyStore に加算する)
        int deficit = amount - cur;
        int pulled = pullFromAdjacent(loc.getBlock(), deficit);
        if (pulled <= 0) return false;
        int newCur = energyStore.getOrDefault(key, 0);
        if (newCur < amount) return false; // 引き出したENが実際に届かなかった
        energyStore.put(key, Math.max(0, newCur - amount));
        return true;
    }

    // ---- EN転送 (隣接ブロック間) ---------------------------------------------

    private static final BlockFace[] FACES = {
            BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST,
            BlockFace.UP, BlockFace.DOWN
    };

    /**
     * 隣接するENストレージブロックからENを引き出す。
     * ENERGY_CELL / GENERATOR / SOLAR_PANEL / HV_CELL から直接、
     * ENERGY_CABLE 経由で再帰的に引き出し可能。
     * @return 実際に引き出せた量
     */
    private int pullFromAdjacent(Block block, int needed) {
        return pullFromAdjacent(block, needed, new HashSet<>(), locKey(block.getLocation()));
    }

    private int pullFromAdjacent(Block block, int needed, Set<String> visited, String callerKey) {
        String blockKey = locKey(block.getLocation());
        if (!visited.add(blockKey)) return 0;
        int pulled = 0;
        for (BlockFace face : FACES) {
            if (pulled >= needed) break;
            Block adj = block.getRelative(face);
            Location adjLoc = adj.getLocation().toBlockLocation();
            if (!machineManager.isMachine(adjLoc)) continue;
            MachineType type = machineManager.getType(adjLoc);

            if (type == MachineType.ENERGY_CABLE) {
                // ケーブル: 自身のENを引き出し、さらに先へ再帰
                String cableKey = locKey(adjLoc);
                int cableEn = energyStore.getOrDefault(cableKey, 0);
                if (cableEn > passiveDrain) {
                    int xfer = Math.min(needed - pulled, cableEn - passiveDrain);
                    energyStore.put(cableKey, cableEn - xfer);
                    energyStore.merge(callerKey, xfer, Integer::sum);
                    pulled += xfer;
                }
                if (pulled < needed) {
                    pulled += pullFromAdjacent(adj, needed - pulled, visited, callerKey);
                }
                continue;
            }

            if (!isEnergySource(type)) continue;
            String adjKey = locKey(adjLoc);
            int adjEn = energyStore.getOrDefault(adjKey, 0);
            if (adjEn <= passiveDrain) continue;
            int transfer = Math.min(needed - pulled, adjEn - passiveDrain);
            if (transfer <= 0) continue;
            energyStore.put(adjKey, adjEn - transfer);
            energyStore.merge(callerKey, transfer, Integer::sum);
            pulled += transfer;
        }
        return pulled;
    }

    /**
     * 蓄電ブロックから隣接する消費側ブロックへENを分配する (能動的プッシュ)。
     * ENERGY_CELL用: 蓄電量が閾値を超えていたら隣接に分配。
     */
    public void distributeFrom(Location loc) {
        distributeFrom(loc, new HashSet<>());
    }

    private void distributeFrom(Location loc, Set<String> visited) {
        String key = locKey(loc);
        if (!visited.add(key)) return;
        int cur = energyStore.getOrDefault(key, 0);
        if (cur <= passiveDrain * 2) return;

        MachineType type = machineManager.getType(loc);
        int threshold;
        if (type == MachineType.ENERGY_CELL || type == MachineType.HV_CELL) {
            threshold = getMaxEnergy(loc) / 2;
        } else {
            threshold = passiveDrain; // 発電機は即分配 (余剰分をすべて押し出す)
        }
        if (cur <= threshold) return;

        int distributable = cur - threshold;
        for (BlockFace face : FACES) {
            if (distributable <= passiveDrain) break;
            Block adj = loc.getBlock().getRelative(face);
            Location adjLoc = adj.getLocation().toBlockLocation();
            if (!machineManager.isMachine(adjLoc)) continue;
            MachineType adjType = machineManager.getType(adjLoc);
            // ENERGY_CELL / HV_CELL: 相互充填
            if (adjType == MachineType.ENERGY_CELL || adjType == MachineType.HV_CELL) {
                String adjKey = locKey(adjLoc);
                int adjCur = energyStore.getOrDefault(adjKey, 0);
                int adjMax = getMaxEnergy(adjLoc);
                int space = adjMax - adjCur;
                if (space <= 0) continue;
                int give = Math.min(distributable, space);
                give = Math.min(give, 64);
                energyStore.put(key, cur - give);
                energyStore.put(adjKey, adjCur + give);
                cur -= give;
                distributable -= give;
            } else if (adjType == MachineType.ENERGY_CABLE) {
                // ケーブルに給電 → さらに先へ再帰的に押し出し
                String adjKey = locKey(adjLoc);
                int adjCur = energyStore.getOrDefault(adjKey, 0);
                int adjMax = getMaxEnergy(adjLoc);
                int space = adjMax - adjCur;
                if (space > 0) {
                    int give = Math.min(distributable, space);
                    give = Math.min(give, 512);
                    energyStore.put(key, cur - give);
                    energyStore.put(adjKey, adjCur + give);
                    cur -= give;
                    distributable -= give;
                }
                // ケーブルがENを持っていれば先へ再帰分配
                distributeFrom(adjLoc, visited);
            } else if (needsEnergy(adjType)) {
                int adjEn = getEnergy(adjLoc);
                int adjMax = getMaxEnergy(adjLoc);
                if (adjEn >= adjMax / 2) continue;
                int space = adjMax - adjEn;
                if (space <= 0) continue;
                int give = Math.min(distributable, space);
                give = Math.min(give, 32);
                energyStore.put(key, cur - give);
                generate(adjLoc, give);
                cur -= give;
                distributable -= give;
            }
        }
    }

    /** EN供給可能な機械種別 */
    public static boolean isEnergySource(MachineType type) {
        return type == MachineType.ENERGY_CELL
                || type == MachineType.GENERATOR
                || type == MachineType.SOLAR_PANEL
                || type == MachineType.HV_CELL
                || type == MachineType.ENERGY_CABLE
                || type == MachineType.COMBUSTION_GENERATOR
                || type == MachineType.WIRELESS_CHARGER
                || type == MachineType.THERMAL_GENERATOR
                || type == MachineType.CHARGER
                || type == MachineType.WATER_GENERATOR
                || type == MachineType.WIND_GENERATOR;
    }

    /** ENを消費する機械種別 (発電系以外) */
    public static boolean needsEnergy(MachineType type) {
        if (type == null || isEnergySource(type)) return false;
        return switch (type) {
            case TIMER, ENTITY_DETECTOR, CHUNK_LOADER, BLOCK_PLACER,
                 BLOCK_BREAKER, IGNITER, RIGHT_CLICKER,
                  STORAGE_CONTROLLER,
                 VACUUM_HOPPER_CTRL -> false; // RS制御のみ or パッシブ
            default -> true; // 実質全機械が要EN
        };
    }

    /** 機械種別ごとの1回の操作あたりEN消費量 */
    public static int getEnergyCost(MachineType type) {
        if (type == null) return 0;
        return switch (type) {
            case CRUSHER -> 10;
            case COMPRESSOR -> 15;
            case AUTO_SMELTER -> 8;
            case VACUUM_HOPPER -> 5;
            case AUTO_CRAFTER -> 20;
            case ITEM_SORTER -> 3;
            case AUTO_FARMER -> 12;
            case AUTO_BREWER -> 15;
            case ITEM_ROUTER -> 3;
            case BLOCK_TRANSMUTER -> 25;
            case FLUID_COLLECTOR -> 10;
            case BONE_MEALER -> 5;
            case WOODCUTTER -> 8;
            case XP_CONVERTER -> 30;
            case FAST_HOPPER, VERT_FAST_HOPPER -> 2;
            case BULK_DROPPER -> 2;
            case AUTO_FISHER -> 20;
            case VERTICAL_ELEVATOR -> 5;
            case COOKING_STATION -> 15;
            case PIXEL_FORGE -> 30;
            case CUSTOM_CRAFTER -> 25;
            case PULVERIZER -> 30;
            case ELECTRIC_FURNACE -> 15;
            case AUTO_ANVIL -> 20;
            case AUTO_SHEARER -> 8;
            case MINER -> 15;
            case STORAGE_DRUM -> 1;
            case AUTO_ENCHANTER -> 50;
            case VOID_MINER -> 200;
            case INDUCTION_FURNACE -> 20;
            case CENTRIFUGE -> 25;
             case COMBUSTION_GENERATOR -> 0;
             case ORE_PROCESSOR -> 40;
             case MATERIALIZER -> 50;
             case RECYCLER -> 30;
             case WIRELESS_CHARGER -> 0;
             case AUTO_DISENCHANTER -> 40;
             case CHARGER -> 20;
             case THERMAL_GENERATOR -> 0;
             case ADVANCED_ASSEMBLER -> 50;
              case NEUTRON_COMPRESSOR -> 100;
              case WASHING_MACHINE -> 20;
              case DISTILLATION_TOWER -> 40;
              case CHEMICAL_REACTOR -> 40;
              case VACUUM_FURNACE -> 60;
              case HIGH_PRESSURE_PRESS -> 80;
              case MOBILE_PLATFORM -> 0;
              default -> 0;
        };
    }

    // ---- デフォルト最大EN ----------------------------------------------------

    private int getDefaultMax(Location loc) {
        MachineType type = machineManager.getType(loc);
        if (type == null) return DEFAULT_MAX_ENERGY;
        return getDefaultMaxForType(type);
    }

    /** 機械種別ごとのデフォルト最大EN (public static) */
    public static int getDefaultMaxForType(MachineType type) {
        if (type == null) return DEFAULT_MAX_ENERGY;
        return switch (type) {
            case ENERGY_CELL -> 50000;
            case HV_CELL -> 200000;
            case GENERATOR, SOLAR_PANEL -> 2000;
            case STORAGE_DRUM -> 500;
            case CRUSHER, COMPRESSOR, AUTO_SMELTER -> 1500;
            case ENERGY_CABLE -> 100;
            case INDUCTION_FURNACE -> 2000;
            case CENTRIFUGE -> 1500;
            case COMBUSTION_GENERATOR -> 3000;
            case ORE_PROCESSOR -> 3000;
            case MATERIALIZER -> 3000;
            case RECYCLER -> 2000;
            case WIRELESS_CHARGER -> 5000;
            case AUTO_DISENCHANTER -> 2000;
            case MOBILE_PLATFORM -> 100;
            case VOID_MINER -> 10000;
            case AUTO_ENCHANTER -> 5000;
            case NEUTRON_COMPRESSOR -> 5000;
            case ADVANCED_ASSEMBLER -> 3000;
            case THERMAL_GENERATOR -> 2000;
            case XP_CONVERTER -> 3000;
            case CHARGER -> 10000;
            case WASHING_MACHINE -> 2000;
            case DISTILLATION_TOWER -> 3000;
            case CHEMICAL_REACTOR -> 3000;
            case VACUUM_FURNACE -> 4000;
            case HIGH_PRESSURE_PRESS -> 5000;
            case WATER_GENERATOR, WIND_GENERATOR -> 2000;
            case CRAFTER_CONTROLLER -> 2000;
            default -> 1000;
        };
    }

    // ---- データ永続化 ---------------------------------------------------------

    private String locKey(Location loc) {
        return loc.getWorld().getName()
                + ":" + loc.getBlockX()
                + ":" + loc.getBlockY()
                + ":" + loc.getBlockZ();
    }

    /** 機械のENデータを削除 (機械破壊時) */
    public void removeData(Location loc) {
        String key = locKey(loc);
        energyStore.remove(key);
        maxEnergyStore.remove(key);
    }

    /** 機械のENデータを移動 (ピストン移動時) */
    public void moveData(Location from, Location to) {
        String fromKey = locKey(from);
        String toKey = locKey(to);
        Integer en = energyStore.remove(fromKey);
        Integer max = maxEnergyStore.remove(fromKey);
        if (en != null) energyStore.put(toKey, en);
        if (max != null) maxEnergyStore.put(toKey, max);
    }

    public void saveData() {
        YamlConfiguration cfg = new YamlConfiguration();
        int i = 0;
        for (Map.Entry<String, Integer> e : energyStore.entrySet()) {
            cfg.set("energy." + i + ".key", e.getKey());
            cfg.set("energy." + i + ".value", e.getValue());
            Integer max = maxEnergyStore.get(e.getKey());
            if (max != null) cfg.set("energy." + i + ".max", max);
            i++;
        }
        try { cfg.save(dataFile); }
        catch (IOException ex) { ex.printStackTrace(); }
    }

    public void loadData() {
        if (!dataFile.exists()) return;
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(dataFile);
        if (!cfg.contains("energy")) return;
        for (String key : cfg.getConfigurationSection("energy").getKeys(false)) {
            String base = "energy." + key;
            String locKey = cfg.getString(base + ".key");
            if (locKey == null) continue;
            energyStore.put(locKey, cfg.getInt(base + ".value", 0));
            if (cfg.contains(base + ".max")) {
                maxEnergyStore.put(locKey, cfg.getInt(base + ".max"));
            }
        }
    }

    /** passive drain 値 */
    public int getPassiveDrain() { return passiveDrain; }

    /**
     * 機械種別に対応したEN消費を試行する。
     * needsEnergy=false の機械は常に成功 (EN不要)。
     */
    public boolean tryConsume(Location loc, MachineType type) {
        if (!needsEnergy(type)) return true;
        int cost = getEnergyCost(type) * energyMultiplier;
        return tryConsume(loc, cost);
    }

    /**
     * 機械設置時のENデータ初期化。
     */
    public void initData(Location loc, MachineType type) {
        String key = locKey(loc);
        if (!energyStore.containsKey(key)) {
            energyStore.put(key, 0);
            maxEnergyStore.put(key, getDefaultMax(loc));
        }
    }
}
