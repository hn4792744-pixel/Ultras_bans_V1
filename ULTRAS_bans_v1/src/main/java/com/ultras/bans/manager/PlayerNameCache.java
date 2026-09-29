package com.ultras.bans.manager;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentSkipListMap;

/** Case-insensitive sorted set of every known player name, for offline-aware tab completion without DB hits. */
public final class PlayerNameCache {

    private final ConcurrentSkipListMap<String, String> names = new ConcurrentSkipListMap<>();

    public void add(String name) {
        if (name != null && !name.isBlank()) names.put(name.toLowerCase(Locale.ROOT), name);
    }

    public List<String> startingWith(String prefix, int limit) {
        String p = prefix == null ? "" : prefix.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (var e : names.tailMap(p, true).entrySet()) {
            if (!e.getKey().startsWith(p) || out.size() >= limit) break;
            out.add(e.getValue());
        }
        return out;
    }

    public int size() { return names.size(); }
}
