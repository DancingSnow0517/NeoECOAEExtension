package cn.dancingsnow.neoecoae.gui.computation;

import static cn.dancingsnow.neoecoae.gui.computation.ComputationControllerTextures.HEIGHT;
import static cn.dancingsnow.neoecoae.gui.computation.ComputationControllerTextures.WIDTH;
import cn.dancingsnow.neoecoae.api.IECOTier;
import cn.dancingsnow.neoecoae.gui.common.HostPanelTier;
import cn.dancingsnow.neoecoae.gui.common.HostProgressAnimation;
import cn.dancingsnow.neoecoae.crafting.display.format.NEByteFormatter;
import cn.dancingsnow.neoecoae.gui.common.HostElements;
import cn.dancingsnow.neoecoae.gui.common.HostText;
import cn.dancingsnow.neoecoae.multiblock.network.NEFrequencyAllocator;
import com.lowdragmc.lowdraglib2.gui.sync.bindings.impl.DataBindingBuilder;
import com.lowdragmc.lowdraglib2.gui.texture.SpriteTexture;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.BindableValue;
import com.lowdragmc.lowdraglib2.gui.ui.event.HoverTooltips;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import com.lowdragmc.lowdraglib2.gui.util.DrawerHelper;
import dev.vfyjxf.taffy.style.TaffyPosition;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** Draws the computation controller artwork and syncs the host values it shows. */
final class ComputationControllerArtwork extends UIElement {
    private static final float TEXT_SCALE = 0.75F;
    private static final int TEXT_COLOR = 0xFF3F3D52;
    private static final int LABEL_COLOR = 0xFF5E5C72;
    private static final int BLUE = 0xFF2D69E1;
    private static final int PURPLE = 0xFF915DCD;
    private static final int PINK = 0xFFB738A2;
    private static final int WARN = 0xFFD95B25;
    private static final int PANEL_PRIMARY = 0xFFE4E4EE;
    private static final int PANEL_SECONDARY = 0xFFB4B6CC;
    private static final int PANEL_ONLINE = 0xFF7BE495;
    private static final int PANEL_OFFLINE = 0xFFFF7A6B;
    private static final int PANEL_PINK = 0xFFFF8FDD;
    private static final int PANEL_CHANNEL = 0xFFB4E2FF;
    private static final int PANEL_HIGH_ENERGY = 0xFFFF7A6B;
    private static final int FREE = 0xFF2F8A4B;
    private static final int[] SWITCH_X = {6, 29, 52};
    private static final String[] SWITCH_KEYS = {"substitutions", "cycle_planning", "fast_planner"};
    private static final int[] PANEL_X = {78, 135, 192};
    private static final String[] PANEL_KEYS = {"cpu", "threads", "parallel"};
    private static final int[] BAR_X = {83, 140, 197};
    private static final int ROW_Y = 102;
    private static final int ROW_SPACING = 7;
    private static final int ROW_WIDTH = 44;
    private static final int SWITCH_LABEL_WIDTH = 23;
    private final ComputationControllerUI.Config config;
    private final Supplier<ComputationControllerPanelState> panelState;
    private final ItemStack hostItem;
    private final Supplier<IECOTier> panelTier;
    private final HostProgressAnimation opening = new HostProgressAnimation();
    private ComputationControllerPanelState state;
    private long usedBytes, totalBytes, availableBytes;
    private int usedThreads, totalThreads, parallelCount, fullParallelCount, networkMultiplier, networkFrequency;
    private int normalSwitchCount, highEnergySwitchCount;
    private boolean connected;
    private boolean substitutions, cyclePlanning, fastPlanner;

