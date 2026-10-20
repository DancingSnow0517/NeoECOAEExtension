package cn.dancingsnow.neoecoae.gui.storage;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.api.IECOTier;
import cn.dancingsnow.neoecoae.gui.common.HostPanelTier;
import cn.dancingsnow.neoecoae.gui.common.HostProgressAnimation;
import cn.dancingsnow.neoecoae.gui.common.HostText;
import com.google.gson.JsonObject;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.texture.SpriteTexture;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.event.HoverTooltips;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import com.lowdragmc.lowdraglib2.gui.util.DrawerHelper;
import dev.vfyjxf.taffy.style.TaffyPosition;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Comparator;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.locale.Language;
import net.minecraft.world.item.ItemStack;

/** Dynamic custom nodes for storage_controller.json; each draws at its own resolved position. */
final class StorageHostCharts {
    private static final int TEXT = 0xFF3F3D52;
    private final StoragePanelSync sync;
    private final int pageSize;
    private final TypePanel typePanel;
    private final ItemStack hostItem;
    private final Supplier<IECOTier> panelTier;

    StorageHostCharts(StoragePanelSync sync, int pageSize, ItemStack hostItem, Supplier<IECOTier> panelTier) {
        this.sync = sync;
        this.panelTier = panelTier;
        this.pageSize = pageSize;
        this.typePanel = new TypePanel();
        this.hostItem = hostItem.copyWithCount(1);
    }

    UIElement create(String custom, JsonObject definition) {
        return switch (custom) {
            case "storage_caption" -> new Caption(definition.get("metric").getAsString(),
                definition.has("font_scale") ? definition.get("font_scale").getAsFloat() : 0.75F,
                definition.has("text_align") && definition.get("text_align").getAsString().equals("right"));
            case "storage_tier" -> new PanelElement() {
                @Override public void drawBackgroundAdditional(GUIContext context) {
                    var custom = sync.textures().tier();
                    if (custom != null) {
                        texture(context, StoragePanelTextures.tier(state().tier(), custom));
                    } else if (!HostPanelTier.drawName(context, panelTier.get(), getPositionX(), getPositionY(),
                        getSizeWidth(), getSizeHeight(), TEXT) && (state().formed() || state().tier() > 0)) {
                        texture(context, StoragePanelTextures.tier(state().tier()));
                    }
                }
            };
            case "storage_host_icon" -> new HostIcon();
            case "storage_capacity_bar" -> new CapacityBar();
            case "storage_resource_bar" -> new ResourceBar();
            case "storage_energy_bar" -> new EnergyBar();
            case "storage_type_panel" -> typePanel.configureFonts(definition);
            case "storage_type_scrollbar" -> new TypeScrollbar();
            case "storage_information" -> createInformationIcon();
            case "storage_infinite_status" -> new InfiniteStatus();
            case "storage_infinite_animation" -> new InfiniteAnimation();
            default -> throw new IllegalArgumentException("Unknown storage artwork node: " + custom);
        };
    }

    private StoragePanelState state() { return sync.state(); }

    private UIElement createInformationIcon() {
        SpriteTexture normal = SpriteTexture.of(NeoECOAE.id("textures/gui/widget/information.png"));
        SpriteTexture highlighted = SpriteTexture.of(NeoECOAE.id("textures/gui/widget/information_highlight.png"));
        UIElement information = new UIElement().style(style -> style.backgroundTexture(normal))
            .layout(layout -> layout.positionType(TaffyPosition.ABSOLUTE).left(0).top(3).width(21).height(21));
        information.addEventListener(UIEvents.MOUSE_ENTER, event -> information.style(style -> style.backgroundTexture(highlighted)));
        information.addEventListener(UIEvents.MOUSE_LEAVE, event -> information.style(style -> style.backgroundTexture(normal)));
        information.addEventListener(UIEvents.HOVER_TOOLTIPS, event ->
            event.hoverTooltips = new HoverTooltips(domainTooltip(), null, null, null));
        // Match the crafting host's frame; the JSON owns its placement and only the icon has a tooltip.
        return new UIElement().style(style -> style.backgroundTexture(SpriteTexture.of(
            NeoECOAE.id("textures/gui/common/side_bar/mirrored_button_slot_up.png")))).addChild(information);
    }

