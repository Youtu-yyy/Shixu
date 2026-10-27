package cn.shixu.app.test;

import android.app.*;
import android.content.*;
import android.os.*;
import android.service.notification.StatusBarNotification;
import cn.shixu.app.*;
import java.time.*;

/** Runs without starting MainActivity, so verification cannot trigger overdue recovery. */
public class BackgroundReminderInstrumentation extends Instrumentation {
    String mode;long observedId,observedAt;
    static final String TITLE="自动测试进程退出提醒";
    @Override public void onCreate(Bundle arguments){super.onCreate(arguments);mode=arguments.getString("mode","verify");observedId=Long.parseLong(arguments.getString("observed_id","0"));observedAt=Long.parseLong(arguments.getString("observed_at","0"));start();}
    @Override public void onStart(){Bundle result=new Bundle();Context c=getTargetContext();android.content.SharedPreferences probe=c.getSharedPreferences("background_probe",Context.MODE_PRIVATE);
        try(Store store=new Store(c)){
            if(mode.equals("prepare")){
                if(!Reminders.exactAllowed(c)||!Reminders.notificationsAllowed(c))throw new AssertionError("Notification and exact alarm permissions required for this probe");
                long old=probe.getLong("id",0);Task previous=store.find(old);if(previous!=null&&previous.title.equals(TITLE)){Reminders.cancel(c,previous);store.delete(old);}
                Task t=new Task();LocalDateTime target=LocalDateTime.now().plusSeconds(40).withNano(0);t.title=TITLE;t.date=target.toLocalDate().toString();t.time=target.toLocalTime().toString();t.remind=true;t.lead=0;store.save(t);Reminders.schedule(c,t);probe.edit().putLong("id",t.id).putLong("at",t.triggerMillis()).commit();
                result.putString("result","PASS: scheduled process-death probe id="+t.id+", at="+Instant.ofEpochMilli(t.triggerMillis()));
            }else if(mode.equals("cleanup")){
                Task t=store.find(probe.getLong("id",0));if(t!=null&&t.title.equals(TITLE)){Reminders.cancel(c,t);store.delete(t.id);}probe.edit().clear().commit();result.putString("result","PASS: probe cleaned up");
            }else{
                long id=probe.getLong("id",0),at=probe.getLong("at",0);Task t=store.find(id);StatusBarNotification received=null;
                for(StatusBarNotification n:c.getSystemService(NotificationManager.class).getActiveNotifications())if(n.getId()==(int)id)received=n;
                if(t==null||!t.title.equals(TITLE)||t.lastNotified!=at||(received==null&&(observedId!=id||observedAt<=0)))throw new AssertionError("Alarm did not deliver without opening MainActivity: recorded="+(t==null?-1:t.lastNotified)+", expected="+at+", notification="+(received!=null));
                long deliveredAt=observedId==id&&observedAt>0?observedAt:received.getNotification().extras.getLong(Reminders.DELIVERED_AT,received.getPostTime());long delay=deliveredAt-at;if(delay< -1000||delay>15000)throw new AssertionError("Reminder delay outside tolerance: "+delay+"ms");
                result.putString("result","PASS: process-death and forced-idle reminder delivered without opening app; scheduled="+Instant.ofEpochMilli(at)+", delivered="+Instant.ofEpochMilli(deliveredAt)+", delayMs="+delay);
            }
            finish(Activity.RESULT_OK,result);
        }catch(Throwable error){result.putString("error",android.util.Log.getStackTraceString(error));finish(Activity.RESULT_CANCELED,result);}
    }
}
