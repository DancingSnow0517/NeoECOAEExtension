package cn.dancingsnow.neoecoae.data.tag;

import cn.dancingsnow.neoecoae.all.NETags;
import com.tterrag.registrate.providers.RegistrateItemTagsProvider;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Items;

public class NETagGenerator {

    public static void itemTag(RegistrateItemTagsProvider provider) {
        provider.rawBuilder(NETags.Items.ENERGIZED_CRYSTAL)
            .addOptionalElement(Identifier.parse("ae2cs:purified_energized_certus_quartz_crystal"));

        provider.rawBuilder(NETags.Items.ENERGIZED_FLUIX_CRYSTAL)
            .addOptionalElement(Identifier.parse("ae2cs:purified_energized_fluix_crystal"));

        provider.tag(NETags.Items.CRYSTAL_INGOT_BASE)
            .add(Items.DIAMOND)
            .add(Items.EMERALD);

        provider.tag(NETags.Items.SUPERCONDUCTIVE_INGOT_BASE)
            .add(Items.DIAMOND)
            .add(Items.EMERALD);
    }
}
