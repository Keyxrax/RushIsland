package ir.nebula.rushisland;

import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class Settings {
    public final String worldName;
    public final int arenaCount, baseY, laneSpacing, lanes, islandRadius, lootIslandRadius, bridgeGap, pvpRadius;
    public final int minPlayers, maxPlayers, countdownSeconds, fullCountdownSeconds, bridgeLootSeconds, readyCountdownSeconds;
    public final int pvpSeconds, pvpGraceSeconds, suddenDeathSeconds, shrinkIntervalSeconds, minPvpRadius;
    public final int endingSeconds, startingBridgeBlocks;
    public final boolean randomizeLanes, randomizeLoot, allowReady, scoreboard, sounds, fireworks, lightning, titles;
    public final String scoreboardTitle, scoreboardFooter;
    public final List<String> allowedCommands, winCommands, participationCommands, killCommands;

    private Settings(FileConfiguration c) {
        String w = c.getString("arena.world", "rushisland");
        worldName = (w == null || !w.matches("[A-Za-z0-9_-]+")) ? "rushisland" : w;
        arenaCount = 1;
        baseY = clamp(c.getInt("arena.base-y", 100), 20, 200);
        laneSpacing = Math.max(32, c.getInt("arena.lane-spacing", 48));
        lanes = clamp(c.getInt("arena.lanes", 8), 2, 8);
        islandRadius = Math.max(4, c.getInt("arena.island-radius", 5));
        lootIslandRadius = Math.max(5, c.getInt("arena.loot-island-radius", 6));
        bridgeGap = clamp(c.getInt("arena.bridge-gap", 32), 16, 80);
        pvpRadius = Math.max(24, c.getInt("arena.pvp-radius", 30));

        int max = Math.min(lanes, clamp(c.getInt("match.max-players", 8), 2, 8));
        maxPlayers = max;
        minPlayers = Math.min(max, clamp(c.getInt("match.min-players", 2), 2, 8));
        int countdown = Math.max(5, c.getInt("match.countdown-seconds", 20));
        countdownSeconds = countdown;
        fullCountdownSeconds = clamp(c.getInt("match.full-countdown-seconds", 5), 3, countdown);
        bridgeLootSeconds = Math.max(20, c.getInt("match.bridge-loot-seconds", 90));
        readyCountdownSeconds = clamp(c.getInt("match.ready-countdown-seconds", 5), 3, 15);
        allowReady = c.getBoolean("match.allow-ready", true);
        int pvp = Math.max(60, c.getInt("match.pvp-seconds", 300));
        pvpSeconds = pvp;
        pvpGraceSeconds = clamp(c.getInt("match.pvp-grace-seconds", 5), 0, 15);
        suddenDeathSeconds = clamp(c.getInt("match.sudden-death-seconds", 120), 0, pvp - 20);
        shrinkIntervalSeconds = Math.max(2, c.getInt("match.shrink-interval-seconds", 5));
        minPvpRadius = clamp(c.getInt("match.min-pvp-radius", 4), 3, pvpRadius - 2);
        endingSeconds = clamp(c.getInt("match.ending-seconds", 8), 3, 20);
        startingBridgeBlocks = clamp(c.getInt("match.starting-bridge-blocks", 128), 1, 320);
        randomizeLanes = c.getBoolean("match.randomize-lanes", true);
        randomizeLoot = c.getBoolean("loot.randomize", true);

        scoreboard = c.getBoolean("scoreboard.enabled", true);
        scoreboardTitle = color(c.getString("scoreboard.title", "&e&lRUSH ISLAND"), 32);
        scoreboardFooter = color(c.getString("scoreboard.footer", "&eplay.trexmine.com"), 32);
        sounds = c.getBoolean("effects.sounds", true);
        fireworks = c.getBoolean("effects.fireworks", true);
        lightning = c.getBoolean("effects.lightning", true);
        titles = c.getBoolean("effects.titles", true);

        List<String> allowed = new ArrayList<String>();
        for (String s : c.getStringList("allowed-commands")) if (s != null && !s.trim().isEmpty()) allowed.add(s.trim().toLowerCase());
        if (allowed.isEmpty()) allowed.addAll(Arrays.asList("/msg", "/tell", "/w", "/r", "/reply"));
        allowedCommands = allowed;
        winCommands = clean(c.getStringList("rewards.win-commands"));
        participationCommands = clean(c.getStringList("rewards.participation-commands"));
        killCommands = clean(c.getStringList("rewards.kill-commands"));
    }

    public static Settings from(FileConfiguration config) { return new Settings(config); }

    private static List<String> clean(List<String> in) {
        List<String> out = new ArrayList<String>();
        for (String s : in) if (s != null && !s.trim().isEmpty()) out.add(s.trim());
        return out;
    }

    private static String color(String s, int max) {
        String t = ChatColor.translateAlternateColorCodes('&', s == null ? "" : s);
        return t.length() > max ? t.substring(0, max) : t;
    }

    private static int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }
}
