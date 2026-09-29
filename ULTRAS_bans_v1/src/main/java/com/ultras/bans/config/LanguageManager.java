package com.ultras.bans.config;

import org.bukkit.ChatColor;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Loads every YAML file under {@code lang/<language>/} into a single flat map
 * keyed as {@code "<filename>.<yaml-path>"} (e.g. file punishments.yml, key
 * "already-permanent" -> lookup key "punishments.already-permanent"). English
 * and Arabic must expose identical keys (spec section 30) - {@link #validateParity}
 * logs a warning listing any keys missing from one language so a partial
 * translation never silently falls back to a raw key string in front of players.
 */
public final class LanguageManager {

    private final File langFolder;
    private final Logger logger;
    private String activeLanguage;
    private Map<String, String> messages = new HashMap<>();

    public LanguageManager(File dataFolder, Logger logger) {
        this.langFolder = new File(dataFolder, "lang");
        this.logger = logger;
    }

    /** Copies bundled default language files from the jar to disk if missing, without overwriting user edits. */
    public void ensureDefaults(String[] fileNames, ClassLoader classLoader) {
        for (String code : new String[]{"en", "ar"}) {
            File dir = new File(langFolder, code);
            if (!dir.exists() && !dir.mkdirs()) {
                logger.warning("Could not create lang/" + code + " directory.");
            }
            for (String fileName : fileNames) {
                File target = new File(dir, fileName + ".yml");
                if (!target.exists()) {
                    String resourcePath = "lang/" + code + "/" + fileName + ".yml";
                    try (var in = classLoader.getResourceAsStream(resourcePath)) {
                        if (in != null) {
                            Files.copy(in, target.toPath());
                        }
                    } catch (IOException ex) {
                        logger.log(Level.WARNING, "Failed to extract default lang file " + resourcePath, ex);
                    }
                }
            }
        }
    }

    public void load(String language, String[] fileNames) {
        Map<String, String> loaded = new HashMap<>();
        File dir = new File(langFolder, language);
        if (!dir.exists()) {
            logger.warning("Language folder '" + language + "' not found, falling back to 'en'.");
            language = "en";
            dir = new File(langFolder, "en");
        }

        for (String fileName : fileNames) {
            File file = new File(dir, fileName + ".yml");
            if (!file.exists()) continue;
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
            flatten(fileName, yaml.getValues(true), loaded);
        }

        this.activeLanguage = language;
        this.messages = loaded;
    }

    @SuppressWarnings("unchecked")
    private void flatten(String prefix, Map<String, Object> values, Map<String, String> out) {
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            Object value = entry.getValue();
            String key = prefix + "." + entry.getKey();
            if (value instanceof Map) {
                flatten(key, (Map<String, Object>) value, out);
            } else if (value != null) {
                out.put(key, String.valueOf(value));
            }
        }
    }

    /** Raw message with color codes translated, before placeholder substitution. */
    public String raw(String key) {
        String value = messages.get(key);
        if (value == null) {
            logger.warning("Missing language key: " + key + " (language=" + activeLanguage + ")");
            return ChatColor.RED + "Missing message: " + key;
        }
        return ChatColor.translateAlternateColorCodes('&', value);
    }

    /** Message with {0}, {1}... placeholders substituted positionally. */
    public String get(String key, Object... placeholders) {
        String template = raw(key);
        for (int i = 0; i < placeholders.length; i++) {
            template = template.replace("{" + i + "}", String.valueOf(placeholders[i]));
        }
        return template;
    }

    /** Message with named {reason}, {duration}, {id} etc. placeholders substituted. */
    public String getNamed(String key, Map<String, String> placeholders) {
        String template = raw(key);
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            template = template.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return template;
    }

    public String activeLanguage() {
        return activeLanguage;
    }

    /** Logs any key present in one language's file set but missing in the other. Called once after loading both. */
    public void validateParity(String[] fileNames) {
        Map<String, String> en = new HashMap<>();
        Map<String, String> ar = new HashMap<>();
        for (String fileName : fileNames) {
            File enFile = new File(new File(langFolder, "en"), fileName + ".yml");
            File arFile = new File(new File(langFolder, "ar"), fileName + ".yml");
            if (enFile.exists()) flatten(fileName, YamlConfiguration.loadConfiguration(enFile).getValues(true), en);
            if (arFile.exists()) flatten(fileName, YamlConfiguration.loadConfiguration(arFile).getValues(true), ar);
        }
        for (String key : en.keySet()) {
            if (!ar.containsKey(key)) logger.warning("[lang parity] Key '" + key + "' exists in en but not ar.");
        }
        for (String key : ar.keySet()) {
            if (!en.containsKey(key)) logger.warning("[lang parity] Key '" + key + "' exists in ar but not en.");
        }
    }
}
