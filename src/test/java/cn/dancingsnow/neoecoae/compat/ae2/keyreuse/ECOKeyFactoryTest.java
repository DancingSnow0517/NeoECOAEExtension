package cn.dancingsnow.neoecoae.compat.ae2.keyreuse;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.GenericStack;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import java.util.concurrent.Executors;
import java.util.ArrayList;
import static org.junit.jupiter.api.Assertions.*;

class ECOKeyFactoryTest {
    @BeforeAll static void bootstrap() {
        InventoryTestBootstrap.initialize();
        ((net.minecraft.core.MappedRegistry<?>) net.minecraft.core.registries.BuiltInRegistries.ITEM).unfreeze(false);
    }
    @AfterEach void stop() { KeyReuse.configure(false); }

    @Test void factoryReusePreservesPublicItemCountsAndEcoNormalization() {
        KeyReuse.configure(true);
        ItemStack input = new ItemStack(Items.IRON_INGOT, 64);
        AEItemKey external = AEItemKey.of(input);
        assertNotSame(external, AEItemKey.of(input));
        AEItemKey owned = ECOKeyFactory.item(input);
        assertNotSame(external, owned);
        assertSame(owned, ECOKeyFactory.item(input));
        assertSame(owned, ECOKeyFactory.canonicalize(external));
        assertEquals(external, owned);
        assertEquals(external.hashCode(), owned.hashCode());
        assertEquals(64, input.getCount());
        assertEquals(1, owned.getReadOnlyStack().getCount());
        FluidStack water = new FluidStack(Fluids.WATER, 1000);
        AEFluidKey externalFluid = AEFluidKey.of(water);
        if ((Object) externalFluid instanceof FluidKeyComponents) assertSame(externalFluid, AEFluidKey.of(water));
        else assertNotSame(externalFluid, AEFluidKey.of(water));
        assertSame(ECOKeyFactory.fluid(water), ECOKeyFactory.canonicalize(externalFluid));
        KeyReuse.configure(false);
        assertNotSame(ECOKeyFactory.item(input), ECOKeyFactory.item(input));
        assertEquals(owned, ECOKeyFactory.item(input));
    }

