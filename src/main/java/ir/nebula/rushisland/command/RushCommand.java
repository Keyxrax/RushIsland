package ir.nebula.rushisland.command;

import ir.nebula.rushisland.Game;
import ir.nebula.rushisland.RushIslandPlugin;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public final class RushCommand implements CommandExecutor, TabCompleter {
    private static final String PREFIX = Game.PREFIX;
    private final RushIslandPlugin plugin;

    public RushCommand(RushIslandPlugin plugin) { this.plugin = plugin; }

    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sender.sendMessage(ChatColor.AQUA + "" + ChatColor.BOLD + "RUSH ISLAND " + ChatColor.DARK_GRAY + "\u00bb " + ChatColor.GRAY + "Commands");
            sender.sendMessage(ChatColor.AQUA + "/rush join " + ChatColor.DARK_GRAY + "\u00bb " + ChatColor.GRAY + "vared match shoo");
            sender.sendMessage(ChatColor.AQUA + "/rush leave " + ChatColor.DARK_GRAY + "\u00bb " + ChatColor.GRAY + "khorooj az queue ya match");
            sender.sendMessage(ChatColor.AQUA + "/rush ready " + ChatColor.DARK_GRAY + "\u00bb " + ChatColor.GRAY + "ready for PvP");
            sender.sendMessage(ChatColor.AQUA + "/rush list " + ChatColor.DARK_GRAY + "\u00bb " + ChatColor.GRAY + "arena ha");
            sender.sendMessage(ChatColor.AQUA + "/rush stats [player] " + ChatColor.DARK_GRAY + "\u00bb " + ChatColor.GRAY + "stats");
            if (sender.hasPermission("rushisland.admin")) sender.sendMessage(ChatColor.AQUA + "/rush admin " + ChatColor.DARK_GRAY + "\u00bb " + ChatColor.GRAY + "setup | info | reload | forcestart | stop");
            return true;
        }
        String sub = args[0].toLowerCase();
        if (sub.equals("join")) {
            if (!(sender instanceof Player)) { sender.sendMessage(PREFIX + "Faghat player mitune join kone"); return true; }
            Player player = (Player) sender;
            if (!player.hasPermission("rushisland.play")) { player.sendMessage(PREFIX + ChatColor.RED + "Permission nadari"); return true; }
            int arena = 0;
            if (args.length >= 2) {
                try { arena = Integer.parseInt(args[1]); }
                catch (NumberFormatException ex) { player.sendMessage(PREFIX + ChatColor.RED + "Shomare arena bayad adad bashe"); return true; }
            }
            plugin.getGameManager().join(player, arena);
        } else if (sub.equals("leave")) {
            if (!(sender instanceof Player)) { sender.sendMessage(PREFIX + "Faghat player mitune leave kone"); return true; }
            plugin.getGameManager().leave((Player) sender);
        } else if (sub.equals("ready")) {
            if (!(sender instanceof Player)) return true;
            Player p = (Player) sender;
            Game g = plugin.getGameManager().getPlayingGame(p.getUniqueId());
            if (g == null) p.sendMessage(PREFIX + ChatColor.RED + "Tu match nisti");
            else g.toggleReady(p);
        } else if (sub.equals("list")) {
            sender.sendMessage(PREFIX + ChatColor.AQUA + "" + ChatColor.BOLD + "ARENAS");
            for (Game g : plugin.getGameManager().getGames()) sender.sendMessage(" " + g.describe());
        } else if (sub.equals("stats")) {
            Player target = sender instanceof Player ? (Player) sender : null;
            if (args.length >= 2) target = plugin.getServer().getPlayer(args[1]);
            if (target == null) {
                sender.sendMessage(PREFIX + ChatColor.YELLOW + "Usage: /rush stats [online player]");
                return true;
            }
            UUID id = target.getUniqueId();
            int kills = plugin.getStatsManager().getKills(id);
            int deaths = plugin.getStatsManager().getDeaths(id);
            double kd = deaths == 0 ? kills : (double) kills / deaths;
            sender.sendMessage(ChatColor.GOLD + "" + ChatColor.BOLD + target.getName() + " " + ChatColor.DARK_GRAY + "\u00bb " + ChatColor.GRAY + "Stats");
            sender.sendMessage(ChatColor.GRAY + "Wins " + ChatColor.GREEN + plugin.getStatsManager().getWins(id)
                    + ChatColor.DARK_GRAY + " | " + ChatColor.GRAY + "Kills " + ChatColor.GREEN + kills
                    + ChatColor.DARK_GRAY + " | " + ChatColor.GRAY + "Deaths " + ChatColor.RED + deaths);
            sender.sendMessage(ChatColor.GRAY + "K/D " + ChatColor.GOLD + String.format(java.util.Locale.US, "%.2f", kd)
                    + ChatColor.DARK_GRAY + " | " + ChatColor.GRAY + "Matches " + ChatColor.GREEN + plugin.getStatsManager().getMatches(id)
                    + ChatColor.DARK_GRAY + " | " + ChatColor.GRAY + "Rank " + ChatColor.GOLD + (plugin.getStatsManager().getRank(id) == 0 ? "-" : "#" + plugin.getStatsManager().getRank(id)));
        } else if (sub.equals("admin")) {
            handleAdmin(sender, args);
        } else {
            sender.sendMessage(PREFIX + ChatColor.RED + "Unknown command \u00bb /rush help");
        }
        return true;
    }

    private void handleAdmin(CommandSender sender, String[] args) {
        if (!sender.hasPermission("rushisland.admin")) { sender.sendMessage(PREFIX + ChatColor.RED + "Permission nadari"); return; }
        if (args.length < 2) { sender.sendMessage(PREFIX + "Usage: /rush admin <setup|info|reload|forcestart|stop> [arena]"); return; }
        String action = args[1].toLowerCase();
        if (action.equals("setup") || action.equals("reload")) {
            sender.sendMessage(PREFIX + ChatColor.YELLOW + "Config reloaded \u00bb rebuilding arenas...");
            if (!plugin.rebuildArenas(sender)) {
                sender.sendMessage(PREFIX + ChatColor.RED + "Not possible while a match, queue or arena build is active");
            }
        } else if (action.equals("info")) {
            sender.sendMessage(PREFIX + ChatColor.GREEN + "RushIsland v" + plugin.getDescription().getVersion());
            sender.sendMessage(ChatColor.GRAY + "Ready: " + plugin.isArenasReady() + " | World: " + plugin.getSettings().worldName);
            sender.sendMessage(ChatColor.GRAY + "Queued: " + plugin.getGameManager().totalQueued() + " | Playing: " + plugin.getGameManager().totalPlaying()
                    + " | Free arenas: " + plugin.getGameManager().freeArenas());
            for (Game g : plugin.getGameManager().getGames()) sender.sendMessage(" " + g.describe());
        } else if (action.equals("forcestart") || action.equals("stop")) {
            int n = 1;
            if (args.length >= 3) {
                try { n = Integer.parseInt(args[2]); } catch (NumberFormatException ex) { sender.sendMessage(PREFIX + ChatColor.RED + "Shomare arena bayad adad bashe"); return; }
            }
            Game g = plugin.getGameManager().getGame(n);
            if (g == null) { sender.sendMessage(PREFIX + ChatColor.RED + "No such arena"); return; }
            if (action.equals("stop")) {
                g.stop("Admin bazi ro tamum kard.");
                sender.sendMessage(PREFIX + ChatColor.GREEN + "Arena #" + n + " stopped");
            } else {
                sender.sendMessage(PREFIX + (g.forceStart() ? ChatColor.GREEN + "Arena #" + n + " starts now" : ChatColor.RED + "Arena #" + n + " needs at least 2 queued players"));
            }
        } else {
            sender.sendMessage(PREFIX + "Usage: /rush admin <setup|info|reload|forcestart|stop> [arena]");
        }
    }

    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> options = new ArrayList<String>();
        if (args.length == 1) {
            options.addAll(Arrays.asList("join", "leave", "ready", "list", "stats", "help"));
            if (sender.hasPermission("rushisland.admin")) options.add("admin");
        } else if (args.length == 2 && args[0].equalsIgnoreCase("admin") && sender.hasPermission("rushisland.admin")) {
            options.addAll(Arrays.asList("setup", "info", "reload", "forcestart", "stop"));
        } else if (args.length == 2 && args[0].equalsIgnoreCase("join")) {
            for (int i = 1; i <= plugin.getGameManager().getGames().size(); i++) options.add(String.valueOf(i));
        }
        List<String> result = new ArrayList<String>();
        String prefix = args.length == 0 ? "" : args[args.length - 1].toLowerCase();
        for (String option : options) if (option.startsWith(prefix)) result.add(option);
        return result;
    }
}
