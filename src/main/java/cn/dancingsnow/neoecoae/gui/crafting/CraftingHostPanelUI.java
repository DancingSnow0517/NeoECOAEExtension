package cn.dancingsnow.neoecoae.gui.crafting;

import cn.dancingsnow.neoecoae.crafting.display.CraftingOutputDisplay;
import cn.dancingsnow.neoecoae.gui.common.HostSideButtonBar;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.inventory.InventorySlots;
import dev.vfyjxf.taffy.style.TaffyPosition;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

/** Assembles the controller UI; drawing, transport and events have dedicated components. */
public final class CraftingHostPanelUI {
    public static final int UI_WIDTH = 253;
    public static final int UI_HEIGHT = 214;
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
        CraftingControllerArtwork artwork = new CraftingControllerArtwork(config);
        UIElement root = new UIElement().layout(layout -> layout
            .positionType(TaffyPosition.RELATIVE).width(UI_WIDTH).height(UI_HEIGHT));
        root.setOverflowVisible(true);
        root.addChild(artwork);
        root.addChild(CraftingControllerInteractions.createModeButton(10, "overclock", config.overclocked, config.toggleOverclocked));
        root.addChild(CraftingControllerInteractions.createModeButton(37, "active_cooling", config.activeCooling, config.toggleActiveCooling));
        root.addChild(playerInventory());
        return root;
    }

    private static UIElement playerInventory() {
        InventorySlots inventory = new InventorySlots();
        inventory.layout(layout -> layout.positionType(TaffyPosition.ABSOLUTE).left(7).top(129).width(162).height(76));
        inventory.hotbar.layout(layout -> layout.marginTop(4));
        inventory.apply(slot -> slot.getStyle().backgroundTexture(IGuiTexture.EMPTY));
        inventory.getChildren().forEach(child -> child.getStyle().backgroundTexture(IGuiTexture.EMPTY));
        return inventory;
    }

    public static UIElement create(Config config, UIElement guide, UIElement buildButton,
                                   UIElement buildWindow, Supplier<Component> maintenanceStatus) {
        UIElement root = create(config);
        List<UIElement> buttons = new ArrayList<>();
        buttons.add(guide);
        buttons.add(buildButton);
        buttons.addAll(createToolbarButtons(config));
        root.addChild(HostSideButtonBar.grayLeft(buttons.toArray(UIElement[]::new)));
        root.addChild(createMaintenanceStatusIcon(maintenanceStatus));
        root.addChild(buildWindow);
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
        return (int) Math.ceil(10.0D / (Math.clamp(effectiveOverclockTimes, 0, 9) + 1));
    }
}
