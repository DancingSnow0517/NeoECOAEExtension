package cn.dancingsnow.neoecoae.impl.storage;

import appeng.api.stacks.GenericStack;
import cn.dancingsnow.neoecoae.impl.storage.infinite.HugeAmount;
import com.google.common.math.LongMath;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

public final class ECOCellHandle {
    public static final int VERSION = 2;

    private static final String TAG_ID = "eco_cell_id";
    private static final String TAG_VERSION = "eco_cell_version";
    private static final String TAG_STATE = "eco_cell_state";
    private static final String TAG_USED_BYTES = "eco_cell_used_bytes";
    private static final String TAG_USED_TYPES = "eco_cell_used_types";
    private static final String TAG_STORED_AMOUNT = "eco_cell_stored_amount";
    static final String LEGACY_CONTENTS_TAG = "eco_cell_contents";

    private ECOCellHandle() {}

    public static Optional<UUID> getId(@Nullable ItemStack stack) {
        if (stack == null || stack.isEmpty() || !stack.hasTag()) {
            return Optional.empty();
        }
        CompoundTag tag = stack.getTag();
        return tag != null && tag.hasUUID(TAG_ID) ? Optional.of(tag.getUUID(TAG_ID)) : Optional.empty();
    }

    public static UUID getOrCreateId(ItemStack stack) {
        CompoundTag tag = stack.getOrCreateTag();
        UUID id;
        if (tag.hasUUID(TAG_ID)) {
            id = tag.getUUID(TAG_ID);
        } else {
            id = UUID.randomUUID();
            tag.putUUID(TAG_ID, id);
        }
        tag.putInt(TAG_VERSION, VERSION);
        return id;
    }

    public static void setId(ItemStack stack, UUID id) {
        CompoundTag tag = stack.getOrCreateTag();
        tag.putUUID(TAG_ID, id);
        tag.putInt(TAG_VERSION, VERSION);
    }

    public static int getVersion(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag == null ? 0 : tag.getInt(TAG_VERSION);
    }

    public static boolean hasLegacyContents(@Nullable ItemStack stack) {
        return stack != null
                && !stack.isEmpty()
                && stack.hasTag()
                && stack.getTag() != null
                && stack.getTag().contains(LEGACY_CONTENTS_TAG, Tag.TAG_LIST);
    }

    public static List<GenericStack> readLegacyStacks(ItemStack stack) {
        if (!hasLegacyContents(stack)) {
            return List.of();
        }

        ListTag list = stack.getTag().getList(LEGACY_CONTENTS_TAG, Tag.TAG_COMPOUND);
        List<GenericStack> stacks = new ArrayList<>(list.size());
        for (int i = 0; i < list.size(); i++) {
            GenericStack stored = GenericStack.readTag(list.getCompound(i));
            if (stored != null && stored.amount() > 0) {
                stacks.add(stored);
            }
        }
        return stacks;
    }

    public static void updateSummary(ItemStack stack, ECOStorageBackend backend, long usedBytes) {
        updateSummary(stack, backend.getStoredTypes(), backend.getStoredAmount(), usedBytes);
    }

    public static void updateSummary(ItemStack stack, int storedTypes, HugeAmount storedAmount, long usedBytes) {
        CompoundTag tag = stack.getOrCreateTag();
        tag.putInt(TAG_VERSION, VERSION);
        tag.putString(TAG_STATE, "normal");
        tag.putString(TAG_USED_BYTES, Long.toString(Math.max(0L, usedBytes)));
        tag.putInt(TAG_USED_TYPES, Math.max(0, storedTypes));
        tag.putString(TAG_STORED_AMOUNT, storedAmount.toBigInteger().max(BigInteger.ZERO).toString());
    }

    public static void updateSummaryFromLegacy(ItemStack stack, long usedBytes) {
        List<GenericStack> stacks = readLegacyStacks(stack);
        long amount = 0L;
        int types = 0;
        for (GenericStack stored : stacks) {
            if (stored.amount() <= 0L) {
                continue;
            }
            types++;
            amount = LongMath.saturatedAdd(amount, stored.amount());
        }
        updateSummary(stack, types, HugeAmount.of(amount), usedBytes);
    }

    public static void clearLegacyContents(@Nullable ItemStack stack) {
        if (stack == null || stack.isEmpty() || !stack.hasTag()) {
            return;
        }
        CompoundTag tag = stack.getTag();
        if (tag != null) {
            tag.remove(LEGACY_CONTENTS_TAG);
        }
    }

    public static void clearStorageHandle(@Nullable ItemStack stack) {
        if (stack == null || stack.isEmpty() || !stack.hasTag()) {
            return;
        }
        CompoundTag tag = stack.getTag();
        if (tag != null) {
            tag.remove(TAG_ID);
            tag.remove(TAG_VERSION);
            tag.remove(TAG_STATE);
            tag.remove(TAG_USED_BYTES);
            tag.remove(TAG_USED_TYPES);
            tag.remove(TAG_STORED_AMOUNT);
            tag.remove(LEGACY_CONTENTS_TAG);
        }
    }

    public static void markMissing(ItemStack stack) {
        CompoundTag tag = stack.getOrCreateTag();
        tag.putInt(TAG_VERSION, VERSION);
        tag.putString(TAG_STATE, "missing_data");
        tag.remove(LEGACY_CONTENTS_TAG);
    }

    public static void markLocked(ItemStack stack) {
        CompoundTag tag = stack.getOrCreateTag();
        tag.putInt(TAG_VERSION, VERSION);
        tag.putString(TAG_STATE, "locked");
        tag.remove(LEGACY_CONTENTS_TAG);
    }

    public static void clearProblemState(ItemStack stack) {
        CompoundTag tag = stack.getOrCreateTag();
        String state = tag.getString(TAG_STATE);
        if ("locked".equals(state) || "missing_data".equals(state)) {
            tag.remove(TAG_STATE);
        }
    }

    public static boolean hasNonEmptySummary(ItemStack stack) {
        return getStoredTypesSummary(stack) > 0 || getStoredAmountSummary(stack) > 0 || getUsedBytesSummary(stack) > 0;
    }

    public static long getStoredAmountSummary(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null) {
            return 0L;
        }
        if (tag.contains(TAG_STORED_AMOUNT)) {
            try {
                return HugeAmount.of(new BigInteger(tag.getString(TAG_STORED_AMOUNT))).toLongSaturated();
            } catch (RuntimeException ignored) {
                return 0L;
            }
        }
        return 0L;
    }

    public static long getUsedBytesSummary(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag == null ? 0L : parseLongTag(tag, TAG_USED_BYTES);
    }

    public static int getStoredTypesSummary(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag == null ? 0 : Math.max(0, tag.getInt(TAG_USED_TYPES));
    }

    public static boolean isMissing(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && "missing_data".equals(tag.getString(TAG_STATE));
    }

    private static long parseLongTag(CompoundTag tag, String key) {
        try {
            if (tag.contains(key)) {
                return Math.max(0L, Long.parseLong(tag.getString(key)));
            }
        } catch (NumberFormatException ignored) {
        }
        return 0L;
    }
}
