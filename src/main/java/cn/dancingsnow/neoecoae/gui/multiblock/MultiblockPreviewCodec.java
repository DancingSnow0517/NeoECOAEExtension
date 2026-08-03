package cn.dancingsnow.neoecoae.gui.multiblock;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/** Codec kept separate from the UI element so base data tests do not initialize LDLib UI state. */
final class MultiblockPreviewCodec {
    private MultiblockPreviewCodec() {}

    static CompoundTag encode(MultiblockPreviewSnapshot snapshot, HolderLookup.Provider registries) {
        CompoundTag out = encodeBase(snapshot);
        ListTag materials = new ListTag();
        for (MultiblockPreviewSnapshot.Material material : snapshot.materials()) {
            CompoundTag item = new CompoundTag();
            item.put("stack", material.stack().save(registries));
            item.putInt("required", material.required());
            item.putBoolean("enough", material.enough());
            materials.add(item);
        }
        out.put("materials", materials);
        return out;
    }

    static CompoundTag encodeBase(MultiblockPreviewSnapshot snapshot) {
        CompoundTag out = new CompoundTag();
        out.putString("status", snapshot.status().name());
        out.putInt("missing", snapshot.missing());
        out.putInt("conflictCount", snapshot.conflictCount());
        out.putInt("reused", snapshot.reused());
        out.putInt("requiredCount", snapshot.requiredCount());
        ListTag conflicts = new ListTag();
        for (BlockPos pos : snapshot.conflicts()) {
            CompoundTag p = new CompoundTag();
            p.putInt("x", pos.getX());
            p.putInt("y", pos.getY());
            p.putInt("z", pos.getZ());
            conflicts.add(p);
        }
        out.put("conflicts", conflicts);
        return out;
    }

    static MultiblockPreviewSnapshot decode(@Nullable CompoundTag tag, HolderLookup.Provider registries) {
        MultiblockPreviewSnapshot base = decodeBase(tag);
        if (tag == null) return base;
        List<MultiblockPreviewSnapshot.Material> materials = new ArrayList<>();
        ListTag materialTags = tag.getList("materials", Tag.TAG_COMPOUND);
        for (int i = 0; i < Math.min(materialTags.size(), MultiblockPreviewSnapshot.MAX_MATERIALS); i++) {
            CompoundTag item = materialTags.getCompound(i);
            if (!item.contains("stack", Tag.TAG_COMPOUND) || !item.contains("required", Tag.TAG_INT) || !item.contains("enough", Tag.TAG_BYTE)) continue;
            materials.add(new MultiblockPreviewSnapshot.Material(
                ItemStack.parseOptional(registries, item.getCompound("stack")), item.getInt("required"), item.getBoolean("enough")));
        }
        return new MultiblockPreviewSnapshot(base.status(), base.missing(), base.conflictCount(), base.reused(), base.requiredCount(), base.conflicts(), materials);
    }

    static MultiblockPreviewSnapshot decodeBase(@Nullable CompoundTag tag) {
        if (tag == null) return new MultiblockPreviewSnapshot(MultiblockPreviewSnapshot.Status.NO_DEFINITION, 0, 0, 0, 0, List.of(), List.of());
        MultiblockPreviewSnapshot.Status status;
        try { status = MultiblockPreviewSnapshot.Status.valueOf(tag.getString("status")); }
        catch (IllegalArgumentException e) { status = MultiblockPreviewSnapshot.Status.NO_DEFINITION; }
        List<BlockPos> conflicts = new ArrayList<>();
        ListTag conflictTags = tag.getList("conflicts", Tag.TAG_COMPOUND);
        for (int i = 0; i < Math.min(conflictTags.size(), MultiblockPreviewSnapshot.MAX_CONFLICTS); i++) {
            CompoundTag p = conflictTags.getCompound(i);
            conflicts.add(new BlockPos(p.getInt("x"), p.getInt("y"), p.getInt("z")));
        }
        return new MultiblockPreviewSnapshot(status, tag.getInt("missing"), tag.getInt("conflictCount"), tag.getInt("reused"), tag.getInt("requiredCount"), conflicts, List.of());
    }
}
