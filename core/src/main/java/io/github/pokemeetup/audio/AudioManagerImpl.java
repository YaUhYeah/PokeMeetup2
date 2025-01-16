package io.github.pokemeetup.audio;

import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.math.MathUtils;
import io.github.pokemeetup.system.gameplay.overworld.biomes.BiomeType;
import io.github.pokemeetup.utils.GameLogger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AudioManagerImpl implements IAudioManager {

    private final SoundLoader soundLoader;

    private final Map<WeatherSoundEffect, Sound> weatherSounds = new EnumMap<>(WeatherSoundEffect.class);
    private final Map<WeatherSoundEffect, Long> loopingSoundIds = new EnumMap<>(WeatherSoundEffect.class);
    private final Map<SoundEffect, Sound> sounds;
    private final Map<BiomeType, List<Music>> biomeMusic;

    private final Map<String, Sound> customSounds;
    private final float MUSIC_FADE_DURATION = 2.0f;
    private final Map<AmbientSoundType, Sound> ambientSounds;
    private final Map<AmbientSoundType, Long> activeAmbientLoops;
    private final Map<WeatherSoundEffect, Long> loopingStartTimes = new EnumMap<>(WeatherSoundEffect.class);
    private final Map<WeatherSoundEffect, Float> loopingDurations = new EnumMap<>(WeatherSoundEffect.class);
    private List<Music> menuMusicList;

    private Music currentMusic;
    private BiomeType currentBiome;
    private float masterVolume = 1.0f;
    private float musicVolume = 0.7f;
    private float soundVolume = 1.0f;
    private boolean musicEnabled = true;
    private boolean soundEnabled = true;
    private BiomeType pendingBiome;
    private boolean isFadingOutMusic = false;
    private float fadeOutMusicTimer = 0f;
    private boolean isFadingInMusic = false;
    private float fadeInMusicTimer = 0f;

    @Autowired
    public AudioManagerImpl(SoundLoader soundLoader) {
        this.soundLoader = soundLoader;
        this.sounds = new EnumMap<>(SoundEffect.class);
        this.biomeMusic = new EnumMap<>(BiomeType.class);
        this.customSounds = new ConcurrentHashMap<>();
        this.ambientSounds = new EnumMap<>(AmbientSoundType.class);
        this.activeAmbientLoops = new EnumMap<>(AmbientSoundType.class);

        initializeAmbientSounds();
        initializeWeatherSounds();
        initializeAudio();
    }

    private void initializeWeatherSounds() {
        for (WeatherSoundEffect effect : WeatherSoundEffect.values()) {
            Sound sound = soundLoader.loadSound(effect.getPath());
            if (sound == null) {
                GameLogger.error("Failed to load weather sound: " + effect.getPath());
            } else {
                weatherSounds.put(effect, sound);
            }
        }
    }

    private void initializeAmbientSounds() {
        for (AmbientSoundType type : AmbientSoundType.values()) {
            Sound sound = soundLoader.loadSound(type.getPath());
            if (sound != null) {
                ambientSounds.put(type, sound);
                GameLogger.info("Loaded ambient sound: " + type.name());
            } else {
                GameLogger.error("Failed to load ambient sound: " + type.name());
            }
        }
    }

    private void initializeAudio() {
        for (SoundEffect effect : SoundEffect.values()) {
            Sound sound = soundLoader.loadSound(effect.getPath());
            if (sound != null) {
                sounds.put(effect, sound);
            } else {
                GameLogger.error("Failed to load sound: " + effect.getPath());
            }
        }

        menuMusicList = new ArrayList<>();
        loadMenuMusic(Arrays.asList(
            "assets/music/Menu-Music-1.mp3",
            "assets/music/Menu-Music-2.mp3",
            "assets/music/Menu-Music-0.mp3",
            "assets/music/Menu-Music-3.mp3",
            "assets/music/Menu-Music-4.mp3"
        ));
        loadBiomeMusic(BiomeType.RUINS, Arrays.asList("assets/music/Ruins-Biome-0.mp3", "assets/music/Ruins-Biome-1.mp3"));
        loadBiomeMusic(BiomeType.FOREST, Arrays.asList("assets/music/Forest-Biome-0.mp3", "assets/music/Forest-Biome-1.mp3",
            "assets/music/Forest-Biome-2.mp3", "assets/music/Forest-Biome-3.mp3"));
        loadBiomeMusic(BiomeType.SNOW, Arrays.asList("assets/music/Snow-Biome-0.mp3", "assets/music/Snow-Biome-1.mp3", "assets/music/Snow-Biome-2.mp3"));
        loadBiomeMusic(BiomeType.HAUNTED, Arrays.asList("assets/music/Haunted-Biome-0.mp3", "assets/music/Haunted-Biome-1.mp3"));
        loadBiomeMusic(BiomeType.PLAINS, Arrays.asList("assets/music/Plains-Biome-0.mp3", "assets/music/Plains-Biome-1.mp3", "assets/music/Plains-Biome-2.mp3", "assets/music/Plains-Biome-3.mp3", "assets/music/Plains-Biome-4.mp3"));
        loadBiomeMusic(BiomeType.BIG_MOUNTAINS, Arrays.asList("assets/music/Mountain-Biome-1.mp3", "assets/music/Mountain-Biome-0.mp3"));
        loadBiomeMusic(BiomeType.RAIN_FOREST, Arrays.asList("assets/music/RainForest-Biome-0.mp3", "assets/music/RainForest-Biome-1.mp3", "assets/music/RainForest-Biome-2.mp3", "assets/music/RainForest-Biome-3.mp3"));
        loadBiomeMusic(BiomeType.DESERT, Arrays.asList("assets/music/Desert-Biome-0.mp3", "assets/music/Desert-Biome-1.mp3", "assets/music/Desert-Biome-2.mp3", "assets/music/Desert-Biome-3.mp3", "assets/music/Desert-Biome-4.mp3"));

        updateVolumes();
    }

    private void loadMenuMusic(List<String> paths) {
        for (String path : paths) {
            Music music = soundLoader.loadMusic(path);
            if (music != null) {
                music.setVolume(musicVolume * masterVolume);
                menuMusicList.add(music);
            } else {
                GameLogger.error("Failed to load menu music: " + path);
            }
        }
    }

    private void loadBiomeMusic(BiomeType biome, List<String> paths) {
        List<Music> musicList = new ArrayList<>();
        for (String path : paths) {
            Music music = soundLoader.loadMusic(path);
            if (music != null) {
                music.setVolume(musicVolume * masterVolume);
                musicList.add(music);
            } else {
                GameLogger.error("Failed to load music: " + path);
            }
        }
        biomeMusic.put(biome, musicList);
    }

    private void updateVolumes() {
        if (currentMusic != null) {
            currentMusic.setVolume(Math.max(0, musicVolume * masterVolume));
        }
        for (List<Music> musicList : biomeMusic.values()) {
            for (Music music : musicList) {
                music.setVolume(musicVolume * masterVolume);
            }
        }
    }

    private void updateSoundVolumes() {
        for (Map.Entry<WeatherSoundEffect, Long> entry : loopingSoundIds.entrySet()) {
            Sound sound = weatherSounds.get(entry.getKey());
            Long soundId = entry.getValue();
            if (sound != null && soundId != null) {
                sound.setVolume(soundId, soundVolume * masterVolume);
            }
        }
    }

    @Override
    public void playWeatherSound(WeatherSoundEffect effect, float volume, float pitch) {
        if (!soundEnabled) return;
        Sound sound = weatherSounds.get(effect);
        if (sound != null) {
            sound.play(volume * soundVolume * masterVolume, pitch, 0);
        }
    }

    @Override
    public void stopAllAmbientSounds() {
        for (Map.Entry<AmbientSoundType, Long> entry : activeAmbientLoops.entrySet()) {
            Sound sound = ambientSounds.get(entry.getKey());
            if (sound != null) {
                sound.stop(entry.getValue());
            }
        }
        activeAmbientLoops.clear();
    }

    @Override
    public void stopAllWeatherLoops() {
        for (WeatherSoundEffect effect : WeatherSoundEffect.values()) {
            stopWeatherLoop(effect);
        }
    }

    private void stopWeatherLoop(WeatherSoundEffect effect) {
        Sound sound = weatherSounds.get(effect);
        Long id = loopingSoundIds.get(effect);
        if (sound != null && id != null) {
            sound.stop(id);
            loopingSoundIds.remove(effect);
            loopingStartTimes.remove(effect);
            loopingDurations.remove(effect);
        }
    }

    @Override
    public void playSound(SoundEffect effect) {
        if (!soundEnabled) return;
        Sound sound = sounds.get(effect);
        if (sound != null) {
            sound.play(soundVolume * masterVolume);
        }
    }

    @Override
    public void playMenuMusic() {
        if (musicEnabled && (currentMusic == null || !currentMusic.isPlaying())) {
            stopCurrentMusic();
            int index = MathUtils.random(menuMusicList.size() - 1);
            currentMusic = menuMusicList.get(index);
            currentBiome = null;
            currentMusic.setVolume(0f);
            currentMusic.setLooping(false);
            currentMusic.play();
            isFadingInMusic = true;
            fadeInMusicTimer = MUSIC_FADE_DURATION;
            setMusicCompletionListenerForMenu();
        }
    }

    private void setMusicCompletionListenerForMenu() {
        if (currentMusic != null) {
            currentMusic.setOnCompletionListener(music -> playMenuMusic());
        }
    }

    @Override
    public void stopMenuMusic() {
        if (currentMusic != null && menuMusicList.contains(currentMusic)) {
            isFadingOutMusic = true;
            fadeOutMusicTimer = MUSIC_FADE_DURATION;
        }
    }

    @Override
    public float getMusicVolume() {
        return musicVolume;
    }

    @Override
    public void setMusicVolume(float musicVolume) {
        this.musicVolume = musicVolume;
        updateVolumes();
    }

    @Override
    public float getSoundVolume() {
        return soundVolume;
    }

    @Override
    public void setSoundVolume(float soundVolume) {
        this.soundVolume = soundVolume;
        updateSoundVolumes();
    }

    @Override
    public boolean isMusicEnabled() {
        return musicEnabled;
    }

    @Override
    public void setMusicEnabled(boolean musicEnabled) {
        this.musicEnabled = musicEnabled;
        if (currentMusic != null) {
            if (musicEnabled) {
                if (!currentMusic.isPlaying()) currentMusic.play();
                currentMusic.setVolume(musicVolume * masterVolume);
            } else {
                currentMusic.pause();
            }
        }
    }

    @Override
    public boolean isSoundEnabled() {
        return soundEnabled;
    }

    @Override
    public void setSoundEnabled(boolean soundEnabled) {
        this.soundEnabled = soundEnabled;
        if (!soundEnabled) {
            stopAllWeatherLoops();
            stopAllAmbientSounds();
        }
    }

    @Override
    public void updateBiomeMusic(BiomeType newBiome) {
        if (!musicEnabled || (pendingBiome != null && newBiome == pendingBiome)) return;

        if (currentBiome != newBiome) {
            pendingBiome = newBiome;
            GameLogger.info("Pending biome set to: " + pendingBiome);
            if (currentMusic != null && menuMusicList.contains(currentMusic)) {
                isFadingOutMusic = true;
                fadeOutMusicTimer = MUSIC_FADE_DURATION;
            } else if (currentMusic == null || !currentMusic.isPlaying()) {
                startMusicForPendingBiome();
            }
        }
    }

    private void startMusicForPendingBiome() {
        if (pendingBiome != null) {
            List<Music> musicList = biomeMusic.get(pendingBiome);
            if (musicList != null && !musicList.isEmpty()) {
                int index = MathUtils.random(musicList.size() - 1);
                currentMusic = musicList.get(index);
                currentBiome = pendingBiome;
                pendingBiome = null;
                currentMusic.setVolume(0f);
                currentMusic.setLooping(false);
                currentMusic.play();
                GameLogger.info("Started playing music for biome: " + currentBiome);
                setMusicCompletionListener();
                isFadingInMusic = true;
                fadeInMusicTimer = MUSIC_FADE_DURATION;
            } else {
                GameLogger.error("No music found for biome: " + pendingBiome);
                currentMusic = null;
                currentBiome = null;
                pendingBiome = null;
            }
        } else {
            currentMusic = null;
            currentBiome = null;
        }
    }

    private void setMusicCompletionListener() {
        if (currentMusic != null) {
            currentMusic.setOnCompletionListener(music -> {
                if (pendingBiome != null && pendingBiome != currentBiome) {
                    startMusicForPendingBiome();
                } else {
                    pendingBiome = currentBiome;
                    startMusicForPendingBiome();
                }
            });
        }
    }

    private void stopCurrentMusic() {
        if (currentMusic != null) {
            isFadingOutMusic = true;
            fadeOutMusicTimer = MUSIC_FADE_DURATION;
        }
    }

    @Override
    public void update(float delta) {
        if (isFadingInMusic && currentMusic != null) {
            fadeInMusicTimer -= delta;
            float progress = 1 - Math.max(0, fadeInMusicTimer / MUSIC_FADE_DURATION);
            float volume = progress * musicVolume * masterVolume;
            currentMusic.setVolume(volume);

            if (fadeInMusicTimer <= 0) {
                isFadingInMusic = false;
                currentMusic.setVolume(musicVolume * masterVolume);
            }
        }
        if (isFadingOutMusic && currentMusic != null) {
            fadeOutMusicTimer -= delta;
            float volume = Math.max(0, (fadeOutMusicTimer / MUSIC_FADE_DURATION) * musicVolume * masterVolume);
            currentMusic.setVolume(volume);

            if (fadeOutMusicTimer <= 0) {
                currentMusic.stop();
                isFadingOutMusic = false;
                currentMusic = null;
                if (pendingBiome != null) {
                    startMusicForPendingBiome();
                } else if (menuMusicList.contains(null)) {
                    playMenuMusic();
                }
            }
        }
    }

    @Override
    public void dispose() {
        for (Sound sound : sounds.values()) {
            sound.dispose();
        }
        sounds.clear();

        loopingStartTimes.clear();
        loopingDurations.clear();

        for (List<Music> musicList : biomeMusic.values()) {
            for (Music music : musicList) {
                music.dispose();
            }
        }
        biomeMusic.clear();

        for (Sound sound : customSounds.values()) {
            sound.dispose();
        }
        customSounds.clear();

        for (Sound sound : weatherSounds.values()) {
            sound.dispose();
        }

        stopAllAmbientSounds();
        for (Sound sound : ambientSounds.values()) {
            sound.dispose();
        }
        ambientSounds.clear();
        weatherSounds.clear();
        loopingSoundIds.clear();
    }

    public enum AmbientSoundType {
        ;

        private final String path;

        AmbientSoundType(String path) {
            this.path = path;
        }

        public String getPath() {
            return path;
        }
    }

    public enum WeatherSoundEffect {
        LIGHT_RAIN("assets/sounds/weather/rain.ogg"),
        THUNDER("assets/sounds/weather/thunder.ogg"),
        WIND("assets/sounds/weather/wind.ogg"),
        SAND_WIND("assets/sounds/weather/sandwind.ogg");

        private final String path;

        WeatherSoundEffect(String path) {
            this.path = path;
        }

        public String getPath() {
            return path;
        }
    }

    public enum SoundEffect {
        ITEM_PICKUP("assets/sounds/pickup.ogg"),
        MENU_SELECT("assets/sounds/select.ogg"),
        MENU_BACK("assets/sounds/back.ogg"),
        BATTLE_WIN("assets/sounds/battle_win.ogg"),
        CRITICAL_HIT("assets/sounds/critical_hit.ogg"),
        CURSOR_MOVE("assets/sounds/cursor_move.ogg"),
        DAMAGE("assets/sounds/damage.ogg"),
        COLLIDE("assets/sounds/player-bump.ogg"),
        MOVE_SELECT("assets/sounds/move_select.ogg"),
        NOT_EFFECTIVE("assets/sounds/not_effective.ogg"),
        SUPER_EFFECTIVE("assets/sounds/super_effective.ogg"),
        CRAFT("assets/sounds/crafting.ogg"),
        BLOCK_PLACE_0("assets/sounds/block_place_0.ogg"),
        BLOCK_PLACE_1("assets/sounds/block_place_1.ogg"),
        BLOCK_PLACE_2("assets/sounds/block_place_2.ogg"),
        BLOCK_BREAK_WOOD("assets/sounds/break_wood.ogg"),
        TOOL_BREAK("assets/sounds/tool_break.ogg"),
        BLOCK_BREAK_WOOD_HAND("assets/sounds/break_wood_hand.ogg"),
        PUDDLE("assets/sounds/puddle.ogg"),
        CHEST_OPEN("assets/sounds/chest-open.ogg"),
        CHEST_CLOSE("assets/sounds/chest-close.ogg"),
        HOUSE_BUILD("assets/sounds/house_build.ogg");

        private final String path;

        SoundEffect(String path) {
            this.path = path;
        }

        public String getPath() {
            return path;
        }
    }
}
