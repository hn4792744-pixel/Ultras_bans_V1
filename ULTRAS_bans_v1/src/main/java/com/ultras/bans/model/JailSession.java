package com.ultras.bans.model;

import java.util.UUID;

/**
 * Everything needed to put a player back exactly as they were: full inventory, armor, offhand, previous
 * location and the per-player jail restriction flags. One session per jailed player, persisted so it survives
 * restarts, deaths and relogs.
 */
public final class JailSession {

    public final UUID playerUuid;
    public final long punishmentId;
    public final String jailName;
    public final String prevServer, prevWorld;
    public final double prevX, prevY, prevZ;
    public final float prevYaw, prevPitch;
    public final String inventoryB64, armorB64, offhandB64;
    public final long createdAt;

    public volatile boolean chatBlocked, commandsBlocked, movementRestricted, interactionRestricted, itemsRestricted;

    public JailSession(UUID playerUuid, long punishmentId, String jailName, String prevServer, String prevWorld,
                       double prevX, double prevY, double prevZ, float prevYaw, float prevPitch,
                       String inventoryB64, String armorB64, String offhandB64, long createdAt) {
        this.playerUuid = playerUuid;
        this.punishmentId = punishmentId;
        this.jailName = jailName;
        this.prevServer = prevServer;
        this.prevWorld = prevWorld;
        this.prevX = prevX; this.prevY = prevY; this.prevZ = prevZ;
        this.prevYaw = prevYaw; this.prevPitch = prevPitch;
        this.inventoryB64 = inventoryB64;
        this.armorB64 = armorB64;
        this.offhandB64 = offhandB64;
        this.createdAt = createdAt;
    }

    public String flagsCsv() {
        return (chatBlocked ? 1 : 0) + "," + (commandsBlocked ? 1 : 0) + "," + (movementRestricted ? 1 : 0)
                + "," + (interactionRestricted ? 1 : 0) + "," + (itemsRestricted ? 1 : 0);
    }

    public void applyFlagsCsv(String csv) {
        if (csv == null) return;
        String[] p = csv.split(",");
        if (p.length < 5) return;
        chatBlocked = p[0].equals("1");
        commandsBlocked = p[1].equals("1");
        movementRestricted = p[2].equals("1");
        interactionRestricted = p[3].equals("1");
        itemsRestricted = p[4].equals("1");
    }
}
