package fr.moderation;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.UUID;

final class Menus implements Listener {

    enum Type { MAIN, REASON, BAN_CHOICE, DURATION, FREEZE, FROZEN }
    enum Act { MUTE, WARN, BAN }

    static final class Holder implements InventoryHolder {
        final Type type;
        final UUID target;
        final String name;
        final Act act;
        final long dur;
        Inventory inv;

        Holder(Type type, UUID target, String name, Act act, long dur) {
            this.type = type; this.target = target; this.name = name; this.act = act; this.dur = dur;
        }

        @Override public @NotNull Inventory getInventory() { return inv; }
    }

    private final ModerationPlugin plugin;
    private final Store store;
    private final Actions actions;

    Menus(ModerationPlugin plugin, Store store, Actions actions) {
        this.plugin = plugin;
        this.store = store;
        this.actions = actions;
    }

    // ------------------------------------------------------------ helpers
    private Holder make(Type t, UUID id, String name, Act a, long dur, String title) {
        Holder h = new Holder(t, id, name, a, dur);
        h.inv = Bukkit.createInventory(h, 27, Util.c(title));
        ItemStack filler = Util.item(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 27; i++) h.inv.setItem(i, filler);
        return h;
    }

    private static String perm(Player p, String node) {
        return p.hasPermission(node) ? "<green>▶ Clique pour utiliser" : "<red>✖ Permission manquante";
    }

    private boolean need(Player p, String node) {
        if (p.hasPermission(node)) return true;
        p.sendMessage(Util.prefix("<red>Tu n'as pas la permission <white>" + node + "<red>."));
        return false;
    }

    // ------------------------------------------------------------ MAIN
    void openMain(Player mod, UUID id, String name) {
        Holder h = make(Type.MAIN, id, name, null, 0, "<dark_red>Modération <dark_gray>» <black>" + Util.e(name));
        long muteLeft = store.muteRemaining(id);
        int mutes = store.muteCount(id);

        if (muteLeft > 0) {
            h.inv.setItem(10, Util.item(Material.LIME_DYE, "<green><bold>Démute",
                    "Temps restant : <white>" + Util.fmt(muteLeft), perm(mod, "moderation.unmute")));
        } else {
            h.inv.setItem(10, Util.item(Material.NOTE_BLOCK, "<gold><bold>Mute",
                    "Prochain mute : <white>" + Util.fmt(actions.muteDuration(mutes)),
                    "Mutes déjà reçus : <white>" + mutes, perm(mod, "moderation.mute")));
        }

        h.inv.setItem(12, Util.item(Material.OAK_SIGN, "<yellow><bold>Avertir",
                "Avertissements : <white>" + store.warnCount(id), perm(mod, "moderation.warn")));

        if (store.isFrozen(id)) {
            h.inv.setItem(14, Util.item(Material.BLUE_ICE, "<aqua><bold>Freeze <gray>(actif)",
                    perm(mod, "moderation.unfreeze")));
        } else {
            h.inv.setItem(14, Util.item(Material.PACKED_ICE, "<aqua><bold>Freeze", perm(mod, "moderation.freeze")));
        }

        if (store.banRemaining(id) != null) {
            h.inv.setItem(16, Util.item(Material.LIME_DYE, "<green><bold>Débannir", perm(mod, "moderation.unban")));
        } else {
            h.inv.setItem(16, Util.item(Material.BARRIER, "<red><bold>Bannir",
                    "Temporaire ou définitif", perm(mod, "moderation.ban.temp")));
        }

        h.inv.setItem(22, Util.item(Material.BOOK, "<white><bold>Logs", "Voir les 10 dernières actions",
                perm(mod, "moderation.logs")));
        mod.openInventory(h.inv);
    }

