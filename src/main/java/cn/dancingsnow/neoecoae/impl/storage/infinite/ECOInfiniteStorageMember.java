package cn.dancingsnow.neoecoae.impl.storage.infinite;

import appeng.api.ids.AEComponents;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.component.DataComponents;
import org.jetbrains.annotations.Nullable;

public final class ECOInfiniteStorageMember {
    private static final String MEMBER_TAG = "neoecoae_infinite_member";
    private static final String DOMAIN_TAG = "neoecoae_infinite_domain";

    private ECOInfiniteStorageMember() {}

    public static boolean isMember(@Nullable ItemStack stack) {
        return stack != null
                && !stack.isEmpty()
                && stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY)
                        .copyTag()
                        .getBoolean(MEMBER_TAG);
    }

    public static Optional<UUID> getDomainId(@Nullable ItemStack stack) {
        if (!isMember(stack)) {
            return Optional.empty();
        }
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (!tag.hasUUID(DOMAIN_TAG)) {
            return Optional.empty();
        }
        return Optional.of(tag.getUUID(DOMAIN_TAG));
    }

    public static boolean isMemberOf(@Nullable ItemStack stack, UUID domainId) {
        return getDomainId(stack).map(domainId::equals).orElse(false);
    }

    public static void markMember(@Nullable ItemStack stack, UUID domainId) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        tag.putBoolean(MEMBER_TAG, true);
        tag.putUUID(DOMAIN_TAG, domainId);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    public static void copyClientSyncTags(CompoundTag source, CompoundTag target) {
        if (source.getBoolean(MEMBER_TAG)) {
            target.putBoolean(MEMBER_TAG, true);
        }
        if (source.hasUUID(DOMAIN_TAG)) {
            target.putUUID(DOMAIN_TAG, source.getUUID(DOMAIN_TAG));
        }
    }

    public static void clearMember(@Nullable ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        tag.remove(MEMBER_TAG);
        tag.remove(DOMAIN_TAG);
        if (tag.isEmpty()) {
            stack.remove(DataComponents.CUSTOM_DATA);
        } else {
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        }
    }

    public static void clearStoredContents(@Nullable ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        stack.remove(AEComponents.STORAGE_CELL_INV);
    }
}
