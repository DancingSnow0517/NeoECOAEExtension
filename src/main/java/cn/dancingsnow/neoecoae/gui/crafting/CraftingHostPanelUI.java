package cn.dancingsnow.neoecoae.gui.crafting;

import cn.dancingsnow.neoecoae.platform.JdkCompat;

import cn.dancingsnow.neoecoae.api.IECOTier;
import cn.dancingsnow.neoecoae.gui.common.HostPanelTextureState;
import cn.dancingsnow.neoecoae.gui.common.HostPanelTextureSync;
import cn.dancingsnow.neoecoae.crafting.display.CraftingOutputDisplay;
import cn.dancingsnow.neoecoae.gui.common.HostSideButtonBar;
import cn.dancingsnow.neoecoae.gui.common.MultiblockBuildUI;
import cn.dancingsnow.neoecoae.gui.json.NeoUiJsonContext;
import cn.dancingsnow.neoecoae.gui.json.NeoUiJsonLoader;
import com.google.gson.JsonObject;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.texture.IGuiTexture;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.UIElement;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.elements.Button;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.elements.inventory.InventorySlots;
import cn.dancingsnow.neoecoae.platform.ui.runtime.layout.TaffyPosition;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.fluids.FluidStack;

/** Assembles the controller UI; drawing, transport and events have dedicated components. */
public final class CraftingHostPanelUI {
    public static final int UI_WIDTH = 253;
    public static final int UI_HEIGHT = 214;
    /** Static geometry and element order for the controller panel. */
    private static final ResourceLocation JSON_LAYOUT =
        new ResourceLocation("neoecoae", "screens/crafting/crafting_controller");
    private CraftingHostPanelUI() {}

    public record Config(
        Supplier<Component> title, Supplier<Component> statusNotice, IntSupplier networkMultiplier,
        BooleanSupplier networkConnected, BooleanSupplier overclocked, BooleanSupplier virtualMode, Runnable toggleOverclocked,
        BooleanSupplier activeCooling, Runnable toggleActiveCooling, IntSupplier activeWorkerCores,
        IntSupplier workerCores, IntSupplier singleCoreCapacity, LongSupplier ftParallelCapacity,
        LongSupplier overflowCapacity, IntSupplier theoreticalOverclockTimes, IntSupplier effectiveOverclockTimes,
        IntSupplier executionTicks, LongSupplier performanceAverageNanos, LongSupplier energyUsage,
        IntSupplier coolantAmount, IntSupplier coolantCapacity, IntSupplier coolantMaxOverclock,
        Supplier<FluidStack> coolantFluid, Supplier<FluidStack> outputFluid, IntSupplier outputCapacity,
        Supplier<HolderLookup.Provider> registries, Supplier<? extends java.util.Collection<?>> tasks,
        BooleanSupplier ignoringPatternSubstitutions, IntSupplier substitutionPatternCount,
        Runnable toggleIgnoringPatternSubstitutions, IntSupplier networkFrequency, IntConsumer adjustNetworkFrequency,
        Supplier<CraftingControllerPanelState> panelState, LongSupplier queuedCraftCount,
        LongSupplier batchCapacity, IntSupplier normalSwitchCount, IntSupplier highEnergySwitchCount,
        Supplier<ItemStack> hostItem, Supplier<List<CraftingOutputDisplay>> workerOutputs
    ) {}

    public static UIElement create(Config config) {
        return create(config, () -> null);
    }

    public static UIElement create(Config config, Supplier<IECOTier> panelTier) {
        return create(config, panelTier, () -> null);
    }

    public static UIElement create(Config config, Supplier<IECOTier> panelTier, Supplier<BlockState> hostState) {
        var textures = new HostPanelTextureSync(hostState, HostPanelTextureState.Panel.CRAFTING);
        UIElement root = NeoUiJsonLoader.load(JSON_LAYOUT, new JsonContext(config, panelTier, textures));
        root.addChild(textures);
        return root;
    }

    /**
     * The JSON definition owns the inventory position.  Keeping the optional legacy geometry
     * makes this helper safe for old callers and for resource-load diagnostics.
     */
    private static UIElement playerInventory(boolean withLayout) {
        InventorySlots inventory = new InventorySlots();
        if (withLayout) {
            inventory.layout(layout -> layout.positionType(TaffyPosition.ABSOLUTE)
                .left(7).top(129).width(162).height(76));
        }
        inventory.hotbar.layout(layout -> layout.marginTop(4));
        inventory.apply(slot -> slot.getStyle().backgroundTexture(IGuiTexture.EMPTY));
        inventory.getChildren().forEach(child -> child.getStyle().backgroundTexture(IGuiTexture.EMPTY));
        return inventory;
    }

    private static UIElement playerInventory() {
        return playerInventory(true);
    }

