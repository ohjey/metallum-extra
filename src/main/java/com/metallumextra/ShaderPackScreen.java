package com.metallumextra;

import com.metallumextra.shader.Shaders;
import com.metallumextra.shader.pack.PackManager;
import com.metallumextra.shader.pack.PackManager.Entry;
import com.metallumextra.shader.pack.ZipPack;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import org.lwjgl.glfw.GLFW;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * The shader pack menu, laid out like the shader screen players know from OptiFine: a title, one dark list of plain
 * rows (OFF first, then the packs, the chosen row boxed in white), a footer line, and Shaders Folder, Done and Shader Options...
 * along the bottom, with Shader Options... where OptiFine has it. Pack settings are not shown.
 * <p>
 * Clicking a row only chooses it. The middle button reads Done until something chosen has not been applied, then
 * Apply; it applies and stays open, and reads Done again. Done closes the menu; Escape closes it and drops
 * what was not applied. The folder is read again every time the menu opens, so there is nothing to reload.
 */
public final class ShaderPackScreen extends Screen {
    /** The id of the OFF row. */
    private static final String OFF_ID = "off";
    private static final int TOP = 32;
    private static final int ROW = 20;
    private static final int IN_USE = 0xFFFFF263;
    private static final int ERROR = 0xFFFF5555;

    /** What the hint under the list says shaders do to your game, one after the other. */
    private static final String[] HINT_VERBS = {"beautify", "prettify", "glamorize", "embellish", "enhance", "transform", "elevate", "dazzle"};

    private final @Nullable Screen parent;

    private List<Entry> shown;
    /** The row chosen in the menu: {@link #OFF_ID} or a pack's id. */
    private String chosen;
    /** A row that was clicked but cannot be used, so its reason can be shown. */
    private @Nullable String refused;
    /** The pack last applied from this menu; if it then breaks, the choice moves to what took over. */
    private @Nullable String appliedId;

    /** What the last drop did, shown in the footer. */
    private @Nullable String notice;
    private boolean noticeIsError;

    private PackList list;
    private Button done;
    /** Greyed out while OFF is the chosen row. */
    private Button options;

    public ShaderPackScreen(final @Nullable Screen parent) {
        super(Component.literal("Shaders"));
        this.parent = parent;
        PackManager.rescan();
        this.shown = PackManager.entries();
        this.chosen = inUse();
    }

    /** What is in use now, in the same terms as {@link #chosen}. */
    private static String inUse() {
        return ExtraConfig.get().shadersEnabled ? PackManager.activeId() : OFF_ID;
    }

    private boolean changed() {
        return !chosen.equals(inUse());
    }

    @Override
    protected void init() {
        list = addRenderableWidget(new PackList(minecraft));
        fillList();

        int bottom = height - 27;
        addRenderableWidget(Button.builder(Component.literal("Shaders Folder"), button -> openFolder())
                .bounds(width / 2 - 154, bottom, 100, ROW).build());
        done = addRenderableWidget(Button.builder(doneLabel(), button -> {
            if (changed()) {
                apply();
            } else {
                onClose();
            }
        }).bounds(width / 2 - 50, bottom, 100, ROW).build());
        options = addRenderableWidget(Button.builder(Component.literal("Shader Options..."), button -> minecraft.gui.setScreen(new ShaderOptionsScreen(this)))
                .bounds(width / 2 + 54, bottom, 100, ROW).build());
        options.active = !chosen.equals(OFF_ID);
    }

    private Component doneLabel() {
        return changed() ? Component.literal("Apply") : CommonComponents.GUI_DONE;
    }

    private void fillList() {
        ArrayList<ListEntry> rows = new ArrayList<>();
        rows.add(new ListEntry(OFF_ID, "OFF", null));
        for (Entry pack : shown) {
            rows.add(new ListEntry(pack.id(), pack.id().equals(PackManager.BUILTIN_ID) ? "(" + pack.name() + ")" : pack.name(), pack));
        }
        list.replaceEntries(rows);
        for (ListEntry row : rows) {
            if (row.id.equals(chosen)) list.setSelected(row);
        }
        list.setScrollAmount(0);
    }

