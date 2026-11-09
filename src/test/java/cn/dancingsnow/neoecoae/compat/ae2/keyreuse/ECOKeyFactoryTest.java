package cn.dancingsnow.neoecoae.compat.ae2.keyreuse;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.GenericStack;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import java.util.concurrent.Executors;
import java.util.ArrayList;
import static org.junit.jupiter.api.Assertions.*;

class ECOKeyFactoryTest {
    @BeforeAll static void bootstrap() { InventoryTestBootstrap.initialize(); }
    @AfterEach void stop() { KeyReuse.configure(false); }

    @Test void defaultKeysShareTheSameSlotWithTheOptionalFactoryBridge() {
        KeyReuse.configure(true);
        ItemStack input = new ItemStack(Items.IRON_INGOT, 64);
        AEItemKey external = AEItemKey.of(input);
        boolean bridged = (Object) input.getItem() instanceof DefaultKeyOwner;
        if (bridged) assertSame(external, AEItemKey.of(input));
        else assertNotSame(external, AEItemKey.of(input));
        AEItemKey owned = ECOKeyFactory.item(input);
        if (bridged) assertSame(external, owned);
        else assertNotSame(external, owned);
        assertSame(owned, ECOKeyFactory.item(input));
        assertSame(owned, ECOKeyFactory.canonicalize(external));
        assertEquals(external, owned);
        assertEquals(external.hashCode(), owned.hashCode());
        assertEquals(64, input.getCount());
        assertEquals(1, owned.getReadOnlyStack().getCount());
        FluidStack water = new FluidStack(Fluids.WATER, 1000);
        AEFluidKey externalFluid = AEFluidKey.of(water);
        if (bridged) assertSame(externalFluid, AEFluidKey.of(water));
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
        complex.getOrCreateTag().putString("eco_scope", "variant");
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

    @Test void emptyNbtAndSerializedCapabilitiesCannotAliasPlainItems() {
        KeyReuse.configure(true);
        ItemStack source = new ItemStack(Items.IRON_INGOT);
        AEItemKey plain = ECOKeyFactory.item(source);
        ItemStack emptyTag = source.copy();
        emptyTag.setTag(new net.minecraft.nbt.CompoundTag());
        assertNotEquals(plain, ECOKeyFactory.item(emptyTag));
        assertNotSame(ECOKeyFactory.item(emptyTag), ECOKeyFactory.item(emptyTag));
        for (boolean emptyCaps : new boolean[] { true, false }) {
            var encoded = AEItemKey.of(source).toTag();
            var caps = new net.minecraft.nbt.CompoundTag();
            if (!emptyCaps) caps.putString("eco_test_cap", "preserve");
            encoded.put("caps", caps);
            AEItemKey withCaps = AEItemKey.fromTag(encoded);
            assertNotEquals(plain, withCaps);
            assertSame(withCaps, ECOKeyFactory.canonicalize(withCaps));
            KeyReuse.invalidate();
            assertSame(withCaps, ECOKeyFactory.canonicalize(withCaps));
            assertEquals(withCaps, ECOKeyFactory.itemFromTag(encoded));
        }
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

    @Test void wovenFactoriesPreserveNullVersusEmptyTagsAndGenerationBoundaries() {
        org.junit.jupiter.api.Assumptions.assumeTrue((Object) Items.IRON_INGOT instanceof DefaultKeyOwner);
        KeyReuse.configure(true);
        AEItemKey item = AEItemKey.of(Items.IRON_INGOT);
        AEFluidKey fluid = AEFluidKey.of(Fluids.WATER);
        assertSame(item, AEItemKey.of(new ItemStack(Items.IRON_INGOT, 64)));
        assertSame(fluid, AEFluidKey.of(new FluidStack(Fluids.WATER, 1000)));
        assertNotEquals(item, AEItemKey.of(Items.IRON_INGOT, new net.minecraft.nbt.CompoundTag()));
        assertNotEquals(fluid, AEFluidKey.of(Fluids.WATER, new net.minecraft.nbt.CompoundTag()));
        var tag = new net.minecraft.nbt.CompoundTag();
        tag.putInt("variant", 1);
        AEFluidKey first = AEFluidKey.of(Fluids.WATER, tag);
        assertEquals(first, AEFluidKey.of(Fluids.WATER, tag.copy()));
        tag.putInt("variant", 2);
        assertNotEquals(first, AEFluidKey.of(Fluids.WATER, tag));
        KeyReuse.invalidate();
        assertNotSame(item, AEItemKey.of(Items.IRON_INGOT));
        assertEquals(item, AEItemKey.of(Items.IRON_INGOT));
        assertNotSame(fluid, AEFluidKey.of(Fluids.WATER));
        assertEquals(fluid, AEFluidKey.of(Fluids.WATER));
        KeyReuse.configure(false);
        assertNotSame(AEItemKey.of(Items.IRON_INGOT), AEItemKey.of(Items.IRON_INGOT));
        assertNotSame(AEFluidKey.of(Fluids.WATER), AEFluidKey.of(Fluids.WATER));
    }

    private static final class EqualOwner {
        @Override public boolean equals(Object other) { return other instanceof EqualOwner; }
        @Override public int hashCode() { return 1; }
    }
}
