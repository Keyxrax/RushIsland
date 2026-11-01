package ir.nebula.rushisland.arena;

import ir.nebula.rushisland.Settings;
import org.bukkit.Location;
import org.bukkit.World;

public final class ArenaLayout {
    private final Settings s;

    public ArenaLayout(Settings s) { this.s = s; }

    public int laneDistance() { return s.islandRadius + s.lootIslandRadius + s.bridgeGap + 1; }
    public int pvpLocalZ() { return laneDistance() + s.pvpRadius + 120; }
    public int pvpSpawnRadius() { return Math.max(7, s.pvpRadius / 2); }
    public int pvpDepth() { return s.pvpRadius / 3 + 2; }
    public int localMinZ() { return -s.islandRadius - 10; }
    public int localMaxZ() { return pvpLocalZ() + s.pvpRadius + 10; }
    public int strideZ() { return localMaxZ() - localMinZ() + 96; }
    public int originZ(int arena) { return arena * strideZ(); }
    public int laneX(int lane) { return lane * s.laneSpacing; }
    public int pvpX() { return ((s.lanes - 1) * s.laneSpacing) / 2; }
    public int lootZ(int arena) { return originZ(arena) + laneDistance(); }
    public int pvpZ(int arena) { return originZ(arena) + pvpLocalZ(); }

    public boolean containsZ(int arena, double z) {
        return z >= originZ(arena) + localMinZ() && z <= originZ(arena) + localMaxZ();
    }

    public Location startSpawn(World w, int arena, int lane) {
        return new Location(w, laneX(lane) + 0.5, s.baseY + 1.0, originZ(arena) + 0.5, 0f, 0f);
    }

    public Location pvpCenter(World w, int arena) {
        return new Location(w, pvpX() + 0.5, s.baseY + 1.0, pvpZ(arena) + 0.5);
    }

    public int sideRadius() { return 3; }
    public int sideOffset() { return Math.max(6, Math.min(12, s.laneSpacing / 2 - sideRadius() - 3)); }
    public int sideX(int lane, int k) { return laneX(lane) + (k == 0 ? -sideOffset() : sideOffset()); }
    public int sideZ(int arena, int k) { return originZ(arena) + s.islandRadius + (s.bridgeGap * (k + 1)) / 3 + 1; }

    public int[][] chests(int arena, int lane) {
        int x = laneX(lane);
        int z = lootZ(arena);
        int y = s.baseY;
        return new int[][] {
                { x - 3, y, z }, { x + 3, y, z }, { x - 3, y, z + 3 }, { x + 3, y, z + 3 },
                { sideX(lane, 0), y, sideZ(arena, 0) }, { sideX(lane, 1), y, sideZ(arena, 1) }
        };
    }

    public boolean isLaneChest(int arena, int lane, Location loc) {
        for (int[] c : chests(arena, lane)) {
            if (c[0] == loc.getBlockX() && c[1] == loc.getBlockY() && c[2] == loc.getBlockZ()) return true;
        }
        return false;
    }

    public boolean inBridgeZone(int arena, int lane, Location loc) {
        int bx = loc.getBlockX(), by = loc.getBlockY(), bz = loc.getBlockZ();
        return Math.abs(bx - laneX(lane)) <= sideOffset() + sideRadius() + 2
                && bz >= originZ(arena) - s.islandRadius - 2
                && bz <= lootZ(arena) + s.lootIslandRadius + 2
                && by >= s.baseY - 1 && by <= s.baseY + 4;
    }

    public boolean inLootZone(int arena, int lane, Location loc) {
        return Math.abs(loc.getBlockX() - laneX(lane)) <= s.lootIslandRadius
                && Math.abs(loc.getBlockZ() - lootZ(arena)) <= s.lootIslandRadius
                && Math.abs(loc.getBlockY() - s.baseY) <= 1;
    }

    public boolean outOfLane(int arena, int lane, Location loc) {
        return Math.abs(loc.getX() - (laneX(lane) + 0.5)) > sideOffset() + sideRadius() + 8
                || loc.getZ() < originZ(arena) + localMinZ() + 4
                || loc.getZ() > lootZ(arena) + s.lootIslandRadius + 12;
    }

    public boolean inPvpBuildZone(int arena, Location loc, int radius) {
        double dx = loc.getBlockX() - pvpX();
        double dz = loc.getBlockZ() - pvpZ(arena);
        int by = loc.getBlockY();
        return radius > 1 && dx * dx + dz * dz <= (double) radius * radius
                && by >= s.baseY && by <= s.baseY + 2;
    }
}
