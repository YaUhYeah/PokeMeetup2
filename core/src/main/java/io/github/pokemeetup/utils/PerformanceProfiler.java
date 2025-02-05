package io.github.pokemeetup.utils;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class PerformanceProfiler {
    private static final Map<String, Long> timings = new ConcurrentHashMap<>();

    public static void start(String tag) {
        timings.put(tag, System.nanoTime());
    }

    public static void end(String tag) {
        Long startTime = timings.remove(tag);
        if (startTime != null) {
            long duration = System.nanoTime() - startTime;
//            GameLogger.info("Performance [" + tag + "]: " + (duration / 1_000_000.0) + " ms");
        }
    }
}
