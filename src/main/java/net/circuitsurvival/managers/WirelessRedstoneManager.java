package net.circuitsurvival.managers;

import net.circuitsurvival.CircuitSurvivalPlugin;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * ワイヤレスレッドストーン管理
 *
 * 送信機 (WIRELESS_RS_TX): 隣接するレッドストーン信号を読み取り、チャンネルに送信
 * 受信機 (WIRELESS_RS_RX): チャンネルの信号状態に応じて出力側にレッドストーンブロックを設置/除去
 */
public class WirelessRedstoneManager {

    public enum RSBlockType { TRANSMITTER, RECEIVER }

    public static class RSBlock {
        public RSBlockType type;
        public String channel;
        public BlockFace outputFace; // 受信機の出力方向

        public RSBlock(RSBlockType type, String channel, BlockFace outputFace) {
            this.type = type;
            this.channel = channel;
            this.outputFace = outputFace;
        }
    }

    private final CircuitSurvivalPlugin plugin;
    private final Map<Location, RSBlock> rsBlocks = new HashMap<>();
    // チャンネル → 現在の信号状態
    private final Map<String, Boolean> channelState = new HashMap<>();
    private final File dataFile;

    private static final BlockFace[] FACES = {
            BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST, BlockFace.UP, BlockFace.DOWN
    };

    public WirelessRedstoneManager(CircuitSurvivalPlugin plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "wireless_rs.yml");
        loadData();
    }

    public void register(Location loc, RSBlock block) {
        rsBlocks.put(loc.toBlockLocation(), block);
        saveData();
    }

    public RSBlock remove(Location loc) {
        RSBlock b = rsBlocks.remove(loc.toBlockLocation());
        if (b != null) saveData();
        return b;
    }

    public boolean isRSBlock(Location loc) {
        return rsBlocks.containsKey(loc.toBlockLocation());
    }

    public RSBlock get(Location loc) {
        return rsBlocks.get(loc.toBlockLocation());
    }

    /**
     * 送信機の信号状態を更新し、受信機を反映する
     */
    public void updateTransmitter(Location txLoc) {
        RSBlock tx = rsBlocks.get(txLoc.toBlockLocation());
        if (tx == null || tx.type != RSBlockType.TRANSMITTER) return;

        Block block = txLoc.getBlock();
        boolean powered = isPowered(block);
        boolean prev = channelState.getOrDefault(tx.channel, false);

        if (powered != prev) {
            channelState.put(tx.channel, powered);
            updateReceivers(tx.channel, powered);
        }
    }

    private boolean isPowered(Block block) {
        for (BlockFace face : FACES) {
            Block neighbor = block.getRelative(face);
            if (neighbor.getType() == Material.REDSTONE_BLOCK) return true;
            if (neighbor.getBlockPower() > 0) return true;
        }
        return block.getBlockPower() > 0;
    }

    private void updateReceivers(String channel, boolean powered) {
        for (Map.Entry<Location, RSBlock> entry : rsBlocks.entrySet()) {
            RSBlock b = entry.getValue();
            if (b.type == RSBlockType.RECEIVER && channel.equals(b.channel)) {
                applyReceiverOutput(entry.getKey(), b, powered);
            }
        }
    }

    private void applyReceiverOutput(Location rxLoc, RSBlock rx, boolean powered) {
        Block outputBlock = rxLoc.getBlock().getRelative(rx.outputFace);
        if (powered) {
            if (outputBlock.getType() == Material.AIR) {
                outputBlock.setType(Material.REDSTONE_BLOCK);
            }
        } else {
            if (outputBlock.getType() == Material.REDSTONE_BLOCK) {
                outputBlock.setType(Material.AIR);
            }
        }
    }

    public void startUpdateTask(int intervalTicks) {
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            for (Map.Entry<Location, RSBlock> entry : new HashMap<>(rsBlocks).entrySet()) {
                if (entry.getValue().type == RSBlockType.TRANSMITTER) {
                    updateTransmitter(entry.getKey());
                }
            }
        }, intervalTicks, intervalTicks);
    }

    private void saveData() {
        YamlConfiguration cfg = new YamlConfiguration();
        int i = 0;
        for (Map.Entry<Location, RSBlock> e : rsBlocks.entrySet()) {
            Location loc = e.getKey();
            RSBlock b = e.getValue();
            String base = "blocks." + i;
            cfg.set(base + ".world", loc.getWorld().getName());
            cfg.set(base + ".x", loc.getBlockX());
            cfg.set(base + ".y", loc.getBlockY());
            cfg.set(base + ".z", loc.getBlockZ());
            cfg.set(base + ".type", b.type.name());
            cfg.set(base + ".channel", b.channel);
            cfg.set(base + ".face", b.outputFace.name());
            i++;
        }
        try { cfg.save(dataFile); } catch (IOException ex) { ex.printStackTrace(); }
    }

    private void loadData() {
        if (!dataFile.exists()) return;
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(dataFile);
        if (!cfg.contains("blocks")) return;
        for (String key : cfg.getConfigurationSection("blocks").getKeys(false)) {
            String base = "blocks." + key;
            org.bukkit.World world = plugin.getServer().getWorld(cfg.getString(base + ".world"));
            if (world == null) continue;
            int x = cfg.getInt(base + ".x"), y = cfg.getInt(base + ".y"), z = cfg.getInt(base + ".z");
            RSBlockType type = RSBlockType.valueOf(cfg.getString(base + ".type"));
            String channel = cfg.getString(base + ".channel");
            BlockFace face = BlockFace.valueOf(cfg.getString(base + ".face", "NORTH"));
            rsBlocks.put(new Location(world, x, y, z), new RSBlock(type, channel, face));
        }
    }
}
