package ir.nebula.rushisland.arena;

import ir.nebula.rushisland.RushIslandPlugin;
import ir.nebula.rushisland.Settings;
import org.bukkit.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Random;

public final class ArenaBuilder {
    private static final long BUDGET_NANOS = 15000000L;
    private static final int[] LANE_COLORS = { 14, 11, 5, 4, 1, 2, 9, 10 };

    private final RushIslandPlugin plugin;
    private World world;
    private boolean building;

    public ArenaBuilder(RushIslandPlugin plugin) { this.plugin = plugin; }

    public boolean isBuilding() { return building; }
    public World getWorld() { return world; }

    public interface BuildCallback { void complete(boolean success); }

    private Settings cfg() { return plugin.getSettings(); }
    private ArenaLayout layout() { return plugin.getLayout(); }

    public World ensureWorld() {
        Settings s = cfg();
        World w = Bukkit.getWorld(s.worldName);
        if (w == null) {
            WorldCreator creator = new WorldCreator(s.worldName);
            creator.environment(World.Environment.NORMAL);
            creator.generator(new VoidGenerator(s.baseY));
            creator.generateStructures(false);
            w = creator.createWorld();
        }
        if (w != null) {
            w.setGameRuleValue("doMobSpawning", "false");
            w.setGameRuleValue("doDaylightCycle", "false");
            w.setGameRuleValue("mobGriefing", "false");
            w.setGameRuleValue("doFireTick", "false");
            w.setTime(6000L);
            w.setStorm(false);
            w.setThundering(false);
            w.setDifficulty(Difficulty.NORMAL);
            w.setPVP(true);
            w.setSpawnFlags(false, false);
            w.setKeepSpawnInMemory(false);
            w.setAutoSave(false);
            w.setSpawnLocation(VoidGenerator.SPAWN_X, s.baseY + 1, VoidGenerator.SPAWN_Z);
        }
        world = w;
        return w;
    }

