package net.circuitsurvival.commands;

import net.circuitsurvival.managers.SelectionManager;
import net.circuitsurvival.managers.SelectionManager.UndoRecord;
import net.circuitsurvival.utils.InventoryUtils;
import net.circuitsurvival.worldedit.BlockEntry;
import net.circuitsurvival.worldedit.Clipboard;
import net.circuitsurvival.worldedit.Selection;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * サバイバルWorldEditコマンド群
 * //set, //replace, //fill, //walls, //outline, //copy, //paste, //undo, //rotate
 */
public class SWECommand implements CommandExecutor {

    private final SelectionManager selMgr;

    /** 大量操作の確認待ち: UUID → 実行ペンディング情報 */
    private final Map<UUID, PendingOp> pendingOps = new ConcurrentHashMap<>();

    private static final long CONFIRM_TIMEOUT_MS = 20_000L; // 20秒
    /** 確認なしで実行できる最大ブロック数 */
    private static final long SAFE_VOLUME = 50_000L;

    private record PendingOp(Runnable action, long expireAt, String description) {}

    public SWECommand(SelectionManager selMgr) {
        this.selMgr = selMgr;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("プレイヤーのみ使用可能です。");
            return true;
        }

        if (!player.hasPermission("circuitsurvival.swe")) {
            msg(player, "権限がありません。", NamedTextColor.RED);
            return true;
        }

