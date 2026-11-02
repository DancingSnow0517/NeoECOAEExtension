package cn.dancingsnow.neoecoae.crafting;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.crafting.CraftingLink;
import appeng.crafting.inv.ListCraftingInventory;
import cn.dancingsnow.neoecoae.util.LegacyNbt;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.Nullable;

/**
 * 26.1 port bridge: AE2 26.1 only (de)serializes keys, stacks, links and crafting inventories through
 * {@code ValueInput}/{@code ValueOutput}. The ECO crafting persistence keeps its 1.21.1 CompoundTag layout, so
 * these helpers convert in both directions without changing the on-disk format.
 */
public final class ECOLegacyAENbt {
    private static final String LIST_KEY = "list";

    private ECOLegacyAENbt() {
    }

    /** Replacement for 1.21.1 {@code legacy writeStack(registries, stack)}. */
    public static CompoundTag writeStack(HolderLookup.Provider registries, @Nullable GenericStack stack) {
        return LegacyNbt.output(registries, out -> GenericStack.writeTag(out, stack));
    }

    /** Replacement for 1.21.1 {@code legacy readStack(registries, tag)}. */
    @Nullable
    public static GenericStack readStack(HolderLookup.Provider registries, @Nullable CompoundTag tag) {
        if (tag == null || tag.isEmpty()) {
            return null;
        }
        try {
            return GenericStack.readTag(LegacyNbt.input(registries, tag));
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** Replacement for 1.21.1 {@code legacy writeKey(registries, key)}. */
    public static CompoundTag writeKey(HolderLookup.Provider registries, AEKey key) {
        return LegacyNbt.output(registries, key::toTagGeneric);
    }

    /** Replacement for 1.21.1 {@code legacy readKey(registries, tag)}. */
    @Nullable
    public static AEKey readKey(HolderLookup.Provider registries, @Nullable Tag tag) {
        if (!(tag instanceof CompoundTag compound) || compound.isEmpty()) {
            return null;
        }
        return AEKey.fromTagGeneric(LegacyNbt.input(registries, compound));
    }

    /** Replacement for 1.21.1 {@code itemKey.toTag(registries)}. */
    public static CompoundTag writeItemKey(HolderLookup.Provider registries, AEItemKey key) {
        return LegacyNbt.output(registries, key::toTag);
    }

    /** Replacement for 1.21.1 {@code legacy readItemKey(registries, tag)}. */
    @Nullable
    public static AEItemKey readItemKey(HolderLookup.Provider registries, @Nullable CompoundTag tag) {
        if (tag == null || tag.isEmpty()) {
            return null;
        }
        try {
            return AEItemKey.fromTag(LegacyNbt.input(registries, tag));
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** Replacement for 1.21.1 {@code link.writeToNBT(tag)}. */
    public static CompoundTag writeLink(HolderLookup.Provider registries, CraftingLink link) {
        return LegacyNbt.output(registries, link::writeToNBT);
    }

    /** Replacement for 1.21.1 {@code inventory.writeToNBT(registries)}. */
    public static ListTag writeInventory(HolderLookup.Provider registries, ListCraftingInventory inventory) {
        CompoundTag wrapper = LegacyNbt.output(registries, out -> inventory.serialize(out.childrenList(LIST_KEY)));
        return wrapper.getListOrEmpty(LIST_KEY);
    }

    /** Replacement for 1.21.1 {@code inventory.readFromNBT(list, registries)}. */
    public static void readInventory(HolderLookup.Provider registries, ListCraftingInventory inventory, ListTag data) {
        CompoundTag wrapper = new CompoundTag();
        wrapper.put(LIST_KEY, data);
        inventory.deserialize(LegacyNbt.input(registries, wrapper).childrenListOrEmpty(LIST_KEY));
    }
}