    ComputationControllerArtwork(ComputationControllerUI.Config config,
                                 Supplier<ComputationControllerPanelState> panelState, Supplier<IECOTier> panelTier) {
        this.config = config;
        this.panelTier = panelTier;
        this.panelState = panelState;
        hostItem = config.hostItem().get().copyWithCount(1);
        CompoundTag initial = snapshot();
        read(initial);
        layout(layout -> layout.positionType(TaffyPosition.ABSOLUTE).left(0).top(0).width(WIDTH).height(HEIGHT));

        addChild(tooltipRegion(78, 99, 54, 43, this::storageTooltip));
        addChild(tooltipRegion(178, 176, 16, 16, () -> switchTooltip(0)));
        addChild(tooltipRegion(135, 99, 54, 43, this::threadTooltip));
        addChild(tooltipRegion(202, 176, 16, 16, () -> switchTooltip(1)));
        addChild(tooltipRegion(192, 99, 54, 43, this::parallelTooltip));
        addChild(tooltipRegion(226, 176, 16, 16, () -> switchTooltip(2)));
        addChild(tooltipRegion(178, 151, 66, 16, this::connectionTooltip));
        addChild(tooltipRegion(178, 201, 66, 16, this::multiplierTooltip));
        addChild(tooltipRegion(9, 39, 16, 34, this::hostTooltip));

        BindableValue<Tag> synced = new BindableValue<>(initial);
        synced.bind(DataBindingBuilder.tagS2C(this::snapshot).build());
        synced.registerValueListener(this::read);
        synced.setDisplay(false);
        addChild(synced);
    }

    @Override
    protected void onRemoved() {
        super.onRemoved();
        opening.reset();
    }

    private CompoundTag snapshot() {
        CompoundTag tag = panelState.get().toTag();
        tag.putLong("usedBytes", Math.max(0L, config.usedBytes().getAsLong()));
        tag.putLong("totalBytes", Math.max(0L, config.totalBytes().getAsLong()));
        tag.putLong("availableBytes", Math.max(0L, config.availableBytes().getAsLong()));
        tag.putInt("usedThreads", Math.max(0, config.usedThreads().getAsInt()));
        tag.putInt("totalThreads", Math.max(0, config.totalThreads().getAsInt()));
        tag.putInt("parallelCount", Math.max(0, config.parallelCount().getAsInt()));
        tag.putInt("fullParallelCount", Math.max(0, config.fullParallelCount().getAsInt()));
        tag.putInt("networkMultiplier", Math.max(1, config.networkMultiplier().getAsInt()));
        tag.putInt("networkFrequency", config.networkFrequency().getAsInt());
        tag.putInt("normalSwitchCount", Math.max(0, config.normalSwitchCount().getAsInt()));
        tag.putInt("highEnergySwitchCount", Math.max(0, config.highEnergySwitchCount().getAsInt()));
        tag.putBoolean("connected", config.connected().getAsBoolean());
        tag.putBoolean("substitutions", !config.ignoringPatternSubstitutions().getAsBoolean());
        tag.putBoolean("cyclePlanning", config.cyclePlanningEnabled().getAsBoolean());
        tag.putBoolean("fastPlanner", config.fastPlannerEnabled().getAsBoolean());
        return tag;
    }

    private void read(Tag value) {
        CompoundTag tag = value instanceof CompoundTag compound ? compound : new CompoundTag();
        state = ComputationControllerPanelState.fromTag(tag);
        usedBytes = tag.getLong("usedBytes");
        totalBytes = tag.getLong("totalBytes");
        availableBytes = tag.getLong("availableBytes");
        usedThreads = tag.getInt("usedThreads");
        totalThreads = tag.getInt("totalThreads");
        parallelCount = tag.getInt("parallelCount");
        fullParallelCount = tag.getInt("fullParallelCount");
        networkMultiplier = tag.getInt("networkMultiplier");
        networkFrequency = tag.getInt("networkFrequency");
        normalSwitchCount = tag.getInt("normalSwitchCount");
        highEnergySwitchCount = tag.getInt("highEnergySwitchCount");
        connected = tag.getBoolean("connected");
        substitutions = tag.getBoolean("substitutions");
        cyclePlanning = tag.getBoolean("cyclePlanning");
        fastPlanner = tag.getBoolean("fastPlanner");
    }

    private UIElement tooltipRegion(int x, int y, int width, int height, Supplier<List<Component>> lines) {
        return new UIElement().layout(layout -> layout.positionType(TaffyPosition.ABSOLUTE)
            .left(x).top(y).width(width).height(height))
            .addEventListener(UIEvents.HOVER_TOOLTIPS, event -> event.hoverTooltips = new HoverTooltips(
                lines.get(), null, null, null));
    }

