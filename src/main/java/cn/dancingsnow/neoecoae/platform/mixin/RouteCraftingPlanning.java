package cn.dancingsnow.neoecoae.platform.mixin;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.spongepowered.asm.mixin.injection.At;

/** A tracked redirect covering native and addon-merged instance planning entries. */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface RouteCraftingPlanning {
    At[] at();
    int require() default -1;
}
