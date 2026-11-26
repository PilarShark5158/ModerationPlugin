package fr.moderation;

import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.UUID;

/** Applique les sanctions : ban à la connexion, mute au chat/commandes, blocage du joueur freeze. */
final class Guard implements Listener {
    private final ModerationPlugin plugin;
    private final Store store;
    private final Actions actions;

    Guard(ModerationPlugin plugin, Store store, Actions actions) {
        this.plugin = plugin;
        this.store = store;
        this.actions = actions;
    }

    private boolean frozen(Player p) { return store.isFrozen(p.getUniqueId()); }

    // ---------------- BAN ----------------
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPreLogin(AsyncPlayerPreLoginEvent e) {
        Long remaining = store.banRemaining(e.getUniqueId());
        if (remaining == null) return;
        e.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED,
                Util.banScreen(store.banReason(e.getUniqueId()), remaining, actions.discord()));
    }

    // ---------------- MUTE ----------------
    private void muteMessage(Player p, long left) {
        p.sendMessage(Util.prefix("<red>Tu es mute encore <white>" + Util.fmt(left)
                + "<red> — raison : <white>" + Util.e(store.muteReason(p.getUniqueId()))));
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent e) {
        Player p = e.getPlayer();
        long left = store.muteRemaining(p.getUniqueId());
        if (left > 0) {
            e.setCancelled(true);
            muteMessage(p, left);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onCommand(PlayerCommandPreprocessEvent e) {
        Player p = e.getPlayer();
        String label = e.getMessage().substring(1).split(" ")[0].toLowerCase();
        if (label.contains(":")) label = label.substring(label.indexOf(':') + 1);

        if (frozen(p)) {
            e.setCancelled(true);
            p.sendMessage(Util.prefix("<red>Tu ne peux pas utiliser de commande en étant freeze."));
            return;
        }
        long left = store.muteRemaining(p.getUniqueId());
        if (left > 0 && plugin.getConfig().getStringList("mute.blocked-commands").contains(label)) {
            e.setCancelled(true);
            muteMessage(p, left);
        }
    }

    // ---------------- FREEZE ----------------
    @EventHandler
    public void onMove(PlayerMoveEvent e) {
        if (!frozen(e.getPlayer())) return;
        Location from = e.getFrom(), to = e.getTo();
        if (to == null) return;
        if (from.getX() != to.getX() || from.getY() != to.getY() || from.getZ() != to.getZ()) {
            Location l = from.clone();
            l.setYaw(to.getYaw());
            l.setPitch(to.getPitch());
            e.setTo(l);
        }
    }

    @EventHandler public void onBreak(BlockBreakEvent e) { if (frozen(e.getPlayer())) e.setCancelled(true); }
    @EventHandler public void onPlace(BlockPlaceEvent e) { if (frozen(e.getPlayer())) e.setCancelled(true); }
    @EventHandler public void onInteract(PlayerInteractEvent e) { if (frozen(e.getPlayer())) e.setCancelled(true); }
    @EventHandler public void onDrop(PlayerDropItemEvent e) { if (frozen(e.getPlayer())) e.setCancelled(true); }

    @EventHandler
    public void onPickup(EntityPickupItemEvent e) {
        if (e.getEntity() instanceof Player p && frozen(p)) e.setCancelled(true);
    }

    @EventHandler
    public void onDamage(EntityDamageEvent e) {
        if (e.getEntity() instanceof Player p && frozen(p)) e.setCancelled(true);
    }

    @EventHandler
    public void onDamageBy(EntityDamageByEntityEvent e) {
        if (e.getDamager() instanceof Player p && frozen(p)) e.setCancelled(true);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        if (frozen(p)) actions.freezeDisconnect(p.getUniqueId(), p.getName());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        UUID id = p.getUniqueId();
        if (store.isFrozen(id)) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (p.isOnline() && store.isFrozen(id)) plugin.menus().openFrozen(p);
            }, 20L);
        }
    }
}
