package cn.dancingsnow.neoecoae.data.tag;

import cn.dancingsnow.neoecoae.all.NEItems;
import cn.dancingsnow.neoecoae.all.NETags;
import com.tterrag.registrate.providers.RegistrateItemTagsProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;

public class NETagGenerator {
    private static final ResourceLocation GTL_INFINITE_CELL_COMPONENT =
            ResourceLocation.fromNamespaceAndPath("gtlcore", "infinite_cell_component");
    private static final ResourceLocation GTO_INFINITE_CELL_COMPONENT =
            ResourceLocation.fromNamespaceAndPath("gtocore", "infinite_cell_component");

    public static void itemTag(RegistrateItemTagsProvider provider) {
        provider.addTag(NETags.Items.CRYSTAL_INGOT_BASE).add(Items.DIAMOND).add(Items.EMERALD);

        provider.addTag(NETags.Items.SUPERCONDUCTIVE_INGOT_BASE)
                .add(Items.DIAMOND)
                .add(Items.EMERALD);

        provider.addTag(NETags.Items.INFINITE_CELL_COMPONENTS)
                .add(NEItems.ECO_INFINITE_CELL_COMPONENT.get())
                .addOptional(GTL_INFINITE_CELL_COMPONENT)
                .addOptional(GTO_INFINITE_CELL_COMPONENT);
    }
}
