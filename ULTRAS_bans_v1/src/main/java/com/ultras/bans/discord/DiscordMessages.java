package com.ultras.bans.discord;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/** Loads discord/messages_&lt;lang&gt;.yml into embed templates (title/description/color/footer per event key). */
public final class DiscordMessages {

    public record Template(String color, String title, String description, String footer) { }

    private final Map<String, Template> templates = new HashMap<>();

    public static DiscordMessages load(File dir, String language) {
        DiscordMessages m = new DiscordMessages();
        File file = new File(dir, "messages_" + language + ".yml");
        if (!file.exists()) file = new File(dir, "messages_en.yml");
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        for (String key : y.getKeys(false)) {
            ConfigurationSection s = y.getConfigurationSection(key);
            if (s == null) continue;
            m.templates.put(key, new Template(
                    s.getString("color", "999999"),
                    s.getString("title", ""),
                    s.getString("description", ""),
                    s.getString("footer", "")
            ));
        }
        return m;
    }

    public Template get(String key) {
        return templates.getOrDefault(key, new Template("999999", key, "", ""));
    }
}
