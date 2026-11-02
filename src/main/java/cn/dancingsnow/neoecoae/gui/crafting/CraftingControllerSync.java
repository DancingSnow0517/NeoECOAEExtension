package cn.dancingsnow.neoecoae.gui.crafting;

import cn.dancingsnow.neoecoae.crafting.display.CraftingOutputDisplay;
import cn.dancingsnow.neoecoae.gui.crafting.CraftingHostPanelUI.Config;
import com.lowdragmc.lowdraglib2.gui.sync.bindings.impl.DataBindingBuilder;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.BindableValue;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

/** NBT transport for the controller. Registration order stays identical on both sides. */
final class CraftingControllerSync {
    private final Config config;

    private CraftingControllerSync(Config config) {
        this.config = config;
    }

    static void attach(UIElement parent, Config config, CraftingControllerViewState view, Runnable onUpdate) {
        CraftingControllerSync codec = new CraftingControllerSync(config);
        CompoundTag initial = codec.snapshot();
        codec.read(initial, view);
        onUpdate.run();
        BindableValue<Tag> synced = new BindableValue<>(initial);
        synced.bind(DataBindingBuilder.tagS2C(codec::snapshot).build());
        synced.registerValueListener(value -> {
            codec.read(value, view);
            onUpdate.run();
        });
        synced.setDisplay(false);
        parent.addChild(synced);
    }
    private CompoundTag snapshot() {
        CompoundTag tag = config.panelState().get().toTag();
        tag.putLong("energyUsage", config.energyUsage().getAsLong());
        tag.putLong("performanceNanos", config.performanceAverageNanos().getAsLong());
        tag.putLong("queuedCraftCount", config.queuedCraftCount().getAsLong());
        tag.putLong("batchCapacity", config.batchCapacity().getAsLong());
        tag.putLong("ftParallelCapacity", config.ftParallelCapacity().getAsLong());
        tag.putLong("overflowCapacity", config.overflowCapacity().getAsLong());
        tag.putInt("coolantAmount", config.coolantAmount().getAsInt());
        tag.putInt("coolantCapacity", config.coolantCapacity().getAsInt());
        tag.putInt("coolantMaxOverclock", config.coolantMaxOverclock().getAsInt());
        tag.putInt("outputCapacity", config.outputCapacity().getAsInt());
        tag.putInt("activeWorkerCores", config.activeWorkerCores().getAsInt());
        tag.putInt("workerCores", config.workerCores().getAsInt());
        tag.putInt("singleCoreCapacity", config.singleCoreCapacity().getAsInt());
        tag.putInt("theoreticalOverclockTimes", config.theoreticalOverclockTimes().getAsInt());
        tag.putInt("effectiveOverclockTimes", config.effectiveOverclockTimes().getAsInt());
        tag.putInt("executionTicks", config.executionTicks().getAsInt());
        tag.putInt("networkMultiplier", config.networkMultiplier().getAsInt());
        tag.putInt("taskCount", taskCount());
        tag.putInt("normalSwitchCount", config.normalSwitchCount().getAsInt());
        tag.putInt("highEnergySwitchCount", config.highEnergySwitchCount().getAsInt());
        tag.putBoolean("networkConnected", config.networkConnected().getAsBoolean());
        tag.putBoolean("overclocked", config.overclocked().getAsBoolean());
        tag.putBoolean("virtualMode", config.virtualMode().getAsBoolean());
        tag.putBoolean("activeCooling", config.activeCooling().getAsBoolean());
        tag.put("coolantFluid", saveFluid(config.coolantFluid().get()));
        tag.put("outputFluid", saveFluid(config.outputFluid().get()));
        ListTag items = new ListTag();
        List<CraftingOutputDisplay> outputs = config.workerOutputs().get();
        HolderLookup.Provider registries = config.registries().get();
        for (int cell = 0; cell < CraftingControllerPanelState.SLOT_COUNT; cell++) {
            CraftingOutputDisplay output = cell < outputs.size() ? outputs.get(cell) : CraftingOutputDisplay.EMPTY;
            CompoundTag entry = new CompoundTag();
            if (!output.item().isEmpty()) {
                entry.put("item", output.item().saveOptional(registries));
                entry.putString("amount", output.amount().toString());
            }
            items.add(entry);
        }
        tag.put("workerOutputs", items);
        return tag;
    }

