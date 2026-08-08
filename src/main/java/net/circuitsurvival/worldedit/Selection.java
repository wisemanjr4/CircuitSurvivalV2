package net.circuitsurvival.worldedit;

import org.bukkit.Location;
import org.bukkit.World;

/**
 * プレイヤーの選択範囲 (Pos1 〜 Pos2)
 */
public class Selection {
    private Location pos1;
    private Location pos2;

    public void setPos1(Location loc) {
        this.pos1 = loc.clone();
    }

    public void setPos2(Location loc) {
        this.pos2 = loc.clone();
    }

    public Location getPos1() {
        return pos1;
    }

    public Location getPos2() {
        return pos2;
    }

    public boolean isComplete() {
        return pos1 != null && pos2 != null
                && pos1.getWorld() != null
                && pos1.getWorld().equals(pos2.getWorld());
    }

    public World getWorld() {
        return pos1.getWorld();
    }

    public int getMinX() { return Math.min(pos1.getBlockX(), pos2.getBlockX()); }
    public int getMinY() { return Math.min(pos1.getBlockY(), pos2.getBlockY()); }
    public int getMinZ() { return Math.min(pos1.getBlockZ(), pos2.getBlockZ()); }
    public int getMaxX() { return Math.max(pos1.getBlockX(), pos2.getBlockX()); }
    public int getMaxY() { return Math.max(pos1.getBlockY(), pos2.getBlockY()); }
    public int getMaxZ() { return Math.max(pos1.getBlockZ(), pos2.getBlockZ()); }

    public int getSizeX() { return getMaxX() - getMinX() + 1; }
    public int getSizeY() { return getMaxY() - getMinY() + 1; }
    public int getSizeZ() { return getMaxZ() - getMinZ() + 1; }

    public long volume() {
        return (long) getSizeX() * getSizeY() * getSizeZ();
    }
}