    private abstract class PanelElement extends UIElement {
        void texture(GUIContext context, IGuiTexture texture) {
            context.drawTexture(texture, getPositionX(), getPositionY(), getSizeWidth(), getSizeHeight());
        }

        void tooltips(Supplier<List<Component>> lines) {
            addEventListener(UIEvents.HOVER_TOOLTIPS, event ->
                event.hoverTooltips = new HoverTooltips(lines.get(), null, null, null));
        }

        void text(GUIContext context, Component component, float x, float y, float width, float maxScale, int color) {
            text(context, component, x, y, width, maxScale, color, false, false);
        }

        void text(GUIContext context, Component component, float x, float y, float width, float maxScale, int color,
            boolean centerVertically, boolean rightAligned) {
            if (component == null || component.getString().isEmpty() || width <= 0) return;
            Font font = Minecraft.getInstance().font;
            float scale = Math.max(0.5F, Math.min(maxScale, width / Math.max(1, font.width(component))));
            if (centerVertically) y += (getSizeHeight() - font.lineHeight * scale) / 2;
            var value = Language.getInstance().getVisualOrder(
                font.substrByWidth(component, Math.max(1, (int) (width / scale))));
            if (rightAligned) x += Math.max(0, width - font.width(value) * scale);
            var pose = context.graphics.pose();
            pose.pushPose();
            try {
                pose.translate(getPositionX() + x, getPositionY() + y, 0);
                pose.scale(scale, scale, 1);
                context.graphics.drawString(font, value, 0, 0, color, false);
            } finally {
                pose.popPose();
            }
        }
    }

    private final class Caption extends PanelElement {
        private final String metric;
        private final float fontScale;
        private final boolean rightAligned;

        Caption(String metric, float fontScale, boolean rightAligned) {
            this.metric = metric;
            this.fontScale = fontScale;
            this.rightAligned = rightAligned;
            tooltips(() -> switch (metric) {
                case "title" -> domainTooltip();
                case "energy", "energy_stored" -> energyTooltip();
                case "bytes" -> capacityTooltip();
                case "usage" -> capacityTooltip();
                case "resources" -> resourceTooltip();
                case "cells" -> List.of(StorageCellGrid.pageText(state(), pageSize),
                    Component.translatable("gui.neoecoae.storage.ui.scroll_cells"));
                default -> List.of();
            });
        }

        @Override public void drawBackgroundAdditional(GUIContext context) {
            if (metric.equals("energy")) {
                drawEnergyUsage(context);
                return;
            }
            Component caption = switch (metric) {
                case "title" -> Component.translatable("gui.neoecoae.storage.ui.title");
                case "cells" -> StorageCellGrid.pageText(state(), pageSize);
                case "bytes" -> Component.translatable("gui.neoecoae.storage.legacy.graph.total_bytes",
                    capacityValue(state()));
                case "usage" -> state().unbounded() ? Component.empty()
                    : Component.literal(HostText.percent(state().usage()));
                case "energy_stored" -> Component.translatable("gui.neoecoae.storage.ui.energy",
                    HostText.ae2Amount(state().energy()), HostText.ae2Amount(state().maxEnergy()));
                case "energy" -> Component.translatable("gui.neoecoae.storage.legacy.graph.energy_usage", HostText.ae2Amount(state().drain()));
                case "resources" -> Component.translatable("gui.neoecoae.storage.ui.resource_distribution");
                default -> throw new IllegalArgumentException("Unknown storage caption metric: " + metric);
            };
            float scale = metric.equals("energy_stored") ? energyRowScale() : fontScale;
            text(context, caption, 0, 0, getSizeWidth(), scale, TEXT, metric.equals("title"), rightAligned);
        }

        /**
         * The stored and drain captions share one row, so both take the scale that fits them side by side.
         * Otherwise longer translations (e.g. English) overlap each other.
         */
        private float energyRowScale() {
            Font font = Minecraft.getInstance().font;
            int gap = 8;
            int stored = font.width(Component.translatable("gui.neoecoae.storage.ui.energy",
                HostText.ae2Amount(state().energy()), HostText.ae2Amount(state().maxEnergy())).getString());
            int drain = font.width(Component.translatable("gui.neoecoae.storage.legacy.graph.energy_usage",
                HostText.ae2Amount(state().drain())).getString());
            return Math.max(0.5F, Math.min(fontScale, getSizeWidth() / Math.max(1, stored + gap + drain)));
        }

