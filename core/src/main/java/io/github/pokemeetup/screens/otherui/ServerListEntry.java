package io.github.pokemeetup.screens.otherui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.TimeUtils;
import com.esotericsoftware.kryonet.Client;
import com.esotericsoftware.kryonet.Connection;
import com.esotericsoftware.kryonet.Listener;
import io.github.pokemeetup.multiplayer.network.NetworkProtocol;
import io.github.pokemeetup.multiplayer.server.config.ServerConnectionConfig;
import io.github.pokemeetup.utils.GameLogger;
import io.github.pokemeetup.utils.textures.TextureManager;

import java.util.Base64;

public class ServerListEntry extends Table {
    private final Texture defaultIcon;
    private Image serverIcon;
    private Label nameLabel;
    private Label motdLabel;
    private Label playerCountLabel;
    private Label pingLabel;
    private ServerConnectionConfig config;
    private NetworkProtocol.ServerInfo serverInfo;
    private final Skin skin;
    private float lastUpdateTime = 0;
    private static final float UPDATE_INTERVAL = 5f; // Update every 5 seconds

    public ServerListEntry(ServerConnectionConfig config, Skin skin) {
        super(skin);
        this.skin = skin;
        this.config = config;
        this.defaultIcon = new Texture(Gdx.files.internal("ui/default-server-icon.png"));

        setup();
        updateServerInfo();
    }

    private void setup() {
        setBackground(new TextureRegionDrawable(TextureManager.ui.findRegion("slot_background"))); // Use your skin's button background
        pad(10);

        // Server icon (64x64 recommended size)
        serverIcon = new Image(defaultIcon);
        serverIcon.setSize(64, 64);

        // Labels with custom styles
        nameLabel = new Label(config.getServerName(), skin);
        motdLabel = new Label("Connecting...", skin);
        motdLabel.setWrap(true);
        playerCountLabel = new Label("???/???", skin);
        pingLabel = new Label("???ms", skin);

        // Layout
        Table leftSide = new Table();
        leftSide.add(serverIcon).size(64).pad(5);

        Table serverInfo = new Table();
        serverInfo.add(nameLabel).left().expandX().row();
        serverInfo.add(motdLabel).left().expandX().width(300).row();

        Table rightSide = new Table();
        rightSide.add(playerCountLabel).right().padRight(10);
        rightSide.add(pingLabel).right();

        add(leftSide).padRight(10);
        add(serverInfo).expandX().fill();
        add(rightSide).right();

        // Hover effect
        addListener(new InputListener() {
            @Override
            public void enter(InputEvent event, float x, float y, int pointer, Actor fromActor) {
                setBackground(new TextureRegionDrawable(TextureManager.ui.findRegion("slot_background")));
            }

            @Override
            public void exit(InputEvent event, float x, float y, int pointer, Actor toActor) {
                setBackground(new TextureRegionDrawable(TextureManager.ui.findRegion("slot_background")));
            }
        });
    }


    public void updateServerInfo() {
        float currentTime = TimeUtils.millis() / 1000f;
        if (currentTime - lastUpdateTime < UPDATE_INTERVAL) {
            return;
        }
        lastUpdateTime = currentTime;

        Thread infoThread = new Thread(() -> {
            Client tempClient = null;
            try {
                tempClient = new Client(16384, 2048);
                NetworkProtocol.registerClasses(tempClient.getKryo());
                tempClient.start();

                final long startTime = System.currentTimeMillis();
                final Client finalClient = tempClient;

                tempClient.addListener(new Listener() {
                    @Override
                    public void received(Connection connection, Object object) {
                        if (object instanceof NetworkProtocol.ServerInfoResponse) {
                            NetworkProtocol.ServerInfoResponse response =
                                (NetworkProtocol.ServerInfoResponse) object;
                            long ping = System.currentTimeMillis() - startTime;

                            Gdx.app.postRunnable(() -> {
                                updateUI(response.serverInfo, ping);
                            });

                            closeClientSafely(finalClient);
                        }
                    }
                });

                tempClient.connect(5000, config.getServerIP(),
                    config.getTcpPort(), config.getUdpPort());

                NetworkProtocol.ServerInfoRequest request = new NetworkProtocol.ServerInfoRequest();
                request.timestamp = System.currentTimeMillis();
                tempClient.sendTCP(request);

            } catch (Exception e) {
                final Client finalClient = tempClient;
                Gdx.app.postRunnable(() -> {
                    updateUIError();
                    closeClientSafely(finalClient);
                });
            }
        });
        infoThread.start();
    }private void closeClientSafely(Client client) {
        if (client != null) {
            try {
                client.close();
            } catch (Exception e) {
                GameLogger.error("Error closing temporary client: " + e.getMessage());
            }
        }
    }
    private void updateUI(NetworkProtocol.ServerInfo info, long ping) {
        this.serverInfo = info;

        nameLabel.setText(info.name);
        motdLabel.setText(info.motd);
        playerCountLabel.setText(info.playerCount + "/" + info.maxPlayers);
        pingLabel.setText(ping + "ms");

        // Update server icon if provided
        if (info.iconBase64 != null && !info.iconBase64.isEmpty()) {
            try {
                byte[] iconData = Base64.getDecoder().decode(info.iconBase64);
                Pixmap pixmap = new Pixmap(iconData, 0, iconData.length);
                Texture iconTexture = new Texture(pixmap);
                serverIcon.setDrawable(new TextureRegionDrawable(new TextureRegion(iconTexture)));
                pixmap.dispose();
            } catch (Exception e) {
                GameLogger.error("Error loading server icon: " + e.getMessage());
            }
        }
    }

    private void updateUIError() {
        nameLabel.setText(config.getServerName() + " (Offline)");
        motdLabel.setText("Cannot connect to server");
        playerCountLabel.setText("0/0");
        pingLabel.setText("---");
    }

    public void dispose() {
        defaultIcon.dispose();
        // Dispose of any custom icon textures
        if (serverIcon.getDrawable() instanceof TextureRegionDrawable) {
            TextureRegionDrawable drawable = (TextureRegionDrawable) serverIcon.getDrawable();
            drawable.getRegion().getTexture().dispose();
        }
    }
}
