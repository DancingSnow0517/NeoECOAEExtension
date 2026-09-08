package cn.dancingsnow.neoecoae.api.me;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.execution.CraftingCpuHelper;
import appeng.crafting.inv.ListCraftingInventory;
import com.mojang.serialization.MapCodec;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

class ECOCraftingInputPreviewTest {
    @Test
    void ecoPreviewDoesNotSilentlyConsumeASubstitutionMember() {
        AEKey glass = TestKey.of("glass");
        AEKey pane = TestKey.of("glass_pane");
        ListCraftingInventory inventory = new ListCraftingInventory(ignored -> { });
        inventory.insert(pane, 4, Actionable.MODULATE);

        IPatternDetails pattern = new SubstitutionPattern(glass, pane);
        ECOCraftingInputPreview preview = new ECOCraftingInputPreview(inventory, pattern);

        assertTrue(keys(preview.findFuzzyTemplates(pane)).isEmpty());
        assertTrue(keys(preview.findFuzzyTemplates(glass)).isEmpty());
        assertEquals(4, inventory.extract(pane, Long.MAX_VALUE, Actionable.SIMULATE));

        assertNull(CraftingCpuHelper.extractPatternInputs(pattern, preview, null, new KeyCounter(), new KeyCounter()));

        inventory.insert(glass, 2, Actionable.MODULATE);
        assertEquals(List.of(glass), keys(preview.findFuzzyTemplates(glass)));
        assertTrue(keys(preview.findFuzzyTemplates(pane)).isEmpty());

        var inputs = CraftingCpuHelper.extractPatternInputs(pattern, preview, null,
            new KeyCounter(), new KeyCounter());
        assertNotNull(inputs);
        assertEquals(1, inputs[0].get(glass));
        assertEquals(0, inputs[0].get(pane));
    }

    private static List<AEKey> keys(Iterable<AEKey> values) {
        List<AEKey> result = new ArrayList<>();
        values.forEach(result::add);
        return result;
    }

    private record SubstitutionPattern(AEKey primary, AEKey substitute) implements IPatternDetails {
        @Override
        public appeng.api.stacks.AEItemKey getDefinition() {
            return null;
        }

        @Override
        public IInput[] getInputs() {
            return new IInput[] {new IInput() {
                @Override
                public GenericStack[] getPossibleInputs() {
                    return new GenericStack[] {new GenericStack(primary, 1), new GenericStack(substitute, 1)};
                }

                @Override
                public long getMultiplier() {
                    return 1;
                }

                @Override
                public boolean isValid(AEKey input, net.minecraft.world.level.Level level) {
                    return primary.equals(input) || substitute.equals(input);
                }

                @Override
                public AEKey getRemainingKey(AEKey input) {
                    return null;
                }
            }};
        }

        @Override
        public List<GenericStack> getOutputs() {
            return List.of(new GenericStack(primary, 1));
        }
    }

    private static final class TestKey extends AEKey {
        private static final AEKeyType TYPE = new Type();
        private final String name;

        private TestKey(String name) {
            this.name = name;
        }

        static TestKey of(String name) {
            return new TestKey(name);
        }

        @Override
        public AEKeyType getType() {
            return TYPE;
        }

        @Override
        public AEKey dropSecondary() {
            return this;
        }

        @Override
        public CompoundTag toTag(HolderLookup.Provider registries) {
            var tag = new CompoundTag();
            tag.putString("name", name);
            return tag;
        }

        @Override
        public Object getPrimaryKey() {
            return name;
        }

        @Override
        public ResourceLocation getId() {
            return ResourceLocation.fromNamespaceAndPath("neoecoae_test", name);
        }

        @Override
        public void writeToPacket(RegistryFriendlyByteBuf data) {
            throw new UnsupportedOperationException();
        }

        @Override
        protected Component computeDisplayName() {
            return Component.literal(name);
        }

        @Override
        public void addDrops(long amount, List<ItemStack> drops, Level level, BlockPos pos) {
        }

        @Override
        public boolean hasComponents() {
            return false;
        }

        @Override
        public boolean equals(Object object) {
            return object instanceof TestKey other && name.equals(other.name);
        }

        @Override
        public int hashCode() {
            return Objects.hash(name);
        }

        @Override
        public String toString() {
            return name;
        }

        private static final class Type extends AEKeyType {
            private Type() {
                super(ResourceLocation.fromNamespaceAndPath("neoecoae_test", "preview_keys"), TestKey.class,
                    Component.literal("Preview Test Keys"));
            }

            @Override
            public MapCodec<? extends AEKey> codec() {
                throw new UnsupportedOperationException();
            }

            @Override
            public AEKey readFromPacket(RegistryFriendlyByteBuf input) {
                throw new UnsupportedOperationException();
            }
        }
    }
}
