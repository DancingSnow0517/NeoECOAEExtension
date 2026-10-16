package cn.dancingsnow.neoecoae.gui.computation;

import appeng.api.config.CpuSelectionMode;
import cn.dancingsnow.neoecoae.api.IECOTier;
import cn.dancingsnow.neoecoae.crafting.execution.ECOCraftingCPU;
import cn.dancingsnow.neoecoae.gui.common.HostSideButtonBar;
import cn.dancingsnow.neoecoae.gui.crafting.CraftingHostPanelUI;
import cn.dancingsnow.neoecoae.gui.json.NeoUiJsonContext;
import cn.dancingsnow.neoecoae.gui.json.NeoUiJsonLoader;
import cn.dancingsnow.neoecoae.gui.theme.NETextures;
import com.google.gson.JsonObject;
import com.lowdragmc.lowdraglib2.gui.sync.bindings.impl.DataBindingBuilder;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.texture.SpriteTexture;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.BindableValue;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.inventory.InventorySlots;
import com.lowdragmc.lowdraglib2.gui.ui.event.HoverTooltips;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/** Texture-backed computation controller; LDLib2 retains inventory, toolbar actions and menu synchronization. */
public final class ComputationControllerUI {
    private static final ResourceLocation LAYOUT = ResourceLocation.fromNamespaceAndPath("neoecoae", "screens/computation/controller");

