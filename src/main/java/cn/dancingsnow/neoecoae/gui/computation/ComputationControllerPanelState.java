package cn.dancingsnow.neoecoae.gui.computation;

import cn.dancingsnow.neoecoae.platform.JdkCompat;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Everything the computation controller artwork needs to draw itself.
 *
 * <p>Slot data is stored per <em>cell</em> (screen position, left to right), so mirroring is resolved on the
 * server and the client never needs to know about it.
 */
public record ComputationControllerPanelState(
    boolean formed,
    int hostTier,
    int cells,
    int upperCores,
    int coreTiers,
    int lowerCores,
    List<Member> upperMembers,
    List<Member> coreMembers,
    List<Member> lowerMembers
) {
    public static final int SLOT_COUNT = 11;
    public static final ComputationControllerPanelState UNFORMED =
        new ComputationControllerPanelState(false, 0, 0, 0, 0, 0);

    private static final int TIER_MASK = 3;
    private static final String FORMED = "formed";
    private static final String HOST_TIER = "host";
    private static final String CELLS = "cells";
    private static final String UPPER = "up";
    private static final String CORE = "core";
    private static final String LOWER = "low";

    /** Explicit presence allows a real member to have tier zero. Texture locations are server-authored. */
    public record Member(boolean present, int tier, @Nullable ResourceLocation texture) {
        public static final Member ABSENT = new Member(false, 0, null);
    }

    /** Legacy callers use zero as the absence sentinel for the upper/lower rows. */
    public ComputationControllerPanelState(boolean formed, int hostTier, int cells,
        int upperCores, int coreTiers, int lowerCores) {
        this(formed, hostTier, cells, upperCores, coreTiers, lowerCores,
            legacyMembers(cells, upperCores, false), legacyMembers(cells, coreTiers, true),
            legacyMembers(cells, lowerCores, false));
    }

    public ComputationControllerPanelState {
        upperMembers = List.copyOf(upperMembers);
        coreMembers = List.copyOf(coreMembers);
        lowerMembers = List.copyOf(lowerMembers);
        hostTier = JdkCompat.clamp(hostTier, 0, 3);
    }

    public boolean hasCell(int cell) {
        return cell >= 0 && cell < SLOT_COUNT && (cells >> cell & 1) != 0;
    }

    public int upperTier(int cell) {
        return tierAt(upperCores, cell);
    }

    public int coreTier(int cell) {
        return tierAt(coreTiers, cell);
    }

    public int lowerTier(int cell) {
        return tierAt(lowerCores, cell);
    }

    /** Index {@code 0} is the threading core next to the controller. Slots past {@link #SLOT_COUNT} are not drawn. */
    public static ComputationControllerPanelState of(
        int hostTier,
        List<Integer> upperNearestFirst,
        List<Integer> coreNearestFirst,
        List<Integer> lowerNearestFirst,
        boolean mirrored
    ) {
        int upper = 0;
        int core = 0;
        int lower = 0;
        int cells = 0;
        int visible = Math.min(coreNearestFirst.size(), SLOT_COUNT);
        for (int slot = 0; slot < visible; slot++) {
            int cell = cellOf(slot, mirrored);
            cells |= 1 << cell;
            upper |= tierBits(upperNearestFirst, slot) << cell * 2;
            core |= tierBits(coreNearestFirst, slot) << cell * 2;
            lower |= tierBits(lowerNearestFirst, slot) << cell * 2;
        }
        return new ComputationControllerPanelState(true, hostTier, cells, upper, core, lower);
    }

    public Member upperMember(int cell) { return memberAt(upperMembers, cell); }
    public Member coreMember(int cell) { return memberAt(coreMembers, cell); }
    public Member lowerMember(int cell) { return memberAt(lowerMembers, cell); }

    /** Unlike of(), absent members are explicit and tier zero remains drawable. */
    public static ComputationControllerPanelState ofMembers(int hostTier, List<Member> upper,
        List<Member> core, List<Member> lower, boolean mirrored) {
        List<Member> up = new ArrayList<>(java.util.Collections.nCopies(SLOT_COUNT, Member.ABSENT));
        List<Member> mid = new ArrayList<>(up);
        List<Member> low = new ArrayList<>(up);
        int cells = 0, upperBits = 0, coreBits = 0, lowerBits = 0;
        for (int slot = 0; slot < Math.min(core.size(), SLOT_COUNT); slot++) {
            int cell = cellOf(slot, mirrored);
            Member center = memberAt(core, slot);
            if (!center.present()) continue;
            Member above = memberAt(upper, slot), below = memberAt(lower, slot);
            cells |= 1 << cell;
            up.set(cell, above); mid.set(cell, center); low.set(cell, below);
            upperBits |= JdkCompat.clamp(above.tier(), 0, 3) << cell * 2;
            coreBits |= JdkCompat.clamp(center.tier(), 0, 3) << cell * 2;
            lowerBits |= JdkCompat.clamp(below.tier(), 0, 3) << cell * 2;
        }
        return new ComputationControllerPanelState(true, hostTier, cells, upperBits, coreBits, lowerBits, up, mid, low);
    }

    private static Member memberAt(List<Member> members, int cell) {
        return cell < 0 || cell >= members.size() ? Member.ABSENT : members.get(cell);
    }

    private static List<Member> legacyMembers(int cells, int packed, boolean middle) {
        List<Member> members = new ArrayList<>(SLOT_COUNT);
        for (int cell = 0; cell < SLOT_COUNT; cell++) {
            int tier = tierAt(packed, cell);
            members.add(new Member((cells >> cell & 1) != 0 && (middle || tier > 0), tier, null));
        }
        return members;
    }

    public static int cellOf(int slot, boolean mirrored) {
        return mirrored ? SLOT_COUNT - 1 - slot : slot;
    }

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean(FORMED, formed);
        tag.putInt(HOST_TIER, hostTier);
        tag.putInt(CELLS, cells);
        tag.putInt(UPPER, upperCores);
        tag.putInt(CORE, coreTiers);
        tag.putInt(LOWER, lowerCores);
        putMembers(tag, "upperMembers", upperMembers);
        putMembers(tag, "coreMembers", coreMembers);
        putMembers(tag, "lowerMembers", lowerMembers);
        return tag;
    }

    private static void putMembers(CompoundTag tag, String name, List<Member> members) {
        ListTag list = new ListTag();
        for (Member member : members) {
            CompoundTag entry = new CompoundTag();
            entry.putBoolean("present", member.present());
            entry.putInt("tier", member.tier());
            if (member.texture() != null) entry.putString("texture", member.texture().toString());
            list.add(entry);
        }
        tag.put(name, list);
    }

    private static List<Member> readMembers(CompoundTag tag, String name, int packed, boolean middle) {
        if (!tag.contains(name, Tag.TAG_LIST)) return legacyMembers(tag.getInt(CELLS), packed, middle);
        ListTag list = tag.getList(name, Tag.TAG_COMPOUND);
        List<Member> members = new ArrayList<>(SLOT_COUNT);
        for (int cell = 0; cell < Math.min(SLOT_COUNT, list.size()); cell++) {
            CompoundTag entry = list.getCompound(cell);
            members.add(new Member(entry.getBoolean("present"), entry.getInt("tier"),
                entry.getString("texture").isEmpty() ? null : ResourceLocation.tryParse(entry.getString("texture"))));
        }
        return members;
    }

    public static ComputationControllerPanelState fromTag(CompoundTag tag) {
        if (tag == null || tag.isEmpty()) {
            return UNFORMED;
        }
        return new ComputationControllerPanelState(
            tag.getBoolean(FORMED),
            tag.getInt(HOST_TIER),
            tag.getInt(CELLS),
            tag.getInt(UPPER),
            tag.getInt(CORE),
            tag.getInt(LOWER),
            readMembers(tag, "upperMembers", tag.getInt(UPPER), false),
            readMembers(tag, "coreMembers", tag.getInt(CORE), true),
            readMembers(tag, "lowerMembers", tag.getInt(LOWER), false)
        );
    }

    private static int tierAt(int packed, int cell) {
        return cell < 0 || cell >= SLOT_COUNT ? 0 : packed >> cell * 2 & TIER_MASK;
    }

    private static int tierBits(List<Integer> tiers, int slot) {
        return slot < tiers.size() ? JdkCompat.clamp(tiers.get(slot), 0, 3) : 0;
    }
}
