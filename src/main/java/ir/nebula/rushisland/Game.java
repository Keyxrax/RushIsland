package ir.nebula.rushisland;

import ir.nebula.rushisland.arena.ArenaLayout;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

public final class Game {
    public enum State { WAITING, COUNTDOWN, BRIDGE_LOOT, PVP, ENDING }

    public static final String PREFIX = ChatColor.AQUA + "" + ChatColor.BOLD + "RUSH " + ChatColor.DARK_GRAY + "\u00bb " + ChatColor.RESET;
    private static final String GOLDEN_HEAD_NAME = ChatColor.GOLD + "Golden Head";
    private static final String READY_NAME = ChatColor.GREEN + "" + ChatColor.BOLD + "READY " + ChatColor.GRAY + "(Rast Click)";

    private static final class BlockPos {
        final int x, y, z;
        BlockPos(int x, int y, int z) { this.x = x; this.y = y; this.z = z; }
        @Override public boolean equals(Object o) {
            if (!(o instanceof BlockPos)) return false;
            BlockPos b = (BlockPos) o;
            return b.x == x && b.y == y && b.z == z;
        }
        @Override public int hashCode() { return (x * 31 + y) * 31 + z; }
    }

    private final RushIslandPlugin plugin;
    private final GameManager manager;
    private final int index;
    private final Random random = new Random();

    private State state = State.WAITING;
    private final LinkedHashSet<UUID> queue = new LinkedHashSet<UUID>();
    private final LinkedHashSet<UUID> participants = new LinkedHashSet<UUID>();
    private final LinkedHashSet<UUID> alive = new LinkedHashSet<UUID>();
    private final Set<UUID> ready = new HashSet<UUID>();
    private final Set<UUID> arrived = new HashSet<UUID>();
    private final Map<UUID, PlayerSnapshot> snapshots = new HashMap<UUID, PlayerSnapshot>();
    private final Map<UUID, PlayerSnapshot> pendingRestore = new HashMap<UUID, PlayerSnapshot>();
    private final Set<UUID> pendingSpectate = new HashSet<UUID>();
    private final Map<UUID, Integer> laneByPlayer = new HashMap<UUID, Integer>();
    private final Map<UUID, Integer> matchKills = new HashMap<UUID, Integer>();
    private final Map<UUID, Integer> killStreaks = new HashMap<UUID, Integer>();
    private final Map<UUID, UUID> lastAttacker = new HashMap<UUID, UUID>();
    private final Map<UUID, Long> lastAttackTime = new HashMap<UUID, Long>();
    private final Map<UUID, GameScoreboard> boards = new HashMap<UUID, GameScoreboard>();
    private final Set<BlockPos> placed = new HashSet<BlockPos>();

    private BukkitTask ticker;
    private int secondsLeft, graceLeft, endingLeft, elapsed, shrinkTimer, currentPvpRadius, startedWith;
    private boolean reachedPvp, shrunk, suddenAnnounced, countsForStats;
    private UUID winnerId;

