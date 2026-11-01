package ir.nebula.rushisland;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public final class StatsManager {
    private final File file;
    private final YamlConfiguration data;
    private List<UUID> cachedLeaderboard = new ArrayList<UUID>();
    private boolean leaderboardDirty = true;
    private volatile boolean dirty;
    private final JavaPlugin plugin;

    public StatsManager(JavaPlugin plugin) {
        this.plugin = plugin;
        if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();
        file = new File(plugin.getDataFolder(), "stats.yml");
        data = YamlConfiguration.loadConfiguration(file);
    }

    private String path(UUID id, String key) { return "players." + id.toString() + "." + key; }
    public int getWins(UUID id) { return data.getInt(path(id, "wins"), 0); }
    public int getKills(UUID id) { return data.getInt(path(id, "kills"), 0); }
    public int getMatches(UUID id) { return data.getInt(path(id, "matches"), 0); }
    public int getDeaths(UUID id) { return data.getInt(path(id, "deaths"), 0); }
    public double getKd(UUID id) {
        int deaths = getDeaths(id);
        return deaths == 0 ? getKills(id) : (double) getKills(id) / deaths;
    }
    public String getName(UUID id) {
        String saved = data.getString(path(id, "name"));
        if (saved != null && !saved.isEmpty()) return saved;
        OfflinePlayer offline = Bukkit.getOfflinePlayer(id);
        return offline.getName() == null ? "Unknown" : offline.getName();
    }
    public void recordName(UUID id, String name) {
        if (name != null && !name.equals(data.getString(path(id, "name")))) {
            data.set(path(id, "name"), name);
            leaderboardDirty = true;
            save();
        }
    }
    public void addWin(UUID id) { data.set(path(id, "wins"), getWins(id) + 1); leaderboardDirty = true; save(); }
    public void addKill(UUID id) { data.set(path(id, "kills"), getKills(id) + 1); leaderboardDirty = true; save(); }
    public void addMatch(UUID id) { data.set(path(id, "matches"), getMatches(id) + 1); leaderboardDirty = true; save(); }
    public void addDeath(UUID id) { data.set(path(id, "deaths"), getDeaths(id) + 1); leaderboardDirty = true; save(); }

    public List<UUID> getLeaderboard() {
        if (!leaderboardDirty) return new ArrayList<UUID>(cachedLeaderboard);
        List<UUID> ids = new ArrayList<UUID>();
        ConfigurationSection players = data.getConfigurationSection("players");
        if (players == null) {
            cachedLeaderboard = ids;
            leaderboardDirty = false;
            return new ArrayList<UUID>(cachedLeaderboard);
        }
        for (String key : players.getKeys(false)) {
            try { ids.add(UUID.fromString(key)); } catch (IllegalArgumentException ignored) { }
        }
        Collections.sort(ids, new Comparator<UUID>() {
            public int compare(UUID a, UUID b) {
                int wins = Integer.compare(getWins(b), getWins(a));
                if (wins != 0) return wins;
                int kills = Integer.compare(getKills(b), getKills(a));
                if (kills != 0) return kills;
                int kd = Double.compare(getKd(b), getKd(a));
                if (kd != 0) return kd;
                int deaths = Integer.compare(getDeaths(a), getDeaths(b));
                if (deaths != 0) return deaths;
                return getName(a).compareToIgnoreCase(getName(b));
            }
        });
        cachedLeaderboard = Collections.unmodifiableList(ids);
        leaderboardDirty = false;
        return new ArrayList<UUID>(cachedLeaderboard);
    }

    public int getRank(UUID id) {
        int index = getLeaderboard().indexOf(id);
        return index < 0 ? 0 : index + 1;
    }

    public UUID getTopPlayer(int position) {
        List<UUID> leaderboard = getLeaderboard();
        return position < 1 || position > leaderboard.size() ? null : leaderboard.get(position - 1);
    }

    private void save() { dirty = true; }

    public synchronized void flush() {
        if (!dirty) return;
        dirty = false;
        try { data.save(file); }
        catch (IOException ex) {
            dirty = true;
            plugin.getLogger().severe("Could not save RushIsland stats: " + ex.getMessage());
        }
    }
}
