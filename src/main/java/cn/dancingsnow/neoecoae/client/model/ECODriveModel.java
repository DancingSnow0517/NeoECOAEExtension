package cn.dancingsnow.neoecoae.client.model;

import appeng.client.render.BasicUnbakedModel;
import cn.dancingsnow.neoecoae.NeoECOAE;
import com.google.common.base.Preconditions;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.function.Function;

public class ECODriveModel implements BasicUnbakedModel {
    public static final ResourceLocation DRIVE_EMPTY = NeoECOAE.id("block/eco_drive_empty");
    public static final ResourceLocation DRIVE_FULL = NeoECOAE.id("block/eco_drive_full");

    @Override
    public @Nullable BakedModel bake(
        ModelBaker modelBaker,
        Function<Material, TextureAtlasSprite> function,
        ModelState modelState
    ) {
        BakedModel driveEmptyModel = modelBaker.bake(DRIVE_EMPTY, modelState, function);
        BakedModel driveFullModel = modelBaker.bake(DRIVE_FULL, modelState, function);
        Preconditions.checkNotNull(driveEmptyModel);
        Preconditions.checkNotNull(driveFullModel);
        return new ECODriveBakedModel(
            driveEmptyModel,
            driveFullModel
        );
    }
}
