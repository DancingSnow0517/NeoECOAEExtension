package cn.dancingsnow.neoecoae.impl.storage.infinite;

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
        return hasStack(stack) && isSealedData(customData(stack));
    }

    static boolean isSealedData(CustomData data) {
        CompoundTag tag = readTag(data);
        return tag.hasUUID(MIGRATION_TAG) || tag.getBoolean(MEMBER_TAG);
    }

    /**
     * Read-only access: mutations must still copy the tag and replace the component.
     */
    @SuppressWarnings("deprecation")
    private static CompoundTag readTag(CustomData data) {
        return data.getUnsafe();
    }

    public static UUID identity(ItemStack stack) {
        Optional<UUID> existing = getIdentity(stack);
        if (existing.isPresent()) return existing.get();
        UUID identity = UUID.randomUUID();
        CompoundTag tag = copyTag(stack);
        tag.putUUID(IDENTITY_TAG, identity);
        writeTag(stack, tag);
        return identity;
    }

    public static Optional<UUID> getIdentity(@Nullable ItemStack stack) {
        return storedUuid(stack, IDENTITY_TAG);
    }

    public static boolean isMigrating(@Nullable ItemStack stack) {
        return getMigrationId(stack) != null;
    }

    public static @Nullable UUID getMigrationId(@Nullable ItemStack stack) {
        return hasStack(stack) ? migrationId(customData(stack)) : null;
    }

    public static Optional<UUID> getMigrationDomainId(@Nullable ItemStack stack) {
        return storedUuid(stack, MIGRATION_DOMAIN_TAG);
    }

    static @Nullable UUID migrationId(CustomData data) {
        CompoundTag tag = readTag(data);
        return tag.hasUUID(MIGRATION_TAG) ? tag.getUUID(MIGRATION_TAG) : null;
    }

    public static UUID beginMigration(ItemStack stack, UUID domain) {
        CompoundTag tag = copyTag(stack);
        if (tag.hasUUID(MIGRATION_TAG)) {
            if (!tag.hasUUID(MIGRATION_DOMAIN_TAG) || !domain.equals(tag.getUUID(MIGRATION_DOMAIN_TAG))) {
                throw new IllegalStateException("Storage cell belongs to another migration");
            }
            return tag.getUUID(MIGRATION_TAG);
        }
        UUID id = UUID.randomUUID();
        tag.putUUID(MIGRATION_TAG, id);
        tag.putUUID(MIGRATION_DOMAIN_TAG, domain);
        writeTag(stack, tag);
        return id;
    }

    public static boolean isMember(@Nullable ItemStack stack) {
        return hasStack(stack) && isMemberData(customData(stack));
    }

    static boolean isMemberData(CustomData data) {
        return readTag(data).getBoolean(MEMBER_TAG);
    }

    public static Optional<UUID> getDomainId(@Nullable ItemStack stack) {
        return hasStack(stack) ? domainId(customData(stack)) : Optional.empty();
    }

    static Optional<UUID> domainId(CustomData data) {
        CompoundTag tag = readTag(data);
        if (!tag.getBoolean(MEMBER_TAG) || !tag.hasUUID(DOMAIN_TAG)) {
            return Optional.empty();
        }
        return Optional.of(tag.getUUID(DOMAIN_TAG));
    }

    public static boolean isMemberOf(@Nullable ItemStack stack, UUID domainId) {
        return getDomainId(stack).map(domainId::equals).orElse(false);
    }

    public static void markMember(@Nullable ItemStack stack, UUID domainId) {
        if (!hasStack(stack)) {
            return;
        }
        CompoundTag tag = copyTag(stack);
        tag.putBoolean(MEMBER_TAG, true);
        tag.remove(MIGRATION_TAG);
        tag.remove(MIGRATION_DOMAIN_TAG);
        tag.putUUID(DOMAIN_TAG, domainId);
        if (!tag.hasUUID(IDENTITY_TAG)) tag.putUUID(IDENTITY_TAG, UUID.randomUUID());
        writeTag(stack, tag);
    }

    public static void clearMember(@Nullable ItemStack stack) {
        if (!hasStack(stack)) {
            return;
        }
        CompoundTag tag = copyTag(stack);
        tag.remove(MEMBER_TAG);
        tag.remove(DOMAIN_TAG);
        // Identities identify one membership lifetime. Keeping them on an ordinary cell makes
        // creative/NBT copies indistinguishable during the next restore.
        tag.remove(IDENTITY_TAG);
        writeTag(stack, tag);
    }

    public static void clearStoredContents(@Nullable ItemStack stack) {
        if (!hasStack(stack)) {
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

    private static boolean hasStack(@Nullable ItemStack stack) {
        return stack != null && !stack.isEmpty();
    }

    private static CustomData customData(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
    }

    private static CompoundTag copyTag(ItemStack stack) {
        return customData(stack).copyTag();
    }

    /** An empty tag removes the component, so a stack without membership or identity data carries no custom data. */
    private static void writeTag(ItemStack stack, CompoundTag tag) {
        if (tag.isEmpty()) {
            stack.remove(DataComponents.CUSTOM_DATA);
        } else {
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        }
    }

    private static Optional<UUID> storedUuid(@Nullable ItemStack stack, String key) {
        if (!hasStack(stack)) return Optional.empty();
        CompoundTag tag = readTag(customData(stack));
        return tag.hasUUID(key) ? Optional.of(tag.getUUID(key)) : Optional.empty();
    }
}
