package cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui;

import cn.dancingsnow.neoecoae.platform.ui.runtime.layout.*;
import java.util.function.Consumer;

/** The project's pixel/flex/grid layout declarations, independent of the source version's native layout library. */
public final class Layout {
    public Float width, height, left, top, right, bottom, minWidth, minHeight, maxWidth, maxHeight;
    public boolean widthPercent, heightPercent, leftPercent, topPercent, rightPercent, bottomPercent;
    public float paddingLeft, paddingRight, paddingTop, paddingBottom, marginLeft, marginRight, marginTop, marginBottom;
    public float gap, grow, shrink = 1;
    public Float aspectRatio,rowGap,columnGap;
    public AlignItems alignSelf,justifySelf;
    public AlignContent alignContent=AlignContent.STRETCH;
    private final java.util.Map<String,Float> percentages=new java.util.HashMap<>();
    private final java.util.Set<String> inlineProperties=new java.util.HashSet<>();
    private Layout inline(String key,Runnable action){inlineProperties.add(key);return update(action);}
    void clearCss(){if(!inlineProperties.contains("height"))height=null;
        if(!inlineProperties.contains("gap"))gap=0;
        if(!inlineProperties.contains("paddingLeft"))paddingLeft=0;if(!inlineProperties.contains("paddingRight"))paddingRight=0;
        if(!inlineProperties.contains("paddingTop"))paddingTop=0;if(!inlineProperties.contains("paddingBottom"))paddingBottom=0;}
    public void applyCss(String key,float value){
        switch(key){
            case "height"->{if(!inlineProperties.contains("height")){height=value;heightPercent=false;}}
            case "gap-all"->{if(!inlineProperties.contains("gap"))gap=value;}
            case "padding-all"->{if(!inlineProperties.contains("paddingLeft"))paddingLeft=value;if(!inlineProperties.contains("paddingRight"))paddingRight=value;
                if(!inlineProperties.contains("paddingTop"))paddingTop=value;if(!inlineProperties.contains("paddingBottom"))paddingBottom=value;}
            default->throw new IllegalArgumentException(key);
        }changed.run();
    }
    public void clearCssProperties(){clearCss();}
    public TaffyPosition position = TaffyPosition.RELATIVE;
    public TaffyDisplay display = TaffyDisplay.FLEX;
    public FlexDirection direction = FlexDirection.COLUMN;
    public AlignItems align = AlignItems.STRETCH;
    public AlignContent justify = AlignContent.START;
    public AlignItems justifyItems = AlignItems.STRETCH;
    public String columns = "auto", rows = "auto";
    public int gridColumn, gridRow;
    private final Runnable changed;

