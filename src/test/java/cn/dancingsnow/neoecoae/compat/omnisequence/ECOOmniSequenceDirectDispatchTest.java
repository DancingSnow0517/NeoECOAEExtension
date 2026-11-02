package cn.dancingsnow.neoecoae.compat.omnisequence;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import cn.dancingsnow.neoecoae.crafting.execution.batch.ECOBatchAdmission;
import cn.dancingsnow.neoecoae.crafting.execution.batch.ECOBatchDispatchRequest;
import cn.dancingsnow.neoecoae.crafting.execution.batch.ECOBatchExecutionView;
import cn.dancingsnow.neoecoae.crafting.execution.batch.ECOPatternIdentity;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import com.atir.molecularmanipulator.api.crafting.OmniBatchAdmission;
import com.atir.molecularmanipulator.api.crafting.OmniBatchCraftingProvider;
import com.atir.molecularmanipulator.api.crafting.OmniBatchDelivery;
import com.atir.molecularmanipulator.crafting.MolecularBatchDispatchContext;
import com.atir.molecularmanipulator.integration.ae2.MolecularBatchCraftingProvider;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.neoforged.fml.ModList;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

class ECOOmniSequenceDirectDispatchTest {

    @BeforeAll
    static void bootstrap() {
        InventoryTestBootstrap.initialize();
    }

    interface TestMolecularProvider extends ICraftingProvider, MolecularBatchCraftingProvider {}
    interface TestOmniProvider extends ICraftingProvider, OmniBatchCraftingProvider {}

    @Test
    void isLoadedReturnsFalseWhenModListIsAbsentOrModNotPresent() {
        try (var mocked = mockStatic(ModList.class)) {
            mocked.when(ModList::get).thenReturn(null);
            assertFalse(ECOOmniSequenceDirectDispatch.isLoaded());

            var modList = mock(ModList.class);
            mocked.when(ModList::get).thenReturn(modList);
            when(modList.isLoaded("molecularmanipulator")).thenReturn(false);
            assertFalse(ECOOmniSequenceDirectDispatch.isLoaded());

            when(modList.isLoaded("molecularmanipulator")).thenReturn(true);
            assertTrue(ECOOmniSequenceDirectDispatch.isLoaded());
        }
    }

    @Test
    void molecularBatchCraftingProviderBatchesAndCleansContext() {
        try (var mocked = mockStatic(ModList.class)) {
            var modList = mock(ModList.class);
            mocked.when(ModList::get).thenReturn(modList);
            when(modList.isLoaded("molecularmanipulator")).thenReturn(true);

            var provider = mock(TestMolecularProvider.class);
            var pattern = mock(IPatternDetails.class);
            when(pattern.getDefinition()).thenReturn(AEItemKey.of(Items.DIAMOND));
            when(pattern.getInputs()).thenReturn(new IPatternDetails.IInput[0]);
            when(provider.molecularmanipulator$supportsBatching(pattern)).thenReturn(true);
            when(provider.molecularmanipulator$getBatchLimit(pattern)).thenReturn(256L);

            var oneCraftInput = new KeyCounter();
            var key = mock(AEKey.class);
            oneCraftInput.add(key, 2);
            var oneCraftInputs = new KeyCounter[]{oneCraftInput};

            var session = ECOOmniSequenceDirectDispatch.open(provider, pattern, oneCraftInputs, 256L);
            assertNotNull(session);
            assertEquals(256L, session.maxBatchSize());

            var batchInputs = new KeyCounter[]{new KeyCounter()};
            batchInputs[0].add(key, 512);
            var batchOutputs = new KeyCounter();
            batchOutputs.add(key, 256);
            var batchRemainders = new KeyCounter();
            var jobId = UUID.randomUUID();
            var level = mock(Level.class);
            var identity = ECOPatternIdentity.of(pattern, provider);
            var executionView = mock(ECOBatchExecutionView.class);
            var batch = new ECOBatchDispatchRequest(identity, executionView, batchInputs, batchOutputs, batchRemainders, 256L, level, jobId);

            AtomicBoolean contextVerified = new AtomicBoolean(false);
            when(provider.pushPattern(eq(pattern), eq(batchInputs))).thenAnswer(inv -> {
                var current = MolecularBatchDispatchContext.current(pattern, batchInputs);
                assertNotNull(current, "Context must be active inside pushPattern");
                assertEquals(jobId, current.craftingId());
                assertEquals(256L, current.craftCount());
                contextVerified.set(true);
                return true;
            });

            var admission = session.submit(batch, oneCraftInputs);
            assertTrue(contextVerified.get(), "pushPattern must have been called with active context");
            assertEquals(ECOBatchAdmission.Status.ACCEPTED, admission.status());
            assertEquals(256L, admission.acceptedCrafts());

            // After submit, the ThreadLocal context must be closed
            assertNull(MolecularBatchDispatchContext.current(pattern, batchInputs), "Context must be cleared after submit");
        }
    }

