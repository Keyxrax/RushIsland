package ir.nebula.rushisland;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.Bukkit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public final class PlayerSnapshot {
    private final Location location;
    private final ItemStack[] contents;
    private final ItemStack[] armor;
    private final double health;
    private final int food;
    private final float saturation;
    private final int level;
    private final float exp;
    private final GameMode gameMode;
    private final List<PotionEffect> effects;
    private final Scoreboard scoreboard;
    private final boolean allowFlight;

    public PlayerSnapshot(Player player) {
        location = player.getLocation().clone();
        contents = cloneItems(player.getInventory().getContents());
        armor = cloneItems(player.getInventory().getArmorContents());
        health = player.getHealth();
        food = player.getFoodLevel();
        saturation = player.getSaturation();
        level = player.getLevel();
        exp = player.getExp();
        gameMode = player.getGameMode();
        effects = new ArrayList<PotionEffect>(player.getActivePotionEffects());
        scoreboard = player.getScoreboard();
        allowFlight = player.getAllowFlight();
    }

    private ItemStack[] cloneItems(ItemStack[] source) {
        ItemStack[] copy = new ItemStack[source.length];
        for (int i = 0; i < source.length; i++) copy[i] = source[i] == null ? null : source[i].clone();
        return copy;
    }

    public Location getLocation() { return location.clone(); }

    public void restore(Player player) {
        player.teleport(location);
        player.getInventory().clear();
        player.getInventory().setContents(cloneItems(contents));
        player.getInventory().setArmorContents(cloneItems(armor));
        player.setGameMode(gameMode);
        player.setAllowFlight(allowFlight);
        player.setScoreboard(scoreboard != null ? scoreboard : Bukkit.getScoreboardManager().getMainScoreboard());
        player.setFoodLevel(food);
        player.setSaturation(saturation);
        player.setLevel(level);
        player.setExp(exp);
        Collection<PotionEffect> active = new ArrayList<PotionEffect>(player.getActivePotionEffects());
        for (PotionEffect effect : active) player.removePotionEffect(effect.getType());
        for (PotionEffect effect : effects) player.addPotionEffect(effect);
        player.setHealth(Math.max(1.0, Math.min(health, player.getMaxHealth())));
        player.setFireTicks(0);
        player.setFallDistance(0);
        player.updateInventory();
    }
}
