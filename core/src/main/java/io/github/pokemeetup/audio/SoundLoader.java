package io.github.pokemeetup.audio;

import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.audio.Sound;

public interface SoundLoader {
    Sound loadSound(String path);
    Music loadMusic(String path);
}
