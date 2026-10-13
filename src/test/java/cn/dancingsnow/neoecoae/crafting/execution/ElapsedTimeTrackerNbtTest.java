package cn.dancingsnow.neoecoae.crafting.execution;

import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.AEKeyTypes;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import java.math.BigInteger;
import java.util.LinkedHashSet;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ElapsedTimeTrackerNbtTest {
    private MockedStatic<AEKeyTypes> keyTypes;

    @BeforeAll
    static void bootstrap() {
        InventoryTestBootstrap.initialize();
    }

    @BeforeEach
    void registerKeyTypes() {
        keyTypes = mockStatic(AEKeyTypes.class);
        keyTypes.when(AEKeyTypes::getAll).thenReturn(new LinkedHashSet<>(
            List.of(AEKeyType.items(), AEKeyType.fluids())));
    }

    @AfterEach
    void closeKeyTypes() {
        keyTypes.close();
    }

    @Test
    void exactLedgersRoundTripThroughNbt() {
        var tracker = new ElapsedTimeTracker();
        tracker.startExactWork();
        tracker.addMaxItems(5L, AEKeyType.items());
        tracker.decrementItems(3L, AEKeyType.items());
        tracker.addMaxItems(7L, AEKeyType.fluids());

        CompoundTag saved = tracker.writeToNBT();
        var restored = new ElapsedTimeTracker(saved);

        assertEquals(BigInteger.valueOf(5), restored.getExactStartedWork(AEKeyType.items()));
        assertEquals(BigInteger.valueOf(3), restored.getExactCompletedWork(AEKeyType.items()));
        assertEquals(BigInteger.valueOf(7), restored.getExactStartedWork(AEKeyType.fluids()));
        assertEquals(BigInteger.ZERO, restored.getExactCompletedWork(AEKeyType.fluids()));

        CompoundTag resaved = restored.writeToNBT();
        assertTrue(resaved.getBoolean("exactWork"));
        assertEquals(saved.getCompound("exactStarted"), resaved.getCompound("exactStarted"));
        assertEquals(saved.getCompound("exactCompleted"), resaved.getCompound("exactCompleted"));
    }

    @Test
    void exactLedgerAboveLongRangeSurvivesLoad() {
        BigInteger huge = BigInteger.TWO.pow(70);
        CompoundTag tag = exactTag();
        tag.getCompound("exactStarted").putString(idOf(AEKeyType.items()), huge.toString());

        var restored = new ElapsedTimeTracker(tag);

        assertEquals(huge, restored.getExactStartedWork(AEKeyType.items()));
        assertEquals(huge.toString(), restored.writeToNBT().getCompound("exactStarted").getString(idOf(AEKeyType.items())));
    }

    @Test
    void registeredTypeMissingFromSavedLedgerLoadsAsZero() {
        CompoundTag tag = exactTag();
        tag.getCompound("exactStarted").putString(idOf(AEKeyType.items()), "5");
        tag.getCompound("exactCompleted").putString(idOf(AEKeyType.items()), "2");

        var restored = assertDoesNotThrow(() -> new ElapsedTimeTracker(tag));

        assertTrue(restored.writeToNBT().getBoolean("exactWork"));
        assertEquals(BigInteger.valueOf(5), restored.getExactStartedWork(AEKeyType.items()));
        assertEquals(BigInteger.ZERO, restored.getExactStartedWork(AEKeyType.fluids()));
        assertEquals(BigInteger.ZERO, restored.getExactCompletedWork(AEKeyType.fluids()));
    }

    @Test
    void malformedOrNegativeLedgerEntriesLoadAsZero() {
        CompoundTag tag = exactTag();
        tag.getCompound("exactStarted").putString(idOf(AEKeyType.items()), "not-a-number");
        tag.getCompound("exactStarted").putString(idOf(AEKeyType.fluids()), "-7");
        tag.getCompound("exactCompleted").putString(idOf(AEKeyType.items()), "");
        tag.getCompound("exactCompleted").putString(idOf(AEKeyType.fluids()), "3");

        var restored = assertDoesNotThrow(() -> new ElapsedTimeTracker(tag));

        assertEquals(BigInteger.ZERO, restored.getExactStartedWork(AEKeyType.items()));
        assertEquals(BigInteger.ZERO, restored.getExactStartedWork(AEKeyType.fluids()));
        assertEquals(BigInteger.ZERO, restored.getExactCompletedWork(AEKeyType.items()));
        assertEquals(BigInteger.valueOf(3), restored.getExactCompletedWork(AEKeyType.fluids()));
    }

    private static CompoundTag exactTag() {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("exactWork", true);
        tag.put("exactStarted", new CompoundTag());
        tag.put("exactCompleted", new CompoundTag());
        return tag;
    }

    private static String idOf(AEKeyType type) {
        return type.getId().toString();
    }
}
