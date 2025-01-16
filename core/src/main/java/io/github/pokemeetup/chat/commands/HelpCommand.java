//package io.github.pokemeetup.chat;
//
//import io.github.pokemeetup.managers.DatabaseManager;
//import io.github.pokemeetup.multiplayer.client.GameClient;
//import io.github.pokemeetup.multiplayer.network.NetworkProtocol;
//
//import java.util.HashMap;
//import java.util.Map;
//import java.util.concurrent.ConcurrentHashMap;
//
//public class CommandSystem {
//    private static final String COMMAND_PREFIX = "/";
//    private final Map<String, Command> commands = new HashMap<>();
//    private final GameClient gameClient;
//    private final ChatSystem chatSystem;
//
//    public CommandSystem(GameClient gameClient, ChatSystem chatSystem) {
//        this.gameClient = gameClient;
//        this.chatSystem = chatSystem;
//        registerDefaultCommands();
//    }
//
//    private void registerDefaultCommands() {
//        // Universal Commands (work in both SP and MP)
//        registerCommand(new HelpCommand());
//        registerCommand(new TimeCommand());
//        registerCommand(new WeatherCommand());
//        registerCommand(new ClearCommand());
//        registerCommand(new SpawnCommand());
//
//        // Multiplayer Only Commands
//        if (!gameClient.isSinglePlayer()) {
//            registerCommand(new TpaCommand());
//            registerCommand(new TpAcceptCommand());
//            registerCommand(new TpDenyCommand());
//            registerCommand(new WhisperCommand());
//            registerCommand(new PartyCommand());
//            registerCommand(new TradeCommand());
//        }
//    }
//
//    public void processCommand(String message) {
//        if (!message.startsWith(COMMAND_PREFIX)) return;
//
//        String[] parts = message.substring(1).split("\\s+", 2);
//        String commandName = parts[0].toLowerCase();
//        String args = parts.length > 1 ? parts[1] : "";
//
//        Command command = commands.get(commandName);
//        if (command != null) {
//            if (command.isMultiplayerOnly() && gameClient.isSinglePlayer()) {
//                sendSystemMessage("This command is only available in multiplayer!");
//                return;
//            }
//            command.execute(args, gameClient, chatSystem);
//        } else {
//            sendSystemMessage("Unknown command: " + commandName);
//        }
//    }
//
//    private void registerCommand(Command command) {
//        commands.put(command.getName().toLowerCase(), command);
//        for (String alias : command.getAliases()) {
//            commands.put(alias.toLowerCase(), command);
//        }
//    }
//
//    private void sendSystemMessage(String message) {
//        NetworkProtocol.ChatMessage systemMsg = new NetworkProtocol.ChatMessage();
//        systemMsg.sender = "System";
//        systemMsg.content = message;
//        systemMsg.type = NetworkProtocol.ChatType.SYSTEM;
//        systemMsg.timestamp = System.currentTimeMillis();
//        chatSystem.handleIncomingMessage(systemMsg);
//    }
//}
//
//// Example Command Implementations
//public class HelpCommand implements Command {
//    @Override
//    public String getName() { return "help"; }
//
//    @Override
//    public String[] getAliases() { return new String[]{"?"}; }
//
//    @Override
//    public String getDescription() { return "Shows list of available commands"; }
//
//    @Override
//    public String getUsage() { return "/help [command]"; }
//
//    @Override
//    public boolean isMultiplayerOnly() { return false; }
//
//    @Override
//    public void execute(String args, GameClient gameClient, ChatSystem chatSystem) {
//        // Implementation
//    }
//}
//
//public class TpaCommand implements Command {
//    @Override
//    public String getName() { return "tpa"; }
//
//    @Override
//    public String[] getAliases() { return new String[]{"tpask"}; }
//
//    @Override
//    public String getDescription() { return "Request to teleport to another player"; }
//
//    @Override
//    public String getUsage() { return "/tpa <player>"; }
//
//    @Override
//    public boolean isMultiplayerOnly() { return true; }
//
//    @Override
//    public void execute(String args, GameClient gameClient, ChatSystem chatSystem) {
//        if (args.isEmpty()) {
//            sendUsageMessage(chatSystem);
//            return;
//        }
//
//        NetworkProtocol.TeleportRequest request = new NetworkProtocol.TeleportRequest();
//        request.from = gameClient.getLocalUsername();
//        request.to = args;
//        request.timestamp = System.currentTimeMillis();
//        gameClient.sendTCP(request);
//    }
//}
//
//// Now let's implement the location-based commands
//public class SpawnCommand implements Command {
//    @Override
//    public String getName() { return "spawn"; }
//
//    @Override
//    public String[] getAliases() { return new String[]{}; }
//
//    @Override
//    public String getDescription() { return "Teleport to spawn point"; }
//
//    @Override
//    public String getUsage() { return "/spawn"; }
//
//    @Override
//    public boolean isMultiplayerOnly() { return false; }
//
//    @Override
//    public void execute(String args, GameClient gameClient, ChatSystem chatSystem) {
//        if (!gameClient.getPermissionManager().hasPermission(
//            gameClient.getLocalUsername(), Permission.COMMAND_SPAWN)) {
//            sendNoPermissionMessage(chatSystem);
//            return;
//        }
//
//        WorldData worldData = gameClient.getCurrentWorld().getWorldData();
//        Vector2 spawnPoint = new Vector2(worldData.getSpawnX(), worldData.getSpawnY());
//
//        if (gameClient.isSinglePlayer()) {
//            gameClient.getPlayer().teleportTo(spawnPoint.x, spawnPoint.y);
//            sendSuccessMessage(chatSystem, "Teleported to spawn!");
//        } else {
//            NetworkProtocol.TeleportRequest request = new NetworkProtocol.TeleportRequest();
//            request.type = TeleportType.SPAWN;
//            request.player = gameClient.getLocalUsername();
//            gameClient.sendTCP(request);
//        }
//    }
//}
//
//public class SetSpawnCommand implements Command {
//    @Override
//    public String getName() { return "setspawn"; }
//
//    @Override
//    public String[] getAliases() {
//        return new String[0];
//    }
//
//    @Override
//    public String getDescription() {
//        return null;
//    }
//
//    @Override
//    public String getUsage() {
//        return null;
//    }
//
//    @Override
//    public boolean isMultiplayerOnly() {
//        return false;
//    }
//
//    @Override
//    public void execute(String args, GameClient gameClient, ChatSystem chatSystem) {
//        if (!gameClient.getPermissionManager().hasPermission(
//            gameClient.getLocalUsername(), Permission.COMMAND_SETSPAWN)) {
//            sendNoPermissionMessage(chatSystem);
//            return;
//        }
//
//        Player player = gameClient.getPlayer();
//        Vector2 newSpawn = new Vector2(player.getTileX(), player.getTileY());
//
//        if (gameClient.isSinglePlayer()) {
//            WorldData worldData = gameClient.getCurrentWorld().getWorldData();
//            worldData.setSpawnX((int)newSpawn.x);
//            worldData.setSpawnY((int)newSpawn.y);
//            sendSuccessMessage(chatSystem, "Spawn point set!");
//        } else {
//            NetworkProtocol.SetSpawnRequest request = new NetworkProtocol.SetSpawnRequest();
//            request.x = (int)newSpawn.x;
//            request.y = (int)newSpawn.y;
//            request.player = gameClient.getLocalUsername();
//            gameClient.sendTCP(request);
//        }
//    }
//}
//
//// Home System
//public class HomeManager {
//    public static class HomeLocation {
//        public final String name;
//        public final int x, y;
//        public final long created;
//
//        public HomeLocation(String name, int x, int y) {
//            this.name = name;
//            this.x = x;
//            this.y = y;
//            this.created = System.currentTimeMillis();
//        }
//    }
//
//    private final Map<String, Map<String, HomeLocation>> playerHomes = new ConcurrentHashMap<>();
//    private final DatabaseManager databaseManager;
//    private final PermissionManager permissionManager;
//
//    public void setHome(String player, String homeName, int x, int y) {
//        playerHomes.computeIfAbsent(player, k -> new ConcurrentHashMap<>())
//            .put(homeName, new HomeLocation(homeName, x, y));
//        databaseManager.saveHomeLocation(player, homeName, x, y);
//    }
//
//    public HomeLocation getHome(String player, String homeName) {
//        Map<String, HomeLocation> homes = playerHomes.get(player);
//        return homes != null ? homes.get(homeName) : null;
//    }
//}
//
//public class HomeCommand implements Command {
//    @Override
//    public String getName() { return "home"; }
//
//    @Override
//    public String[] getAliases() {
//        return new String[0];
//    }
//
//    @Override
//    public String getDescription() {
//        return null;
//    }
//
//    @Override
//    public String getUsage() { return "/home [name]"; }
//
//    @Override
//    public boolean isMultiplayerOnly() {
//        return false;
//    }
//
//    @Override
//    public void execute(String args, GameClient gameClient, ChatSystem chatSystem) {
//        if (!gameClient.getPermissionManager().hasPermission(
//            gameClient.getLocalUsername(), Permission.COMMAND_HOME)) {
//            sendNoPermissionMessage(chatSystem);
//            return;
//        }
//
//        String homeName = args.isEmpty() ? "default" : args;
//        HomeManager.HomeLocation home = gameClient.getHomeManager().getHome(
//            gameClient.getLocalUsername(), homeName);
//
//        if (home == null) {
//            sendErrorMessage(chatSystem, "Home '" + homeName + "' not found!");
//            return;
//        }
//
//        if (gameClient.isSinglePlayer()) {
//            gameClient.getPlayer().teleportTo(home.x, home.y);
//            sendSuccessMessage(chatSystem, "Teleported to home!");
//        } else {
//            NetworkProtocol.TeleportRequest request = new NetworkProtocol.TeleportRequest();
//            request.type = TeleportType.HOME;
//            request.player = gameClient.getLocalUsername();
//            request.homeName = homeName;
//            gameClient.sendTCP(request);
//        }
//    }
//}
//
