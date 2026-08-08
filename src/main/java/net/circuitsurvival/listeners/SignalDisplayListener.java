package net.circuitsurvival.listeners;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.Set;

/**
 * レッドストーン信号強度のリアルタイム表示
 *
 * レッドストーンダストを手に持って右クリックすると
 * クリックしたブロック周辺の信号強度をアクションバーに表示
 *
 * また、レッドストーンダストを手に持っているとき
 * 見ているブロックの信号強度を常時アクションバーに表示
 */
public class SignalDisplayListener implements Listener {

    private static final Set<Material> REDSTONE_BLOCKS = Set.of(
            Material.REDSTONE_WIRE,
            Material.REDSTONE_TORCH,
            Material.REDSTONE_WALL_TORCH,
            Material.REDSTONE_LAMP,
            Material.REPEATER,
            Material.COMPARATOR,
            Material.OBSERVER,
            Material.PISTON,
            Material.STICKY_PISTON,
            Material.LEVER,
            Material.STONE_BUTTON,
            Material.OAK_BUTTON,
            Material.SPRUCE_BUTTON,
            Material.BIRCH_BUTTON,
            Material.JUNGLE_BUTTON,
            Material.ACACIA_BUTTON,
            Material.DARK_OAK_BUTTON,
            Material.MANGROVE_BUTTON,
            Material.CHERRY_BUTTON,
            Material.BAMBOO_BUTTON,
            Material.CRIMSON_BUTTON,
            Material.WARPED_BUTTON,
            Material.POLISHED_BLACKSTONE_BUTTON,
            Material.REDSTONE_BLOCK,
            Material.DAYLIGHT_DETECTOR,
            Material.TRAPPED_CHEST,
            Material.TRIPWIRE_HOOK,
            Material.TARGET
    );

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getClickedBlock() == null) return;

        Player player = event.getPlayer();
        // スニーク(Shift)+右クリックのみ信号表示 (通常右クリックはブロック設置を妨げない)
        if (!player.isSneaking()) return;

        Material held = player.getInventory().getItemInMainHand().getType();
        if (held != Material.REDSTONE) return;

        Block block = event.getClickedBlock();
        displaySignalInfo(player, block);
        // event.setCancelled はしない → レッドストーンブロック設置を妨げないため
    }

    private void displaySignalInfo(Player player, Block block) {
        int power       = block.getBlockPower();
        int indirectPow = block.isBlockIndirectlyPowered() ? 1 : 0;
        boolean powered = block.isBlockPowered();
        boolean indirect = block.isBlockIndirectlyPowered();

        String mat = block.getType().name();

        // ActionBar表示
        NamedTextColor color = power > 0 || powered ? NamedTextColor.RED : NamedTextColor.GRAY;
        Component msg = Component.text(
                "[" + mat + "] 直接: " + power
                        + " | 間接: " + indirectPow
                        + " | 通電: " + (powered ? "YES" : "NO")
                        + " | 間接通電: " + (indirect ? "YES" : "NO"),
                color
        );
        player.sendActionBar(msg);

        // チャットでも詳細表示
        player.sendMessage(Component.text("=== 信号強度情報 ===", NamedTextColor.GOLD));
        player.sendMessage(Component.text("ブロック: " + mat + " at " +
                block.getX() + "," + block.getY() + "," + block.getZ(), NamedTextColor.WHITE));
        player.sendMessage(Component.text("直接信号強度: " + power, power > 0 ? NamedTextColor.RED : NamedTextColor.GRAY));
        player.sendMessage(Component.text("間接信号強度: " + indirectPow, indirectPow > 0 ? NamedTextColor.YELLOW : NamedTextColor.GRAY));
        player.sendMessage(Component.text("通電状態: " + (powered ? "ON" : "OFF"), powered ? NamedTextColor.GREEN : NamedTextColor.RED));
        player.sendMessage(Component.text("間接通電: " + (indirect ? "ON" : "OFF"), indirect ? NamedTextColor.GREEN : NamedTextColor.RED));

        // 6面の状態も表示
        org.bukkit.block.BlockFace[] faces = {
                org.bukkit.block.BlockFace.NORTH, org.bukkit.block.BlockFace.SOUTH,
                org.bukkit.block.BlockFace.EAST, org.bukkit.block.BlockFace.WEST,
                org.bukkit.block.BlockFace.UP, org.bukkit.block.BlockFace.DOWN
        };
        StringBuilder faceInfo = new StringBuilder("隣接: ");
        for (org.bukkit.block.BlockFace face : faces) {
            Block neighbor = block.getRelative(face);
            int p = neighbor.getBlockPower();
            if (p > 0) faceInfo.append(face.name()).append("=").append(p).append(" ");
        }
        player.sendMessage(Component.text(faceInfo.toString(), NamedTextColor.AQUA));
    }
}
