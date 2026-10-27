package cn.shixu.app.test;
import android.app.*;
import android.content.*;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.*;
import cn.shixu.app.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

/** Optional private fixture; never package the user's photo inside the application. */
public final class NoticePhotoInstrumentation extends OcrInstrumentation {
    boolean probe;
    @Override public void onCreate(Bundle args){probe="true".equals(args.getString("probe"));super.onCreate(args);}
    String read(Context c,Uri uri,boolean enhanced)throws Exception{
        CountDownLatch ready=new CountDownLatch(1);String[] text={null};
        OcrEngine.recognize(c,uri,enhanced,new OcrEngine.Listener(){public void preview(Bitmap b){}public void text(String s){text[0]=s;ready.countDown();}public void error(String e){text[0]="ERROR:"+e;ready.countDown();}});
        check(ready.await(50,TimeUnit.SECONDS),"Private image OCR finishes");check(text[0]!=null&&!text[0].startsWith("ERROR:"),"Private image returns text");return text[0];
    }
    @Override public void onStart(){Context c=getTargetContext();Bundle result=new Bundle();int code=Activity.RESULT_OK;List<Uri> cleanup=new ArrayList<>();try{
        File fixture=new File(c.getExternalFilesDir(null),"notice-user-photo.jpg");check(fixture.isFile(),"Private notice fixture supplied");Uri uri=save(c,java.nio.file.Files.readAllBytes(fixture.toPath()),"image/jpeg",cleanup);
        String raw=read(c,uri,false);try(FileOutputStream out=new FileOutputStream(new File(c.getExternalFilesDir(null),"notice-photo-ocr.txt"))){out.write(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8));}
        if(probe){result.putString("ocr",raw);result.putString("result","PASS: OCR probe");}
        else{
            Task t=NotificationExtractor.extract(raw,java.time.LocalDateTime.now(),TimeParser.DEFAULTS).task;
            check(t.location.equals("BLD508"),"Actual image yields BLD508 location");check(t.time.equals("13:30")&&t.endTime.equals("14:15"),"Actual image yields complete interval");check(t.date.endsWith("10-27"),"Explicit date retained");check(t.title.equals("参加第一次小组会议"),"Parenthetical explanation is excluded from title");check(t.note.equals(raw),"Recognized original text is retained in notes");
            int baseline;try(Store store=new Store(c)){baseline=store.all().size();}
            startActivitySync(new Intent(c,MainActivity.class).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));waitForIdleSync();check(waitText("事项清单"),"Home ready");clickDescription("从图片识别事项");check(waitText("图片识别"),"OCR page ready");choose(uri);check(waitText("识别完成，请核对文字"),"Private image recognized through picker");clickDescription("用识别文字生成事项");check(waitText("新建事项")&&has("参加第一次小组会议")&&has("BLD508"),"Image proceeds to original shared editor");check(has("13:30—14:15"),"Editor shows full interval");try(Store store=new Store(c)){check(store.all().size()==baseline,"Image recognition does not auto-save");}click("取消");result.putString("result","PASS: "+checks+" private notice OCR, location, title, date, interval and editor checks");
        }
    }catch(Throwable e){code=Activity.RESULT_CANCELED;result.putString("error",android.util.Log.getStackTraceString(e));}finally{for(Uri uri:cleanup)try{c.getContentResolver().delete(uri,null,null);}catch(Exception ignored){}}finish(code,result);}
}
