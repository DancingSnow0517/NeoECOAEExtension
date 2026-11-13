package cn.dancingsnow.neoecoae.gui.computation;

import cn.dancingsnow.neoecoae.compat.ae2.keyreuse.ECOKeyFactory;

import appeng.api.stacks.AEKey;
import cn.dancingsnow.neoecoae.gui.theme.NETextures;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.event.HoverTooltips;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import dev.vfyjxf.taffy.style.TaffyPosition;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;

/**
 * One contextual menu (locate, open machine, open provider, in 1.7.10's order) and a chooser for several machines or
 * providers; locating a provider also highlights it in the world.
 */
public final class ComputationItemActionsPopup extends UIElement {
    private static final int PAGE_SIZE = 4;
    private enum Mode { MENU, CHOOSE, MESSAGE }
    private final Consumer<CompoundTag> request;
    private final Consumer<Component> chat;
    private final Consumer<CompoundTag> highlight;
    private final List<CompoundTag> sources = new ArrayList<>();
    private final List<CompoundTag> machines = new ArrayList<>();
    private List<CompoundTag> choices = sources;
    private String chooseAction = "open";
    private AEKey key;
    private CompoundTag context = new CompoundTag();
    private long sequence;
    private boolean loaded;
    private final net.minecraft.core.HolderLookup.Provider registries;
    private Mode mode = Mode.MENU;
    private int page, hovered = -1, pressed = -1;
    private Component message = Component.empty();

    public ComputationItemActionsPopup(net.minecraft.core.HolderLookup.Provider registries, Consumer<CompoundTag> request) {
        this(registries, request, message -> Minecraft.getInstance().gui.getChat().addMessage(message));
    }

    ComputationItemActionsPopup(net.minecraft.core.HolderLookup.Provider registries, Consumer<CompoundTag> request, Consumer<Component> chat) {
        this(registries, request, chat, cn.dancingsnow.neoecoae.client.rendering.ProviderLocationHighlight::show);
    }

    ComputationItemActionsPopup(net.minecraft.core.HolderLookup.Provider registries, Consumer<CompoundTag> request,
            Consumer<Component> chat, Consumer<CompoundTag> highlight) {
        this.registries = registries;
        this.request = request;
        this.chat = chat;
        this.highlight = highlight;
        setId("computation-item-actions");
        setFocusable(true);
        setDisplay(false);
        // Vanilla item models render above the base GUI plane, including their depth buffer writes.
        style(style -> style.zIndex(400));
        setOverflowVisible(true);
        addEventListener(UIEvents.MOUSE_DOWN, event -> {
            pressed = event.button == 0 ? row(event.x, event.y) : -1;
            event.stopImmediatePropagation();
        });
        addEventListener(UIEvents.MOUSE_UP, event -> {
            int row = row(event.x, event.y);
            if (event.button == 0 && row == pressed) activate(row);
            pressed = -1;
            event.stopImmediatePropagation();
        });
        addEventListener(UIEvents.MOUSE_MOVE, event -> hovered = row(event.x, event.y));
        addEventListener(UIEvents.KEY_DOWN, event -> {
            if (event.keyCode == 256) {
                close();
                event.stopImmediatePropagation();
            }
        });
        addEventListener(UIEvents.MOUSE_WHEEL, event -> {
            if (mode == Mode.CHOOSE) turnPage(event.deltaY < 0 ? 1 : -1);
            event.stopImmediatePropagation();
        });
        addEventListener(UIEvents.HOVER_TOOLTIPS, event -> {
            var ui = getModularUI();
            if (ui == null) return;
            int row = row(ui.getLastMouseX(), ui.getLastMouseY());
            if (mode != Mode.MENU || !loaded) return;
            if ((row == 0 || row == 2) && sources.isEmpty()) event.hoverTooltips =
                HoverTooltips.empty().append(Component.translatable("gui.neoecoae.processing.no_pattern"));
            else if (row == 1 && machines.isEmpty()) event.hoverTooltips =
                HoverTooltips.empty().append(Component.translatable("gui.neoecoae.processing.no_machine"));
        });
    }

    void open(ComputationCpuItemEntry item, float x, float y) {
        open(item.key(), x, y);
    }