    private void clickMain(Player mod, Holder h, int slot) {
        switch (slot) {
            case 10 -> {
                if (store.muteRemaining(h.target) > 0) {
                    if (!need(mod, "moderation.unmute")) return;
                    mod.closeInventory();
                    actions.unmute(mod, h.target, h.name);
                } else {
                    if (!need(mod, "moderation.mute")) return;
                    openReasons(mod, h, Act.MUTE, 0);
                }
            }
            case 12 -> {
                if (!need(mod, "moderation.warn")) return;
                openReasons(mod, h, Act.WARN, 0);
            }
            case 14 -> {
                boolean frozen = store.isFrozen(h.target);
                if (!need(mod, frozen ? "moderation.unfreeze" : "moderation.freeze")) return;
                if (!frozen && Bukkit.getPlayer(h.target) == null) {
                    mod.sendMessage(Util.prefix("<red>Le joueur doit être en ligne pour être freeze."));
                    return;
                }
                openFreeze(mod, h);
            }
            case 16 -> {
                if (store.banRemaining(h.target) != null) {
                    if (!need(mod, "moderation.unban")) return;
                    mod.closeInventory();
                    actions.unban(mod, h.target, h.name);
                } else {
                    if (!mod.hasPermission("moderation.ban.temp") && !mod.hasPermission("moderation.ban.perm")) {
                        need(mod, "moderation.ban.temp");
                        return;
                    }
                    openBanChoice(mod, h);
                }
            }
            case 22 -> {
                if (!need(mod, "moderation.logs")) return;
                mod.closeInventory();
                actions.sendHistory(mod, h.target, h.name, 10);
            }
            default -> {}
        }
    }

    // ------------------------------------------------------------ REASONS
    private void openReasons(Player mod, Holder from, Act act, long dur) {
        Holder h = make(Type.REASON, from.target, from.name, act, dur,
                "<dark_red>Raison <dark_gray>» <black>" + Util.e(from.name));
        List<String> reasons = plugin.getConfig().getStringList("reasons");
        for (int i = 0; i < Math.min(18, reasons.size()); i++) {
            h.inv.setItem(i, Util.item(Material.PAPER, "<white>" + Util.e(reasons.get(i)), "Clique pour confirmer"));
        }
        h.inv.setItem(22, Util.item(Material.ARROW, "<red>Retour"));
        mod.openInventory(h.inv);
    }

    private void clickReason(Player mod, Holder h, int slot) {
        if (slot == 22) {
            if (h.act == Act.BAN) openBanChoice(mod, h); else openMain(mod, h.target, h.name);
            return;
        }
        List<String> reasons = plugin.getConfig().getStringList("reasons");
        if (slot < 0 || slot >= 18 || slot >= reasons.size()) return;
        String reason = reasons.get(slot);
        switch (h.act) {
            case MUTE -> {
                if (!need(mod, "moderation.mute")) return;
                mod.closeInventory();
                actions.mute(mod, h.target, h.name, reason);
            }
            case WARN -> {
                if (!need(mod, "moderation.warn")) return;
                mod.closeInventory();
                actions.warn(mod, h.target, h.name, reason);
            }
            case BAN -> {
                if (!need(mod, h.dur < 0 ? "moderation.ban.perm" : "moderation.ban.temp")) return;
                mod.closeInventory();
                actions.ban(mod, h.target, h.name, h.dur, reason);
            }
        }
    }

    // ------------------------------------------------------------ BAN
    private void openBanChoice(Player mod, Holder from) {
        Holder h = make(Type.BAN_CHOICE, from.target, from.name, Act.BAN, 0,
                "<dark_red>Bannir <dark_gray>» <black>" + Util.e(from.name));
        h.inv.setItem(11, Util.item(Material.CLOCK, "<gold><bold>Ban temporaire",
                "Choisir une durée", perm(mod, "moderation.ban.temp")));
        h.inv.setItem(15, Util.item(Material.BEDROCK, "<dark_red><bold>Ban définitif",
                "Jusqu'à un unban", perm(mod, "moderation.ban.perm")));
        h.inv.setItem(22, Util.item(Material.ARROW, "<red>Retour"));
        mod.openInventory(h.inv);
    }

    private void clickBanChoice(Player mod, Holder h, int slot) {
        switch (slot) {
            case 11 -> { if (need(mod, "moderation.ban.temp")) openDurations(mod, h); }
            case 15 -> { if (need(mod, "moderation.ban.perm")) openReasons(mod, h, Act.BAN, -1); }
            case 22 -> openMain(mod, h.target, h.name);
            default -> {}
        }
    }