    private int taskCount() {
        return config.tasks().get().size();
    }

    private CompoundTag saveFluid(FluidStack fluid) {
        if (fluid.isEmpty()) return new CompoundTag();
        Tag saved = fluid.saveOptional(config.registries().get());
        return saved instanceof CompoundTag compound ? compound : new CompoundTag();
    }

    private FluidStack loadFluid(CompoundTag tag, Supplier<FluidStack> fallbackSupplier) {
        FluidStack parsed = FluidStack.parseOptional(config.registries().get(), tag);
        if (!parsed.isEmpty()) return parsed;
        FluidStack fallback = fallbackSupplier.get();
        return fallback.isEmpty() ? FluidStack.EMPTY : fallback.copy();
    }

    void read(Tag value, CraftingControllerViewState view) {
        CompoundTag tag = value instanceof CompoundTag compound ? compound : new CompoundTag();
        view.state = CraftingControllerPanelState.fromTag(tag);
        view.energyUsage = Math.max(0L, tag.getLongOr("energyUsage", 0L));
        view.performanceAverageNanos = Math.max(0L, tag.getLongOr("performanceNanos", 0L));
        view.queuedCraftCount = Math.max(0L, tag.getLongOr("queuedCraftCount", 0L));
        view.batchCapacity = Math.max(0L, tag.getLongOr("batchCapacity", 0L));
        view.ftParallelCapacity = Math.max(0L, tag.getLongOr("ftParallelCapacity", 0L));
        view.overflowCapacity = Math.max(0L, tag.getLongOr("overflowCapacity", 0L));
        view.coolantAmount = Math.max(0, tag.getIntOr("coolantAmount", 0));
        view.coolantCapacity = Math.max(0, tag.getIntOr("coolantCapacity", 0));
        view.coolantMaxOverclock = tag.getIntOr("coolantMaxOverclock", 0);
        view.outputCapacity = Math.max(0, tag.getIntOr("outputCapacity", 0));
        view.activeWorkerCores = Math.max(0, tag.getIntOr("activeWorkerCores", 0));
        view.workerCores = Math.max(0, tag.getIntOr("workerCores", 0));
        view.singleCoreCapacity = Math.max(0, tag.getIntOr("singleCoreCapacity", 0));
        view.theoreticalOverclockTimes = Math.max(0, tag.getIntOr("theoreticalOverclockTimes", 0));
        view.effectiveOverclockTimes = Math.max(0, tag.getIntOr("effectiveOverclockTimes", 0));
        view.executionTicks = Math.max(1, tag.getIntOr("executionTicks", 0));
        view.networkMultiplier = Math.max(1, tag.getIntOr("networkMultiplier", 0));
        view.taskCount = Math.max(0, tag.getIntOr("taskCount", 0));
        view.normalSwitchCount = Math.max(0, tag.getIntOr("normalSwitchCount", 0));
        view.highEnergySwitchCount = Math.max(0, tag.getIntOr("highEnergySwitchCount", 0));
        view.networkConnected = tag.getBooleanOr("networkConnected", false);
        view.overclocked = tag.getBooleanOr("overclocked", false);
        view.virtualMode = tag.getBooleanOr("virtualMode", false);
        view.activeCooling = tag.getBooleanOr("activeCooling", false);
        view.coolantFluid = loadFluid(tag.getCompoundOrEmpty("coolantFluid"), config.coolantFluid());
        view.outputFluid = loadFluid(tag.getCompoundOrEmpty("outputFluid"), config.outputFluid());
        ListTag items = tag.getListOrEmpty("workerOutputs");
        HolderLookup.Provider registries = config.registries().get();
        List<CraftingOutputDisplay> outputs = new ArrayList<>();
        for (int cell = 0; cell < CraftingControllerPanelState.SLOT_COUNT; cell++) {
            CompoundTag entry = items.getCompoundOrEmpty(cell);
            ItemStack item = ItemStack.parseOptional(registries, entry.getCompoundOrEmpty("item"));
            BigInteger amount = item.isEmpty() ? BigInteger.ZERO : new BigInteger(entry.getStringOr("amount", ""));
            outputs.add(new CraftingOutputDisplay(item, amount));
        }
        view.workerOutputs = List.copyOf(outputs);
    }
}
