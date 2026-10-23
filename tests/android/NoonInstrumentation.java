package cn.shixu.app.test;
import android.app.*;
import android.content.*;
import android.os.*;
import android.view.accessibility.AccessibilityNodeInfo;
import cn.shixu.app.*;
import java.time.*;
import java.util.*;

public final class NoonInstrumentation extends SmokeInstrumentation {
    String preview(){for(AccessibilityNodeInfo n:nodes())if(n.getText()!=null&&n.getText().toString().startsWith("已识别："))return n.getText().toString();return "";}
    boolean waitPreview(String text){for(int n=0;n<16;n++){if(preview().contains(text))return true;pause();}return false;}
    @Override public void onStart(){Context c=getTargetContext();Bundle result=new Bundle();int code=Activity.RESULT_OK;Set<Long> baseline=new HashSet<>();String marker="自动测试午间钟点";try{
        try(Store store=new Store(c)){for(Task t:store.all())baseline.add(t.id);}
        startActivitySync(new Intent(c,MainActivity.class).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));waitForIdleSync();check(await("事项清单"),"Home ready");
        setEdit(1,"明天中午11点去吃饭");check(waitPreview("11:00"),"Quick preview shows 11:00");check(preview().contains("11:00")&&!preview().contains("23:00"),"Reported input no longer becomes 23:00");capture(c,"noon-preview.png");
        click("＋");check(await("新建事项")&&has("11:00"),"Same correct time reaches editor");setEdit(0,marker);showReminder();click("保存事项");check(await("事项清单"),"Task saves");
        try(Store store=new Store(c)){Task saved=null;for(Task t:store.all())if(!baseline.contains(t.id)&&t.title.equals(marker))saved=t;check(saved!=null&&saved.time.equals("11:00"),"SQLite stores 11:00");check(saved.date.equals(LocalDate.now().plusDays(1).toString()),"Tomorrow date preserved");check(Instant.ofEpochMilli(saved.triggerMillis()).atZone(ZoneId.systemDefault()).toLocalTime().equals(LocalTime.of(10,30)),"Default reminder is tomorrow 10:30");}
        setEdit(1,"明天中午11点到1点吃饭");check(waitPreview("13:00"),"Range preview includes 13:00 endpoint");check(preview().contains("11:00")&&preview().contains("13:00"),"Noon range spans 11:00 to 13:00");
        click("＋");check(await("新建事项")&&has("13:00"),"Range reaches shared editor");click("取消");
        setEdit(1,"明天晚上11点整理书架");check(waitPreview("23:00"),"Evening eleven remains 23:00");check(preview().contains("23:00"),"Evening interpretation unaffected");setEdit(1,"");
        result.putString("result","PASS: "+checks+" noon quick preview, shared editor, persisted time, reminder, interval and evening checks");
    }catch(Throwable e){try{capture(c,"noon-failure.png");}catch(Exception ignored){}code=Activity.RESULT_CANCELED;result.putString("error",android.util.Log.getStackTraceString(e));}finally{try(Store store=new Store(c)){for(Task t:store.all())if(!baseline.contains(t.id)&&t.title.equals(marker)){Reminders.cancel(c,t);store.delete(t.id);}}}finish(code,result);}
}