    /** Puts what is chosen into use. A pack changes at the start of the next frame. */
    private void apply() {
        if (chosen.equals(OFF_ID)) {
            // Switching off keeps the chosen pack for when shaders come back.
            if (ExtraConfig.get().shadersEnabled) ExtraConfig.get().setShadersEnabled(false);
        } else {
            Entry entry = PackManager.find(chosen);
            if (entry != null && entry.selectable()) {
                PackManager.select(chosen);
                appliedId = chosen;
                if (!ExtraConfig.get().shadersEnabled) ExtraConfig.get().setShadersEnabled(true);
            }
        }
        done.setMessage(doneLabel());
    }

    private void openFolder() {
        try {
            Files.createDirectories(PackManager.folder());
            Util.getPlatform().openPath(PackManager.folder());
        } catch (IOException e) {
            MetallumExtra.LOGGER.warn("[Metallum Extra] Could not open {}", PackManager.folder(), e);
        }
    }

    @Override
    public void tick() {
        // A pack that broke after it was applied is marked in the list, and the choice follows what took over.
        List<Entry> now = PackManager.entries();
        if (now != shown) {
            shown = now;
            Entry applied = appliedId == null ? null : PackManager.find(appliedId);
            if (applied != null && applied.error() != null && applied.id().equals(chosen)) {
                chosen = PackManager.activeId();
            }
            fillList();
        }
        if (done != null) done.setMessage(doneLabel());
        if (options != null) options.active = !chosen.equals(OFF_ID);
    }

    @Override
    public void extractRenderState(final GuiGraphicsExtractor graphics, final int mouseX, final int mouseY, final float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        graphics.centeredText(font, title, width / 2, 15, -1);

        // The footer line: why a row cannot be used, what went wrong earlier, or what to do.
        Entry entry = PackManager.find(refused != null ? refused : chosen);
        String problem = PackManager.problem();
        String unavailable = Shaders.unavailableReason();
        Component footer;
        if (notice != null) {
            footer = Component.literal(notice).withColor(noticeIsError ? ERROR : 0xFF55FF55);
        } else if (unavailable != null) {
            footer = Component.literal(unavailable).withColor(ERROR);
        } else if (entry != null && entry.error() != null) {
            footer = Component.literal(entry.error()).withColor(ERROR);
        } else if (problem != null) {
            footer = Component.literal(problem).withColor(ERROR);
        } else {
            // The hint, with its verb changing every few seconds.
            String verb = HINT_VERBS[(int) (System.currentTimeMillis() / 2500 % HINT_VERBS.length)];
            footer = Component.literal("Drag and drop shaders to " + verb + " your game!").withStyle(ChatFormatting.GRAY);
        }
        String text = footer.getString();
        if (font.width(text) > width - 20) {
            footer = Component.literal(font.plainSubstrByWidth(text, width - 30) + "...").withStyle(footer.getStyle());
        }
        graphics.centeredText(font, footer, width / 2, height - 57, -1);

        // The Mac's specs.
        MacSpecs.Specs specs = MacSpecs.get();
        graphics.centeredText(font, Component.literal(specs == null ? "Reading this Mac's specs..." : specs.line()), width / 2, height - 43, 0xFF808080);
    }

    /**
     * Escape closes this menu when the key is let go, not when it is pressed. Sodium's video settings, which this menu
     * is often opened from, close on the release of Escape; if this menu closed on the press, Sodium's screen would
     * come back just in time to receive the same Escape's release and close too, so one tap would leave both.
     */
    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public boolean keyReleased(final KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        return super.keyReleased(event);
    }

    @Override
    public boolean keyPressed(final KeyEvent event) {
        // Taken here, acted on at the release (above).
        return event.key() == GLFW.GLFW_KEY_ESCAPE || super.keyPressed(event);
    }

