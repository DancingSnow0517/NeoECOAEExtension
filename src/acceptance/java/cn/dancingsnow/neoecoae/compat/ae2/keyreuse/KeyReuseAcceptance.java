package cn.dancingsnow.neoecoae.compat.ae2.keyreuse;

import cn.dancingsnow.neoecoae.compat.ae2.keyreuse.ECOFluxKeys;

import cn.dancingsnow.neoecoae.compat.ae2.keyreuse.ECOChemicalKeys;

import cn.dancingsnow.neoecoae.compat.ae2.keyreuse.ECOKeyFactory;

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
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.fluids.FluidStack;

/** Executed in the real transformed launcher; excluded from every published mod JAR. */
@EventBusSubscriber(modid = "neoecoae")
public final class KeyReuseAcceptance {
    private static net.minecraft.world.item.Item capabilityItem;
    @EventBusSubscriber(modid = "neoecoae", bus = EventBusSubscriber.Bus.MOD)
    public static final class TestRegistration {
        @SubscribeEvent
        public static void register(net.minecraftforge.registries.RegisterEvent event) {
            event.register(net.minecraftforge.registries.ForgeRegistries.Keys.ITEMS, helper -> {
                capabilityItem = new CapabilityItem();
                helper.register(new net.minecraft.resources.ResourceLocation("neoecoae", "key_reuse_capability_test"), capabilityItem);
            });
        }
    }
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
        check(AEFluidKey.of(fluid) != AEFluidKey.of(fluid), "upstream fluid factory remains independent with ECO reuse enabled");
        KeyReuse.configure(false);
        AEItemKey oldItem = ECOKeyFactory.item(input);
        AEFluidKey oldFluid = ECOKeyFactory.fluid(fluid);
        CompoundTag oldItemTag = oldItem.toTagGeneric();
        CompoundTag oldFluidTag = oldFluid.toTagGeneric();
        ItemStack cellStack = new ItemStack(NEItems.ECO_ITEM_CELL_16M.get());
        ECOStorageCell oldCell = new ECOStorageCell(cellStack, null);
        check(oldCell.insert(oldItem, 4096, Actionable.MODULATE, IActionSource.empty()) == 4096, "old finite cell inserted");
        CompoundTag serializedCell = (CompoundTag) cellStack.save(new CompoundTag());
        ECOInfiniteStorageData data = ECOInfiniteStorageData.createNew();
        SavedDataInfiniteStorageEngine oldEngine = new SavedDataInfiniteStorageEngine(data);
        BigInteger huge = BigInteger.TEN.pow(40);
        oldEngine.insert(oldItem, huge, Actionable.MODULATE);
        oldEngine.insert(oldFluid, BigInteger.ONE.shiftLeft(100), Actionable.MODULATE);
        UUID receipt = UUID.fromString("c5348bb2-50fa-4d72-868c-1fc7cf663eee");
        oldEngine.insertOnce(receipt, oldItem, 17);
        CompoundTag oldSnapshot = data.save(new CompoundTag());
        Path snapshot = Path.of("key-reuse-old-inventory.nbt");
        NbtIo.writeCompressed(oldSnapshot, snapshot.toFile());