    @Test void complexKeysKeepValueSemanticsWithoutEnteringThePlainCache() {
        KeyReuse.configure(true);
        ItemStack plain = new ItemStack(Items.IRON_INGOT);
        AEItemKey shared = ECOKeyFactory.item(plain);
        ItemStack complex = plain.copy();
        complex.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal("variant"));
        AEItemKey first = ECOKeyFactory.item(complex);
        assertNotSame(first, ECOKeyFactory.item(complex));
        assertNotEquals(shared, first);
        assertSame(first, ECOKeyFactory.canonicalize(first));
        assertSame(shared, ECOKeyFactory.item(plain));
        GenericStack generic = ECOKeyFactory.fromItemStack(complex);
        assertEquals(first, generic.what());
        assertEquals(complex.getCount(), generic.amount());
    }

    @Test void concurrentEcoCreationAndReloadKeepOneOwnedKeyPerGeneration() throws Exception {
        KeyReuse.configure(true);
        ItemStack source = new ItemStack(Items.STONE);
        var executor = Executors.newFixedThreadPool(8);
        try {
            var tasks = new ArrayList<java.util.concurrent.Callable<AEItemKey>>();
            for (int i = 0; i < 64; i++) tasks.add(() -> ECOKeyFactory.item(source));
            var results = executor.invokeAll(tasks);
            AEItemKey shared = results.get(0).get();
            for (var result : results) assertSame(shared, result.get());
            KeyReuse.invalidate();
            AEItemKey replacement = ECOKeyFactory.item(source);
            assertNotSame(shared, replacement);
            assertEquals(shared, replacement);
            assertSame(replacement, ECOKeyFactory.canonicalize(shared));
        } finally { executor.shutdownNow(); }
    }

    @Test void identityTablesSeparateOwnersAndChannelsEvenWhenOwnerEqualsClaimsTheyMatch() {
        KeyReuse.configure(true);
        Object epoch = KeyReuse.epoch();
        Object first = new EqualOwner();
        Object second = new EqualOwner();
        assertNotSame(KeyReuse.slot(epoch, "a", first), KeyReuse.slot(epoch, "a", second));
        assertNotSame(KeyReuse.slot(epoch, "a", first), KeyReuse.slot(epoch, "b", first));
        assertSame(KeyReuse.slot(epoch, "a", first), KeyReuse.slot(epoch, "a", first));
    }
    @Test void ownerTableFallsBackAtItsBoundWithoutEvictingExistingKeys() {
        OwnerSlots slots = new OwnerSlots(1);
        Object first = new Object();
        KeyReuseSlot owned = slots.get(first);
        assertNotNull(owned);
        assertNull(slots.get(new Object()));
        assertSame(owned, slots.get(first));
    }

    @Test void externalAndOwnedKeysShareOneExactLedgerAcrossDisableAndReload() {
        KeyReuse.configure(true);
        AEItemKey external = AEItemKey.of(new ItemStack(Items.IRON_INGOT));
        AEItemKey owned = ECOKeyFactory.item(new ItemStack(Items.IRON_INGOT));
        var engine = new cn.dancingsnow.neoecoae.storage.infinite.SavedDataInfiniteStorageEngine(
            cn.dancingsnow.neoecoae.storage.infinite.ECOInfiniteStorageData.createNew());
        var huge = java.math.BigInteger.TEN.pow(40);
        var mode = appeng.api.config.Actionable.MODULATE;
        assertEquals(huge, engine.insert(external, huge, mode));
        assertEquals(huge, engine.getAmount(owned).toBigInteger());
        KeyReuse.invalidate();
        assertEquals(31, engine.extract(ECOKeyFactory.item(new ItemStack(Items.IRON_INGOT)), 31, mode));
        KeyReuse.configure(false);
        assertEquals(huge.subtract(java.math.BigInteger.valueOf(31)), engine.getAmount(external).toBigInteger());
        assertNotSame(external, AEItemKey.of(new ItemStack(Items.IRON_INGOT)));
    }

    @Test void disabledFluidFactoryDelegatesTheOriginalStackOverload() {
        KeyReuse.configure(false);
        FluidStack source = new FluidStack(Fluids.WATER, 1000);
        AEFluidKey expected = AEFluidKey.of(source);
        try (var factory = org.mockito.Mockito.mockStatic(AEFluidKey.class)) {
            factory.when(() -> AEFluidKey.of(source)).thenReturn(expected);
            assertSame(expected, ECOKeyFactory.fluid(source));
            factory.verify(() -> AEFluidKey.of(source));
            factory.verify(() -> AEFluidKey.of(Fluids.WATER), org.mockito.Mockito.never());
        }
    }

    @Test void changedDefaultComponentsCannotReuseOrPoisonThePreviousPrototype() {
        KeyReuse.configure(true);
        MutableDefaultsItem item = register("prototypes", new MutableDefaultsItem());
        ItemStack oldStack = new ItemStack(item);
        AEItemKey oldKey = ECOKeyFactory.item(oldStack);
        Object epoch = KeyReuse.epoch();
        item.defaults = net.minecraft.core.component.DataComponentMap.builder().addAll(item.components())
            .set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal("new defaults"))
            .build();
        ItemStack newStack = new ItemStack(item);
        assertTrue(oldStack.isComponentsPatchEmpty());
        assertTrue(newStack.isComponentsPatchEmpty());
        AEItemKey current = ECOKeyFactory.item(newStack);
        assertSame(epoch, KeyReuse.epoch());
        assertNotEquals(oldKey, current);
        assertEquals(oldKey, ECOKeyFactory.item(oldStack));
        assertEquals(oldKey, ECOKeyFactory.canonicalize(oldKey));
        assertEquals(oldKey, AEItemKey.of(oldStack));
        assertSame(current, ECOKeyFactory.item(newStack));
        assertSame(current, ECOKeyFactory.item(item));
    }

    @Test void subclassDefaultFactoriesStillRunAndUnregisteredOwnersAreNotRetained() {
        KeyReuse.configure(true);
        var item = register("custom_default", new MutableDefaultsItem() {
            int calls;
            @Override public ItemStack getDefaultInstance() {
                var stack = new ItemStack(this);
                stack.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
                    net.minecraft.network.chat.Component.literal("call " + ++calls));
                return stack;
            }
        });
        assertNotEquals(AEItemKey.of(item), AEItemKey.of(item));
        assertNotEquals(ECOKeyFactory.item(item), ECOKeyFactory.item(item));
        ((net.minecraft.core.MappedRegistry<?>) net.minecraft.core.registries.BuiltInRegistries.ITEM).unfreeze(false);
        var unregistered = new net.minecraft.world.item.Item(new net.minecraft.world.item.Item.Properties());
        assertFalse(unregistered.builtInRegistryHolder().isBound());
        assertNull(KeyReuse.itemSlot(KeyReuse.epoch(), unregistered));
        assertNotSame(ECOKeyFactory.item(unregistered), ECOKeyFactory.item(unregistered));
        register("late_registered", unregistered);
    }

    @Test void fluidComponentsRemainDistinctAndMutableCopiesCannotChangeSharedKeys() {
        KeyReuse.configure(true);
        FluidStack plain = new FluidStack(Fluids.WATER, 1000);
        AEFluidKey shared = ECOKeyFactory.fluid(plain);
        FluidStack firstStack = plain.copy();
        firstStack.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal("one"));
        FluidStack secondStack = plain.copy();
        secondStack.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal("two"));
        AEFluidKey first = ECOKeyFactory.fluid(firstStack);
        assertNotSame(first, ECOKeyFactory.fluid(firstStack));
        assertEquals(first, ECOKeyFactory.fluid(firstStack.copy()));
        assertNotEquals(first, shared);
        assertNotEquals(first, ECOKeyFactory.fluid(secondStack));
        KeyReuse.invalidate(); // The tagged shortcut must also be correct before a default key has been cached.
        assertSame(first, ECOKeyFactory.canonicalize(first));
        AEFluidKey current = ECOKeyFactory.fluid(plain);
        FluidStack copy = current.toStack(1000);
        copy.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal("copy"));
        assertEquals(shared, current);
        assertSame(current, ECOKeyFactory.fluid(plain));
        assertNotEquals(current, AEFluidKey.of(copy));
    }

    @Test void publicSubclassFactoriesRetainDynamicDerivedMetadata() {
        KeyReuse.configure(true);
        int[] maximum = {64};
        var item = register("dynamic_maximum", new MutableDefaultsItem() {
            @Override public int getMaxStackSize(ItemStack stack) { return maximum[0]; }
        });
        var stack = new ItemStack(item);
        AEItemKey before = AEItemKey.of(stack);
        maximum[0] = 16;
        AEItemKey after = AEItemKey.of(stack);
        assertEquals(before, after, "AE2 equality intentionally ignores the cached derived maximum");
        assertEquals(64, before.getMaxStackSize());
        assertEquals(16, after.getMaxStackSize());
        assertNotSame(before, after);
    }

    @Test void wovenFactoriesShareOnlyDefaultKeysAndReleaseThemOnReload() {
        org.junit.jupiter.api.Assumptions.assumeTrue((Object) Items.IRON_INGOT instanceof DefaultKeyOwner);
        KeyReuse.configure(true);
        AEItemKey item = AEItemKey.of(Items.IRON_INGOT);
        assertSame(item, AEItemKey.of(new ItemStack(Items.IRON_INGOT)));
        assertSame(item, ECOKeyFactory.item(new ItemStack(Items.IRON_INGOT, 64)));
        var many = AEItemKey.of(new ItemStack(Items.IRON_INGOT, 64));
        assertEquals(64, many.getReadOnlyStack().getCount());
        assertNotSame(item, many);
        AEFluidKey fluid = AEFluidKey.of(Fluids.WATER);
        assertSame(fluid, AEFluidKey.of(new FluidStack(Fluids.WATER, 1000)));
        KeyReuse.invalidate();
        assertNotSame(item, AEItemKey.of(Items.IRON_INGOT));
        assertNotSame(fluid, AEFluidKey.of(Fluids.WATER));
        KeyReuse.configure(false);
        assertNotSame(AEItemKey.of(Items.IRON_INGOT), AEItemKey.of(Items.IRON_INGOT));
        assertNotSame(AEFluidKey.of(Fluids.WATER), AEFluidKey.of(Fluids.WATER));
    }

    private static <T extends net.minecraft.world.item.Item> T register(String name, T item) {
        ((net.minecraft.core.MappedRegistry<?>) net.minecraft.core.registries.BuiltInRegistries.ITEM).unfreeze(false);
        return net.minecraft.core.Registry.register(net.minecraft.core.registries.BuiltInRegistries.ITEM,
            "eco_key_factory_test:" + name, item);
    }

    private static class MutableDefaultsItem extends net.minecraft.world.item.Item {
        net.minecraft.core.component.DataComponentMap defaults;
        MutableDefaultsItem() { super(new Properties()); }
        @Override public net.minecraft.core.component.DataComponentMap components() {
            return defaults == null ? super.components() : defaults;
        }
    }

    private static final class EqualOwner {
        @Override public boolean equals(Object other) { return other instanceof EqualOwner; }
        @Override public int hashCode() { return 1; }
    }
}