    /**
     * Shader packs dropped onto the window are copied into the shaderpacks folder and listed straight away. Only ZIPs
     * that have a {@code pack.json} are taken, and a file is never overwritten.
     */
    @Override
    public void onFilesDrop(final List<Path> files) {
        List<String> added = new ArrayList<>();
        List<String> refused = new ArrayList<>();
        for (Path file : files) {
            String name = file.getFileName().toString();
            if (!name.toLowerCase(java.util.Locale.ROOT).endsWith(".zip") || !ZipPack.isPack(file)) {
                refused.add(name + " is not a shader pack");
                continue;
            }
            Path target = PackManager.folder().resolve(name);
            if (Files.exists(target)) {
                refused.add(name + " is already in the folder");
                continue;
            }
            try {
                Files.createDirectories(PackManager.folder());
                Files.copy(file, target);
                added.add(name);
            } catch (IOException e) {
                MetallumExtra.LOGGER.warn("[Metallum Extra] Could not copy {} to {}", file, target, e);
                refused.add(name + " could not be copied");
            }
        }
        PackManager.rescan();
        shown = PackManager.entries();
        if (!added.isEmpty()) {
            Entry last = PackManager.find(added.get(added.size() - 1));
            if (last != null && last.selectable()) {
                chosen = last.id();
                refused.clear();
            }
        }
        fillList();
        if (options != null) options.active = !chosen.equals(OFF_ID);
        notice = !refused.isEmpty() ? String.join(". ", refused) : "Added " + String.join(", ", added);
        noticeIsError = !refused.isEmpty();
    }

    @Override
    public boolean mouseClicked(final MouseButtonEvent event, final boolean doubleClick) {
        notice = null;
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(parent);
    }

    private final class PackList extends ObjectSelectionList<ListEntry> {
        PackList(final Minecraft minecraft) {
            super(minecraft, ShaderPackScreen.this.width - 20, ShaderPackScreen.this.height - TOP - 67, TOP, ROW);
            setX(10);
        }

        @Override
        public int getRowWidth() {
            return getWidth() - 16;
        }

        /** A dark panel instead of the menu background. */
        @Override
        protected void extractListBackground(final GuiGraphicsExtractor graphics) {
            graphics.fill(getX(), getY(), getRight(), getBottom(), 0x90000000);
        }

        @Override
        protected void extractListSeparators(final GuiGraphicsExtractor graphics) {
        }

        /** The chosen row is boxed in white. */
        @Override
        protected void extractSelection(final GuiGraphicsExtractor graphics, final ListEntry entry, final int color) {
            graphics.fill(entry.getX(), entry.getY(), entry.getX() + entry.getWidth(), entry.getY() + ROW, 0xFFFFFFFF);
            graphics.fill(entry.getX() + 1, entry.getY() + 1, entry.getX() + entry.getWidth() - 1, entry.getY() + ROW - 1, 0xFF000000);
        }
    }

    private final class ListEntry extends ObjectSelectionList.Entry<ListEntry> {
        private final String id;
        private final String label;
        private final @Nullable Entry pack;

        ListEntry(final String id, final String label, final @Nullable Entry pack) {
            this.id = id;
            this.label = label;
            this.pack = pack;
        }

        @Override
        public void extractContent(final GuiGraphicsExtractor graphics, final int mouseX, final int mouseY, final boolean hovered, final float partialTick) {
            boolean broken = pack != null && pack.error() != null;
            int color = broken ? ERROR : id.equals(inUse()) ? IN_USE : hovered ? -1 : 0xFFE0E0E0;
            String fitted = font.width(label) > getWidth() - 12 ? font.plainSubstrByWidth(label, getWidth() - 24) + "..." : label;
            graphics.centeredText(font, fitted, getX() + getWidth() / 2, getY() + (ROW - 8) / 2, color);
        }

        @Override
        public boolean mouseClicked(final MouseButtonEvent event, final boolean doubleClick) {
            if (pack != null && !pack.selectable()) {
                // Cannot be used, but its reason is shown in the footer.
                refused = id;
                return true;
            }
            refused = null;
            chosen = id;
            list.setSelected(this);
            done.setMessage(doneLabel());
            if (doubleClick) apply();
            return true;
        }

        @Override
        public Component getNarration() {
            return Component.literal(label);
        }
    }
}
