package cn.dancingsnow.neoecoae.impl.storage.transfer;

import java.util.UUID;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import org.jetbrains.annotations.Nullable;

/** Persistent identity and ownership metadata for an ordinary finite storage cell. */
public final class ECOFiniteCellMetadata {
    private static final String ROOT = "neoecoae_finite_storage";
    private static final String CELL_ID = "cellId";
    private static final String GENERATION = "generation";
    private static final String LEASE_ID = "leaseId";
    private static final String LEASE_GENERATION = "leaseGeneration";
    private static final String LEASE_COMMITTED = "leaseCommitted";

    public record State(UUID cellId, long generation, @Nullable UUID leaseId, long leaseGeneration,
                        boolean leaseCommitted) {}

    private ECOFiniteCellMetadata() {}

    public static State acquire(ItemStack stack, UUID domainId, boolean strictExistingLease) {
        CompoundTag custom = custom(stack);
        CompoundTag root = custom.getCompound(ROOT);
        UUID cellId = root.hasUUID(CELL_ID) ? root.getUUID(CELL_ID) : UUID.randomUUID();
        long generation = nonNegative(root.getLong(GENERATION));
        UUID existingLease = root.hasUUID(LEASE_ID) ? root.getUUID(LEASE_ID) : null;
        if (strictExistingLease) {
            if (!domainId.equals(existingLease) || !root.hasUUID(CELL_ID)) {
                throw new IllegalStateException("Finite storage cell ownership lease changed");
            }
            return new State(cellId, generation, existingLease,
                nonNegative(root.getLong(LEASE_GENERATION)), root.getBoolean(LEASE_COMMITTED));
        }
        root.putUUID(CELL_ID, cellId);
        root.putLong(GENERATION, generation);
        root.putUUID(LEASE_ID, domainId);
        root.putLong(LEASE_GENERATION, generation);
        root.putBoolean(LEASE_COMMITTED, false);
        write(stack, custom, root);
        return new State(cellId, generation, domainId, generation, false);
    }

    public static State read(ItemStack stack) {
        CompoundTag root = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY)
            .getUnsafe().getCompound(ROOT);
        UUID cellId = root.hasUUID(CELL_ID) ? root.getUUID(CELL_ID) : new UUID(0L, 0L);
        UUID leaseId = root.hasUUID(LEASE_ID) ? root.getUUID(LEASE_ID) : null;
        return new State(cellId, nonNegative(root.getLong(GENERATION)), leaseId,
            nonNegative(root.getLong(LEASE_GENERATION)), root.getBoolean(LEASE_COMMITTED));
    }

    public static void commit(ItemStack stack, UUID domainId, long expectedGeneration) {
        CompoundTag custom = custom(stack);
        CompoundTag root = custom.getCompound(ROOT);
        State state = read(stack);
        if (!domainId.equals(state.leaseId()) || state.leaseGeneration() != expectedGeneration) {
            throw new IllegalStateException("Finite storage cell ownership lease is stale");
        }
        long committedGeneration = expectedGeneration == Long.MAX_VALUE ? Long.MAX_VALUE : expectedGeneration + 1L;
        if (state.leaseCommitted()) {
            if (state.generation() != committedGeneration) {
                throw new IllegalStateException("Finite storage cell committed generation changed");
            }
            return;
        }
        if (state.generation() != expectedGeneration) {
            throw new IllegalStateException("Finite storage cell content generation changed");
        }
        root.putLong(GENERATION, committedGeneration);
        root.putBoolean(LEASE_COMMITTED, true);
        write(stack, custom, root);
    }

    public static void bumpGenerationIfUnleased(ItemStack stack) {
        CompoundTag custom = custom(stack);
        CompoundTag root = custom.getCompound(ROOT);
        if (root.hasUUID(LEASE_ID) && !root.getBoolean(LEASE_COMMITTED)) return;
        if (!root.hasUUID(CELL_ID)) root.putUUID(CELL_ID, UUID.randomUUID());
        long generation = nonNegative(root.getLong(GENERATION));
        root.putLong(GENERATION, generation == Long.MAX_VALUE ? Long.MAX_VALUE : generation + 1L);
        root.remove(LEASE_ID);
        root.remove(LEASE_GENERATION);
        root.remove(LEASE_COMMITTED);
        write(stack, custom, root);
    }

    private static long nonNegative(long value) {
        return Math.max(0L, value);
    }

    private static CompoundTag custom(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
    }

    private static void write(ItemStack stack, CompoundTag custom, CompoundTag root) {
        custom.put(ROOT, root);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(custom));
    }
}
