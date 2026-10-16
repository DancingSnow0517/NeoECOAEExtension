package cn.dancingsnow.neoecoae.gui.storage;

import cn.dancingsnow.neoecoae.gui.common.HostText;
import cn.dancingsnow.neoecoae.gui.storage.StorageHostUI.CellEntry;
import appeng.api.storage.cells.CellState;
import com.mojang.serialization.DataResult;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.world.item.ItemStack;

/** A server-authored display snapshot. Only the current cell page is sent to the client. */
final class StoragePanelState {
    private final cn.dancingsnow.neoecoae.core.ui.StoragePanelState<Component, CellEntry, TypeLine> model;
    StoragePanelState(Component title, int tier, boolean formed, boolean infinite, boolean migrating,
    long energy, long maxEnergy, long drain, String usedBytes, double usage, int cellCount, int cellOffset,
    List<CellEntry> cells, List<TypeLine> types, String domainId, int missingMembers, boolean componentPresent,
    BigInteger totalBytes) {
        this.model = new cn.dancingsnow.neoecoae.core.ui.StoragePanelState<>(title, tier, formed, infinite, migrating, energy, maxEnergy, drain, usedBytes, usage, cellCount, cellOffset, cells, types, domainId, missingMembers, componentPresent, totalBytes);
    }

    Component title() { return model.title(); }
    int tier() { return model.tier(); }
    boolean formed() { return model.formed(); }
    boolean infinite() { return model.infinite(); }
    boolean migrating() { return model.migrating(); }
    long energy() { return model.energy(); }
    long maxEnergy() { return model.maxEnergy(); }
    long drain() { return model.drain(); }
    String usedBytes() { return model.usedBytes(); }
    double usage() { return model.usage(); }
    int cellCount() { return model.cellCount(); }
    int cellOffset() { return model.cellOffset(); }
    List<CellEntry> cells() { return model.cells(); }
    List<TypeLine> types() { return model.types(); }
    String domainId() { return model.domainId(); }
    int missingMembers() { return model.missingMembers(); }
    boolean componentPresent() { return model.componentPresent(); }
    BigInteger totalBytes() { return model.totalBytes(); }


    StoragePanelState(Component title, int tier, boolean formed, boolean infinite, boolean migrating,
        long energy, long maxEnergy, long drain, String usedBytes, double usage, int cellCount, int cellOffset,
        List<CellEntry> cells, List<TypeLine> types, String domainId, int missingMembers, boolean componentPresent) {
        this(title, tier, formed, infinite, migrating, energy, maxEnergy, drain, usedBytes, usage, cellCount, cellOffset,
            cells, types, domainId, missingMembers, componentPresent, totalCapacity(cells));
    }

    static final StoragePanelState EMPTY = new StoragePanelState(Component.empty(), 0, false, false, false,
        0, 0, 0, "0", 0, 0, 0, List.of(), List.of(), "", 0, false);

    record TypeLine(int id, Component name, int color, long usedTypes, long totalTypes,
        long usedBytes, long totalBytes, String bytesText, int swatch, BigInteger exactUsedBytes)
            implements cn.dancingsnow.neoecoae.core.ui.StorageTypeView {
        TypeLine(int id, Component name, int color, long usedTypes, long totalTypes,
            long usedBytes, long totalBytes, String bytesText) {
            this(id, name, color, usedTypes, totalTypes, usedBytes, totalBytes, bytesText,
                StoragePanelTextures.FALLBACK_SWATCH, BigInteger.valueOf(Math.max(0, usedBytes)));
        }
    }

