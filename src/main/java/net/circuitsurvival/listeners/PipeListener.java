package net.circuitsurvival.listeners;

import net.circuitsurvival.managers.PipeManager;
import net.circuitsurvival.managers.PipeManager.PipeData;
import net.circuitsurvival.managers.PipeManager.PipeType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

/**
 * パイプブロックの設置・破壊・右クリック処理
 *
 * パイプ種別まとめ:
 *   アイテムパイプ  (PIPE)       : 鉄格子 – 通常双方向パイプ
 *   入力パイプ      (INPUT_PIPE)  : 金の格子 – 隣接コンテナから能動的に引き出す
 *   出力パイプ      (OUTPUT_PIPE) : レンガ – 隣接コンテナへ受動的に押し込む先
 *   フィルターパイプ (FILTER)     : 鎖 – 特定アイテムのみ通過
 *   ワイヤレス送信機 (WIRELESS_TX): エンダーチェスト
 *   ワイヤレス受信機 (WIRELESS_RX): エンダーチェスト
 */
public class PipeListener implements Listener {

    private final PipeManager pipeManager;
    private final NamespacedKey pipeTypeKey;
    private final NamespacedKey pipeFilterKey;
    private final NamespacedKey pipeChannelKey;

    public PipeListener(PipeManager pipeManager, NamespacedKey pipeTypeKey,
                        NamespacedKey pipeFilterKey, NamespacedKey pipeChannelKey) {
        this.pipeManager   = pipeManager;
        this.pipeTypeKey   = pipeTypeKey;
        this.pipeFilterKey = pipeFilterKey;
        this.pipeChannelKey = pipeChannelKey;
    }

    @EventHandler
    public void onPlace(BlockPlaceEvent event) {
        ItemStack item = event.getItemInHand();
        ItemMeta meta = item.hasItemMeta() ? item.getItemMeta() : null;
        if (meta == null) return;

        var pdc = meta.getPersistentDataContainer();
        if (!pdc.has(pipeTypeKey, PersistentDataType.STRING)) return;

        String typeStr = pdc.get(pipeTypeKey, PersistentDataType.STRING);
        PipeType type;
        try { type = PipeType.valueOf(typeStr); }
        catch (IllegalArgumentException e) { return; }

        PipeData data = new PipeData(type);
        if (pdc.has(pipeFilterKey, PersistentDataType.STRING)) {
            try { data.filterMaterial = Material.valueOf(pdc.get(pipeFilterKey, PersistentDataType.STRING)); }
            catch (IllegalArgumentException ignored) {}
        }
        if (pdc.has(pipeChannelKey, PersistentDataType.STRING)) {
            data.channel = pdc.get(pipeChannelKey, PersistentDataType.STRING);
        }

        pipeManager.registerPipe(event.getBlock().getLocation(), data);

        String typeName = switch (type) {
            case PIPE        -> "アイテムパイプ";
            case INPUT_PIPE  -> "入力パイプ";
            case OUTPUT_PIPE -> "出力パイプ";
            case FAST_PIPE   -> "高速パイプ";
            case FAST_INPUT_PIPE -> "高速入力パイプ";
            case FAST_OUTPUT_PIPE -> "高速出力パイプ";
            case FILTER      -> "フィルターパイプ";
            case WIRELESS_TX -> "ワイヤレス送信機";
            case WIRELESS_RX -> "ワイヤレス受信機";
        };
        event.getPlayer().sendMessage(Component.text(
                "[Pipe] " + typeName + " を設置しました。", NamedTextColor.GREEN));
    }

    @EventHandler
    public void onBreak(BlockBreakEvent event) {
        if (!pipeManager.isPipe(event.getBlock().getLocation())) return;

        PipeData data = pipeManager.removePipe(event.getBlock().getLocation());
        if (data == null) return;

        event.setDropItems(false);
        ItemStack drop = buildPipeItem(data);
        event.getBlock().getWorld().dropItemNaturally(event.getBlock().getLocation(), drop);
        event.getPlayer().sendMessage(Component.text("[Pipe] パイプを回収しました。", NamedTextColor.YELLOW));
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getClickedBlock() == null) return;
        if (!pipeManager.isPipe(event.getClickedBlock().getLocation())) return;

        Player player = event.getPlayer();
        if (player.isSneaking()) return; // スニーク中はブロック設置を優先

        PipeData data = pipeManager.getPipe(event.getClickedBlock().getLocation());
        if (data == null) return;

