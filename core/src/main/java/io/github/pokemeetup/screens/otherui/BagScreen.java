package io.github.pokemeetup.screens.otherui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Scaling;
import io.github.pokemeetup.context.GameContext;
import io.github.pokemeetup.system.data.ItemData;
import io.github.pokemeetup.system.gameplay.inventory.Inventory;
import io.github.pokemeetup.system.gameplay.inventory.Item;
import io.github.pokemeetup.system.gameplay.inventory.ItemManager;
import io.github.pokemeetup.utils.textures.TextureManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BagScreen extends Window {

    public interface BagSelectionListener {
        void onItemSelected(ItemData item);
    }

    private final Inventory inventory;
    private final BagSelectionListener listener;
    private Runnable onClose;

    private final Skin skin;
    private final float windowWidth;
    private final float windowHeight;
    private final float baseFontScale;
    private final boolean isCompactMode;
    private final float contentPadding;

    // CRITICAL: Track the actual available width for content
    private final float availableContentWidth;

    // Cache drawables to prevent memory issues
    private final Map<String, Drawable> drawableCache = new HashMap<>();
    private final Map<String, Texture> textureCache = new HashMap<>();

    private static final List<String> HEALING_ITEMS = Arrays.asList(
        "potion", "super_potion", "hyper_potion", "max_potion",
        "elixir", "max_elixir", "ether", "max_ether",
        "full_heal", "antidote", "paralyz_heal", "burn_heal", "ice_heal", "awakening"
    );

    private static final List<String> CATCHING_ITEMS = Arrays.asList(
        "pokeball", "greatball", "ultraball", "masterball", "safariball", "netball", "diveball"
    );

    private static final List<String> BATTLE_ITEMS = Arrays.asList(
        "x_attack", "x_defend", "x_special", "x_speed", "x_accuracy", "dire_hit", "guard_spec"
    );

    private static final List<String> ACTION_ITEMS = Arrays.asList(
        "repel", "super_repel", "max_repel", "escape_rope", "poke_flute"
    );

    public BagScreen(Skin skin, Inventory inventory, BagSelectionListener listener) {
        super("Bag", skin);
        this.skin = skin;
        this.inventory = inventory;
        this.listener = listener;

        float contextWidth;
        float contextHeight;
        boolean inBattle = GameContext.get() != null && GameContext.get().getBattleSystem() != null && GameContext.get().getBattleSystem().isInBattle();

        if (inBattle) {
            Stage battleStage = GameContext.get().getBattleStage();
            contextWidth = battleStage.getViewport().getWorldWidth();
            contextHeight = battleStage.getViewport().getWorldHeight();
        } else {
            contextWidth = Gdx.graphics.getWidth();
            contextHeight = Gdx.graphics.getHeight();
        }

        float screenMargin = 20f;
        float maxUsableWidth = contextWidth - (screenMargin * 2);
        float maxUsableHeight = contextHeight - (screenMargin * 2);

        this.isCompactMode = maxUsableWidth < 600 || maxUsableHeight < 400;

        if (isCompactMode) {
            this.windowWidth = maxUsableWidth;
            this.windowHeight = maxUsableHeight;
            this.contentPadding = 5f;
        } else {
            this.windowWidth = Math.min(maxUsableWidth * 0.9f, 800f);
            this.windowHeight = Math.min(maxUsableHeight * 0.9f, 600f);
            this.contentPadding = 10f;
        }

        this.baseFontScale = isCompactMode ? 0.8f : 1.0f;
        float windowPaddingTotal = contentPadding * 2; // Window's pad()
        float contentWrapperPaddingTotal = contentPadding * 2; // ContentWrapper's pad()
        float scrollBarWidth = 20f; // Approximate scrollbar width
        this.availableContentWidth = windowWidth - windowPaddingTotal - contentWrapperPaddingTotal - scrollBarWidth;

        setSize(windowWidth, windowHeight);
        setPosition((contextWidth - windowWidth) / 2, (contextHeight - windowHeight) / 2);
        setModal(true);
        setMovable(false);
        setResizable(false);

        float topPad = isCompactMode ? 35f : 45f;
        padTop(topPad);
        pad(contentPadding);

        getTitleLabel().setFontScale(this.baseFontScale);

        // Create cached drawables
        initializeDrawableCache();

        buildUI();
    }

    private void initializeDrawableCache() {
        // Create commonly used drawables once
        drawableCache.put("dark_bg", createColoredDrawableInternal(new Color(0.1f, 0.1f, 0.1f, 0.9f)));
        drawableCache.put("tab_bg", createColoredDrawableInternal(new Color(0.15f, 0.15f, 0.15f, 0.9f)));
        drawableCache.put("item_row_bg", createColoredDrawableInternal(new Color(0.2f, 0.2f, 0.2f, 0.6f)));
        drawableCache.put("icon_bg", createColoredDrawableInternal(new Color(0.3f, 0.3f, 0.3f, 0.8f)));

        // Tab button states
        drawableCache.put("tab_up", createColoredDrawableInternal(new Color(0.25f, 0.25f, 0.25f, 0.8f)));
        drawableCache.put("tab_down", createColoredDrawableInternal(new Color(0.35f, 0.35f, 0.35f, 0.9f)));
        drawableCache.put("tab_over", createColoredDrawableInternal(new Color(0.3f, 0.3f, 0.3f, 0.85f)));
        drawableCache.put("tab_checked", createColoredDrawableInternal(new Color(0.4f, 0.5f, 0.7f, 0.9f)));
    }

    private Drawable createColoredDrawableInternal(Color color) {
        Pixmap pixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pixmap.setColor(color);
        pixmap.fill();
        Texture texture = new Texture(pixmap);
        pixmap.dispose();
        return new TextureRegionDrawable(new TextureRegion(texture));
    }

    public void setOnClose(Runnable onClose) {
        this.onClose = onClose;
    }

    @Override
    public boolean remove() {
        if (onClose != null) {
            onClose.run();
        }
        // Dispose cached textures
        for (Texture texture : textureCache.values()) {
            texture.dispose();
        }
        textureCache.clear();
        drawableCache.clear();
        return super.remove();
    }

    private void buildUI() {
        clearChildren();

        Table mainTable = new Table();

        // Create 2x2 grid for category tabs
        Table tabGrid = new Table();
        tabGrid.setBackground(drawableCache.get("tab_bg"));
        tabGrid.pad(contentPadding);

        String[] tabNames;
        String[] tabIcons = {"♥", "⚔", "◉", "★"}; // Unicode symbols as icons

        if (windowWidth < 400) {
            tabNames = new String[]{"Heal", "Battle", "Balls", "Items"};
        } else {
            tabNames = new String[]{"Medicine", "Battle", "Poke Balls", "Items"};
        }

        // *** THE CRITICAL FIX STARTS HERE ***

        // 1. Create a ButtonGroup to manage the tabs
        final ButtonGroup<TextButton> tabGroup = new ButtonGroup<>();

        // Create custom button style for tabs
        TextButton.TextButtonStyle tabStyle = new TextButton.TextButtonStyle(skin.get(TextButton.TextButtonStyle.class));
        tabStyle.up = drawableCache.get("tab_up");
        tabStyle.down = drawableCache.get("tab_down");
        tabStyle.over = drawableCache.get("tab_over");
        tabStyle.checked = drawableCache.get("tab_checked");

        // Content area (defined early to be accessible by listeners)
        final Table contentTable = new Table();
        contentTable.top();
        final ScrollPane scrollPane = new ScrollPane(contentTable, skin);
        scrollPane.setFadeScrollBars(true);
        scrollPane.setScrollingDisabled(true, false);
        scrollPane.setOverscroll(false, false);


        String[] sections = {"healing", "battle", "catching", "action"};
        for (int i = 0; i < sections.length; i++) {
            // Create tab with icon and text
            Table tabContent = new Table();
            Label iconLabel = new Label(tabIcons[i], skin);
            iconLabel.setFontScale(baseFontScale * 1.5f);
            Label textLabel = new Label(tabNames[i], skin);
            textLabel.setFontScale(baseFontScale * 0.85f);
            tabContent.add(iconLabel).padRight(5);
            tabContent.add(textLabel);

            TextButton tabButton = new TextButton("", tabStyle);
            tabButton.clearChildren();
            tabButton.add(tabContent).expand().center();
            tabButton.pad(10);

            final int index = i;
            tabButton.addListener(new ChangeListener() {
                @Override
                public void changed(ChangeEvent event, Actor actor) {
                    // The ButtonGroup ensures this only fires when the checked state *changes*.
                    // This check is a secondary safeguard.
                    if (((TextButton)actor).isChecked()) {
                        populateSection(contentTable, sections[index]);
                        scrollPane.setScrollY(0);
                    }
                }
            });

            tabGroup.add(tabButton); // Add the button to the group

            // Add to grid - 2 columns
            tabGrid.add(tabButton).expandX().fillX().height(50).pad(3);
            if (i % 2 == 1) {
                tabGrid.row();
            }
        }

        // The ButtonGroup needs these settings to function as radio buttons
        tabGroup.setMaxCheckCount(1);
        tabGroup.setMinCheckCount(1);
        tabGroup.setUncheckLast(true);

        // Manually set the initial checked state ONCE after all buttons are created.
        tabGroup.getButtons().get(0).setChecked(true);

        // *** THE CRITICAL FIX ENDS HERE ***


        mainTable.add(tabGrid).expandX().fillX().padBottom(contentPadding).row();

        // Content area wrapper
        Table contentWrapper = new Table();
        contentWrapper.setBackground(drawableCache.get("dark_bg"));
        contentWrapper.pad(contentPadding);

        contentWrapper.add(scrollPane).expand().fill();
        mainTable.add(contentWrapper).expand().fill().padTop(contentPadding * 0.5f).row();

        TextButton closeButton = new TextButton("Close", skin);
        closeButton.getLabel().setFontScale(baseFontScale);
        float closePad = isCompactMode ? 6f : 8f;
        closeButton.pad(closePad, closePad * 2.5f, closePad, closePad * 2.5f);
        mainTable.add(closeButton).padTop(contentPadding);

        add(mainTable).expand().fill();

        // This listener is now safe because the event loop is broken.
        closeButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                remove();
            }
        });
    }

    private void populateSection(Table contentTable, String section) {
        contentTable.clear();
        contentTable.top();
        contentTable.defaults().padBottom(contentPadding * 0.5f);

        List<ItemData> items = inventory.getAllItems();
        List<ItemData> filtered = new ArrayList<>();

        for (ItemData item : items) {
            if (item == null || item.getCount() <= 0) continue;
            String normalizedId = item.getItemId().toLowerCase().replace(" ", "_");

            switch (section) {
                case "healing":
                    if (HEALING_ITEMS.contains(normalizedId)) filtered.add(item);
                    break;
                case "battle":
                    if (BATTLE_ITEMS.contains(normalizedId)) filtered.add(item);
                    break;
                case "catching":
                    if (CATCHING_ITEMS.contains(normalizedId)) filtered.add(item);
                    break;
                case "action":
                    if (ACTION_ITEMS.contains(normalizedId)) filtered.add(item);
                    break;
            }
        }

        if (filtered.isEmpty()) {
            Label emptyLabel = new Label("No items in this pocket.", skin);
            emptyLabel.setAlignment(Align.center);
            emptyLabel.setFontScale(baseFontScale * 0.9f);
            emptyLabel.setColor(Color.GRAY);
            contentTable.add(emptyLabel).expand().fill().center();
        } else {
            for (final ItemData item : filtered) {
                Table itemRow = createItemRow(item);
                // CRITICAL: Ensure the row width doesn't exceed available space
                contentTable.add(itemRow).expandX().fillX().row();
            }
        }
    }

