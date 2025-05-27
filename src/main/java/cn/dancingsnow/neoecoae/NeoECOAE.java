package cn.dancingsnow.neoecoae;


import com.tterrag.registrate.Registrate;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

@Mod(NeoECOAE.MOD_ID)
public class NeoECOAE {
    public static final String MOD_ID = "neoecoae";

    public static final Registrate REGISTRATE = Registrate.create(MOD_ID);

    public NeoECOAE(IEventBus modBus, ModContainer modContainer) {

    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
