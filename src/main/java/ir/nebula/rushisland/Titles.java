package ir.nebula.rushisland;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

public final class Titles {
    private static boolean broken;
    private static boolean ready;
    private static Class<?> cComponent, cSerializer, cTitle, cAction, cChat, cPacket;
    private static Object aTimes, aTitle, aSubtitle;
    private static Method mSerialize;
    private static Constructor<?> ctTitleTimes, ctTitle, ctChat;

    private Titles() { }

    private static boolean init() {
        if (broken) return false;
        if (ready) return true;
        try {
            String v = Bukkit.getServer().getClass().getPackage().getName().split("\\.")[3];
            String nms = "net.minecraft.server." + v + ".";
            cComponent = Class.forName(nms + "IChatBaseComponent");
            cSerializer = Class.forName(nms + "IChatBaseComponent$ChatSerializer");
            cTitle = Class.forName(nms + "PacketPlayOutTitle");
            cAction = Class.forName(nms + "PacketPlayOutTitle$EnumTitleAction");
            cChat = Class.forName(nms + "PacketPlayOutChat");
            cPacket = Class.forName(nms + "Packet");
            mSerialize = cSerializer.getMethod("a", String.class);
            aTimes = cAction.getField("TIMES").get(null);
            aTitle = cAction.getField("TITLE").get(null);
            aSubtitle = cAction.getField("SUBTITLE").get(null);
            ctTitleTimes = cTitle.getConstructor(cAction, cComponent, int.class, int.class, int.class);
            ctTitle = cTitle.getConstructor(cAction, cComponent);
            ctChat = cChat.getConstructor(cComponent, byte.class);
            ready = true;
            return true;
        } catch (Throwable t) {
            broken = true;
            return false;
        }
    }

    private static Object component(String text) throws Exception {
        String json = "{\"text\":\"" + text.replace("\\", "\\\\").replace("\"", "\\\"") + "\"}";
        return mSerialize.invoke(null, json);
    }

    private static void send(Player p, Object packet) throws Exception {
        Object handle = p.getClass().getMethod("getHandle").invoke(p);
        Object connection = handle.getClass().getField("playerConnection").get(handle);
        connection.getClass().getMethod("sendPacket", cPacket).invoke(connection, packet);
    }

    public static void send(Player p, String title, String subtitle, int fadeIn, int stay, int fadeOut) {
        if (p == null || !p.isOnline() || !init()) return;
        try {
            send(p, ctTitleTimes.newInstance(aTimes, null, fadeIn, stay, fadeOut));
            send(p, ctTitle.newInstance(aTitle, component(title == null ? "" : title)));
            send(p, ctTitle.newInstance(aSubtitle, component(subtitle == null ? "" : subtitle)));
        } catch (Throwable t) {
            broken = true;
        }
    }

    public static void actionBar(Player p, String text) {
        if (p == null || !p.isOnline() || !init()) return;
        try {
            send(p, ctChat.newInstance(component(text == null ? "" : text), (byte) 2));
        } catch (Throwable t) {
            broken = true;
        }
    }
}
