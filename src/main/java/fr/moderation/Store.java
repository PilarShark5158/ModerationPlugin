package fr.moderation;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Données (mutes, bans, avertissements, freeze) + logs précis dans plugins/ModerationPlugin/logs.log */
final class Store {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ModerationPlugin plugin;
    private final File dataFile;
    private final Path logPath;
    private final YamlConfiguration data;
    private final Set<UUID> frozen = ConcurrentHashMap.newKeySet();

    Store(ModerationPlugin plugin) {
        this.plugin = plugin;
        plugin.getDataFolder().mkdirs();
        this.dataFile = new File(plugin.getDataFolder(), "data.yml");
        this.logPath = new File(plugin.getDataFolder(), "logs.log").toPath();
        this.data = YamlConfiguration.loadConfiguration(dataFile);
        for (String s : data.getStringList("frozen")) {
            try { frozen.add(UUID.fromString(s)); } catch (IllegalArgumentException ignored) {}
        }
    }

    private synchronized void save() {
        data.set("frozen", frozen.stream().map(UUID::toString).toList());
        try { data.save(dataFile); }
        catch (IOException ex) { plugin.getLogger().severe("Impossible de sauvegarder data.yml : " + ex.getMessage()); }
    }

    // ---------- Mute ----------
    synchronized long muteRemaining(UUID id) {
        long until = data.getLong("mutes." + id + ".until", 0);
        if (until <= 0) return 0;
        long r = until - System.currentTimeMillis();
        if (r <= 0) { data.set("mutes." + id, null); save(); return 0; }
        return r;
    }
    synchronized String muteReason(UUID id) { return data.getString("mutes." + id + ".reason", "-"); }
    synchronized void setMute(UUID id, long until, String reason, String by) {
        data.set("mutes." + id + ".until", until);
        data.set("mutes." + id + ".reason", reason);
        data.set("mutes." + id + ".by", by);
        save();
    }
    synchronized void clearMute(UUID id) { data.set("mutes." + id, null); save(); }
    /** Compteur de mutes : ne se reset jamais. */
    synchronized int muteCount(UUID id) { return data.getInt("mutecount." + id, 0); }
    synchronized void incMuteCount(UUID id) { data.set("mutecount." + id, muteCount(id) + 1); save(); }

    // ---------- Warn ----------
    synchronized int warnCount(UUID id) { return data.getInt("warncount." + id, 0); }
    synchronized int incWarn(UUID id) {
        int n = warnCount(id) + 1;
        data.set("warncount." + id, n);
        save();
        return n;
    }

    // ---------- Ban ----------
    /** null = pas banni ; -1 = définitif ; sinon temps restant en ms. */
    synchronized Long banRemaining(UUID id) {
        if (!data.contains("bans." + id)) return null;
        long until = data.getLong("bans." + id + ".until", -1);
        if (until < 0) return -1L;
        long r = until - System.currentTimeMillis();
        if (r <= 0) { data.set("bans." + id, null); save(); return null; }
        return r;
    }
    synchronized String banReason(UUID id) { return data.getString("bans." + id + ".reason", "-"); }
    synchronized void setBan(UUID id, String name, long until, String reason, String by) {
        data.set("bans." + id + ".name", name);
        data.set("bans." + id + ".until", until);
        data.set("bans." + id + ".reason", reason);
        data.set("bans." + id + ".by", by);
        save();
    }
    synchronized void clearBan(UUID id) { data.set("bans." + id, null); save(); }
    synchronized UUID findBannedByName(String name) {
        ConfigurationSection sec = data.getConfigurationSection("bans");
        if (sec == null) return null;
        for (String k : sec.getKeys(false)) {
            if (name.equalsIgnoreCase(data.getString("bans." + k + ".name", ""))) {
                try { return UUID.fromString(k); } catch (IllegalArgumentException ignored) {}
            }
        }
        return null;
    }

    // ---------- Freeze ----------
    boolean isFrozen(UUID id) { return frozen.contains(id); }
    synchronized void freeze(UUID id) { frozen.add(id); save(); }
    synchronized void unfreeze(UUID id) { frozen.remove(id); save(); }

    // ---------- Logs ----------
    private static String clean(String s) {
        return s == null ? "-" : s.replace('|', '/').replace('\n', ' ').replace('\r', ' ');
    }

    synchronized void log(String action, String modName, UUID modId, UUID targetId, String targetName,
                          String reason, long durationMs, String details) {
        String line = LocalDateTime.now().format(TIME)
                + " | " + action
                + " | mod=" + clean(modName)
                + " | mod_id=" + (modId == null ? "-" : modId)
                + " | cible=" + clean(targetName)
                + " | cible_id=" + targetId
                + " | durée=" + (durationMs == 0 ? "-" : Util.fmt(durationMs))
                + " | raison=" + clean(reason)
                + " | détails=" + clean(details);
        try {
            Files.writeString(logPath, line + System.lineSeparator(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException ex) {
            plugin.getLogger().severe("Impossible d'écrire dans logs.log : " + ex.getMessage());
        }
        plugin.getLogger().info("[LOG] " + line);
    }

    synchronized List<String> history(UUID targetId, int n) {
        List<String> out = new ArrayList<>();
        try {
            if (!Files.exists(logPath)) return out;
            for (String l : Files.readAllLines(logPath, StandardCharsets.UTF_8)) {
                if (l.contains("cible_id=" + targetId)) out.add(l);
            }
        } catch (IOException ex) {
            plugin.getLogger().severe("Lecture logs.log impossible : " + ex.getMessage());
        }
        int from = Math.max(0, out.size() - Math.max(1, n));
        return new ArrayList<>(out.subList(from, out.size()));
    }
}
