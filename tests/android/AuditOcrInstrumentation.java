package cn.shixu.app.test;
import android.app.*;
import android.content.*;
import android.graphics.*;
import android.net.Uri;
import android.os.*;
import cn.shixu.app.*;
import java.util.*;
import java.time.*;
import java.util.concurrent.*;

/** Reproducible fictional low-contrast and ticket images; never needs a private photo. */
public final class AuditOcrInstrumentation extends BatchInstrumentation {
    boolean probe;StringBuilder mismatches=new StringBuilder();
    @Override public void onCreate(Bundle b){probe="true".equals(b.getString("probe"));super.onCreate(b);}
    String read(Context c,Uri uri,boolean enhanced)throws Exception {
        CountDownLatch done=new CountDownLatch(1);String[] text={null};
        OcrEngine.recognize(c,uri,enhanced,new OcrEngine.Listener(){public void preview(Bitmap b){b.recycle();}public void text(String s){text[0]=s;done.countDown();}public void error(String s){text[0]="ERROR:"+s;done.countDown();}});
        check(done.await(55,TimeUnit.SECONDS),"Offline OCR finishes");check(text[0]!=null&&!text[0].startsWith("ERROR:"),"Offline OCR returns text");return text[0];
    }
    void rows(String text,boolean exactTitles){
        List<MultiEventParser.Candidate> list=MultiEventParser.extract(text,LocalDateTime.of(2026,10,22,10,0),TimeParser.DEFAULTS);
        check(list.size()==4,"All four table rows extracted");
        String[] names={"阅读分享","办公技能培训","素质拓展","结营考试"},dates={"2026-10-24","2026-10-24","2026-10-25","2026-11-01"},times={"09:30","10:30","14:00","20:00"},places={"A101","A202","校园广场","A106"};
        for(int n=0;n<4;n++){Task t=list.get(n).task;if(exactTitles)check(t.title.equals(names[n]),"Row "+n+" title aligned; expected="+names[n]+"; actual="+t.title);else if(!t.title.equals(names[n]))mismatches.append("标准图第").append(n+1).append("行：期望").append(names[n]).append("，实际").append(t.title).append("；\n");check(t.date.equals(dates[n]),"Row "+n+" date aligned; actual="+t.date);check(t.time.equals(times[n]),"Row "+n+" time aligned; actual="+t.time);check(t.location.equals(places[n]),"Row "+n+" location aligned; actual="+t.location);}
    }
    @Override public void onStart(){Bundle result=new Bundle();Context c=getTargetContext();List<Uri> cleanup=new ArrayList<>();int code=Activity.RESULT_OK;try{
        int baseline;try(Store store=new Store(c)){baseline=store.all().size();}
        Bitmap original=table();Uri clean=save(c,png(original),"image/png",cleanup);
        if(probe){StringBuilder log=new StringBuilder();for(int scale:new int[]{1,2}){Bitmap enlarged=scale==1?original:Bitmap.createScaledBitmap(original,original.getWidth()*scale,original.getHeight()*scale,true);Uri input=save(c,png(enlarged),"image/png",cleanup);for(boolean enhanced:new boolean[]{false,true})log.append("SCALE ").append(scale).append(" ENHANCED ").append(enhanced).append("\n").append(read(c,input,enhanced)).append("\n");if(scale!=1)enlarged.recycle();}original.recycle();result.putString("probe",log.toString());result.putString("result","PASS: OCR diagnostic probe only");finish(Activity.RESULT_OK,result);return;}
        rows(read(c,clean,false),false);rows(read(c,clean,true),true);
        Bitmap low=Bitmap.createBitmap(original.getWidth(),original.getHeight(),Bitmap.Config.ARGB_8888);Paint tone=new Paint();tone.setColorFilter(new ColorMatrixColorFilter(new float[]{.25f,0,0,0,150,0,.25f,0,0,150,0,0,.25f,0,150,0,0,0,1,0}));new Canvas(low).drawBitmap(original,0,0,tone);
        Uri dim=save(c,png(low),"image/png",cleanup);rows(read(c,dim,true),true);original.recycle();low.recycle();
        Bitmap ticket=Bitmap.createBitmap(1000,800,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(ticket);canvas.drawColor(Color.WHITE);Paint ink=new Paint(Paint.ANTI_ALIAS_FLAG);ink.setColor(Color.BLACK);ink.setTextSize(42);canvas.drawText("虚构音乐会 2030年10月23日",40,130,ink);canvas.drawText("演出地点：城市音乐厅",40,230,ink);canvas.drawText("倒计时 00天20时53分",40,400,ink);canvas.drawText("2030年10月22日10:05开抢",40,690,ink);
        Uri whole=save(c,png(ticket),"image/png",cleanup);String raw=read(c,whole,false);check(raw.contains("2030")&&raw.contains("开抢"),"Whole screenshot retains performance and ticket text");
        Bitmap crop=Bitmap.createBitmap(ticket,0,600,1000,160);Uri cropped=save(c,png(crop),"image/png",cleanup);
        for(boolean enhanced:new boolean[]{false,true}){String text=read(c,cropped,enhanced);Task t=TimeParser.parse(text,LocalDateTime.of(2026,10,22,10,0),TimeParser.DEFAULTS).task;check(t.date.equals("2030-10-22")&&t.time.equals("10:05"),"Crop selects ticket date and clock rather than concert or countdown");check(t.title.contains("开抢"),"Ticket action remains in title");check(t.lead==30,"Ticket reminder keeps default lead");}
        ticket.recycle();crop.recycle();try(Store store=new Store(c)){check(store.all().size()==baseline,"Image tests never create user tasks");}
        result.putString("accuracy_limits",mismatches.toString());result.putString("result","PASS: "+checks+" fictional image standard/enhanced OCR, every-row alignment, crop date and no-save checks");
    }catch(Throwable e){code=Activity.RESULT_CANCELED;result.putString("error",android.util.Log.getStackTraceString(e));}finally{for(Uri uri:cleanup)try{c.getContentResolver().delete(uri,null,null);}catch(Exception ignored){}}finish(code,result);}
}
