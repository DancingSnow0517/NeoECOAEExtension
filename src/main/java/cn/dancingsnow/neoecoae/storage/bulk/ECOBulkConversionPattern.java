package cn.dancingsnow.neoecoae.storage.bulk;

import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.IPatternDetailsDecoder;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import cn.dancingsnow.neoecoae.all.NEItems;
import cn.dancingsnow.neoecoae.platform.nbt.NEItemData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/** Stable, decodable virtual pattern. Definitions survive AE2 CPU save/reload. */
public final class ECOBulkConversionPattern implements IPatternDetails {
    private final AEItemKey from, to;
    private final long inputCount, outputCount;
    private volatile AEItemKey definition;

    public ECOBulkConversionPattern(AEItemKey from, long inputCount, AEItemKey to, long outputCount) {
        if (from == null || to == null || from.equals(to) || inputCount <= 0 || outputCount <= 0)
            throw new IllegalArgumentException("Invalid bulk conversion");
        this.from = from; this.to = to; this.inputCount = inputCount; this.outputCount = outputCount;
    }
    public AEItemKey inputKey() { return from; }
    public AEItemKey outputKey() { return to; }
    public long inputCount() { return inputCount; }
    public long outputCount() { return outputCount; }

    @Override public AEItemKey getDefinition() {
        if (definition == null) {
            var stack = new ItemStack(NEItems.ECO_BULK_PATTERN.get());
            NEItemData.write(stack, encode());
            definition = AEItemKey.of(stack);
        }
        return definition;
    }
    public CompoundTag encode() {
        CompoundTag data = new CompoundTag();
        data.put("from", BulkItemKeys.write(from)); data.putLong("input", inputCount);
        data.put("to", BulkItemKeys.write(to)); data.putLong("output", outputCount);
        return data;
    }
    public static ECOBulkConversionPattern decode(CompoundTag data) {
        return new ECOBulkConversionPattern(BulkItemKeys.read(data.getCompound("from")), data.getLong("input"),
            BulkItemKeys.read(data.getCompound("to")), data.getLong("output"));
    }
    @Override public IInput[] getInputs() {
        return new IInput[] { new IInput() {
            @Override public GenericStack[] getPossibleInputs() { return new GenericStack[] { new GenericStack(from, inputCount) }; }
            @Override public long getMultiplier() { return 1; }
            @Override public boolean isValid(AEKey input, Level level) { return from.equals(input); }
            @Override public @Nullable AEKey getRemainingKey(AEKey template) { return null; }
        } };
    }
    @Override public GenericStack[] getOutputs() { return new GenericStack[] { new GenericStack(to, outputCount) }; }
    @Override public boolean supportsPushInputsToExternalInventory() { return false; }
    public boolean matchesInputs(KeyCounter[] holders) {
        if (holders == null || holders.length != 1) return false;
        long amount = 0;
        for (var entry : holders[0]) {
            if (entry.getLongValue() == 0) continue;
            if (!from.equals(entry.getKey()) || entry.getLongValue() != inputCount) return false;
            amount = entry.getLongValue();
        }
        return amount == inputCount;
    }
    @Override public boolean equals(Object other) {
        return other instanceof ECOBulkConversionPattern pattern && from.equals(pattern.from) && to.equals(pattern.to)
            && inputCount == pattern.inputCount && outputCount == pattern.outputCount;
    }
    @Override public int hashCode() { return java.util.Objects.hash(from, inputCount, to, outputCount); }

    public static final IPatternDetailsDecoder DECODER = new IPatternDetailsDecoder() {
        @Override public boolean isEncodedPattern(ItemStack stack) { return stack.is(NEItems.ECO_BULK_PATTERN.get()); }
        @Override public @Nullable IPatternDetails decodePattern(AEItemKey key, Level level) {
            if (key == null || !isEncodedPattern(key.getReadOnlyStack())) return null;
            try {
                return decode(NEItemData.copy(key.getReadOnlyStack()));
            } catch (RuntimeException invalid) { return null; }
        }
        @Override public @Nullable IPatternDetails decodePattern(ItemStack stack, Level level, boolean tryRecovery) {
            return stack == null || stack.isEmpty() ? null : decodePattern(AEItemKey.of(stack), level);
        }
    };
}
