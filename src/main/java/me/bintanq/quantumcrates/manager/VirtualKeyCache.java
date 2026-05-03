package me.bintanq.quantumcrates.manager;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class VirtualKeyCache {

    private final ConcurrentHashMap<String, AtomicInteger> cache = new ConcurrentHashMap<>();

    private String key(UUID uuid, String keyId) {
        return uuid + ":" + keyId;
    }

    public void set(UUID uuid, String keyId, int amount) {
        cache.put(key(uuid, keyId), new AtomicInteger(amount));
    }

    public int get(UUID uuid, String keyId) {
        AtomicInteger val = cache.get(key(uuid, keyId));
        return val != null ? val.get() : -1;
    }

    public int add(UUID uuid, String keyId, int amount) {
        return cache.computeIfAbsent(key(uuid, keyId), k -> new AtomicInteger(0))
                .addAndGet(amount);
    }

    public boolean subtract(UUID uuid, String keyId, int amount) {
        AtomicInteger val = cache.get(key(uuid, keyId));
        if (val == null) return false;
        int current;
        do {
            current = val.get();
            if (current < amount) return false;
        } while (!val.compareAndSet(current, current - amount));
        return true;
    }

    public boolean isCached(UUID uuid, String keyId) {
        return cache.containsKey(key(uuid, keyId));
    }

    public void invalidate(UUID uuid, String keyId) {
        cache.remove(key(uuid, keyId));
    }

    public void invalidateAll(UUID uuid) {
        cache.keySet().removeIf(k -> k.startsWith(uuid + ":"));
    }
}