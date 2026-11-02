package cn.dancingsnow.neoecoae.compat.ae2;

import appeng.api.crafting.IPatternDetails;
import cn.dancingsnow.neoecoae.api.me.pattern.ECOPatternWrappers;
import java.util.List;
import org.jetbrains.annotations.Nullable;

/** Provider lookup follows explicit registered contracts, never guessed method names. */
public final class ECOProviderPatternIntrospection {
    private ECOProviderPatternIntrospection() {}

    public static List<Object> wrapperChain(Object original) {
        return ECOPatternWrappers.wrapperChain(original);
    }

    @Nullable
    public static IPatternDetails unwrap(@Nullable IPatternDetails original) {
        return ECOPatternWrappers.unwrap(original);
    }
}