    private List<Component> storageTooltip() {
        return List.of(
            Component.translatable("gui.neoecoae.host.computation.cpu_storage").append(": ")
                .append(HostText.fullByteProgress(usedBytes, totalBytes)),
            Component.translatable("gui.neoecoae.host.computation.free_memory").append(": ")
                .append(Component.literal(HostText.expandedNumber(availableBytes) + " B").withColor(HostText.VALUE)),
            Component.translatable("gui.neoecoae.computation.ui.stat.usage").append(": ")
                .append(Component.literal(percent(permille(usedBytes, totalBytes)))
                    .withColor(HostText.usedValueColor(usedBytes, totalBytes))));
    }

    private List<Component> threadTooltip() {
        return List.of(
            Component.translatable("gui.neoecoae.host.computation.thread_usage").append(": ")
                .append(HostText.usedOfTotal(
                    HostText.usedPart(HostText.expandedNumber(usedThreads), usedThreads, totalThreads), " / ",
                    HostText.totalPart(HostText.expandedNumber(totalThreads)))),
            Component.translatable("gui.neoecoae.computation.ui.stat.free")
                .append(": " + HostText.expandedNumber(Math.max(0, totalThreads - usedThreads))),
            Component.translatable("gui.neoecoae.computation.ui.stat.usage").append(": ")
                .append(Component.literal(percent(permille(usedThreads, totalThreads)))
                    .withColor(HostText.usedValueColor(usedThreads, totalThreads))));
    }

    private List<Component> parallelTooltip() {
        return List.of(
            Component.translatable("gui.neoecoae.host.computation.parallel_count").append(": ")
                .append(HostText.expandedNumber(parallelCount)),
            Component.translatable("gui.neoecoae.computation.ui.parallel_full").append(": ")
                .append(Component.literal(percent(permille(parallelCount, fullParallelCount)))
                    .withColor(HostText.usedValueColor(parallelCount, fullParallelCount))),
            Component.translatable("gui.neoecoae.computation.ui.parallel_shared").withColor(0xAAAAAA),
            Component.translatable("gui.neoecoae.computation.ui.stat.multiplier").append(": x" + networkMultiplier),
            Component.translatable("gui.neoecoae.computation.ui.stat.threads")
                .append(": " + HostText.expandedNumber(totalThreads)));
    }

    private List<Component> switchTooltip(int index) {
        String key = SWITCH_KEYS[index];
        boolean on = switchOn(index);
        return List.of(
            Component.translatable("gui.neoecoae.computation.settings." + key),
            Component.translatable(on ? "gui.neoecoae.computation.ui.switch.on" : "gui.neoecoae.computation.ui.switch.off")
                .withColor(on ? 0x55FF55 : 0xAAAAAA),
            Component.translatable("gui.neoecoae.computation.ui.switch." + key + ".desc").withColor(0xAAAAAA));
    }

    private boolean switchOn(int index) {
        return switch (index) {
            case 0 -> substitutions;
            case 1 -> cyclePlanning;
            default -> fastPlanner;
        };
    }

    private List<Component> connectionTooltip() {
        return List.of(Component.translatable(connected
            ? "gui.neoecoae.host.network.connected" : "gui.neoecoae.host.network.disconnected"),
            HostElements.networkFrequencyTooltip(networkFrequency));
    }

    private List<Component> multiplierTooltip() {
        return List.of(
            Component.translatable("gui.neoecoae.computation.ui.panel.network", networkMultiplier),
            Component.translatable("gui.neoecoae.computation.ui.panel.switch_normal", normalSwitchCount),
            Component.translatable("gui.neoecoae.computation.ui.panel.switch_high_energy", highEnergySwitchCount));
    }

    private List<Component> hostTooltip() {
        return hostItem.isEmpty() ? List.of() : DrawerHelper.getItemToolTip(hostItem);
    }

