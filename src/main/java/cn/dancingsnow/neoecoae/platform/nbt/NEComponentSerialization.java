package cn.dancingsnow.neoecoae.platform.nbt;
import com.mojang.serialization.*;
import net.minecraft.network.chat.Component;
/** Preserves structured chat data through the target version's native component serializer. */
public final class NEComponentSerialization {
    public static final Codec<Component> CODEC=Codec.PASSTHROUGH.comapFlatMap(value->{
        try {var component=Component.Serializer.fromJson(value.convert(JsonOps.INSTANCE).getValue());
            return component==null?DataResult.error(()->"Null chat component"):DataResult.success(component);
        } catch(RuntimeException error){return DataResult.error(()->error.getMessage()==null?"Invalid chat component":error.getMessage());}
    },component->new Dynamic<>(JsonOps.INSTANCE,Component.Serializer.toJsonTree(component)));
    private NEComponentSerialization(){}
}
