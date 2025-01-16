package io.github.pokemeetup.utils;

import java.io.Serializable;

public class ChunkPos implements Serializable {
    public final int x;
    public final int y;

    public ChunkPos(int x, int y) {
        this.x = x;
        this.y = y;
    }

    // Override equals and hashCode using integers
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof ChunkPos)) return false;
        ChunkPos other = (ChunkPos) obj;
        return x == other.x && y == other.y;
    }

    @Override
    public int hashCode() {
        return 31 * x + y;
    }

    @Override
    public String toString() {
        return "(" + x + "," + y + ")";
    }
}