// In: src/main/java/io/github/pokemeetup/screens/otherui/BagScreen.java

    private Table createItemRow(final ItemData item) {
        Table itemRow = new Table();
        itemRow.setBackground(drawableCache.get("item_row_bg"));
        float rowPad = isCompactMode ? 5f : 10f;
        itemRow.pad(rowPad);
        itemRow.defaults().top(); // Align all children vertically to the top.

        // --- Column 1: Icon (Fixed Size) ---
        float iconSize = isCompactMode ? 32f : 40f;
        Table iconBg = new Table();
        iconBg.setBackground(drawableCache.get("icon_bg"));
        Image itemIcon = createItemIcon(item, iconSize);
        iconBg.add(itemIcon).size(iconSize * 0.8f).center();
        itemRow.add(iconBg).size(iconSize).padRight(rowPad);

        // --- Column 2: Information (Expanding) ---
        Table infoColumn = new Table();
        infoColumn.left();

        String itemName = formatItemName(item.getItemId());
        Label nameLabel = new Label(itemName, skin);
        nameLabel.setFontScale(baseFontScale);
        nameLabel.setEllipsis(true);
        infoColumn.add(nameLabel).left().row();

        if (!isCompactMode || windowWidth > 450) {
            String desc = getShortDescription(item.getItemId());
            Label descLabel = new Label(desc, skin);
            descLabel.setFontScale(baseFontScale * 0.8f);
            descLabel.setColor(Color.LIGHT_GRAY);
            descLabel.setWrap(true);
            infoColumn.add(descLabel).expandX().fillX().left().padTop(2);
        }

        // This is the only cell that expands, giving the descLabel a stable width.
        itemRow.add(infoColumn).expandX().fillX();

        // --- Column 3: Count (Preferred Size) ---
        Label countLabel = new Label("x" + item.getCount(), skin);
        countLabel.setFontScale(baseFontScale * 0.9f);
        itemRow.add(countLabel).padLeft(rowPad).padRight(rowPad);

        // --- Column 4: Button (Preferred Size) ---
        TextButton useButton = new TextButton("Use", skin);

        // *** THE CRITICAL FIX ***
        // By disabling wrapping on the button's label, we give the button a
        // stable preferred width, which breaks the layout recursion loop.
        useButton.getLabel().setWrap(false);

        useButton.getLabel().setFontScale(baseFontScale * 0.9f);
        itemRow.add(useButton); // Add the button. It will take its preferred width.

        useButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                if (listener != null) {
                    listener.onItemSelected(item);
                }
                remove();
            }
        });

        return itemRow;
    }
    private Image createItemIcon(ItemData item, float iconSize) {
        Image itemIcon = null;

        // First try to get from ItemManager
        if (ItemManager.isInitialized()) {
            Item itemTemplate = ItemManager.getItem(item.getItemId());
            if (itemTemplate != null && itemTemplate.getIcon() != null) {
                itemIcon = new Image(itemTemplate.getIcon());
                itemIcon.setScaling(Scaling.fit);
                return itemIcon;
            }
        }

        // If that fails, try TextureManager directly
        if (TextureManager.items != null) {
            String itemKey = item.getItemId().toLowerCase() + "_item";
            TextureRegion region = TextureManager.items.findRegion(itemKey);
            if (region == null) {
                region = TextureManager.items.findRegion(item.getItemId().toLowerCase());
            }
            if (region != null) {
                itemIcon = new Image(region);
                itemIcon.setScaling(Scaling.fit);
                return itemIcon;
            }
        }

        // Create placeholder icon - cache these to avoid memory issues
        String cacheKey = "placeholder_" + getItemType(item.getItemId());
        if (textureCache.containsKey(cacheKey)) {
            return new Image(textureCache.get(cacheKey));
        }

        // Create new placeholder texture
        Pixmap pixmap = new Pixmap(24, 24, Pixmap.Format.RGBA8888);
        Color color = getItemColor(item.getItemId());
        pixmap.setColor(color);
        pixmap.fill();
        Texture texture = new Texture(pixmap);
        pixmap.dispose();

        textureCache.put(cacheKey, texture);
        return new Image(texture);
    }

    private String getItemType(String itemId) {
        String lower = itemId.toLowerCase();
        if (lower.contains("potion")) return "potion";
        if (lower.contains("ball")) return "ball";
        if (lower.contains("elixir") || lower.contains("ether")) return "pp";
        if (lower.contains("heal")) return "heal";
        return "default";
    }

    private Color getItemColor(String itemId) {
        String lower = itemId.toLowerCase();
        if (lower.contains("potion")) return new Color(0.8f, 0.2f, 0.8f, 1f);
        if (lower.contains("ball")) return new Color(1f, 0.2f, 0.2f, 1f);
        if (lower.contains("elixir") || lower.contains("ether")) return new Color(0.2f, 0.8f, 0.8f, 1f);
        if (lower.contains("heal")) return new Color(1f, 1f, 0.2f, 1f);
        return new Color(0.5f, 0.5f, 0.5f, 1f);
    }

    private String shortenItemName(String name) {
        switch (name) {
            case "Super Potion": return "S.Potion";
            case "Hyper Potion": return "H.Potion";
            case "Max Potion": return "M.Potion";
            case "Max Elixir": return "M.Elixir";
            case "Max Ether": return "M.Ether";
            case "Great Ball": return "G.Ball";
            case "Ultra Ball": return "U.Ball";
            case "Master Ball": return "M.Ball";
            case "Full Heal": return "F.Heal";
            case "Paralyz Heal": return "P.Heal";
            case "Burn Heal": return "B.Heal";
            case "Ice Heal": return "I.Heal";
            case "Super Repel": return "S.Repel";
            case "Max Repel": return "M.Repel";
            case "Escape Rope": return "E.Rope";
            case "Poke Flute": return "P.Flute";
            case "Safari Ball": return "S.Ball";
            case "Net Ball": return "N.Ball";
            case "Dive Ball": return "D.Ball";
            case "Guard Spec.": return "G.Spec";
            default: return name;
        }
    }

    private String getShortDescription(String itemId) {
        return getItemDescription(itemId);
    }

    private String formatItemName(String itemId) {
        switch (itemId.toLowerCase()) {
            case "potion": return "Potion";
            case "super_potion": return "Super Potion";
            case "hyper_potion": return "Hyper Potion";
            case "max_potion": return "Max Potion";
            case "elixir": return "Elixir";
            case "max_elixir": return "Max Elixir";
            case "ether": return "Ether";
            case "max_ether": return "Max Ether";
            case "pokeball": return "Poke Ball";
            case "greatball": return "Great Ball";
            case "ultraball": return "Ultra Ball";
            case "masterball": return "Master Ball";
            case "full_heal": return "Full Heal";
            case "antidote": return "Antidote";
            case "paralyz_heal": return "Paralyz Heal";
            case "burn_heal": return "Burn Heal";
            case "ice_heal": return "Ice Heal";
            case "awakening": return "Awakening";
            case "x_attack": return "X Attack";
            case "x_defend": return "X Defend";
            case "x_special": return "X Special";
            case "x_speed": return "X Speed";
            case "x_accuracy": return "X Accuracy";
            case "dire_hit": return "Dire Hit";
            case "guard_spec": return "Guard Spec.";
            case "repel": return "Repel";
            case "super_repel": return "Super Repel";
            case "max_repel": return "Max Repel";
            case "escape_rope": return "Escape Rope";
            case "poke_flute": return "Poke Flute";
            case "safariball": return "Safari Ball";
            case "netball": return "Net Ball";
            case "diveball": return "Dive Ball";
            default:
                String[] words = itemId.replace("_", " ").split(" ");
                StringBuilder formatted = new StringBuilder();
                for (String word : words) {
                    if (word.length() > 0) {
                        formatted.append(Character.toUpperCase(word.charAt(0)));
                        if (word.length() > 1) {
                            formatted.append(word.substring(1).toLowerCase());
                        }
                        formatted.append(" ");
                    }
                }
                return formatted.toString().trim();
        }
    }

    private String getItemDescription(String itemId) {
        switch (itemId.toLowerCase()) {
            case "potion": return "Restores 20 HP to a Pokemon.";
            case "super_potion": return "Restores 50 HP to a Pokemon.";
            case "hyper_potion": return "Restores 200 HP to a Pokemon.";
            case "max_potion": return "Fully restores a Pokemon's HP.";
            case "elixir": return "Restores 10 PP to all moves.";
            case "max_elixir": return "Fully restores PP to all moves.";
            case "ether": return "Restores 10 PP to one move.";
            case "max_ether": return "Fully restores PP to one move.";
            case "pokeball": return "A device for catching wild Pokemon.";
            case "greatball": return "A good Ball with higher catch rate.";
            case "ultraball": return "An ultra-high performance Ball.";
            case "masterball": return "The best Ball that never fails.";
            case "full_heal": return "Heals all status conditions.";
            case "antidote": return "Cures a poisoned Pokemon.";
            case "paralyz_heal": return "Cures a paralyzed Pokemon.";
            case "burn_heal": return "Cures a burned Pokemon.";
            case "ice_heal": return "Defrosts a frozen Pokemon.";
            case "awakening": return "Awakens a sleeping Pokemon.";
            case "x_attack": return "Raises Attack stat in battle.";
            case "x_defend": return "Raises Defense stat in battle.";
            case "x_special": return "Raises Sp. Attack stat in battle.";
            case "x_speed": return "Raises Speed stat in battle.";
            case "x_accuracy": return "Raises Accuracy in battle.";
            case "dire_hit": return "Raises critical-hit ratio.";
            case "guard_spec": return "Prevents stat reduction.";
            case "repel": return "Repels weak wild Pokemon for 100 steps.";
            case "super_repel": return "Repels weak wild Pokemon for 200 steps.";
            case "max_repel": return "Repels weak wild Pokemon for 250 steps.";
            case "escape_rope": return "Use to escape instantly from caves.";
            case "poke_flute": return "Awakens sleeping Pokemon.";
            case "safariball": return "A special Ball for the Safari Zone.";
            case "netball": return "Works well on Water and Bug types.";
            case "diveball": return "Works well on Pokemon underwater.";
            default: return "A useful item for trainers.";
        }
    }
}
