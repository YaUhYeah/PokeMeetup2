package io.github.pokemeetup.chat;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.*;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import io.github.pokemeetup.context.GameContext;
import io.github.pokemeetup.multiplayer.client.GameClient;
import io.github.pokemeetup.multiplayer.network.NetworkProtocol;
import io.github.pokemeetup.system.InputManager;  // <--- For setUIState
import io.github.pokemeetup.utils.GameLogger;
import io.github.pokemeetup.utils.TimeUtils;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

public class ChatSystem extends Table {
    public static final float CHAT_PADDING = 10f;
    public static final float MIN_CHAT_WIDTH = 250f;
    public static final float MIN_CHAT_HEIGHT = 200f;
    private static final int MAX_MESSAGES = 50;
    private static final float MESSAGE_FADE_TIME = 10f;
    private static final Color WINDOW_BACKGROUND = new Color(0, 0, 0, 0.8f);

    private static final Color[] CHAT_COLORS = {
        new Color(0.8f, 0.3f, 0.3f, 1), // Red
        new Color(0.3f, 0.8f, 0.3f, 1), // Green
        new Color(0.3f, 0.3f, 0.8f, 1), // Blue
        new Color(0.8f, 0.8f, 0.3f, 1), // Yellow
        new Color(0.8f, 0.3f, 0.8f, 1), // Purple
        new Color(0.3f, 0.8f, 0.8f, 1), // Cyan
        new Color(0.8f, 0.5f, 0.3f, 1), // Orange
        new Color(0.5f, 0.8f, 0.3f, 1)  // Lime
    };

    private final List<String> messageHistory = new ArrayList<>();
    private final Stage stage;
    private final Skin skin;
    private final GameClient gameClient;
    private final String username;
    private final Queue<ChatMessage> messages;
    private final CommandManager commandManager;
    private boolean commandsEnabled;

    private int messageHistoryIndex = -1; // -1 => no history selected
    private Table chatWindow;
    private ScrollPane messageScroll;
    private Table messageTable;
    private boolean isActive;
    private float inactiveTimer;
    private boolean isInitialized = false;
    private TextField inputField;

    public ChatSystem(Stage stage, Skin skin, GameClient gameClient, String username,
                      CommandManager commandManager, boolean commandsEnabled) {
        this.stage = stage;
        this.skin = skin;
        this.gameClient = gameClient;
        this.username = username;
        this.messages = new LinkedList<>();
        this.commandsEnabled = commandsEnabled;
        this.commandManager = commandManager;

        // top alignment
        this.top();

        createChatUI();
        setupChatHandler();
        setupInputHandling();
    }

    @Override
    public void act(float delta) {
        super.act(delta);
        update(delta);
    }

    @Override
    public void setSize(float width, float height) {
        super.setSize(width, height);
        if (chatWindow != null) {
            chatWindow.setSize(width, height);
            if (messageScroll != null) {
                messageScroll.setSize(width, height - 40); // reserve input field space
            }
            chatWindow.invalidateHierarchy();
        }
    }

    // FIX: Removed the hit() method override. The default implementation is sufficient
    // and the original one prevented the ClickListener from working when the chat was inactive.

    @Override
    public void setPosition(float x, float y) {
        super.setPosition(x, y);
        if (chatWindow != null) {
            chatWindow.setPosition(x, y);
        }
    }

    public void resize(int width, int height) {
        float chatWidth = Math.max(MIN_CHAT_WIDTH, width * 0.25f);
        float chatHeight = Math.max(MIN_CHAT_HEIGHT, height * 0.3f);
        setSize(chatWidth, chatHeight);
        // Place near top-left
        setPosition(CHAT_PADDING, height - chatHeight - CHAT_PADDING);
    }

    public boolean isActive() {
        return isActive;
    }

    private void setupChatHandler() {
        gameClient.setChatMessageHandler(this::handleIncomingMessage);
    }

    public void sendMessage(String content) {
        GameLogger.info("sendMessage called with content: " + content);
        if (content.isEmpty()) return;

        // store in local message history if not duplicate
        if (messageHistory.isEmpty() || !content.equals(messageHistory.get(messageHistory.size() - 1))) {
            messageHistory.add(content);
            messageHistoryIndex = messageHistory.size();
        }

        // handle commands:
        if (content.startsWith("/")) {
            if (commandsEnabled || GameContext.get().isMultiplayer()) {
                String command = content.substring(1);
                String[] parts = command.split(" ", 2);
                String commandName = parts[0].toLowerCase();
                String args = parts.length > 1 ? parts[1] : "";
                if (commandManager != null) {
                    Command cmd = commandManager.getCommand(commandName);
                    if (cmd != null) {
                        try {
                            cmd.execute(args, gameClient, this);
                            return;
                        } catch (Exception e) {
                            addSystemMessage("Error executing command: " + e.getMessage());
                            return;
                        }
                    } else {
                        addSystemMessage("Unknown command: " + commandName);
                        return;
                    }
                } else {
                    addSystemMessage("Command system not initialized!");
                    return;
                }
            } else {
                addSystemMessage("Commands are currently disabled.");
                return;
            }
        }

        // normal chat
        NetworkProtocol.ChatMessage chatMessage = new NetworkProtocol.ChatMessage();
        chatMessage.sender = username;
        chatMessage.content = content;
        chatMessage.timestamp = System.currentTimeMillis();
        chatMessage.type = NetworkProtocol.ChatType.NORMAL;

        if (!GameContext.get().isMultiplayer()) {
            handleIncomingMessage(chatMessage);
        } else {
            gameClient.sendMessage(chatMessage);
        }
    }

