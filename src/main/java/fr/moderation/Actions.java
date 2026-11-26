package fr.moderation;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;

/** Toutes les sanctions passent ici : application + log précis + notification du staff. */
final class Actions {
    private final ModerationPlugin plugin;
    private final Store store;

    Actions(ModerationPlugin plugin, Store store) {
        this.plugin = plugin;
        this.store = store;
    }

    String discord() { return plugin.getConfig().getString("discord", "https://discord.gg/"); }

    private static UUID idOf(CommandSender s) { return s instanceof Player p ? p.getUniqueId() : null; }

    private void log(String action, CommandSender mod, UUID id, String name, String reason, long dur, String details) {
        store.log(action, mod.getName(), idOf(mod), id, name, reason, dur, details);
    }

    void notifyStaff(CommandSender mod, String text) {
        Component msg = Util.prefix("<yellow>" + Util.e(mod.getName()) + " <gray>" + text);
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.hasPermission("moderation.notify")) p.sendMessage(msg);
        }
        Bukkit.getConsoleSender().sendMessage(msg);
    }

    boolean protectedTarget(CommandSender mod, UUID target) {
        if (mod instanceof Player m && m.getUniqueId().equals(target)) {
            mod.sendMessage(Util.prefix("<red>Tu ne peux pas te sanctionner toi-même."));
            return true;
        }
        Player t = Bukkit.getPlayer(target);
        if (t != null && t.hasPermission("moderation.bypass")) {
            mod.sendMessage(Util.prefix("<red>Ce joueur ne peut pas être sanctionné."));
            return true;
        }
        return false;
    }

    // ---------------- MUTE ----------------
    long muteDuration(int previousMutes) {
        List<String> d = plugin.getConfig().getStringList("mute.durations");
        if (d.isEmpty()) d = List.of("30m", "1h");
        long dur = Util.parse(d.get(Math.min(previousMutes, d.size() - 1)));
        return dur > 0 ? dur : 30 * 60_000L;
    }

    void mute(CommandSender mod, UUID id, String name, String reason) {
        if (protectedTarget(mod, id)) return;
        int n = store.muteCount(id);
        long dur = muteDuration(n);
        store.setMute(id, System.currentTimeMillis() + dur, reason, mod.getName());
        store.incMuteCount(id);
        log("MUTE", mod, id, name, reason, dur, "mute n°" + (n + 1));
        Player t = Bukkit.getPlayer(id);
        if (t != null) {
            t.sendMessage(Util.prefix("<red>Tu as été mute <white>" + Util.fmt(dur)
                    + "<red> — raison : <white>" + Util.e(reason)));
        }
        notifyStaff(mod, "a mute <white>" + Util.e(name) + " <gray>(" + Util.fmt(dur) + ", n°" + (n + 1)
                + ") — " + Util.e(reason));
    }

    void unmute(CommandSender mod, UUID id, String name) {
        if (store.muteRemaining(id) <= 0) {
            mod.sendMessage(Util.prefix("<red>" + Util.e(name) + " n'est pas mute."));
            return;
        }
        store.clearMute(id);
        log("UNMUTE", mod, id, name, "-", 0, "le compteur de mutes n'est pas reset");
        Player t = Bukkit.getPlayer(id);
        if (t != null) t.sendMessage(Util.prefix("<green>Tu n'es plus mute."));
        notifyStaff(mod, "a démute <white>" + Util.e(name));
    }

    // ---------------- WARN ----------------
    void warn(CommandSender mod, UUID id, String name, String reason) {
        if (protectedTarget(mod, id)) return;
        int n = store.incWarn(id);
        log("WARN", mod, id, name, reason, 0, "avertissement n°" + n);
        Player t = Bukkit.getPlayer(id);
        if (t != null) {
            t.sendMessage(Util.prefix("<red>Avertissement n°" + n + " <gray>— raison : <white>" + Util.e(reason)));
            t.showTitle(Title.title(Util.c("<red><bold>Avertissement"), Util.c("<gray>" + Util.e(reason))));
        }
        notifyStaff(mod, "a averti <white>" + Util.e(name) + " <gray>(n°" + n + ") — " + Util.e(reason));
    }

    // ---------------- FREEZE ----------------
    void freeze(CommandSender mod, Player t) {
        if (protectedTarget(mod, t.getUniqueId())) return;
        if (store.isFrozen(t.getUniqueId())) {
            mod.sendMessage(Util.prefix("<red>" + Util.e(t.getName()) + " est déjà freeze."));
            return;
        }
        store.freeze(t.getUniqueId());
        log("FREEZE", mod, t.getUniqueId(), t.getName(), "-", 0, "freeze par un modérateur");
        sendFreezeReminder(t);
        plugin.menus().openFrozen(t);
        notifyStaff(mod, "a freeze <white>" + Util.e(t.getName()));
    }

    void unfreeze(CommandSender mod, UUID id, String name) {
        if (!store.isFrozen(id)) {
            mod.sendMessage(Util.prefix("<red>" + Util.e(name) + " n'est pas freeze."));
            return;
        }
        store.unfreeze(id);
        log("UNFREEZE", mod, id, name, "-", 0, "unfreeze par un modérateur");
        Player t = Bukkit.getPlayer(id);
        if (t != null) {
            t.closeInventory();
            t.sendMessage(Util.prefix("<green>Tu n'es plus freeze."));
        }
        notifyStaff(mod, "a unfreeze <white>" + Util.e(name));
    }

    void sendFreezeReminder(Player p) {
        String url = discord();
        p.sendMessage(Util.prefix("<red><bold>Tu es freeze</bold> <gray>par un modérateur. "
                + "<red>Ne te déconnecte pas : tu serais banni."));
        p.sendMessage(Util.c("<gray>Pour demander ton unban : ")
                .append(Util.c("<aqua><underlined>" + Util.e(url)).clickEvent(ClickEvent.openUrl(url))));
    }

    /** Déconnexion pendant un freeze = ban définitif automatique. */
    void freezeDisconnect(UUID id, String name) {
        store.unfreeze(id);
        CommandSender console = Bukkit.getConsoleSender();
        banInternal(console, id, name, -1, "Déconnexion en étant freeze", "ban automatique (déconnexion pendant un freeze)", false);
    }

    // ---------------- BAN ----------------
    void ban(CommandSender mod, UUID id, String name, long durMs, String reason) {
        if (protectedTarget(mod, id)) return;
        banInternal(mod, id, name, durMs, reason, durMs < 0 ? "ban définitif" : "ban temporaire", true);
    }

    private void banInternal(CommandSender mod, UUID id, String name, long durMs, String reason,
                             String details, boolean kick) {
        long until = durMs < 0 ? -1 : System.currentTimeMillis() + durMs;
        store.setBan(id, name, until, reason, mod.getName());
        log(durMs < 0 ? "BAN_PERM" : "BAN_TEMP", mod, id, name, reason, Math.max(durMs, 0), details);
        if (kick) {
            Player t = Bukkit.getPlayer(id);
            if (t != null && t.isOnline()) t.kick(Util.banScreen(reason, durMs < 0 ? -1 : durMs, discord()));
        }
        String who = mod instanceof Player ? mod.getName() : "Système";
        Component msg = Util.prefix("<yellow>" + Util.e(who) + " <gray>a banni <white>" + Util.e(name)
                + " <gray>(" + (durMs < 0 ? "définitif" : Util.fmt(durMs)) + ") — " + Util.e(reason));
        for (Player p : Bukkit.getOnlinePlayers()) if (p.hasPermission("moderation.notify")) p.sendMessage(msg);
        Bukkit.getConsoleSender().sendMessage(msg);
    }

    void unban(CommandSender mod, UUID id, String name) {
        if (store.banRemaining(id) == null) {
            mod.sendMessage(Util.prefix("<red>" + Util.e(name) + " n'est pas banni."));
            return;
        }
        store.clearBan(id);
        log("UNBAN", mod, id, name, "-", 0, "débannissement");
        notifyStaff(mod, "a débanni <white>" + Util.e(name));
    }

    // ---------------- LOGS ----------------
    void sendHistory(CommandSender s, UUID id, String name, int n) {
        List<String> l = store.history(id, n);
        s.sendMessage(Util.prefix("<yellow>Logs de <white>" + Util.e(name) + " <yellow>(" + l.size() + " dernières)"));
        if (l.isEmpty()) s.sendMessage(Util.c("<gray>Aucun log."));
        for (String x : l) s.sendMessage(Util.c("<dark_gray>• <gray>" + Util.e(x)));
    }
}
