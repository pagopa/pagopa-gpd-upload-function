package it.gov.pagopa.gpd.upload.util;

import java.time.Instant;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Case-insensitive, in-memory idempotency tracker with TTL support.
 * NOTE: This works only for single-instance deployments.
 */
public class IdempotencyUploadTracker {
	/*
    private static final Set<String> inProgress = ConcurrentHashMap.newKeySet();

    public static boolean tryLock(String key) {
        return inProgress.add(normalize(key)); // returns false if key was already present
    }

    public static void unlock(String key) {
        inProgress.remove(normalize(key));
    }
    
    private static String normalize(String key) {
        return key == null ? null : key.toLowerCase();
    }*/
	
	// Key -> timestamp of insertion
    private static final Map<String, Instant> inProgress = new ConcurrentHashMap<>();

    // Timeout duration in seconds (e.g., 600s = 10 minutes)
    private static final long TIMEOUT_SECONDS = 600;

    public static boolean tryLock(String key) {
        cleanupExpiredKeys(); // remove old entries
        return inProgress.putIfAbsent(normalize(key), Instant.now()) == null;
    }

    public static void unlock(String key) {
        inProgress.remove(normalize(key));
    }

    private static String normalize(String key) {
        return key == null ? null : key.toLowerCase();
    }

    private static void cleanupExpiredKeys() {
        Instant now = Instant.now();
        Iterator<Map.Entry<String, Instant>> iterator = inProgress.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, Instant> entry = iterator.next();
            if (now.minusSeconds(TIMEOUT_SECONDS).isAfter(entry.getValue())) {
                iterator.remove();
            }
        }
    }
}
