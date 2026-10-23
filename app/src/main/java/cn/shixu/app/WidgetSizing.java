package cn.shixu.app;

/** Density-independent sizes supplied by the home-screen widget host. */
public final class WidgetSizing {
    public final int title,detail,tag,heading,padding,rowPadding,categoryWidth;
    public final boolean compact;
    private WidgetSizing(boolean compact,int title,int detail,int tag,int heading,int padding,int rowPadding,int categoryWidth){this.compact=compact;this.title=title;this.detail=detail;this.tag=tag;this.heading=heading;this.padding=padding;this.rowPadding=rowPadding;this.categoryWidth=categoryWidth;}
    public static WidgetSizing forSize(int width,int height){
        if(width<=0)width=250;if(height<=0)height=250;
        if(width<280||height<280)return new WidgetSizing(true,12,9,9,16,10,7,68);
        if(width>=330&&height>=330)return new WidgetSizing(false,15,11,10,20,16,12,110);
        return new WidgetSizing(false,13,10,9,18,14,10,90);
    }
}
