package xyz.censera.guestmode;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/*
 * Tracks UUIDs of players currently in Guest Mode.
 *
 * Backed by a ConcurrentHashMap key set so that the upgrade task (main thread)
 * and any async event handlers can both read safely. All mutations go through
 * add() and remove(), callers that need to iterate and mutate simultaneously
 * must snapshot via Set.copyOf(getAll()) before the loop.
 */
public final class GuestRegistry {

    private final Set<UUID> guests = ConcurrentHashMap.newKeySet();

    public void add(UUID uuid) {
        guests.add(uuid);
    }

    public void remove(UUID uuid) {
        guests.remove(uuid);
    }

    public boolean isGuest(UUID uuid) {
        return guests.contains(uuid);
    }

    /* Returns a live, unmodifiable view. Safe to read (do not mutate while iterating). */
    public Set<UUID> snapshot() {
        return Set.copyOf(guests);
    }

    public int size() {
        return guests.size();
    }
}