        /** Draw the drain amount under the stored amount, keeping both numeric values on one vertical axis. */
        private void drawEnergyUsage(GUIContext context) {
            Font font = Minecraft.getInstance().font;
            String marker = "__ECO_ENERGY_VALUE__";
            String template = Component.translatable("gui.neoecoae.storage.legacy.graph.energy_usage", marker).getString();
            int markerStart = template.indexOf(marker);
            if (markerStart < 0) return;
            String prefix = template.substring(0, markerStart);
            String suffix = template.substring(markerStart + marker.length());
            String amount = HostText.ae2Amount(state().drain());
            String complete = prefix + amount + suffix;
            float scale = energyRowScale();

            // Use the prefix from the stored-energy translation as the shared numeric anchor.
            int valueX;
            float offsetX = 0;
            if (rightAligned) {
                valueX = font.width(Component.literal(prefix));
                offsetX = Math.max(0, getSizeWidth() - font.width(Component.literal(complete)) * scale);
            } else {
                String storedTemplate = Component.translatable("gui.neoecoae.storage.ui.energy", marker, marker).getString();
                int storedMarker = storedTemplate.indexOf(marker);
                if (storedMarker < 0) return;
                valueX = font.width(Component.literal(storedTemplate.substring(0, storedMarker)));
            }
            var pose = context.graphics.pose();
            pose.pushPose();
            try {
                pose.translate(getPositionX() + offsetX, getPositionY(), 0);
                pose.scale(scale, scale, 1);
                drawSegment(context, font, prefix, 0);
                drawSegment(context, font, amount, valueX);
                drawSegment(context, font, suffix, valueX + font.width(Component.literal(amount)));
            } finally {
                pose.popPose();
            }
        }

        private void drawSegment(GUIContext context, Font font, String value, int x) {
            if (!value.isEmpty()) {
                context.graphics.drawString(font, Language.getInstance().getVisualOrder(Component.literal(value)),
                    x, 0, TEXT, false);
            }
        }
    }

    private final class HostIcon extends PanelElement {
        HostIcon() {
            tooltips(() -> hostItem.isEmpty() ? List.of(state().title()) : DrawerHelper.getItemToolTip(hostItem));
        }

        @Override public void drawBackgroundAdditional(GUIContext context) {
            texture(context, StoragePanelTextures.HOST_ICON_BACKGROUND);
            if (state().formed()) {
                float x = getPositionX() + Math.round((getSizeWidth() - 13) / 2);
                drawMarker(context, false, x, getPositionY() + 1);
                drawMarker(context, true, x, getPositionY() + getSizeHeight() - 9);
            }
            if (!hostItem.isEmpty()) context.graphics.renderItem(hostItem,
                Math.round(getPositionX() + (getSizeWidth() - 16) / 2),
                Math.round(getPositionY() + (getSizeHeight() - 16) / 2));
        }
    }

    private void drawMarker(GUIContext context, boolean bottom, float x, float y) {
        var custom = sync.textures().marker();
        if (custom != null) {
            context.drawTexture(StoragePanelTextures.hostMarker(state().tier(), bottom, custom), x, y, 13, 8);
        } else if (!HostPanelTier.drawMarker(context, panelTier.get(), bottom, x, y, 13, 8)) {
            context.drawTexture(StoragePanelTextures.hostMarker(state().tier(), bottom), x, y, 13, 8);
        }
    }

    private abstract class AnimatedBar extends PanelElement {
        final HostProgressAnimation opening = new HostProgressAnimation();

        @Override protected void onRemoved() {
            super.onRemoved();
            opening.reset();
        }
    }

    private final class CapacityBar extends StackedBar {
        CapacityBar() {
            tooltips(() -> {
                List<Component> lines = new ArrayList<>(capacityTooltip());
                lines.addAll(hoveredTooltip);
                return lines;
            });
        }

        @Override public void drawBackgroundAdditional(GUIContext context) {
            hoveredTooltip.clear();
            var snapshot = state();
            if (snapshot.unbounded()) {
                drawInfiniteCapacity(context);
                return;
            }
            int width = Math.round(getSizeWidth() * (float) Math.clamp(snapshot.usage(), 0, 1));
            drawSegments(context, snapshot, width);
        }

