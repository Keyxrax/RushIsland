package ir.nebula.rushisland;

import ir.nebula.rushisland.arena.ArenaBuilder;
import ir.nebula.rushisland.arena.ArenaLayout;
import ir.nebula.rushisland.arena.VoidGenerator;
import ir.nebula.rushisland.command.RushCommand;
import ir.nebula.rushisland.hologram.RushPlaceholders;
import org.bukkit.command.CommandSender;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public final class RushIslandPlugin extends JavaPlugin {
    private Settings settings;
    private ArenaLayout layout;
    private ArenaBuilder arenaBuilder;
    private GameManager gameManager;
    private StatsManager statsManager;
    private RushPlaceholders placeholders;
    private BukkitTask statsTask;
    private volatile boolean arenasReady;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadSettings();
        statsManager = new StatsManager(this);
        arenaBuilder = new ArenaBuilder(this);
        gameManager = new GameManager(this);

        if (getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            placeholders = new RushPlaceholders(this);
            placeholders.register();
            getLogger().info("PlaceholderAPI expansion registered: %rushisland_*%");
        } else {
            getLogger().warning("PlaceholderAPI is not installed. RushIsland hologram placeholders are unavailable.");
        }

        getServer().getPluginManager().registerEvents(new MatchListener(this), this);
        RushCommand executor = new RushCommand(this);
        getCommand("rushisland").setExecutor(executor);
        getCommand("rushisland").setTabCompleter(executor);

        statsTask = getServer().getScheduler().runTaskTimer(this, new Runnable() {
            public void run() { statsManager.flush(); }
        }, 1200L, 1200L);

        buildArenas(null);
        getLogger().info("RushIsland enabled: " + settings.arenaCount + " arena(s), up to " + settings.maxPlayers + " players each.");
    }

    @Override
    public void onDisable() {
        if (statsTask != null) statsTask.cancel();
        if (gameManager != null) gameManager.shutdown();
        if (statsManager != null) statsManager.flush();
        if (placeholders != null) placeholders.unregister();
    }

    @Override
    public ChunkGenerator getDefaultWorldGenerator(String worldName, String id) {
        return new VoidGenerator(settings != null ? settings.baseY : 100);
    }

    private void loadSettings() {
        settings = Settings.from(getConfig());
        layout = new ArenaLayout(settings);
        if (settings.startingBridgeBlocks < settings.bridgeGap + 4) {
            getLogger().warning("match.starting-bridge-blocks (" + settings.startingBridgeBlocks + ") is too close to arena.bridge-gap ("
                    + settings.bridgeGap + "): players may be unable to finish the bridge. Use at least gap + 4 (max 320).");
        }
    }

    private void buildArenas(final CommandSender notify) {
        arenasReady = false;
        arenaBuilder.buildAll(new ArenaBuilder.BuildCallback() {
            public void complete(boolean success) {
                arenasReady = success;
                if (!success) getLogger().severe("Arena unavailable. Check the console, then run /rush admin setup.");
                if (notify != null) {
                    notify.sendMessage(Game.PREFIX + (success ? org.bukkit.ChatColor.GREEN + "Arenas rebuilt \u00bb ready to play"
                            : org.bukkit.ChatColor.RED + "Arena build failed \u00bb check console"));
                }
            }
        });
    }

    public boolean rebuildArenas(CommandSender sender) {
        if (arenaBuilder.isBuilding() || gameManager.isBusy()) return false;
        reloadConfig();
        loadSettings();
        gameManager.rebuild();
        buildArenas(sender);
        return true;
    }

    public Settings getSettings() { return settings; }
    public ArenaLayout getLayout() { return layout; }
    public ArenaBuilder getArenaBuilder() { return arenaBuilder; }
    public GameManager getGameManager() { return gameManager; }
    public StatsManager getStatsManager() { return statsManager; }
    public boolean isArenasReady() { return arenasReady && !arenaBuilder.isBuilding(); }
}
