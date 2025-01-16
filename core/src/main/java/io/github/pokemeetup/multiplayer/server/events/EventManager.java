package io.github.pokemeetup.multiplayer.server.events;

import io.github.pokemeetup.utils.GameLogger;

import java.util.*;
import java.util.concurrent.*;


    public class EventManager {
        private final Map<Class<? extends ServerEvent>, List<EventListener<?>>> listeners;
        private final ExecutorService eventExecutor;
        private volatile boolean isShuttingDown = false;

        public EventManager() {
            this.listeners = new ConcurrentHashMap<>();
            this.eventExecutor = Executors.newFixedThreadPool(2);
        }



        public void fireEvent(ServerEvent event) {
            if (isShuttingDown) {
                GameLogger.info("Dropping event during shutdown: " + event.getEventName());
                return;
            }

            Class<? extends ServerEvent> eventClass = event.getClass();
            List<EventListener<?>> eventListeners = listeners.get(eventClass);

            if (eventListeners != null) {
                eventExecutor.submit(() -> {
                    for (EventListener<?> listener : eventListeners) {
                        try {
                            @SuppressWarnings("unchecked")
                            EventListener<ServerEvent> typedListener = (EventListener<ServerEvent>) listener;
                            typedListener.onEvent(event);
                        } catch (Exception e) {
                            GameLogger.info("Error handling event " + event.getEventName() + ": " + e.getMessage());
                            e.printStackTrace();
                        }
                    }
                });
            }
        }


    public void shutdown() {
        isShuttingDown = true;

        // Stop accepting new events
        eventExecutor.shutdown();

        try {
            // Wait for existing events to complete
            if (!eventExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                // Force shutdown if events don't complete in time
                List<Runnable> pendingEvents = eventExecutor.shutdownNow();
                GameLogger.info("Force-terminated " + pendingEvents.size() + " pending events");
            }
        } catch (InterruptedException e) {
            eventExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        } finally {
            // Clear all listeners
            listeners.clear();
        }
    }
    public <T extends ServerEvent> void registerListener(Class<T> eventClass, EventListener<T> listener) {
        listeners.computeIfAbsent(eventClass, k -> new ArrayList<>()).add(listener);
    }


}

