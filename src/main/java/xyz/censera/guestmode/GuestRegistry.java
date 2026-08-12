package xyz.censera.guestmode;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

final class GuestRegistry {

    private final Set<UUID> guests = new HashSet<>();

    void add(UUID uuid) {
        guests.add(uuid);
    }

    void remove(UUID uuid) {
        guests.remove(uuid);
    }

    Set<UUID> snapshot() {
        return Set.copyOf(guests);
    }
}
