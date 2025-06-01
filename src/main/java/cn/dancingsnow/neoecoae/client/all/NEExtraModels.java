package cn.dancingsnow.neoecoae.client.all;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.all.NEItems;
import cn.dancingsnow.neoecoae.api.ECOComputationModels;
import cn.dancingsnow.neoecoae.api.ECOTier;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ModelEvent;

import java.util.HashSet;
import java.util.Set;

@EventBusSubscriber(value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public class NEExtraModels {
    private static final Set<ResourceLocation> MODELS = new HashSet<>();


    public static final ResourceLocation CABLE_L4 = id("block/compute/cable_l4");
    public static final ResourceLocation CABLE_L6 = id("block/compute/cable_l6");
    public static final ResourceLocation CABLE_L9 = id("block/compute/cable_l9");

    public static final ResourceLocation CABLE_L4_DISCONNECTED = id("block/compute/cable_l4_dis");
    public static final ResourceLocation CABLE_L6_DISCONNECTED = id("block/compute/cable_l6_dis");
    public static final ResourceLocation CABLE_L9_DISCONNECTED = id("block/compute/cable_l9_dis");

    public static final ResourceLocation COMPUTATION_CELL_L4 = id("block/compute/cell_l4");
    public static final ResourceLocation COMPUTATION_CELL_L6 = id("block/compute/cell_l6");
    public static final ResourceLocation COMPUTATION_CELL_L9 = id("block/compute/cell_l9");

    public static final ResourceLocation COMPUTATION_CELL_L4_FORMED = id("block/compute/cell_l4_formed");
    public static final ResourceLocation COMPUTATION_CELL_L6_FORMED = id("block/compute/cell_l6_formed");
    public static final ResourceLocation COMPUTATION_CELL_L9_FORMED = id("block/compute/cell_l9_formed");

    private static ResourceLocation id(String path) {
        ResourceLocation id = NeoECOAE.id(path);
        MODELS.add(id);
        return id;
    }

    @SubscribeEvent
    public static void onRegisterExtraModels(ModelEvent.RegisterAdditional event) {
        for (ResourceLocation model : MODELS) {
            event.register(ModelResourceLocation.standalone(model));
        }
    }

    public static void register() {
        ECOComputationModels.registerCableModel(ECOTier.L4, CABLE_L4_DISCONNECTED, CABLE_L4);
        ECOComputationModels.registerCableModel(ECOTier.L6, CABLE_L6_DISCONNECTED, CABLE_L6);
        ECOComputationModels.registerCableModel(ECOTier.L9, CABLE_L9_DISCONNECTED, CABLE_L9);

        ECOComputationModels.registerCellModel(
            NEItems.ECO_COMPUTATION_CELL_L4,
            COMPUTATION_CELL_L4,
            COMPUTATION_CELL_L4_FORMED
        );
        ECOComputationModels.registerCellModel(
            NEItems.ECO_COMPUTATION_CELL_L6,
            COMPUTATION_CELL_L6,
            COMPUTATION_CELL_L6_FORMED
        );
        ECOComputationModels.registerCellModel(
            NEItems.ECO_COMPUTATION_CELL_L9,
            COMPUTATION_CELL_L9,
            COMPUTATION_CELL_L9_FORMED
        );
    }
}
