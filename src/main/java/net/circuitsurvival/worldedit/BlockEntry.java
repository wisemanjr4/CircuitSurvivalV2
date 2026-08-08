package net.circuitsurvival.worldedit;

import org.bukkit.Material;
import org.bukkit.block.data.BlockData;

/**
 * クリップボードに保存される1ブロックのデータ
 */
public class BlockEntry {
    private final Material material;
    private final BlockData blockData;

    public BlockEntry(Material material, BlockData blockData) {
        this.material = material;
        this.blockData = blockData.clone();
    }

    public Material getMaterial() {
        return material;
    }

    public BlockData getBlockData() {
        return blockData.clone();
    }
}
