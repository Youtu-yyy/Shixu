package cn.shixu.app.test;
import android.app.*;
import android.content.*;
import android.graphics.*;
import android.net.Uri;
import android.os.Bundle;
import java.io.*;
import java.util.*;
import cn.shixu.app.*;
import java.util.concurrent.*;
/** Private, staged-photo experiments; does not contain user content or persist tasks. */
public class EnhanceInstrumentation extends OcrInstrumentation {
    Bitmap enhance(Bitmap source,int mode){int w=source.getWidth(),h=source.getHeight();int[] pixels=new int[w*h];source.getPixels(pixels,0,w,0,0,w,h);int[] gray=new int[pixels.length];for(int i=0;i<pixels.length;i++){int c=pixels[i];gray[i]=(Color.red(c)*77+Color.green(c)*150+Color.blue(c)*29)>>8;}
        long[] sums=new long[(w+1)*(h+1)];if(mode==2)for(int y=0;y<h;y++){long row=0;for(int x=0;x<w;x++){row+=gray[y*w+x];sums[(y+1)*(w+1)+x+1]=sums[y*(w+1)+x+1]+row;}}
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){int i=y*w+x,v=gray[i];if(mode==1)v=(v-160)*3+180;else{int l=Math.max(0,x-18),r=Math.min(w,x+19),t=Math.max(0,y-18),b=Math.min(h,y+19);long mean=(sums[b*(w+1)+r]-sums[t*(w+1)+r]-sums[b*(w+1)+l]+sums[t*(w+1)+l])/((r-l)*(b-t));v=255-(int)(mean-v)*6;}v=Math.max(0,Math.min(255,v));pixels[i]=Color.rgb(v,v,v);}Bitmap out=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);out.setPixels(pixels,0,w,0,0,w,h);return out;}
    @Override public void onStart(){Bundle result=new Bundle();Context c=getTargetContext();List<Uri> cleanup=new ArrayList<>();int outcome=Activity.RESULT_OK;try{File photo=new File(c.getExternalFilesDir(null),"multi-user-table.jpg");Bitmap original=BitmapFactory.decodeFile(photo.toString());check(original!=null,"Staged photo exists");StringBuilder report=new StringBuilder();
        Bitmap production=OcrEnhancement.apply(original),reference=enhance(original,2);boolean equal=true;for(int y=0;y<original.getHeight();y+=13)for(int x=0;x<original.getWidth();x+=13)if(production.getPixel(x,y)!=reference.getPixel(x,y))equal=false;check(equal,"Bounded-memory enhancement equals reference local normalization");production.recycle();reference.recycle();
        CountDownLatch ready=new CountDownLatch(1);String[] enhanced={null};OcrEngine.recognize(c,Uri.fromFile(photo),true,new OcrEngine.Listener(){public void preview(Bitmap b){}public void text(String s){enhanced[0]=s;ready.countDown();}public void error(String e){enhanced[0]="ERROR:"+e;ready.countDown();}});check(ready.await(60,TimeUnit.SECONDS)&&enhanced[0]!=null&&!enhanced[0].startsWith("ERROR:"),"Production enhanced recognition completes offline");List<MultiEventParser.Candidate> rows=MultiEventParser.extract(enhanced[0],java.time.LocalDateTime.now(),TimeParser.DEFAULTS);check(rows.size()==8&&rows.stream().noneMatch(r->r.task.title.equals("待补充标题")),"Enhanced photo retains eight nonempty event titles");check(rows.get(7).task.date.endsWith("-11-06"),"Enhanced recognition preserves the two-digit month");report.append("=== PRODUCTION ENHANCED ===\n").append(enhanced[0]);
        for(int mode=0;mode<3;mode++){Bitmap prepared=mode==0?original:enhance(original,mode);Uri uri=save(c,png(prepared),"image/png",cleanup);report.append("\n=== MODE ").append(mode).append(" ===\n").append(recognize(c,uri,true)).append('\n');
            if(mode==2)for(int n=0;n<3;n++){int top=n*original.getHeight()/3,bottom=(n+1)*original.getHeight()/3;Bitmap tile=Bitmap.createBitmap(prepared,0,top,prepared.getWidth(),bottom-top);Bitmap scaled=Bitmap.createScaledBitmap(tile,tile.getWidth()*2,tile.getHeight()*2,true);Uri part=save(c,png(scaled),"image/png",cleanup);report.append("\n=== TILE ").append(n).append(" ===\n").append(recognize(c,part,true));scaled.recycle();tile.recycle();}if(prepared!=original)prepared.recycle();}
        original.recycle();try(FileWriter out=new FileWriter(new File(c.getExternalFilesDir(null),"enhancement-experiment.txt"))){out.write(report.toString());}result.putString("result","PASS: "+checks+" enhancement experiments");
    }catch(Throwable e){StringWriter trace=new StringWriter();e.printStackTrace(new PrintWriter(trace));result.putString("error",trace.toString());outcome=Activity.RESULT_CANCELED;}finally{for(Uri u:cleanup)try{c.getContentResolver().delete(u,null,null);}catch(RuntimeException ignored){}}finish(outcome,result);}
}
