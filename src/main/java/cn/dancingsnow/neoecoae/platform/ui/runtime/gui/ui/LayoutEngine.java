package cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui;

import cn.dancingsnow.neoecoae.platform.ui.runtime.layout.*;
import java.util.*;

/** Layouts used by ECO JSON: absolute/relative pixels, percentages, flex flow and explicit grid tracks. */
public final class LayoutEngine {
    private LayoutEngine(){}
    private record Bounds(float width,float height){}
    private static boolean row(Layout l){return l.direction==FlexDirection.ROW||l.direction==FlexDirection.ROW_REVERSE;}
    private static boolean shown(UIElement e){return e.isDisplay()&&e.getLayoutStyle().display!=TaffyDisplay.NONE;}
    private static float resolve(Float value,boolean percent,float reference,float fallback){return value==null?fallback:percent?reference*value/100:value;}
    private static float clamp(float value,Float min,Float max){return Math.max(min==null?0:min,Math.min(max==null?Float.MAX_VALUE:max,value));}
    public static void arrange(UIElement root){var natural=measure(root,0,0);place(root,root.getSelfPositionX(),root.getSelfPositionY(),natural.width,natural.height);}
    private static Bounds measure(UIElement element,float availableWidth,float availableHeight){
        if(!shown(element))return new Bounds(0,0);var l=element.getLayoutStyle();l.resolvePercentages(availableWidth,availableHeight);
        float innerW=Math.max(0,resolve(l.width,l.widthPercent,availableWidth,availableWidth)-l.paddingLeft-l.paddingRight);
        float innerH=Math.max(0,resolve(l.height,l.heightPercent,availableHeight,availableHeight)-l.paddingTop-l.paddingBottom);
        float main=0,cross=0;int count=0;
        if(l.display==TaffyDisplay.GRID){
            float[] cols=tracks(l.columns,innerW,l.gap),rows=tracks(l.rows,innerH,l.gap);
            for(var child:element.getChildren()){if(!shown(child))continue;var c=child.getLayoutStyle();var size=measure(child,innerW,innerH);
                int column=Math.max(0,(c.gridColumn==0?count%cols.length:c.gridColumn-1));int r=Math.max(0,(c.gridRow==0?count/cols.length:c.gridRow-1));
                if(column>=cols.length||r>=rows.length)throw new IllegalArgumentException("ECO grid child exceeds declared tracks");
                cols[column]=Math.max(cols[column],size.width+c.marginLeft+c.marginRight);rows[r]=Math.max(rows[r],size.height+c.marginTop+c.marginBottom);count++;}
            main=sum(cols)+Math.max(0,cols.length-1)*l.gap;cross=sum(rows)+Math.max(0,rows.length-1)*l.gap;
        }else{
            for(var child:element.getChildren()){var c=child.getLayoutStyle();if(!shown(child)||c.position==TaffyPosition.ABSOLUTE)continue;
                var size=measure(child,innerW,innerH);float w=size.width+c.marginLeft+c.marginRight,h=size.height+c.marginTop+c.marginBottom;
                main+=row(l)?w:h;cross=Math.max(cross,row(l)?h:w);count++;}
            main+=Math.max(0,count-1)*l.mainGap();
        }
        float w=Math.max(element.intrinsicWidth(),l.display==TaffyDisplay.GRID?main:row(l)?main:cross)+l.paddingLeft+l.paddingRight;
        float h=Math.max(element.intrinsicHeight(),l.display==TaffyDisplay.GRID?cross:row(l)?cross:main)+l.paddingTop+l.paddingBottom;
        w=resolve(l.width,l.widthPercent,availableWidth,w);h=resolve(l.height,l.heightPercent,availableHeight,h);
        if(l.aspectRatio!=null&&l.aspectRatio>0){if(l.height==null&&l.width!=null)h=w/l.aspectRatio;else if(l.width==null&&l.height!=null)w=h*l.aspectRatio;}
        return new Bounds(clamp(w,l.minWidth,l.maxWidth),clamp(h,l.minHeight,l.maxHeight));
    }
    private static void place(UIElement element,float x,float y,float width,float height){
        if(!shown(element)){element.applyBounds(x,y,0,0);return;}element.applyBounds(x,y,width,height);var l=element.getLayoutStyle();
        float iw=Math.max(0,width-l.paddingLeft-l.paddingRight),ih=Math.max(0,height-l.paddingTop-l.paddingBottom);
        var flow=new ArrayList<UIElement>();for(var child:element.getChildren())if(shown(child)&&child.getLayoutStyle().position!=TaffyPosition.ABSOLUTE)flow.add(child);
        if(l.display==TaffyDisplay.GRID){placeGrid(flow,l,iw,ih);}else{
            if(l.direction==FlexDirection.ROW_REVERSE||l.direction==FlexDirection.COLUMN_REVERSE)Collections.reverse(flow);
            float used=0,grow=0,shrink=0;var measured=new IdentityHashMap<UIElement,Bounds>();
            for(var child:flow){var c=child.getLayoutStyle();var b=measure(child,iw,ih);measured.put(child,b);used+=row(l)?b.width+c.marginLeft+c.marginRight:b.height+c.marginTop+c.marginBottom;grow+=c.grow;shrink+=c.shrink*(row(l)?b.width:b.height);}
            used+=Math.max(0,flow.size()-1)*l.mainGap();float free=(row(l)?iw:ih)-used;
            float position=offset(l.justify,Math.max(0,free));float gap=l.mainGap();
            if(grow==0&&free>0){if(l.justify==AlignContent.SPACE_BETWEEN&&flow.size()>1)gap+=free/(flow.size()-1);
                if(l.justify==AlignContent.SPACE_AROUND&&!flow.isEmpty()){gap+=free/flow.size();position=free/flow.size()/2;}
                if(l.justify==AlignContent.SPACE_EVENLY&&!flow.isEmpty()){gap+=free/(flow.size()+1);position=free/(flow.size()+1);}}
            for(var child:flow){var c=child.getLayoutStyle();var b=measured.get(child);float w=b.width,h=b.height;
                float adjustment=free>0&&grow>0?free*c.grow/grow:free<0&&shrink>0?free*c.shrink*(row(l)?w:h)/shrink:0;
                if(row(l))w=Math.max(0,w+adjustment);else h=Math.max(0,h+adjustment);
                AlignItems alignment=c.alignSelf==null?l.align:c.alignSelf;
                if(row(l)){if(c.height==null&&alignment==AlignItems.STRETCH)h=Math.max(0,ih-c.marginTop-c.marginBottom);
                    place(child,l.paddingLeft+position+c.marginLeft,l.paddingTop+c.marginTop+align(alignment,ih-h-c.marginTop-c.marginBottom),w,h);position+=w+c.marginLeft+c.marginRight+gap;
                }else{if(c.width==null&&alignment==AlignItems.STRETCH)w=Math.max(0,iw-c.marginLeft-c.marginRight);
                    place(child,l.paddingLeft+c.marginLeft+align(alignment,iw-w-c.marginLeft-c.marginRight),l.paddingTop+position+c.marginTop,w,h);position+=h+c.marginTop+c.marginBottom+gap;}}
        }
        for(var child:element.getChildren()){
            var c=child.getLayoutStyle();if(!shown(child)){child.applyBounds(0,0,0,0);continue;}if(c.position!=TaffyPosition.ABSOLUTE)continue;
            var natural=measure(child,iw,ih);float left=resolve(c.left,c.leftPercent,iw,Float.NaN),top=resolve(c.top,c.topPercent,ih,Float.NaN);
            float right=resolve(c.right,c.rightPercent,iw,Float.NaN),bottom=resolve(c.bottom,c.bottomPercent,ih,Float.NaN);
            float w=c.width==null&&!Float.isNaN(left)&&!Float.isNaN(right)?Math.max(0,iw-left-right):natural.width;
            float h=c.height==null&&!Float.isNaN(top)&&!Float.isNaN(bottom)?Math.max(0,ih-top-bottom):natural.height;
            float cx=Float.isNaN(left)?Float.isNaN(right)?0:iw-right-w:left;
            float cy=Float.isNaN(top)?Float.isNaN(bottom)?0:ih-bottom-h:top;
            place(child,l.paddingLeft+cx,l.paddingTop+cy,w,h);
        }
        element.onLayoutApplied();
    }
    private static void placeGrid(List<UIElement> flow,Layout l,float iw,float ih){
        float[] cols=tracks(l.columns,iw,l.gap),rows=tracks(l.rows,ih,l.gap);int index=0;
        var sizes=new IdentityHashMap<UIElement,Bounds>();
        for(var child:flow){var c=child.getLayoutStyle();var b=measure(child,iw/cols.length,ih/rows.length);sizes.put(child,b);
            int x=c.gridColumn==0?index%cols.length:c.gridColumn-1,y=c.gridRow==0?index/cols.length:c.gridRow-1;
            if(x<0||x>=cols.length||y<0||y>=rows.length)throw new IllegalArgumentException("Invalid ECO grid placement");
            if(l.columns.split("\\s+")[x].equals("auto"))cols[x]=Math.max(cols[x],b.width+c.marginLeft+c.marginRight);
            if(l.rows.split("\\s+")[y].equals("auto"))rows[y]=Math.max(rows[y],b.height+c.marginTop+c.marginBottom);index++;}
        index=0;for(var child:flow){var c=child.getLayoutStyle();var b=sizes.get(child);int x=c.gridColumn==0?index%cols.length:c.gridColumn-1,y=c.gridRow==0?index/cols.length:c.gridRow-1;
            float left=l.paddingLeft,top=l.paddingTop;for(int i=0;i<x;i++)left+=cols[i]+l.gap;for(int i=0;i<y;i++)top+=rows[i]+l.gap;
            float w=c.width==null&&l.justifyItems==AlignItems.STRETCH?cols[x]-c.marginLeft-c.marginRight:b.width;
            float h=c.height==null&&l.align==AlignItems.STRETCH?rows[y]-c.marginTop-c.marginBottom:b.height;
            place(child,left+c.marginLeft+align(l.justifyItems,cols[x]-w-c.marginLeft-c.marginRight),top+c.marginTop+align(l.align,rows[y]-h-c.marginTop-c.marginBottom),w,h);index++;}
    }
    private static float[] tracks(String definition,float available,float gap){
        String[] tokens=definition.trim().split("\\s+");float[] result=new float[tokens.length];float fixed=0,totalFraction=0;
        for(int i=0;i<tokens.length;i++){String token=tokens[i];if(token.equals("auto"))continue;
            if(token.endsWith("fr"))totalFraction+=Float.parseFloat(token.substring(0,token.length()-2));
            else{result[i]=token.endsWith("px")?Float.parseFloat(token.substring(0,token.length()-2)):Float.parseFloat(token);fixed+=result[i];}}
        if(totalFraction>0)for(int i=0;i<tokens.length;i++)if(tokens[i].endsWith("fr"))result[i]=Math.max(0,available-fixed-gap*(tokens.length-1))*Float.parseFloat(tokens[i].substring(0,tokens[i].length()-2))/totalFraction;
        return result;
    }
    private static float sum(float[] values){float result=0;for(float value:values)result+=value;return result;}
    private static float offset(AlignContent align,float free){return align==AlignContent.CENTER?free/2:align==AlignContent.END||align==AlignContent.FLEX_END?free:0;}
    private static float align(AlignItems align,float free){return align==AlignItems.CENTER?free/2:align==AlignItems.END||align==AlignItems.FLEX_END?free:0;}
}