    public void addSystemMessage(String message) {
        NetworkProtocol.ChatMessage chatMessage = new NetworkProtocol.ChatMessage();
        chatMessage.sender = "System";
        chatMessage.content = message;
        chatMessage.timestamp = System.currentTimeMillis();
        chatMessage.type = NetworkProtocol.ChatType.SYSTEM;
        handleIncomingMessage(chatMessage);
    }

    public void handleIncomingMessage(NetworkProtocol.ChatMessage message) {
        Gdx.app.postRunnable(() -> addMessageToChat(message));
    }

    public void activateChat() {
        isActive = true;
        inputField.setVisible(true);
        inputField.setText("");
        messageHistoryIndex = messageHistory.size();
        inactiveTimer = 0;
        chatWindow.getColor().a = 1f;

        // Use the InputManager to set the state
        GameContext.get().getGameScreen().getInputManager().setUIState(InputManager.UIState.CHAT);

        Gdx.app.postRunnable(() -> {
            stage.setKeyboardFocus(inputField);
            if (Gdx.app.getType() == Application.ApplicationType.Android) {
                Gdx.input.setOnscreenKeyboardVisible(true);
            }
        });
    }

    public void deactivateChat() {
        isActive = false;
        inputField.setVisible(false);
        stage.setKeyboardFocus(null);
        if (Gdx.app.getType() == Application.ApplicationType.Android) {
            Gdx.input.setOnscreenKeyboardVisible(false);
        }

        // Restore the input state to normal via the InputManager
        GameContext.get().getGameScreen().getInputManager().setUIState(InputManager.UIState.NORMAL);
    }


    private void update(float delta) {
        if (!isActive) {
            inactiveTimer += delta;
            if (inactiveTimer > MESSAGE_FADE_TIME) {
                chatWindow.getColor().a =
                    Math.max(0.3f, 1 - (inactiveTimer - MESSAGE_FADE_TIME) / 2f);
            }
        }
        while (messages.size() > MAX_MESSAGES) {
            ((LinkedList<ChatMessage>) messages).removeFirst();
            messageTable.getChildren().first().remove();
        }
    }

