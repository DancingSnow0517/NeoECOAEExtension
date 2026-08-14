package cn.dancingsnow.neoecoae.compat.extendedae;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class ExtendedAEPlusCraftingPlanCompatTest {
    @Test
    void unwrapsForcedPlanAndCopiesMissingItems() {
        var key = new TestKey("missing");
        var missing = new KeyCounter();
        missing.add(key, 7L);
        var original = new TestPlan();
        var forced = new ForcedPlan(original, missing);

        assertSame(original, ExtendedAEPlusCraftingPlanCompat.unwrap(forced));
        assertEquals(7L, ExtendedAEPlusCraftingPlanCompat.getManualMissingItems(forced).get(key));
        assertNull(ExtendedAEPlusCraftingPlanCompat.getManualMissingItems(original));
    }

    private static final class ForcedPlan implements ICraftingPlan {
        private final ICraftingPlan delegate;
        private final KeyCounter missing;

        private ForcedPlan(ICraftingPlan delegate, KeyCounter missing) {
            this.delegate = delegate;
            this.missing = missing;
        }

        public KeyCounter eap$getManualMissingItems() {
            return this.missing;
        }

        @Override public GenericStack finalOutput() { return delegate.finalOutput(); }
        @Override public long bytes() { return delegate.bytes(); }
        @Override public boolean simulation() { return false; }
        @Override public boolean multiplePaths() { return delegate.multiplePaths(); }
        @Override public KeyCounter usedItems() { return delegate.usedItems(); }
        @Override public KeyCounter emittedItems() { return delegate.emittedItems(); }
        @Override public KeyCounter missingItems() { return new KeyCounter(); }
        @Override public Map<IPatternDetails, Long> patternTimes() { return delegate.patternTimes(); }
    }

    private static final class TestPlan implements ICraftingPlan {
        @Override public GenericStack finalOutput() { return null; }
        @Override public long bytes() { return 0L; }
        @Override public boolean simulation() { return true; }
        @Override public boolean multiplePaths() { return false; }
        @Override public KeyCounter usedItems() { return new KeyCounter(); }
        @Override public KeyCounter emittedItems() { return new KeyCounter(); }
        @Override public KeyCounter missingItems() { return new KeyCounter(); }
        @Override public Map<IPatternDetails, Long> patternTimes() { return Map.of(); }
    }

    private static final class TestKey extends AEKey {
        private final String id;

        private TestKey(String id) {
            this.id = id;
        }

        @Override public AEKeyType getType() { return null; }
        @Override public AEKey dropSecondary() { return this; }
        @Override public net.minecraft.nbt.CompoundTag toTag(net.minecraft.core.HolderLookup.Provider registries) {
            return new net.minecraft.nbt.CompoundTag();
        }
        @Override public Object getPrimaryKey() { return id; }
        @Override public net.minecraft.resources.ResourceLocation getId() {
            return net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("test", id);
        }
        @Override public void writeToPacket(net.minecraft.network.RegistryFriendlyByteBuf data) { }
        @Override protected net.minecraft.network.chat.Component computeDisplayName() {
            return net.minecraft.network.chat.Component.literal(id);
        }
        @Override public void addDrops(long amount, List<net.minecraft.world.item.ItemStack> drops,
                net.minecraft.world.level.Level level, net.minecraft.core.BlockPos pos) { }
        @Override public boolean hasComponents() { return false; }
    }
}
