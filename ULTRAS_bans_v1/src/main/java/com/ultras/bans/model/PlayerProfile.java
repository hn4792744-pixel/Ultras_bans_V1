package com.ultras.bans.model;

import java.util.UUID;

/**
 * Persistent per-player record, independent of whether the player is
 * currently online (spec section 53: offline players must be fully
 * manageable). Loaded into an in-memory cache
 * (manager/PlayerProfileCache) on join and flushed to the database on
 * quit/interval, so GUIs can render offline players instantly without a
 * database round trip on every hover.
 */
public final class PlayerProfile {

    private final UUID uuid;
    private String username;
    private String lastIp;
    private PlayerPlatform platform = PlayerPlatform.JAVA;
    private String rank = "default";
    private boolean opStatus;
    private String lastServer = "unknown";
    private String lastWorld = "unknown";
    private double lastX, lastY, lastZ;
    private long firstJoin;
    private long lastJoin;
    private long lastQuit;
    private long playtimeMillis;
    private long blocksBroken;
    private long blocksPlaced;
    private long mobKills;
    private long playerKills;
    private long deaths;
    private double distanceTraveled;

    public PlayerProfile(UUID uuid, String username) {
        this.uuid = uuid;
        this.username = username;
    }

    public UUID uuid() { return uuid; }
    public String username() { return username; }
    public void username(String username) { this.username = username; }
    public String lastIp() { return lastIp; }
    public void lastIp(String lastIp) { this.lastIp = lastIp; }
    public PlayerPlatform platform() { return platform; }
    public void platform(PlayerPlatform platform) { this.platform = platform; }
    public String rank() { return rank; }
    public void rank(String rank) { this.rank = rank; }
    public boolean opStatus() { return opStatus; }
    public void opStatus(boolean opStatus) { this.opStatus = opStatus; }
    public String lastServer() { return lastServer; }
    public void lastServer(String lastServer) { this.lastServer = lastServer; }
    public String lastWorld() { return lastWorld; }
    public void lastWorld(String lastWorld) { this.lastWorld = lastWorld; }
    public double lastX() { return lastX; }
    public double lastY() { return lastY; }
    public double lastZ() { return lastZ; }
    public void lastLocation(double x, double y, double z) { this.lastX = x; this.lastY = y; this.lastZ = z; }
    public long firstJoin() { return firstJoin; }
    public void firstJoin(long firstJoin) { this.firstJoin = firstJoin; }
    public long lastJoin() { return lastJoin; }
    public void lastJoin(long lastJoin) { this.lastJoin = lastJoin; }
    public long lastQuit() { return lastQuit; }
    public void lastQuit(long lastQuit) { this.lastQuit = lastQuit; }
    public long playtimeMillis() { return playtimeMillis; }
    public void addPlaytime(long millis) { this.playtimeMillis += millis; }
    public long blocksBroken() { return blocksBroken; }
    public void incrementBlocksBroken() { this.blocksBroken++; }
    public void blocksBroken(long value) { this.blocksBroken = value; }
    public long blocksPlaced() { return blocksPlaced; }
    public void incrementBlocksPlaced() { this.blocksPlaced++; }
    public void blocksPlaced(long value) { this.blocksPlaced = value; }
    public long mobKills() { return mobKills; }
    public void incrementMobKills() { this.mobKills++; }
    public void mobKills(long value) { this.mobKills = value; }
    public long playerKills() { return playerKills; }
    public void incrementPlayerKills() { this.playerKills++; }
    public void playerKills(long value) { this.playerKills = value; }
    public long deaths() { return deaths; }
    public void incrementDeaths() { this.deaths++; }
    public void deaths(long value) { this.deaths = value; }
    public double distanceTraveled() { return distanceTraveled; }
    public void addDistance(double d) { this.distanceTraveled += d; }
    public void distanceTraveled(double value) { this.distanceTraveled = value; }
    public void playtimeMillis(long value) { this.playtimeMillis = value; }
}
