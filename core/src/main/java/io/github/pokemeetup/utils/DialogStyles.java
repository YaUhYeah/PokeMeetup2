package io.github.pokemeetup.utils;

import com.badlogic.gdx.scenes.scene2d.ui.*;

public class DialogStyles {
    public static void applyStyles(Skin skin) {
        // Window style for dialogs
        Window.WindowStyle windowStyle = skin.get(Window.WindowStyle.class);
        windowStyle.titleFont.getData().setScale(1.2f);

        // Text button style
        TextButton.TextButtonStyle buttonStyle = skin.get(TextButton.TextButtonStyle.class);
        buttonStyle.font.getData().setScale(1.0f);

        // Label styles
        Label.LabelStyle titleStyle = new Label.LabelStyle(skin.get(Label.LabelStyle.class));
        titleStyle.font.getData().setScale(1.2f);
        skin.add("title", titleStyle);

        Label.LabelStyle smallStyle = new Label.LabelStyle(skin.get(Label.LabelStyle.class));
        smallStyle.font.getData().setScale(0.8f);
        skin.add("small", smallStyle);

        // TextField style
        TextField.TextFieldStyle textFieldStyle = skin.get(TextField.TextFieldStyle.class);
        textFieldStyle.font.getData().setScale(1.0f);
    }
}
