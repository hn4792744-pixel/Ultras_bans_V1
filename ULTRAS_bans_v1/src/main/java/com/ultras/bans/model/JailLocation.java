package com.ultras.bans.model;

/** A named jail spawn saved with /setjail. */
public record JailLocation(String name, String server, String world, double x, double y, double z, float yaw, float pitch) { }