    @Override
    public void drawBackgroundAdditional(GUIContext context) {
        sprite(context, ComputationControllerTextures.background(), 0, 0, WIDTH, HEIGHT);
        drawLabels(context);
        if (state.formed()) {
            int tier = state.hostTier();
            if (!HostPanelTier.drawName(context, panelTier.get(), getPositionX() + 23,
                getPositionY() + 5, 13, 18, TEXT_COLOR) && tier >= 1 && tier <= 3) {
                sprite(context, ComputationControllerTextures.tier(tier), 23, 5, 13, 18);
            }
            drawHostMarker(context, false, 40);
            drawHostMarker(context, true, 64);
        }
        if (!hostItem.isEmpty()) {
            context.graphics.renderItem(hostItem, Math.round(getPositionX()) + 9, Math.round(getPositionY()) + 48);
        }
        if (!state.formed()) return;
        drawColumns(context);
        drawStatPanels(context);
        drawStatusIcons(context);
        drawPanelInfo(context);
    }

    private void drawHostMarker(GUIContext context, boolean bottom, int y) {
        if (!HostPanelTier.drawMarker(context, panelTier.get(), bottom,
            getPositionX() + 10, getPositionY() + y, 14, 8) && state.hostTier() >= 1 && state.hostTier() <= 3) {
            sprite(context, ComputationControllerTextures.hostMarker(state.hostTier(), bottom), 10, y, 14, 8);
        }
    }

    private void drawLabels(GUIContext context) {
        Font font = Minecraft.getInstance().font;
        Component title = Component.translatable("gui.neoecoae.computation.ui.title");
        float titleScale = Math.min(HostText.TITLE_SCALE, 92.0F / Math.max(1, font.width(title)));
        drawText(context, title, HostText.TITLE_X, HostText.TITLE_Y, titleScale, TEXT_COLOR);
        drawText(context, Component.translatable("gui.neoecoae.computation.ui.control_heading"), 9, 90, TEXT_COLOR);
        drawSwitchLabels(context);
        for (int i = 0; i < PANEL_KEYS.length; i++) {
            drawText(context, Component.translatable("gui.neoecoae.computation.ui." + PANEL_KEYS[i]),
                PANEL_X[i] + 2, 91, TEXT_COLOR);
        }
    }

    private void drawSwitchLabels(GUIContext context) {
        Font font = Minecraft.getInstance().font;
        Component[] labels = new Component[SWITCH_KEYS.length];
        float scale = TEXT_SCALE;
        for (int i = 0; i < SWITCH_KEYS.length; i++) {
            labels[i] = Component.translatable("gui.neoecoae.computation.ui.switch." + SWITCH_KEYS[i]);
            scale = Math.min(scale, (SWITCH_LABEL_WIDTH - 1.0F) / Math.max(1, font.width(labels[i])));
        }
        for (int i = 0; i < SWITCH_KEYS.length; i++) {
            float left = SWITCH_X[i] + (SWITCH_LABEL_WIDTH - font.width(labels[i]) * scale) / 2.0F;
            drawText(context, labels[i], left, 104, scale, TEXT_COLOR);
        }
    }

    private void drawColumns(GUIContext context) {
        for (int cell = 0; cell < ComputationControllerPanelState.SLOT_COUNT; cell++) {
            if (!state.hasCell(cell)) continue;
            int x = 49 + 18 * cell;
            var upper = state.upperMember(cell);
            var core = state.coreMember(cell);
            var lower = state.lowerMember(cell);
            sprite(context, ComputationControllerTextures.columnBackground(), x, 30, 16, 52);
            if (upper.present()) sprite(context, ComputationControllerTextures.member(upper, 0), x + 3, 47, 10, 5);
            if (core.present()) sprite(context, ComputationControllerTextures.member(core, 1), x + 3, 53, 10, 6);
            if (lower.present()) sprite(context, ComputationControllerTextures.member(lower, 2), x + 3, 60, 10, 6);
        }
    }

