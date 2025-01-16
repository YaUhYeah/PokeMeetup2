package io.github.pokemeetup.audio;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.audio.Sound;
import org.springframework.stereotype.Component;

@Component
public class GdxSoundLoader implements SoundLoader {

    @Override
    public Sound loadSound(String path) {
        try {
            return Gdx.audio.newSound(Gdx.files.internal(path));
        } catch (Exception e) {
            System.err.println("Failed to load sound: " + path + " - " + e.getMessage());
            return null;
        }
    }

    @Override
    public Music loadMusic(String path) {
        try {
            return Gdx.audio.newMusic(Gdx.files.internal(path));
        } catch (Exception e) {
            System.err.println("Failed to load music: " + path + " - " + e.getMessage());
            return null;
        }
    }
}
