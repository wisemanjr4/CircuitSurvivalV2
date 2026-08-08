package net.circuitsurvival.worldedit;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

/**
 * コピーされたブロックデータを保持するクリップボード
 */
public class Clipboard {
    private BlockEntry[][][] blocks; // [x][y][z]
    private int sizeX, sizeY, sizeZ;
    // コピー元のpos1 (minX, minY, minZ)
    private int originX, originY, originZ;
    // プレイヤー位置からのオフセット
    private int playerOffsetX, playerOffsetY, playerOffsetZ;

    /**
     * 選択範囲をコピー
     */
    public void copy(Selection sel, Location playerLoc) {
        sizeX = sel.getSizeX();
        sizeY = sel.getSizeY();
        sizeZ = sel.getSizeZ();
        originX = sel.getMinX();
        originY = sel.getMinY();
        originZ = sel.getMinZ();
        playerOffsetX = originX - playerLoc.getBlockX();
        playerOffsetY = originY - playerLoc.getBlockY();
        playerOffsetZ = originZ - playerLoc.getBlockZ();

        blocks = new BlockEntry[sizeX][sizeY][sizeZ];
        World world = sel.getWorld();

        for (int x = 0; x < sizeX; x++) {
            for (int y = 0; y < sizeY; y++) {
                for (int z = 0; z < sizeZ; z++) {
                    Block b = world.getBlockAt(originX + x, originY + y, originZ + z);
                    blocks[x][y][z] = new BlockEntry(b.getType(), b.getBlockData());
                }
            }
        }
    }

    /**
     * 90度Y軸回転
     */
    public void rotate90() {
        BlockEntry[][][] rotated = new BlockEntry[sizeZ][sizeY][sizeX];
        for (int x = 0; x < sizeX; x++) {
            for (int y = 0; y < sizeY; y++) {
                for (int z = 0; z < sizeZ; z++) {
                    rotated[sizeZ - 1 - z][y][x] = blocks[x][y][z];
                }
            }
        }
        int tmp = sizeX;
        sizeX = sizeZ;
        sizeZ = tmp;
        // オフセットも回転に合わせてスワップ
        int offTmp = playerOffsetX;
        playerOffsetX = playerOffsetZ;
        playerOffsetZ = offTmp;
        blocks = rotated;
    }

    public boolean isEmpty() {
        return blocks == null;
    }

    public int getSizeX() { return sizeX; }
    public int getSizeY() { return sizeY; }
    public int getSizeZ() { return sizeZ; }
    public int getPlayerOffsetX() { return playerOffsetX; }
    public int getPlayerOffsetY() { return playerOffsetY; }
    public int getPlayerOffsetZ() { return playerOffsetZ; }

    public BlockEntry getBlock(int x, int y, int z) {
        return blocks[x][y][z];
    }

    /**
     * ペースト先の各ブロック座標を返す (プレイヤー位置基準)
     * コストとして必要なMaterial→個数のカウント用
     */
    public java.util.Map<Material, Integer> countMaterials() {
        java.util.Map<Material, Integer> map = new java.util.HashMap<>();
        for (int x = 0; x < sizeX; x++)
            for (int y = 0; y < sizeY; y++)
                for (int z = 0; z < sizeZ; z++) {
                    Material mat = blocks[x][y][z].getMaterial();
                    if (mat != Material.AIR && mat != Material.CAVE_AIR && mat != Material.VOID_AIR)
                        map.merge(mat, 1, Integer::sum);
                }
        return map;
    }
}
