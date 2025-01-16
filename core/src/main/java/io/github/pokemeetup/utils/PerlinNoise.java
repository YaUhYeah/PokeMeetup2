package io.github.pokemeetup.utils;

import java.util.Random;

public class PerlinNoise {
    private static final int PERMUTATION_SIZE = 256;
    private static final int PERMUTATION_MASK = PERMUTATION_SIZE - 1;
    private final int[] permutation;
    public PerlinNoise(int seed) {
        Random random = new Random(seed);
        this.permutation = new int[PERMUTATION_SIZE * 2];

        // Initialize the permutation array
        int[] p = new int[PERMUTATION_SIZE];
        for (int i = 0; i < PERMUTATION_SIZE; i++) {
            p[i] = i;
        }

        // Fisher-Yates shuffle
        for (int i = PERMUTATION_SIZE - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            int temp = p[i];
            p[i] = p[j];
            p[j] = temp;
        }

        // Duplicate the permutation array
        for (int i = 0; i < PERMUTATION_SIZE * 2; i++) {
            permutation[i] = p[i & PERMUTATION_MASK];
        }
    }

    // In PerlinNoise class

    public double noise(double x, double y) {
        return generateNoise(x, y) * 1.05; // Slightly amplify to reach edges
    }


    private double generateNoise(double x, double y) {
        // Get integer coordinates
        int X = fastFloor(x) & PERMUTATION_MASK;
        int Y = fastFloor(y) & PERMUTATION_MASK;

        // Get relative coordinates within unit square
        x -= fastFloor(x);
        y -= fastFloor(y);

        // Compute fade curves
        double u = fade(x);
        double v = fade(y);

        // Hash coordinates of the 4 square corners
        int A = permutation[X] + Y;
        int AA = permutation[A];
        int AB = permutation[A + 1];
        int B = permutation[X + 1] + Y;
        int BA = permutation[B];
        int BB = permutation[B + 1];

        // Add blended results from 4 corners of the square
        double result = lerp(v,
            lerp(u, grad(permutation[AA], x, y),
                grad(permutation[BA], x - 1, y)),
            lerp(u, grad(permutation[AB], x, y - 1),
                grad(permutation[BB], x - 1, y - 1)));

        return result / 0.7071; // Approximate maximum value for 2D Perlin noise
    }
    private static double fade(double t) {
        return t * t * t * (t * (t * 6 - 15) + 10);
    }

    private static double lerp(double t, double a, double b) {
        return a + t * (b - a);
    }

    private static double grad(int hash, double x, double y) {
        int h = hash & 7;
        double u = h < 4 ? x : y;
        double v = h < 4 ? y : x;
        return ((h & 1) == 0 ? u : -u) + ((h & 2) == 0 ? v : -v);
    }

    private static int fastFloor(double x) {
        int xi = (int) x;
        return x < xi ? xi - 1 : xi;
    }
}
