package cn.shixu.app;
import android.app.NotificationManager;
import android.content.*;
public class ReminderReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c,Intent i){
        try(Store store=new Store(c)){
            Task t=store.find(i.getLongExtra("id",0));if(t==null||t.done||i.getLongExtra("base",-1)!=t.startMillis())return;
            String action=i.getAction();
            if("STOP_ALERT".equals(action)){ReminderAlertService.stopTask(c,t.id,true);return;}
            if("DISMISS_ALERT".equals(action)){ReminderAlertService.stopTask(c,t.id,false);return;}
            if("COMPLETE".equals(action)){Reminders.cancel(c,t);store.complete(t);Reminders.schedule(c,t);return;}
            if("SNOOZE".equals(action)){ReminderAlertService.stopTask(c,t.id,false);c.getSystemService(NotificationManager.class).cancel((int)t.id);t.snooze(System.currentTimeMillis(),i.getLongExtra("eventStart",t.startMillis()));store.save(t);Reminders.schedule(c,t);return;}
            if(!t.remind||i.getIntExtra("lead",-1)!=t.lead)return;
            long at=i.getLongExtra("at",0);if(at<=t.lastNotified)return;
            t.lastNotified=at;t.snoozeUntil=0;if(t.reminderOverrideMillis>0&&at!=t.reminderOverrideMillis)t.clearSnooze();store.save(t);Task display=t.copy();display.date=t.reminderOverrideMillis>0?java.time.Instant.ofEpochMilli(t.reminderOverrideStart>0?t.reminderOverrideStart:t.startMillis()).atZone(java.time.ZoneId.systemDefault()).toLocalDate().toString():!t.reminderPeriodTime.isEmpty()?java.time.Instant.ofEpochMilli(at).atZone(java.time.ZoneId.systemDefault()).toLocalDate().minusDays(t.reminderPeriodDayOffset).toString():t.reminderEveningTime.isEmpty()?java.time.Instant.ofEpochMilli(at+t.lead*60_000L).atZone(java.time.ZoneId.systemDefault()).toLocalDate().toString():java.time.Instant.ofEpochMilli(at).atZone(java.time.ZoneId.systemDefault()).toLocalDate().plusDays(1).toString();Reminders.notify(c,display,t);
            // Scheduling the next occurrence does not move the unfinished item in the list.
            if(!t.repeat.equals("不重复"))Reminders.schedule(c,t);
        }
    }
}
