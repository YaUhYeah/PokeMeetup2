//package io.github.pokemeetup.chat;
//
//import com.badlogic.gdx.graphics.Color;
//import com.badlogic.gdx.math.Vector2;
//import io.github.pokemeetup.multiplayer.client.GameClient;
//import io.github.pokemeetup.multiplayer.network.NetworkProtocol;
//
//import java.util.HashSet;
//import java.util.Map;
//import java.util.Set;
//import java.util.concurrent.ConcurrentHashMap;
//
//public class TeamSystem {
//    private final Map<String, Team> teams = new ConcurrentHashMap<>();
//    private final Map<String, TeamInvite> pendingInvites = new ConcurrentHashMap<>();
//    private final GameClient gameClient;
//
//    public TeamSystem(GameClient gameClient) {
//        this.gameClient = gameClient;
//    }
//
//    // Network Messages
//    public void sendTeamMessage(String message) {
//        Team playerTeam = getPlayerTeam(gameClient.getLocalUsername());
//        if (playerTeam == null) return;
//
//        NetworkProtocol.ChatMessage teamMessage = new NetworkProtocol.ChatMessage();
//        teamMessage.sender = gameClient.getLocalUsername();
//        teamMessage.content = message;
//        teamMessage.type = NetworkProtocol.ChatType.TEAM;
//        teamMessage.teamName = playerTeam.name;
//        gameClient.sendMessage(teamMessage);
//    }
//
//    public void createTeam(String name, String tag) {
//        NetworkProtocol.TeamCreate request = new NetworkProtocol.TeamCreate();
//        request.name = name;
//        request.tag = tag;
//        request.leader = gameClient.getLocalUsername();
//        gameClient.sendTCP(request);
//    }
//
//    public void invitePlayer(String playerName) {
//        NetworkProtocol.TeamInvite invite = new NetworkProtocol.TeamInvite();
//        invite.teamName = getPlayerTeam(gameClient.getLocalUsername()).name;
//        invite.inviter = gameClient.getLocalUsername();
//        invite.invitee = playerName;
//        gameClient.sendTCP(invite);
//    }
//
//    public void setHeadquarters() {
//        Team team = getPlayerTeam(gameClient.getLocalUsername());
//        if (team == null || !team.leader.equals(gameClient.getLocalUsername())) return;
//
//        NetworkProtocol.TeamHQUpdate update = new NetworkProtocol.TeamHQUpdate();
//        update.teamName = team.name;
//        update.x = gameClient.getPlayer().getTileX();
//        update.y = gameClient.getPlayer().getTileY();
//        gameClient.sendTCP(update);
//    }
//
//    public static class Team {
//        public final String name;
//        public final String tag; // Short team tag like [TEAM]
//        public final Set<String> members = new HashSet<>();
//        public final Set<String> moderators = new HashSet<>();
//        public String leader;
//        public Vector2 headquarters;
//        public String description;
//        private long created;
//        private Color teamColor;
//
//        public Team(String name, String tag, String leader) {
//            this.name = name;
//            this.tag = tag;
//            this.leader = leader;
//            this.created = System.currentTimeMillis();
//            this.teamColor = generateTeamColor();
//        }
//
//        private Color generateTeamColor() {
//            // Generate unique color based on team name
//            float hue = Math.abs(name.hashCode() % 360) / 360f;
//            return Color.valueOf(String.format("hsl %f 0.8 0.5", hue));
//        }
//    }
//
//    public static class TeamInvite {
//        public static final long INVITE_TIMEOUT = 120000; // 2 minutes
//        public final String team;
//        public final String inviter;
//        public final String invitee;
//        public final long timestamp;
//
//        public TeamInvite(String team, String inviter, String invitee) {
//            this.team = team;
//            this.inviter = inviter;
//            this.invitee = invitee;
//            this.timestamp = System.currentTimeMillis();
//        }
//
//        public boolean isExpired() {
//            return System.currentTimeMillis() - timestamp > INVITE_TIMEOUT;
//        }
//    }
//}
//
//// Add to NetworkProtocol
//
//
//// Team Commands
//public class TeamCommand implements Command {
//    private static final String[] SUBCOMMANDS = {
//        "create", "invite", "accept", "leave", "kick", "promote",
//        "demote", "sethq", "hq", "info", "list", "chat"
//    };
//
//    @Override
//    public String getName() {
//        return "team";
//    }
//
//    @Override
//    public String[] getAliases() {
//        return new String[]{"t"};
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
//        return true;
//    }
//
//    @Override
//    public void execute(String args, GameClient gameClient, ChatSystem chatSystem) {
//        String[] parts = args.split("\\s+", 2);
//        String subCommand = parts[0].toLowerCase();
//        String subArgs = parts.length > 1 ? parts[1] : "";
//
//        switch (subCommand) {
//            case "create":
//                handleCreate(subArgs, gameClient, chatSystem);
//                break;
//            case "invite":
//                handleInvite(subArgs, gameClient, chatSystem);
//                break;
//            case "sethq":
//                handleSetHQ(gameClient, chatSystem);
//                break;
//            case "hq":
//                handleHQTeleport(gameClient, chatSystem);
//                break;
//            // ... handle other subcommands
//        }
//    }
//
//    private void handleCreate(String args, GameClient gameClient, ChatSystem chatSystem) {
//        String[] parts = args.split("\\s+", 2);
//        if (parts.length < 2) {
//            sendUsage(chatSystem, "/team create <name> <tag>");
//            return;
//        }
//
//        String name = parts[0];
//        String tag = parts[1];
//
//        if (!isValidTeamName(name) || !isValidTeamTag(tag)) {
//            sendError(chatSystem, "Invalid team name or tag format!");
//            return;
//        }
//
//        gameClient.getTeamSystem().createTeam(name, tag);
//    }
//
//    private void handleSetHQ(GameClient gameClient, ChatSystem chatSystem) {
//        Team team = gameClient.getTeamSystem().getPlayerTeam(gameClient.getLocalUsername());
//        if (team == null) {
//            sendError(chatSystem, "You are not in a team!");
//            return;
//        }
//
//        if (!team.leader.equals(gameClient.getLocalUsername())) {
//            sendError(chatSystem, "Only team leader can set headquarters!");
//            return;
//        }
//
//        gameClient.getTeamSystem().setHeadquarters();
//        sendSuccess(chatSystem, "Team headquarters set to your current location!");
//    }
//
//    private void handleHQTeleport(GameClient gameClient, ChatSystem chatSystem) {
//        Team team = gameClient.getTeamSystem().getPlayerTeam(gameClient.getLocalUsername());
//        if (team == null || team.headquarters == null) {
//            sendError(chatSystem, "No team headquarters set!");
//            return;
//        }
//
//        NetworkProtocol.TeleportRequest request = new NetworkProtocol.TeleportRequest();
//        request.type = TeleportType.TEAM_HQ;
//        request.player = gameClient.getLocalUsername();
//        request.teamName = team.name;
//        gameClient.sendTCP(request);
//    }
//}