    private void openDurations(Player mod, Holder from) {
        Holder h = make(Type.DURATION, from.target, from.name, Act.BAN, 0,
                "<dark_red>Durée <dark_gray>» <black>" + Util.e(from.name));
        List<String> d = plugin.getConfig().getStringList("ban.durations");
        for (int i = 0; i < Math.min(7, d.size()); i++) {
            long ms = Util.parse(d.get(i));
            if (ms <= 0) continue;
            h.inv.setItem(10 + i, Util.item(Material.CLOCK, "<gold>" + Util.fmt(ms), "Clique pour choisir"));
        }
        h.inv.setItem(22, Util.item(Material.ARROW, "<red>Retour"));
        mod.openInventory(h.inv);
    }

    private void clickDuration(Player mod, Holder h, int slot) {
        if (slot == 22) { openBanChoice(mod, h); return; }
        int i = slot - 10;
        List<String> d = plugin.getConfig().getStringList("ban.durations");
        if (i < 0 || i >= 7 || i >= d.size()) return;
        long ms = Util.parse(d.get(i));
        if (ms <= 0) return;
        if (!need(mod, "moderation.ban.temp")) return;
        openReasons(mod, h, Act.BAN, ms);
    }

    // ------------------------------------------------------------ FREEZE
    private void openFreeze(Player mod, Holder from) {
        Holder h = make(Type.FREEZE, from.target, from.name, null, 0,
                "<dark_aqua>Freeze <dark_gray>» <black>" + Util.e(from.name));
        if (store.isFrozen(from.target)) {
            h.inv.setItem(11, Util.item(Material.LIME_DYE, "<green><bold>Unfreeze", perm(mod, "moderation.unfreeze")));
        } else {
            h.inv.setItem(11, Util.item(Material.BLUE_ICE, "<aqua><bold>Freeze le joueur",
                    "S'il se déconnecte : <red>ban définitif", perm(mod, "moderation.freeze")));
        }
        h.inv.setItem(15, Util.item(Material.ENDER_PEARL, "<light_purple><bold>Se téléporter au joueur"));
        h.inv.setItem(22, Util.item(Material.ARROW, "<red>Retour"));
        mod.openInventory(h.inv);
    }

    private void clickFreeze(Player mod, Holder h, int slot) {
        Player t = Bukkit.getPlayer(h.target);
        switch (slot) {
            case 11 -> {
                if (store.isFrozen(h.target)) {
                    if (!need(mod, "moderation.unfreeze")) return;
                    mod.closeInventory();
                    actions.unfreeze(mod, h.target, h.name);
                } else {
                    if (!need(mod, "moderation.freeze")) return;
                    if (t == null) { mod.sendMessage(Util.prefix("<red>Joueur hors ligne.")); return; }
                    mod.closeInventory();
                    actions.freeze(mod, t);
                }
            }
            case 15 -> {
                if (t == null) { mod.sendMessage(Util.prefix("<red>Joueur hors ligne.")); return; }
                mod.closeInventory();
                mod.teleport(t.getLocation());
            }
            case 22 -> openMain(mod, h.target, h.name);
            default -> {}
        }
    }

    /** Interface affichée au joueur freeze (impossible à fermer tant qu'il est freeze). */
    void openFrozen(Player p) {
        Holder h = make(Type.FROZEN, p.getUniqueId(), p.getName(), null, 0, "<aqua><bold>❄ Tu es freeze");
        h.inv.setItem(13, Util.item(Material.BLUE_ICE, "<aqua><bold>Tu es freeze",
                "Un modérateur te contrôle.",
                "<red>Ne te déconnecte pas : ban définitif.",
                "Unban : <aqua>" + Util.e(actions.discord())));
        p.openInventory(h.inv);
    }

    // ------------------------------------------------------------ events
    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof Holder h)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player mod)) return;
        if (e.getClickedInventory() == null || e.getClickedInventory() != e.getView().getTopInventory()) return;
        int slot = e.getSlot();
        switch (h.type) {
            case MAIN -> clickMain(mod, h, slot);
            case REASON -> clickReason(mod, h, slot);
            case BAN_CHOICE -> clickBanChoice(mod, h, slot);
            case DURATION -> clickDuration(mod, h, slot);
            case FREEZE -> clickFreeze(mod, h, slot);
            case FROZEN -> {}
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof Holder) e.setCancelled(true);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (e.getInventory().getHolder() instanceof Holder h && h.type == Type.FROZEN
                && e.getPlayer() instanceof Player p && store.isFrozen(p.getUniqueId())) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (p.isOnline() && store.isFrozen(p.getUniqueId())) openFrozen(p);
            });
        }
    }
}