    public void buildAll(final BuildCallback callback) {
        if (building) {
            if (callback != null) callback.complete(false);
            return;
        }
        final World w = ensureWorld();
        if (w == null) {
            plugin.getLogger().severe("Could not create or load the arena world '" + cfg().worldName + "'.");
            if (callback != null) callback.complete(false);
            return;
        }
        building = true;
        final Deque<Runnable> jobs = new ArrayDeque<Runnable>();
        for (int arena = 0; arena < cfg().arenaCount; arena++) {
            final int a = arena;
            for (int lane = 0; lane < cfg().lanes; lane++) {
                final int l = lane;
                jobs.add(new Runnable() { public void run() { buildLane(w, a, l); } });
            }
            final int parts = 6;
            for (int part = 0; part < parts; part++) {
                final int pp = part;
                jobs.add(new Runnable() { public void run() { clearPvpSlice(w, a, pp, parts); } });
            }
            jobs.add(new Runnable() { public void run() { buildPvp(w, a); } });
        }
        final int total = jobs.size();
        new BukkitRunnable() {
            public void run() {
                try {
                    long start = System.nanoTime();
                    while (!jobs.isEmpty()) {
                        jobs.poll().run();
                        if (System.nanoTime() - start > BUDGET_NANOS) break;
                    }
                    if (jobs.isEmpty()) {
                        cancel();
                        building = false;
                        plugin.getLogger().info("Built " + cfg().arenaCount + " arena(s) (" + total + " steps) in world '" + w.getName() + "'.");
                        if (callback != null) callback.complete(true);
                    }
                } catch (Throwable t) {
                    cancel();
                    building = false;
                    plugin.getLogger().severe("Arena build failed: " + t);
                    t.printStackTrace();
                    if (callback != null) callback.complete(false);
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    private void buildLane(World w, int arena, int lane) {
        Settings s = cfg();
        ArenaLayout l = layout();
        int y = s.baseY;
        int x = l.laneX(lane);
        int z0 = l.originZ(arena);
        int lootZ = l.lootZ(arena);
        int pad = Math.max(Math.max(s.islandRadius, s.lootIslandRadius) + 3, l.sideOffset() + l.sideRadius() + 3);
        int depth = Math.max(s.islandRadius, s.lootIslandRadius) + 6;
        clearBox(w, x - pad, y - depth, z0 - s.islandRadius - 6, x + pad, y + 7, lootZ + s.lootIslandRadius + 6);

        int color = LANE_COLORS[lane % LANE_COLORS.length];
        long seed = (long) arena * 8L + lane;
        buildStartIsland(w, x, y, z0, s.islandRadius, color);
        buildLootIsland(w, x, y, lootZ, s.lootIslandRadius, color, seed);
        for (int k = 0; k < 2; k++) buildSideIsland(w, l.sideX(lane, k), y, l.sideZ(arena, k), l.sideRadius(), color);
        buildScenery(w, x, y, z0, lootZ, pad, seed);

        for (int z = z0 + s.islandRadius + 8; z < lootZ - s.lootIslandRadius - 2; z += 8) {
            put(w, x - 4, y + 2, z, Material.SEA_LANTERN, 0);
            put(w, x + 4, y + 2, z, Material.SEA_LANTERN, 0);
        }
        int[][] chests = l.chests(arena, lane);
        for (int[] c : chests) w.getBlockAt(c[0], c[1], c[2]).setType(Material.CHEST, false);
    }

    private void clearPvpSlice(World w, int arena, int part, int parts) {
        Settings s = cfg();
        ArenaLayout l = layout();
        int cx = l.pvpX(), cz = l.pvpZ(arena), y = s.baseY, r = s.pvpRadius + 3;
        int width = 2 * r + 1;
        int from = cx - r + (width * part) / parts;
        int to = cx - r + (width * (part + 1)) / parts - 1;
        clearBox(w, from, y - (l.pvpDepth() + 6), cz - r, to, y + 9, cz + r);
    }

    public void buildPvp(World w, int arena) {
        Settings s = cfg();
        ArenaLayout l = layout();
        buildPvpPlatform(w, l.pvpX(), s.baseY, l.pvpZ(arena), s.pvpRadius);
    }

    public void rebuildPvp(int arena) { if (world != null) buildPvp(world, arena); }

    public void shrinkRing(int arena, int radius) {
        if (world == null) return;
        Settings s = cfg();
        ArenaLayout l = layout();
        int cx = l.pvpX(), cz = l.pvpZ(arena);
        for (int x = -radius; x <= radius; x++) for (int z = -radius; z <= radius; z++) {
            int d = x * x + z * z;
            if (d > radius * radius || d <= (radius - 1) * (radius - 1)) continue;
            for (int h = -(l.pvpDepth() + 3); h <= 9; h++) world.getBlockAt(cx + x, s.baseY + h, cz + z).setType(Material.AIR, false);
        }
    }

    public void fillChests(int arena) {
        if (world == null) return;
        ArenaLayout l = layout();
        for (int lane = 0; lane < cfg().lanes; lane++) {
            Random rnd = new Random(System.nanoTime() ^ ((long) lane * 7919L));
            List<ItemStack> laneLoot = createLoot();
            if (cfg().randomizeLoot) Collections.shuffle(laneLoot, rnd);
            int[][] pos = l.chests(arena, lane);
            List<Integer> order = new ArrayList<Integer>();
            for (int i = 0; i < pos.length; i++) order.add(i);
            Collections.shuffle(order, rnd);
            List<List<ItemStack>> buckets = new ArrayList<List<ItemStack>>();
            for (int i = 0; i < pos.length; i++) buckets.add(new ArrayList<ItemStack>());
            for (int i = 0; i < laneLoot.size(); i++) buckets.get(order.get(i % pos.length)).add(laneLoot.get(i));
            for (int i = 0; i < pos.length; i++) fillChest(pos[i], buckets.get(i), rnd);
        }
    }

    private void fillChest(int[] pos, List<ItemStack> loot, Random rnd) {
        Block block = world.getBlockAt(pos[0], pos[1], pos[2]);
        if (block.getType() != Material.CHEST) block.setType(Material.CHEST, false);
        if (!(block.getState() instanceof Chest)) return;
        Chest chest = (Chest) block.getState();
        Inventory inventory = chest.getBlockInventory();
        inventory.clear();
        List<Integer> slots = new ArrayList<Integer>();
        for (int i = 0; i < inventory.getSize(); i++) slots.add(i);
        Collections.shuffle(slots, rnd);
        int n = 0;
        for (ItemStack item : loot) {
            if (n >= slots.size()) break;
            inventory.setItem(slots.get(n++), item == null ? null : item.clone());
        }
        chest.update(true, false);
    }

    private void clearBox(World w, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int yy = minY; yy <= maxY; yy++) {
                    Block b = w.getBlockAt(x, yy, z);
                    if (b.getType() != Material.AIR) b.setType(Material.AIR, false);
                }
            }
        }
    }

    @SuppressWarnings("deprecation")
    private void put(World w, int x, int y, int z, Material m, int data) {
        w.getBlockAt(x, y, z).setTypeIdAndData(m.getId(), (byte) data, false);
    }

    private void buildCone(World w, int cx, int topY, int cz, int radius, int layers) {
        int last = topY;
        for (int k = 1; k <= layers; k++) {
            int rad = radius - k;
            if (rad < 1) break;
            int lim = rad * rad + rad;
            Material m = (k % 2 == 1) ? Material.STONE : Material.COBBLESTONE;
            for (int x = -rad; x <= rad; x++) for (int z = -rad; z <= rad; z++) {
                if (x * x + z * z <= lim) put(w, cx + x, topY - k, cz + z, m, 0);
            }
            last = topY - k;
        }
        put(w, cx, last - 1, cz, Material.STONE, 0);
        put(w, cx, last - 2, cz, Material.COBBLESTONE, 0);
    }

    private void buildTree(World w, int bx, int y, int bz) {
        for (int h = 0; h < 4; h++) put(w, bx, y + h, bz, Material.LOG, 0);
        for (int dy = 3; dy <= 5; dy++) {
            int rad = dy == 5 ? 1 : 2;
            for (int dx = -rad; dx <= rad; dx++) for (int dz = -rad; dz <= rad; dz++) {
                if (rad == 2 && Math.abs(dx) == 2 && Math.abs(dz) == 2) continue;
                if (dx == 0 && dz == 0 && dy <= 3) continue;
                put(w, bx + dx, y + dy, bz + dz, Material.LEAVES, 4);
            }
        }
    }

    private void buildScenery(World w, int cx, int y, int z0, int lootZ, int pad, long seed) {
        Random rnd = new Random(seed * 104729L + 17L);
        int span = Math.max(8, lootZ - z0 - 10);
        for (int side = -1; side <= 1; side += 2) {
            for (int k = 0; k < 4; k++) {
                int rz = z0 + 6 + (span * k) / 4 + rnd.nextInt(4);
                int ry = y - 6 + rnd.nextInt(9);
                int rx = cx + side * (pad - 1 - rnd.nextInt(2));
                int n = 3 + rnd.nextInt(3);
                for (int i = 0; i < n; i++) {
                    int roll = rnd.nextInt(4);
                    Material m = roll == 0 ? Material.MOSSY_COBBLESTONE : (roll < 3 ? Material.COBBLESTONE : Material.STONE);
                    put(w, rx - side * rnd.nextInt(2), ry + rnd.nextInt(2), rz + rnd.nextInt(3), m, 0);
                }
            }
        }
    }

    private void buildStartIsland(World w, int cx, int y, int cz, int radius, int color) {
        int lim = radius * radius + radius;
        int inner = (radius - 1) * (radius - 1) + (radius - 1);
        for (int x = -radius; x <= radius; x++) for (int z = -radius; z <= radius; z++) {
            int d = x * x + z * z;
            if (d > lim) continue;
            if (d > inner) put(w, cx + x, y - 1, cz + z, Material.STAINED_CLAY, color);
            else if (Math.abs(x) <= 1 && Math.abs(z) <= 1) put(w, cx + x, y - 1, cz + z, (x == 0 && z == 0) ? Material.SEA_LANTERN : Material.QUARTZ_BLOCK, 0);
            else put(w, cx + x, y - 1, cz + z, Material.GRASS, 0);
            put(w, cx + x, y - 2, cz + z, Material.DIRT, 0);
        }
        buildCone(w, cx, y - 2, cz, radius, radius - 1);
        int pz = cz - radius + 1;
        for (int h = 0; h < 3; h++) put(w, cx, y + h, pz, Material.STAINED_CLAY, color);
        put(w, cx, y + 3, pz, Material.GLOWSTONE, 0);
        int t = Math.max(2, radius - 2);
        buildTree(w, cx - t, y, cz - t);
        buildTree(w, cx + t, y, cz - t);
    }

    private void buildLootIsland(World w, int cx, int y, int cz, int radius, int color, long seed) {
        Random rnd = new Random(seed * 7919L + cz);
        int lim = radius * radius + radius;
        int inner = (radius - 1) * (radius - 1) + (radius - 1);
        for (int x = -radius; x <= radius; x++) for (int z = -radius; z <= radius; z++) {
            int d = x * x + z * z;
            if (d > lim) continue;
            if (d > inner) {
                put(w, cx + x, y - 1, cz + z, Material.STAINED_CLAY, color);
            } else if (d <= 2) {
                if (x == 0 && z == 0) put(w, cx, y - 1, cz, Material.GOLD_BLOCK, 0);
                else put(w, cx + x, y - 1, cz + z, Material.QUARTZ_BLOCK, 1);
            } else {
                int r = rnd.nextInt(10);
                put(w, cx + x, y - 1, cz + z, Material.SMOOTH_BRICK, r < 6 ? 0 : (r < 8 ? 1 : 2));
            }
            put(w, cx + x, y - 2, cz + z, Material.STONE, 0);
        }
        buildCone(w, cx, y - 2, cz, radius, radius - 1);
        int ox = radius - 1;
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int h = 0; h < 3; h++) put(w, cx + sx * ox, y + h, cz + 2, Material.QUARTZ_BLOCK, 2);
            put(w, cx + sx * ox, y + 3, cz + 2, Material.SEA_LANTERN, 0);
        }
        int az = cz - radius + 2;
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int h = 0; h < 4; h++) put(w, cx + sx * 3, y + h, az, Material.QUARTZ_BLOCK, 2);
        }
        for (int dx = -3; dx <= 3; dx++) put(w, cx + dx, y + 4, az, Material.QUARTZ_BLOCK, 0);
        put(w, cx, y + 3, az, Material.SEA_LANTERN, 0);
    }

    private void buildSideIsland(World w, int cx, int y, int cz, int radius, int color) {
        int lim = radius * radius + radius;
        int inner = (radius - 1) * (radius - 1) + (radius - 1);
        for (int x = -radius; x <= radius; x++) for (int z = -radius; z <= radius; z++) {
            int d = x * x + z * z;
            if (d > lim) continue;
            if (d > inner) put(w, cx + x, y - 1, cz + z, Material.STAINED_CLAY, color);
            else put(w, cx + x, y - 1, cz + z, Material.SMOOTH_BRICK, 1);
            put(w, cx + x, y - 2, cz + z, Material.STONE, 0);
        }
        buildCone(w, cx, y - 2, cz, radius, radius - 1);
        put(w, cx, y - 1, cz + 1, Material.SEA_LANTERN, 0);
        for (int sx = -1; sx <= 1; sx += 2) {
            put(w, cx + sx * 2, y, cz - 1, Material.COBBLE_WALL, 0);
            put(w, cx + sx * 2, y + 1, cz - 1, Material.COBBLE_WALL, 0);
            put(w, cx + sx * 2, y + 2, cz - 1, Material.GLOWSTONE, 0);
        }
    }

    private static boolean inRing(int d, int r) { return r > 0 && d >= r * r && d <= (r + 1) * (r + 1); }

    private void buildPvpPlatform(World w, int cx, int y, int cz, int radius) {
        Random rnd = new Random(cx * 31L + cz);
        int r2 = radius * radius;
        int border = (radius - 3) * (radius - 3);
        int wall = (radius - 1) * (radius - 1);
        int gold = Math.max(2, radius / 10), quartz = gold * 2;
        int ring1 = (int) (radius * 0.55), ring2 = radius >= 24 ? (int) (radius * 0.78) : -1;
        for (int x = -radius; x <= radius; x++) for (int z = -radius; z <= radius; z++) {
            int d = x * x + z * z;
            if (d > r2) continue;
            double angle = Math.atan2(z, x) + Math.PI;
            int color = LANE_COLORS[((int) (angle / (Math.PI * 2.0) * 8.0)) % 8];
            boolean spoke = (x == 0 || z == 0 || Math.abs(x) == Math.abs(z));
            if (d <= gold * gold) {
                put(w, cx + x, y - 1, cz + z, Material.GOLD_BLOCK, 0);
            } else if (d <= quartz * quartz) {
                put(w, cx + x, y - 1, cz + z, Material.QUARTZ_BLOCK, 1);
            } else if (d >= border) {
                put(w, cx + x, y - 1, cz + z, Material.STAINED_CLAY, color);
            } else if (inRing(d, ring1) || inRing(d, ring2)) {
                put(w, cx + x, y - 1, cz + z, Material.STAINED_CLAY, 15);
            } else if (spoke) {
                put(w, cx + x, y - 1, cz + z, Material.QUARTZ_BLOCK, 0);
            } else {
                int r = rnd.nextInt(10);
                put(w, cx + x, y - 1, cz + z, Material.SMOOTH_BRICK, r < 7 ? 0 : (r < 9 ? 1 : 2));
            }
            if (d >= wall) {
                for (int h = 0; h < 8; h++) {
                    boolean lamp = h == 7 && ((x + z) & 3) == 0;
                    put(w, cx + x, y + h, cz + z, lamp ? Material.SEA_LANTERN : Material.STAINED_GLASS, lamp ? 0 : color);
                }
            }
        }
        int layers = radius / 3;
        int lastK = 0;
        for (int k = 1; k <= layers; k++) {
            int rad = radius - 2 * k;
            if (rad < 3) break;
            Material m = (k % 2 == 1) ? Material.STONE : Material.COBBLESTONE;
            for (int x = -rad; x <= rad; x++) for (int z = -rad; z <= rad; z++) {
                if (x * x + z * z <= rad * rad) put(w, cx + x, y - 1 - k, cz + z, m, 0);
            }
            lastK = k;
        }
        put(w, cx, y - 2 - lastK, cz, Material.STONE, 0);
        put(w, cx, y - 3 - lastK, cz, Material.COBBLESTONE, 0);
        put(w, cx, y - 4 - lastK, cz, Material.STONE, 0);
        for (int k = 0; k < 12; k++) {
            double a = k * Math.PI / 6.0;
            int px = cx + (int) Math.round(Math.cos(a) * (radius - 1));
            int pz = cz + (int) Math.round(Math.sin(a) * (radius - 1));
            for (int h = 0; h < 8; h++) put(w, px, y + h, pz, Material.QUARTZ_BLOCK, 2);
            put(w, px, y + 8, pz, Material.SEA_LANTERN, 0);
        }
        int crown = Math.max(3, radius / 6);
        for (int k = 0; k < 12; k++) {
            double a = k * Math.PI / 6.0;
            put(w, cx + (int) Math.round(Math.cos(a) * crown), y + 7, cz + (int) Math.round(Math.sin(a) * crown), Material.SEA_LANTERN, 0);
        }
        put(w, cx, y + 7, cz, Material.GLOWSTONE, 0);
    }

    private List<ItemStack> createLoot() {
        Random random = new Random();

        List<ItemStack> items = new ArrayList<ItemStack>();

        items.add(randomArmor(random, "HELMET"));
        items.add(randomArmor(random, "CHESTPLATE"));
        items.add(randomArmor(random, "LEGGINGS"));
        items.add(randomArmor(random, "BOOTS"));

        items.add(randomWeapon(random, false));
        items.add(randomWeapon(random, true));

        ItemStack bow = new ItemStack(Material.BOW);
        int power = random.nextInt(6);
        if (power > 0) bow.addUnsafeEnchantment(Enchantment.ARROW_DAMAGE, power);
        if (random.nextInt(100) < 35) bow.addUnsafeEnchantment(Enchantment.ARROW_KNOCKBACK, 1 + random.nextInt(2));
        if (random.nextInt(100) < 20) bow.addUnsafeEnchantment(Enchantment.ARROW_FIRE, 1);
        if (random.nextInt(100) < 12) bow.addUnsafeEnchantment(Enchantment.ARROW_INFINITE, 1);
        items.add(bow);

        items.add(new ItemStack(Material.ARROW, 16 + random.nextInt(17)));

        items.add(new ItemStack(Material.GOLDEN_APPLE, 3 + random.nextInt(10)));
        ItemStack goldenHeads = new ItemStack(Material.SKULL_ITEM, 3 + random.nextInt(10), (short) 3);
        ItemMeta headMeta = goldenHeads.getItemMeta();
        headMeta.setDisplayName(ChatColor.GOLD + "Golden Head");
        headMeta.setLore(Arrays.asList(ChatColor.GRAY + "RushIsland special loot"));
        goldenHeads.setItemMeta(headMeta);
        items.add(goldenHeads);

        Material[] blockTypes = new Material[] {
                Material.WOOD, Material.COBBLESTONE, Material.STONE,
                Material.SANDSTONE, Material.BRICK, Material.NETHERRACK,
                Material.SOUL_SAND, Material.MOSSY_COBBLESTONE
        };
        Material blockType = blockTypes[random.nextInt(blockTypes.length)];
        items.add(new ItemStack(blockType, 16 + random.nextInt(17)));

        items.add(new ItemStack(Material.ENDER_PEARL, 1 + random.nextInt(8)));

        items.add(new ItemStack(Material.COOKED_BEEF, 6 + random.nextInt(7)));
        items.add(new ItemStack(Material.WATER_BUCKET));

        return items;
    }

    private ItemStack randomArmor(Random random, String piece) {
        String[] tiers = new String[] { "LEATHER", "CHAINMAIL", "IRON", "GOLD", "DIAMOND" };
        String tier = tiers[random.nextInt(tiers.length)];
        Material material = Material.valueOf(tier + "_" + piece);
        ItemStack armor = new ItemStack(material);

        int protectionChance = 25 + (Arrays.asList(tiers).indexOf(tier) * 8);
        if (random.nextInt(100) < protectionChance) {
            armor.addUnsafeEnchantment(Enchantment.PROTECTION_ENVIRONMENTAL, 1 + random.nextInt(3));
        }
        if ("BOOTS".equals(piece) && random.nextInt(100) < 25) {
            armor.addUnsafeEnchantment(Enchantment.PROTECTION_FALL, 1 + random.nextInt(2));
        }
        return armor;
    }

    private ItemStack randomWeapon(Random random, boolean axe) {
        String[] tiers = new String[] { "WOOD", "STONE", "IRON", "GOLD", "DIAMOND" };
        String tier = tiers[random.nextInt(tiers.length)];
        Material material = Material.valueOf(tier + (axe ? "_AXE" : "_SWORD"));
        ItemStack weapon = new ItemStack(material);

        if (random.nextInt(100) < 75) {
            weapon.addUnsafeEnchantment(Enchantment.DAMAGE_ALL, 1 + random.nextInt(5));
        }
        if (!axe && random.nextInt(100) < 28) {
            weapon.addUnsafeEnchantment(Enchantment.KNOCKBACK, 1 + random.nextInt(2));
        }
        if (!axe && random.nextInt(100) < 18) {
            weapon.addUnsafeEnchantment(Enchantment.FIRE_ASPECT, 1);
        }
        return weapon;
    }
}
