package com.ultras.bans.command;

import java.util.UUID;

/** A punishment target resolved from a name, UUID or raw IP. uuid/name may be null for a raw-IP target. */
public record ResolvedTarget(UUID uuid, String name, String ip) {
    public boolean hasPlayer() { return uuid != null; }
}
