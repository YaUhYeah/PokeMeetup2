//package io.github.pokemeetup.chat;
//
//import io.github.pokemeetup.managers.DatabaseManager;
//
//import java.util.Map;
//import java.util.Set;
//import java.util.concurrent.ConcurrentHashMap;
//
//public enum Permission {
//    COMMAND_SPAWN,
//    COMMAND_SETSPAWN,
//    COMMAND_HOME,
//    COMMAND_SETHOME,
//    COMMAND_TPA,
//    COMMAND_TPACCEPT,
//    COMMAND_TPDENY,
//    COMMAND_MULTIPLE_HOMES,  // For allowing multiple named homes
//    ADMIN_COMMANDS,
//
//    // Add more as needed
//}
//
//public class PermissionManager {
//    private final Map<String, Set<Permission>> playerPermissions = new ConcurrentHashMap<>();
//    private final Map<String, Integer> playerHomeLimit = new ConcurrentHashMap<>();
//    private final DatabaseManager databaseManager;
//
//    public PermissionManager(DatabaseManager databaseManager) {
//        this.databaseManager = databaseManager;
//    }
//
//    public boolean hasPermission(String username, Permission permission) {
//        Set<Permission> permissions = playerPermissions.get(username);
//        return permissions != null && permissions.contains(permission);
//    }
//
//    public void loadPlayerPermissions(String username) {
//        // Load from database
//        Set<Permission> permissions = databaseManager.getPlayerPermissions(username);
//        playerPermissions.put(username, permissions);
//        playerHomeLimit.put(username, databaseManager.getPlayerHomeLimit(username));
//    }
//}
