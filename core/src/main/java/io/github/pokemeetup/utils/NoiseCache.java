
package io.github.pokemeetup.utils;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class NoiseCache {
    private static final Map<String, Double> cache = new ConcurrentHashMap<>();

    public static double getNoise(long seed, float x, float y, float scale) {
        String key = seed + ":" + x + ":" + y + ":" + scale;
        return cache.computeIfAbsent(key, k -> (OpenSimplex2.noise2(seed, x * scale, y * scale) + 1.0) / 2.0);
    }
}
