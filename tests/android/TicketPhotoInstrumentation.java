package cn.shixu.app.test;
import android.app.*;
import android.content.*;
import android.graphics.*;
import android.net.Uri;
import android.os.Bundle;
import cn.shixu.app.*;
import java.io.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;

/** Tests a privately staged image without creating or scheduling real user tasks. */
public class TicketPhotoInstrumentation extends OcrInstrumentation {
    String read(Context c,Uri uri,boolean enhanced)throws Exception{CountDownLatch done=new CountDownLatch(1);String[] result={null};OcrEngine.recognize(c,uri,enhanced,new OcrEngine.Listener(){public void preview(Bitmap b){}public void text(String s){result[0]=s;done.countDown();}public void error(String s){result[0]="ERROR:"+s;done.countDown();}});check(done.await(55,TimeUnit.SECONDS),"OCR completes");check(result[0]!=null&&!result[0].startsWith("ERROR:"),"Image yields text");return result[0];}
    Task parse(String text){return NotificationExtractor.looksLikeNotice(text)?NotificationExtractor.extract(text,LocalDateTime.now(),TimeParser.DEFAULTS).task:TimeParser.parse(text,LocalDateTime.now(),TimeParser.DEFAULTS).task;}
    void describe(StringBuilder log,String label,String text){log.append("\n=== ").append(label).append(" ===\n").append(text).append('\n');try{List<MultiEventParser.Candidate> list=MultiEventParser.extract(text,LocalDateTime.now(),TimeParser.DEFAULTS);log.append("candidates=").append(list.size()).append('\n');Task t=parse(text);log.append("title=").append(t.title).append("\ndate=").append(t.date).append("\ntime=").append(t.time).append("\nend=").append(t.endTime).append("\nremind=").append(t.remind).append('\n');}catch(RuntimeException e){log.append("PARSE ERROR ").append(e).append('\n');}}
    @Override public void onStart(){Bundle results=new Bundle();Context c=getTargetContext();List<Uri> cleanup=new ArrayList<>();int outcome=Activity.RESULT_OK;try{
        int count;try(Store store=new Store(c)){count=store.all().size();}File photo=new File(c.getExternalFilesDir(null),"ticket-user-photo.jpg");check(photo.isFile(),"User test image staged");StringBuilder log=new StringBuilder("Device time="+LocalDateTime.now()+"\n");
        String whole=read(c,Uri.fromFile(photo),false);describe(log,"WHOLE STANDARD",whole);String enhanced=read(c,Uri.fromFile(photo),true);describe(log,"WHOLE ENHANCED",enhanced);
        Bitmap image=BitmapFactory.decodeFile(photo.toString());int w=image.getWidth(),h=image.getHeight();Bitmap crop=Bitmap.createBitmap(image,Math.round(w*.375f),Math.round(h*.887f),Math.round(w*.32f),Math.round(h*.035f));Uri uri=save(c,png(crop),"image/png",cleanup);try(FileOutputStream out=new FileOutputStream(new File(c.getExternalFilesDir(null),"ticket-crop-proof.png"))){crop.compress(Bitmap.CompressFormat.PNG,100,out);}String cropped=read(c,uri,false);describe(log,"CROP STANDARD",cropped);String improved=read(c,uri,true);describe(log,"CROP ENHANCED",improved);crop.recycle();image.recycle();
        startActivitySync(new Intent(c,MainActivity.class).setAction(OcrActivity.CREATE).putExtra(OcrActivity.TEXT,cropped).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));waitForIdleSync();check(waitText("新建事项")||has("选择要生成的事项"),"Cropped OCR opens the real task review");capture(c,"ticket-task-proof.png");if(has("新建事项"))click("取消");else click("取消");try(Store store=new Store(c)){check(store.all().size()==count,"Testing does not save tasks or reminders");}
        try(FileWriter out=new FileWriter(new File(c.getExternalFilesDir(null),"ticket-test-report.txt"))){out.write(log.toString());}results.putString("result","PASS: "+checks+" full image, crop, standard/enhanced OCR and real task review checks");
    }catch(Throwable e){StringWriter trace=new StringWriter();e.printStackTrace(new PrintWriter(trace));results.putString("error",trace.toString());outcome=Activity.RESULT_CANCELED;}finally{for(Uri u:cleanup)try{c.getContentResolver().delete(u,null,null);}catch(RuntimeException ignored){}}finish(outcome,results);}
}
