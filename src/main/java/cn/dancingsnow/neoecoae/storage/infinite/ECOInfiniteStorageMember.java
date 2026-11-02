package cn.dancingsnow.neoecoae.storage.infinite;

import appeng.api.config.Actionable;
import appeng.api.ids.AEComponents;
import appeng.api.networking.security.IActionSource;
import cn.dancingsnow.neoecoae.api.storage.ECOStorageCells;
import cn.dancingsnow.neoecoae.api.storage.IECOStorageMigrationCell;

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

    private ECOInfiniteStorageMember() {
    }

    public static boolean isSealed(@Nullable ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        return isSealedData(stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY));
    }

    static boolean isSealedData(CustomData data) {
        CompoundTag tag = readTag(data);
        return tag.read(MIGRATION_TAG, net.minecraft.core.UUIDUtil.CODEC).isPresent() || tag.getBooleanOr(MEMBER_TAG, false);
    }

    /**
     * Read-only access: mutations must still copy the tag and replace the component.
     */
    @SuppressWarnings("deprecation")
    private static CompoundTag readTag(CustomData data) {
        return data.getUnsafe();
    }

    public static UUID identity(ItemStack stack) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (tag.read(IDENTITY_TAG, net.minecraft.core.UUIDUtil.CODEC).isPresent()) return tag.read(IDENTITY_TAG, net.minecraft.core.UUIDUtil.CODEC).orElseThrow();
        UUID identity = UUID.randomUUID();
        tag.store(IDENTITY_TAG, net.minecraft.core.UUIDUtil.CODEC, identity);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return identity;
    }

    public static Optional<UUID> getIdentity(@Nullable ItemStack stack) {
        if (stack == null || stack.isEmpty()) return Optional.empty();
        CompoundTag tag = readTag(stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY));
        return tag.read(IDENTITY_TAG, net.minecraft.core.UUIDUtil.CODEC).isPresent() ? Optional.of(tag.read(IDENTITY_TAG, net.minecraft.core.UUIDUtil.CODEC).orElseThrow()) : Optional.empty();
    }

    public static boolean isMigrating(@Nullable ItemStack stack) {
        return getMigrationId(stack) != null;
    }

    public static @Nullable UUID getMigrationId(@Nullable ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        return migrationId(stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY));
    }

    public static Optional<UUID> getMigrationDomainId(@Nullable ItemStack stack) {
        if (stack == null || stack.isEmpty()) return Optional.empty();
        CompoundTag tag = readTag(stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY));
        return tag.read(MIGRATION_DOMAIN_TAG, net.minecraft.core.UUIDUtil.CODEC).isPresent() ? Optional.of(tag.read(MIGRATION_DOMAIN_TAG, net.minecraft.core.UUIDUtil.CODEC).orElseThrow()) : Optional.empty();
    }

    static @Nullable UUID migrationId(CustomData data) {
        CompoundTag tag = readTag(data);
        return tag.read(MIGRATION_TAG, net.minecraft.core.UUIDUtil.CODEC).isPresent() ? tag.read(MIGRATION_TAG, net.minecraft.core.UUIDUtil.CODEC).orElseThrow() : null;
    }

    public static UUID beginMigration(ItemStack stack, UUID domain) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (tag.read(MIGRATION_TAG, net.minecraft.core.UUIDUtil.CODEC).isPresent()) {
            if (!tag.read(MIGRATION_DOMAIN_TAG, net.minecraft.core.UUIDUtil.CODEC).isPresent() || !domain.equals(tag.read(MIGRATION_DOMAIN_TAG, net.minecraft.core.UUIDUtil.CODEC).orElseThrow())) {
                throw new IllegalStateException("Storage cell belongs to another migration");
            }
            return tag.read(MIGRATION_TAG, net.minecraft.core.UUIDUtil.CODEC).orElseThrow();
        }
        UUID id = UUID.randomUUID();
        tag.store(MIGRATION_TAG, net.minecraft.core.UUIDUtil.CODEC, id);
        tag.store(MIGRATION_DOMAIN_TAG, net.minecraft.core.UUIDUtil.CODEC, domain);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return id;
    }

    public static boolean isMember(@Nullable ItemStack stack) {
        return stack != null
                && !stack.isEmpty()
                && isMemberData(stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY));
    }

    static boolean isMemberData(CustomData data) {
        return readTag(data).getBooleanOr(MEMBER_TAG, false);
    }

    public static Optional<UUID> getDomainId(@Nullable ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return Optional.empty();
        }
        return domainId(stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY));
    }

    static Optional<UUID> domainId(CustomData data) {
        CompoundTag tag = readTag(data);
        if (!tag.getBooleanOr(MEMBER_TAG, false) || !tag.read(DOMAIN_TAG, net.minecraft.core.UUIDUtil.CODEC).isPresent()) {
            return Optional.empty();
        }
        return Optional.of(tag.read(DOMAIN_TAG, net.minecraft.core.UUIDUtil.CODEC).orElseThrow());
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
        tag.store(DOMAIN_TAG, net.minecraft.core.UUIDUtil.CODEC, domainId);
        if (!tag.read(IDENTITY_TAG, net.minecraft.core.UUIDUtil.CODEC).isPresent()) tag.store(IDENTITY_TAG, net.minecraft.core.UUIDUtil.CODEC, UUID.randomUUID());
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    public static void clearMember(@Nullable ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        tag.remove(MEMBER_TAG);
        tag.remove(DOMAIN_TAG);
        // Identities identify one membership lifetime. Keeping them on an ordinary cell makes
        // creative/NBT copies indistinguishable during the next restore.
        tag.remove(IDENTITY_TAG);
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
        if (inventory instanceof IECOStorageMigrationCell migrationCell) {
            migrationCell.clearMigrationStacks();
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
