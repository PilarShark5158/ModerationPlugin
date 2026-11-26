package fr.moderation;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

final class ModCommand implements CommandExecutor, TabCompleter {
    private static final List<String> SUBS = List.of("unmute", "unban", "unfreeze", "logs", "reload");

    private record Target(UUID id, String name) {}

    private final ModerationPlugin plugin;
    private final Store store;
    private final Actions actions;
    private final Menus menus;

    ModCommand(ModerationPlugin plugin, Store store, Actions actions, Menus menus) {
        this.plugin = plugin;
        this.store = store;
        this.actions = actions;
        this.menus = menus;
    }

    private Target resolve(String name) {
        Player p = Bukkit.getPlayerExact(name);
        if (p != null) return new Target(p.getUniqueId(), p.getName());
        UUID banned = store.findBannedByName(name);
        if (banned != null) return new Target(banned, name);
        OfflinePlayer op = Bukkit.getOfflinePlayerIfCached(name);
        if (op != null) return new Target(op.getUniqueId(), op.getName() != null ? op.getName() : name);
        return null;
    }

    private boolean deny(CommandSender s, String node) {
        if (s.hasPermission(node)) return false;
        s.sendMessage(Util.prefix("<red>Tu n'as pas la permission <white>" + node + "<red>."));
        return true;
    }

    @Override
    public boolean onCommand(CommandSender s, Command c, String label, String[] a) {
        if (a.length == 0) {
            s.sendMessage(Util.prefix("<yellow>/moderation <joueur> <gray>— ouvre le menu"));
            s.sendMessage(Util.c("<gray>/moderation unmute|unban|unfreeze <joueur>"));
            s.sendMessage(Util.c("<gray>/moderation logs <joueur> [nombre]"));
            return true;
        }

        String sub = a[0].toLowerCase(Locale.ROOT);
        if (sub.equals("reload")) {
            if (deny(s, "moderation.reload")) return true;
            plugin.reloadConfig();
            s.sendMessage(Util.prefix("<green>Config rechargée."));
            return true;
        }

        if (SUBS.contains(sub)) {
            if (a.length < 2) { s.sendMessage(Util.prefix("<red>Précise un joueur.")); return true; }
            Target t = resolve(a[1]);
            if (t == null) { s.sendMessage(Util.prefix("<red>Joueur introuvable.")); return true; }
            switch (sub) {
                case "unmute" -> { if (!deny(s, "moderation.unmute")) actions.unmute(s, t.id(), t.name()); }
                case "unban" -> { if (!deny(s, "moderation.unban")) actions.unban(s, t.id(), t.name()); }
                case "unfreeze" -> { if (!deny(s, "moderation.unfreeze")) actions.unfreeze(s, t.id(), t.name()); }
                case "logs" -> {
                    if (deny(s, "moderation.logs")) return true;
                    int n = 10;
                    if (a.length >= 3) {
                        try { n = Math.min(50, Integer.parseInt(a[2])); } catch (NumberFormatException ignored) {}
                    }
                    actions.sendHistory(s, t.id(), t.name(), n);
                }
                default -> {}
            }
            return true;
        }

        if (!(s instanceof Player mod)) {
            s.sendMessage("Le menu est réservé aux joueurs en jeu.");
            return true;
        }
        Target t = resolve(a[0]);
        if (t == null) { mod.sendMessage(Util.prefix("<red>Joueur introuvable.")); return true; }
        menus.openMain(mod, t.id(), t.name());
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String label, String[] a) {
        List<String> out = new ArrayList<>();
        if (a.length == 1) {
            out.addAll(SUBS);
            for (Player p : Bukkit.getOnlinePlayers()) out.add(p.getName());
        } else if (a.length == 2 && SUBS.contains(a[0].toLowerCase(Locale.ROOT)) && !a[0].equalsIgnoreCase("reload")) {
            for (Player p : Bukkit.getOnlinePlayers()) out.add(p.getName());
        }
        String last = a[a.length - 1].toLowerCase(Locale.ROOT);
        out.removeIf(x -> !x.toLowerCase(Locale.ROOT).startsWith(last));
        return out;
    }
}
