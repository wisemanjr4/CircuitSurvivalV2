package net.circuitsurvival.listeners;

import net.circuitsurvival.managers.SelectionManager;
import net.circuitsurvival.worldedit.Selection;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.Map;

/**
 * 木の斧を使ったPos1/Pos2選択
 * 左クリック(ブロック) → Pos1
 * 右クリック(ブロック) → Pos2
 *
 * また //コマンド を /swe_ コマンドへ変換する。
 * 例: //set stone → /swe_set stone
 */
public class WandListener implements Listener {

    private final SelectionManager selMgr;

    // // コマンド → /swe_ コマンドへのマッピング
    private static final Map<String, String> SWE_ALIASES = Map.ofEntries(
            Map.entry("pos1",    "swe_pos1"),
            Map.entry("pos2",    "swe_pos2"),
            Map.entry("set",     "swe_set"),
            Map.entry("replace", "swe_replace"),
            Map.entry("fill",    "swe_fill"),
            Map.entry("walls",   "swe_walls"),
            Map.entry("outline", "swe_outline"),
            Map.entry("copy",    "swe_copy"),
            Map.entry("paste",   "swe_paste"),
            Map.entry("undo",    "swe_undo"),
            Map.entry("rotate",  "swe_rotate"),
            Map.entry("sel",     "swe_sel"),
            Map.entry("desel",   "swe_sel"),
            Map.entry("help",    "swe_sel")
    );

    public WandListener(SelectionManager selMgr) {
        this.selMgr = selMgr;
    }

    /**
     * //set stone → /swe_set stone のように変換する
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        String msg = event.getMessage();
        if (!msg.startsWith("//")) return;

        // "//set stone" → ["set", "stone"]
        String withoutSlashes = msg.substring(2).trim();
        String[] parts = withoutSlashes.split("\\s+", 2);
        String sub = parts[0].toLowerCase();

        String mapped = SWE_ALIASES.get(sub);
        if (mapped == null) return;

        event.setCancelled(true);
        String newCmd = "/" + mapped + (parts.length > 1 ? " " + parts[1] : "");
        event.getPlayer().performCommand(newCmd.substring(1)); // performCommand は / なし
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        // オフハンドの重複処理を防ぐ
        if (event.getHand() != EquipmentSlot.HAND) return;

        Player player = event.getPlayer();
        if (!player.hasPermission("circuitsurvival.swe")) return;

        Material held = player.getInventory().getItemInMainHand().getType();
        if (held != Material.WOODEN_AXE) return;

        Action action = event.getAction();
        if (action != Action.LEFT_CLICK_BLOCK && action != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getClickedBlock() == null) return;

        event.setCancelled(true);

        Selection sel = selMgr.getOrCreate(player.getUniqueId());
        Location loc = event.getClickedBlock().getLocation();

        if (action == Action.LEFT_CLICK_BLOCK) {
            sel.setPos1(loc);
            player.sendMessage(Component.text("[SWE] Pos1: " + fmt(loc), NamedTextColor.AQUA));
        } else {
            sel.setPos2(loc);
            player.sendMessage(Component.text("[SWE] Pos2: " + fmt(loc), NamedTextColor.AQUA));
        }

        if (sel.isComplete()) {
            player.sendMessage(Component.text("[SWE] 選択: " + sel.volume() + " ブロック ("
                    + sel.getSizeX() + "x" + sel.getSizeY() + "x" + sel.getSizeZ() + ")", NamedTextColor.YELLOW));
        }
    }

    private String fmt(Location loc) {
        return loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ();
    }
}
