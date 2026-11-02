package cn.dancingsnow.neoecoae.gui.computation;

import appeng.api.config.CpuSelectionMode;
import appeng.api.stacks.AEKey;
import cn.dancingsnow.neoecoae.crafting.ECOLegacyAENbt;
import cn.dancingsnow.neoecoae.crafting.execution.ECOCraftingCPU;
import cn.dancingsnow.neoecoae.crafting.display.format.BigNumberFormatter;
import cn.dancingsnow.neoecoae.crafting.display.format.DisplayNumbers;
import cn.dancingsnow.neoecoae.crafting.display.format.NEByteFormatter;
import cn.dancingsnow.neoecoae.gui.common.HostText;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

/** Compact status shared by the CPU row, selected summary and tooltips, including fluid targets. */
record ComputationCpuEntry(int serial, String name, @Nullable AEKey output,
        BigInteger requested, BigInteger remaining, float progress, long elapsed,
        long storage, int parallel, CpuSelectionMode mode, Identifier overlay, String status) {
    String amount(BigInteger value, boolean full) {
        int unit = output == null ? 1 : Math.max(1, output.getAmountPerUnit());
        String number = full ? DisplayNumbers.grouped(new BigDecimal(value)
            .divide(BigDecimal.valueOf(unit), 6, RoundingMode.DOWN).stripTrailingZeros().toPlainString())
            : BigNumberFormatter.formatSI(value, unit);
        String suffix = output == null ? null : output.getUnitSymbol();
        return number + (suffix == null ? "" : " " + suffix);
    }

    String storageText() { return NEByteFormatter.formatCpuStorage(storage); }
    String fullStorageText() { return storageText() + " (" + HostText.expandedNumber(storage) + " B)"; }

    static ComputationCpuEntry sample(ECOCraftingCPU cpu, int serial) {
        var logic = cpu.getLogic();
        var output = logic.getFinalJobOutput();
        if (output == null && cpu.getPlan() != null) output = cpu.getPlan().finalOutput();
        var view = cpu.getProgressView();
        var parent = view.bigOrder().orElse(null);
        BigInteger requested = parent == null ? BigInteger.valueOf(output == null ? 0 : output.amount()) : parent.requested();
        BigInteger remaining = parent == null ? BigInteger.valueOf(logic.getRemainingJobOutputAmount()) : parent.remaining();
        float progress = parent == null ? view.progress() : parent.requested().signum() == 0 ? 0
            : new BigDecimal(parent.completed()).divide(new BigDecimal(parent.requested()), 6, RoundingMode.DOWN).floatValue();
        String status = logic.getPermanentExecutionError() != null ? "error"
            : logic.isJobSuspended() ? "suspended"
            : logic.isCantStoreItems() || (!logic.hasJob() && cpu.hasRemainingItems()) ? "returning"
            : logic.hasJob() ? "running" : "idle";
        return new ComputationCpuEntry(serial, cpu.getName() == null ? "" : cpu.getName().getString(),
            output == null ? null : output.what(), requested.max(BigInteger.ZERO), remaining.max(BigInteger.ZERO),
            Float.isFinite(progress) ? Math.clamp(progress, 0, 1) : 0, Math.max(0, view.elapsedTimeNanos()),
            cpu.getAvailableStorage(), cpu.getCoProcessors(), cpu.getSelectionMode(), cpu.getTier().getCPUOverlayTexture(), status);
    }

    CompoundTag write(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("serial", serial);
        tag.putString("name", name);
        if (output != null) tag.put("output", ECOLegacyAENbt.writeKey(registries, output));
        tag.putByteArray("requested", requested.toByteArray());
        tag.putByteArray("remaining", remaining.toByteArray());
        tag.putFloat("progress", progress);
        tag.putLong("elapsed", elapsed);
        tag.putLong("storage", storage);
        tag.putInt("parallel", parallel);
        tag.putInt("mode", mode.ordinal());
        tag.putString("overlay", overlay.toString());
        tag.putString("status", status);
        return tag;
    }

    static ComputationCpuEntry read(CompoundTag tag, HolderLookup.Provider registries) {
        return new ComputationCpuEntry(tag.getIntOr("serial", 0), tag.getStringOr("name", ""),
            tag.contains("output") ? ECOLegacyAENbt.readKey(registries, tag.getCompoundOrEmpty("output")) : null,
            new BigInteger(tag.getByteArray("requested").orElseGet(() -> new byte[0])), new BigInteger(tag.getByteArray("remaining").orElseGet(() -> new byte[0])),
            tag.getFloatOr("progress", 0F), tag.getLongOr("elapsed", 0L), tag.getLongOr("storage", 0L), tag.getIntOr("parallel", 0),
            CpuSelectionMode.values()[Math.clamp(tag.getIntOr("mode", 0), 0, CpuSelectionMode.values().length - 1)],
            Identifier.parse(tag.getStringOr("overlay", "")), tag.getStringOr("status", ""));
    }
}
