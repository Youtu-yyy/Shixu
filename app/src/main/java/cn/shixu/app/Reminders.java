package cn.shixu.app;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.Build;
import android.provider.Settings;
import android.net.Uri;
import java.util.*;

public class Reminders {
    static final String ALERT_CHANNEL="shixu_continuous_alerts_v1";
    public static final String DELIVERED_AT="cn.shixu.app.deliveredAt";
    static String alertChannel(Context c){return ALERT_CHANNEL;}
    public static boolean notificationsAllowed(Context c){return (Build.VERSION.SDK_INT<33||c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED)&&c.getSystemService(NotificationManager.class).areNotificationsEnabled();}
    public static boolean exactAllowed(Context c){return Build.VERSION.SDK_INT<31||c.getSystemService(AlarmManager.class).canScheduleExactAlarms();}
    static void channel(Context c){
        NotificationManager manager=c.getSystemService(NotificationManager.class);
        // Keep the live ID so existing system permissions and user choices survive upgrades.
        NotificationChannel channel=new NotificationChannel(ALERT_CHANNEL,"事项提醒",NotificationManager.IMPORTANCE_HIGH);
        channel.setDescription("事项消息与横幅通知；响铃、振动及持续时间在拾序设置中调整");channel.setSound(null,null);channel.enableVibration(false);manager.createNotificationChannel(channel);
        manager.deleteNotificationChannel("shixu_reminders_v1");
    }
    static PendingIntent fireIntent(Context c,Task t,long at){Intent i=new Intent(c,ReminderReceiver.class).setAction("FIRE").putExtra("id",t.id).putExtra("base",t.startMillis()).putExtra("lead",t.lead).putExtra("at",at);return PendingIntent.getBroadcast(c,(int)t.id,i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);}
    public static void cancel(Context c,Task t){ReminderAlertService.stopTask(c,t.id,false);c.getSystemService(AlarmManager.class).cancel(fireIntent(c,t,0));c.getSystemService(NotificationManager.class).cancel((int)t.id);}
    static void alarm(Context c,Task t,long at){
        AlarmManager am=c.getSystemService(AlarmManager.class);PendingIntent pi=fireIntent(c,t,at);
        try{if(exactAllowed(c))am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,at,pi);else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,at,pi);}catch(SecurityException e){am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,at,pi);}
    }
    public static void schedule(Context c,Task t){
        c.getSystemService(AlarmManager.class).cancel(fireIntent(c,t,0));
        if(!t.remind||t.done||t.date.isEmpty())return;
        long now=System.currentTimeMillis(),at=t.triggerMillis();Task occurrence=t.copy();
        if(t.snoozeUntil>0){alarm(c,t,Math.max(now+1500,t.snoozeUntil));return;}
        if(!t.repeat.equals("不重复")){occurrence.advancePast(Math.max(now,t.lastNotified));at=occurrence.triggerMillis();}
        else if(t.lastNotified>=at)return;
        if(at<=now){if(now-at>48*3600_000L)return;at=now+1500;}
        alarm(c,t,at);
    }
    public static void restore(Context c){channel(c);try(Store store=new Store(c)){for(Task t:store.all())schedule(c,t);}}
    static PendingIntent action(Context c,Task t,Task display,String action,int code){Intent i=new Intent(c,ReminderReceiver.class).setAction(action).putExtra("id",t.id).putExtra("base",t.startMillis()).putExtra("eventStart",display.startMillis());return PendingIntent.getBroadcast(c,code,i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);}
    public static void notify(Context c,Task t){notify(c,t,t);}
    public static void notify(Context c,Task t,Task identity){
        if(!notificationsAllowed(c))return;channel(c);
        long deliveredAt=System.currentTimeMillis();Notification n=notification(c,t,identity,false);n.flags&=~Notification.FLAG_ONLY_ALERT_ONCE;n.extras.putLong(DELIVERED_AT,deliveredAt);n.when=deliveredAt;c.getSystemService(NotificationManager.class).notify((int)t.id,n);
        ReminderAlertService.start(c,t,identity,deliveredAt);
    }
    static Notification notification(Context c,Task t,Task identity,boolean alerting){
        channel(c);
        Intent open=new Intent(c,MainActivity.class).putExtra("taskId",t.id).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent content=PendingIntent.getActivity(c,(int)t.id,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        String when=t.dateLabel()+(t.location.isEmpty()?"":" · "+t.location);
        Notification.Builder builder=new Notification.Builder(c,alertChannel(c)).setSmallIcon(R.drawable.ic_notification).setColor(0xff4561ee).setContentTitle(t.title).setContentText(alerting?"持续提醒中 · "+when:when).setStyle(new Notification.BigTextStyle().bigText(when+(t.note.isEmpty()?"":"\n"+t.note))).setCategory(alerting?Notification.CATEGORY_ALARM:Notification.CATEGORY_REMINDER).setAutoCancel(!alerting).setOnlyAlertOnce(true).setContentIntent(content).setDeleteIntent(action(c,identity,t,"DISMISS_ALERT",(int)t.id)).addAction(new Notification.Action.Builder(null,"完成",action(c,identity,t,"COMPLETE",(int)t.id)).build()).addAction(new Notification.Action.Builder(null,"10分钟后再提醒",action(c,identity,t,"SNOOZE",(int)t.id)).build());
        if(alerting){builder.addAction(new Notification.Action.Builder(null,"停止提醒",action(c,identity,t,"STOP_ALERT",(int)t.id)).build());if(Build.VERSION.SDK_INT>=31)builder.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE);}
        return builder.build();
    }
    public static void openExact(Activity a){if(Build.VERSION.SDK_INT>=31)a.startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,Uri.parse("package:"+a.getPackageName())));}
}