    public void open(AEKey key, float x, float y) {
        this.key = key;
        sequence++;
        context = new CompoundTag();
        mode = Mode.MENU;
        loaded = false;
        page = 0;
        sources.clear();
        machines.clear();
        choices = sources;
        place(x, y, 138, 74);
        setDisplay(true);
        focus();
        var request = new CompoundTag();
        request.putString("action", "inspect");
        request.putLong("sequence", sequence);
        request.put("key", key.toTagGeneric(registries));
        this.request.accept(request);
    }

    public void accept(CompoundTag response) {
        if (key == null || response.getLong("sequence") != sequence) return;
        AEKey receivedKey = ECOKeyFactory.fromTagGeneric(registries, response.getCompound("key"));
        if (!key.equals(receivedKey)) return;
        context = response.copy();
        loaded = true;
        sources.clear();
        for (var raw : response.getList("sources", Tag.TAG_COMPOUND)) sources.add(((CompoundTag) raw).copy());
        machines.clear();
        for (var raw : response.getList("machines", Tag.TAG_COMPOUND)) machines.add(((CompoundTag) raw).copy());
        if (response.contains("error")) {
            message = Component.translatable(response.getString("error"));
            mode = Mode.MESSAGE;
            dialog();
        } else if (response.getBoolean("close")) close();
    }

    public boolean isOpen() { return key != null; }

    public void close() { key = null; blur(); setDisplay(false); pressed = hovered = -1; }

    private int availableWidth() {
        return getParent() == null ? ComputationCpuStatusPanel.WIDTH : Math.max(1, Math.round(getParent().getSizeWidth()));
    }

    private int availableHeight() {
        return getParent() == null ? ComputationCpuStatusPanel.HEIGHT : Math.max(1, Math.round(getParent().getSizeHeight()));
    }

    private void place(float x, float y, int width, int height) {
        int left = Math.max(0, Math.min(availableWidth() - width, Math.round(x)));
        int top = Math.max(0, Math.min(availableHeight() - height, Math.round(y)));
        layout(layout -> layout.positionType(TaffyPosition.ABSOLUTE).left(left).top(top).width(width).height(height));
    }

    private void dialog() {
        int rows = mode == Mode.MESSAGE ? 1 : Math.min(PAGE_SIZE, Math.max(1, choices.size()));
        place((availableWidth() - 230) / 2F, 36, 230, 24 + rows * 30 + 22);
    }

    private int row(float x, float y) {
        if (!isMouseOverElement(x, y)) return -1;
        float localY = y - getPositionY();
        if (mode == Mode.MENU) {
            if (localY >= 22 && localY < 70) return (int) ((localY - 22) / 16);
            return -1;
        }
        if (localY >= getSizeHeight() - 20) {
            return 100 + Math.min(2, Math.max(0, (int) ((x - getPositionX()) / (getSizeWidth() / 3))));
        }
        int index = (int) ((localY - 24) / 30);
        return localY >= 24 && index >= 0 && index < PAGE_SIZE && page * PAGE_SIZE + index < choices.size() ? index : -1;
    }

    private void activate(int row) {
        if (row < 0) return;
        if (mode == Mode.MENU) {
            if (!loaded) return;
            if (row == 0 && !sources.isEmpty()) showLocations();
            else if (row == 1) choose(machines, "open_machine");
            else if (row == 2) choose(sources, "open");
        } else if (row >= 100) {
            if (row == 100) turnPage(-1);
            else if (row == 101) turnPage(1);
            else close();
        } else if (mode == Mode.CHOOSE) send(chooseAction, choices, page * PAGE_SIZE + row);
    }

    /** One entry opens directly; several open the chooser for that list. */
    private void choose(List<CompoundTag> list, String action) {
        if (list.isEmpty()) return;
        if (list.size() == 1) {
            send(action, list, 0);
            return;
        }
        choices = list;
        chooseAction = action;
        page = 0;
        mode = Mode.CHOOSE;
        dialog();
    }

    private void turnPage(int change) {
        page = Math.max(0, Math.min(Math.max(0, (choices.size() - 1) / PAGE_SIZE), page + change));
    }