        private void drawInfiniteCapacity(GUIContext context) {
            float width = getSizeWidth();
            float revealedWidth = Math.round(width * opening.value(1));
            if (revealedWidth <= 0 || getSizeHeight() <= 0) return;
            var symbol = StoragePanelTextures.INFINITE_CAPACITY_SYMBOL;
            var fill = StoragePanelTextures.INFINITE_CAPACITY_FILL;
            int symbolWidth = symbol.spriteSize.width;
            int height = symbol.spriteSize.height;
            float symbolOffset = Math.round((width - symbolWidth) / 2);
            float x = getPositionX();
            float y = getPositionY() + Math.round((getSizeHeight() - height) / 2);
            // Opening and undersized layouts crop the fixed artwork instead of scaling it.
            boolean clipped = revealedWidth < width || width < symbolWidth || getSizeHeight() < height;
            if (clipped) context.enableScissor(x, getPositionY(), revealedWidth, getSizeHeight());
            try {
                if (symbolOffset > 0) context.drawTexture(fill, x, y, symbolOffset, height);
                context.drawTexture(symbol, x + symbolOffset, y, symbolWidth, height);
                float rightOffset = symbolOffset + symbolWidth;
                if (rightOffset < width) context.drawTexture(fill, x + rightOffset, y, width - rightOffset, height);
            } finally {
                if (clipped) context.disableScissor();
            }
        }
    }

    private abstract class StackedBar extends AnimatedBar {
        final List<Component> hoveredTooltip = new ArrayList<>();

        void drawSegments(GUIContext context, StoragePanelState snapshot, int filledWidth) {
            hoveredTooltip.clear();
            var segments = capacitySegments(snapshot, filledWidth);
            if (segments.isEmpty()) return;
            // Reveal fixed byte-share boundaries rather than rescaling every segment during opening.
            int revealedWidth = Math.round(filledWidth * opening.value(1));
            var mouse = isHover() || isSelfOrChildHover() ? getLocalMouse(context.mouseX, context.mouseY) : null;
            float mouseX = mouse == null ? Float.NaN : mouse.x - getPositionX();
            float mouseY = mouse == null ? Float.NaN : mouse.y - getPositionY();
            int x = 0;
            for (CapacitySegment segment : segments) {
                int width = Math.min(segment.width(), revealedWidth - x);
                if (width <= 0) break;
                context.drawTexture(StoragePanelTextures.swatch(segment.swatch(), segment.color()),
                    getPositionX() + x, getPositionY(), width, getSizeHeight());
                if (mouseX >= x && mouseX < x + width && mouseY >= 0 && mouseY < getSizeHeight()) {
                    context.drawTexture(StoragePanelTextures.RESOURCE_HOVER,
                        getPositionX() + x, getPositionY(), width, getSizeHeight());
                    hoveredTooltip.addAll(resourceTooltip(snapshot, segment));
                }
                x += segment.width();
            }
        }
    }

    private final class ResourceBar extends StackedBar {
        ResourceBar() { tooltips(() -> List.copyOf(hoveredTooltip)); }

        @Override public void drawBackgroundAdditional(GUIContext context) {
            drawSegments(context, state(), Math.round(getSizeWidth()));
        }
    }

    private final class EnergyBar extends AnimatedBar {
        EnergyBar() { tooltips(StorageHostCharts.this::energyTooltip); }

        @Override public void drawBackgroundAdditional(GUIContext context) {
            SpriteTexture scale = StoragePanelTextures.SCALE;
            int pixels = Math.round(scale.spriteSize.width
                * opening.value(HostText.usageRatio(state().energy(), state().maxEnergy())));
            if (pixels > 0) context.drawTexture(scale.copy().setSprite(scale.spritePosition.x,
                scale.spritePosition.y, pixels, scale.spriteSize.height), getPositionX(), getPositionY(),
                getSizeWidth() * pixels / scale.spriteSize.width, getSizeHeight());
        }
    }

    private final class TypePanel extends PanelElement {
        private int offset;
        private float nameFontScale = 0.55F;
        private float valueFontScale = 0.5F;
        private float nameWidth = 21;

        TypePanel configureFonts(JsonObject definition) {
            if (definition.has("name_font_scale")) nameFontScale = definition.get("name_font_scale").getAsFloat();
            if (definition.has("value_font_scale")) valueFontScale = definition.get("value_font_scale").getAsFloat();
            if (definition.has("name_width")) nameWidth = definition.get("name_width").getAsFloat();
            return this;
        }

