package ir.nebula.rushisland;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.weather.WeatherChangeEvent;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.ChatColor;

public final class MatchListener implements Listener {
    private final RushIslandPlugin plugin;

    public MatchListener(RushIslandPlugin plugin) { this.plugin = plugin; }

    private GameManager manager() { return plugin.getGameManager(); }
    private Game playing(Player p) { return manager().getPlayingGame(p.getUniqueId()); }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) { manager().handleJoin(event.getPlayer()); }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) { manager().handleQuit(event.getPlayer()); }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Game g = playing(event.getEntity());
        if (g == null) return;
        event.getDrops().clear();
        event.setDroppedExp(0);
        event.setDeathMessage(null);
        g.handleDeath(event.getEntity());
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Game g = manager().getGame(event.getPlayer().getUniqueId());
        if (g != null) g.handleRespawn(event.getPlayer(), event);
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        Location to = event.getTo();
        if (to == null) return;
        Player p = event.getPlayer();
        Game g = playing(p);
        if (g == null) {
            if (manager().isMatchWorld(to) && !p.hasPermission("rushisland.admin")) event.setTo(event.getFrom());
            return;
        }
        g.handleMove(p, to);
    }

    @EventHandler(ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        Location to = event.getTo();
        Player p = event.getPlayer();
        Game g = playing(p);
        if (g != null && event.getCause() == PlayerTeleportEvent.TeleportCause.ENDER_PEARL) {
            if (!g.allowPearlTarget(p, to)) {
                event.setCancelled(true);
                p.getInventory().addItem(new ItemStack(Material.ENDER_PEARL, 1));
                p.updateInventory();
                p.sendMessage(Game.PREFIX + ChatColor.RED + "Pearl nemitune to biroon az arena bere");
            }
            return;
        }
        if (!manager().isMatchWorld(to)) return;
        if (g != null || p.hasPermission("rushisland.admin")) return;
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player p = event.getPlayer();
        Game g = manager().getGame(p.getUniqueId());
        if (g == null || !g.isPlaying(p.getUniqueId()) || p.hasPermission("rushisland.admin")) return;
        String msg = event.getMessage().toLowerCase();
        String root = msg.split(" ")[0];
        if (root.equals("/rush") || root.equals("/rushisland")) {
            String[] parts = msg.split(" ");
            String sub = parts.length > 1 ? parts[1] : "";
            if (sub.equals("leave") || sub.equals("ready") || sub.equals("help") || sub.equals("list") || sub.equals("stats")) return;
        }
        if (plugin.getSettings().allowedCommands.contains(root)) return;
        event.setCancelled(true);
        p.sendMessage(Game.PREFIX + ChatColor.RED + "Tu match faghat /rush leave kar mikone");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Game g = playing(event.getPlayer());
        if (g == null) return;
        if (!g.canPlace(event.getPlayer(), event.getBlock().getLocation())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(Game.PREFIX + ChatColor.RED + "Alan inja nemitooni block bezari");
            return;
        }
        event.setBuild(true);
        g.track(event.getBlock());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Game g = playing(event.getPlayer());
        if (g == null) return;
        event.setCancelled(true);
        g.handleBreak(event.getPlayer(), event.getBlock());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        Game g = playing(event.getPlayer());
        if (g == null) return;
        Block target = event.getBlockClicked().getRelative(event.getBlockFace());
        if (!g.canPlace(event.getPlayer(), target.getLocation())) {
            event.setCancelled(true);
            return;
        }
        g.track(target);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) {
        Game g = playing(event.getPlayer());
        if (g == null) return;
        Block target = event.getBlockClicked().getRelative(event.getBlockFace());
        if (!g.isTracked(target) && !g.isTracked(event.getBlockClicked())) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onFlow(BlockFromToEvent event) {
        if (manager().isMatchWorld(event.getBlock().getLocation())) event.setCancelled(true);
    }

    @EventHandler
    public void onWeather(WeatherChangeEvent event) {
        if (event.toWeatherState() && event.getWorld().getName().equals(plugin.getSettings().worldName)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        Player p = event.getPlayer();
        Game g = playing(p);
        if (g == null) return;
        Action a = event.getAction();
        if (a == Action.PHYSICAL) { event.setCancelled(true); return; }
        if (!g.isAlive(p.getUniqueId())) { event.setCancelled(true); return; }

        ItemStack item = event.getItem();
        if ((a == Action.RIGHT_CLICK_AIR || a == Action.RIGHT_CLICK_BLOCK) && item != null) {
            if (g.isGoldenHead(item)) {
                event.setCancelled(true);
                g.eatGoldenHead(p);
                return;
            }
            if (g.isReadyItem(item)) {
                event.setCancelled(true);
                g.toggleReady(p);
                return;
            }
            if (item.getType() == Material.ENDER_PEARL && !g.arePearlsBlocked()) {
                event.setUseItemInHand(Event.Result.ALLOW);
            }
            if (item.getType() == Material.ENDER_PEARL && g.arePearlsBlocked()) {
                event.setCancelled(true);
                p.sendMessage(Game.PREFIX + ChatColor.RED + "Ender pearl faghat tu PvP kar mikone!");
                p.updateInventory();
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player)) return;
        Player p = (Player) event.getPlayer();
        Game g = playing(p);
        if (g == null) return;
        InventoryHolder holder = event.getInventory().getHolder();
        if (holder instanceof Chest) {
            Location loc = ((Chest) holder).getLocation();
            if (!g.canOpenChest(p, loc)) {
                event.setCancelled(true);
                p.sendMessage(Game.PREFIX + ChatColor.RED + "Faghat chest haye khodet ro mitooni baz koni");
            }
        } else if (holder != null && holder != p) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) { if (playing(event.getPlayer()) != null) event.setCancelled(true); }

    @EventHandler(ignoreCancelled = true)
    public void onPickup(PlayerPickupItemEvent event) { if (playing(event.getPlayer()) != null) event.setCancelled(true); }

    @EventHandler(ignoreCancelled = true)
    public void onFood(FoodLevelChangeEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        Game g = playing((Player) event.getEntity());
        if (g != null && g.isDamageProtected(event.getEntity().getUniqueId())) {
            event.setCancelled(true);
            event.setFoodLevel(20);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        Player victim = (Player) event.getEntity();
        Game vg = playing(victim);

        Player attacker = null;
        if (event instanceof EntityDamageByEntityEvent) {
            Entity d = ((EntityDamageByEntityEvent) event).getDamager();
            if (d instanceof Player) attacker = (Player) d;
            else if (d instanceof Projectile && ((Projectile) d).getShooter() instanceof Player) attacker = (Player) ((Projectile) d).getShooter();
        }
        if (attacker != null && attacker.getUniqueId().equals(victim.getUniqueId())) attacker = null;
        Game ag = attacker == null ? null : playing(attacker);
        if (vg == null && ag == null) return;

        if (attacker != null) {
            if (vg == null || ag != vg || !vg.canFight(attacker.getUniqueId(), victim.getUniqueId())) {
                event.setCancelled(true);
                return;
            }
            vg.recordHit(victim, attacker);
        } else if (vg == null) {
            return;
        }

        if (vg.isDamageProtected(victim.getUniqueId())) {
            event.setCancelled(true);
            return;
        }
        if (victim.getHealth() - event.getFinalDamage() <= 0.0) {
            event.setCancelled(true);
            vg.eliminate(victim, attacker != null ? attacker : vg.recentAttacker(victim), causeText(event.getCause()), false);
        }
    }

    private String causeText(EntityDamageEvent.DamageCause cause) {
        switch (cause) {
            case VOID: return "fell into the void";
            case FALL: return "fell from a high place";
            case FIRE:
            case FIRE_TICK:
            case LAVA: return "burned to death";
            case DROWNING: return "drowned";
            case ENTITY_ATTACK:
            case PROJECTILE: return "was slain";
            default: return "was eliminated";
        }
    }
}
