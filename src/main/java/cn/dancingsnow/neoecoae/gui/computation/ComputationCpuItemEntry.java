package cn.dancingsnow.neoecoae.gui.computation;

import appeng.api.stacks.AEKey;
import cn.dancingsnow.neoecoae.crafting.display.format.BigNumberFormatter;
import cn.dancingsnow.neoecoae.crafting.display.format.DisplayNumbers;
import cn.dancingsnow.neoecoae.crafting.display.processing.ECOProcessingProgress;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;

/** One item row in the ECO crafting CPU status page. */
record ComputationCpuItemEntry(AEKey key, BigInteger stored, BigInteger active, BigInteger pending, boolean batched,
        ECOProcessingProgress processing, boolean stepStopped) {
    ComputationCpuItemEntry(AEKey key, BigInteger stored, BigInteger active, BigInteger pending, boolean batched) {
        this(key, stored, active, pending, batched, ECOProcessingProgress.EMPTY, false);
    }

    ComputationCpuItemEntry(AEKey key, BigInteger stored, BigInteger active, BigInteger pending, boolean batched,
            ECOProcessingProgress processing) {
        this(key, stored, active, pending, batched, processing, false);
    }

    ComputationCpuItemEntry withProcessing(ECOProcessingProgress value) {
        return new ComputationCpuItemEntry(key, stored, active, pending, batched, value, stepStopped);
    }

    String amount(BigInteger value, boolean full) {
        int unit = Math.max(1, key.getAmountPerUnit());
        String number = full
            ? DisplayNumbers.grouped(new BigDecimal(value).divide(BigDecimal.valueOf(unit), 6, RoundingMode.DOWN)
                .stripTrailingZeros().toPlainString())
            : BigNumberFormatter.formatSI(value, unit);
        String suffix = key.getUnitSymbol();
        return number + (suffix == null ? "" : " " + suffix);
    }

    CompoundTag write(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.put("key", key.toTagGeneric());
        tag.putByteArray("stored", stored.toByteArray());
        tag.putByteArray("active", active.toByteArray());
        tag.putByteArray("pending", pending.toByteArray());
        tag.putBoolean("batched", batched);
        if (stepStopped) tag.putBoolean("stepStopped", true);
        if (processing.machineCount() > 0 || processing.refreshing()) tag.put("processing", processing.write());
        return tag;
    }

    static ComputationCpuItemEntry read(CompoundTag tag, HolderLookup.Provider registries) {
        return new ComputationCpuItemEntry(
            AEKey.fromTagGeneric(tag.getCompound("key")),
            new BigInteger(tag.getByteArray("stored")),
            new BigInteger(tag.getByteArray("active")),
            new BigInteger(tag.getByteArray("pending")),
            tag.getBoolean("batched"), ECOProcessingProgress.read(tag.getCompound("processing")), tag.getBoolean("stepStopped"));
    }
}