    public record Config(
        BooleanSupplier connected, IntSupplier networkMultiplier, IntSupplier normalSwitchCount, IntSupplier highEnergySwitchCount,
        LongSupplier usedBytes, LongSupplier totalBytes, LongSupplier availableBytes,
        IntSupplier usedThreads, IntSupplier totalThreads, IntSupplier parallelCount, IntSupplier fullParallelCount,
        Supplier<CpuSelectionMode> cpuSelectionMode, IntConsumer adjustCpuSelectionMode,
        BooleanSupplier ignoringPatternSubstitutions, IntSupplier substitutionPatternCount,
        Runnable toggleIgnoringPatternSubstitutions,
        BooleanSupplier cyclePlanningEnabled, Runnable toggleCyclePlanning,
        BooleanSupplier fastPlannerEnabled, Runnable toggleFastPlanner,
        IntSupplier networkFrequency, IntConsumer adjustNetworkFrequency,
        Supplier<ItemStack> hostItem,
        Supplier<List<ComputationDriveEntry>> upperDrives, Supplier<List<ComputationDriveEntry>> lowerDrives,
        @Nullable Player player, DriveExtractor driveExtractor, DriveInserter driveInserter,
        LongSupplier gameTime, BooleanSupplier canInteract,
        Supplier<List<ECOCraftingCPU>> cpus, Consumer<ECOCraftingCPU> toggleCpuSuspended, Consumer<ECOCraftingCPU> cancelCpu,
        Supplier<Component> endgameStatus
    ) {
        HolderLookup.Provider registries() {
            return player == null ? RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY)
                : player.registryAccess();
        }
    }

    @FunctionalInterface
    public interface DriveExtractor {
        boolean extract(BlockPos position, ItemStack expected, boolean toInventory);
    }

    @FunctionalInterface
    public interface DriveInserter {
        boolean insert(BlockPos position, ItemStack expected);
    }

    private ComputationControllerUI() {}

    public static UIElement create(Config config,
                                   Supplier<ComputationControllerPanelState> panelState,
                                   UIElement guideButton, UIElement buildButton, UIElement buildWindow) {
        return create(config, panelState, guideButton, buildButton, buildWindow, () -> null);
    }

    public static UIElement create(Config config, Supplier<ComputationControllerPanelState> panelState,
        UIElement guideButton, UIElement buildButton, UIElement buildWindow, Supplier<IECOTier> panelTier) {
        Button cpuMode = ComputationControllerInteractions.createCpuSelectionButton(config);
        cpuMode.setId("computation-cpu-mode");
        Button frequency = ComputationControllerInteractions.createNetworkFrequencyButton(config);
        frequency.setId("computation-frequency");
        var identities = new CpuSelectionState.Identities<ECOCraftingCPU>();
        ComputationCpuStatusPanel status = new ComputationCpuStatusPanel(config, identities);
        status.setDisplay(false);
        UIElement[] rootRef = new UIElement[1];
        ComputationCpuSelector selector = new ComputationCpuSelector(config, serial -> {
            mainOf(rootRef[0]).setDisplay(false);
            status.setDisplay(true);
            status.open(serial);
            setViewSize(rootRef[0], 326, 254);
            recenter();
        }, identities);
        selector.setDisplay(false);
        status.setBackAction(() -> {
            status.setDisplay(false);
            mainOf(rootRef[0]).setDisplay(true);
            setViewSize(rootRef[0], selector.isDisplayed() ? 362 : 295, 231);
            recenter();
        });
        UIElement info = CraftingHostPanelUI.createMaintenanceStatusIcon(config.endgameStatus());
        info.setId("computation-info");
        info.style(style -> style.backgroundTexture(SpriteTexture.of(
            ResourceLocation.fromNamespaceAndPath("neoecoae", "textures/gui/common/side_bar/mirrored_button_slot_up.png"))));
        Button listToggle = HostSideButtonBar.createButton();
        UIElement listToggleIcon = new UIElement().layout(layout -> layout.heightPercent(100).setAspectRatio(1f))
            .style(style -> style.backgroundTexture(NETextures.CPU_SELECTOR_CLOSED))
            .addClasses("__icon__", "__button_post-icon__");
        listToggle.noText().addChild(listToggleIcon);
        listToggle.layout(layout -> layout.width(18).height(20));
        listToggle.setId("computation-cpu-list-toggle");
        listToggle.setOnClick(event -> {
            boolean show = !selector.isDisplayed();
            selector.setDisplay(show);
            listToggleIcon.style(style -> style.backgroundTexture(
                show ? NETextures.CPU_SELECTOR : NETextures.CPU_SELECTOR_CLOSED));
            info.setDisplay(!show);
            setViewSize(rootRef[0], show ? 362 : 295, 231);
            recenter();
        });
        listToggle.addEventListener(UIEvents.HOVER_TOOLTIPS, event -> event.hoverTooltips = new HoverTooltips(
            List.of(Component.translatable("gui.neoecoae.computation.ui.cpu_list")), null, null, null));
        UIElement root = NeoUiJsonLoader.load(LAYOUT, new NeoUiJsonContext() {
            @Override
            public UIElement createCustom(JsonObject node) {
                return switch (node.has("name") ? node.get("name").getAsString() : "") {
                    case "computation_artwork" -> new ComputationControllerArtwork(config, panelState, panelTier);
                    case "computation_drive_grid" -> new ComputationDriveGrid(config);
                    case "computation_cpu_selector" -> selector;
                    case "computation_status" -> status;
                    case "computation_switch" -> createSwitch(config,
                        node.has("key") ? node.get("key").getAsString() : "");
                    case "computation_toolbar" -> HostSideButtonBar.left(guideButton, buildButton, cpuMode, frequency, listToggle);
                    case "computation_info" -> info;
                    default -> null;
                };
            }

            @Override
            public void configure(JsonObject node, UIElement element) {
                if ("player_inventory".equals(node.has("type") ? node.get("type").getAsString() : "")
                    && element instanceof InventorySlots inventory) {
                    inventory.hotbar.layout(layout -> layout.marginTop(4));
                    inventory.apply(slot -> slot.style(style -> style.backgroundTexture(IGuiTexture.EMPTY)));
                    inventory.getChildren().forEach(child -> child.style(style ->
                        style.backgroundTexture(IGuiTexture.EMPTY)));
                }
            }
        });
        rootRef[0] = root;
        root.setOverflowVisible(true);
        mainOf(root).addChild(buildWindow);
        return root;
    }

    private static UIElement mainOf(UIElement root) {
        return root.selectId("computation_main").findFirst().orElseThrow();
    }

    private static void setViewSize(UIElement root, int width, int height) {
        root.layout(layout -> layout.width(width).height(height));
    }

    /** Defers a screen re-init so ModularUI re-centres the root; never rebuilds widgets mid-dispatch. */
    private static void recenter() {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.execute(() -> {
            Screen screen = minecraft.screen;
            if (screen != null) screen.resize(minecraft, screen.width, screen.height);
        });
    }

    private static Button createSwitch(Config config, String key) {
        BooleanSupplier enabled;
        Runnable action;
        switch (key) {
            case "substitutions" -> {
                enabled = () -> !config.ignoringPatternSubstitutions().getAsBoolean();
                action = config.toggleIgnoringPatternSubstitutions();
            }
            case "cycle_planning" -> {
                enabled = config.cyclePlanningEnabled();
                action = config.toggleCyclePlanning();
            }
            case "fast_planner" -> {
                enabled = config.fastPlannerEnabled();
                action = config.toggleFastPlanner();
            }
            default -> throw new IllegalArgumentException("Unknown computation setting: " + key);
        }
        Button button = new Button().noText();
        button.setId("computation-switch-" + key);
        button.layout(layout -> layout.paddingAll(0));
        BindableValue<Boolean> value = new BindableValue<>(enabled.getAsBoolean());
        value.bind(DataBindingBuilder.boolS2C(enabled::getAsBoolean).build());
        value.registerValueListener(state -> switchTexture(button, Boolean.TRUE.equals(state)));
        value.setDisplay(false);
        button.addChild(value);
        BindableValue<Integer> count = "substitutions".equals(key)
            ? syncedPatternCount(button, config.substitutionPatternCount()) : null;
        switchTexture(button, enabled.getAsBoolean());
        button.setOnServerClick(event -> action.run());
        button.addEventListener(UIEvents.HOVER_TOOLTIPS, event -> {
            boolean on = Boolean.TRUE.equals(value.getValue());
            List<Component> lines = new ArrayList<>();
            lines.add(Component.translatable("gui.neoecoae.computation.settings." + key));
            lines.add(Component.translatable(on
                ? "gui.neoecoae.computation.ui.switch.on"
                : "gui.neoecoae.computation.ui.switch.off").withColor(on ? 0x55FF55 : 0xAAAAAA));
            lines.add(Component.translatable("gui.neoecoae.computation.ui.switch." + key + ".desc").withColor(0xAAAAAA));
            if (count != null) {
                lines.add(Component.translatable("gui.neoecoae.crafting.planning.substitution_pattern_count",
                    Math.max(0, count.getValue() == null ? 0 : count.getValue())));
            }
            event.hoverTooltips = new HoverTooltips(lines, null, null, null);
        });
        return button;
    }

    private static BindableValue<Integer> syncedPatternCount(Button button, IntSupplier count) {
        BindableValue<Integer> synced = new BindableValue<>(count.getAsInt());
        synced.bind(DataBindingBuilder.intValS2C(count::getAsInt).build());
        synced.setDisplay(false);
        button.addChild(synced);
        return synced;
    }

    private static void switchTexture(Button button, boolean enabled) {
        IGuiTexture normal = ComputationControllerTextures.switchSprite(enabled, false);
        IGuiTexture hover = ComputationControllerTextures.switchSprite(enabled, true);
        button.buttonStyle(style -> style.baseTexture(normal).hoverTexture(hover).pressedTexture(hover));
    }
}
