package cn.dancingsnow.neoecoae.impl.storage.infinite;

import cn.dancingsnow.neoecoae.impl.storage.ECOCellHandle;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

public final class ECOInfiniteStorageMember {
    private static final String CONTENTS_TAG = "eco_cell_contents";
    private static final String MEMBER_TAG = "neoecoae_infinite_member";
    private static final String DOMAIN_TAG = "neoecoae_infinite_domain";

    private ECOInfiniteStorageMember() {}

    public static boolean isMember(@Nullable ItemStack stack) {
        return stack != null
                && !stack.isEmpty()
                && stack.hasTag()
                && stack.getTag().getBoolean(MEMBER_TAG);
    }

    public static Optional<UUID> getDomainId(@Nullable ItemStack stack) {
        if (!isMember(stack) || !stack.getTag().hasUUID(DOMAIN_TAG)) {
            return Optional.empty();
        }
        return Optional.of(stack.getTag().getUUID(DOMAIN_TAG));
    }

    public static boolean isMemberOf(@Nullable ItemStack stack, UUID domainId) {
        return getDomainId(stack).map(domainId::equals).orElse(false);
    }

    public static void markMember(@Nullable ItemStack stack, UUID domainId) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        CompoundTag tag = stack.getOrCreateTag();
        tag.putBoolean(MEMBER_TAG, true);
        tag.putUUID(DOMAIN_TAG, domainId);
    }

    public static void clearMember(@Nullable ItemStack stack) {
        if (stack == null || !stack.hasTag()) {
            return;
        }
        CompoundTag tag = stack.getTag();
        tag.remove(MEMBER_TAG);
        tag.remove(DOMAIN_TAG);
        if (tag.isEmpty()) {
            stack.setTag(null);
        }
    }

    public static void clearStoredContents(@Nullable ItemStack stack) {
        if (stack == null || !stack.hasTag()) {
            return;
        }
        CompoundTag tag = stack.getTag();
        tag.remove(CONTENTS_TAG);
        ECOCellHandle.clearStorageHandle(stack);
        if (tag.isEmpty()) {
            stack.setTag(null);
        }
    }
}
