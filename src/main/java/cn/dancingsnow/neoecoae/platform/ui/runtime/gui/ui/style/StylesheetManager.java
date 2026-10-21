package cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.style;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.*;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.elements.*;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.texture.*;
import cn.dancingsnow.neoecoae.platform.ui.runtime.editor.resource.BuiltinPath;
import net.minecraft.resources.ResourceLocation;
import java.util.*;
import java.util.regex.*;
import java.nio.charset.StandardCharsets;

/** Strict reader for the ECO stylesheet declarations used by the locked source. */
public final class StylesheetManager {
    public static final StylesheetManager INSTANCE=new StylesheetManager();
    private final Map<ResourceLocation,Stylesheet> cache=new java.util.concurrent.ConcurrentHashMap<>();
    @FunctionalInterface public interface ResourceOpener { java.io.InputStream open(ResourceLocation location) throws java.io.IOException; }
    private volatile ResourceOpener resourceOpener;
    public void setResourceOpener(ResourceOpener opener){resourceOpener=Objects.requireNonNull(opener);clearCache();}
    public void clearCache(){cache.clear();}
    public Stylesheet getStylesheetSafe(ResourceLocation location){return cache.computeIfAbsent(location,this::load);}
    private Stylesheet load(ResourceLocation location){
        String path=location.getPath();if(!path.endsWith(".lss"))path="lss/"+path+".lss";
        String asset="assets/"+location.getNamespace()+"/"+path;
        var opener=resourceOpener;
        try(var input=opener==null?StylesheetManager.class.getClassLoader().getResourceAsStream(asset):opener.open(new ResourceLocation(location.getNamespace(),path))){
            if(input==null)throw new IllegalArgumentException("Missing stylesheet "+asset);
            return parse(new String(input.readAllBytes(),StandardCharsets.UTF_8));
        }catch(java.io.IOException ex){throw new IllegalStateException("Cannot load "+asset,ex);}
    }
    public static Stylesheet parse(String text){
        var rules=new ArrayList<Rule>();var matcher=Pattern.compile("([^{}]+)\\{([^{}]*)}").matcher(text.replaceAll("(?m)//.*$",""));
        while(matcher.find()){
            var declarations=new LinkedHashMap<String,String>();for(String pair:matcher.group(2).split(";"))if(!pair.isBlank()){
                int colon=pair.indexOf(':');if(colon<1)throw new IllegalArgumentException("Invalid style declaration "+pair);
                String key=pair.substring(0,colon).trim();if(!Set.of("background","base-background","hover-background","pressed-background","hover-overlay","padding-all","gap-all","height","text-color","text-shadow").contains(key))throw new IllegalArgumentException("Unsupported ECO style "+key);
                declarations.put(key,pair.substring(colon+1).trim());}
            for(String selector:matcher.group(1).split(","))rules.add(new Rule(selector.trim().replace(":host",""),Map.copyOf(declarations)));
        }return new Stylesheet(List.copyOf(rules));
    }
    public record Stylesheet(List<Rule> rules){
        public void apply(UIElement root){applyOne(root);for(var child:root.getChildren())apply(child);}
        private void applyOne(UIElement element){
            var values=new LinkedHashMap<String,String>();
            rules.stream().filter(rule->rule.matches(element)).sorted(Comparator.comparingInt(Rule::specificity)).forEach(rule->values.putAll(rule.values));
            if(!element.getStyle().inlineBackground)element.getStyle().background=null;
            element.getLayoutStyle().clearCssProperties();
            if(element instanceof TextElement text){var style=text.getTextStyle();if(!style.inlineColor)style.color=0xff404040;if(!style.inlineShadow)style.shadow=false;}
            if(element instanceof Button button){var style=button.getButtonStyle();if(!style.inlineBase)style.base=null;if(!style.inlineHover)style.hover=null;if(!style.inlinePressed)style.pressed=null;}
            values.forEach((name,value)->{
                switch(name){
                    case "background"->{if(!element.getStyle().inlineBackground)element.getStyle().background=texture(value);}
                    case "padding-all","gap-all","height"->element.getLayoutStyle().applyCss(name,Float.parseFloat(value));
                    case "base-background"->{if(element instanceof Button button&&!button.getButtonStyle().inlineBase)button.getButtonStyle().base=texture(value);}
                    case "hover-background"->{if(element instanceof Button button&&!button.getButtonStyle().inlineHover)button.getButtonStyle().hover=texture(value);}
                    case "pressed-background"->{if(element instanceof Button button&&!button.getButtonStyle().inlinePressed)button.getButtonStyle().pressed=texture(value);}
                    case "hover-overlay"->{if(element instanceof ItemSlot slot)slot.slotStyle(style->{if(style.hover==null)style.hover=texture(value);});else if(element instanceof FluidSlot slot)slot.slotStyle(style->style.hover=texture(value));}
                    case "text-color"->{if(element instanceof TextElement text&&!text.getTextStyle().inlineColor)text.getTextStyle().color=color(value);}
                    case "text-shadow"->{if(element instanceof TextElement text&&!text.getTextStyle().inlineShadow)text.getTextStyle().shadow=Boolean.parseBoolean(value);}
                    default->throw new IllegalArgumentException(name);
                }
            });
        }
    }

    public record Rule(String selector,Map<String,String> values){
        int specificity(){int score=0;for(char c:selector.toCharArray())if(c=='.')score+=10;else if(c=='#')score+=100;
            for(String part:selector.split("\\s+"))if(!part.startsWith(".")&&!part.startsWith("#"))score++;return score;}
        boolean matches(UIElement element){String[] parts=selector.split("\\s+");int i=parts.length-1;if(!simple(element,parts[i--]))return false;
            for(UIElement ancestor=element.getParentElement();i>=0&&ancestor!=null;ancestor=ancestor.getParentElement())if(simple(ancestor,parts[i]))i--;
            return i<0;}
        static boolean simple(UIElement element,String text){int dot=text.indexOf('.');String type=dot<0?text:text.substring(0,dot);
            if(!type.isEmpty()&&!element.matches(type))return false;if(dot>=0)for(String cls:text.substring(dot+1).split("\\."))if(!element.hasClass(cls))return false;return true;}
    }
    private static IGuiTexture texture(String text){return text.startsWith("built-in(")?new UIResourceTexture(new BuiltinPath(text.substring(9,text.length()-1))):new ColorRectTexture(color(text));}
    private static int color(String text){String hex=text.substring(1);if(hex.length()==3)hex=""+hex.charAt(0)+hex.charAt(0)+hex.charAt(1)+hex.charAt(1)+hex.charAt(2)+hex.charAt(2);
        long value=Long.parseLong(hex,16);return (int)(hex.length()<=6?value|0xff000000L:value);}
}
