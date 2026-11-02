package cn.dancingsnow.neoecoae.blocks.entity;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LargeWorkstationExtraInputsTest {
    // 26.1 port: AE2 Lightning Tech's LightningKey (1.21.1 jar only) cannot initialize on 26.1; the extra-input
    // handling under test is key-agnostic, so two distinct item keys stand in for the two lightning tiers.
    private static final appeng.api.stacks.AEKey EXTRA_A = appeng.api.stacks.AEItemKey.of(net.minecraft.world.item.Items.BLAZE_ROD);
    private static final appeng.api.stacks.AEKey EXTRA_B = appeng.api.stacks.AEItemKey.of(net.minecraft.world.item.Items.GLOWSTONE_DUST);

    @BeforeAll static void bootstrap() { InventoryTestBootstrap.initialize(); }

    @Test void partialExtractionIsOwnedAndRetryOnlyRequestsRemainder() {
        var storage = mock(MEStorage.class);
        var source = mock(IActionSource.class);
        var missing = new KeyCounter();
        var owned = new KeyCounter();
        var changes = new AtomicInteger();
        missing.add(EXTRA_A, 28);
        when(storage.extract(EXTRA_A, 28, Actionable.MODULATE, source)).thenReturn(10L);
        when(storage.extract(EXTRA_A, 18, Actionable.MODULATE, source)).thenReturn(18L);
        assertFalse(LargeWorkstationExtraInputs.acquire(missing, owned, storage, source, changes::incrementAndGet));
        assertEquals(10, owned.get(EXTRA_A));
        assertEquals(18, missing.get(EXTRA_A));
        assertTrue(LargeWorkstationExtraInputs.acquire(missing, owned, storage, source, changes::incrementAndGet));
        assertEquals(28, owned.get(EXTRA_A));
        assertEquals(2, changes.get());
        assertTrue(LargeWorkstationExtraInputs.acquire(missing, owned, storage, source, changes::incrementAndGet));
        verify(storage).extract(EXTRA_A, 28, Actionable.MODULATE, source);
        verify(storage).extract(EXTRA_A, 18, Actionable.MODULATE, source);
        verifyNoMoreInteractions(storage);
    }

    @Test void missingLightningNeverCreatesOwnedResources() {
        var missing = new KeyCounter();
        var owned = new KeyCounter();
        missing.add(EXTRA_B, 4096);
        assertFalse(LargeWorkstationExtraInputs.acquire(missing, owned, mock(MEStorage.class), mock(IActionSource.class),
            () -> fail("No mutation should be saved")));
        assertEquals(0, owned.get(EXTRA_B));
        assertEquals(4096, missing.get(EXTRA_B));
    }

    @Test void returningOwnedInputsNeverInsertsUnacquiredExtras() {
        var storage = mock(MEStorage.class);
        var source = mock(IActionSource.class);
        var owned = new KeyCounter();
        var stillMissing = new KeyCounter();
        owned.add(EXTRA_A, 10);
        stillMissing.add(EXTRA_A, 18);
        when(storage.insert(EXTRA_A, 10, Actionable.MODULATE, source)).thenReturn(10L);

        assertTrue(LargeWorkstationExtraInputs.returnOwned(owned, storage, source, () -> {}));

        assertEquals(0, owned.get(EXTRA_A));
        assertEquals(18, stillMissing.get(EXTRA_A));
        verify(storage).insert(EXTRA_A, 10, Actionable.MODULATE, source);
        verifyNoMoreInteractions(storage);
    }

    @Test void partialReturnKeepsOnlyTheUninsertedOwnedAmount() {
        var storage = mock(MEStorage.class);
        var source = mock(IActionSource.class);
        var owned = new KeyCounter();
        var changes = new AtomicInteger();
        owned.add(EXTRA_B, 10);
        when(storage.insert(EXTRA_B, 10, Actionable.MODULATE, source)).thenReturn(4L);
        when(storage.insert(EXTRA_B, 6, Actionable.MODULATE, source)).thenReturn(6L);

        assertFalse(LargeWorkstationExtraInputs.returnOwned(owned, storage, source, changes::incrementAndGet));
        assertEquals(6, owned.get(EXTRA_B));
        assertTrue(LargeWorkstationExtraInputs.returnOwned(owned, storage, source, changes::incrementAndGet));
        assertEquals(0, owned.get(EXTRA_B));
        assertEquals(2, changes.get());
        verify(storage).insert(EXTRA_B, 10, Actionable.MODULATE, source);
        verify(storage).insert(EXTRA_B, 6, Actionable.MODULATE, source);
        verifyNoMoreInteractions(storage);
    }
}
