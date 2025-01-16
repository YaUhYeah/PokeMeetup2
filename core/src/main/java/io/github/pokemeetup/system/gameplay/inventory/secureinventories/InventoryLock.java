package io.github.pokemeetup.system.gameplay.inventory.secureinventories;

import java.util.concurrent.locks.ReentrantReadWriteLock;

public class InventoryLock {
    private static final ReentrantReadWriteLock lock = new ReentrantReadWriteLock(true);
    
    public static void readLock() {
        lock.readLock().lock();
    }
    
    public static void readUnlock() {
        lock.readLock().unlock();
    }
    
    public static void writeLock() {
        lock.writeLock().lock();
    }
    
    public static void writeUnlock() {
        lock.writeLock().unlock();
    }
    
    public static ReentrantReadWriteLock getLock() {
        return lock;
    }
}