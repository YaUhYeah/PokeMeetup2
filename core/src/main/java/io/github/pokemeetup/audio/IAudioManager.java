package io.github.pokemeetup.audio;

import io.github.pokemeetup.system.gameplay.overworld.biomes.BiomeType;

public interface IAudioManager {
    void playSound(AudioManagerImpl.SoundEffect effect);
    void updateBiomeMusic(BiomeType newBiome);
    void playMenuMusic();
    void stopMenuMusic();
    void setMusicVolume(float volume);
    float getMusicVolume();
    void setSoundVolume(float volume);
    float getSoundVolume();
    void setMusicEnabled(boolean enabled);
    boolean isMusicEnabled();
    void setSoundEnabled(boolean enabled);
    boolean isSoundEnabled();
    void update(float delta);
    void dispose();
    void playWeatherSound(AudioManagerImpl.WeatherSoundEffect effect, float volume, float pitch);
    void stopAllAmbientSounds();
    void stopAllWeatherLoops();
}
