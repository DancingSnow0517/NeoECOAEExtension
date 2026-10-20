package cn.dancingsnow.neoecoae.gui.storage;

import cn.dancingsnow.neoecoae.platform.JdkCompat;

import cn.dancingsnow.neoecoae.gui.common.HostText;
import cn.dancingsnow.neoecoae.gui.storage.StorageHostUI.CellEntry;
import appeng.api.storage.cells.CellState;
import com.mojang.serialization.DataResult;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
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
record StoragePanelState(Component title, int tier, boolean formed, boolean infinite, boolean migrating,
    long energy, long maxEnergy, long drain, String usedBytes, double usage, int cellCount, int cellOffset,
    List<CellEntry> cells, List<TypeLine> types, String domainId, int missingMembers, boolean componentPresent,
    BigInteger totalBytes) {

    StoragePanelState(Component title, int tier, boolean formed, boolean infinite, boolean migrating,
        long energy, long maxEnergy, long drain, String usedBytes, double usage, int cellCount, int cellOffset,
        List<CellEntry> cells, List<TypeLine> types, String domainId, int missingMembers, boolean componentPresent) {
        this(title, tier, formed, infinite, migrating, energy, maxEnergy, drain, usedBytes, usage, cellCount, cellOffset,
            cells, types, domainId, missingMembers, componentPresent, totalCapacity(cells));
    }

    static final StoragePanelState EMPTY = new StoragePanelState(Component.empty(), 0, false, false, false,
        0, 0, 0, "0", 0, 0, 0, List.of(), List.of(), "", 0, false);

    record TypeLine(int id, Component name, int color, long usedTypes, long totalTypes,
        long usedBytes, long totalBytes, String bytesText, int swatch, BigInteger exactUsedBytes) {
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
        if (total <= 0 || pageSize <= 0) return 0;
        int last = (total - 1) / pageSize * pageSize;
        return JdkCompat.clamp(requested, 0, last) / pageSize * pageSize;
    }

    /** Each page contains whole columns of all three rows; short structures leave gaps in every row. */
    static List<CellEntry> cellPage(List<CellEntry> all, int offset, int pageSize) {
        if (all.isEmpty()) return List.of();
        int columns = (all.size() + 2) / 3;
        int pageColumns = pageSize / 3;
        int firstColumn = offset / 3;
        List<CellEntry> result = new ArrayList<>(pageSize);
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < pageColumns; column++) {
                int source = row * columns + firstColumn + column;
                result.add(firstColumn + column < columns && source < all.size() ? all.get(source) : CellEntry.EMPTY);
            }
        }
        return List.copyOf(result);
    }

    static double usage(List<CellEntry> cells) {
        BigInteger used = BigInteger.ZERO, total = BigInteger.ZERO;
        for (CellEntry cell : cells) {
            if (cell.infinite() || cell.totalBytes() < 0) return 1;
            // Special cells report Long.MAX_VALUE instead of a comparable finite byte capacity.
            if (cell.totalBytes() == Long.MAX_VALUE) continue;
            used = used.add(BigInteger.valueOf(Math.max(0, cell.usedBytes())));
            total = total.add(BigInteger.valueOf(Math.max(0, cell.totalBytes())));
        }
        if (total.signum() == 0) return 0;
        return JdkCompat.clamp(new BigDecimal(used).divide(new BigDecimal(total), MathContext.DECIMAL64).doubleValue(), 0, 1);
    }

    private static BigInteger totalCapacity(List<CellEntry> cells) {
        return cells.stream().filter(cell -> cell.totalBytes() != Long.MAX_VALUE)
            .map(cell -> BigInteger.valueOf(Math.max(0, cell.totalBytes())))
            .reduce(BigInteger.ZERO, BigInteger::add);
    }

    TypeLine type(int id) {
        return types.stream().filter(type -> type.id() == id).findFirst().orElse(null);
    }

    boolean unbounded() { return infinite || migrating; }

    CompoundTag toTag() {
        return toTag(RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
    }

    CompoundTag toTag(HolderLookup.Provider registries) {
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
                CellState.values()[JdkCompat.clamp(cell.getInt("state"), 0, CellState.values().length - 1)], cell.getBoolean("online"),
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
}
