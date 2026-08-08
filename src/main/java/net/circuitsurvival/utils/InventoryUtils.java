package net.circuitsurvival.utils;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Map;

public class InventoryUtils {

    /**
     * プレイヤーが指定の素材を必要数持っているか確認
     */
    public static boolean hasItems(Player player, Map<Material, Integer> required) {
        for (Map.Entry<Material, Integer> entry : required.entrySet()) {
            if (countItems(player, entry.getKey()) < entry.getValue()) return false;
        }
        return true;
    }

    /**
     * プレイヤーのインベントリから素材を消費
     */
    public static void consumeItems(Player player, Map<Material, Integer> required) {
        for (Map.Entry<Material, Integer> entry : required.entrySet()) {
            removeItems(player, entry.getKey(), entry.getValue());
        }
    }

    /**
     * 指定素材の所持数を返す
     */
    public static int countItems(Player player, Material material) {
        int count = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() == material) {
                count += item.getAmount();
            }
        }
        return count;
    }

    /**
     * インベントリから指定素材をamount個取り除く
     */
    public static void removeItems(Player player, Material material, int amount) {
        int remaining = amount;
        org.bukkit.inventory.Inventory inv = player.getInventory();
        for (int i = 0; i < inv.getSize(); i++) {
            if (remaining <= 0) break;
            ItemStack item = inv.getItem(i);
            if (item != null && item.getType() == material) {
                if (item.getAmount() <= remaining) {
                    remaining -= item.getAmount();
                    inv.setItem(i, null);
                } else {
                    item.setAmount(item.getAmount() - remaining);
                    remaining = 0;
                }
            }
        }
    }

    /**
     * プレイヤーにアイテムを渡す。溢れた分はドロップ
     */
    public static void giveItem(Player player, ItemStack item) {
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(item.clone());
        for (ItemStack drop : leftover.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), drop);
        }
    }
}
