package com.ultras.bans.model;

import java.util.UUID;

/** Who performed an administrative action. uuid is null for CONSOLE/SYSTEM. */
public record OperatorInfo(UUID uuid, String name) {

    public static final OperatorInfo CONSOLE = new OperatorInfo(null, "CONSOLE");

    public static OperatorInfo system() {
        return CONSOLE;
    }

    public boolean isConsole() {
        return uuid == null;
    }
}
