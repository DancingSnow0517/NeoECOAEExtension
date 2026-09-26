package cn.dancingsnow.neoecoae.api.me.attachment;

import cn.dancingsnow.neoecoae.api.me.lifecycle.ECOCraftingJobContext;
import cn.dancingsnow.neoecoae.api.me.lifecycle.ECOCraftingJobResult;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Owns extension attachment binding and lossless persistence for one CPU job. */
public final class ECOCraftingJobAttachments {
    private static final Logger LOGGER = LoggerFactory.getLogger("neoecoae");

    private final Map<ResourceLocation, ECOCraftingJobAttachment> bound = new LinkedHashMap<>();
    private final Map<ResourceLocation, CompoundTag> unboundData = new LinkedHashMap<>();

    public void initialize(ECOCraftingJobContext context) {
        bound.clear();
        unboundData.clear();
        for (var attachment : ECOCraftingJobAttachmentRegistry.createAll(context)) {
            bound.putIfAbsent(attachment.id(), attachment);
        }
    }

    public void reset() {
        bound.clear();
        unboundData.clear();
    }

    public void load(CompoundTag jobData, HolderLookup.Provider registries, ECOCraftingJobContext context) {
        initialize(context);
        CompoundTag persisted = jobData.getCompound("attachments");
        for (var entry : List.copyOf(bound.entrySet())) {
            String id = entry.getKey().toString();
            if (!persisted.contains(id, Tag.TAG_COMPOUND)) continue;
            try {
                entry.getValue().load(persisted.getCompound(id).copy(), registries);
                unboundData.put(entry.getKey(), persisted.getCompound(id).copy());
            } catch (RuntimeException failure) {
                LOGGER.error("ECO job attachment {} could not be restored; preserving raw state", id, failure);
                bound.remove(entry.getKey());
                unboundData.put(entry.getKey(), persisted.getCompound(id).copy());
            }
        }
        for (String idString : persisted.getAllKeys()) {
            ResourceLocation id = ResourceLocation.tryParse(idString);
            if (id == null) {
                LOGGER.warn("Ignoring ECO job attachment with invalid id {}", idString);
            } else if (!bound.containsKey(id)) {
                unboundData.put(id, persisted.getCompound(idString).copy());
            }
        }
    }

    public void save(CompoundTag jobData, HolderLookup.Provider registries, ECOCraftingJobContext context) {
        resolveUnbound(registries, context);
        CompoundTag persisted = new CompoundTag();
        for (var entry : bound.entrySet()) {
            try {
                CompoundTag data = entry.getValue().save(registries);
                if (data == null) throw new IllegalStateException("save returned null");
                persisted.put(entry.getKey().toString(), data.copy());
                unboundData.put(entry.getKey(), data.copy());
            } catch (RuntimeException failure) {
                CompoundTag previous = unboundData.get(entry.getKey());
                if (previous == null) {
                    throw new IllegalStateException("Unable to persist ECO job attachment " + entry.getKey(), failure);
                }
                persisted.put(entry.getKey().toString(), previous.copy());
                LOGGER.error("ECO job attachment {} save failed; retaining its previous payload",
                        entry.getKey(), failure);
            }
        }
        for (var entry : unboundData.entrySet()) {
            String id = entry.getKey().toString();
            if (!persisted.contains(id)) persisted.put(id, entry.getValue().copy());
        }
        if (persisted.isEmpty()) jobData.remove("attachments");
        else jobData.put("attachments", persisted);
    }

    public void clear(ECOCraftingJobResult result) {
        for (var attachment : List.copyOf(bound.values())) {
            try {
                attachment.clear(result);
            } catch (RuntimeException failure) {
                LOGGER.warn("ECO job attachment {} failed to clear", attachment.id(), failure);
            }
        }
        reset();
    }

    private void resolveUnbound(HolderLookup.Provider registries, ECOCraftingJobContext context) {
        if (unboundData.isEmpty()) return;
        for (var entry : List.copyOf(unboundData.entrySet())) {
            if (bound.containsKey(entry.getKey())) continue;
            var attachment = ECOCraftingJobAttachmentRegistry.create(entry.getKey(), context);
            if (attachment == null) continue;
            try {
                attachment.load(entry.getValue().copy(), registries);
                bound.put(entry.getKey(), attachment);
                unboundData.remove(entry.getKey());
            } catch (RuntimeException failure) {
                LOGGER.error("ECO job attachment {} could not be restored", entry.getKey(), failure);
            }
        }
    }
}
