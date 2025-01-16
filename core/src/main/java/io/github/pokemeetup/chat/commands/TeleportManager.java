package io.github.pokemeetup.chat.commands;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class TeleportManager {
    private final Map<String, TeleportRequest> pendingRequests = new ConcurrentHashMap<>();
    private static final long REQUEST_TIMEOUT = 60000; // 60 seconds

    public static class TeleportRequest {
        public final String from;
        public final String to;
        public final long timestamp;

        public TeleportRequest(String from, String to) {
            this.from = from;
            this.to = to;
            this.timestamp = System.currentTimeMillis();
        }

        public boolean isExpired() {
            return System.currentTimeMillis() - timestamp > REQUEST_TIMEOUT;
        }
    }

    public void addRequest(String from, String to) {
        cleanExpiredRequests();
        pendingRequests.put(from, new TeleportRequest(from, to));
    }

    public TeleportRequest getRequest(String from) {
        return pendingRequests.get(from);
    }

    public void removeRequest(String from) {
        pendingRequests.remove(from);
    }

    private void cleanExpiredRequests() {
        pendingRequests.entrySet().removeIf(entry -> entry.getValue().isExpired());
    }
}