    private void drawStatPanels(GUIContext context) {
        int[] permilles = {
            permille(usedBytes, totalBytes), permille(usedThreads, totalThreads), permille(parallelCount, fullParallelCount)};
        for (int i = 0; i < PANEL_X.length; i++) {
            int fill = Math.round(44 * opening.value(permilles[i] / 1000D));
            if (fill > 0) {
                sprite(context, ComputationControllerTextures.widget(0, 154 + 7 * i, fill, 7), BAR_X[i], 131, fill, 7);
            }
        }
        drawRow(context, 0, 0, Component.translatable("gui.neoecoae.computation.ui.stat.used"), BLUE,
            byteCandidates(usedBytes));
        drawRow(context, 0, 1, Component.translatable("gui.neoecoae.computation.ui.stat.capacity"), TEXT_COLOR,
            byteCandidates(totalBytes));
        drawRow(context, 0, 2, Component.translatable("gui.neoecoae.computation.ui.stat.free"),
            availableBytes == 0 && totalBytes > 0 ? WARN : FREE, byteCandidates(availableBytes));
        drawRow(context, 1, 0, Component.translatable("gui.neoecoae.computation.ui.stat.busy"), PURPLE,
            counts(usedThreads));
        drawRow(context, 1, 1, Component.translatable("gui.neoecoae.computation.ui.stat.total"), TEXT_COLOR,
            counts(totalThreads));
        drawRow(context, 1, 2, Component.translatable("gui.neoecoae.computation.ui.stat.free"),
            totalThreads - usedThreads == 0 && totalThreads > 0 ? WARN : FREE,
            counts(Math.max(0, totalThreads - usedThreads)));
        drawRow(context, 2, 0, Component.translatable("gui.neoecoae.computation.ui.stat.parallel"), PINK,
            counts(parallelCount));
        drawRow(context, 2, 1, Component.translatable("gui.neoecoae.computation.ui.stat.multiplier"),
            networkMultiplier > 1 ? WARN : TEXT_COLOR, "x" + networkMultiplier);
        drawRow(context, 2, 2, Component.translatable("gui.neoecoae.computation.ui.stat.threads"), TEXT_COLOR,
            counts(totalThreads));
    }

    private void drawStatusIcons(GUIContext context) {
        if (!connected) return;
        boolean pink = endgamePink();
        sprite(context, ComputationControllerTextures.statusIcon(0, pink), 180, 153, 12, 12);
        if (substitutions) sprite(context, ComputationControllerTextures.statusIcon(1, pink), 180, 178, 12, 12);
        if (cyclePlanning) sprite(context, ComputationControllerTextures.statusIcon(2, pink), 204, 178, 12, 12);
        if (fastPlanner) sprite(context, ComputationControllerTextures.statusIcon(3, pink), 228, 178, 12, 12);
        if (networkMultiplier > 1) {
            // Index 4 is the normal switch; index 5 is the high-energy switch. Pink overrides both.
            boolean highEnergy = highEnergySwitchCount > 0 && !pink;
            sprite(context, ComputationControllerTextures.statusIcon(highEnergy ? 5 : 4, pink), 180, 203, 12, 12);
        }
    }

    private boolean endgamePink() {
        return networkMultiplier >= 8 && totalBytes == Long.MAX_VALUE && parallelCount == Integer.MAX_VALUE;
    }

    private void drawPanelInfo(GUIContext context) {
        drawInfoRow(context,
            Component.translatable(connected
                ? "gui.neoecoae.computation.ui.stat.online" : "gui.neoecoae.computation.ui.stat.offline"),
            connected ? PANEL_ONLINE : PANEL_OFFLINE,
            networkFrequency == NEFrequencyAllocator.UNASSIGNED
                ? Component.translatable("gui.neoecoae.computation.ui.panel.frequency.unassigned")
                : Component.translatable("gui.neoecoae.computation.ui.panel.frequency", networkFrequency),
            PANEL_CHANNEL, 147);
        Component switchFirst;
        Component switchSecond;
        int switchFirstColor = PANEL_PRIMARY;
        int switchSecondColor = PANEL_HIGH_ENERGY;
        if (normalSwitchCount == 0 && highEnergySwitchCount == 0) {
            switchFirst = Component.translatable("gui.neoecoae.computation.ui.panel.switch_none");
            switchSecond = Component.translatable("gui.neoecoae.computation.ui.panel.network", networkMultiplier);
            switchSecondColor = PANEL_SECONDARY;
        } else {
            switchFirst = Component.translatable("gui.neoecoae.computation.ui.panel.switch_normal", normalSwitchCount);
            switchSecond = Component.translatable(
                "gui.neoecoae.computation.ui.panel.switch_high_energy", highEnergySwitchCount);
            switchFirstColor = endgamePink() ? PANEL_PINK : PANEL_CHANNEL;
        }
        drawInfoRow(context, switchFirst, switchFirstColor, switchSecond, switchSecondColor, 197);
    }

