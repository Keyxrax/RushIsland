package ir.nebula.rushisland.arena;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.generator.BlockPopulator;
import org.bukkit.generator.ChunkGenerator;

import java.util.Collections;
import java.util.List;
import java.util.Random;

public final class VoidGenerator extends ChunkGenerator {
    public static final int SPAWN_X = -1000;
    public static final int SPAWN_Z = -1000;

    private final int spawnY;

    public VoidGenerator(int spawnY) { this.spawnY = spawnY; }

    @Override
    public byte[][] generateBlockSections(World world, Random random, int x, int z, BiomeGrid biomes) {
        return new byte[world.getMaxHeight() / 16][];
    }

    @Override
    public Location getFixedSpawnLocation(World world, Random random) {
        return new Location(world, SPAWN_X + 0.5, spawnY + 1, SPAWN_Z + 0.5);
    }

    @Override
    public List<BlockPopulator> getDefaultPopulators(World world) {
        return Collections.<BlockPopulator>emptyList();
    }
}
