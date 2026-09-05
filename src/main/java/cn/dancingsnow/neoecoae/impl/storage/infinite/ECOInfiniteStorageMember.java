package cn.dancingsnow.neoecoae.impl.storage.infinite;

import appeng.api.config.Actionable;
import appeng.api.ids.AEComponents;
import appeng.api.networking.security.IActionSource;
import cn.dancingsnow.neoecoae.api.storage.ECOStorageCells;
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
    private static final String MIGRATION_TAG = "neoecoae_migration_id";
    private static final String MIGRATION_DOMAIN_TAG = "neoecoae_migration_domain";
    private static final String IDENTITY_TAG = "neoecoae_member_identity";

    private ECOInfiniteStorageMember() {}

    public static boolean isSealed(@Nullable ItemStack stack) {
        return isMigrating(stack) || isMember(stack);
    }

    public static UUID identity(ItemStack stack) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (tag.hasUUID(IDENTITY_TAG)) return tag.getUUID(IDENTITY_TAG);
        UUID identity = UUID.randomUUID();
        tag.putUUID(IDENTITY_TAG, identity);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return identity;
    }

    public static boolean isMigrating(@Nullable ItemStack stack) {
        return stack != null && !stack.isEmpty()
            && stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().hasUUID(MIGRATION_TAG);
    }

    public static UUID beginMigration(ItemStack stack, UUID domain) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (tag.hasUUID(MIGRATION_TAG)) {
            if (!tag.hasUUID(MIGRATION_DOMAIN_TAG) || !domain.equals(tag.getUUID(MIGRATION_DOMAIN_TAG))) {
                throw new IllegalStateException("Storage cell belongs to another migration");
            }
            return tag.getUUID(MIGRATION_TAG);
        }
        UUID id = UUID.randomUUID();
        tag.putUUID(MIGRATION_TAG, id);
        tag.putUUID(MIGRATION_DOMAIN_TAG, domain);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return id;
    }

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
        tag.remove(MIGRATION_TAG);
        tag.remove(MIGRATION_DOMAIN_TAG);
        tag.putUUID(DOMAIN_TAG, domainId);
        if (!tag.hasUUID(IDENTITY_TAG)) tag.putUUID(IDENTITY_TAG, UUID.randomUUID());
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
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
        var inventory = ECOStorageCells.getCellInventory(stack, null);
        if (inventory instanceof cn.dancingsnow.neoecoae.integration.ae2omnicells.ECOUniversalStorageCell universal) {
            universal.clearMigrationStacks();
        } else if (inventory != null) {
            var available = inventory.getAvailableStacks();
            for (var entry : available) {
                inventory.extract(entry.getKey(), entry.getLongValue(), Actionable.MODULATE, IActionSource.empty());
            }
            inventory.persist();
        }
        // Standard ECO cells use this component. Removing it is also a safe fallback if no handler was available.
        stack.remove(AEComponents.STORAGE_CELL_INV);
    }
}
