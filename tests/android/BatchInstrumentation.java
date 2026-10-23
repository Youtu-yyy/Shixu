package cn.shixu.app.test;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.net.Uri;
import android.os.*;
import cn.shixu.app.*;
import java.io.*;
import java.time.*;
import java.util.*;

public class BatchInstrumentation extends OcrInstrumentation {
    Bitmap table(){Bitmap image=Bitmap.createBitmap(1400,1000,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(image);canvas.drawColor(Color.WHITE);Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);p.setColor(Color.BLACK);p.setTextSize(32);String[][] rows={{"时间","内容","主讲人","地点"},{"10月24日9:30","阅读分享","陈老师","A101"},{"10月24日10:30","办公技能培训","林老师","A202"},{"10月25日14:00","素质拓展","周老师","校园广场"},{"11月1日20:00","结营考试","赵老师","A106"}};int[] x={35,420,880,1170};for(int r=0;r<rows.length;r++)for(int col=0;col<4;col++)canvas.drawText(rows[r][col],x[col],100+r*170,p);return image;}
    Activity launch(Context c,String source){Activity a=startActivitySync(new Intent(c,BatchImportActivity.class).putExtra("source",source).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK));waitForIdleSync();check(waitText("选择要生成的事项"),"Candidate selection page opens");return a;}
    @Override public void onStart(){Bundle results=new Bundle();Context c=getTargetContext();List<Uri> cleanup=new ArrayList<>();Set<Long> before=new HashSet<>();int outcome=Activity.RESULT_OK;
        String marker="多事项虚构测试";try{
            try(Store store=new Store(c)){for(Task t:store.all())before.add(t.id);}
            Bitmap image=table();Uri uri=save(c,png(image),"image/png",cleanup);image.recycle();String recognized=recognize(c,uri,true);try(FileWriter log=new FileWriter(new File(c.getExternalFilesDir(null),"batch-ocr-table.txt"))){log.write(recognized);}
            List<MultiEventParser.Candidate> extracted=MultiEventParser.extract(recognized,LocalDateTime.now(),TimeParser.DEFAULTS);check(extracted.size()==4,"Real OCR yields four schedule candidates");check(extracted.get(0).task.location.equals("A101")&&extracted.get(1).task.location.equals("A202"),"OCR table row locations remain aligned");check(extracted.get(0).task.time.equals("09:30")&&extracted.get(1).task.time.equals("10:30"),"Repeated dates retain distinct start times");
            // The supplied photo is only exercised privately when explicitly staged by the host.
            File supplied=new File(c.getExternalFilesDir(null),"multi-user-table.jpg");if(supplied.isFile()){String text=recognize(c,Uri.fromFile(supplied),true);try(FileWriter log=new FileWriter(new File(c.getExternalFilesDir(null),"batch-private-photo-result.txt"))){log.write(text);}check(MultiEventParser.extract(text,LocalDateTime.now(),TimeParser.DEFAULTS).size()==8,"Supplied photographed schedule yields eight rows");}
            String source="2030年11月7日14:00 | "+marker+"阅读交流 | 地点：A101\n2030年11月8日15:00-16:00 | "+marker+"电脑培训 | 地点：A202\n2030年11月9日10:00 | "+marker+"成果分享 | 地点：A303";
            startActivitySync(new Intent(c,MainActivity.class).setAction(OcrActivity.CREATE).putExtra(OcrActivity.TEXT,source).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));waitForIdleSync();check(waitText("选择要生成的事项"),"OCR and text creation route to batch review");check(has("已选 3 / 3 项"),"All detected events initially selected");capture(c,"batch-selection-proof.png");try(Store store=new Store(c)){check(store.all().size()==before.size(),"No events are saved before confirmation");}click("取消");try(Store store=new Store(c)){check(store.all().size()==before.size(),"Cancelling batch preserves records");}
            Activity draft=launch(c,source);click("清空选择");check(has("已选 0 / 3 项"),"Selection can be cleared");clickDescription("选择第1项");clickDescription("选择第3项");check(has("生成所选 2 项"),"Nonadjacent selection updates save count");clickDescription("修改第1项");check(waitText("编辑事项"),"Each candidate can be edited");setEdit(0,marker+"修改后的阅读交流");click("保留修改");check(waitText(marker+"修改后的阅读交流"),"Edited draft appears in review");runOnMainSync(draft::recreate);waitForIdleSync();check(waitText(marker+"修改后的阅读交流")&&has("生成所选 2 项"),"Draft edits and selection survive activity recreation");click("生成所选 2 项");check(waitText("事项清单"),"Saving batch returns home");
            try(Store store=new Store(c)){List<Task> added=new ArrayList<>();for(Task t:store.all())if(!before.contains(t.id))added.add(t);check(added.size()==2,"Only the two selected tasks are stored");check(added.stream().anyMatch(t->t.title.equals(marker+"修改后的阅读交流")),"Candidate edits persist");check(added.stream().noneMatch(t->t.title.contains("电脑培训")),"Unselected event is not stored");check(added.stream().allMatch(t->t.note.equals(source)&&t.priority==2&&t.lead==30&&t.remind),"Original source, low priority and reminder defaults persist");}
            launch(c,source);click("清空选择");clickDescription("选择第2项");click("生成所选 1 项");check(waitText("事项清单"),"Single selection is also supported");try(Store store=new Store(c)){check(store.all().size()==before.size()+3,"Single selection creates precisely one additional task");}
            // A mid-batch failure must not leave a partial import behind.
            int count;try(Store store=new Store(c)){count=store.all().size();}Task a=new Task();a.title=marker+"回滚A";Task b=new Task();b.title=marker+"回滚B";try(Store fail=new Store(c){int writes;@Override public void save(Task t){if(++writes==2)throw new IllegalStateException("Injected batch failure");super.save(t);}}){try{fail.saveBatch(Arrays.asList(a,b));throw new AssertionError("Expected failure");}catch(IllegalStateException expected){}}try(Store store=new Store(c)){check(store.all().size()==count&&a.id==0&&b.id==0,"Database transaction rolls back a partial batch");}
            results.putString("result","PASS: "+checks+" OCR table rows, selection, edits, single/multiple saves, source preservation and transaction checks");
        }catch(Throwable error){StringWriter trace=new StringWriter();error.printStackTrace(new PrintWriter(trace));results.putString("error",trace.toString());outcome=Activity.RESULT_CANCELED;}finally{
            try(Store store=new Store(c)){for(Task t:store.all())if(!before.contains(t.id)&&t.title.startsWith(marker)){Reminders.cancel(c,t);store.delete(t.id);}}for(Uri uri:cleanup)try{c.getContentResolver().delete(uri,null,null);}catch(RuntimeException ignored){}
        }finish(outcome,results);
    }
}