    public Layout(Runnable changed) { this.changed = changed; }
    private Layout update(Runnable action) { action.run(); changed.run(); return this; }
    public Layout width(float value) { return update(() -> { width = value; widthPercent = false; }); }
    public Layout height(float value) { return inline("height",() -> { height = value; heightPercent = false; }); }
    public Layout widthAuto(){return update(()->{width=null;widthPercent=false;});}
    public Layout heightAuto(){return inline("height",()->{height=null;heightPercent=false;});}
    public Layout widthPercent(float value) { return update(() -> { width = value; widthPercent = true; }); }
    public Layout heightPercent(float value) { return inline("height",() -> { height = value; heightPercent = true; }); }
    public Layout left(float value) { return update(() -> { left = value; leftPercent = false; }); }
    public Layout top(float value) { return update(() -> { top = value; topPercent = false; }); }
    public Layout right(float value) { return update(() -> { right = value; rightPercent = false; }); }
    public Layout bottom(float value) { return update(() -> { bottom = value; bottomPercent = false; }); }
    public Layout leftPercent(float value) { return update(() -> { left = value; leftPercent = true; }); }
    public Layout topPercent(float value) { return update(() -> { top = value; topPercent = true; }); }
    public Layout rightPercent(float value) { return update(() -> { right = value; rightPercent = true; }); }
    public Layout bottomPercent(float value) { return update(() -> { bottom = value; bottomPercent = true; }); }
    public Layout minWidth(float value) { return update(() -> minWidth = value); }
    public Layout minHeight(float value) { return update(() -> minHeight = value); }
    public Layout maxWidth(float value) { return update(() -> maxWidth = value); }
    public Layout maxHeight(float value) { return update(() -> maxHeight = value); }
    public Layout positionType(TaffyPosition value) { return update(() -> position = value); }
    public Layout display(TaffyDisplay value) { return update(() -> display = value); }
    public Layout flexDirection(FlexDirection value) { return update(() -> direction = value); }
    public Layout alignItems(AlignItems value) { return update(() -> align = value); }
    public Layout justifyItems(AlignItems value) { return update(() -> justifyItems = value); }
    public Layout justifyContent(AlignContent value) { return update(() -> justify = value); }
    public Layout gapAll(float value) { return inline("gap",() -> gap = value); }
    public Layout paddingAll(float value) { return paddingLeft(value).paddingRight(value).paddingTop(value).paddingBottom(value); }
    public Layout paddingLeft(float value) { return inline("paddingLeft",() -> { percentages.remove("paddingLeft"); paddingLeft = value; }); }
    public Layout paddingRight(float value) { return inline("paddingRight",() -> { percentages.remove("paddingRight"); paddingRight = value; }); }
    public Layout paddingTop(float value) { return inline("paddingTop",() -> { percentages.remove("paddingTop"); paddingTop = value; }); }
    public Layout paddingBottom(float value) { return inline("paddingBottom",() -> { percentages.remove("paddingBottom"); paddingBottom = value; }); }
    public Layout marginAll(float value) { return marginLeft(value).marginRight(value).marginTop(value).marginBottom(value); }
    public Layout marginLeft(float value) { return update(() -> { percentages.remove("marginLeft"); marginLeft = value; }); }
    public Layout marginRight(float value) { return update(() -> { percentages.remove("marginRight"); marginRight = value; }); }
    public Layout marginTop(float value) { return update(() -> { percentages.remove("marginTop"); marginTop = value; }); }
    public Layout marginBottom(float value) { return update(() -> { percentages.remove("marginBottom"); marginBottom = value; }); }
    public Layout flex(float value) { return flexGrow(value); }
    public Layout flexGrow(float value) { return update(() -> grow = value); }
    public Layout flexShrink(float value) { return update(() -> shrink = value); }
    public Layout gridTemplateColumns(String value) { return update(() -> columns = value); }
    public Layout gridTemplateRows(String value) { return update(() -> rows = value); }
    public Layout gridRow(int value) { return update(() -> gridRow = value); }
    public Layout gridColumn(int value) { return update(() -> gridColumn = value); }
    public Layout gridColumn(String value){return gridColumn(Integer.parseInt(value));}
    public Layout gridRow(String value){return gridRow(Integer.parseInt(value));}
    public Layout paddingLeftPercent(float value){return inline("paddingLeft",()->percentages.put("paddingLeft",value));}
    public Layout paddingRightPercent(float value){return inline("paddingRight",()->percentages.put("paddingRight",value));}
    public Layout paddingTopPercent(float value){return inline("paddingTop",()->percentages.put("paddingTop",value));}
    public Layout paddingBottomPercent(float value){return inline("paddingBottom",()->percentages.put("paddingBottom",value));}
    public Layout marginLeftPercent(float value){return update(()->percentages.put("marginLeft",value));}
    public Layout marginRightPercent(float value){return update(()->percentages.put("marginRight",value));}
    public Layout marginTopPercent(float value){return update(()->percentages.put("marginTop",value));}
    public Layout marginBottomPercent(float value){return update(()->percentages.put("marginBottom",value));}
    public Layout paddingAllPercent(float value){return paddingLeftPercent(value).paddingRightPercent(value).paddingTopPercent(value).paddingBottomPercent(value);}
    public Layout marginAllPercent(float value){return marginLeftPercent(value).marginRightPercent(value).marginTopPercent(value).marginBottomPercent(value);}
    public Layout gapAllPercent(float value){return gapRowPercent(value).gapColumnPercent(value);}
    public Layout gapRow(float value){return update(()->{percentages.remove("rowGap");rowGap=value;});}
    public Layout gapColumn(float value){return update(()->{percentages.remove("columnGap");columnGap=value;});}
    public Layout gapRowPercent(float value){return update(()->percentages.put("rowGap",value));}
    public Layout gapColumnPercent(float value){return update(()->percentages.put("columnGap",value));}
    public Layout alignContent(AlignContent value){return update(()->alignContent=value);}
    public Layout alignSelf(AlignItems value){return update(()->alignSelf=value);}
    public Layout justifySelf(AlignItems value){return update(()->justifySelf=value);}
    public Layout setAspectRatio(float value){return update(()->aspectRatio=value);}
    void resolvePercentages(float width,float height){percentages.forEach((name,value)->{float pixels=value*(name.equals("rowGap")?height:width)/100;
        switch(name){
            case "paddingLeft" -> paddingLeft=pixels;
            case "paddingRight" -> paddingRight=pixels;
            case "paddingTop" -> paddingTop=pixels;
            case "paddingBottom" -> paddingBottom=pixels;
            case "marginLeft" -> marginLeft=pixels;
            case "marginRight" -> marginRight=pixels;
            case "marginTop" -> marginTop=pixels;
            case "marginBottom" -> marginBottom=pixels;
            case "rowGap" -> rowGap=pixels;
            case "columnGap" -> columnGap=pixels;
            default -> throw new IllegalStateException(name);
        }});}
    float mainGap(){return direction==FlexDirection.ROW||direction==FlexDirection.ROW_REVERSE?(columnGap==null?gap:columnGap):(rowGap==null?gap:rowGap);}
}
