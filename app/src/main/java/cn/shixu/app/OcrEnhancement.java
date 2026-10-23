package cn.shixu.app;
import android.graphics.*;

/** Local illumination normalization. Sliding box sums avoid a full-size long integral image. */
public final class OcrEnhancement {
    public static Bitmap apply(Bitmap source){
        int w=source.getWidth(),h=source.getHeight(),radius=18;int[] pixels=new int[w*h];byte[] gray=new byte[w*h];int[] columns=new int[w];source.getPixels(pixels,0,w,0,0,w,h);
        for(int i=0;i<pixels.length;i++){int c=pixels[i],a=Color.alpha(c),v=(Color.red(c)*77+Color.green(c)*150+Color.blue(c)*29)>>8;gray[i]=(byte)((v*a+255*(255-a))/255);}
        for(int y=0;y<Math.min(h,radius+1);y++)for(int x=0;x<w;x++)columns[x]+=gray[y*w+x]&255;
        for(int y=0;y<h;y++){
            int top=Math.max(0,y-radius),bottom=Math.min(h,y+radius+1),sum=0;for(int x=0;x<Math.min(w,radius+1);x++)sum+=columns[x];
            for(int x=0;x<w;x++){int left=Math.max(0,x-radius),right=Math.min(w,x+radius+1),mean=sum/((right-left)*(bottom-top)),i=y*w+x;int v=Math.max(0,Math.min(255,255-(mean-(gray[i]&255))*6));pixels[i]=Color.rgb(v,v,v);if(x-radius>=0)sum-=columns[x-radius];if(x+radius+1<w)sum+=columns[x+radius+1];}
            for(int x=0;x<w;x++){if(y-radius>=0)columns[x]-=gray[(y-radius)*w+x]&255;if(y+radius+1<h)columns[x]+=gray[(y+radius+1)*w+x]&255;}
        }
        Bitmap out=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);out.setPixels(pixels,0,w,0,0,w,h);return out;
    }
    private OcrEnhancement(){}
}
