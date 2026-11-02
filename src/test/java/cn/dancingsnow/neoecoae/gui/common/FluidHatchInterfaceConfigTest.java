package cn.dancingsnow.neoecoae.gui.common;

import appeng.api.behaviors.ContainerItemStrategies;
import appeng.api.behaviors.EmptyingAction;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEKeyTypes;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.GenericStack;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import java.util.HashMap;
import java.math.BigDecimal;
import java.util.Set;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FluidHatchInterfaceConfigTest {
    @BeforeAll static void bootstrap() { InventoryTestBootstrap.initialize(); }

    @Test
    void fluidLabelsUseAe2BucketUnitsAndFullTooltipAmounts() {
        var water = AEFluidKey.of(Fluids.WATER);
        assertEquals("300", FluidHatchInterfaceConfig.slotAmount(water, 300_000));
        assertEquals("2.5", FluidHatchInterfaceConfig.slotAmount(water, 2500));
        assertEquals("1", FluidHatchInterfaceConfig.slotAmount(water, 1000));
        for (long amount : new long[]{1, 999, 1000, 2500, 300000, Long.MAX_VALUE}) {
            assertEquals(water.formatAmount(amount, appeng.api.stacks.AmountFormat.SLOT),
                FluidHatchInterfaceConfig.slotAmount(water, amount));
            assertEquals(amount, FluidHatchStockAmounts.checkedValue(BigDecimal.valueOf(amount, 3), 1000, 0, Long.MAX_VALUE).orElseThrow());
        }
        assertEquals(2500, amount("2.5").orElseThrow());
        assertEquals(1, amount("0.0001").orElseThrow());
        assertEquals(Long.MAX_VALUE, amount("9223372036854775.807").orElseThrow());
        for (String invalid : new String[]{"-1", "-0.0000001", "9223372036854775.808", "18446744073709551.616"}) {
            assertTrue(amount(invalid).isEmpty(), invalid);
        }
    }

    @Test
    void usesNativeInterfaceContainerAndFakeSlotClickSemantics() {
        var water = AEFluidKey.of(Fluids.WATER);
        var lava = AEFluidKey.of(Fluids.LAVA);
        var bucket = new ItemStack(Items.WATER_BUCKET);
        var wrappers = new HashMap<Integer, GenericStack>();
        try (var types = mockStatic(AEKeyTypes.class); var stacks = mockStatic(GenericStack.class, CALLS_REAL_METHODS);
             var containers = mockStatic(ContainerItemStrategies.class)) {
            types.when(AEKeyTypes::getAll).thenReturn(Set.of(AEKeyType.fluids()));
            stacks.when(() -> GenericStack.wrapInItemStack(any(GenericStack.class))).thenAnswer(call -> {
                GenericStack generic = call.getArgument(0);
                if (generic == null) return ItemStack.EMPTY;
                var wrapper = new ItemStack(Items.PAPER);
                var tag = new CompoundTag();
                int id = wrappers.size() + 1;
                tag.putInt("wrapper", id);
                wrapper.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
                wrappers.put(id, generic);
                return wrapper;
            });
            stacks.when(() -> GenericStack.unwrapItemStack(any(ItemStack.class))).thenAnswer(call -> {
                ItemStack wrapper = call.getArgument(0);
                var data = wrapper.get(DataComponents.CUSTOM_DATA);
                return wrapper.isEmpty() || data == null ? null : wrappers.get(data.copyTag().getInt("wrapper"));
            });
            containers.when(() -> ContainerItemStrategies.getEmptyingAction(bucket))
                .thenReturn(new EmptyingAction(Component.literal("Water"), water, 1000));

            assertNull(FluidHatchInterfaceConfig.click(null, bucket, 0)); // This hatch accepts fluids, not bucket items.
            var marked = FluidHatchInterfaceConfig.click(null, bucket, 1);
            assertEquals(new GenericStack(water, 1000), marked);
            assertNull(FluidHatchInterfaceConfig.click(marked, ItemStack.EMPTY, 1));
            assertNull(FluidHatchInterfaceConfig.click(marked, ItemStack.EMPTY, 0));
            assertEquals(new GenericStack(water, 1000), FluidHatchInterfaceConfig.click(new GenericStack(water, 300_000), bucket, 1));
            var ghost = GenericStack.wrapInItemStack(new GenericStack(water, 1000));
            assertEquals(new GenericStack(water, 2000), FluidHatchInterfaceConfig.click(marked, ghost, 0));
            assertNull(FluidHatchInterfaceConfig.click(marked, ghost, 1));
            assertEquals(new GenericStack(water, 1000), FluidHatchInterfaceConfig.click(new GenericStack(lava, 9000), ghost, 0));
            assertEquals(new GenericStack(water, Long.MAX_VALUE),
                FluidHatchInterfaceConfig.click(new GenericStack(water, Long.MAX_VALUE - 1), ghost, 0));
            assertEquals(new GenericStack(water, Long.MAX_VALUE),
                FluidHatchInterfaceConfig.click(new GenericStack(water, Long.MAX_VALUE), ghost, 0));
            assertEquals(new GenericStack(water, Long.MAX_VALUE - 1000),
                FluidHatchInterfaceConfig.click(new GenericStack(water, Long.MAX_VALUE), ghost, 1));
            assertEquals(marked, FluidHatchInterfaceConfig.click(marked, new ItemStack(Items.STONE), 0));
            assertEquals(1, bucket.getCount());
        }
    }

    private static java.util.OptionalLong amount(String buckets) {
        return FluidHatchStockAmounts.checkedValue(new BigDecimal(buckets), 1000, 0, Long.MAX_VALUE);
    }
}
