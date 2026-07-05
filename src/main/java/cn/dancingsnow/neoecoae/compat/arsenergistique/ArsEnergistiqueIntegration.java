package cn.dancingsnow.neoecoae.compat.arsenergistique;

import cn.dancingsnow.neoecoae.api.ECOCellModels;
import cn.dancingsnow.neoecoae.api.integration.Integration;
import cn.dancingsnow.neoecoae.compat.AbstractCellIntegration;
import java.util.List;

@Integration("arseng")
public class ArsEnergistiqueIntegration extends AbstractCellIntegration {
    public ArsEnergistiqueIntegration() {
        super(
                ArsEnergistiqueCompat::getSourceKeyType,
                1,
                NEArsEnergistiqueCellTypes::register,
                NEArsEnergistiqueItems::register,
                ECOSourceCellHandler.INSTANCE,
                List.of(
                        NEArsEnergistiqueItems.ECO_SOURCE_CELL_16M,
                        NEArsEnergistiqueItems.ECO_SOURCE_CELL_64M,
                        NEArsEnergistiqueItems.ECO_SOURCE_CELL_256M),
                List.of(
                        ECOCellModels.STORAGE_CELL_L4_SOURCE,
                        ECOCellModels.STORAGE_CELL_L6_SOURCE,
                        ECOCellModels.STORAGE_CELL_L9_SOURCE));
    }
}