        String sub = label.replace("/", "").toLowerCase();
        switch (sub) {
            case "swe_set"     -> cmdSet(player, args);
            case "swe_replace" -> cmdReplace(player, args);
            case "swe_fill"    -> cmdFill(player, args);
            case "swe_walls"   -> cmdWalls(player, args);
            case "swe_outline" -> cmdOutline(player, args);
            case "swe_copy"    -> cmdCopy(player);
            case "swe_paste"   -> cmdPaste(player);
            case "swe_undo"    -> cmdUndo(player);
            case "swe_rotate"  -> cmdRotate(player, args);
            case "swe_pos1"    -> cmdPos(player, 1);
            case "swe_pos2"    -> cmdPos(player, 2);
            case "swe_sel"     -> cmdInfo(player);
            case "swe_confirm" -> cmdConfirm(player);
            default -> showHelp(player);
        }
        return true;
    }

    // ---- //confirm -----------------------------------------------------------

    private void cmdConfirm(Player player) {
        PendingOp op = pendingOps.remove(player.getUniqueId());
        if (op == null) {
            msg(player, "確認待ちの操作がありません。", NamedTextColor.YELLOW);
            return;
        }
        if (System.currentTimeMillis() > op.expireAt()) {
            msg(player, "操作がタイムアウトしました。もう一度実行してください。", NamedTextColor.RED);
            return;
        }
        msg(player, "実行します: " + op.description(), NamedTextColor.YELLOW);
        op.action().run();
    }

    /**
     * 大量操作または危険な操作 (AIRを含む) は確認を要求する。
     * @return 即時実行 → true、確認待ち → false
     */
    private boolean requireConfirm(Player player, long volume, boolean isDestructive, Runnable action, String desc) {
        if (!isDestructive && volume <= SAFE_VOLUME) {
            action.run();
            return true;
        }
        pendingOps.put(player.getUniqueId(),
                new PendingOp(action, System.currentTimeMillis() + CONFIRM_TIMEOUT_MS, desc));
        if (isDestructive) {
            msg(player, "⚠ AIR (消去操作) を検出しました。", NamedTextColor.RED);
        }
        msg(player, "対象: " + volume + " ブロック。20秒以内に //confirm で実行してください。", NamedTextColor.YELLOW);
        return false;
    }

    // ---- //set <block> -------------------------------------------------------

    private void cmdSet(Player player, String[] args) {
        if (args.length < 1) { msg(player, "使い方: //set <ブロック名>", NamedTextColor.YELLOW); return; }
        Material mat = parseMaterial(args[0]);
        if (mat == null) { msg(player, "不明なブロック: " + args[0], NamedTextColor.RED); return; }

        // ブロックとして設置できないアイテムを弾く (アイテムはブロックではない)
        if (!mat.isBlock()) {
            msg(player, mat.name() + " はブロックではありません。", NamedTextColor.RED);
            return;
        }

        Selection sel = selMgr.get(player.getUniqueId());
        if (!isSelectionReady(player, sel)) return;

        long volume = sel.volume();
        boolean isDestructive = isAir(mat);

        // AIR以外: 素材チェック
        Map<Material, Integer> cost = new HashMap<>();
        if (!isDestructive) {
            if (volume > Integer.MAX_VALUE) {
                msg(player, "選択範囲が大きすぎます。", NamedTextColor.RED); return;
            }
            cost.put(mat, (int) volume);
        }

        if (!cost.isEmpty() && !InventoryUtils.hasItems(player, cost)) {
            msg(player, mat.name() + " が足りません。必要: " + volume, NamedTextColor.RED);
            return;
        }

        Runnable action = () -> {
            UndoRecord undo = captureUndo(sel.getWorld(), sel);
            if (!cost.isEmpty()) InventoryUtils.consumeItems(player, cost);
            World world = sel.getWorld();
            for (int x = sel.getMinX(); x <= sel.getMaxX(); x++)
                for (int y = sel.getMinY(); y <= sel.getMaxY(); y++)
                    for (int z = sel.getMinZ(); z <= sel.getMaxZ(); z++)
                        world.getBlockAt(x, y, z).setType(mat, false);
            selMgr.pushUndo(player.getUniqueId(), undo);
            msg(player, volume + " ブロックを " + mat.name() + " に設定しました。", NamedTextColor.GREEN);
        };

        requireConfirm(player, volume, isDestructive, action,
                "//set " + mat.name() + " (" + volume + " ブロック)");
    }

    // ---- //replace <from> <to> -----------------------------------------------

    private void cmdReplace(Player player, String[] args) {
        if (args.length < 2) { msg(player, "使い方: //replace <置換元> <置換先>", NamedTextColor.YELLOW); return; }
        Material from = parseMaterial(args[0]);
        Material to   = parseMaterial(args[1]);
        if (from == null) { msg(player, "不明なブロック (置換元): " + args[0], NamedTextColor.RED); return; }
        if (to == null)   { msg(player, "不明なブロック (置換先): " + args[1], NamedTextColor.RED); return; }
        if (!to.isBlock() && !isAir(to)) { msg(player, to.name() + " はブロックではありません。", NamedTextColor.RED); return; }

        Selection sel = selMgr.get(player.getUniqueId());
        if (!isSelectionReady(player, sel)) return;

        World world = sel.getWorld();
        int count = 0;
        for (int x = sel.getMinX(); x <= sel.getMaxX(); x++)
            for (int y = sel.getMinY(); y <= sel.getMaxY(); y++)
                for (int z = sel.getMinZ(); z <= sel.getMaxZ(); z++)
                    if (world.getBlockAt(x, y, z).getType() == from) count++;

        if (count == 0) { msg(player, from.name() + " が選択範囲内にありません。", NamedTextColor.YELLOW); return; }

        Map<Material, Integer> cost = new HashMap<>();
        if (!isAir(to)) cost.put(to, count);
        if (!cost.isEmpty() && !InventoryUtils.hasItems(player, cost)) {
            msg(player, to.name() + " が足りません。必要: " + count, NamedTextColor.RED); return;
        }

        boolean isDestructive = isAir(to);
        final int finalCount = count;
        Runnable action = () -> {
            UndoRecord undo = captureUndo(world, sel);
            if (!cost.isEmpty()) InventoryUtils.consumeItems(player, cost);
            int replaced = 0;
            for (int x = sel.getMinX(); x <= sel.getMaxX(); x++)
                for (int y = sel.getMinY(); y <= sel.getMaxY(); y++)
                    for (int z = sel.getMinZ(); z <= sel.getMaxZ(); z++) {
                        Block b = world.getBlockAt(x, y, z);
                        if (b.getType() == from) { b.setType(to, false); replaced++; }
                    }
            selMgr.pushUndo(player.getUniqueId(), undo);
            msg(player, replaced + " ブロックを " + from.name() + " → " + to.name() + " に置換しました。",
                    NamedTextColor.GREEN);
        };

        requireConfirm(player, finalCount, isDestructive, action,
                "//replace " + from.name() + " " + to.name() + " (" + finalCount + " ブロック)");
    }

    // ---- //fill (AIRを埋める) -------------------------------------------------

    private void cmdFill(Player player, String[] args) {
        if (args.length < 1) { msg(player, "使い方: //fill <ブロック名>", NamedTextColor.YELLOW); return; }
        Material mat = parseMaterial(args[0]);
        if (mat == null) { msg(player, "不明なブロック: " + args[0], NamedTextColor.RED); return; }
        if (isAir(mat))  { msg(player, "AIRで埋めることはできません。//replace を使ってください。", NamedTextColor.RED); return; }
        if (!mat.isBlock()) { msg(player, mat.name() + " はブロックではありません。", NamedTextColor.RED); return; }

        Selection sel = selMgr.get(player.getUniqueId());
        if (!isSelectionReady(player, sel)) return;

        World world = sel.getWorld();
        int count = 0;
        for (int x = sel.getMinX(); x <= sel.getMaxX(); x++)
            for (int y = sel.getMinY(); y <= sel.getMaxY(); y++)
                for (int z = sel.getMinZ(); z <= sel.getMaxZ(); z++)
                    if (isAir(world.getBlockAt(x, y, z).getType())) count++;

        if (count == 0) { msg(player, "選択範囲内に空気がありません。", NamedTextColor.YELLOW); return; }

        Map<Material, Integer> cost = new HashMap<>();
        cost.put(mat, count);
        if (!InventoryUtils.hasItems(player, cost)) {
            msg(player, mat.name() + " が足りません。必要: " + count, NamedTextColor.RED); return;
        }

        Runnable action = () -> {
            UndoRecord undo = captureUndo(world, sel);
            InventoryUtils.consumeItems(player, cost);
            int filled = 0;
            for (int x = sel.getMinX(); x <= sel.getMaxX(); x++)
                for (int y = sel.getMinY(); y <= sel.getMaxY(); y++)
                    for (int z = sel.getMinZ(); z <= sel.getMaxZ(); z++) {
                        Block b = world.getBlockAt(x, y, z);
                        if (isAir(b.getType())) { b.setType(mat, false); filled++; }
                    }
            selMgr.pushUndo(player.getUniqueId(), undo);
            msg(player, filled + " ブロックを埋めました。", NamedTextColor.GREEN);
        };

        requireConfirm(player, count, false, action, "//fill " + mat.name() + " (" + count + " ブロック)");
    }

    // ---- //walls -------------------------------------------------------------

    private void cmdWalls(Player player, String[] args) {
        if (args.length < 1) { msg(player, "使い方: //walls <ブロック名>", NamedTextColor.YELLOW); return; }
        Material mat = parseMaterial(args[0]);
        if (mat == null)    { msg(player, "不明なブロック: " + args[0], NamedTextColor.RED); return; }
        if (!mat.isBlock() && !isAir(mat)) { msg(player, mat.name() + " はブロックではありません。", NamedTextColor.RED); return; }

        Selection sel = selMgr.get(player.getUniqueId());
        if (!isSelectionReady(player, sel)) return;

        World world = sel.getWorld();
        int count = 0;
        for (int x = sel.getMinX(); x <= sel.getMaxX(); x++)
            for (int y = sel.getMinY(); y <= sel.getMaxY(); y++)
                for (int z = sel.getMinZ(); z <= sel.getMaxZ(); z++)
                    if (isWall(sel, x, y, z)) count++;

        Map<Material, Integer> cost = new HashMap<>();
        if (!isAir(mat)) cost.put(mat, count);
        if (!cost.isEmpty() && !InventoryUtils.hasItems(player, cost)) {
            msg(player, mat.name() + " が足りません。必要: " + count, NamedTextColor.RED); return;
        }

        UndoRecord undo = captureUndo(world, sel);
        if (!cost.isEmpty()) InventoryUtils.consumeItems(player, cost);

        int placed = 0;
        for (int x = sel.getMinX(); x <= sel.getMaxX(); x++)
            for (int y = sel.getMinY(); y <= sel.getMaxY(); y++)
                for (int z = sel.getMinZ(); z <= sel.getMaxZ(); z++)
                    if (isWall(sel, x, y, z)) { world.getBlockAt(x, y, z).setType(mat, false); placed++; }

        selMgr.pushUndo(player.getUniqueId(), undo);
        msg(player, placed + " ブロックで壁を作成しました。", NamedTextColor.GREEN);
    }

    // ---- //outline -----------------------------------------------------------

    private void cmdOutline(Player player, String[] args) {
        if (args.length < 1) { msg(player, "使い方: //outline <ブロック名>", NamedTextColor.YELLOW); return; }
        Material mat = parseMaterial(args[0]);
        if (mat == null)    { msg(player, "不明なブロック: " + args[0], NamedTextColor.RED); return; }
        if (!mat.isBlock() && !isAir(mat)) { msg(player, mat.name() + " はブロックではありません。", NamedTextColor.RED); return; }

        Selection sel = selMgr.get(player.getUniqueId());
        if (!isSelectionReady(player, sel)) return;

        World world = sel.getWorld();
        int count = 0;
        for (int x = sel.getMinX(); x <= sel.getMaxX(); x++)
            for (int y = sel.getMinY(); y <= sel.getMaxY(); y++)
                for (int z = sel.getMinZ(); z <= sel.getMaxZ(); z++)
                    if (isOutline(sel, x, y, z)) count++;

        Map<Material, Integer> cost = new HashMap<>();
        if (!isAir(mat)) cost.put(mat, count);
        if (!cost.isEmpty() && !InventoryUtils.hasItems(player, cost)) {
            msg(player, mat.name() + " が足りません。必要: " + count, NamedTextColor.RED); return;
        }

        UndoRecord undo = captureUndo(world, sel);
        if (!cost.isEmpty()) InventoryUtils.consumeItems(player, cost);

        int placed = 0;
        for (int x = sel.getMinX(); x <= sel.getMaxX(); x++)
            for (int y = sel.getMinY(); y <= sel.getMaxY(); y++)
                for (int z = sel.getMinZ(); z <= sel.getMaxZ(); z++)
                    if (isOutline(sel, x, y, z)) { world.getBlockAt(x, y, z).setType(mat, false); placed++; }

        selMgr.pushUndo(player.getUniqueId(), undo);
        msg(player, placed + " ブロックで外枠を作成しました。", NamedTextColor.GREEN);
    }

    // ---- //copy --------------------------------------------------------------

    private void cmdCopy(Player player) {
        Selection sel = selMgr.get(player.getUniqueId());
        if (!isSelectionReady(player, sel)) return;

        Clipboard cb = selMgr.getClipboard(player.getUniqueId());
        cb.copy(sel, player.getLocation());
        msg(player, sel.volume() + " ブロックをコピーしました。(//paste でペースト、//rotate で回転)", NamedTextColor.GREEN);
    }

    // ---- //paste -------------------------------------------------------------

    private void cmdPaste(Player player) {
        Clipboard cb = selMgr.getClipboard(player.getUniqueId());
        if (cb.isEmpty()) {
            msg(player, "クリップボードが空です。先に //copy してください。", NamedTextColor.RED);
            return;
        }

        // AIRブロックを除いた実コスト
        Map<Material, Integer> cost = cb.countMaterials();

        if (!InventoryUtils.hasItems(player, cost)) {
            StringBuilder sb = new StringBuilder("素材が足りません: ");
            cost.forEach((m, n) -> {
                int have = InventoryUtils.countItems(player, m);
                if (have < n) sb.append(m.name()).append(" x").append(n - have).append("  ");
            });
            msg(player, sb.toString(), NamedTextColor.RED);
            return;
        }

        // ペースト先基点 = プレイヤー位置 + コピー時オフセット
        Location playerLoc = player.getLocation();
        Location base = new Location(
                player.getWorld(),
                playerLoc.getBlockX() + cb.getPlayerOffsetX(),
                playerLoc.getBlockY() + cb.getPlayerOffsetY(),
                playerLoc.getBlockZ() + cb.getPlayerOffsetZ());

        long pasteVol = (long) cb.getSizeX() * cb.getSizeY() * cb.getSizeZ();
        final Map<Material, Integer> finalCost = cost;

        Runnable action = () -> {
            World world = player.getWorld();

            // undo 記録
            Map<Vector, BlockEntry> undoBlocks = new HashMap<>();
            for (int x = 0; x < cb.getSizeX(); x++)
                for (int y = 0; y < cb.getSizeY(); y++)
                    for (int z = 0; z < cb.getSizeZ(); z++) {
                        Block b = world.getBlockAt(base.getBlockX() + x, base.getBlockY() + y, base.getBlockZ() + z);
                        undoBlocks.put(new Vector(b.getX(), b.getY(), b.getZ()),
                                new BlockEntry(b.getType(), b.getBlockData()));
                    }

            InventoryUtils.consumeItems(player, finalCost);

            for (int x = 0; x < cb.getSizeX(); x++)
                for (int y = 0; y < cb.getSizeY(); y++)
                    for (int z = 0; z < cb.getSizeZ(); z++) {
                        BlockEntry entry = cb.getBlock(x, y, z);
                        if (entry == null) continue;
                        Block b = world.getBlockAt(base.getBlockX() + x, base.getBlockY() + y, base.getBlockZ() + z);
                        b.setType(entry.getMaterial(), false);
                        // BlockData は素材セット後のみ適用 (素材変更に失敗した場合はスキップ)
                        if (b.getType() == entry.getMaterial() && entry.getBlockData() != null) {
                            try { b.setBlockData(entry.getBlockData(), false); }
                            catch (Exception ignored) {}
                        }
                    }

            selMgr.pushUndo(player.getUniqueId(), new UndoRecord(world, undoBlocks));
            msg(player, "ペーストしました。(" + cb.getSizeX() + "x" + cb.getSizeY() + "x" + cb.getSizeZ() + ")",
                    NamedTextColor.GREEN);
        };

        requireConfirm(player, pasteVol, false, action, "//paste (" + pasteVol + " ブロック)");
    }

    // ---- //undo --------------------------------------------------------------

    private void cmdUndo(Player player) {
        UndoRecord record = selMgr.popUndo(player.getUniqueId());
        if (record == null) {
            msg(player, "Undo履歴がありません。", NamedTextColor.RED);
            return;
        }

        Map<Material, Integer> refund = new HashMap<>();
        for (Map.Entry<Vector, BlockEntry> entry : record.blocks.entrySet()) {
            Vector v   = entry.getKey();
            BlockEntry be = entry.getValue();
            Block current = record.world.getBlockAt(v.getBlockX(), v.getBlockY(), v.getBlockZ());
            Material currentMat = current.getType();
            // 現在のブロックが非AIRなら返却
            if (!isAir(currentMat)) {
                refund.merge(currentMat, 1, Integer::sum);
            }
            current.setType(be.getMaterial(), false);
            if (current.getType() == be.getMaterial() && be.getBlockData() != null) {
                try { current.setBlockData(be.getBlockData(), false); }
                catch (Exception ignored) {}
            }
        }

        for (Map.Entry<Material, Integer> e : refund.entrySet()) {
            InventoryUtils.giveItem(player, new org.bukkit.inventory.ItemStack(e.getKey(), e.getValue()));
        }

        msg(player, "Undoしました。(" + record.blocks.size() + " ブロック)", NamedTextColor.GREEN);
    }

    // ---- //rotate [角度] -----------------------------------------------------

    private void cmdRotate(Player player, String[] args) {
        int times = 1;
        if (args.length > 0) {
            try {
                int deg = Integer.parseInt(args[0]);
                if (deg % 90 != 0) {
                    msg(player, "角度は90の倍数で指定してください。(90, 180, 270)", NamedTextColor.RED);
                    return;
                }
                times = ((deg / 90) % 4 + 4) % 4;
            } catch (NumberFormatException e) {
                msg(player, "角度は整数で指定してください。", NamedTextColor.RED);
                return;
            }
        }
        if (times == 0) { msg(player, "0度回転: 変更なし。", NamedTextColor.YELLOW); return; }

        Clipboard cb = selMgr.getClipboard(player.getUniqueId());
        if (cb.isEmpty()) {
            msg(player, "クリップボードが空です。", NamedTextColor.RED);
            return;
        }
        for (int i = 0; i < times; i++) cb.rotate90();
        msg(player, (times * 90) + "度回転しました。(サイズ: " + cb.getSizeX() + "x" + cb.getSizeY() + "x" + cb.getSizeZ() + ")",
                NamedTextColor.GREEN);
    }

    // ---- //pos1, //pos2 ------------------------------------------------------

    private void cmdPos(Player player, int num) {
        Selection sel = selMgr.getOrCreate(player.getUniqueId());
        Location loc = player.getLocation();
        if (num == 1) {
            sel.setPos1(loc);
            msg(player, "Pos1 を設定: " + formatLoc(loc), NamedTextColor.AQUA);
        } else {
            sel.setPos2(loc);
            msg(player, "Pos2 を設定: " + formatLoc(loc), NamedTextColor.AQUA);
        }
        if (sel.isComplete()) {
            msg(player, "選択範囲: " + sel.volume() + " ブロック ("
                    + sel.getSizeX() + "x" + sel.getSizeY() + "x" + sel.getSizeZ() + ")", NamedTextColor.YELLOW);
        }
    }

    private void cmdInfo(Player player) {
        Selection sel = selMgr.get(player.getUniqueId());
        if (sel == null || !sel.isComplete()) {
            msg(player, "選択範囲が設定されていません。木の斧で左右クリック、または //pos1 //pos2 で選択。",
                    NamedTextColor.YELLOW);
        } else {
            msg(player, "選択範囲: " + sel.volume() + " ブロック ("
                    + sel.getSizeX() + "x" + sel.getSizeY() + "x" + sel.getSizeZ() + ")", NamedTextColor.GREEN);
        }
    }

    // ---- ヘルプ ---------------------------------------------------------------

    private void showHelp(Player player) {
        player.sendMessage(Component.text("=== サバイバルWorldEdit ===", NamedTextColor.GOLD));
        player.sendMessage(Component.text("木の斧: 左クリック=Pos1, 右クリック=Pos2", NamedTextColor.YELLOW));
        player.sendMessage(Component.text("//pos1, //pos2  - 足元で選択", NamedTextColor.WHITE));
        player.sendMessage(Component.text("//set <block>   - 範囲を埋める ※AIR指定は //confirm 必須", NamedTextColor.WHITE));
        player.sendMessage(Component.text("//replace <from> <to> - 置換", NamedTextColor.WHITE));
        player.sendMessage(Component.text("//fill <block>  - 空気を埋める", NamedTextColor.WHITE));
        player.sendMessage(Component.text("//walls <block> - 4面の壁", NamedTextColor.WHITE));
        player.sendMessage(Component.text("//outline <block> - 6面の外枠", NamedTextColor.WHITE));
        player.sendMessage(Component.text("//copy          - コピー", NamedTextColor.WHITE));
        player.sendMessage(Component.text("//paste         - ペースト (素材消費)", NamedTextColor.WHITE));
        player.sendMessage(Component.text("//rotate [角度] - クリップボードを回転", NamedTextColor.WHITE));
        player.sendMessage(Component.text("//undo          - 元に戻す (素材返却)", NamedTextColor.WHITE));
        player.sendMessage(Component.text("//confirm       - 大量操作の確認実行", NamedTextColor.AQUA));
    }

    // ---- ユーティリティ -------------------------------------------------------

    private boolean isSelectionReady(Player player, Selection sel) {
        if (sel == null || !sel.isComplete()) {
            msg(player, "選択範囲が設定されていません。", NamedTextColor.RED);
            return false;
        }
        if (!sel.getWorld().equals(player.getWorld())) {
            msg(player, "選択範囲と現在のワールドが異なります。", NamedTextColor.RED);
            return false;
        }
        return true;
    }

    private UndoRecord captureUndo(World world, Selection sel) {
        Map<Vector, BlockEntry> map = new HashMap<>();
        for (int x = sel.getMinX(); x <= sel.getMaxX(); x++)
            for (int y = sel.getMinY(); y <= sel.getMaxY(); y++)
                for (int z = sel.getMinZ(); z <= sel.getMaxZ(); z++) {
                    Block b = world.getBlockAt(x, y, z);
                    map.put(new Vector(x, y, z), new BlockEntry(b.getType(), b.getBlockData()));
                }
        return new UndoRecord(world, map);
    }

    private boolean isWall(Selection sel, int x, int y, int z) {
        return x == sel.getMinX() || x == sel.getMaxX()
                || z == sel.getMinZ() || z == sel.getMaxZ();
    }

    private boolean isOutline(Selection sel, int x, int y, int z) {
        return x == sel.getMinX() || x == sel.getMaxX()
                || y == sel.getMinY() || y == sel.getMaxY()
                || z == sel.getMinZ() || z == sel.getMaxZ();
    }

    private boolean isAir(Material m) {
        return m == Material.AIR || m == Material.CAVE_AIR || m == Material.VOID_AIR;
    }

    private Material parseMaterial(String name) {
        try { return Material.valueOf(name.toUpperCase().replace("MINECRAFT:", "")); }
        catch (IllegalArgumentException e) { return null; }
    }

    private String formatLoc(Location loc) {
        return loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ();
    }

    private void msg(Player player, String text, NamedTextColor color) {
        player.sendMessage(Component.text("[SWE] " + text, color));
    }
}
