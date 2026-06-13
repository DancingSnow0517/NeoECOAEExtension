package cn.dancingsnow.neoecoae.client.all;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.client.model.ECODriveModel;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

public class NEBuiltinModels {
    private static final Map<Identifier, Supplier<UnbakedModel>> registry = new HashMap<>();

    public static final Identifier ECO_DRIVE = register(NeoECOAE.id("block/builtin/eco_drive"), ECODriveModel::new);

    public static Identifier register(Identifier id, Supplier<UnbakedModel> factory) {
        registry.put(id, factory);
        return id;
    }

    public static UnbakedModel getUnbakedModel(Identifier id) {
        if (!id.getNamespace().equals(NeoECOAE.MOD_ID)) return null;
        Supplier<UnbakedModel> factory = registry.get(id);
        if (factory != null) {
            return factory.get();
        }
        return null;
    }
}