    static StoragePanelState capture(StorageHostUI.Config config, int requestedOffset, int pageSize) {
        if (config == null) return EMPTY;
        List<CellEntry> all = StorageHostUI.safeEntries(config.cellEntries());
        int offset = pageOffset(requestedOffset, all.size(), pageSize);
        boolean infinite = config.infiniteStorage().getAsBoolean();
        boolean migrating = config.migratingToInfinite().getAsBoolean();
        List<TypeLine> types = new ArrayList<>();
        for (StorageHostUI.StorageTypeLine line : config.storageTypes()) {
            if (!line.visible().getAsBoolean()) continue;
            long usedTypes = line.usedTypes().getAsLong(), totalTypes = line.totalTypes().getAsLong();
            long usedBytes = line.usedBytes().getAsLong(), totalBytes = line.totalBytes().getAsLong();
            // Empty drives use -1, which also identifies the first infinite resource type.
            if (usedTypes == 0 && totalTypes == 0 && usedBytes == 0 && totalBytes == 0
                && all.stream().noneMatch(cell -> cell.installed() && cell.typeId() == line.registryIndex())) continue;
            int swatch = StoragePanelTextures.swatchIndex(line.resourceId());
            int color = swatch < StoragePanelTextures.FALLBACK_SWATCH ? StoragePanelTextures.COLORS[swatch]
                : HostText.storageTypeAccentColor(line.type(), line.registryIndex());
            types.add(new TypeLine(line.registryIndex(), line.displayName().get(), color,
                usedTypes, totalTypes, usedBytes, totalBytes,
                infinite || migrating || totalBytes < 0 ? line.infiniteBytesText().get() : HostText.ae2Amount(usedBytes),
                swatch, line.exactUsedBytes().get().max(BigInteger.ZERO)));
        }
        return new StoragePanelState(config.title().get(), config.tier().getAsInt(), config.formed().getAsBoolean(),
            infinite, migrating, config.storedEnergy().getAsLong(), config.maxEnergy().getAsLong(),
            config.energyConsumePerTick().getAsLong(), config.totalUsedBytesText().get(), usage(all), all.size(), offset,
            cellPage(all, offset, pageSize), List.copyOf(types),
            config.domainId().get(), config.missingMembers().getAsInt(),
            !config.infiniteComponentInventory().getStackInSlot(0).isEmpty(), totalCapacity(all));
    }

    static int pageOffset(int requested, int total, int pageSize) {
        return cn.dancingsnow.neoecoae.core.ui.StoragePanelState.pageOffset(requested, total, pageSize);
    }

    static List<CellEntry> cellPage(List<CellEntry> all, int offset, int pageSize) {
        return cn.dancingsnow.neoecoae.core.ui.StoragePanelState.cellPage(all, offset, pageSize, CellEntry.EMPTY);
    }

    static double usage(List<CellEntry> cells) {
        return cn.dancingsnow.neoecoae.core.ui.StoragePanelState.usage(cells);
    }

    private static BigInteger totalCapacity(List<CellEntry> cells) {
        return cn.dancingsnow.neoecoae.core.ui.StoragePanelState.totalCapacity(cells);
    }

    TypeLine type(int id) { return model.type(id); }
    boolean unbounded() { return model.unbounded(); }

