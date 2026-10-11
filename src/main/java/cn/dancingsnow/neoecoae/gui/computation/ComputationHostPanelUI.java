package cn.dancingsnow.neoecoae.gui.computation;

import appeng.api.config.CpuSelectionMode;
import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.crafting.execution.ECOCraftingCPU;
import cn.dancingsnow.neoecoae.gui.common.HostElements;
import cn.dancingsnow.neoecoae.gui.common.HostSideButtonBar;
import cn.dancingsnow.neoecoae.gui.json.NeoUiJsonContext;
import cn.dancingsnow.neoecoae.gui.json.NeoUiJsonLoader;
import cn.dancingsnow.neoecoae.gui.theme.AETextures;
import cn.dancingsnow.neoecoae.gui.theme.ECOIcon;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.inventory.InventorySlots;
import com.google.gson.JsonObject;
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
        var identities = new CpuSelectionState.Identities<ECOCraftingCPU>();
        ComputationCpuStatusPanel status = new ComputationCpuStatusPanel(config, identities);
        status.setDisplay(false);
        UIElement[] mainRef = new UIElement[1];
        UIElement main = new ComputationCpuPanel(config, serial -> {
            mainRef[0].setDisplay(false);
            status.setDisplay(true);
            status.open(serial);
        }, identities);
        mainRef[0] = main;
        ComputationSettingsPanel settings = new ComputationSettingsPanel(config, false);
        settings.setDisplay(false);
        Button open = HostSideButtonBar.createButton().noText().addPostIcon(AETextures.icon(ECOIcon.COG));
        open.setId("computation-settings-open");
        HostElements.tooltips(open, Component.translatable("gui.neoecoae.computation.settings.title"));
        open.setOnClick(event -> {
            main.setDisplay(false);
            settings.setDisplay(true);
        });
        status.setBackAction(() -> {
            status.setDisplay(false);
            main.setDisplay(true);
        });
        Button cpuMode = createCpuSelectionButton(config);
        cpuMode.setId("computation-cpu-mode");
        Button frequency = createNetworkFrequencyButton(config);
        frequency.setId("computation-frequency");
        UIElement root = NeoUiJsonLoader.load(NeoECOAE.id("ui/computation_controller"),
            new NeoUiJsonContext() {
                @Override
                public UIElement createCustom(JsonObject node) {
                    return switch (node.has("name") ? node.get("name").getAsString() : "") {
                        case "computation_main" -> main;
                        case "computation_settings" -> settings;
                        case "computation_status" -> status;
                        case "computation_toolbar" -> HostSideButtonBar.left(guideButton, buildButton,
                            cpuMode, frequency, open);
                        case "computation_settings_title" -> ComputationSettingsPanel.settingLabel(
                            214, 14, () -> Component.translatable("gui.neoecoae.computation.settings.title"));
                        case "computation_settings_substitution_count" -> ComputationSettingsPanel.settingLabel(
                            207, 11, () -> Component.translatable(
                                "gui.neoecoae.crafting.planning.substitution_pattern_count",
                                Math.max(0, config.substitutionPatternCount().getAsInt())));
                        case "computation_settings_label" -> ComputationSettingsPanel.settingLabel(
                            207, 14, () -> Component.translatable("gui.neoecoae.computation.settings."
                                + (node.has("key") ? node.get("key").getAsString() : "")));
                        case "computation_settings_toggle" -> ComputationSettingsPanel.createToggle(config,
                            node.has("key") ? node.get("key").getAsString() : "");
                        case "computation_settings_back" -> new ComputationSettingsPanel.BackButton().noText()
                            .layout(layout -> layout.paddingAll(0));
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
                    if ("computation_settings_back".equals(node.has("name") ? node.get("name").getAsString() : "")) {
                        Button back = (Button) element;
                        back.setOnClick(event -> {
                            settings.setDisplay(false);
                            main.setDisplay(true);
                        });
                        HostElements.tooltips(back,
                            Component.translatable("gui.neoecoae.computation.settings.back"));
                    }
                }
            });
        root.setOverflowVisible(true);
        root.selectId("computation-main").findFirst().ifPresent(element -> element.addChild(buildWindow));
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
