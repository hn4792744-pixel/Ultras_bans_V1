package com.ultras.bans.model;

/** Where something happened: server id (for proxy networks), world name and coordinates. */
public record LocationSnapshot(String server, String world, double x, double y, double z) {

    public static LocationSnapshot unknown(String serverName) {
        return new LocationSnapshot(serverName, "unknown", 0, 0, 0);
    }

    public String formatted() {
        return String.format("%s / %s (%.1f, %.1f, %.1f)", server, world, x, y, z);
    }
}