        TypePanel() {
            for (int row = 0; row < 3; row++) {
                final int index = row;
                addChild(new UIElement().layout(layout -> layout.positionType(TaffyPosition.ABSOLUTE)
                    .left(0).top(index * 29).width(68).height(29))
                    .addEventListener(UIEvents.HOVER_TOOLTIPS, event ->
                        event.hoverTooltips = new HoverTooltips(tooltip(index), null, null, null)));
            }
            addEventListener(UIEvents.MOUSE_WHEEL, event -> {
                if (event.deltaY == 0) return;
                scroll(event.deltaY < 0 ? 1 : -1);
                event.stopImmediatePropagation();
            });
        }

        int maximumOffset() { return Math.max(0, state().types().size() - 3); }
        int currentOffset() { return offset = Math.clamp(offset, 0, maximumOffset()); }
        void scroll(int delta) { offset = Math.clamp(currentOffset() + delta, 0, maximumOffset()); }

        @Override public void drawBackgroundAdditional(GUIContext context) {
            int start = currentOffset();
            for (int row = 0; row < 3; row++) {
                float y = getPositionY() + row * 29;
                int index = start + row;
                if (index >= state().types().size()) continue;
                context.drawTexture(StoragePanelTextures.TYPE_PANEL, getPositionX(), y, 68, 29);
                StoragePanelState.TypeLine type = state().types().get(index);
                context.drawTexture(StoragePanelTextures.resourceIcon(type.swatch(), type.color()), getPositionX() + 2, y + 2, 6, 6);
                // Mod-provided name styles can disagree with the resource icon's palette.
                text(context, Component.literal(type.name().getString()), 10, row * 29 + 2,
                    nameWidth, nameFontScale, type.color());
                text(context, usage(HostText.ae2Amount(type.usedTypes()), type.usedTypes(), type.totalTypes(),
                    StorageCellGrid.capacity(type.totalTypes())), 3, row * 29 + 13, 29, valueFontScale, TEXT);
                text(context, usage(type.bytesText(), type.usedBytes(), type.totalBytes(),
                    StorageCellGrid.capacity(type.totalBytes())), 35, row * 29 + 13, 30, valueFontScale, TEXT);
                bar(context, StoragePanelTextures.BLUE_BAR, type.usedTypes(), type.totalTypes(), 3, y + 21);
                bar(context, StoragePanelTextures.PINK_BAR, type.usedBytes(), type.totalBytes(), 36, y + 21);
            }
        }

        private void bar(GUIContext context, SpriteTexture texture, long used, long total, int x, float y) {
            float ratio = state().unbounded() || total < 0 ? 1 : HostText.usageRatio(used, total);
            int width = Math.round(29 * ratio);
            if (width > 0) context.drawTexture(texture.copy().setSprite(texture.spritePosition.x,
                texture.spritePosition.y, width, 4), getPositionX() + x, y, width, 4);
        }

        /** Panel figure: coloured used part over a bounded total, or the plain used figure when unbounded. */
        private Component usage(String used, long usedValue, long totalValue, String total) {
            if (totalValue < 0 || state().unbounded()) return HostText.totalPart(used).withColor(StoragePanelTextures.VALUE_TEXT);
            return HostText.usedOfTotal(HostText.usedPart(used, usedValue, totalValue), "/",
                HostText.totalPart(total).withColor(StoragePanelTextures.VALUE_TEXT));
        }

        List<Component> tooltip(int row) {
            int index = currentOffset() + row;
            if (index >= state().types().size()) return List.of();
            StoragePanelState.TypeLine type = state().types().get(index);
            return List.of(type.name(),
                Component.translatable("gui.neoecoae.storage.legacy.cell_types",
                    HostText.usedPart(HostText.ae2Amount(type.usedTypes()), type.usedTypes(), type.totalTypes()),
                    HostText.totalPart(state().unbounded() ? "∞" : StorageCellGrid.capacity(type.totalTypes()))),
                Component.translatable("gui.neoecoae.storage.legacy.cell_bytes",
                    HostText.usedPart(HostText.fullBytes(type.exactUsedBytes()), type.usedBytes(), type.totalBytes()),
                    HostText.totalPart(state().unbounded() ? "∞" : HostText.fullBytes(type.totalBytes()))),
                Component.translatable("gui.neoecoae.storage.ui.scroll_types").withColor(HostText.MUTED));
        }
    }

