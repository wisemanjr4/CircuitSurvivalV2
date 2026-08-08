package net.circuitsurvival.managers;

import net.circuitsurvival.worldedit.Clipboard;
import net.circuitsurvival.worldedit.Selection;

import java.util.*;

public class SelectionManager {
    // プレイヤーUUID → 選択範囲
    private final Map<UUID, Selection> selections = new HashMap<>();
    // プレイヤーUUID → クリップボード
    private final Map<UUID, Clipboard> clipboards = new HashMap<>();
    // プレイヤーUUID → undo履歴 (新しい順)
    private final Map<UUID, Deque<UndoRecord>> undoHistory = new HashMap<>();

    private static final int MAX_UNDO = 10;

    public Selection getOrCreate(UUID uuid) {
        return selections.computeIfAbsent(uuid, k -> new Selection());
    }

    public Selection get(UUID uuid) {
        return selections.get(uuid);
    }

    public Clipboard getClipboard(UUID uuid) {
        return clipboards.computeIfAbsent(uuid, k -> new Clipboard());
    }

    public void pushUndo(UUID uuid, UndoRecord record) {
        Deque<UndoRecord> history = undoHistory.computeIfAbsent(uuid, k -> new ArrayDeque<>());
        history.addFirst(record);
        if (history.size() > MAX_UNDO) {
            history.removeLast();
        }
    }

    public UndoRecord popUndo(UUID uuid) {
        Deque<UndoRecord> history = undoHistory.get(uuid);
        if (history == null || history.isEmpty()) return null;
        return history.pollFirst();
    }

    // undo用: 変更前のブロックデータを格納
    public static class UndoRecord {
        public final org.bukkit.World world;
        public final Map<org.bukkit.util.Vector, net.circuitsurvival.worldedit.BlockEntry> blocks;

        public UndoRecord(org.bukkit.World world, Map<org.bukkit.util.Vector, net.circuitsurvival.worldedit.BlockEntry> blocks) {
            this.world = world;
            this.blocks = blocks;
        }
    }
}
