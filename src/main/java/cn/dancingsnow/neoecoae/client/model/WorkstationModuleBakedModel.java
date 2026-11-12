package cn.dancingsnow.neoecoae.client.model;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.api.workstation.ECOWorkstationModules;
import cn.dancingsnow.neoecoae.blocks.ECOIntegratedWorkingStation;
import cn.dancingsnow.neoecoae.blocks.entity.ECOLargeIntegratedWorkingStationBlockEntity;
import com.mojang.math.Quadrant;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockModelRotation;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.DelegateBlockStateModel;
import net.neoforged.neoforge.client.model.standalone.SimpleUnbakedStandaloneModel;
import net.neoforged.neoforge.client.model.standalone.StandaloneModelKey;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Custom module models use north-facing coordinates, like the built-in workstation models.
 * <p>
 * 26.1: module models are registered as standalone models (one per horizontal facing, rotated at bake time),
 * and the formed workstation block state models are wrapped so that the installed module's model is emitted
 * instead of the default geometry.
 */
@EventBusSubscriber(modid = NeoECOAE.MOD_ID, value = Dist.CLIENT)
public final class WorkstationModuleBakedModel extends DelegateBlockStateModel {
    public interface PreviewAppearance {
        @Nullable Identifier previewModule(BlockPos pos);
    }

    private static final Identifier WORKSTATION_ID = NeoECOAE.id("integrated_working_station");
    private static final Quadrant[] TURNS = {Quadrant.R0, Quadrant.R90, Quadrant.R180, Quadrant.R270};
    /** model id -> keys indexed by clockwise quarter turns from north. */
    private static final Map<Identifier, List<StandaloneModelKey<BlockStateModelPart>>> KEYS = new HashMap<>();

    public WorkstationModuleBakedModel(BlockStateModel original) {
        super(original);
    }

    @SubscribeEvent
    public static void registerStandalone(ModelEvent.RegisterStandalone event) {
        KEYS.clear();
        for (var module : ECOWorkstationModules.registered()) {
            register(event, module.idleModel());
            register(event, module.workingModel());
        }
    }

    private static void register(ModelEvent.RegisterStandalone event, @Nullable Identifier id) {
        if (id == null || KEYS.containsKey(id)) return;
        var keys = new java.util.ArrayList<StandaloneModelKey<BlockStateModelPart>>(4);
        for (int turns = 0; turns < 4; turns++) {
            int t = turns;
            StandaloneModelKey<BlockStateModelPart> key = new StandaloneModelKey<>(() -> id + "#turns=" + t);
            var rotation = BlockModelRotation.get(Quadrant.fromXYZAngles(Quadrant.R0, TURNS[t], Quadrant.R0));
            event.register(key, SimpleUnbakedStandaloneModel.simpleModelWrapper(id, rotation));
            keys.add(key);
        }
        KEYS.put(id, List.copyOf(keys));
    }

    @SubscribeEvent
    public static void wrapModels(ModelEvent.ModifyBakingResult event) {
        event.getBakingResult().blockStateModels().replaceAll((state, model) ->
            isFormedWorkstation(state) && !(model instanceof WorkstationModuleBakedModel)
                ? new WorkstationModuleBakedModel(model) : model);
    }

    private static boolean isFormedWorkstation(BlockState state) {
        return WORKSTATION_ID.equals(BuiltInRegistries.BLOCK.getKey(state.getBlock()))
            && state.getOptionalValue(ECOIntegratedWorkingStation.FORMED).orElse(false);
    }

    @Override
    public void collectParts(BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random,
                             List<BlockStateModelPart> parts) {
        BlockStateModelPart selected = selected(level, pos, state);
        if (selected == null) {
            super.collectParts(level, pos, state, random, parts);
        } else {
            parts.add(selected);
        }
    }

    @Nullable
    private static BlockStateModelPart selected(BlockAndTintGetter level, BlockPos pos, BlockState state) {
        Identifier appearance = level instanceof PreviewAppearance preview ? preview.previewModule(pos) : null;
        if (appearance == null && level.getBlockEntity(pos) instanceof ECOLargeIntegratedWorkingStationBlockEntity host) {
            appearance = host.getModuleAppearanceId();
        }
        var module = appearance == null ? null : ECOWorkstationModules.find(appearance);
        if (module == null) return null;
        boolean working = state.getOptionalValue(ECOIntegratedWorkingStation.WORKING).orElse(false);
        Identifier id = working && module.workingModel() != null ? module.workingModel() : module.idleModel();
        if (id == null) return null;
        var keys = KEYS.get(id);
        if (keys == null) return null;
        Direction facing = state.getOptionalValue(BlockStateProperties.HORIZONTAL_FACING).orElse(Direction.NORTH);
        int turns = switch (facing) {
            case EAST -> 1;
            case SOUTH -> 2;
            case WEST -> 3;
            default -> 0;
        };
        return Minecraft.getInstance().getModelManager().getStandaloneModel(keys.get(turns));
    }
}