    CompoundTag toTag() {
        return toTag(RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
    }

    CompoundTag toTag(HolderLookup.Provider registries) {
        Component title = title();
        int tier = tier();
        boolean formed = formed();
        boolean infinite = infinite();
        boolean migrating = migrating();
        long energy = energy();
        long maxEnergy = maxEnergy();
        long drain = drain();
        String usedBytes = usedBytes();
        double usage = usage();
        int cellCount = cellCount();
        int cellOffset = cellOffset();
        List<CellEntry> cells = cells();
        List<TypeLine> types = types();
        String domainId = domainId();
        int missingMembers = missingMembers();
        boolean componentPresent = componentPresent();
        BigInteger totalBytes = totalBytes();
        CompoundTag tag = new CompoundTag();
        putComponent(tag, "title", title);
        tag.putInt("tier", tier); tag.putBoolean("formed", formed);
        tag.putBoolean("infinite", infinite); tag.putBoolean("migrating", migrating);
        tag.putLong("energy", energy); tag.putLong("maxEnergy", maxEnergy); tag.putLong("drain", drain);
        tag.putString("usedBytes", usedBytes); tag.putDouble("usage", usage);
        tag.putString("totalBytes", totalBytes.toString());
        tag.putInt("cellCount", cellCount); tag.putInt("cellOffset", cellOffset);
        tag.putString("domain", domainId); tag.putInt("missing", missingMembers);
        tag.putBoolean("component", componentPresent);
        ListTag entries = new ListTag();
        for (CellEntry cell : cells) {
            CompoundTag entry = new CompoundTag();
            entry.putInt("id", cell.typeId()); entry.putInt("tier", cell.tier()); entry.putInt("kind", cell.kind());
            if (cell.panelTierName() != null) putComponent(entry, "tierName", cell.panelTierName());
            entry.putLong("ut", cell.usedTypes()); entry.putLong("tt", cell.totalTypes());
            entry.putLong("ub", cell.usedBytes()); entry.putLong("tb", cell.totalBytes());
            entry.putBoolean("infinite", cell.infinite());
            if (cell.position() != null) entry.putLong("position", cell.position().asLong());
            if (cell.interactionId() != null) entry.putUUID("interactionId", cell.interactionId());
            if (!cell.stack().isEmpty()) entry.put("stack", cell.stack().save(registries));
            if (cell.extractionBlockReason() != null) putComponent(entry, "locked", cell.extractionBlockReason());
            entry.putInt("state", cell.state().ordinal()); entry.putBoolean("online", cell.online()); entries.add(entry);
        }
        tag.put("cells", entries);
        ListTag lines = new ListTag();
        for (TypeLine type : types) {
            CompoundTag entry = new CompoundTag();
            entry.putInt("id", type.id()); putComponent(entry, "name", type.name()); entry.putInt("color", type.color());
            entry.putLong("ut", type.usedTypes()); entry.putLong("tt", type.totalTypes());
            entry.putLong("ub", type.usedBytes()); entry.putLong("tb", type.totalBytes());
            entry.putString("text", type.bytesText()); lines.add(entry);
            entry.putInt("swatch", type.swatch()); entry.putString("exactBytes", type.exactUsedBytes().toString());
        }
        tag.put("types", lines);
        return tag;
    }

    static StoragePanelState fromTag(Tag value) {
        return fromTag(value, RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
    }

    static StoragePanelState fromTag(Tag value, HolderLookup.Provider registries) {
        if (!(value instanceof CompoundTag tag) || tag.isEmpty()) return EMPTY;
        List<CellEntry> cells = new ArrayList<>();
        for (Tag valueCell : tag.getList("cells", Tag.TAG_COMPOUND)) {
            CompoundTag cell = (CompoundTag) valueCell;
            cells.add(new CellEntry(cell.getInt("id"), cell.getInt("tier"), cell.getInt("kind"),
                cell.getLong("ut"), cell.getLong("tt"), cell.getLong("ub"), cell.getLong("tb"), cell.getBoolean("infinite"),
                CellState.values()[Math.clamp(cell.getInt("state"), 0, CellState.values().length - 1)], cell.getBoolean("online"),
                cell.contains("position") ? BlockPos.of(cell.getLong("position")) : null,
                ItemStack.parseOptional(registries, cell.getCompound("stack")),
                cell.contains("locked") ? component(cell, "locked") : null,
                cell.hasUUID("interactionId") ? cell.getUUID("interactionId") : null,
                cell.contains("tierName") ? component(cell, "tierName") : null));
        }
        List<TypeLine> types = new ArrayList<>();
        for (Tag valueType : tag.getList("types", Tag.TAG_COMPOUND)) {
            CompoundTag type = (CompoundTag) valueType;
            types.add(new TypeLine(type.getInt("id"), component(type, "name"), type.getInt("color"),
                type.getLong("ut"), type.getLong("tt"), type.getLong("ub"), type.getLong("tb"), type.getString("text"),
                type.contains("swatch") ? type.getInt("swatch") : StoragePanelTextures.FALLBACK_SWATCH,
                type.contains("exactBytes") ? new BigInteger(type.getString("exactBytes"))
                    : BigInteger.valueOf(Math.max(0, type.getLong("ub")))));
        }
        return new StoragePanelState(component(tag, "title"), tag.getInt("tier"), tag.getBoolean("formed"),
            tag.getBoolean("infinite"), tag.getBoolean("migrating"), tag.getLong("energy"), tag.getLong("maxEnergy"),
            tag.getLong("drain"), tag.getString("usedBytes"), tag.getDouble("usage"), tag.getInt("cellCount"),
            tag.getInt("cellOffset"), List.copyOf(cells), List.copyOf(types), tag.getString("domain"),
            tag.getInt("missing"), tag.getBoolean("component"),
            tag.contains("totalBytes") ? new BigInteger(tag.getString("totalBytes")) : totalCapacity(cells));
    }

    private static void putComponent(CompoundTag tag, String key, Component component) {
        DataResult<Tag> encoded = ComponentSerialization.CODEC.encodeStart(NbtOps.INSTANCE, component);
        tag.put(key, encoded.getOrThrow());
    }

    private static Component component(CompoundTag tag, String key) {
        Tag value = tag.get(key);
        return value == null ? Component.empty()
            : ComponentSerialization.CODEC.parse(NbtOps.INSTANCE, value).result().orElse(Component.empty());
    }
    @Override public boolean equals(Object other) {
        return other instanceof StoragePanelState state && model.equals(state.model);
    }
    @Override public int hashCode() { return model.hashCode(); }
    @Override public String toString() { return model.toString(); }
}
