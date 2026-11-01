package ir.nebula.rushisland;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class GameManager {
    private final RushIslandPlugin plugin;
    private final List<Game> games = new ArrayList<Game>();
    private final Map<UUID, Game> byPlayer = new HashMap<UUID, Game>();

    public GameManager(RushIslandPlugin plugin) {
        this.plugin = plugin;
        createGames();
    }

    private void createGames() {
        for (int i = 0; i < plugin.getSettings().arenaCount; i++) games.add(new Game(plugin, this, i));
    }

    public void rebuild() {
        shutdown();
        createGames();
    }

    public void shutdown() {
        for (Game g : games) g.shutdown();
        games.clear();
        byPlayer.clear();
    }

    public List<Game> getGames() { return Collections.unmodifiableList(games); }
    public Game getGame(int number) { return number >= 1 && number <= games.size() ? games.get(number - 1) : null; }

    void bind(UUID id, Game g) { byPlayer.put(id, g); }
    void unbind(UUID id) { byPlayer.remove(id); }

    public Game getGame(UUID id) { return byPlayer.get(id); }

    public Game getPlayingGame(UUID id) {
        Game g = byPlayer.get(id);
        return g != null && g.isPlaying(id) ? g : null;
    }

    public boolean isMatchWorld(Location loc) {
        return loc != null && loc.getWorld() != null && loc.getWorld().getName().equals(plugin.getSettings().worldName);
    }

    public boolean isBusy() {
        for (Game g : games) if (!g.isIdle()) return true;
        return false;
    }

    private Game selectGame() {
        Game best = null;
        for (Game g : games) {
            if (!g.isJoinable()) continue;
            if (best == null) { best = g; continue; }
            boolean gCount = g.getState() == Game.State.COUNTDOWN, bCount = best.getState() == Game.State.COUNTDOWN;
            if (gCount != bCount) { if (gCount) best = g; continue; }
            if (g.getQueueSize() > best.getQueueSize()) best = g;
        }
        return best;
    }

    public boolean join(Player player, int arenaNumber) {
        if (!plugin.isArenasReady()) {
            player.sendMessage(Game.PREFIX + ChatColor.RED + "Arena dar hale amade shodan, chand sanie dige try kon");
            return false;
        }
        if (byPlayer.containsKey(player.getUniqueId())) {
            player.sendMessage(Game.PREFIX + ChatColor.RED + "Tu match hasti, aval /rush leave bezan");
            return false;
        }
        Game target;
        if (arenaNumber > 0) {
            target = getGame(arenaNumber);
            if (target == null) {
                player.sendMessage(Game.PREFIX + ChatColor.RED + "Arena #" + arenaNumber + " vojood nadare (1-" + games.size() + ")");
                return false;
            }
        } else {
            target = selectGame();
            if (target == null) {
                player.sendMessage(Game.PREFIX + ChatColor.RED + "Arena alan por e, chand daghighe dige try kon");
                return false;
            }
        }
        return target.join(player);
    }

    public void leave(Player player) {
        Game g = byPlayer.get(player.getUniqueId());
        if (g == null) {
            player.sendMessage(Game.PREFIX + ChatColor.RED + "Tu match nisti");
            return;
        }
        g.leave(player);
    }

    public void handleQuit(Player player) {
        Game g = byPlayer.get(player.getUniqueId());
        if (g != null) g.handleQuit(player);
    }

    public void handleJoin(final Player player) {
        for (Game g : games) g.handleJoin(player);
        Bukkit.getScheduler().runTaskLater(plugin, new Runnable() {
            public void run() {
                if (!player.isOnline() || getPlayingGame(player.getUniqueId()) != null) return;
                if (!isMatchWorld(player.getLocation()) || player.hasPermission("rushisland.admin")) return;
                World main = Bukkit.getWorlds().get(0);
                if (main != null && !main.getName().equals(plugin.getSettings().worldName)) {
                    player.setGameMode(org.bukkit.GameMode.SURVIVAL);
                    player.teleport(main.getSpawnLocation());
                }
            }
        }, 5L);
    }

    public int getKillStreak(UUID id) {
        Game g = byPlayer.get(id);
        return g == null ? 0 : g.getKillStreak(id);
    }

    public int totalQueued() { int n = 0; for (Game g : games) n += g.getQueueSize(); return n; }
    public int totalPlaying() {
        int n = 0;
        for (Game g : games) if (g.getState() == Game.State.BRIDGE_LOOT || g.getState() == Game.State.PVP || g.getState() == Game.State.ENDING) n += g.getPlayerCount();
        return n;
    }
    public int freeArenas() { int n = 0; for (Game g : games) if (g.isJoinable()) n++; return n; }
}