    private final class TypeScrollbar extends PanelElement {
        TypeScrollbar() {
            tooltips(() -> List.of(Component.translatable("gui.neoecoae.storage.ui.scroll_types")));
            addEventListener(UIEvents.MOUSE_WHEEL, event -> {
                if (event.deltaY == 0) return;
                typePanel.scroll(event.deltaY < 0 ? 1 : -1);
                event.stopImmediatePropagation();
            });
            addEventListener(UIEvents.MOUSE_DOWN, event -> {
                if (event.button != 0) return;
                double ratio = Math.clamp((event.y - getPositionY()) / Math.max(1, getSizeHeight()), 0, 1);
                typePanel.offset = (int) Math.round(ratio * typePanel.maximumOffset());
                event.stopImmediatePropagation();
            });
        }

        @Override public void drawBackgroundAdditional(GUIContext context) {
            int maximum = typePanel.maximumOffset();
            if (maximum == 0) return;
            float height = Math.min(StoragePanelTextures.SLIDER.spriteSize.height, getSizeHeight());
            float y = getPositionY() + (getSizeHeight() - height) * typePanel.currentOffset() / maximum;
            context.drawTexture(StoragePanelTextures.SLIDER,
                getPositionX(), y, getSizeWidth(), height);
        }
    }

    private final class InfiniteStatus extends PanelElement {
        InfiniteStatus() { tooltips(StorageHostCharts.this::domainTooltip); }
        @Override public void drawBackgroundAdditional(GUIContext context) {
            if (state().unbounded()) texture(context, StoragePanelTextures.INFINITY);
        }
    }

    private final class InfiniteAnimation extends PanelElement {
        InfiniteAnimation() { tooltips(() -> state().infinite() ? domainTooltip() : List.of()); }
        @Override public void drawBackgroundAdditional(GUIContext context) {
            if (!state().infinite()) return;
            texture(context, StoragePanelTextures.INFINITE_ANIMATION);
        }
    }

    record CapacitySegment(int swatch, int color, int width) {}
    private record ResourceColor(int swatch, int color) {}

    /** Divide the supplied filled width by exact stored bytes, merging aliases and preserving amounts beyond long. */
    static List<CapacitySegment> capacitySegments(StoragePanelState state, int availableWidth) {
        int width = Math.max(0, availableWidth);
        if (width <= 0) return List.of();
        Map<ResourceColor, BigInteger> amounts = new LinkedHashMap<>();
        for (StoragePanelState.TypeLine type : state.types()) {
            if (type.exactUsedBytes().signum() <= 0) continue;
            amounts.merge(new ResourceColor(type.swatch(), type.color()), type.exactUsedBytes(), BigInteger::add);
        }
        BigInteger total = amounts.values().stream().reduce(BigInteger.ZERO, BigInteger::add);
        if (total.signum() <= 0) return List.of();
        var resources = amounts.entrySet().stream().sorted(
            Comparator.<Map.Entry<ResourceColor, BigInteger>, BigInteger>comparing(Map.Entry::getValue).reversed()
                .thenComparingInt(entry -> entry.getKey().swatch()).thenComparingInt(entry -> entry.getKey().color())).toList();
        List<CapacitySegment> segments = new ArrayList<>();
        BigInteger accumulated = BigInteger.ZERO;
        int previous = 0;
        for (var resource : resources) {
            accumulated = accumulated.add(resource.getValue());
            int end = accumulated.multiply(BigInteger.valueOf(width)).add(total.shiftRight(1)).divide(total).intValue();
            if (end > previous) segments.add(new CapacitySegment(resource.getKey().swatch(), resource.getKey().color(), end - previous));
            previous = end;
        }
        return List.copyOf(segments);
    }

    static Component capacityValue(StoragePanelState state) {
        var used = Component.literal(state.usedBytes()).withColor(state.unbounded()
            ? StoragePanelTextures.VALUE_TEXT : HostText.gaugeTextColor((float) state.usage()));
        if (state.unbounded()) return used;
        return used.append(Component.literal(" / ").withColor(TEXT))
            .append(Component.literal(HostText.ae2Amount(state.totalBytes())).withColor(StoragePanelTextures.VALUE_TEXT));
    }

