package io.github.pokemeetup.audio;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.math.MathUtils;
import io.github.pokemeetup.system.gameplay.overworld.biomes.BiomeType;
import io.github.pokemeetup.utils.GameLogger;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class AudioManager {
    private static AudioManager instance;
    private final Map<WeatherSoundEffect, Sound> weatherSounds = new EnumMap<>(WeatherSoundEffect.class);
    private final Map<WeatherSoundEffect, Long> loopingSoundIds = new EnumMap<>(WeatherSoundEffect.class);
    private final Map<SoundEffect, Sound> sounds;
    private final Map<BiomeType, List<Music>> biomeMusic;

    private final Map<String, Sound> customSounds;
    private final float MUSIC_FADE_DURATION = 2.0f;
    private final float FADE_OUT_DURATION = 2f;
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
    private float fadeOutTimer = 0f;
    private boolean soundEnabled = true;
    private float musicFadeTimer = 0;
    private Music nextMusic;
    private boolean isFadingOut = false;
    private BiomeType pendingBiome; // New field to track the latest biome
    private float ambientVolume = 0.5f;
    private boolean isFadingOutMusic = false;
    private float fadeOutMusicTimer = 0f;
    private boolean isFadingInMusic = false;
    private float fadeInMusicTimer = 0f;

    private AudioManager() {
        sounds = new EnumMap<>(SoundEffect.class);
        biomeMusic = new EnumMap<>(BiomeType.class);
        customSounds = new ConcurrentHashMap<>();
        this.ambientSounds = new EnumMap<>(AmbientSoundType.class);
        this.activeAmbientLoops = new EnumMap<>(AmbientSoundType.class);
        initializeAmbientSounds();
        initializeWeatherSounds();
        initializeAudio();
    }

    public static AudioManager getInstance() {
        if (instance == null) {
            instance = new AudioManager();
        }
        return instance;
    }

    public static void setInstance(AudioManager instance) {
        AudioManager.instance = instance;
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

    private void initializeWeatherSounds() {
        for (WeatherSoundEffect effect : WeatherSoundEffect.values()) {
            try {
                Sound sound = Gdx.audio.newSound(Gdx.files.internal(effect.getPath()));
                weatherSounds.put(effect, sound);
            } catch (Exception e) {
                GameLogger.error("Failed to load weather sound: " + effect.getPath());
            }
        }
    }

    private void initializeAmbientSounds() {
        for (AmbientSoundType type : AmbientSoundType.values()) {
            try {
                Sound sound = Gdx.audio.newSound(Gdx.files.internal(type.getPath()));
                ambientSounds.put(type, sound);
                GameLogger.info("Loaded ambient sound: " + type.name());
            } catch (Exception e) {
                GameLogger.error("Failed to load ambient sound: " + type.name() + " - " + e.getMessage());
            }
        }
    }

    public void stopAllAmbientSounds() {
        for (Map.Entry<AmbientSoundType, Long> entry : activeAmbientLoops.entrySet()) {
            Sound sound = ambientSounds.get(entry.getKey());
            if (sound != null) {
                sound.stop(entry.getValue());
            }
        }
        activeAmbientLoops.clear();
    }

    public void playWeatherSound(WeatherSoundEffect effect, float volume, float pitch) {
        if (!soundEnabled) return;

        Sound sound = weatherSounds.get(effect);
        if (sound != null) {
            sound.play(volume * soundVolume * masterVolume, pitch, 0);
        }
    }

    public void updateWeatherLoop(WeatherSoundEffect effect, float volume) {
        if (!soundEnabled) {
            stopWeatherLoop(effect);
            return;
        }

        Sound sound = weatherSounds.get(effect);
        if (sound != null) {
            Long currentId = loopingSoundIds.get(effect);

            if (currentId == null || !isPlaying(effect)) {
                // Start new loop
                long id = sound.loop(volume * soundVolume * masterVolume);
                loopingSoundIds.put(effect, id);
                loopingStartTimes.put(effect, System.currentTimeMillis());

                // Store duration based on effect type
                float duration = getEffectDuration(effect);
                loopingDurations.put(effect, duration);
            } else {
                // Update existing loop volume
                sound.setVolume(currentId, volume * soundVolume * masterVolume);
            }
        }
    }

    private float getEffectDuration(WeatherSoundEffect effect) {
        // Define durations for each effect (in seconds)
        switch (effect) {
            case LIGHT_RAIN:
            case WIND:
            case SAND_WIND:
                return 10.0f; // 10-second loop for ambient sounds
            case THUNDER:
                return 3.0f; // 3-second duration for thunder
            default:
                return 5.0f; // Default duration
        }
    }

    public void stopWeatherLoop(WeatherSoundEffect effect) {
        Sound sound = weatherSounds.get(effect);
        Long id = loopingSoundIds.get(effect);
        if (sound != null && id != null) {
            sound.stop(id);
            loopingSoundIds.remove(effect);
            loopingStartTimes.remove(effect);
            loopingDurations.remove(effect);
        }
    }

    public void stopAllWeatherLoops() {
        for (WeatherSoundEffect effect : WeatherSoundEffect.values()) {
            stopWeatherLoop(effect);
        }
    }

    private boolean isPlaying(WeatherSoundEffect effect) {
        Long startTime = loopingStartTimes.get(effect);
        Float duration = loopingDurations.get(effect);
        Long soundId = loopingSoundIds.get(effect);

        if (startTime == null || duration == null || soundId == null) {
            return false;
        }
        long currentTime = System.currentTimeMillis();
        long elapsedTime = currentTime - startTime;

        if (elapsedTime > duration * 1000) { // Convert duration to milliseconds
            loopingSoundIds.remove(effect);
            loopingStartTimes.remove(effect);
            return false;
        }

        return true;
    }

    public void playSound(AudioManager.SoundEffect effect) {
        if (!soundEnabled) return;

        Sound sound = sounds.get(effect);
        if (sound != null) {
            sound.play(soundVolume * masterVolume);
        }
    }

    private void initializeAudio() {
        // Load sound effects
        for (SoundEffect effect : SoundEffect.values()) {
            try {
                Sound sound = Gdx.audio.newSound(Gdx.files.internal(effect.getPath()));
                sounds.put(effect, sound);
            } catch (Exception e) {
                Gdx.app.error("AudioManager", "Failed to load sound: " + effect.getPath());
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
        loadBiomeMusic(BiomeType.RUINS, (Arrays.asList("assets/music/Ruins-Biome-0.mp3", "assets/music/Ruins-Biome-1.mp3")));
        loadBiomeMusic(BiomeType.FOREST, (Arrays.asList("assets/music/Forest-Biome-0.mp3", "assets/music/Forest-Biome-1.mp3", "assets/music/Forest-Biome-2.mp3", "assets/music/Forest-Biome-3.mp3")));
        loadBiomeMusic(BiomeType.SNOW, (Arrays.asList("assets/music/Snow-Biome-0.mp3", "assets/music/Snow-Biome-1.mp3", "assets/music/Snow-Biome-2.mp3")));
        loadBiomeMusic(BiomeType.HAUNTED, (Arrays.asList("assets/music/Haunted-Biome-0.mp3", "assets/music/Haunted-Biome-1.mp3")));
        loadBiomeMusic(BiomeType.PLAINS, (Arrays.asList("assets/music/Plains-Biome-0.mp3", "assets/music/Plains-Biome-1.mp3", "assets/music/Plains-Biome-2.mp3", "assets/music/Plains-Biome-3.mp3", "assets/music/Plains-Biome-4.mp3")));
        loadBiomeMusic(BiomeType.BIG_MOUNTAINS, (Arrays.asList("assets/music/Mountain-Biome-1.mp3", "assets/music/Mountain-Biome-0.mp3")));
        loadBiomeMusic(BiomeType.RAIN_FOREST, (Arrays.asList("assets/music/RainForest-Biome-0.mp3", "assets/music/RainForest-Biome-1.mp3", "assets/music/RainForest-Biome-2.mp3", "assets/music/RainForest-Biome-3.mp3")));
        loadBiomeMusic(BiomeType.DESERT, (Arrays.asList("assets/music/Desert-Biome-0.mp3", "assets/music/Desert-Biome-1.mp3", "assets/music/Desert-Biome-2.mp3", "assets/music/Desert-Biome-3.mp3", "assets/music/Desert-Biome-4.mp3")));

    }

    private void loadMenuMusic(List<String> paths) {
        for (String path : paths) {
            try {
                Music music = Gdx.audio.newMusic(Gdx.files.internal(path));
                music.setVolume(musicVolume * masterVolume);
                menuMusicList.add(music);
            } catch (Exception e) {
                Gdx.app.error("AudioManager", "Failed to load menu music: " + path + ", error: " + e.getMessage(), e);
            }
        }
    }

    private void loadBiomeMusic(BiomeType biome, List<String> paths) {
        List<Music> musicList = new ArrayList<>();
        for (String path : paths) {
            try {
                Music music = Gdx.audio.newMusic(Gdx.files.internal(path));
                music.setVolume(musicVolume * masterVolume);
                musicList.add(music);
            } catch (Exception e) {
                Gdx.app.error("AudioManager", "Failed to load music: " + path + ", error: " + e.getMessage(), e);
            }
        }
        biomeMusic.put(biome, musicList);
    }


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
            currentMusic.setOnCompletionListener(music -> {
                // Play next menu music track
                playMenuMusic();
            });
        }
    }

    public void stopMenuMusic() {
        if (currentMusic != null && menuMusicList.contains(currentMusic)) {
            isFadingOutMusic = true;
            fadeOutMusicTimer = MUSIC_FADE_DURATION;
        }
    }

    public float getMusicVolume() {
        return musicVolume;
    }

    public void setMusicVolume(float musicVolume) {
        this.musicVolume = musicVolume;
        updateVolumes();
    }

    public float getSoundVolume() {
        return soundVolume;
    }

    public void setSoundVolume(float soundVolume) {
        this.soundVolume = soundVolume;
        // Optionally update volumes of looping sounds
        updateSoundVolumes();
    }

    public boolean isMusicEnabled() {
        return musicEnabled;
    }

    public void setMusicEnabled(boolean musicEnabled) {
        this.musicEnabled = musicEnabled;
        if (currentMusic != null) {
            if (musicEnabled) {
                if (!currentMusic.isPlaying()) {
                    currentMusic.play();
                }
                currentMusic.setVolume(musicVolume * masterVolume);
            } else {
                currentMusic.pause();
            }
        }
    }

    public boolean isSoundEnabled() {
        return soundEnabled;
    }

    public void setSoundEnabled(boolean soundEnabled) {
        this.soundEnabled = soundEnabled;
        if (!soundEnabled) {
            // Stop all playing sounds if necessary
            stopAllWeatherLoops();
            stopAllAmbientSounds();
        }
    }

    public void fadeOutMenuMusic() {
        isFadingOut = true;
        fadeOutTimer = FADE_OUT_DURATION;
    }

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
                currentMusic.setVolume(0f); // Start from 0 volume for fade-in
                currentMusic.setLooping(false); // Don't loop so it can end naturally
                currentMusic.play();
                GameLogger.info("Started playing music for biome: " + currentBiome);
                setMusicCompletionListener();
                isFadingInMusic = true; // Flag to start fade-in
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

    private void stopCurrentMusic() {
        if (currentMusic != null) {
            isFadingOutMusic = true;
            fadeOutMusicTimer = MUSIC_FADE_DURATION;
        }
    }


    private void setMusicCompletionListener() {
        if (currentMusic != null) {
            currentMusic.setOnCompletionListener(music -> {
                if (pendingBiome != null && pendingBiome != currentBiome) {
                    // Biome has changed, start music for new biome
                    startMusicForPendingBiome();
                } else {
                    // Biome hasn't changed, pick another random song from currentBiome
                    pendingBiome = currentBiome; // Ensure pendingBiome is set
                    startMusicForPendingBiome();
                }
            });
        }
    }

    public void dispose() {
        for (Sound sound : sounds.values()) {
            sound.dispose();
        }
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
        sounds.clear();
        biomeMusic.clear();
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
