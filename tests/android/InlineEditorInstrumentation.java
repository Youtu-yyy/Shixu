package cn.shixu.app.test;
import android.app.*;
import android.content.*;
import android.os.*;
import android.view.accessibility.AccessibilityNodeInfo;
import cn.shixu.app.*;
import java.io.*;
import java.time.*;
import java.util.*;

public class InlineEditorInstrumentation extends SmokeInstrumentation {
    boolean reminderOn(){for(AccessibilityNodeInfo n:nodes())if(n.getContentDescription()!=null&&"开启或关闭事项提醒".contentEquals(n.getContentDescription()))return n.isChecked();throw new AssertionError("Reminder switch absent");}
    void open(Context c,String input){startActivitySync(new Intent(c,MainActivity.class).setAction(OcrActivity.CREATE).putExtra(OcrActivity.TEXT,input).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));waitForIdleSync();check(await("新建事项"),"Main shared editor opens");}
    @Override public void onStart(){Context c=getTargetContext();Bundle results=new Bundle();Set<Long> baseline=new HashSet<>();String prefix="自动测试内联";int outcome=Activity.RESULT_OK;try{
        try(Store store=new Store(c)){for(Task t:store.all())baseline.add(t.id);}
        open(c,"明天下午14:00-16:00"+prefix+"阅读交流");check(!has("下一步：提醒")&&has("保存事项"),"Task saves directly without separate reminder dialog");capture(c,"inline-editor-top.png");showReminder();check(reminderOn()&&has("提醒时间"),"Enabled reminder shows inline time controls");capture(c,"inline-editor-reminder.png");clickDescription("开启或关闭事项提醒");check(!reminderOn()&&!has("提醒时间"),"Turning reminders off hides time settings");click("保存事项");check(await("事项清单"),"Disabled reminder task saves in one step");
        Task saved=null;try(Store store=new Store(c)){for(Task t:store.all())if(!baseline.contains(t.id)&&t.title.startsWith(prefix))saved=t;}check(saved!=null&&!saved.remind&&saved.time.equals("14:00")&&saved.endTime.equals("16:00"),"Ordinary task persists interval with reminders off");
        startActivitySync(new Intent(c,MainActivity.class).putExtra("taskId",saved.id).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK));waitForIdleSync();check(await("编辑事项"),"Existing task uses same editor");showReminder();check(!reminderOn(),"Existing off state persists");clickDescription("开启或关闭事项提醒");check(has("提醒时间"),"Enabling exposes time selector");clickDescription("选择提醒时间");click("提前1小时");click("保存事项");check(await("事项清单"),"Inline reminder saves directly");try(Store store=new Store(c)){Task loaded=store.find(saved.id);check(loaded.remind&&loaded.lead==60,"Selected reminder time persists");}
        startActivitySync(new Intent(c,MainActivity.class).putExtra("taskId",saved.id).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK));waitForIdleSync();check(await("编辑事项"),"Task reopens");showReminder();clickDescription("开启或关闭事项提醒");click("取消");try(Store store=new Store(c)){check(store.find(saved.id).remind,"Cancelling reminder edits leaves persisted task intact");}
        open(c,"明天中午前"+prefix+"归还图书");showReminder();check(has("前一时段")&&has("09:00"),"Deadline retains previous-period reminder inline");click("保存事项");check(await("事项清单"),"Deadline saves without reminder popup");
        String source="2030年11月7日14:00 | "+prefix+"候选一 | 地点：A101\n2030年11月8日15:00 | "+prefix+"候选二 | 地点：A202";int before;try(Store store=new Store(c)){before=store.all().size();}
        startActivitySync(new Intent(c,BatchImportActivity.class).putExtra("source",source).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK));waitForIdleSync();check(await("选择要生成的事项"),"Batch selection opens");clickDescription("修改第1项");check(await("编辑事项")&&has("事项标题 *")&&!has("日期（年-月-日）"),"Batch uses shared editor, not raw date fields");clickDescription("选择事项日期");check(!has("保留修改"),"Batch date opens native date picker");getUiAutomation().executeShellCommand("input keyevent 4").close();pause();showReminder();check(reminderOn()&&has("提醒时间"),"Batch reminder uses same inline controls");capture(c,"batch-shared-reminder.png");clickDescription("选择提醒时间");click("提前10分钟");click("保留修改");check(await("选择要生成的事项")&&has("提前10分钟提醒"),"Draft reminder updates candidate summary");try(Store store=new Store(c)){check(store.all().size()==before,"Editing candidate does not write a task");}click("取消");
        results.putString("result","PASS: "+checks+" shared editor, inline visibility, one-step save, cancel, deadline and batch draft checks");
    }catch(Throwable e){StringWriter trace=new StringWriter();e.printStackTrace(new PrintWriter(trace));results.putString("error",trace.toString());outcome=Activity.RESULT_CANCELED;}finally{try(Store store=new Store(c)){for(Task t:store.all())if(!baseline.contains(t.id)&&t.title.startsWith(prefix)){Reminders.cancel(c,t);store.delete(t.id);}}}finish(outcome,results);}
}
