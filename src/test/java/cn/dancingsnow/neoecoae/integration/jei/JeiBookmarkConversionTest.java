package cn.dancingsnow.neoecoae.integration.jei;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import java.util.Optional;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.neoforge.NeoForgeTypes;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import tamaized.ae2jeiintegration.api.integrations.jei.IngredientConverter;
import tamaized.ae2jeiintegration.api.integrations.jei.IngredientConverters;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class JeiBookmarkConversionTest {
    @BeforeAll static void bootstrap() { InventoryTestBootstrap.initialize(); }

    @Test void itemAndFluidBookmarksKeepTheirExactResourceComponents() {
        var item = new ItemStack(Items.STONE, 32);
        item.set(DataComponents.CUSTOM_NAME, Component.literal("Bookmarked item"));
        var itemIngredient = mock(ITypedIngredient.class);
        when(itemIngredient.getIngredient(VanillaTypes.ITEM_STACK)).thenReturn(Optional.of(item));
        assertEquals(AEItemKey.of(item), JeiBookmarkAccessImpl.ingredientKey(itemIngredient));

        var fluid = new FluidStack(Fluids.WATER, 1000);
        fluid.set(DataComponents.CUSTOM_NAME, Component.literal("Bookmarked fluid"));
        var fluidIngredient = mock(ITypedIngredient.class);
        when(fluidIngredient.getIngredient(NeoForgeTypes.FLUID_STACK)).thenReturn(Optional.of(fluid));
        assertEquals(AEFluidKey.of(fluid), JeiBookmarkAccessImpl.ingredientKey(fluidIngredient));
        assertNotEquals(AEFluidKey.of(Fluids.WATER), JeiBookmarkAccessImpl.ingredientKey(fluidIngredient));
    }

    @Test void customBookmarksUseTheRegisteredAe2JeiConverterAndUnknownTypesAreSkipped() {
        var type = mock(IIngredientType.class);
        var ingredient = mock(ITypedIngredient.class);
        var value = new Object();
        var key = mock(AEKey.class);
        when(ingredient.getType()).thenReturn(type);
        when(ingredient.getIngredient()).thenReturn(value);
        assertNull(JeiBookmarkAccessImpl.ingredientKey(ingredient));

        var converter = mock(IngredientConverter.class);
        when(converter.getIngredientType()).thenReturn(type);
        when(converter.getStackFromIngredient(value)).thenReturn(new GenericStack(key, 1000));
        assertTrue(IngredientConverters.register(converter));
        assertSame(key, JeiBookmarkAccessImpl.ingredientKey(ingredient));
    }
}