    private void createChatUI() {
        if (isInitialized) {
            return;
        }
        chatWindow = new Table();
        chatWindow.top();
        // Background
        Pixmap pixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pixmap.setColor(WINDOW_BACKGROUND);
        pixmap.fill();
        TextureRegion bgTexture = new TextureRegion(new Texture(pixmap));
        pixmap.dispose();
        chatWindow.setBackground(new TextureRegionDrawable(bgTexture));

        Table contentTable = new Table();
        contentTable.pad(10);

        // message area
        messageTable = new Table();
        messageScroll = new ScrollPane(messageTable, skin);
        messageScroll.setFadeScrollBars(false);
        messageScroll.setScrollingDisabled(true, false);
        contentTable.add(messageScroll).expand().fill().padBottom(5).row();

        // input field
        TextField.TextFieldStyle textFieldStyle =
            new TextField.TextFieldStyle(skin.get(TextField.TextFieldStyle.class));
        textFieldStyle.background = skin.newDrawable("white", new Color(0.2f, 0.2f, 0.2f, 0.8f));
        textFieldStyle.fontColor = Color.WHITE;
        textFieldStyle.cursor = skin.newDrawable("white", Color.WHITE);

        inputField = new TextField("", textFieldStyle);
        inputField.setMessageText("Press T to chat...");
        inputField.setTouchable(Touchable.enabled);

        // Add capture listener for key input
        inputField.addCaptureListener(new InputListener() {
            @Override
            public boolean keyDown(InputEvent event, int keycode) {
                if (!isActive) return false; // Only intercept if chat is active.

                switch (keycode) {
                    case Input.Keys.UP:
                        if (!messageHistory.isEmpty() && messageHistoryIndex > 0) {
                            messageHistoryIndex--;
                            String upMsg = messageHistory.get(messageHistoryIndex);
                            inputField.setText(upMsg);
                            inputField.setCursorPosition(upMsg.length());
                        }
                        // **Notice we do NOT call event.stop()** so other input
                        // processors can see keyUp if needed. We'll just return true.
                        return true;

                    case Input.Keys.DOWN:
                        if (!messageHistory.isEmpty()) {
                            if (messageHistoryIndex < messageHistory.size() - 1) {
                                messageHistoryIndex++;
                                String downMsg = messageHistory.get(messageHistoryIndex);
                                inputField.setText(downMsg);
                                inputField.setCursorPosition(downMsg.length());
                            } else {
                                messageHistoryIndex = messageHistory.size();
                                inputField.setText("");
                            }
                        }
                        return true;

                    case Input.Keys.ENTER:
                        // Send chat, then deactivate
                        String content = inputField.getText().trim();
                        if (!content.isEmpty()) {
                            sendMessage(content);
                            inputField.setText("");
                        }
                        deactivateChat();

                        // DO NOT call event.stop() here. Let the event pass through
                        // so other input can see it if needed
                        return true;

                    case Input.Keys.ESCAPE:
                        // Esc just deactivates chat
                        deactivateChat();
                        return true;
                }
                return false;
            }

            @Override
            public boolean keyTyped(InputEvent event, char character) {
                // If user typed something, set messageHistoryIndex to end
                if (character != '\0' && character != '\r' && character != '\n') {
                    messageHistoryIndex = messageHistory.size();
                }
                return false;
            }
        });

        // For mobile: if user taps chat window, we show onscreen keyboard
        chatWindow.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                if (Gdx.app.getType() == Application.ApplicationType.Android && !isActive) {
                    activateChat();
                }
            }
        });

        contentTable.add(inputField).expandX().fillX().height(30);
        chatWindow.add(contentTable).expand().fill();
        this.add(chatWindow).expand().fill();

        inputField.setVisible(false);
        isInitialized = true;
    }

    private Color getSenderColor(String sender) {
        int index = Math.abs(sender.hashCode()) % CHAT_COLORS.length;
        return CHAT_COLORS[index];
    }

    private void addMessageToChat(NetworkProtocol.ChatMessage message) {
        Table messageEntry = new Table();
        messageEntry.pad(5);

        Label.LabelStyle timeStyle = new Label.LabelStyle(skin.get(Label.LabelStyle.class));
        timeStyle.fontColor = Color.GRAY;
        Label timeLabel = new Label(TimeUtils.formatTime(message.timestamp), timeStyle);

        Label.LabelStyle nameStyle = new Label.LabelStyle(skin.get(Label.LabelStyle.class));
        nameStyle.fontColor = getSenderColor(message.sender);
        Label nameLabel = new Label(message.sender + ": ", nameStyle);

        Label.LabelStyle contentStyle = new Label.LabelStyle(skin.get(Label.LabelStyle.class));
        contentStyle.fontColor = Color.WHITE;
        Label contentLabel = new Label(message.content, contentStyle);
        contentLabel.setWrap(true);

        messageEntry.add(timeLabel).padRight(5);
        messageEntry.add(nameLabel).padRight(5);
        messageEntry.add(contentLabel).expandX().fillX();

        messages.add(new ChatMessage(message));
        messageTable.add(messageEntry).expandX().fillX().padBottom(2).row();
        messageScroll.scrollTo(0, 0, 0, 0);

        chatWindow.getColor().a = 1f;
        inactiveTimer = 0;
    }

    private void setupInputHandling() {
        // We attach a general listener to the entire stage to open chat if T or slash
        stage.addListener(new InputListener() {
            @Override
            public boolean keyDown(InputEvent event, int keycode) {
                // if chat is active, we handle it in inputField. If not active, check for T or slash
                if (!isActive && (keycode == Input.Keys.T || keycode == Input.Keys.SLASH)) {
                    activateChat();
                    if (keycode == Input.Keys.SLASH) {
                        inputField.setText("/");
                        inputField.setCursorPosition(1);
                    }
                    // Here we do NOT call event.stop(). We just return true so other
                    // processors know we've handled T or slash, but not forcibly stopping.
                    return true;
                }
                // If chat is active and ESC pressed, we want to deactivate
                if (isActive && keycode == Input.Keys.ESCAPE) {
                    deactivateChat();
                    return true;
                }
                return false;
            }
        });
        // Also re‑attach the same capturing logic to inputField if you want
        // That’s already done above in createChatUI() for ENTER, ESC, etc.
    }

    private static class ChatMessage {
        public final String sender;
        public final String content;
        public final long timestamp;

        public ChatMessage(NetworkProtocol.ChatMessage msg) {
            this.sender = msg.sender;
            this.content = msg.content;
            this.timestamp = msg.timestamp;
        }
    }
}
