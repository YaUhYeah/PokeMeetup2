    package io.github.pokemeetup.oldsystems;

    import java.util.ArrayList;
    import java.util.List;

    /**
     * Temporary class to represent io.github.pokemeetup.system.data.PlayerData in version 1.
     */
    public class PlayerDataV1 {
        private String username;
        private float x = 0;
        private float y = 0;
        private String direction = "down"; // Default to "down"
        private boolean isMoving = false;
        private boolean wantsToRun = false;
        private List<String> inventoryItems = new ArrayList<>();
        private List<String> hotbarItems = new ArrayList<>();
        private boolean isDirty;

        // Default constructor for Json serialization
        public PlayerDataV1() {}

        // Getters and Setters
        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public float getX() {
            return x;
        }

        public void setX(float x) {
            this.x = x;
        }

        public float getY() {
            return y;
        }

        public void setY(float y) {
            this.y = y;
        }

        public String getDirection() {
            return direction;
        }

        public void setDirection(String direction) {
            this.direction = direction;
        }

        public boolean isMoving() {
            return isMoving;
        }

        public void setMoving(boolean moving) {
            isMoving = moving;
        }

        public boolean isWantsToRun() {
            return wantsToRun;
        }

        public void setWantsToRun(boolean wantsToRun) {
            this.wantsToRun = wantsToRun;
        }

        public List<String> getInventoryItems() {
            return inventoryItems;
        }

        public void setInventoryItems(List<String> inventoryItems) {
            this.inventoryItems = inventoryItems;
        }

        public List<String> getHotbarItems() {
            return hotbarItems;
        }

        public void setHotbarItems(List<String> hotbarItems) {
            this.hotbarItems = hotbarItems;
        }

        public boolean isDirty() {
            return isDirty;
        }

        public void setDirty(boolean dirty) {
            isDirty = dirty;
        }

        // Method to create a copy
        public PlayerDataV1 copy() {
            PlayerDataV1 copy = new PlayerDataV1();
            copy.username = this.username;
            copy.x = this.x;
            copy.y = this.y;
            copy.direction = this.direction;
            copy.isMoving = this.isMoving;
            copy.wantsToRun = this.wantsToRun;
            copy.inventoryItems = new ArrayList<>(this.inventoryItems);
            copy.hotbarItems = new ArrayList<>(this.hotbarItems);
            copy.isDirty = this.isDirty;
            return copy;
        }
    }
