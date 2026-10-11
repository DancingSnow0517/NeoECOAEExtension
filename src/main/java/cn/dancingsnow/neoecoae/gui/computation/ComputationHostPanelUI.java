package cn.dancingsnow.neoecoae.gui.computation;

import appeng.api.config.CpuSelectionMode;
import cn.dancingsnow.neoecoae.crafting.execution.ECOCraftingCPU;
import cn.dancingsnow.neoecoae.gui.common.HostElements;
import cn.dancingsnow.neoecoae.gui.common.HostSideButtonBar;
import cn.dancingsnow.neoecoae.gui.theme.AETextures;
import cn.dancingsnow.neoecoae.gui.theme.ECOIcon;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.inventory.InventorySlots;
import dev.vfyjxf.taffy.style.TaffyPosition;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.Component;

/** Texture-backed controller; LDLib2 retains inventory, toolbar actions and menu synchronization. */
public final class ComputationHostPanelUI {

    private ComputationHostPanelUI() {}

    public record Config(
        Supplier<Component> title, BooleanSupplier connected, IntSupplier networkMultiplier,
        LongSupplier gameTime, BooleanSupplier canInteract,
        LongSupplier usedBytes, LongSupplier totalBytes, LongSupplier availableBytes,
        IntSupplier usedThreads, IntSupplier totalThreads, IntSupplier parallelCount,
        Supplier<CpuSelectionMode> cpuSelectionMode, IntConsumer adjustCpuSelectionMode,
        Supplier<HolderLookup.Provider> registries, Supplier<List<ECOCraftingCPU>> cpus,
        Consumer<ECOCraftingCPU> toggleCpuSuspended, Consumer<ECOCraftingCPU> cancelCpu,
        BooleanSupplier ignoringPatternSubstitutions, IntSupplier substitutionPatternCount,
        Runnable toggleIgnoringPatternSubstitutions, BooleanSupplier cyclePlanningEnabled,
        Runnable toggleCyclePlanning, BooleanSupplier fastPlannerEnabled, Runnable toggleFastPlanner,
        IntSupplier networkFrequency, IntConsumer adjustNetworkFrequency
    ) {}

    public static UIElement create(Config config, UIElement guideButton, UIElement buildButton, UIElement buildWindow) {
        UIElement root = new UIElement().layout(layout -> layout
            .width(ComputationCpuStatusPanel.WIDTH).height(ComputationCpuStatusPanel.HEIGHT).paddingAll(0));
        root.setOverflowVisible(true);
        var identities = new CpuSelectionState.Identities<ECOCraftingCPU>();
        ComputationCpuStatusPanel status = new ComputationCpuStatusPanel(config, identities);
        HostElements.absolute(status, 0, 0, ComputationCpuStatusPanel.WIDTH, ComputationCpuStatusPanel.HEIGHT);
        status.setDisplay(false);
        UIElement[] mainRef = new UIElement[1];
        UIElement main = new ComputationCpuPanel(config, serial -> {
            mainRef[0].setDisplay(false);
            status.setDisplay(true);
            status.open(serial);
        }, identities);
        mainRef[0] = main;
        int mainX = (ComputationCpuStatusPanel.WIDTH - ComputationCpuPanel.WIDTH) / 2;
        int mainY = (ComputationCpuStatusPanel.HEIGHT - ComputationCpuPanel.HEIGHT) / 2;
        HostElements.absolute(main, mainX, mainY, ComputationCpuPanel.WIDTH, ComputationCpuPanel.HEIGHT);
        InventorySlots inventory = new InventorySlots();
        inventory.layout(layout -> layout.positionType(TaffyPosition.ABSOLUTE)
            .left(7).top(129).width(162).height(77));
        inventory.hotbar.layout(layout -> layout.marginTop(4));
        inventory.apply(slot -> slot.style(style -> style.backgroundTexture(IGuiTexture.EMPTY)));
        inventory.getChildren().forEach(child -> child.style(style -> style.backgroundTexture(IGuiTexture.EMPTY)));
        main.addChild(inventory);
        ComputationSettingsPanel settings = new ComputationSettingsPanel(config);
        settings.layout(layout -> layout.left(mainX)
            .top(mainY + (ComputationCpuPanel.HEIGHT - ComputationSettingsPanel.HEIGHT) / 2));
        settings.setDisplay(false);
        Button open = HostSideButtonBar.createButton().noText().addPostIcon(AETextures.icon(ECOIcon.COG));
        open.setId("computation-settings-open");
        HostElements.tooltips(open, Component.translatable("gui.neoecoae.computation.settings.title"));
        open.setOnClick(event -> {
            main.setDisplay(false);
            settings.setDisplay(true);
        });
        settings.addBackButton(() -> {
            settings.setDisplay(false);
            main.setDisplay(true);
        });
        status.setBackAction(() -> {
            status.setDisplay(false);
            main.setDisplay(true);
        });
        Button cpuMode = createCpuSelectionButton(config);
        cpuMode.setId("computation-cpu-mode");
        Button frequency = createNetworkFrequencyButton(config);
        frequency.setId("computation-frequency");
        main.addChildren(HostSideButtonBar.left(guideButton, buildButton, cpuMode, frequency, open), buildWindow);
        root.addChildren(main, settings, status);
        return root;
    }

    public static Button createCpuSelectionButton(Config config) {
        return ComputationControllerInteractions.createCpuSelectionButton(config);
    }

    public static Button createNetworkFrequencyButton(Config config) {
        return ComputationControllerInteractions.createNetworkFrequencyButton(config);
    }

    static Component cpuSelectionModeTooltip(CpuSelectionMode mode) {
        return ComputationControllerInteractions.cpuSelectionModeTooltip(mode);
    }
}
