package cn.dancingsnow.neoecoae.data.tag;

import cn.dancingsnow.neoecoae.all.NETags;
import com.tterrag.registrate.providers.RegistrateItemTagsProvider;
import net.minecraft.world.item.Items;

public class NETagGenerator {

    public static void itemTag(RegistrateItemTagsProvider provider) {
        provider.addTag(NETags.Items.CRYSTAL_INGOT_BASE)
            .add(Items.DIAMOND)
            .add(Items.EMERALD);
    }
}
