package cn.dancingsnow.neoecoae.platform.nbt;

import appeng.api.config.FuzzyMode;
import appeng.api.orientation.RelativeSide;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import cn.dancingsnow.neoecoae.api.components.AutoExportSides;
import cn.dancingsnow.neoecoae.platform.PlatformTestBootstrap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.util.EnumSet;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class NEItemNbtTest {
    @BeforeAll static void bootstrap() throws Exception {
        PlatformTestBootstrap.initializeKeyRegistry();
    }

    @Test void readsDoNotCreateAnItemTag() {
        var stack = new ItemStack(Items.PAPER);
        assertTrue(NEItemData.read(stack).isEmpty());
        assertFalse(stack.hasTag());
        assertTrue(NECellNbt.readStacks(stack).isEmpty());
        assertFalse(stack.hasTag());
    }

    @Test void emptyDisplayStacksUseAnEmptyCompoundAndRoundTrip() {
        var tag = NEStacks.save(ItemStack.EMPTY, net.minecraft.core.RegistryAccess.EMPTY);
        assertTrue(tag.isEmpty());
        assertTrue(NEStacks.read(net.minecraft.core.RegistryAccess.EMPTY, tag).isEmpty());
    }

    @Test void updatesPreserveUnrelatedMetadataAndDoNotAliasCallerTags() {
        var stack = new ItemStack(Items.PAPER);
        var data = new CompoundTag();
        data.putString("unrelated", "keep");
        NEItemData.write(stack, data);
        data.putString("unrelated", "changed");
        NEItemData.update(stack, tag -> tag.putLong("receipt", Long.MAX_VALUE));
        assertEquals("keep", stack.getTag().getString("unrelated"));
        assertEquals(Long.MAX_VALUE, stack.getTag().getLong("receipt"));
        NEItemData.remove(stack, "receipt");
        assertEquals("keep", stack.getTag().getString("unrelated"));
    }

    @Test void failedUpdatesDoNotPublishAPartialTag() {
        var stack = new ItemStack(Items.PAPER);
        assertThrows(IllegalStateException.class, () -> NEItemData.update(stack, tag -> {
            tag.putInt("partial", 1);
            throw new IllegalStateException("test");
        }));
        assertFalse(stack.hasTag());
    }

    @Test void realAeKeysAndLongAmountsSurviveCellSaveReload() {
        var stack = new ItemStack(Items.PAPER);
        var keyStack = new ItemStack(Items.DIAMOND);
        keyStack.setTag(new CompoundTag());
        keyStack.getTag().putInt("grade", 7);
        var expected = List.of(new GenericStack(AEItemKey.of(keyStack), Long.MAX_VALUE));
        NECellNbt.writeStacks(stack, expected);
        var restoredItem = ItemStack.of(stack.save(new CompoundTag()));
        assertEquals(expected, NECellNbt.readStacks(restoredItem));
    }

    @Test void clearingContentsKeepsModeReceiptsAndOtherItemData() {
        var stack = new ItemStack(Items.PAPER);
        NEItemData.update(stack, root -> root.putString("unrelated", "keep"));
        NECellNbt.fuzzyMode(stack, FuzzyMode.PERCENT_50);
        NECellNbt.writeStacks(stack, List.of(new GenericStack(AEItemKey.of(Items.DIAMOND), 42)));
        NECellNbt.clear(stack);
        assertEquals("keep", stack.getTag().getString("unrelated"));
        assertEquals(FuzzyMode.PERCENT_50, NECellNbt.fuzzyMode(stack));
        assertTrue(NECellNbt.readStacks(stack).isEmpty());
    }

    @Test void unknownStorageKeysFailWithoutReplacingTheirOriginalPayload() {
        var stack = new ItemStack(Items.PAPER);
        var unknown = new CompoundTag();
        unknown.putString("#t", "missing:key_type");
        unknown.putLong("#", 42);
        var entries = new ListTag();
        entries.add(unknown);
        var data = new CompoundTag();
        data.putInt("format", 1);
        data.put("stacks", entries);
        NEItemData.update(stack, root -> root.put(NECellNbt.CONTENTS, data));
        var original = stack.getTag().copy();
        assertThrows(IllegalStateException.class, () -> NECellNbt.readStacks(stack));
        assertEquals(original, stack.getTag());
    }

    @Test void badAmountsCannotOverwriteExistingCellData() {
        var stack = new ItemStack(Items.PAPER);
        NECellNbt.writeStacks(stack, List.of(new GenericStack(AEItemKey.of(Items.DIAMOND), 42)));
        var original = stack.getTag().copy();
        assertThrows(IllegalArgumentException.class, () -> NECellNbt.writeStacks(stack,
            List.of(new GenericStack(AEItemKey.of(Items.DIAMOND), -1))));
        assertEquals(original, stack.getTag());
    }

    @Test void autoExportSettingsTakeAnImmutableSnapshotAndKeepEmptyDistinctFromMissing() {
        var sides = EnumSet.of(RelativeSide.FRONT, RelativeSide.TOP);
        var settings = new AutoExportSides(sides);
        sides.clear();
        var tag = new CompoundTag();
        assertNull(AutoExportSides.read(tag));
        settings.write(tag);
        assertEquals(EnumSet.of(RelativeSide.FRONT, RelativeSide.TOP), AutoExportSides.read(tag).sides());
        new AutoExportSides(sides).write(tag);
        assertTrue(AutoExportSides.read(tag).sides().isEmpty());
    }

    @Test void malformedAutoExportSettingsAreRejected() {
        var tag = new CompoundTag();
        tag.putIntArray(AutoExportSides.NBT_KEY, new int[]{-1});
        assertThrows(IllegalArgumentException.class, () -> AutoExportSides.read(tag));
        tag.putString(AutoExportSides.NBT_KEY, "wrong type");
        assertThrows(IllegalArgumentException.class, () -> AutoExportSides.read(tag));
    }
}
