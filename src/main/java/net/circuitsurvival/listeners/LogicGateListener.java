package net.circuitsurvival.listeners;

import net.circuitsurvival.CircuitSurvivalPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockRedstoneEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashMap;
import java.util.Map;

/**
 * 論理ゲートブロック
 *
 * 種別: AND, OR, NOT, XOR
 * 入力: 北・東・西の3面のレッドストーン信号
 * 出力: 南面にレッドストーンブロックを設置/除去
 *
 * ゲートはSEA_LANTERNブロックで表現。
 * 設置時にPDCに種別を記録。
 */
public class LogicGateListener implements Listener {

    public enum GateType { AND, OR, NOT, XOR }

    private final CircuitSurvivalPlugin plugin;
    private final NamespacedKey gateTypeKey;
    // ゲート設置位置 → 種別
    private final Map<Location, GateType> gates = new HashMap<>();

    private static final BlockFace[] INPUT_FACES = { BlockFace.NORTH, BlockFace.EAST, BlockFace.WEST };
    private static final BlockFace OUTPUT_FACE = BlockFace.SOUTH;

    public LogicGateListener(CircuitSurvivalPlugin plugin, NamespacedKey gateTypeKey) {
        this.plugin = plugin;
        this.gateTypeKey = gateTypeKey;
    }

    @EventHandler
    public void onPlace(BlockPlaceEvent event) {
        ItemStack item = event.getItemInHand();
        if (!item.hasItemMeta()) return;
        ItemMeta meta = item.getItemMeta();
        var pdc = meta.getPersistentDataContainer();
        if (!pdc.has(gateTypeKey, PersistentDataType.STRING)) return;

        String typeStr = pdc.get(gateTypeKey, PersistentDataType.STRING);
        GateType type;
        try { type = GateType.valueOf(typeStr); }
        catch (IllegalArgumentException e) { return; }

        gates.put(event.getBlock().getLocation().toBlockLocation(), type);
        event.getPlayer().sendMessage(Component.text("[Gate] " + type.name() + " ゲートを設置。", NamedTextColor.GREEN));
    }

    @EventHandler
    public void onBreak(BlockBreakEvent event) {
        Location loc = event.getBlock().getLocation().toBlockLocation();
        GateType type = gates.remove(loc);
        if (type == null) return;

        // 出力ブロックを除去
        Block output = event.getBlock().getRelative(OUTPUT_FACE);
        if (output.getType() == Material.REDSTONE_BLOCK) output.setType(Material.AIR);

        event.setDropItems(false);
        event.getBlock().getWorld().dropItemNaturally(event.getBlock().getLocation(), buildGateItem(type));
        event.getPlayer().sendMessage(Component.text("[Gate] ゲートを回収しました。", NamedTextColor.YELLOW));
    }

    @EventHandler
    public void onRedstone(BlockRedstoneEvent event) {
        // ゲート自体、またはゲートの隣が変化したとき評価
        Block changed = event.getBlock();
        evaluateNearby(changed);
    }

    private void evaluateNearby(Block changed) {
        // 変化したブロックの隣にゲートがあるか確認
        for (BlockFace face : BlockFace.values()) {
            Block neighbor = changed.getRelative(face);
            Location loc = neighbor.getLocation().toBlockLocation();
            if (gates.containsKey(loc)) {
                evaluateGate(neighbor, gates.get(loc));
            }
        }
        // 変化したブロック自体がゲートなら評価
        Location loc = changed.getLocation().toBlockLocation();
        if (gates.containsKey(loc)) {
            evaluateGate(changed, gates.get(loc));
        }
    }

    private void evaluateGate(Block gateBlock, GateType type) {
        boolean[] inputs = new boolean[INPUT_FACES.length];
        for (int i = 0; i < INPUT_FACES.length; i++) {
            Block neighbor = gateBlock.getRelative(INPUT_FACES[i]);
            inputs[i] = isPowered(neighbor);
        }

        boolean output = switch (type) {
            case AND -> inputs[0] && inputs[1] && inputs[2];
            case OR  -> inputs[0] || inputs[1] || inputs[2];
            case NOT -> !(inputs[0] || inputs[1] || inputs[2]);
            case XOR -> {
                int count = 0;
                for (boolean b : inputs) if (b) count++;
                yield count % 2 == 1;
            }
        };

        Block outputBlock = gateBlock.getRelative(OUTPUT_FACE);
        // 出力を設定 (空気またはレッドストーンブロックのみ変更)
        if (output && outputBlock.getType() == Material.AIR) {
            outputBlock.setType(Material.REDSTONE_BLOCK);
        } else if (!output && outputBlock.getType() == Material.REDSTONE_BLOCK) {
            outputBlock.setType(Material.AIR);
        }
    }

    private boolean isPowered(Block block) {
        if (block.getType() == Material.REDSTONE_BLOCK) return true;
        return block.getBlockPower() > 0 || block.isBlockPowered() || block.isBlockIndirectlyPowered();
    }

    private ItemStack buildGateItem(GateType type) {
        ItemStack item = new ItemStack(Material.SEA_LANTERN);
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(gateTypeKey, PersistentDataType.STRING, type.name());
        item.setItemMeta(meta);
        return item;
    }

    public Map<Location, GateType> getGates() { return gates; }
}
