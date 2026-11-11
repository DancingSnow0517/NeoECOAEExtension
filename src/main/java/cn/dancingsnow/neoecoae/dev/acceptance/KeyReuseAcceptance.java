package cn.dancingsnow.neoecoae.dev.acceptance;

import cn.dancingsnow.neoecoae.compat.ae2.keyreuse.ECOMatterKeys;

import cn.dancingsnow.neoecoae.compat.ae2.keyreuse.ECOFluxKeys;

import cn.dancingsnow.neoecoae.compat.ae2.keyreuse.ECOChemicalKeys;

import cn.dancingsnow.neoecoae.compat.ae2.keyreuse.ECOKeyFactory;
import cn.dancingsnow.neoecoae.compat.ae2.keyreuse.KeyReuse;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.*;
import cn.dancingsnow.neoecoae.all.NEItems;
import cn.dancingsnow.neoecoae.storage.ECOStorageCell;
import cn.dancingsnow.neoecoae.storage.infinite.*;
import io.netty.buffer.Unpooled;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.fluids.FluidStack;

/** Executed in the real transformed launcher; excluded from every published mod JAR. */
@EventBusSubscriber(modid = "neoecoae")
public final class KeyReuseAcceptance {
    private static final List<String> checks = new ArrayList<>();
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
        checks.add(message);
    }

    @SubscribeEvent
    public static void started(ServerStartedEvent event) {
        if (!Boolean.getBoolean("neoecoae.keyReuseAcceptance")) return;
        String outcome = "PASS";
        Throwable failure = null;
        try {
            run(event.getServer().registryAccess());
        } catch (Throwable problem) {
            outcome = "FAIL";
            failure = problem;
            problem.printStackTrace();
        }
        try {
            Path report = Path.of("key-reuse-acceptance-" + (event.getServer().isDedicatedServer() ? "server" : "client") + ".txt");
            Files.writeString(report, outcome + "\n" + String.join("\n", checks) + "\n" + (failure == null ? "" : failure.toString()));
            System.out.println("[KEY_REUSE_ACCEPTANCE] " + outcome + " " + checks.size() + " checks; " + report.toAbsolutePath());
        } catch (Exception problem) { throw new RuntimeException(problem); }
        if (event.getServer().isDedicatedServer()) event.getServer().halt(false);
    }

    private static void run(net.minecraft.core.RegistryAccess registries) throws Exception {
        check(KeyReuse.epoch() != null, "local config enabled reuse before acceptance overrides");
        ItemStack input = new ItemStack(Items.IRON_INGOT, 64);
        FluidStack fluid = new FluidStack(Fluids.WATER, 1000);
        check(AEItemKey.of(input) != AEItemKey.of(input), "upstream item factory remains independent with ECO reuse enabled");
        AEFluidKey firstFluid = AEFluidKey.of(fluid);
        check(firstFluid.equals(AEFluidKey.of(fluid)), "fluid factory preserves component equality");
        if ((Object) firstFluid instanceof cn.dancingsnow.neoecoae.compat.ae2.keyreuse.FluidKeyComponents)
            check(firstFluid == AEFluidKey.of(fluid), "checked default fluid factory reuses its owner slot");
        KeyReuse.configure(false);
        AEItemKey oldItem = ECOKeyFactory.item(input);
        AEFluidKey oldFluid = ECOKeyFactory.fluid(fluid);
        CompoundTag oldItemTag = oldItem.toTagGeneric(registries);
        CompoundTag oldFluidTag = oldFluid.toTagGeneric(registries);
        ItemStack cellStack = new ItemStack(NEItems.ECO_ITEM_CELL_16M.get());
        ECOStorageCell oldCell = new ECOStorageCell(cellStack, null);
        check(oldCell.insert(oldItem, 4096, Actionable.MODULATE, IActionSource.empty()) == 4096, "old finite cell inserted");
        CompoundTag serializedCell = (CompoundTag) cellStack.save(registries);
        ECOInfiniteStorageData data = ECOInfiniteStorageData.createNew();
        SavedDataInfiniteStorageEngine oldEngine = new SavedDataInfiniteStorageEngine(data);
        BigInteger huge = BigInteger.TEN.pow(40);
        oldEngine.insert(oldItem, huge, Actionable.MODULATE);
        oldEngine.insert(oldFluid, BigInteger.ONE.shiftLeft(100), Actionable.MODULATE);
        UUID receipt = UUID.fromString("c5348bb2-50fa-4d72-868c-1fc7cf663eee");
        oldEngine.insertOnce(receipt, oldItem, 17);
        CompoundTag oldSnapshot = data.save(new CompoundTag(), registries);
        Path snapshot = Path.of("key-reuse-old-inventory.nbt");
        NbtIo.writeCompressed(oldSnapshot, snapshot);

        KeyReuse.configure(true);
        AEItemKey item = ECOKeyFactory.item(input);
        AEFluidKey water = ECOKeyFactory.fluid(fluid);
        check(item != oldItem && item.equals(oldItem) && item.hashCode() == oldItem.hashCode(), "old and reused items retain equals/hashCode");
        check(water != oldFluid && water.equals(oldFluid), "old and reused fluids retain equals");
        check(item == ECOKeyFactory.item(input) && item == ECOKeyFactory.item(input.copyWithCount(3)), "item factories reuse before copying");
        check(water == ECOKeyFactory.fluid(fluid) && water == ECOKeyFactory.fluid(Fluids.WATER), "fluid factories reuse");
        check(item.getReadOnlyStack().getCount() == 1 && input.getCount() == 64, "representative count is one and source is untouched");
        check(item.toTagGeneric(registries).equals(oldItemTag) && water.toTagGeneric(registries).equals(oldFluidTag), "encoded key fields are unchanged");
        check(ECOKeyFactory.fromTagGeneric(registries, oldItemTag) == item && ECOKeyFactory.fromTagGeneric(registries, oldFluidTag) == water, "old generic key codecs reuse");
        check(ECOKeyFactory.readTag(registries, GenericStack.writeTag(registries, new GenericStack(oldItem, 987))).what() == item, "direct GenericStack map codec reuses");
        RegistryFriendlyByteBuf packet = new RegistryFriendlyByteBuf(Unpooled.buffer(), registries);
        try {
            oldItem.writeToPacket(packet);
            check(ECOKeyFactory.canonicalize(AEItemKey.fromPacket(packet)) == item, "item packet decoder reuses");
            packet.clear();
            oldFluid.writeToPacket(packet);
            check(ECOKeyFactory.canonicalize(AEFluidKey.fromPacket(packet)) == water, "fluid packet decoder reuses");
        } finally { packet.release(); }
        ItemStack named = input.copy();
        named.set(DataComponents.CUSTOM_NAME, Component.literal("variant"));
        check(ECOKeyFactory.item(named) != ECOKeyFactory.item(named) && !item.equals(ECOKeyFactory.item(named)), "component variants retain the upstream path");
        ItemStack removed = input.copy();
        removed.remove(DataComponents.MAX_STACK_SIZE);
        check(!removed.isComponentsPatchEmpty() && !item.equals(ECOKeyFactory.item(removed)), "removed default components are not pooled");
        check(ECOKeyFactory.item(ItemStack.EMPTY) == null && ECOKeyFactory.fluid(FluidStack.EMPTY) == null, "empty factories retain null semantics");
        KeyReuse.invalidate();
        try (var executor = Executors.newFixedThreadPool(8)) {
            var calls = new ArrayList<java.util.concurrent.Callable<AEItemKey>>();
            for (int i = 0; i < 64; i++) calls.add(() -> ECOKeyFactory.item(input));
            var values = executor.invokeAll(calls);
            Object shared = values.getFirst().get();
            for (var value : values) check(value.get() == shared, "concurrent cold item publication");
        }
        AEItemKey refreshed = ECOKeyFactory.item(input);
        check(item != refreshed && item.equals(refreshed), "invalidation preserves old key equality");
        ECOStorageCell reloadedCell = new ECOStorageCell(ItemStack.parse(registries, serializedCell).orElseThrow(), null);
        check(reloadedCell.extract(refreshed, 4096, Actionable.MODULATE, IActionSource.empty()) == 4096, "old finite inventory reload and extraction conserve resources");
        ECOInfiniteStorageData loaded = ECOInfiniteStorageData.load(NbtIo.readCompressed(snapshot, NbtAccounter.unlimitedHeap()), registries);
        SavedDataInfiniteStorageEngine engine = new SavedDataInfiniteStorageEngine(loaded);
        check(engine.getAmount(refreshed).toBigInteger().equals(huge.add(BigInteger.valueOf(17))), "old 10^40 item quantity survives real key codec and disk reload");
        check(engine.getAmount(ECOKeyFactory.fluid(fluid)).toBigInteger().equals(BigInteger.ONE.shiftLeft(100)), "legacy exact fluid quantity survives reload");
        check(engine.insertOnce(receipt, refreshed, 17) == 17
            && engine.getAmount(refreshed).toBigInteger().equals(huge.add(BigInteger.valueOf(17))),
            "migration receipt prevents duplicate import after reload");
        check(engine.extract(refreshed, 31, Actionable.MODULATE) == 31 && engine.getAmount(oldItem).toBigInteger().equals(huge.subtract(BigInteger.valueOf(14))), "old and new key extraction shares one exact ledger");
        KeyReuse.configure(false);
        AEItemKey disabled = ECOKeyFactory.item(input);
        check(disabled != ECOKeyFactory.item(input) && disabled.equals(refreshed), "disabled mode restores factory behavior and old access");
        KeyReuse.configure(true);
        check(ECOKeyFactory.item(input) != refreshed && ECOKeyFactory.item(input).equals(disabled), "config reenable creates a fresh valid generation");
        checkOptionalChannels(registries);
    }

    private static void checkOptionalChannels(net.minecraft.core.RegistryAccess registries) {
        var otherChannel = new OtherChannelMatterType();
        KeyReuse.configure(false);
        String originalRejection;
        try {
            ECOMatterKeys.of(otherChannel);
            throw new AssertionError("unregistered MatterType was accepted");
        } catch (IllegalArgumentException expected) { originalRejection = expected.getMessage(); }
        KeyReuse.configure(true);
        try {
            ECOMatterKeys.of(otherChannel);
            throw new AssertionError("unregistered MatterType was accepted with reuse enabled");
        } catch (IllegalArgumentException expected) {
            check(java.util.Objects.equals(originalRejection, expected.getMessage()), "unknown matter implementations retain upstream rejection");
        }
        var matterType = com.buuz135.replication.api.MatterType.EARTH;
        var matter = ECOMatterKeys.of(matterType);
        check(dev.lapis256.apprep.api.ae2.stack.MatterKey.Companion.of(matterType)
            != dev.lapis256.apprep.api.ae2.stack.MatterKey.Companion.of(matterType), "upstream Matter factory remains independent");
        check(matter == ECOMatterKeys.of(matterType), "matter type factory reuses");
        check(matter == ECOMatterKeys.of(new com.buuz135.replication.api.matter_fluid.MatterStack(matterType, 99)), "matter stack factory reuses");
        matter.getStack().setAmount(77);
        check(matter.getStack().getAmount() == 1, "shared matter stack is defensively copied");
        check(ECOKeyFactory.fromTagGeneric(registries, matter.toTagGeneric(registries)) == matter, "matter map codec reuses");
        RegistryFriendlyByteBuf matterPacket = new RegistryFriendlyByteBuf(Unpooled.buffer(), registries);
        try {
            matter.writeToPacket(matterPacket);
            check(ECOKeyFactory.canonicalize(dev.lapis256.apprep.api.ae2.stack.MatterKey.Companion.fromPacket(matterPacket)) == matter, "matter packet decoder reuses");
        } finally { matterPacket.release(); }
        var type = com.glodblock.github.appflux.common.me.key.type.EnergyType.values()[0];
        var flux = ECOFluxKeys.of(type);
        check(com.glodblock.github.appflux.common.me.key.FluxKey.of(type)
            != com.glodblock.github.appflux.common.me.key.FluxKey.of(type), "upstream Flux factory remains independent");
        check(flux == ECOFluxKeys.of(type), "energy factory reuses");
        check(ECOKeyFactory.fromTagGeneric(registries, flux.toTagGeneric(registries)) == flux, "energy codec reuses constructor-reference path");
        var chemical = new mekanism.api.chemical.ChemicalStack(mekanism.common.registries.MekanismChemicals.OXYGEN, 123);
        var key = ECOChemicalKeys.of(chemical);
        check(me.ramidzkh.mekae2.ae2.MekanismKey.of(chemical)
            != me.ramidzkh.mekae2.ae2.MekanismKey.of(chemical), "upstream Chemical factory remains independent");
        check(key == ECOChemicalKeys.of(chemical), "chemical factory reuses");
        key.getStack().setAmount(88);
        check(key.getStack().getAmount() == 1 && chemical.getAmount() == 123, "shared chemical stack is defensively copied");
        check(ECOKeyFactory.fromTagGeneric(registries, key.toTagGeneric(registries)) == key, "chemical codec reuses");
        RegistryFriendlyByteBuf packet = new RegistryFriendlyByteBuf(Unpooled.buffer(), registries);
        try {
            key.writeToPacket(packet);
            check(ECOKeyFactory.canonicalize(me.ramidzkh.mekae2.ae2.MekanismKey.fromPacket(packet)) == key, "chemical packet reuses");
        } finally { packet.release(); }
    }
    private static final class OtherChannelMatterType implements com.buuz135.replication.api.IMatterType {
        @Override public String getName() { return "key_reuse_acceptance"; }
        @Override public java.util.function.Supplier<float[]> getColor() { return () -> new float[] {1, 1, 1}; }
        @Override public int getMax() { return 1000; }
    }

}
