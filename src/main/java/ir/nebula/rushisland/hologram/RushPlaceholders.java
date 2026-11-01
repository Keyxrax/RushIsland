package ir.nebula.rushisland.hologram;

import ir.nebula.rushisland.RushIslandPlugin;
import ir.nebula.rushisland.StatsManager;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import java.util.UUID;

public final class RushPlaceholders extends PlaceholderExpansion {
    private final RushIslandPlugin plugin;

    public RushPlaceholders(RushIslandPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getIdentifier() { return "rushisland"; }

    @Override
    public String getAuthor() { return "Keyxrax"; }

    @Override
    public String getVersion() { return plugin.getDescription().getVersion(); }

    @Override
    public boolean persist() { return true; }

    @Override
    public boolean canRegister() { return true; }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        ir.nebula.rushisland.GameManager games = plugin.getGameManager();

        if (params.equalsIgnoreCase("queued")) return String.valueOf(games.totalQueued());
        if (params.equalsIgnoreCase("playing")) return String.valueOf(games.totalPlaying());
        if (params.equalsIgnoreCase("players")) return String.valueOf(games.totalQueued() + games.totalPlaying());
        if (params.equalsIgnoreCase("arenas_free")) return String.valueOf(games.freeArenas());
        if (params.equalsIgnoreCase("arenas_total")) return String.valueOf(games.getGames().size());
        if (params.toLowerCase().startsWith("arena_")) {
            String[] parts = params.toLowerCase().split("_");
            if (parts.length == 3) {
                try {
                    ir.nebula.rushisland.Game g = games.getGame(Integer.parseInt(parts[1]));
                    if (g == null) return "";
                    if (parts[2].equals("state")) return g.stateKey();
                    if (parts[2].equals("players")) return String.valueOf(g.getPlayerCount());
                    if (parts[2].equals("max")) return String.valueOf(plugin.getSettings().maxPlayers);
                } catch (NumberFormatException ignored) {
                    return "";
                }
            }
            return "";
        }

        if (player == null) return "";
        UUID id = player.getUniqueId();
        StatsManager stats = plugin.getStatsManager();

        if (params.equalsIgnoreCase("rank")) {
            int rank = stats.getRank(id);
            return rank == 0 ? ChatColor.GRAY + "Unranked" : ChatColor.GOLD + "#" + rank;
        }
        if (params.equalsIgnoreCase("wins")) return String.valueOf(stats.getWins(id));
        if (params.equalsIgnoreCase("kills")) return String.valueOf(stats.getKills(id));
        if (params.equalsIgnoreCase("deaths")) return String.valueOf(stats.getDeaths(id));
        if (params.equalsIgnoreCase("kd")) return String.format(java.util.Locale.US, "%.2f", stats.getKd(id));
        if (params.equalsIgnoreCase("matches")) return String.valueOf(stats.getMatches(id));
        if (params.equalsIgnoreCase("streak")) return String.valueOf(games.getKillStreak(id));

        if (params.startsWith("top_")) {
            String suffix = params.substring(4);
            try {
                int position = Integer.parseInt(suffix);
                if (position < 1 || position > 10) return "";
                UUID top = stats.getTopPlayer(position);
                if (top == null) return ChatColor.GRAY + "#" + position + " - Empty";
                String medal = position == 1 ? ChatColor.GOLD.toString()
                        : position == 2 ? ChatColor.GRAY.toString()
                        : position == 3 ? ChatColor.YELLOW.toString() : ChatColor.WHITE.toString();
                return medal + "#" + position + " " + stats.getName(top)
                        + ChatColor.DARK_GRAY + " - " + ChatColor.GREEN + stats.getWins(top) + " wins";
            } catch (NumberFormatException ignored) {
                return "";
            }
        }
        return null;
    }
}
