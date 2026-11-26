package fr.moderation;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

final class Util {
    private static final MiniMessage MM = MiniMessage.miniMessage();

    private Util() {}

    static Component c(String s) { return MM.deserialize(s); }

    /** Échappe le texte saisi par un joueur avant de l'insérer dans du MiniMessage. */
    static String e(String s) { return MM.escapeTags(s == null ? "" : s); }

    static Component prefix(String s) {
        return c("<dark_gray>[<red>Modération<dark_gray>] <gray>" + s);
    }

    static ItemStack item(Material m, String name, String... lore) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(c("<!italic>" + name));
        List<Component> l = new ArrayList<>();
        for (String s : lore) l.add(c("<!italic><gray>" + s));
        if (!l.isEmpty()) meta.lore(l);
        it.setItemMeta(meta);
        return it;
    }

    static String fmt(long ms) {
        if (ms < 0) return "définitif";
        long s = ms / 1000;
        long d = s / 86400, h = (s % 86400) / 3600, m = (s % 3600) / 60, sec = s % 60;
        StringBuilder sb = new StringBuilder();
        if (d > 0) sb.append(d).append("j ");
        if (h > 0) sb.append(h).append("h ");
        if (m > 0) sb.append(m).append("min ");
        if (d == 0 && h == 0 && sec > 0) sb.append(sec).append("s");
        return sb.length() == 0 ? "0s" : sb.toString().trim();
    }

    /** "30m", "1h", "2d", "1w" -> millisecondes (0 si invalide). */
    static long parse(String in) {
        try {
            in = in.trim().toLowerCase();
            long n = Long.parseLong(in.substring(0, in.length() - 1));
            return switch (in.charAt(in.length() - 1)) {
                case 's' -> n * 1000L;
                case 'm' -> n * 60_000L;
                case 'h' -> n * 3_600_000L;
                case 'd' -> n * 86_400_000L;
                case 'w' -> n * 604_800_000L;
                default -> 0L;
            };
        } catch (Exception ex) {
            return 0L;
        }
    }

    static Component banScreen(String reason, long remaining, String discord) {
        String dur = remaining < 0 ? "Définitif" : fmt(remaining);
        return c("<red><bold>Tu as été banni du serveur</bold><newline><newline>"
                + "<gray>Raison : <white>" + e(reason) + "<newline>"
                + "<gray>Durée restante : <white>" + dur + "<newline><newline>"
                + "<gray>Demande d'unban sur Discord :<newline><aqua>" + e(discord));
    }
}
