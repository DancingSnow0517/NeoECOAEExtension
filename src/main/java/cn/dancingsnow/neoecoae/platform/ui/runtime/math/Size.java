package cn.dancingsnow.neoecoae.platform.ui.runtime.math;
public final class Size { public final int width,height; private Size(int width,int height){this.width=width;this.height=height;} public static Size of(int width,int height){return new Size(width,height);} }