    public Game(RushIslandPlugin plugin, GameManager manager, int index) {
        this.plugin = plugin;
        this.manager = manager;
        this.index = index;
        this.ticker = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            public void run() { tick(); }
        }, 20L, 20L);
    }

    private Settings cfg() { return plugin.getSettings(); }
    private ArenaLayout layout() { return plugin.getLayout(); }
    private World world() { return plugin.getArenaBuilder().getWorld(); }
    private Location spawnFor(int lane) { return layout().startSpawn(world(), index, lane); }
    private Location pvpCenter() { return layout().pvpCenter(world(), index); }

    private void msg(Player p, String s) { p.sendMessage(PREFIX + s); }

    private List<Player> audience() {
        List<Player> list = new ArrayList<Player>();
        for (UUID id : queue) { Player p = Bukkit.getPlayer(id); if (p != null && p.isOnline()) list.add(p); }
        for (UUID id : participants) { Player p = Bukkit.getPlayer(id); if (p != null && p.isOnline()) list.add(p); }
        return list;
    }

    private void broadcast(String s) { for (Player p : audience()) p.sendMessage(PREFIX + s); }

    private void titleAll(String title, String sub, int in, int stay, int out) {
        if (!cfg().titles) return;
        for (Player p : audience()) Titles.send(p, title, sub, in, stay, out);
    }

    private void sound(Sound s, float pitch) {
        if (!cfg().sounds) return;
        for (Player p : audience()) p.playSound(p.getLocation(), s, 1f, pitch);
    }

    private static String fmt(int seconds) { return String.format("%d:%02d", seconds / 60, seconds % 60); }

    private String nameOf(UUID id) {
        Player p = Bukkit.getPlayer(id);
        return p != null ? p.getName() : plugin.getStatsManager().getName(id);
    }

    private void runCommands(List<String> commands, String player) {
        for (String c : commands) {
            try { Bukkit.dispatchCommand(Bukkit.getConsoleSender(), c.replace("%player%", player)); }
            catch (Exception ex) { plugin.getLogger().warning("Reward command failed: " + c + " (" + ex.getMessage() + ")"); }
        }
    }

    public int getIndex() { return index; }
    public State getState() { return state; }
    public int getQueueSize() { return queue.size(); }
    public int getAliveCount() { return alive.size(); }
    public int getPlayerCount() { return state == State.WAITING || state == State.COUNTDOWN ? queue.size() : participants.size(); }
    public int getSecondsLeft() { return secondsLeft; }
    public boolean isJoinable() { return (state == State.WAITING || state == State.COUNTDOWN) && queue.size() < cfg().maxPlayers; }
    public boolean isIdle() { return state == State.WAITING && queue.isEmpty(); }
    public boolean isPlaying(UUID id) { return participants.contains(id); }
    public boolean isAlive(UUID id) { return alive.contains(id); }
    public int getKillStreak(UUID id) { Integer v = killStreaks.get(id); return v == null ? 0 : v; }
    public boolean arePearlsBlocked() { return state == State.BRIDGE_LOOT; }

    public boolean allowPearlTarget(Player p, Location to) {
        if (state != State.PVP || to == null || !alive.contains(p.getUniqueId())) return false;
        Location c = pvpCenter();
        double dx = to.getX() - c.getX(), dz = to.getZ() - c.getZ();
        double r = cfg().pvpRadius - 1;
        return dx * dx + dz * dz <= r * r && to.getY() >= cfg().baseY - 1 && to.getY() <= cfg().baseY + 7;
    }

    public String describe() {
        String color;
        String label;
        switch (state) {
            case WAITING: color = ChatColor.GREEN.toString(); label = "Waiting"; break;
            case COUNTDOWN: color = ChatColor.YELLOW.toString(); label = "Starting " + secondsLeft + "s"; break;
            case BRIDGE_LOOT: color = ChatColor.RED.toString(); label = "Bridge & Loot"; break;
            case PVP: color = ChatColor.RED.toString(); label = "PvP"; break;
            default: color = ChatColor.GOLD.toString(); label = "Ending"; break;
        }
        return ChatColor.AQUA + "#" + (index + 1) + " " + color + label + ChatColor.GRAY + " (" + getPlayerCount() + "/" + cfg().maxPlayers + ")";
    }

    public String stateKey() { return state.name(); }

    public boolean join(Player player) {
        if (!isJoinable()) { msg(player, ChatColor.RED + "Arena alan available nist"); return false; }
        UUID id = player.getUniqueId();
        queue.add(id);
        manager.bind(id, this);
        broadcast(ChatColor.GREEN + "+ " + ChatColor.WHITE + player.getName() + ChatColor.DARK_GRAY + " [" + ChatColor.GRAY + queue.size() + ChatColor.DARK_GRAY + "/" + ChatColor.GRAY + cfg().maxPlayers + ChatColor.DARK_GRAY + "]");
        sound(Sound.NOTE_PLING, 1.5f);
        if (queue.size() >= cfg().minPlayers) {
            if (state == State.WAITING) startCountdown();
            if (state == State.COUNTDOWN && queue.size() >= cfg().maxPlayers && secondsLeft > cfg().fullCountdownSeconds) {
                secondsLeft = cfg().fullCountdownSeconds;
                broadcast(ChatColor.GREEN + "" + ChatColor.BOLD + "ARENA FULL " + ChatColor.DARK_GRAY + "\u00bb " + ChatColor.GRAY + "Bazi " + ChatColor.YELLOW + secondsLeft + "s" + ChatColor.GRAY + " dige shoroo mishe");
            }
        } else {
            msg(player, ChatColor.GRAY + "Montazer " + ChatColor.YELLOW + (cfg().minPlayers - queue.size()) + ChatColor.GRAY + " player dige...");
        }
        return true;
    }

    public void leave(Player player) {
        UUID id = player.getUniqueId();
        if (queue.remove(id)) {
            manager.unbind(id);
            msg(player, ChatColor.GRAY + "Az queue umadi biroon");
            broadcast(ChatColor.RED + "- " + ChatColor.WHITE + player.getName() + ChatColor.DARK_GRAY + " [" + ChatColor.GRAY + queue.size() + ChatColor.DARK_GRAY + "/" + ChatColor.GRAY + cfg().maxPlayers + ChatColor.DARK_GRAY + "]");
            if (state == State.COUNTDOWN && queue.size() < cfg().minPlayers) cancelCountdown();
            return;
        }
        if (participants.contains(id)) {
            removeParticipant(player, true);
            return;
        }
    }

    public void handleQuit(Player player) {
        UUID id = player.getUniqueId();
        if (queue.remove(id)) {
            manager.unbind(id);
            if (state == State.COUNTDOWN && queue.size() < cfg().minPlayers) cancelCountdown();
            return;
        }
        if (participants.contains(id)) removeParticipant(player, false);
    }

    private void removeParticipant(Player player, boolean voluntary) {
        UUID id = player.getUniqueId();
        boolean wasAlive = alive.remove(id);
        participants.remove(id);
        ready.remove(id);
        laneByPlayer.remove(id);
        pendingSpectate.remove(id);
        GameScoreboard board = boards.remove(id);
        PlayerSnapshot snap = snapshots.remove(id);
        if (snap != null) {
            if (player.isDead()) pendingRestore.put(id, snap);
            else snap.restore(player);
        }
        if (snap == null || !player.isDead()) manager.unbind(id);
        if (voluntary) msg(player, ChatColor.GRAY + "Az match umadi biroon");
        if (wasAlive) {
            broadcast(ChatColor.RED + "- " + ChatColor.WHITE + player.getName() + ChatColor.GRAY + " az match raft");
            checkWinner();
        }
    }

    public void handleJoin(final Player player) {
        final UUID id = player.getUniqueId();
        final PlayerSnapshot snap = pendingRestore.remove(id);
        if (snap != null) {
            manager.unbind(id);
            Bukkit.getScheduler().runTask(plugin, new Runnable() {
                public void run() { if (player.isOnline()) snap.restore(player); }
            });
        }
    }

    private void startCountdown() {
        state = State.COUNTDOWN;
        secondsLeft = queue.size() >= cfg().maxPlayers ? cfg().fullCountdownSeconds : cfg().countdownSeconds;
    }

    private void cancelCountdown() {
        if (state != State.COUNTDOWN) return;
        state = State.WAITING;
        broadcast(ChatColor.RED + "" + ChatColor.BOLD + "CANCELLED " + ChatColor.DARK_GRAY + "\u00bb " + ChatColor.GRAY + "player kam shod");
    }

    public boolean forceStart() {
        if (state == State.COUNTDOWN) { secondsLeft = 1; return true; }
        if (state == State.WAITING && queue.size() >= 2) { startCountdown(); secondsLeft = 1; return true; }
        return false;
    }

    private void beginMatch() {
        World w = world();
        List<Player> selected = new ArrayList<Player>();
        for (UUID id : queue) {
            Player p = Bukkit.getPlayer(id);
            if (p != null && p.isOnline()) selected.add(p);
        }
        if (w == null || selected.size() < cfg().minPlayers) {
            state = State.WAITING;
            if (w == null) broadcast(ChatColor.RED + "Arena world load nashode");
            return;
        }
        while (selected.size() > cfg().maxPlayers) {
            Player extra = selected.remove(selected.size() - 1);
            manager.unbind(extra.getUniqueId());
            msg(extra, ChatColor.RED + "Arena jaa nadasht");
        }
        queue.clear();
        if (cfg().randomizeLanes) Collections.shuffle(selected, random);

        participants.clear(); alive.clear(); ready.clear(); arrived.clear(); snapshots.clear();
        laneByPlayer.clear(); matchKills.clear(); killStreaks.clear(); lastAttacker.clear(); lastAttackTime.clear();
        pendingSpectate.clear(); placed.clear(); boards.clear();
        reachedPvp = false; shrunk = false; suddenAnnounced = false; winnerId = null; countsForStats = true;
        currentPvpRadius = cfg().pvpRadius;
        elapsed = 0;
        startedWith = selected.size();

        plugin.getArenaBuilder().fillChests(index);
        state = State.BRIDGE_LOOT;
        secondsLeft = cfg().bridgeLootSeconds;

        for (int lane = 0; lane < selected.size(); lane++) {
            Player p = selected.get(lane);
            UUID id = p.getUniqueId();
            snapshots.put(id, new PlayerSnapshot(p));
            participants.add(id);
            alive.add(id);
            laneByPlayer.put(id, lane);
            matchKills.put(id, 0);
            manager.bind(id, this);
            plugin.getStatsManager().recordName(id, p.getName());
            plugin.getStatsManager().addMatch(id);

            resetPlayer(p);
            for (int left = cfg().startingBridgeBlocks; left > 0; left -= 64) {
                p.getInventory().addItem(new ItemStack(Material.WOOD, Math.min(64, left)));
            }
            if (cfg().allowReady) p.getInventory().setItem(8, readyItem());
            p.teleport(spawnFor(lane));
            if (cfg().scoreboard) {
                GameScoreboard b = new GameScoreboard(cfg().scoreboardTitle);
                boards.put(id, b);
                p.setScoreboard(b.getBoard());
            }
            p.updateInventory();
        }

        broadcast(ChatColor.GREEN + "" + ChatColor.BOLD + "GAME START " + ChatColor.DARK_GRAY + "\u00bb " + ChatColor.GRAY + "Time: " + ChatColor.YELLOW + fmt(secondsLeft));
        titleAll(ChatColor.GREEN + "" + ChatColor.BOLD + "GAME START", ChatColor.GRAY + "Time: " + ChatColor.YELLOW + fmt(secondsLeft), 5, 40, 10);
        sound(Sound.LEVEL_UP, 1f);
        updateBoards();
    }

    private void resetPlayer(Player p) {
        p.getInventory().clear();
        p.getInventory().setArmorContents(new ItemStack[4]);
        p.setGameMode(GameMode.SURVIVAL);
        p.setAllowFlight(false);
        p.setFlying(false);
        p.setMaxHealth(20.0);
        p.setHealth(20.0);
        p.setFoodLevel(20);
        p.setSaturation(20f);
        p.setLevel(0);
        p.setExp(0f);
        p.setFireTicks(0);
        p.setFallDistance(0f);
        for (PotionEffect e : new ArrayList<PotionEffect>(p.getActivePotionEffects())) p.removePotionEffect(e.getType());
    }

    private ItemStack readyItem() {
        ItemStack it = new ItemStack(Material.NETHER_STAR);
        ItemMeta m = it.getItemMeta();
        m.setDisplayName(READY_NAME);
        m.setLore(Arrays.asList(ChatColor.GRAY + "Loot kardi? " + ChatColor.WHITE + "Right-click"));
        it.setItemMeta(m);
        return it;
    }

    public boolean isReadyItem(ItemStack it) {
        return it != null && it.getType() == Material.NETHER_STAR && it.hasItemMeta()
                && READY_NAME.equals(it.getItemMeta().getDisplayName());
    }

    private void tick() {
        try {
            switch (state) {
                case COUNTDOWN: tickCountdown(); break;
                case BRIDGE_LOOT: tickBridge(); break;
                case PVP: tickPvp(); break;
                case ENDING: tickEnding(); break;
                default: break;
            }
            updateBoards();
        } catch (Throwable t) {
            plugin.getLogger().severe("Arena #" + (index + 1) + " tick error: " + t);
            t.printStackTrace();
        }
    }

    private void tickCountdown() {
        if (queue.size() < cfg().minPlayers) { cancelCountdown(); return; }
        if (secondsLeft <= 5 || secondsLeft == 10 || secondsLeft == 15 || secondsLeft == 30) {
            broadcast(ChatColor.GRAY + "Game starts in " + ChatColor.YELLOW + secondsLeft + "s");
            sound(Sound.CLICK, secondsLeft <= 3 ? 1.6f : 1.1f);
            if (secondsLeft <= 3) titleAll(ChatColor.YELLOW + "" + ChatColor.BOLD + secondsLeft, "", 0, 18, 4);
        }
        if (--secondsLeft <= 0) beginMatch();
    }

    private void tickBridge() {
        elapsed++;
        for (UUID id : alive) {
            if (arrived.contains(id)) continue;
            Player p = Bukkit.getPlayer(id);
            Integer lane = laneByPlayer.get(id);
            if (p == null || lane == null) continue;
            if (layout().inLootZone(index, lane, p.getLocation())) {
                arrived.add(id);
                broadcast(ChatColor.GREEN + p.getName() + ChatColor.GRAY + " reached their island " + ChatColor.DARK_GRAY + "\u00bb " + ChatColor.YELLOW + fmt(elapsed)
                        + (arrived.size() == 1 ? ChatColor.GOLD + "  " + ChatColor.BOLD + "FIRST!" : ""));
                p.playSound(p.getLocation(), Sound.ORB_PICKUP, 1f, 1.2f);
                if (cfg().titles) Titles.actionBar(p, ChatColor.GREEN + "On your island " + ChatColor.DARK_GRAY + "\u00bb " + ChatColor.YELLOW + fmt(elapsed));
            }
        }
        if (secondsLeft == 60 || secondsLeft == 30 || secondsLeft == 10 || secondsLeft <= 5) {
            if (secondsLeft > 0) {
                broadcast(ChatColor.GRAY + "PvP starts in " + ChatColor.RED + secondsLeft + "s");
                sound(Sound.CLICK, 1.3f);
            }
        }
        if (--secondsLeft <= 0) startPvp();
    }

    private void startPvp() {
        reachedPvp = true;
        state = State.PVP;
        graceLeft = cfg().pvpGraceSeconds;
        secondsLeft = cfg().pvpSeconds;
        shrinkTimer = 0;
        ready.clear();
        Location center = pvpCenter();
        List<UUID> list = new ArrayList<UUID>(alive);
        int n = Math.max(1, list.size());
        for (int i = 0; i < list.size(); i++) {
            Player p = Bukkit.getPlayer(list.get(i));
            if (p == null) continue;
            double angle = (Math.PI * 2.0 * i) / n;
            double ring = layout().pvpSpawnRadius();
            Location spot = center.clone().add(Math.cos(angle) * ring, 0, Math.sin(angle) * ring);
            spot.setYaw((float) Math.toDegrees(Math.atan2(-(spot.getX() - center.getX()), spot.getZ() - center.getZ())) + 180f);
            p.getInventory().remove(Material.NETHER_STAR);
            p.setHealth(p.getMaxHealth());
            p.setFoodLevel(20);
            p.setSaturation(20f);
            p.setFireTicks(0);
            p.setFallDistance(0f);
            p.teleport(spot);
            p.updateInventory();
        }
        broadcast(ChatColor.RED + "" + ChatColor.BOLD + "PVP START");
        titleAll(ChatColor.RED + "" + ChatColor.BOLD + "PVP", ChatColor.GRAY + "Last one standing", 5, 35, 10);
        sound(Sound.ENDERDRAGON_GROWL, 1f);
    }

    private void tickPvp() {
        if (graceLeft > 0 && --graceLeft == 0) {
            broadcast(ChatColor.RED + "" + ChatColor.BOLD + "FIGHT!");
            titleAll(ChatColor.DARK_RED + "" + ChatColor.BOLD + "FIGHT!", "", 0, 25, 8);
            sound(Sound.WITHER_SPAWN, 1.4f);
        }
        secondsLeft--;
        int sd = cfg().suddenDeathSeconds;
        if (sd > 0 && secondsLeft <= sd) {
            if (!suddenAnnounced) {
                suddenAnnounced = true;
                shrinkTimer = 0;
                broadcast(ChatColor.DARK_RED + "" + ChatColor.BOLD + "SUDDEN DEATH " + ChatColor.DARK_GRAY + "\u00bb " + ChatColor.RED + "The arena is shrinking");
                titleAll(ChatColor.DARK_RED + "" + ChatColor.BOLD + "SUDDEN DEATH", ChatColor.RED + "The arena is shrinking", 5, 40, 10);
                sound(Sound.ENDERDRAGON_GROWL, 0.6f);
            }
            if (--shrinkTimer <= 0 && currentPvpRadius > cfg().minPvpRadius) {
                shrinkTimer = shrinkInterval();
                plugin.getArenaBuilder().shrinkRing(index, currentPvpRadius);
                currentPvpRadius--;
                shrunk = true;
                sound(Sound.EXPLODE, 0.5f);
            }
        } else if (secondsLeft == 60 || secondsLeft == 30 || secondsLeft == 10) {
            broadcast(ChatColor.GRAY + "Time left: " + ChatColor.RED + fmt(secondsLeft));
        }
        if (secondsLeft <= 0) endByTimeout();
    }

    private int shrinkInterval() {
        int steps = Math.max(1, cfg().pvpRadius - cfg().minPvpRadius);
        int fit = (cfg().suddenDeathSeconds * 3 / 4) / steps;
        return Math.max(1, Math.min(cfg().shrinkIntervalSeconds, fit));
    }

    private void endByTimeout() {
        List<UUID> list = new ArrayList<UUID>(alive);
        if (list.size() <= 1) { checkWinner(); return; }
        Collections.sort(list, new Comparator<UUID>() {
            public int compare(UUID a, UUID b) {
                Player pa = Bukkit.getPlayer(a), pb = Bukkit.getPlayer(b);
                double ha = pa == null ? 0 : pa.getHealth(), hb = pb == null ? 0 : pb.getHealth();
                int c = Double.compare(hb, ha);
                if (c != 0) return c;
                return Integer.compare(kills(b), kills(a));
            }
        });
        UUID best = list.get(0), second = list.get(1);
        Player pb = Bukkit.getPlayer(best), ps = Bukkit.getPlayer(second);
        boolean tie = pb != null && ps != null && pb.getHealth() == ps.getHealth() && kills(best) == kills(second);
        if (tie) endMatch(null, ChatColor.YELLOW + "" + ChatColor.BOLD + "TIME'S UP " + ChatColor.DARK_GRAY + "\u00bb " + ChatColor.GRAY + "mosavi shod", true);
        else endMatch(best, ChatColor.YELLOW + "" + ChatColor.BOLD + "TIME'S UP " + ChatColor.DARK_GRAY + "\u00bb " + ChatColor.GOLD + nameOf(best) + ChatColor.GRAY + " ba bishtarin jon bord", true);
    }

    private int kills(UUID id) { Integer k = matchKills.get(id); return k == null ? 0 : k; }

    private void tickEnding() {
        if (cfg().fireworks && winnerId != null) {
            Player w = Bukkit.getPlayer(winnerId);
            if (w != null && w.isOnline()) {
                Location l = w.getLocation().add((random.nextDouble() - 0.5) * 6, 1, (random.nextDouble() - 0.5) * 6);
                spawnFirework(l);
            }
        }
        if (--endingLeft <= 0) finishAndReset();
    }

    private void spawnFirework(Location loc) {
        try {
            Firework fw = loc.getWorld().spawn(loc, Firework.class);
            FireworkMeta meta = fw.getFireworkMeta();
            Color[] colors = { Color.AQUA, Color.YELLOW, Color.LIME, Color.RED, Color.ORANGE, Color.FUCHSIA };
            meta.addEffect(FireworkEffect.builder().withColor(colors[random.nextInt(colors.length)])
                    .with(FireworkEffect.Type.BALL_LARGE).flicker(true).build());
            meta.setPower(0);
            fw.setFireworkMeta(meta);
        } catch (Exception ignored) { }
    }

    private void updateBoards() {
        if (!cfg().scoreboard || boards.isEmpty()) return;
        String timeColor = (state == State.PVP && suddenAnnounced) ? ChatColor.RED.toString() : ChatColor.GREEN.toString();
        for (UUID id : participants) {
            GameScoreboard b = boards.get(id);
            Player p = Bukkit.getPlayer(id);
            if (b == null || p == null) continue;
            List<String> lines = new ArrayList<String>();
            lines.add(ChatColor.DARK_GRAY + "Arena #" + (index + 1));
            lines.add(" ");
            if (state == State.ENDING) {
                lines.add(ChatColor.WHITE + "Winner: " + ChatColor.GOLD + (winnerId == null ? "-" : nameOf(winnerId)));
            } else {
                lines.add(ChatColor.WHITE + "Time: " + timeColor + fmt(Math.max(0, secondsLeft)));
            }
            lines.add("  ");
            lines.add(ChatColor.WHITE + "Alive: " + ChatColor.GREEN + alive.size() + ChatColor.GRAY + "/" + startedWith);
            lines.add(ChatColor.WHITE + "Kills: " + ChatColor.RED + kills(id));
            if (state == State.BRIDGE_LOOT) {
                lines.add("   ");
                lines.add(ChatColor.WHITE + "Island: " + (arrived.contains(id) ? ChatColor.GREEN + "Yes" : ChatColor.RED + "No"));
                if (cfg().allowReady) lines.add(ChatColor.WHITE + "Ready: " + ChatColor.GREEN + ready.size() + ChatColor.GRAY + "/" + alive.size());
            }
            lines.add("    ");
            lines.add(cfg().scoreboardFooter);
            b.update(lines);
        }
    }

    public void toggleReady(Player p) {
        UUID id = p.getUniqueId();
        if (state != State.BRIDGE_LOOT || !alive.contains(id)) return;
        Integer lane = laneByPlayer.get(id);
        if (lane == null || !layout().inLootZone(index, lane, p.getLocation())) {
            msg(p, ChatColor.RED + "Aval boro be island lootet!");
            return;
        }
        if (!ready.add(id)) {
            ready.remove(id);
            msg(p, ChatColor.YELLOW + "Ready ro bardashti");
            return;
        }
        broadcast(ChatColor.GREEN + p.getName() + ChatColor.GRAY + " ready shod " + ChatColor.DARK_GRAY + "[" + ChatColor.GRAY + ready.size() + ChatColor.DARK_GRAY + "/" + ChatColor.GRAY + alive.size() + ChatColor.DARK_GRAY + "]");
        p.playSound(p.getLocation(), Sound.CLICK, 1f, 1.5f);
        if (ready.containsAll(alive) && secondsLeft > cfg().readyCountdownSeconds) {
            secondsLeft = cfg().readyCountdownSeconds;
            broadcast(ChatColor.GREEN + "" + ChatColor.BOLD + "ALL READY " + ChatColor.DARK_GRAY + "\u00bb " + ChatColor.GRAY + "PvP starts in " + ChatColor.YELLOW + secondsLeft + "s");
        }
    }

    private BlockPos pos(Block b) { return new BlockPos(b.getX(), b.getY(), b.getZ()); }
    public boolean isTracked(Block b) { return placed.contains(pos(b)); }
    public void track(Block b) { placed.add(pos(b)); }

    public boolean canPlace(Player p, Location loc) {
        UUID id = p.getUniqueId();
        if (!alive.contains(id)) return false;
        if (state == State.BRIDGE_LOOT) {
            Integer lane = laneByPlayer.get(id);
            return lane != null && layout().inBridgeZone(index, lane, loc);
        }
        if (state == State.PVP) return layout().inPvpBuildZone(index, loc, currentPvpRadius - 3);
        return false;
    }

    public void handleBreak(Player p, Block block) {
        if (!alive.contains(p.getUniqueId()) || (state != State.BRIDGE_LOOT && state != State.PVP)) return;
        if (!placed.remove(pos(block))) return;
        Material type = block.getType();
        byte data = block.getData();
        block.setType(Material.AIR, false);
        if (type.isBlock() && type != Material.AIR && type != Material.WATER && type != Material.STATIONARY_WATER) {
            p.getInventory().addItem(new ItemStack(type, 1, (short) data));
        }
    }

    public boolean canOpenChest(Player p, Location chest) {
        UUID id = p.getUniqueId();
        if (!alive.contains(id)) return false;
        if (state != State.BRIDGE_LOOT) return false;
        Integer lane = laneByPlayer.get(id);
        return lane != null && layout().isLaneChest(index, lane, chest);
    }

    public boolean canFight(UUID attacker, UUID victim) {
        return state == State.PVP && graceLeft <= 0 && alive.contains(attacker) && alive.contains(victim) && !attacker.equals(victim);
    }

    public boolean isDamageProtected(UUID victim) {
        return !alive.contains(victim) || state != State.PVP || graceLeft > 0;
    }

    public void recordHit(Player victim, Player attacker) {
        lastAttacker.put(victim.getUniqueId(), attacker.getUniqueId());
        lastAttackTime.put(victim.getUniqueId(), System.currentTimeMillis());
    }

    public Player recentAttacker(Player victim) {
        Long t = lastAttackTime.get(victim.getUniqueId());
        UUID a = lastAttacker.get(victim.getUniqueId());
        if (t == null || a == null || System.currentTimeMillis() - t > 10000L) return null;
        Player p = Bukkit.getPlayer(a);
        return p != null && alive.contains(a) ? p : null;
    }

    public void handleMove(Player player, Location to) {
        UUID id = player.getUniqueId();
        if (!alive.contains(id)) return;
        int baseY = cfg().baseY;
        if (state == State.BRIDGE_LOOT && to.getY() < baseY - 20) {
            Integer lane = laneByPlayer.get(id);
            if (lane != null) { player.setFallDistance(0f); player.teleport(spawnFor(lane)); }
        } else if (state == State.BRIDGE_LOOT) {
            Integer lane = laneByPlayer.get(id);
            if (lane != null && layout().outOfLane(index, lane, to)) {
                player.teleport(spawnFor(lane));
                msg(player, ChatColor.RED + "Az masire khodet kharej nashoo!");
            }
        } else if (state == State.PVP && to.getY() < baseY - 8) {
            eliminate(player, recentAttacker(player), "fell into the void", false);
        } else if (state == State.PVP) {
            Location c = pvpCenter();
            double dx = to.getX() - c.getX(), dz = to.getZ() - c.getZ();
            double r = cfg().pvpRadius + 1;
            if (to.getY() > baseY + 12 || dx * dx + dz * dz > r * r) {
                player.setFallDistance(0f);
                player.teleport(c.clone().add(0, 1, 0));
            }
        } else if (state == State.ENDING && to.getY() < baseY - 20) {
            player.setFallDistance(0f);
            player.teleport(pvpCenter());
        }
    }

    public void handleDeath(Player victim) {
        if (!participants.contains(victim.getUniqueId())) return;
        eliminate(victim, recentAttacker(victim), "was eliminated", true);
    }

    public void handleRespawn(final Player player, PlayerRespawnEvent event) {
        final UUID id = player.getUniqueId();
        final PlayerSnapshot snap = pendingRestore.remove(id);
        if (snap != null) {
            event.setRespawnLocation(snap.getLocation());
            manager.unbind(id);
            Bukkit.getScheduler().runTask(plugin, new Runnable() {
                public void run() { if (player.isOnline()) snap.restore(player); }
            });
            return;
        }
        if (pendingSpectate.remove(id) && participants.contains(id)) {
            event.setRespawnLocation(pvpCenter().add(0, 8, 0));
            Bukkit.getScheduler().runTask(plugin, new Runnable() {
                public void run() { if (player.isOnline() && participants.contains(id)) makeSpectator(player); }
            });
        }
    }

    public void eliminate(Player victim, Player killer, String cause, boolean dead) {
        UUID vid = victim.getUniqueId();
        if (!alive.remove(vid)) return;
        ready.remove(vid);
        killStreaks.remove(vid);
        plugin.getStatsManager().addDeath(vid);

        boolean credited = false;
        if (killer != null && state == State.PVP && !killer.getUniqueId().equals(vid) && alive.contains(killer.getUniqueId())) {
            UUID kid = killer.getUniqueId();
            plugin.getStatsManager().addKill(kid);
            Integer k = matchKills.get(kid);
            matchKills.put(kid, (k == null ? 0 : k) + 1);
            Integer s = killStreaks.get(kid);
            int streak = (s == null ? 0 : s) + 1;
            killStreaks.put(kid, streak);
            broadcast(ChatColor.RED + killer.getName() + ChatColor.GRAY + " killed " + ChatColor.YELLOW + victim.getName()
                    + ChatColor.DARK_GRAY + " \u00bb " + ChatColor.GREEN + alive.size() + ChatColor.GRAY + " left");
            if (streak >= 3) broadcast(ChatColor.GOLD + killer.getName() + ChatColor.YELLOW + " is on a " + ChatColor.GOLD + streak + ChatColor.YELLOW + " kill streak!");
            killer.playSound(killer.getLocation(), Sound.ORB_PICKUP, 1f, 0.8f);
            if (cfg().titles) Titles.actionBar(killer, ChatColor.RED + "+1 Kill " + ChatColor.DARK_GRAY + "\u00bb " + ChatColor.GRAY + victim.getName());
            runCommands(cfg().killCommands, killer.getName());
            credited = true;
        }
        if (!credited) broadcast(ChatColor.YELLOW + victim.getName() + ChatColor.GRAY + " " + cause + ChatColor.DARK_GRAY + " \u00bb " + ChatColor.GREEN + alive.size() + ChatColor.GRAY + " left");

        if (cfg().lightning && state == State.PVP) {
            try { victim.getWorld().strikeLightningEffect(victim.getLocation()); } catch (Exception ignored) { }
        }
        if (dead) pendingSpectate.add(vid);
        else makeSpectator(victim);
        msg(victim, ChatColor.RED + "" + ChatColor.BOLD + "ELIMINATED " + ChatColor.DARK_GRAY + "\u00bb " + ChatColor.GRAY + "/rush leave baraye khorooj");
        if (cfg().titles) Titles.send(victim, ChatColor.RED + "" + ChatColor.BOLD + "ELIMINATED", ChatColor.GRAY + "Alan tamasha mikoni", 5, 40, 10);
        sound(Sound.ANVIL_LAND, 1.2f);
        checkWinner();
    }

    private void makeSpectator(Player p) {
        p.getInventory().clear();
        p.getInventory().setArmorContents(new ItemStack[4]);
        for (PotionEffect e : new ArrayList<PotionEffect>(p.getActivePotionEffects())) p.removePotionEffect(e.getType());
        p.setHealth(p.getMaxHealth());
        p.setFoodLevel(20);
        p.setFireTicks(0);
        p.setFallDistance(0f);
        p.setGameMode(GameMode.SPECTATOR);
        p.teleport(pvpCenter().add(0, 8, 0));
    }

    public boolean isGoldenHead(ItemStack it) {
        return it != null && it.getType() == Material.SKULL_ITEM && it.hasItemMeta()
                && GOLDEN_HEAD_NAME.equals(it.getItemMeta().getDisplayName());
    }

    public void eatGoldenHead(Player p) {
        if (!alive.contains(p.getUniqueId()) || (state != State.BRIDGE_LOOT && state != State.PVP)) return;
        ItemStack hand = p.getItemInHand();
        if (!isGoldenHead(hand)) return;
        if (hand.getAmount() <= 1) p.setItemInHand(null);
        else hand.setAmount(hand.getAmount() - 1);
        p.removePotionEffect(PotionEffectType.REGENERATION);
        p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 160, 1));
        p.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 2400, 0));
        p.playSound(p.getLocation(), Sound.BURP, 1f, 1f);
        p.updateInventory();
    }

    private void checkWinner() {
        if (state != State.BRIDGE_LOOT && state != State.PVP) return;
        if (alive.size() > 1) return;
        if (alive.isEmpty()) { endMatch(null, ChatColor.YELLOW + "" + ChatColor.BOLD + "NO WINNER " + ChatColor.DARK_GRAY + "\u00bb " + ChatColor.GRAY + "hame raftan", reachedPvp); return; }
        UUID last = alive.iterator().next();
        if (!reachedPvp) {
            endMatch(null, ChatColor.RED + "" + ChatColor.BOLD + "CANCELLED " + ChatColor.DARK_GRAY + "\u00bb " + ChatColor.GRAY + "player kam moond", false);
            return;
        }
        endMatch(last, ChatColor.GOLD + "" + ChatColor.BOLD + "WINNER " + ChatColor.YELLOW + nameOf(last), true);
    }

    private void endMatch(UUID winner, String headline, boolean counts) {
        if (state == State.ENDING) return;
        state = State.ENDING;
        endingLeft = cfg().endingSeconds;
        winnerId = winner;
        countsForStats = counts;
        broadcast(ChatColor.DARK_GRAY + "" + ChatColor.STRIKETHROUGH + "                                ");
        broadcast(headline);
        if (counts) {
            List<UUID> top = new ArrayList<UUID>(matchKills.keySet());
            Collections.sort(top, new Comparator<UUID>() {
                public int compare(UUID a, UUID b) { return Integer.compare(kills(b), kills(a)); }
            });
            int shown = 0;
            for (UUID id : top) {
                if (kills(id) <= 0 || shown >= 3) break;
                broadcast(ChatColor.GRAY + "#" + (++shown) + " " + ChatColor.YELLOW + nameOf(id) + ChatColor.DARK_GRAY + " \u00bb " + ChatColor.RED + kills(id) + (kills(id) == 1 ? " kill" : " kills"));
            }
            if (winner != null) {
                plugin.getStatsManager().addWin(winner);
                runCommands(cfg().winCommands, nameOf(winner));
            }
            for (UUID id : participants) {
                Player p = Bukkit.getPlayer(id);
                if (p != null) runCommands(cfg().participationCommands, p.getName());
            }
        }
        broadcast(ChatColor.DARK_GRAY + "" + ChatColor.STRIKETHROUGH + "                                ");
        if (winner != null) titleAll(ChatColor.GOLD + "" + ChatColor.BOLD + "WINNER", ChatColor.YELLOW + nameOf(winner), 5, 70, 15);
        else titleAll(ChatColor.YELLOW + "" + ChatColor.BOLD + "GAME OVER", "", 5, 50, 15);
        sound(Sound.LEVEL_UP, 1f);
    }

    private void finishAndReset() {
        for (UUID id : new ArrayList<UUID>(participants)) {
            Player p = Bukkit.getPlayer(id);
            PlayerSnapshot snap = snapshots.remove(id);
            boards.remove(id);
            if (p != null && p.isOnline() && snap != null) {
                if (p.isDead()) { pendingRestore.put(id, snap); continue; }
                snap.restore(p);
                msg(p, ChatColor.GREEN + "" + ChatColor.BOLD + "GG! " + ChatColor.GRAY + "Mersi ke bazi kardi");
            }
            manager.unbind(id);
        }
        participants.clear(); alive.clear(); ready.clear(); arrived.clear(); snapshots.clear(); boards.clear();
        laneByPlayer.clear(); matchKills.clear(); killStreaks.clear(); lastAttacker.clear(); lastAttackTime.clear();
        pendingSpectate.clear();
        winnerId = null;
        resetArena();
        state = State.WAITING;
    }

    private void resetArena() {
        World w = world();
        if (w == null) { placed.clear(); return; }
        for (BlockPos p : placed) {
            Block b = w.getBlockAt(p.x, p.y, p.z);
            if (b.getType() != Material.AIR) b.setType(Material.AIR, false);
        }
        placed.clear();
        try {
            ArenaLayout l = layout();
            for (Entity e : w.getEntitiesByClasses(Arrow.class, Item.class, EnderPearl.class, Firework.class)) {
                if (l.containsZ(index, e.getLocation().getZ())) e.remove();
            }
        } catch (Exception ignored) { }
        if (shrunk) {
            plugin.getArenaBuilder().rebuildPvp(index);
            shrunk = false;
        }
        currentPvpRadius = cfg().pvpRadius;
    }

    public void stop(String reason) {
        for (Player p : audience()) p.sendMessage(PREFIX + ChatColor.RED + reason);
        for (UUID id : new ArrayList<UUID>(queue)) manager.unbind(id);
        queue.clear();
        for (UUID id : new ArrayList<UUID>(participants)) {
            Player p = Bukkit.getPlayer(id);
            PlayerSnapshot snap = snapshots.remove(id);
            if (p != null && p.isOnline() && snap != null) {
                if (p.isDead()) { pendingRestore.put(id, snap); continue; }
                snap.restore(p);
            }
            manager.unbind(id);
        }
        participants.clear(); alive.clear(); ready.clear(); arrived.clear(); snapshots.clear(); boards.clear();
        laneByPlayer.clear(); matchKills.clear(); killStreaks.clear(); lastAttacker.clear(); lastAttackTime.clear();
        pendingSpectate.clear();
        winnerId = null;
        resetArena();
        state = State.WAITING;
    }

    public void shutdown() {
        if (ticker != null) { ticker.cancel(); ticker = null; }
        try { stop("Arena dare baste mishe."); } catch (Throwable ignored) { }
    }
}