    @Test
    void omniBatchCraftingProviderAdmitsAndCommitsBatch() {
        try (var mocked = mockStatic(ModList.class)) {
            var modList = mock(ModList.class);
            mocked.when(ModList::get).thenReturn(modList);
            when(modList.isLoaded("molecularmanipulator")).thenReturn(true);

            var provider = mock(TestOmniProvider.class);
            var pattern = mock(IPatternDetails.class);
            when(pattern.getDefinition()).thenReturn(AEItemKey.of(Items.DIAMOND));
            when(pattern.getInputs()).thenReturn(new IPatternDetails.IInput[0]);

            var key = mock(AEKey.class);
            var oneCraftInput = new KeyCounter();
            oneCraftInput.add(key, 1);
            var oneCraftInputs = new KeyCounter[]{oneCraftInput};

            var admissionMock = mock(OmniBatchAdmission.class);
            when(admissionMock.maxCrafts()).thenReturn(100L);
            doAnswer(inv -> {
                OmniBatchDelivery delivery = inv.getArgument(0);
                delivery.accept(new OmniBatchDelivery.Receipt(
                        OmniBatchDelivery.Ownership.PERSISTED_PROVIDER_QUEUE,
                        OmniBatchDelivery.Backpressure.MAY_ACCEPT_MORE));
                return null;
            }).when(admissionMock).commit(any());

            when(provider.prepareOmniBatch(any())).thenReturn(admissionMock);

            var session = ECOOmniSequenceDirectDispatch.open(provider, pattern, oneCraftInputs, 100L);
            assertNotNull(session);
            assertEquals(100L, session.maxBatchSize());

            var batchInputs = new KeyCounter[]{new KeyCounter()};
            batchInputs[0].add(key, 100);
            var batchOutputs = new KeyCounter();
            batchOutputs.add(key, 100);
            var batchRemainders = new KeyCounter();
            var jobId = UUID.randomUUID();
            var level = mock(Level.class);
            var identity = ECOPatternIdentity.of(pattern, provider);
            var executionView = mock(ECOBatchExecutionView.class);
            var batch = new ECOBatchDispatchRequest(identity, executionView, batchInputs, batchOutputs, batchRemainders, 100L, level, jobId);

            var result = session.submit(batch, oneCraftInputs);
            assertEquals(ECOBatchAdmission.Status.ACCEPTED, result.status());
            assertEquals(100L, result.acceptedCrafts());
            verify(admissionMock).commit(any());
            session.close();
            verify(admissionMock).close();
        }
    }

    @Test
    void flushAfterCpuAccountingInvokesProviderFlush() {
        try (var mocked = mockStatic(ModList.class)) {
            var modList = mock(ModList.class);
            mocked.when(ModList::get).thenReturn(modList);
            when(modList.isLoaded("molecularmanipulator")).thenReturn(true);

            var provider = mock(TestMolecularProvider.class);
            boolean flushed = ECOOmniSequenceDirectDispatch.flushAfterCpuAccounting(provider);
            assertTrue(flushed);
            verify(provider).flushOutputsAfterCpuAccounting();
        }
    }

    @Test
    void absentModDoesNotResolveOmniClasses() throws Exception {
        try (var mocked = mockStatic(ModList.class)) {
            var mods = mock(ModList.class);
            mocked.when(ModList::get).thenReturn(mods);
            when(mods.isLoaded("molecularmanipulator")).thenReturn(false);
            var source = ECOOmniSequenceDirectDispatch.class.getProtectionDomain().getCodeSource().getLocation();
            try (var loader = new java.net.URLClassLoader(new java.net.URL[]{source}, getClass().getClassLoader()) {
                @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                    if (name.startsWith("com.atir.molecularmanipulator.")) throw new ClassNotFoundException(name);
                    if (name.startsWith(ECOOmniSequenceDirectDispatch.class.getName())) {
                        synchronized (getClassLoadingLock(name)) {
                            var found = findLoadedClass(name);
                            if (found == null) found = findClass(name);
                            if (resolve) resolveClass(found);
                            return found;
                        }
                    }
                    return super.loadClass(name, resolve);
                }
            }) {
                var bridge = loader.loadClass(ECOOmniSequenceDirectDispatch.class.getName());
                var provider = mock(ICraftingProvider.class);
                assertEquals(false, bridge.getMethod("isEligible", ICraftingProvider.class).invoke(null, provider));
                assertEquals(false, bridge.getMethod("flushAfterCpuAccounting", ICraftingProvider.class).invoke(null, provider));
                assertNull(bridge.getMethod("open", ICraftingProvider.class, IPatternDetails.class,
                                KeyCounter[].class, long.class)
                        .invoke(null, provider, mock(IPatternDetails.class), new KeyCounter[0], 2L));
            }
        }
    }
}