    private static Component fullCapacityValue(StoragePanelState state) {
        var used = Component.literal(fullUsedBytes(state)).withColor(state.unbounded()
            ? HostText.VALUE : HostText.gaugeTextColor((float) state.usage()));
        if (state.unbounded()) return used;
        return HostText.usedOfTotal(used, " / ", HostText.totalPart(HostText.fullBytes(state.totalBytes())));
    }

    private static String fullUsedBytes(StoragePanelState state) {
        BigInteger used = state.types().stream().map(StoragePanelState.TypeLine::exactUsedBytes)
            .reduce(BigInteger.ZERO, BigInteger::add);
        return HostText.fullBytes(used);
    }

    private List<Component> capacityTooltip() {
        return List.of(
            Component.translatable("gui.neoecoae.storage.legacy.graph.total_bytes", fullCapacityValue(state())),
            Component.translatable("gui.neoecoae.storage.legacy.graph.total_usage",
                state().unbounded() ? "∞" : HostText.percent(state().usage())));
    }

    private List<Component> energyTooltip() {
        return List.of(
            Component.translatable("gui.neoecoae.storage.ui.energy",
                HostText.usedPart(HostText.ae2Amount(state().energy()), state().energy(), state().maxEnergy()),
                HostText.totalPart(HostText.ae2Amount(state().maxEnergy()))),
            Component.translatable("gui.neoecoae.storage.legacy.graph.energy_usage", HostText.ae2Amount(state().drain())));
    }

    private List<Component> resourceTooltip() {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("gui.neoecoae.storage.ui.resource_distribution"));
        BigInteger total = state().types().stream().map(StoragePanelState.TypeLine::exactUsedBytes)
            .reduce(BigInteger.ZERO, BigInteger::add);
        if (total.signum() <= 0) return lines;
        for (StoragePanelState.TypeLine type : state().types()) {
            if (type.exactUsedBytes().signum() <= 0) continue;
            double share = new BigDecimal(type.exactUsedBytes())
                .divide(new BigDecimal(total), MathContext.DECIMAL64).doubleValue();
            lines.add(Component.translatable("gui.neoecoae.storage.ui.resource_share",
                type.name(), HostText.fullBytes(type.exactUsedBytes()), HostText.percent(share)).withColor(type.color()));
        }
        return lines;
    }

    private List<Component> resourceTooltip(StoragePanelState snapshot, CapacitySegment segment) {
        var types = snapshot.types().stream().filter(type -> type.exactUsedBytes().signum() > 0
            && type.swatch() == segment.swatch() && type.color() == segment.color()).toList();
        if (types.isEmpty()) return List.of();
        BigInteger used = types.stream().map(StoragePanelState.TypeLine::exactUsedBytes)
            .reduce(BigInteger.ZERO, BigInteger::add);
        BigInteger total = snapshot.types().stream().map(StoragePanelState.TypeLine::exactUsedBytes)
            .filter(amount -> amount.signum() > 0).reduce(BigInteger.ZERO, BigInteger::add);
        var name = Component.empty();
        for (Component alias : types.stream().map(StoragePanelState.TypeLine::name).distinct().toList()) {
            if (!name.getString().isEmpty()) name.append(" / ");
            name.append(alias);
        }
        double share = new BigDecimal(used).divide(new BigDecimal(total), MathContext.DECIMAL64).doubleValue();
        return List.of(Component.translatable("gui.neoecoae.storage.ui.resource_share", name,
            HostText.fullBytes(used), HostText.percent(share)).withColor(segment.color()));
    }

    private List<Component> domainTooltip() {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable(state().migrating() ? "gui.neoecoae.storage.ui.migrating"
            : state().infinite() ? "gui.neoecoae.storage.ui.infinite" : "gui.neoecoae.storage.ui.normal"));
        if (!state().domainId().isEmpty()) lines.add(Component.literal("UUID: " + state().domainId()).withColor(0x55FFFF));
        if (state().missingMembers() > 0) {
            lines.add(Component.translatable("gui.neoecoae.storage.members_missing", state().missingMembers()).withColor(0xFF6A75));
            lines.add(Component.translatable("gui.neoecoae.storage.members_return").withColor(0xFF6A75));
        }
        return lines;
    }
}
