package ir.nebula.rushisland;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.List;

public final class GameScoreboard {
    private static final String CHARS = "0123456789abcdef";
    private static final int MAX_LINES = 15;

    private final Scoreboard board;
    private final Objective objective;
    private final Team[] teams = new Team[MAX_LINES];
    private int shown;

    public GameScoreboard(String title) {
        board = Bukkit.getScoreboardManager().getNewScoreboard();
        objective = board.registerNewObjective("rush", "dummy");
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        objective.setDisplayName(title);
    }

    public Scoreboard getBoard() { return board; }

    private static String entry(int i) { return ChatColor.COLOR_CHAR + "" + CHARS.charAt(i) + ChatColor.COLOR_CHAR + "r"; }

    public void update(List<String> lines) {
        int n = Math.min(lines.size(), MAX_LINES);
        for (int i = 0; i < n; i++) {
            String entry = entry(i);
            Team team = teams[i];
            if (team == null) {
                team = board.registerNewTeam("l" + i);
                team.addEntry(entry);
                teams[i] = team;
            }
            String text = lines.get(i);
            String prefix = text;
            String suffix = "";
            if (text.length() > 16) {
                prefix = text.substring(0, 16);
                suffix = text.substring(16);
                if (prefix.endsWith(String.valueOf(ChatColor.COLOR_CHAR))) {
                    prefix = prefix.substring(0, 15);
                    suffix = ChatColor.COLOR_CHAR + suffix;
                }
                suffix = ChatColor.getLastColors(prefix) + suffix;
                if (suffix.length() > 16) suffix = suffix.substring(0, 16);
            }
            if (!prefix.equals(team.getPrefix())) team.setPrefix(prefix);
            if (!suffix.equals(team.getSuffix())) team.setSuffix(suffix);
            objective.getScore(entry).setScore(n - i);
        }
        for (int i = n; i < shown; i++) board.resetScores(entry(i));
        shown = n;
    }
}
