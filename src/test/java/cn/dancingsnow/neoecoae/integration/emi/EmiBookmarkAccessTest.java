package cn.dancingsnow.neoecoae.integration.emi;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import dev.emi.emi.api.forge.ForgeEmiStack;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.stack.FluidEmiStack;
import dev.emi.emi.api.stack.ItemEmiStack;
import dev.emi.emi.registry.EmiIngredientSerializers;
import dev.emi.emi.runtime.EmiFavorite;
import dev.emi.emi.runtime.EmiFavorites;
import dev.emi.emi.runtime.EmiPersistentData;
import dev.emi.emi.stack.serializer.FluidEmiStackSerializer;
import dev.emi.emi.stack.serializer.ItemEmiStackSerializer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EmiBookmarkAccessTest {
    @BeforeAll static void bootstrap() { InventoryTestBootstrap.initialize(); }

    @Test void explicitFavoritesKeepExactNbtAndOrderAndDeduplicateResources() {
        var item = namedItem();
        var fluid = namedFluid();
        var previous = new ArrayList<>(EmiFavorites.favorites);
        try {
            EmiFavorites.favorites.clear();
            EmiFavorites.favorites.add(new EmiFavorite(EmiStack.of(item), null));
            EmiFavorites.favorites.add(new EmiFavorite(ForgeEmiStack.of(fluid), null));
            EmiFavorites.favorites.add(new EmiFavorite(EmiStack.of(item.copyWithCount(1)), null));
            var ambiguous = mock(EmiIngredient.class);
            when(ambiguous.getEmiStacks()).thenReturn(List.of(EmiStack.of(Items.STONE), EmiStack.of(Items.DIRT)));
            EmiFavorites.favorites.add(new EmiFavorite(ambiguous, null));
            EmiFavorites.favorites.add(new EmiFavorite(EmiStack.EMPTY, null));

            assertEquals(List.of(AEItemKey.of(item), AEFluidKey.of(fluid)), EmiBookmarkAccess.bookmarkedKeys());
            assertNotEquals(AEItemKey.of(Items.STONE), EmiBookmarkAccess.bookmarkedKeys().get(0));
            assertNotEquals(AEFluidKey.of(Fluids.WATER), EmiBookmarkAccess.bookmarkedKeys().get(1));
        } finally {
            EmiFavorites.favorites.clear();
            EmiFavorites.favorites.addAll(previous);
        }
    }

    @Test void savingMissingItemsAndFluidsUsesEmisPersistenceAndNeverTogglesExistingFavoritesOff() {
        var previous = new ArrayList<>(EmiFavorites.favorites);
        var byClass = new HashMap<>(EmiIngredientSerializers.BY_CLASS);
        var byType = new HashMap<>(EmiIngredientSerializers.BY_TYPE);
        try (var persistence = mockStatic(EmiPersistentData.class)) {
            EmiFavorites.favorites.clear();
            var itemSerializer = new ItemEmiStackSerializer();
            var fluidSerializer = new FluidEmiStackSerializer();
            EmiIngredientSerializers.BY_CLASS.put(ItemEmiStack.class, itemSerializer);
            EmiIngredientSerializers.BY_CLASS.put(FluidEmiStack.class, fluidSerializer);
            EmiIngredientSerializers.BY_TYPE.put("item", itemSerializer);
            EmiIngredientSerializers.BY_TYPE.put("fluid", fluidSerializer);
            List<AEKey> keys = List.of(AEItemKey.of(namedItem()), AEFluidKey.of(namedFluid()));

            EmiBookmarkAccess.addMissingToBookmarks(keys);
            EmiBookmarkAccess.addMissingToBookmarks(keys);

            assertEquals(keys, EmiBookmarkAccess.bookmarkedKeys());
            assertEquals(2, EmiFavorites.favorites.size());
            persistence.verify(EmiPersistentData::save, times(2));
        } finally {
            EmiFavorites.favorites.clear();
            EmiFavorites.favorites.addAll(previous);
            EmiIngredientSerializers.BY_CLASS.clear();
            EmiIngredientSerializers.BY_CLASS.putAll(byClass);
            EmiIngredientSerializers.BY_TYPE.clear();
            EmiIngredientSerializers.BY_TYPE.putAll(byType);
        }
    }

    private static ItemStack namedItem() {
        var stack = new ItemStack(Items.STONE, 32);
        stack.setHoverName(Component.literal("Bookmarked item"));
        return stack;
    }

    private static FluidStack namedFluid() {
        var stack = new FluidStack(Fluids.WATER, 1000);
        stack.getOrCreateTag().putString("test_name", "Bookmarked fluid");
        return stack;
    }
}