    private void drawInfoRow(GUIContext context, Component first, int firstColor, Component second, int secondColor,
                             int rowTop) {
        Font font = Minecraft.getInstance().font;
        float textHeight = (font.lineHeight - 1) * TEXT_SCALE;
        float firstY = rowTop + (24 - textHeight * 2 - 4) / 2.0F;
        Component[] lines = {first, second};
        int[] colors = {firstColor, secondColor};
        for (int i = 0; i < lines.length; i++) {
            String value = lines[i].getString();
            if (value.isEmpty()) continue;
            float scale = Math.min(TEXT_SCALE, 47.0F / Math.max(1, font.width(value)));
            float y = firstY + i * (textHeight + 4);
            float textY = y + (font.lineHeight - 1) * (TEXT_SCALE - scale) / 2.0F;
            drawText(context, lines[i], 196, textY, scale, colors[i], true);
        }
    }

    private void sprite(GUIContext context, SpriteTexture texture, int x, int y, int width, int height) {
        context.drawTexture(texture, getPositionX() + x, getPositionY() + y, width, height);
    }

    private void drawRow(GUIContext context, int panel, int row, Component label, int valueColor,
                         String... candidates) {
        Font font = Minecraft.getInstance().font;
        int x = PANEL_X[panel] + 5;
        int y = ROW_Y + row * ROW_SPACING;
        float room = ROW_WIDTH - font.width(label) * TEXT_SCALE - 2;
        String value = candidates[candidates.length - 1];
        for (String candidate : candidates) {
            if (font.width(candidate) * TEXT_SCALE <= room) {
                value = candidate;
                break;
            }
        }
        drawText(context, label, x, y, LABEL_COLOR);
        drawText(context, Component.literal(value), x + ROW_WIDTH - font.width(value) * TEXT_SCALE, y, valueColor);
    }

    private void drawText(GUIContext context, Component text, float x, float y, int color) {
        drawText(context, text, x, y, TEXT_SCALE, color);
    }

    private void drawText(GUIContext context, Component text, float x, float y, float scale, int color) {
        drawText(context, text, x, y, scale, color, false);
    }

    private void drawText(GUIContext context, Component text, float x, float y, float scale, int color,
                          boolean shadow) {
        var pose = context.graphics.pose();
        pose.pushPose();
        try {
            pose.translate(getPositionX() + x, getPositionY() + y, 0.0D);
            pose.scale(scale, scale, 1.0F);
            context.graphics.drawString(Minecraft.getInstance().font, text, 0, 0, color, shadow);
        } finally {
            pose.popPose();
        }
    }

    private static String percent(int permille) {
        return Math.round(permille / 10F) + "%";
    }

    private static String[] counts(long value) {
        return new String[] {Long.toString(value), HostText.ae2Amount(value)};
    }

    private static String[] byteCandidates(long bytes) {
        String full = NEByteFormatter.format(bytes);
        int space = full.lastIndexOf(' ');
        String rounded = new BigDecimal(full.substring(0, space).replace(",", "")).setScale(1, RoundingMode.HALF_UP)
            .stripTrailingZeros().toPlainString() + full.substring(space);
        return new String[] {full, rounded, rounded.replace(" ", "")};
    }

    private static int permille(long numerator, long denominator) {
        if (numerator <= 0 || denominator <= 0) return 0;
        if (numerator >= denominator) return 1000;
        return (int) Math.round(numerator * 1000.0D / denominator);
    }
}
