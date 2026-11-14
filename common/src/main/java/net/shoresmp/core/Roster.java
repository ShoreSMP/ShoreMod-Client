package net.shoresmp.core;

import java.util.*;

/** Atomically replaces complete snapshots, including snapshots delivered out of order. */
public final class Roster {
    private long generation = Long.MIN_VALUE;
    private long appliedGeneration = Long.MIN_VALUE;
    private int expected;
    private final Map<Integer, List<Wire.User>> pending = new HashMap<>();
    private Map<UUID, Wire.User> users = Map.of();
    public boolean accept(Wire.Message message) {
        if (message.op() != Wire.ROSTER || message.generation() < generation) return false;
        if(expected>0&&message.generation()==appliedGeneration)return true; // A heartbeat reuses the immutable snapshot.
        if (message.generation() > generation) {
            generation = message.generation(); expected = message.pages(); pending.clear();
        }
        if (message.pages() != expected) return false;
        pending.put(message.page(), message.users());
        if (pending.size() != expected) return false;
        var next = new HashMap<UUID, Wire.User>();
        pending.values().forEach(page -> page.forEach(u -> next.put(u.id(), u)));
        users = Map.copyOf(next);
        appliedGeneration=message.generation();
        return true;
    }
    public Map<UUID, Wire.User> users() { return users; }
    public void clear() { generation = appliedGeneration = Long.MIN_VALUE; expected = 0; pending.clear(); users = Map.of(); }
}