    private void showLocations() {
        for (var source : sources) {
            highlight.accept(source);
            Component name = Component.Serializer.fromJson(source.getString("name"), registries);
            BlockPos position = BlockPos.of(source.getLong("position"));
            var coordinates = Component.literal(position.toShortString()).withStyle(style -> style
                .withColor(ChatFormatting.AQUA).withUnderlined(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND,
                    "/tp " + position.getX() + " " + position.getY() + " " + position.getZ())));
            var location = Component.translatable("gui.neoecoae.processing.pattern_location",
                key.getDisplayName(), name == null ? Component.empty() : name,
                source.getString("dimension"), coordinates);
            if (source.contains("side")) location.append(" · " + source.getString("side"));
            chat.accept(location);
        }
        close();
    }

    private void send(String action, List<CompoundTag> list, int source) {
        var tag = new CompoundTag();
        tag.putString("action", action);
        tag.putLong("sequence", sequence);
        tag.put("key", key.toTagGeneric(registries));
        if (context.hasUUID("job")) tag.putUUID("job", context.getUUID("job"));
        if (source >= 0 && source < list.size()) tag.putString("source", list.get(source).getString("id"));
        request.accept(tag);
    }

    @Override public void drawBackgroundAdditional(GUIContext context) {
        float x = getPositionX(), y = getPositionY(), width = getSizeWidth();
        context.graphics.flush();
        context.drawTexture(NETextures.AE2_CONTEXT_BACKGROUND, x, y, width, getSizeHeight());
        context.graphics.flush();
        Component title = key == null ? Component.empty() : key.getDisplayName();
        if (mode == Mode.CHOOSE) title = title.copy().append(" · " + (page + 1) + "/" + Math.max(1, (choices.size() + PAGE_SIZE - 1) / PAGE_SIZE));
        text(context, title, 7, 6, 0x413F54);
        divider(context, 18);
        if (mode == Mode.MENU) {
            row(context, 0, 22, "gui.neoecoae.processing.action.locate", loaded && !sources.isEmpty());
            row(context, 1, 38, "gui.neoecoae.processing.action.open_machine", loaded && !machines.isEmpty());
            row(context, 2, 54, "gui.neoecoae.processing.action.open", loaded && !sources.isEmpty());
        } else {
            if (mode == Mode.MESSAGE) text(context, message, 7, 29, 0xA22D37);
            else for (int index = 0; index < PAGE_SIZE && page * PAGE_SIZE + index < choices.size(); index++) {
                var source = choices.get(page * PAGE_SIZE + index);
                float top = 24 + index * 30;
                if (mode == Mode.CHOOSE && hovered == index) context.graphics.fill(Math.round(x + 4), Math.round(y + top - 2), Math.round(x + width - 4), Math.round(y + top + 28), 0x807B81B8);
                Component name = Component.Serializer.fromJson(source.getString("name"), registries);
                text(context, name == null ? Component.empty() : name, 7, top, 0x413F54);
                text(context, Component.literal(source.getString("dimension")), 7, top + 9, 0x666477);
                text(context, Component.literal(BlockPos.of(source.getLong("position")).toShortString()
                    + (source.contains("side") ? " · " + source.getString("side") : "")), 7, top + 18, 0x413F54);
            }
            divider(context, getSizeHeight() - 23);
            float footer = getSizeHeight() - 16;
            text(context, Component.translatable("gui.neoecoae.processing.previous"), 7, footer, 0x413F54);
            text(context, Component.translatable("gui.neoecoae.processing.next"), width / 3 + 7, footer, 0x413F54);
            text(context, Component.translatable("gui.neoecoae.processing.close"), width * 2 / 3 + 7, footer, 0x413F54);
        }
    }

    private void row(GUIContext context, int row, int top, String label, boolean enabled) {
        if (enabled && hovered == row) context.graphics.fill(Math.round(getPositionX() + 4), Math.round(getPositionY() + top),
            Math.round(getPositionX() + getSizeWidth() - 4), Math.round(getPositionY() + top + 16), 0x807B81B8);
        text(context, Component.translatable(label), 8, top + 4, enabled ? 0x413F54 : 0x8C8B98);
    }

    private void divider(GUIContext context, float top) {
        int x = Math.round(getPositionX() + 5), y = Math.round(getPositionY() + top), right = Math.round(getPositionX() + getSizeWidth() - 5);
        context.graphics.fill(x, y, right, y + 1, 0xFF8B8A9A);
        context.graphics.fill(x, y + 1, right, y + 2, 0xFFE8E7EF);
    }

    private void text(GUIContext context, Component text, float x, float y, int color) {
        var font = Minecraft.getInstance().font;
        var value = font.plainSubstrByWidth(text.getString(), Math.round(getSizeWidth() - x - 7));
        context.graphics.drawString(font, value, Math.round(getPositionX() + x), Math.round(getPositionY() + y), 0xFF000000 | color, false);
    }
}