        switch (data.type) {
            case FILTER -> {
                event.setCancelled(true);
                ItemStack held = player.getInventory().getItemInMainHand();
                if (held.getType() == Material.AIR) {
                    data.filterMaterial = null;
                    player.sendMessage(Component.text("[Pipe] フィルターをリセットしました。", NamedTextColor.YELLOW));
                } else {
                    data.filterMaterial = held.getType();
                    player.sendMessage(Component.text("[Pipe] フィルター設定: " + held.getType().name(), NamedTextColor.GREEN));
                }
                pipeManager.registerPipe(event.getClickedBlock().getLocation(), data);
            }
            case WIRELESS_TX, WIRELESS_RX -> {
                event.setCancelled(true);
                ItemStack held = player.getInventory().getItemInMainHand();
                if (held.hasItemMeta() && held.getItemMeta().hasDisplayName()) {
                    // 名前付きアイテムを持っている → その名前をチャンネルに設定
                    net.kyori.adventure.text.Component displayName = held.getItemMeta().displayName();
                    String plain = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(displayName);
                    if (!plain.isEmpty()) {
                        data.channel = plain;
                        pipeManager.registerPipe(event.getClickedBlock().getLocation(), data);
                        player.sendMessage(Component.text("[Pipe] チャンネルを " + plain + " に設定しました。", NamedTextColor.GREEN));
                        break;
                    }
                }
                // 現在のチャンネルを表示
                String ch = data.channel != null ? data.channel : "未設定";
                player.sendMessage(Component.text("[Pipe] チャンネル: " + ch, NamedTextColor.AQUA));
                player.sendMessage(Component.text("名前付きアイテムを持って右クリックで変更 /cs pipe channel <名前>", NamedTextColor.GRAY));
            }
            case INPUT_PIPE -> {
                event.setCancelled(true);
                player.sendMessage(Component.text(
                        "[入力パイプ] 隣接コンテナからアイテムを能動的に引き出します。",
                        NamedTextColor.GOLD));
            }
            case OUTPUT_PIPE -> {
                event.setCancelled(true);
                player.sendMessage(Component.text(
                        "[出力パイプ] パイプチェーンの終端として隣接コンテナへアイテムを押し込みます。",
                        NamedTextColor.GOLD));
            }
            case PIPE -> {
                event.setCancelled(true);
                player.sendMessage(Component.text(
                        "[アイテムパイプ] 隣接コンテナ間をアイテムで繋ぎます。",
                        NamedTextColor.AQUA));
            }
        }
    }

    // ---- パイプアイテム再生成 (破壊時ドロップ) ---------------------------------

    private ItemStack buildPipeItem(PipeData data) {
        Material mat;
        String displayName;
        String lore1;
        NamedTextColor color;

        switch (data.type) {
            case INPUT_PIPE -> {
                mat = Material.IRON_BARS;
                displayName = "入力パイプ";
                lore1 = "隣接コンテナからアイテムを能動的に引き出す";
                color = NamedTextColor.GOLD;
            }
            case OUTPUT_PIPE -> {
                mat = Material.IRON_BARS;
                displayName = "出力パイプ";
                lore1 = "パイプチェーン終端 → 隣接コンテナへ押し込む";
                color = NamedTextColor.RED;
            }
            case FILTER -> {
                mat = Material.CHAIN;
                displayName = "フィルターパイプ";
                lore1 = "右クリックで通過させるアイテムを設定";
                color = NamedTextColor.YELLOW;
            }
            case WIRELESS_TX -> {
                mat = Material.ENDER_CHEST;
                displayName = "ワイヤレス送信機";
                lore1 = "チャンネルにアイテムを送信";
                color = NamedTextColor.LIGHT_PURPLE;
            }
            case WIRELESS_RX -> {
                mat = Material.ENDER_CHEST;
                displayName = "ワイヤレス受信機";
                lore1 = "チャンネルからアイテムを受信";
                color = NamedTextColor.LIGHT_PURPLE;
            }
            default -> { // PIPE
                mat = Material.IRON_BARS;
                displayName = "アイテムパイプ";
                lore1 = "チェスト間をアイテムで繋ぐ";
                color = NamedTextColor.AQUA;
            }
        }

        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(displayName, color));
        meta.lore(List.of(Component.text(lore1, NamedTextColor.GRAY)));
        meta.getPersistentDataContainer().set(pipeTypeKey, PersistentDataType.STRING, data.type.name());
        if (data.filterMaterial != null)
            meta.getPersistentDataContainer().set(pipeFilterKey, PersistentDataType.STRING, data.filterMaterial.name());
        if (data.channel != null)
            meta.getPersistentDataContainer().set(pipeChannelKey, PersistentDataType.STRING, data.channel);
        item.setItemMeta(meta);
        return item;
    }
}
