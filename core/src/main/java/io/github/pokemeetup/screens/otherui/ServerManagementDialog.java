package io.github.pokemeetup.screens.otherui;

import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import io.github.pokemeetup.multiplayer.server.config.ServerConnectionConfig;

import java.util.function.Consumer;


public class ServerManagementDialog extends Dialog {
    private final TextField nameField;
    private final TextField ipField;
    private final TextField tcpPortField;
    private final TextField udpPortField;
    private final ServerConnectionConfig editingServer;
    private final Consumer<ServerConnectionConfig> onSave;

    public ServerManagementDialog(Skin skin, ServerConnectionConfig editServer,
                                  Consumer<ServerConnectionConfig> onSave) {
        super("", skin);
        this.editingServer = editServer;
        this.onSave = onSave;

        Table content = new Table();
        content.pad(20);

        // Title
        content.add(new Label(editServer == null ? "Add Server" : "Edit Server",
            skin)).colspan(2).pad(10).row();

        // Fields
        nameField = new TextField(editServer != null ? editServer.getServerName() : "", skin);
        ipField = new TextField(editServer != null ? editServer.getServerIP() : "", skin);
        tcpPortField = new TextField(editServer != null ?
            String.valueOf(editServer.getTcpPort()) : "", skin);
        udpPortField = new TextField(editServer != null ?
            String.valueOf(editServer.getUdpPort()) : "", skin);

        content.add(new Label("Server Name:", skin)).left().pad(5);
        content.add(nameField).width(200).pad(5).row();

        content.add(new Label("IP Address:", skin)).left().pad(5);
        content.add(ipField).width(200).pad(5).row();

        content.add(new Label("TCP Port:", skin)).left().pad(5);
        content.add(tcpPortField).width(200).pad(5).row();

        content.add(new Label("UDP Port:", skin)).left().pad(5);
        content.add(udpPortField).width(200).pad(5).row();

        // Buttons
        TextButton saveButton = new TextButton("Save", skin);
        TextButton cancelButton = new TextButton("Cancel", skin);

        Table buttons = new Table();
        buttons.add(saveButton).width(100).pad(10);
        buttons.add(cancelButton).width(100).pad(10);

        content.add(buttons).colspan(2).pad(20);

        // Event handlers
        saveButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                saveServer();
            }
        });

        cancelButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                hide();
            }
        });

        getContentTable().add(content);
    }

    private void saveServer() {
        try {
            // Validate input
            String name = nameField.getText().trim();
            String ip = ipField.getText().trim();
            int tcpPort = Integer.parseInt(tcpPortField.getText().trim());
            int udpPort = Integer.parseInt(udpPortField.getText().trim());

            if (name.isEmpty() || ip.isEmpty()) {
                throw new IllegalArgumentException("All fields are required");
            }

            // Create config
            ServerConnectionConfig config = new ServerConnectionConfig(
                ip, tcpPort, udpPort, name,  100
            );

            onSave.accept(config);
            hide();

        } catch (NumberFormatException e) {
            showError("Invalid port number");
        } catch (Exception e) {
            showError(e.getMessage());
        }
    }

    private void showError(String message) {
        Dialog error = new Dialog("Error", getSkin());
        error.text(message);
        error.button("OK");
        error.show(getStage());
    }
}
