package com.ultras.bans.config;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Loaded from commands.yml. {@link #isDisabled(String)} is checked by every
 * command's onCommand/tabComplete entry point (see command/UltrasCommandBase)
 * before anything else runs, and by {@link com.ultras.bans.listener.CommandConflictGuard}
 * so a disabled command is invisible in tab completion and returns "unknown
 * command" to anyone without ultrasbans.bypass.disabled.
 */
public final class CommandsConfig {

    private final Set<String> disabledCommands;
    private final boolean conflictGuardEnabled;
    private final Set<String> protectedLabels;
    private final Set<String> hiddenConflicting;

    private CommandsConfig(Set<String> disabledCommands, boolean conflictGuardEnabled, Set<String> protectedLabels, Set<String> hiddenConflicting) {
        this.disabledCommands = disabledCommands;
        this.conflictGuardEnabled = conflictGuardEnabled;
        this.protectedLabels = protectedLabels;
        this.hiddenConflicting = hiddenConflicting;
    }

    public boolean isDisabled(String label) {
        return disabledCommands.contains(label.toLowerCase());
    }

    public boolean isConflictGuardEnabled() {
        return conflictGuardEnabled;
    }

    public boolean isProtectedLabel(String label) {
        return protectedLabels.contains(label.toLowerCase());
    }

    /** External/vanilla commands that overlap ULTRAS functionality; hidden and blocked for non-bypass senders. */
    public boolean isHiddenConflicting(String label) {
        return hiddenConflicting.contains(label.toLowerCase());
    }

    public static CommandsConfig load(File file) {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);

        Set<String> disabled = new HashSet<>();
        for (String s : yaml.getStringList("disabled-commands")) {
            disabled.add(s.toLowerCase());
        }

        boolean guard = yaml.getBoolean("conflict-guard-enabled", true);

        Set<String> protectedLabels = new HashSet<>();
        List<String> configured = yaml.getStringList("protected-labels");
        for (String s : configured) {
            protectedLabels.add(s.toLowerCase());
        }

        Set<String> hidden = new HashSet<>();
        for (String s : yaml.getStringList("hidden-conflicting-commands")) hidden.add(s.toLowerCase());

        return new CommandsConfig(disabled, guard, protectedLabels, hidden);
    }
}