    /** Resource context for the static controller tree.  Dynamic artwork and behavior remain Java. */
    private static final class JsonContext implements NeoUiJsonContext {
        private final Config config;
        private final Supplier<IECOTier> panelTier;
        private final HostPanelTextureSync textures;

        private JsonContext(Config config, Supplier<IECOTier> panelTier, HostPanelTextureSync textures) {
            this.config = config;
            this.panelTier = panelTier;
            this.textures = textures;
        }

        @Override
        public IECOTier panelTier() { return panelTier.get(); }

        @Override
        public UIElement createCustom(JsonObject definition) {
            String custom = string(definition, "custom", string(definition, "id", ""));
            return switch (custom) {
                case "crafting_artwork" -> new CraftingControllerArtwork(config, panelTier, textures);
                case "mode_button" -> modeButton(definition);
                case "player_inventory" -> playerInventory(false);
                default -> throw new IllegalArgumentException("Unknown crafting UI custom element: " + custom);
            };
        }

        @Override
        public void configure(JsonObject definition, UIElement element) {
            // Geometry, classes and ordering are interpreted by NeoUiJsonLoader.  The custom
            // factories above inject the corresponding binding and server action, while keeping
            // this hook available for future static nodes and loader diagnostics.
            if ("crafting_controller_root".equals(string(definition, "id", ""))) {
                element.setOverflowVisible(true);
            }
            String action = string(definition, "action", "");
            if (!action.isEmpty() && !isKnownAction(action)) {
                throw new IllegalArgumentException("Unknown crafting UI action: " + action);
            }
            String binding = string(definition, "binding", "");
            if (!binding.isEmpty() && !isKnownBinding(binding)) {
                throw new IllegalArgumentException("Unknown crafting UI binding: " + binding);
            }
        }

        private Button modeButton(JsonObject definition) {
            String mode = string(definition, "mode", "");
            return switch (mode) {
                case "overclock" -> CraftingControllerInteractions.createModeButton(
                    mode, config.overclocked(), config.toggleOverclocked(), panelTier, textures);
                case "active_cooling" -> CraftingControllerInteractions.createModeButton(
                    mode, config.activeCooling(), config.toggleActiveCooling(), panelTier, textures);
                default -> throw new IllegalArgumentException("Unknown crafting mode: " + mode);
            };
        }

        private static boolean isKnownAction(String action) {
            return switch (action) {
                case "toggle_overclocked", "toggle_active_cooling" -> true;
                default -> false;
            };
        }

        private static boolean isKnownBinding(String binding) {
            return switch (binding) {
                case "overclocked", "active_cooling" -> true;
                default -> false;
            };
        }

        private static String string(JsonObject definition, String name, String fallback) {
            return definition.has(name) && definition.get(name).isJsonPrimitive()
                ? definition.get(name).getAsString() : fallback;
        }
    }

    public static UIElement create(Config config, UIElement guide, Supplier<Component> maintenanceStatus) {
        return create(config, guide, maintenanceStatus, () -> null);
    }

    public static UIElement create(Config config, UIElement guide,
        Supplier<Component> maintenanceStatus, Supplier<IECOTier> panelTier) {
        return create(config, guide, maintenanceStatus, panelTier, () -> null);
    }

    public static UIElement create(Config config, UIElement guide,
        Supplier<Component> maintenanceStatus, Supplier<IECOTier> panelTier,
        Supplier<BlockState> hostState) {
        return create(config, guide, maintenanceStatus, panelTier, hostState, null);
    }

    public static UIElement create(Config config, UIElement guide,
        Supplier<Component> maintenanceStatus, Supplier<IECOTier> panelTier,
        Supplier<BlockState> hostState, MultiblockBuildUI.Elements builder) {
        UIElement root = create(config, panelTier, hostState);
        List<UIElement> buttons = new ArrayList<>();
        buttons.add(guide);
        buttons.addAll(createToolbarButtons(config));
        if (builder != null) buttons.add(builder.openButton());
        root.addChild(HostSideButtonBar.grayLeft(buttons.toArray(UIElement[]::new)));
        root.addChild(createMaintenanceStatusIcon(maintenanceStatus));
        if (builder != null) root.addChild(builder.window());
        return root;
    }

    public static List<Button> createToolbarButtons(Config config) {
        return CraftingControllerInteractions.createToolbarButtons(config);
    }

    public static UIElement createMaintenanceStatusIcon(Supplier<Component> status) {
        return CraftingControllerInteractions.createMaintenanceStatusIcon(status);
    }

    static List<Component> maintenanceTooltipLines(Component status) {
        return CraftingControllerInteractions.maintenanceTooltipLines(status);
    }

    public static int formatRecipeTimeTicks(int effectiveOverclockTimes) {
        return (int) Math.ceil(10.0D / (JdkCompat.clamp(effectiveOverclockTimes, 0, 9) + 1));
    }
}