        KeyReuse.configure(true);
        AEItemKey item = ECOKeyFactory.item(input);
        AEFluidKey water = ECOKeyFactory.fluid(fluid);
        check(item != oldItem && item.equals(oldItem) && item.hashCode() == oldItem.hashCode(), "old and reused items retain equals/hashCode");
        check(water != oldFluid && water.equals(oldFluid), "old and reused fluids retain equals");
        check(item == ECOKeyFactory.item(input) && item == ECOKeyFactory.item(input.copyWithCount(3)), "item factories reuse before copying");
        check(water == ECOKeyFactory.fluid(fluid) && water == ECOKeyFactory.fluid(Fluids.WATER), "fluid factories reuse");
        check(item.getReadOnlyStack().getCount() == 1 && input.getCount() == 64, "representative count is one and source is untouched");
        check(item.toTagGeneric().equals(oldItemTag) && water.toTagGeneric().equals(oldFluidTag), "encoded key fields are unchanged");
        check(ECOKeyFactory.fromTagGeneric( oldItemTag) == item && ECOKeyFactory.fromTagGeneric( oldFluidTag) == water, "old generic key codecs reuse");
        check(ECOKeyFactory.readTag(GenericStack.writeTag( new GenericStack(oldItem, 987))).what() == item, "direct GenericStack map codec reuses");
        FriendlyByteBuf packet = new FriendlyByteBuf(Unpooled.buffer());
        try {
            oldItem.writeToPacket(packet);
            check(ECOKeyFactory.canonicalize(AEItemKey.fromPacket(packet)) == item, "item packet decoder reuses");
            packet.clear();
            oldFluid.writeToPacket(packet);
            check(ECOKeyFactory.canonicalize(AEFluidKey.fromPacket(packet)) == water, "fluid packet decoder reuses");
        } finally { packet.release(); }
        ItemStack named = input.copy();
        named.setHoverName(Component.literal("variant"));
        check(ECOKeyFactory.item(named) != ECOKeyFactory.item(named) && !item.equals(ECOKeyFactory.item(named)), "component variants retain the upstream path");
        ItemStack emptyTag = input.copy();
        emptyTag.setTag(new CompoundTag());
        check(!item.equals(ECOKeyFactory.item(emptyTag)) && ECOKeyFactory.item(emptyTag) != ECOKeyFactory.item(emptyTag), "null and empty NBT remain distinct and empty compounds are not pooled");
        check(ECOKeyFactory.item(ItemStack.EMPTY) == null && ECOKeyFactory.fluid(FluidStack.EMPTY) == null, "empty factories retain null semantics");
        KeyReuse.invalidate();
        var executor = Executors.newFixedThreadPool(8);
        try {
            var calls = new ArrayList<java.util.concurrent.Callable<AEItemKey>>();
            for (int i = 0; i < 64; i++) calls.add(() -> ECOKeyFactory.item(input));
            var values = executor.invokeAll(calls);
            Object shared = values.get(0).get();
            for (var value : values) check(value.get() == shared, "concurrent cold item publication");
        }
        finally { executor.shutdownNow(); }
        AEItemKey refreshed = ECOKeyFactory.item(input);
        check(item != refreshed && item.equals(refreshed), "invalidation preserves old key equality");
        ECOStorageCell reloadedCell = new ECOStorageCell(ItemStack.of(serializedCell), null);
        check(reloadedCell.extract(refreshed, 4096, Actionable.MODULATE, IActionSource.empty()) == 4096, "old finite inventory reload and extraction conserve resources");
        ECOInfiniteStorageData loaded = ECOInfiniteStorageData.load(NbtIo.readCompressed(snapshot.toFile()));
        SavedDataInfiniteStorageEngine engine = new SavedDataInfiniteStorageEngine(loaded);
        check(engine.getAmount(refreshed).toBigInteger().equals(huge.add(BigInteger.valueOf(17))), "old 10^40 item quantity survives real key codec and disk reload");
        check(engine.getAmount(ECOKeyFactory.fluid(fluid)).toBigInteger().equals(BigInteger.ONE.shiftLeft(100)), "old U126 fluid quantity survives reload");
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
        ItemStack capable = new ItemStack(capabilityItem);
        var energy = capable.getCapability(net.minecraftforge.common.capabilities.ForgeCapabilities.ENERGY).orElseThrow(AssertionError::new);
        AEItemKey before = ECOKeyFactory.item(capable);
        energy.receiveEnergy(23, false);
        AEItemKey after = ECOKeyFactory.item(capable);
        check(!before.equals(after), "Forge capability contents remain part of key identity");
        check(after != ECOKeyFactory.item(capable) && after.equals(ECOKeyFactory.item(capable)), "capability-bearing stacks retain the uncached path");
    }

    private static final class CapabilityItem extends net.minecraft.world.item.Item {
        CapabilityItem() { super(new net.minecraft.world.item.Item.Properties()); }

        @Override public net.minecraftforge.common.capabilities.ICapabilityProvider initCapabilities(ItemStack stack, CompoundTag nbt) {
            return new net.minecraftforge.common.capabilities.ICapabilitySerializable<CompoundTag>() {
                private final net.minecraftforge.energy.EnergyStorage storage = new net.minecraftforge.energy.EnergyStorage(1000);
                private final net.minecraftforge.common.util.LazyOptional<net.minecraftforge.energy.IEnergyStorage> optional =
                    net.minecraftforge.common.util.LazyOptional.of(() -> storage);
                @Override public <T> net.minecraftforge.common.util.LazyOptional<T> getCapability(
                    net.minecraftforge.common.capabilities.Capability<T> capability, net.minecraft.core.Direction direction) {
                    return capability == net.minecraftforge.common.capabilities.ForgeCapabilities.ENERGY
                        ? optional.cast() : net.minecraftforge.common.util.LazyOptional.empty();
                }
                @Override public CompoundTag serializeNBT() {
                    CompoundTag tag = new CompoundTag();
                    tag.putInt("energy", storage.getEnergyStored());
                    return tag;
                }
                @Override public void deserializeNBT(CompoundTag tag) { storage.receiveEnergy(tag.getInt("energy"), false); }
            };
        }
    }

    private static void checkOptionalChannels(net.minecraft.core.RegistryAccess registries) {
        var type = com.glodblock.github.appflux.common.me.key.type.EnergyType.values()[0];
        var flux = ECOFluxKeys.of(type);
        check(com.glodblock.github.appflux.common.me.key.FluxKey.of(type)
            != com.glodblock.github.appflux.common.me.key.FluxKey.of(type), "upstream Flux factory remains independent");
        check(flux == ECOFluxKeys.of(type), "energy factory reuses");
        check(ECOKeyFactory.fromTagGeneric( flux.toTagGeneric()) == flux, "energy codec reuses constructor-reference path");
        var chemical = mekanism.common.registries.MekanismGases.OXYGEN.getStack(123);
        var key = ECOChemicalKeys.of(chemical);
        check(me.ramidzkh.mekae2.ae2.MekanismKey.of(chemical)
            != me.ramidzkh.mekae2.ae2.MekanismKey.of(chemical), "upstream Chemical factory remains independent");
        check(key == ECOChemicalKeys.of(chemical), "chemical factory reuses");
        key.getStack().setAmount(88);
        check(key.getStack().getAmount() == 1 && chemical.getAmount() == 123, "shared chemical stack is defensively copied");
        check(ECOKeyFactory.fromTagGeneric( key.toTagGeneric()) == key, "chemical codec reuses");
        FriendlyByteBuf packet = new FriendlyByteBuf(Unpooled.buffer());
        try {
            AEKey.writeKey(packet, key);
            check(ECOKeyFactory.readKey(packet) == key, "chemical packet reuses");
        } finally { packet.release(); }
    }
}
