package cn.dancingsnow.neoecoae.storage;

import static org.junit.jupiter.api.Assertions.*;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.storage.cells.ISaveProvider;
import cn.dancingsnow.neoecoae.compat.ae2.keyreuse.ECOKeyFactory;
import cn.dancingsnow.neoecoae.compat.ae2.keyreuse.KeyReuse;
import cn.dancingsnow.neoecoae.storage.persistence.ECOCellSavedData;
import java.math.BigInteger;
import java.nio.file.Path;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ECOStorageCellHotPathTest {
    private static final IActionSource SOURCE=IActionSource.empty();
    @BeforeAll static void bootstrap() throws Exception { StorageTestContext.bootstrap(); }
    @AfterEach void reset(){KeyReuse.configure(false);}

    @Test void equalKeysEpochsDeletionAndReadmissionShareOneLedger(@TempDir Path directory) throws Exception {
        KeyReuse.configure(true);
        try(var context=new StorageTestContext(directory)) {
            var first=AEItemKey.of(Items.IRON_INGOT);var equal=AEItemKey.of(Items.IRON_INGOT);
            assertNotSame(first,equal);assertEquals(first,equal);
            assertEquals(100,context.cell.insert(first,100,Actionable.MODULATE,SOURCE));
            KeyReuse.invalidate();var fresh=ECOKeyFactory.item(new ItemStack(Items.IRON_INGOT));
            assertEquals(30,context.cell.extract(equal,30,Actionable.MODULATE,SOURCE));
            assertEquals(70,context.cell.extract(fresh,100,Actionable.MODULATE,SOURCE));
            assertEquals(0,context.cell.getStoredItemTypes());
            KeyReuse.configure(false);
            assertEquals(17,context.cell.insert(equal,17,Actionable.MODULATE,SOURCE));
            assertEquals(17,context.cell.storageData().amounts().visible(first));
            assertEquals(1,context.cell.getStoredItemTypes());
        }
    }

    @Test void nbtAndCapabilitiesRemainSeparateAndSimulationsDoNotModifyResources(@TempDir Path directory) throws Exception {
        KeyReuse.configure(true);
        try(var context=new StorageTestContext(directory)) {
            var plain=AEItemKey.of(Items.IRON_INGOT);
            ItemStack tagged=new ItemStack(Items.IRON_INGOT);tagged.getOrCreateTag().putString("variant","A");
            var nbt=AEItemKey.of(tagged);
            CompoundTag encoded=plain.toTag();CompoundTag caps=new CompoundTag();caps.putString("capability","B");encoded.put("caps",caps);
            var capability=AEItemKey.fromTag(encoded);
            assertNotEquals(plain,nbt);assertNotEquals(plain,capability);
            context.cell.insert(plain,10,Actionable.MODULATE,SOURCE);
            context.cell.insert(nbt,20,Actionable.MODULATE,SOURCE);
            context.cell.insert(capability,30,Actionable.MODULATE,SOURCE);
            long revision=context.cell.contentRevision();
            assertEquals(30,context.cell.extract(capability,40,Actionable.SIMULATE,SOURCE));
            assertEquals(7,context.cell.insert(nbt,7,Actionable.SIMULATE,SOURCE));
            assertEquals(revision,context.cell.contentRevision());
            assertEquals(10,context.cell.storageData().amounts().visible(plain));
            assertEquals(20,context.cell.storageData().amounts().visible(AEItemKey.of(tagged.copy())));
            assertEquals(30,context.cell.storageData().amounts().visible(AEItemKey.fromTag(encoded.copy())));
            assertEquals(3,context.cell.getStoredItemTypes());
        }
    }

    @Test void ownershipReleaseFlushesAndBlocksTheOldHandle(@TempDir Path directory) throws Exception {
        try(var context=new StorageTestContext(directory)) {
            var key=AEItemKey.of(Items.STONE);
            context.cell.insert(key,23,Actionable.MODULATE,SOURCE);
            ISaveProvider replacement=()->{};
            var duplicate=context.session.open(context.stack,replacement);
            assertFalse(duplicate.writable());
            context.session.release(context.stack,context.owner);
            assertFalse(context.cell.storageWritable());
            assertEquals(context.cell.contentRevision(),context.cell.storageData().durableRevision());
            assertEquals(0,context.cell.insert(key,5,Actionable.MODULATE,SOURCE));
            var next=context.open(context.stack,replacement);
            assertTrue(next.storageWritable());assertEquals(23,next.extract(key,99,Actionable.SIMULATE,SOURCE));
            assertEquals(23,next.extract(key,99,Actionable.MODULATE,SOURCE));
        }
    }

    @Test void sparseExactReadsKeepOrdinaryKeysOutAndRespectSealing(@TempDir Path directory) throws Exception {
        try (var context = new StorageTestContext(directory)) {
            var ordinary = AEItemKey.of(Items.DIRT); var wide = AEItemKey.of(Items.STONE);
            context.cell.insert(ordinary, 20, Actionable.MODULATE, SOURCE);
            assertTrue(context.cell.neoecoae$hasSparseExactAmounts());
            assertFalse(context.cell.neoecoae$mayHaveExceptionalAmounts());
            var exact = BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.TEN);
            context.cell.insertBigInteger(wide, exact, Actionable.MODULATE, SOURCE);
            var values = new java.util.HashMap<appeng.api.stacks.AEKey, cn.dancingsnow.neoecoae.crafting.amount.ExactAmount>();
            context.cell.neoecoae$visitExceptionalAmounts(values::put);
            assertEquals(java.util.Set.of(wide), values.keySet());
            assertEquals(exact, values.get(wide).value());
            assertNull(context.cell.neoecoae$exceptionalAmount(ordinary));
            cn.dancingsnow.neoecoae.storage.infinite.ECOInfiniteStorageMember.markMember(context.stack, java.util.UUID.randomUUID());
            values.clear(); context.cell.neoecoae$visitExceptionalAmounts(values::put);
            assertTrue(values.isEmpty());
            assertFalse(context.cell.neoecoae$mayHaveExceptionalAmounts());
            assertNull(context.cell.neoecoae$exceptionalAmount(wide));
        }
    }

    @Test void exactCountsAndUnknownSerializedEntriesAreStillPreserved(@TempDir Path directory) throws Exception {
        try(var context=new StorageTestContext(directory)) {
            var key=AEItemKey.of(Items.STONE);
            BigInteger amount=BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.valueOf(5));
            assertEquals(amount,context.cell.insertBigInteger(key,amount,Actionable.MODULATE,SOURCE));
            assertTrue(context.cell.storageData().flush());
            var root=NbtIo.readCompressed(context.cell.storageData().dataFile().toFile()).getCompound("data");
            var restored=ECOCellSavedData.load(root,context.cell.storageData().id());
            assertEquals(amount,restored.amounts().get(key).toBigInteger());
            CompoundTag unknown=new CompoundTag();unknown.putString("raw","keep");
            root.getList("entries",10).add(unknown);
            var retained=ECOCellSavedData.load(root,context.cell.storageData().id());
            assertFalse(retained.canMutate());
            assertTrue(retained.save(new CompoundTag()).getList("entries",10).contains(unknown));
        }
    }

    @Test void finiteWideSourceClassificationUsesTheExactKeyWithoutListingOtherKeys(@TempDir Path directory) throws Exception {
        try(var context=new StorageTestContext(directory)) {
            var key=AEItemKey.of(Items.STONE);
            BigInteger initial=BigInteger.valueOf(Long.MAX_VALUE).multiply(BigInteger.valueOf(3));
            assertEquals(initial,context.cell.insertBigInteger(key,initial,Actionable.MODULATE,SOURCE));
            int[] listings={0};
            class Source implements appeng.api.storage.MEStorage, cn.dancingsnow.neoecoae.crafting.display.terminal.ExactAmountSource {
                @Override public long extract(appeng.api.stacks.AEKey what,long amount,Actionable mode,IActionSource action){
                    return context.cell.extract(what,amount,mode,action);
                }
                @Override public cn.dancingsnow.neoecoae.crafting.amount.ExactAmount neoecoae$exactAmount(appeng.api.stacks.AEKey what){
                    return context.cell.neoecoae$exactAmount(what);
                }
                @Override public void neoecoae$visitExactAmounts(java.util.function.BiConsumer<appeng.api.stacks.AEKey,
                    cn.dancingsnow.neoecoae.crafting.amount.ExactAmount> visitor){listings[0]++;context.cell.neoecoae$visitExactAmounts(visitor);}
                @Override public void getAvailableStacks(appeng.api.stacks.KeyCounter out){listings[0]++;context.cell.getAvailableStacks(out);}
                @Override public net.minecraft.network.chat.Component getDescription(){return net.minecraft.network.chat.Component.empty();}
            }
            assertEquals(7,cn.dancingsnow.neoecoae.storage.transfer.ECOCreativeExtractionFilter.extract(new Source(),key,7,SOURCE));
            assertEquals(initial.subtract(BigInteger.valueOf(7)),context.cell.storageData().amounts().exact(key));
            assertEquals(0,listings[0]);
            cn.dancingsnow.neoecoae.storage.infinite.ECOInfiniteStorageMember.markMember(context.stack,java.util.UUID.randomUUID());
            assertEquals(BigInteger.ZERO,context.cell.neoecoae$exactAmount(key).value());
        }
    }
}
