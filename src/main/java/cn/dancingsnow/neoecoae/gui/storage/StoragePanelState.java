package cn.dancingsnow.neoecoae.gui.storage;

import cn.dancingsnow.neoecoae.api.storage.ECOStorageChannelDisplays;

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
    BigInteger totalBytes, BigInteger exactUsedBytes, int migrationProgress, Component migrationBlocked) {

    StoragePanelState {
        migrationProgress = Math.clamp(migrationProgress, 0, 100);
        migrationBlocked = migrationBlocked == null ? Component.empty() : migrationBlocked;
    }

    StoragePanelState(Component title, int tier, boolean formed, boolean infinite, boolean migrating,
        long energy, long maxEnergy, long drain, String usedBytes, double usage, int cellCount, int cellOffset,
        List<CellEntry> cells, List<TypeLine> types, String domainId, int missingMembers, boolean componentPresent,
        BigInteger totalBytes, BigInteger exactUsedBytes) {
        this(title, tier, formed, infinite, migrating, energy, maxEnergy, drain, usedBytes, usage, cellCount, cellOffset,
            cells, types, domainId, missingMembers, componentPresent, totalBytes, exactUsedBytes, 0, Component.empty());
    }

    StoragePanelState(Component title, int tier, boolean formed, boolean infinite, boolean migrating,
        long energy, long maxEnergy, long drain, String usedBytes, double usage, int cellCount, int cellOffset,
        List<CellEntry> cells, List<TypeLine> types, String domainId, int missingMembers, boolean componentPresent,
        BigInteger totalBytes) {
        this(title, tier, formed, infinite, migrating, energy, maxEnergy, drain, usedBytes, usage, cellCount, cellOffset,
            cells, types, domainId, missingMembers, componentPresent, totalBytes,
            totalUsedBytes(cells, types, infinite || migrating));
    }

    StoragePanelState(Component title, int tier, boolean formed, boolean infinite, boolean migrating,
        long energy, long maxEnergy, long drain, String usedBytes, double usage, int cellCount, int cellOffset,
        List<CellEntry> cells, List<TypeLine> types, String domainId, int missingMembers, boolean componentPresent) {
        this(title, tier, formed, infinite, migrating, energy, maxEnergy, drain, usedBytes, usage, cellCount, cellOffset,
            cells, types, domainId, missingMembers, componentPresent, totalCapacity(cells));
    }

    static final StoragePanelState EMPTY = new StoragePanelState(Component.empty(), 0, false, false, false,
        0, 0, 0, "0", 0, 0, 0, List.of(), List.of(), "", 0, false);

    record TypeLine(int id, Component name, int color, long usedTypes, long totalTypes,
        long usedBytes, long totalBytes, String bytesText, int swatch, BigInteger exactUsedBytes,
        net.minecraft.resources.Identifier channel, net.minecraft.resources.Identifier icon,
        net.minecraft.resources.Identifier swatchTexture) {
        TypeLine(int id, Component name, int color, long usedTypes, long totalTypes,
            long usedBytes, long totalBytes, String bytesText, int swatch, BigInteger exactUsedBytes) {
            this(id, name, color, usedTypes, totalTypes, usedBytes, totalBytes, bytesText, swatch, exactUsedBytes, null, null, null);
        }
        TypeLine(int id, Component name, int color, long usedTypes, long totalTypes,
            long usedBytes, long totalBytes, String bytesText) {
            this(id, name, color, usedTypes, totalTypes, usedBytes, totalBytes, bytesText,
                StoragePanelTextures.FALLBACK_SWATCH, BigInteger.valueOf(Math.max(0, usedBytes)));
        }
    }

    static StoragePanelState capture(StorageHostUI.Config config, int requestedOffset, int pageSize) {
        List<CellEntry> all = config.cellEntries().get();
        int offset = pageOffset(requestedOffset, all.size(), pageSize);
        boolean infinite = config.infiniteStorage().getAsBoolean();
        boolean migrating = config.migratingToInfinite().getAsBoolean();
        List<TypeLine> types = new ArrayList<>();
        java.util.Map<Integer, Integer> orders = new java.util.HashMap<>();
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
            var display = ECOStorageChannelDisplays.resolve(line.resourceId());
            if (display != null) {
                orders.put(line.registryIndex(), display.order());
                swatch = StoragePanelTextures.FALLBACK_SWATCH;
            }
            types.add(new TypeLine(line.registryIndex(), display == null ? line.displayName().get() : display.name(),
                display == null ? color : display.color(),
                usedTypes, totalTypes, usedBytes, totalBytes,
                infinite || migrating || totalBytes < 0 ? line.infiniteBytesText().get() : HostText.ae2Amount(usedBytes),
                swatch, line.exactUsedBytes().get().max(BigInteger.ZERO), display == null ? null : display.channel(),
                display == null ? null : display.icon(), display == null ? null : display.swatch()));
        }
        types.sort(java.util.Comparator.comparingInt(type -> orders.getOrDefault(type.id(), 0)));
        return new StoragePanelState(config.title().get(), config.tier().getAsInt(), config.formed().getAsBoolean(),
            infinite, migrating, config.storedEnergy().getAsLong(), config.maxEnergy().getAsLong(),
            config.energyConsumePerTick().getAsLong(), config.totalUsedBytesText().get(), usage(all), all.size(), offset,
            cellPage(all, offset, pageSize), List.copyOf(types),
            config.domainId().get(), config.missingMembers().getAsInt(),
            !config.infiniteComponentInventory().getStackInSlot(0).isEmpty(), totalCapacity(all),
            totalUsedBytes(all, types, infinite || migrating),
            migrating ? config.migrationProgress().getAsInt() : 0,
            migrating ? config.migrationBlocked().get() : Component.empty());
    }

    static int pageOffset(int requested, int total, int pageSize) {
        if (total <= 0 || pageSize <= 0) return 0;
        int last = (total - 1) / pageSize * pageSize;
        return Math.clamp(requested, 0, last) / pageSize * pageSize;
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
        return Math.clamp(new BigDecimal(used).divide(new BigDecimal(total), MathContext.DECIMAL64).doubleValue(), 0, 1);
    }

    private static BigInteger totalCapacity(List<CellEntry> cells) {
        return cells.stream().filter(cell -> cell.totalBytes() != Long.MAX_VALUE)
            .map(cell -> BigInteger.valueOf(Math.max(0, cell.totalBytes())))
            .reduce(BigInteger.ZERO, BigInteger::add);
    }

    private static BigInteger totalUsedBytes(List<CellEntry> cells, List<TypeLine> types, boolean unbounded) {
        if (unbounded) {
            return types.stream().map(TypeLine::exactUsedBytes).reduce(BigInteger.ZERO, BigInteger::add);
        }
        // Use the same finite cells as totalCapacity and usage, before selecting the visible page.
        return cells.stream().filter(cell -> cell.totalBytes() != Long.MAX_VALUE)
            .map(CellEntry::exactUsedBytes).reduce(BigInteger.ZERO, BigInteger::add);
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
        tag.putString("exactUsedBytes", exactUsedBytes.toString());
        tag.putInt("cellCount", cellCount); tag.putInt("cellOffset", cellOffset);
        tag.putString("domain", domainId); tag.putInt("missing", missingMembers);
        tag.putBoolean("component", componentPresent);
        tag.putInt("migrationProgress", migrationProgress);
        if (!Component.empty().equals(migrationBlocked)) putComponent(tag, "migrationBlocked", migrationBlocked);
        ListTag entries = new ListTag();
        for (CellEntry cell : cells) {
            CompoundTag entry = new CompoundTag();
            entry.putInt("id", cell.typeId()); entry.putInt("tier", cell.tier()); entry.putInt("kind", cell.kind());
            if (cell.panelTierName() != null) putComponent(entry, "tierName", cell.panelTierName());
            entry.putLong("ut", cell.usedTypes()); entry.putLong("tt", cell.totalTypes());
            entry.putLong("ub", cell.usedBytes()); entry.putLong("tb", cell.totalBytes());
            entry.putString("exactBytes", cell.exactUsedBytes().toString());
            entry.putBoolean("infinite", cell.infinite());
            if (cell.position() != null) entry.putLong("position", cell.position().asLong());
            if (cell.interactionId() != null) entry.store("interactionId", net.minecraft.core.UUIDUtil.CODEC, cell.interactionId());
            if (!cell.stack().isEmpty()) entry.put("stack", cn.dancingsnow.neoecoae.util.LegacyNbt.saveItem(registries, cell.stack()));
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
            if (type.channel() != null) entry.putString("channel", type.channel().toString());
            if (type.icon() != null) entry.putString("icon", type.icon().toString());
            if (type.swatchTexture() != null) entry.putString("swatchTexture", type.swatchTexture().toString());
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
        for (Tag valueCell : tag.getListOrEmpty("cells")) {
            CompoundTag cell = (CompoundTag) valueCell;
            cells.add(new CellEntry(cell.getIntOr("id", 0), cell.getIntOr("tier", 0), cell.getIntOr("kind", 0),
                cell.getLongOr("ut", 0L), cell.getLongOr("tt", 0L), cell.getLongOr("ub", 0L), cell.getLongOr("tb", 0L), cell.getBooleanOr("infinite", false),
                CellState.values()[Math.clamp(cell.getIntOr("state", 0), 0, CellState.values().length - 1)], cell.getBooleanOr("online", false),
                cell.contains("position") ? BlockPos.of(cell.getLongOr("position", 0L)) : null,
                cn.dancingsnow.neoecoae.util.LegacyNbt.parseItem(registries, cell.getCompoundOrEmpty("stack")),
                cell.contains("locked") ? component(cell, "locked") : null,
                cell.read("interactionId", net.minecraft.core.UUIDUtil.CODEC).isPresent() ? cell.read("interactionId", net.minecraft.core.UUIDUtil.CODEC).orElseThrow() : null,
                cell.contains("tierName") ? component(cell, "tierName") : null,
                new BigInteger(cell.getStringOr("exactBytes", ""))));
        }
        List<TypeLine> types = new ArrayList<>();
        for (Tag valueType : tag.getListOrEmpty("types")) {
            CompoundTag type = (CompoundTag) valueType;
            types.add(new TypeLine(type.getIntOr("id", 0), component(type, "name"), type.getIntOr("color", 0),
                type.getLongOr("ut", 0L), type.getLongOr("tt", 0L), type.getLongOr("ub", 0L), type.getLongOr("tb", 0L), type.getStringOr("text", ""),
                type.contains("swatch") ? type.getIntOr("swatch", 0) : StoragePanelTextures.FALLBACK_SWATCH,
                new BigInteger(type.getStringOr("exactBytes", "")),
                type.contains("channel") ? net.minecraft.resources.Identifier.tryParse(type.getStringOr("channel", "")) : null,
                type.contains("icon") ? net.minecraft.resources.Identifier.tryParse(type.getStringOr("icon", "")) : null,
                type.contains("swatchTexture") ? net.minecraft.resources.Identifier.tryParse(type.getStringOr("swatchTexture", "")) : null));
        }
        return new StoragePanelState(component(tag, "title"), tag.getIntOr("tier", 0), tag.getBooleanOr("formed", false),
            tag.getBooleanOr("infinite", false), tag.getBooleanOr("migrating", false), tag.getLongOr("energy", 0L), tag.getLongOr("maxEnergy", 0L),
            tag.getLongOr("drain", 0L), tag.getStringOr("usedBytes", ""), tag.getDoubleOr("usage", 0.0), tag.getIntOr("cellCount", 0),
            tag.getIntOr("cellOffset", 0), List.copyOf(cells), List.copyOf(types), tag.getStringOr("domain", ""),
            tag.getIntOr("missing", 0), tag.getBooleanOr("component", false),
            new BigInteger(tag.getStringOr("totalBytes", "")), new BigInteger(tag.getStringOr("exactUsedBytes", "")),
            tag.getIntOr("migrationProgress", 0),
            tag.contains("migrationBlocked") ? component(tag, "migrationBlocked") : Component.empty());
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
