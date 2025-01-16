package io.github.pokemeetup.blocks;

import com.badlogic.gdx.graphics.g2d.TextureRegion;

import java.util.HashMap;
import java.util.Map;

public class BuildingData {
    private final String id;
    private final String name;
    private final Map<String, Integer> requirements;
    private final BuildingTemplate template;
    private TextureRegion previewTexture;

    public void setPreviewTexture(TextureRegion texture) {
        this.previewTexture = texture;
    }

    public TextureRegion getPreviewTexture() {
        return previewTexture;
    }
    public BuildingData(String id, String name, BuildingTemplate template) {
        this.id = id;
        this.name = name;
        this.template = template;
        this.requirements = new HashMap<>();
    }

    public void addRequirement(String itemId, int amount) {
        requirements.put(itemId, amount);
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public Map<String, Integer> getRequirements() { return requirements; }
    public BuildingTemplate getTemplate() { return template; }

    public static class BuildingSlot {
        public BuildingData buildingData;
        public boolean isLocked;

        public BuildingSlot(BuildingData data, boolean locked) {
            this.buildingData = data;
            this.isLocked = locked;
        }
    }
}
