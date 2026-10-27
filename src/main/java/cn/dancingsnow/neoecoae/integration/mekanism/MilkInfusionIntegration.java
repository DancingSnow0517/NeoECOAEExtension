package cn.dancingsnow.neoecoae.integration.mekanism;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.platform.ForgeBlockCapabilities;
import mekanism.api.chemical.infuse.InfuseType;
import mekanism.common.lib.transmitter.TransmissionType;
import mekanism.common.registration.impl.InfuseTypeDeferredRegister;
import mekanism.common.registration.impl.InfuseTypeRegistryObject;
import mekanism.common.registries.MekanismTileEntityTypes;
import mekanism.common.tile.component.TileComponentConfig;
import net.minecraft.core.Direction;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;

/** Loaded only when both Mekanism and Useless Stretcher are installed. */
public final class MilkInfusionIntegration {
    private static final InfuseTypeDeferredRegister INFUSE_TYPES = new InfuseTypeDeferredRegister(NeoECOAE.MOD_ID);
    private static final InfuseTypeRegistryObject<InfuseType> MILK = INFUSE_TYPES.register("milk", 0xFFF8E7);

    private MilkInfusionIntegration() {}

    public static void register(IEventBus bus) {
        ForgeMod.enableMilkFluid();
        INFUSE_TYPES.register(bus);
        bus.addListener(MilkInfusionIntegration::setup);
    }

    private static void setup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> ForgeBlockCapabilities.register(ForgeCapabilities.FLUID_HANDLER,
            MekanismTileEntityTypes.METALLURGIC_INFUSER.get(), (tile, side) -> {
                if (!allowsInput(tile.getConfig(), side)) return null;
                return new MilkInfusionFluidHandler(tile.infusionTank, MILK.get(), ForgeMod.MILK.get(),
                    () -> !tile.isRemoved() && tile.getLevel() != null && !tile.getLevel().isClientSide
                        && allowsInput(tile.getConfig(), side),
                    tile::markForSave);
            }));
    }

    static boolean allowsInput(TileComponentConfig config, Direction side) {
        if (side == null) return true;
        var slot = config.getSlotInfo(TransmissionType.INFUSION, side);
        return slot != null && slot.canInput();
    }
}
