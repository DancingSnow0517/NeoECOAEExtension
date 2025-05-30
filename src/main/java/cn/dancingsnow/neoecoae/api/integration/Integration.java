package cn.dancingsnow.neoecoae.api.integration;

import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
public @interface Integration {
    String value();
}
