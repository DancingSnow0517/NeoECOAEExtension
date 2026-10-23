package cn.dancingsnow.neoecoae.integration.jei;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import java.util.Optional;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.forge.ForgeTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class JeiBookmarkConversionTest {
    @BeforeAll static void bootstrap() { InventoryTestBootstrap.initialize(); }

    @Test void itemAndFluidBookmarksKeepTheirExactResourceComponents() {
        var item = new ItemStack(Items.STONE, 32);
        item.setHoverName(Component.literal("Bookmarked item"));
        var itemIngredient = mock(ITypedIngredient.class);
        when(itemIngredient.getIngredient(VanillaTypes.ITEM_STACK)).thenReturn(Optional.of(item));
        assertEquals(AEItemKey.of(item), JeiBookmarkAccessImpl.ingredientKey(itemIngredient));

        var fluid = new FluidStack(Fluids.WATER, 1000);
        fluid.getOrCreateTag().putString("test_name", "Bookmarked fluid");
        var fluidIngredient = mock(ITypedIngredient.class);
        when(fluidIngredient.getIngredient(ForgeTypes.FLUID_STACK)).thenReturn(Optional.of(fluid));
        assertEquals(AEFluidKey.of(fluid), JeiBookmarkAccessImpl.ingredientKey(fluidIngredient));
        assertNotEquals(AEFluidKey.of(Fluids.WATER), JeiBookmarkAccessImpl.ingredientKey(fluidIngredient));
    }

    @Test void unknownBookmarksAreSkippedWhenTheOptionalAe2JeiConvertersAreAbsent() {
        var type = mock(IIngredientType.class);
        var ingredient = mock(ITypedIngredient.class);
        var value = new Object();
        when(ingredient.getType()).thenReturn(type);
        when(ingredient.getIngredient()).thenReturn(value);
        assertNull(JeiBookmarkAccessImpl.ingredientKey(ingredient));
    }
}
