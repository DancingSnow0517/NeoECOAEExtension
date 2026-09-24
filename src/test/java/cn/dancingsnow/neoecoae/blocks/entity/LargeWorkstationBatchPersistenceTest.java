package cn.dancingsnow.neoecoae.blocks.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import java.util.UUID;
import net.minecraft.nbt.ListTag;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

@Disabled("Requires a Forge/Minecraft test harness; plain Gradle JUnit cannot bootstrap item registries.")
class LargeWorkstationBatchPersistenceTest {
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void completedPartialOutputRestoresOnlyUndeliveredRemainder() {
        var iron = AEItemKey.of(Items.IRON_INGOT);
        KeyCounter outputs = new KeyCounter();
        outputs.add(iron, 10);
        var batch = new ECOLargeIntegratedWorkingStationBlockEntity.PendingBatch(
                1, 200, 0, 1, new KeyCounter(), outputs, null);
        batch.progress = 200;
        batch.pendingOutput.add(iron, 4);

        var restored = ECOLargeIntegratedWorkingStationBlockEntity.PendingBatch.load(batch.save());

        assertNotNull(restored);
        assertEquals(4, restored.pendingOutput.get(iron));
        assertEquals(0, restored.inputs.get(iron));
    }

    @Test
    void canceledBatchWithAlreadyReturnedInputsCanFinishRemovalAfterReload() {
        var iron = AEItemKey.of(Items.IRON_INGOT);
        KeyCounter outputs = new KeyCounter();
        outputs.add(iron, 1);
        var batch = new ECOLargeIntegratedWorkingStationBlockEntity.PendingBatch(
                1, 200, 0, 1, new KeyCounter(), outputs, null);
        batch.progress = 40;
        batch.canceling = true;

        var restored = ECOLargeIntegratedWorkingStationBlockEntity.PendingBatch.load(batch.save());

        assertNotNull(restored);
        assertEquals(0, restored.inputs.get(iron));
        assertEquals(true, restored.canceling);
    }

    @Test
    void unsupportedJobOwnershipAndExtraInputsStayOutOfOrdinaryQueue() {
        var iron = AEItemKey.of(Items.IRON_INGOT);
        KeyCounter inputs = new KeyCounter();
        inputs.add(iron, 1);
        KeyCounter outputs = new KeyCounter();
        outputs.add(iron, 1);
        var batch = new ECOLargeIntegratedWorkingStationBlockEntity.PendingBatch(
                1, 200, 0, 1, inputs, outputs, null);

        var jobOwned = batch.save();
        jobOwned.putUUID("craftingJobId", UUID.randomUUID());
        assertNull(ECOLargeIntegratedWorkingStationBlockEntity.PendingBatch.load(jobOwned));

        var missingExtra = batch.save();
        ListTag extras = new ListTag();
        extras.add(GenericStack.writeTag(new GenericStack(iron, 1)));
        missingExtra.put("missingExtras", extras);
        assertNull(ECOLargeIntegratedWorkingStationBlockEntity.PendingBatch.load(missingExtra));
    }
}
