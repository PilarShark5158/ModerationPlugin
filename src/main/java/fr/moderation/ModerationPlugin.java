package fr.moderation;

import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class ModerationPlugin extends JavaPlugin {

    private Store store;
    private Actions actions;
    private Menus menus;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        store = new Store(this);
        actions = new Actions(this, store);
        menus = new Menus(this, store, actions);

        getServer().getPluginManager().registerEvents(menus, this);
        getServer().getPluginManager().registerEvents(new Guard(this, store, actions), this);

        ModCommand cmd = new ModCommand(this, store, actions, menus);
        PluginCommand pc = getCommand("moderation");
        if (pc != null) {
            pc.setExecutor(cmd);
            pc.setTabCompleter(cmd);
        }

        long ticks = Math.max(5, getConfig().getLong("freeze.reminder-seconds", 30)) * 20L;
        getServer().getScheduler().runTaskTimer(this, () -> {
            for (Player p : getServer().getOnlinePlayers()) {
                if (store.isFrozen(p.getUniqueId())) actions.sendFreezeReminder(p);
            }
        }, ticks, ticks);
    }

    Menus menus() { return menus; }
}
