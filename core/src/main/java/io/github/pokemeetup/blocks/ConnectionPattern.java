
package io.github.pokemeetup.blocks;

public class ConnectionPattern {
    public static final ConnectionPattern NONE = new ConnectionPattern(false, false, false, false);

    private final boolean north;
    private final boolean south;
    private final boolean east;
    private final boolean west;

    public ConnectionPattern(boolean north, boolean south, boolean east, boolean west) {
        this.north = north;
        this.south = south;
        this.east = east;
        this.west = west;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ConnectionPattern)) return false;
        ConnectionPattern that = (ConnectionPattern) o;
        return north == that.north &&
            south == that.south &&
            east == that.east &&
            west == that.west;
    }

    @Override
    public int hashCode() {
        int result = (north ? 1 : 0);
        result = 31 * result + (south ? 1 : 0);
        result = 31 * result + (east ? 1 : 0);
        result = 31 * result + (west ? 1 : 0);
        return result;
    }
}
