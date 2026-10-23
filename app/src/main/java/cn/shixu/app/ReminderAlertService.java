package cn.shixu.app;

import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.media.*;
import android.os.*;

/** A bounded, user-visible alarm. Never restart a ringing reminder after process death. */
public final class ReminderAlertService extends Service {
    private static volatile ReminderAlertService active;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private Task display,identity;
    private MediaPlayer player;
    private Vibrator vibrator;
    private PowerManager.WakeLock wake;
    private AudioManager audio;
    private AudioFocusRequest focus;
    private boolean foreground;
    private long deliveredAt;
    public static boolean sound(Context c){return c.getSharedPreferences("settings",0).getBoolean("alert_sound",false);}
    public static boolean vibration(Context c){return c.getSharedPreferences("settings",0).getBoolean("alert_vibration",true);}
    public static int seconds(Context c){int n=c.getSharedPreferences("settings",0).getInt("alert_seconds",30);return n==2||n==5||n==15||n==30?n:30;}
    public static boolean isAlerting(long id){ReminderAlertService s=active;return s!=null&&s.identity!=null&&s.identity.id==id;}
    public static void stopTask(Context c,long id,boolean keep){ReminderAlertService s=active;if(s!=null)s.handler.post(()->{if(s.identity!=null&&s.identity.id==id)s.finishAlert(keep);});}
    public static void stopAll(){ReminderAlertService s=active;if(s!=null)s.handler.post(()->s.finishAlert(true));}
    public static void start(Context c,Task display,Task identity,long deliveredAt){
        if((!sound(c)&&!vibration(c))||!Reminders.notificationsAllowed(c))return;
        try{c.startForegroundService(new Intent(c,ReminderAlertService.class).putExtra("id",identity.id).putExtra("base",identity.startMillis()).putExtra("notified",identity.lastNotified).putExtra("trigger",identity.triggerMillis()).putExtra("display",display).putExtra("deliveredAt",deliveredAt));}
        catch(RuntimeException e){android.util.Log.w("ShixuAlert","Continuous alert unavailable; ordinary notification retained",e);}
    }
    @Override public void onCreate(){super.onCreate();active=this;}
    @Override public IBinder onBind(Intent i){return null;}
    @Override public int onStartCommand(Intent i,int flags,int startId){
        Task current=null;Task shown=i==null?null:(Task)i.getSerializableExtra("display");
        if(i!=null)try(Store store=new Store(this)){current=store.find(i.getLongExtra("id",0));}
        if(current==null||shown==null||!current.remind||current.done||current.snoozeUntil>0||(!sound(this)&&!vibration(this))||!Reminders.notificationsAllowed(this)||current.startMillis()!=i.getLongExtra("base",-1)||current.lastNotified!=i.getLongExtra("notified",-1)||current.triggerMillis()!=i.getLongExtra("trigger",-1)){
            // Satisfy startForegroundService's deadline even if the task changed before delivery.
            if(!foreground){if(shown!=null){startForeground((int)shown.id,Reminders.notification(this,shown,shown,true));stopForeground(STOP_FOREGROUND_REMOVE);}stopSelf(startId);}return START_NOT_STICKY;
        }
        if(foreground)detach(true);
        identity=current;display=shown;deliveredAt=i.getLongExtra("deliveredAt",System.currentTimeMillis());
        try{
            Notification n=Reminders.notification(this,display,identity,true);
            n.extras.putLong(Reminders.DELIVERED_AT,deliveredAt);n.when=deliveredAt;
            if(Build.VERSION.SDK_INT>=29)startForeground((int)identity.id,n,ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);else startForeground((int)identity.id,n);
            foreground=true;
            NotificationChannel channel=getSystemService(NotificationManager.class).getNotificationChannel(Reminders.alertChannel(this));
            if(channel==null||channel.getImportance()==NotificationManager.IMPORTANCE_NONE){finishAlert(false);return START_NOT_STICKY;}
            long duration=seconds(this)*1000L;
            wake=getSystemService(PowerManager.class).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"Shixu:ReminderAlert");wake.acquire(duration+5000);
            AudioAttributes attrs=new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build();
            vibrator=Build.VERSION.SDK_INT>=31?getSystemService(VibratorManager.class).getDefaultVibrator():getSystemService(Vibrator.class);
            if(vibration(this)&&vibrator!=null&&vibrator.hasVibrator()){
                int strong=VibrationEffect.DEFAULT_AMPLITUDE;
                vibrator.vibrate(VibrationEffect.createWaveform(new long[]{0,650,300,650,700},new int[]{0,strong,0,strong,0},0),attrs);
            }
            if(sound(this))play(attrs);
            handler.postDelayed(()->finishAlert(true),duration);
        }catch(RuntimeException e){android.util.Log.w("ShixuAlert","Alert output unavailable",e);finishAlert(true);}
        return START_NOT_STICKY;
    }
    private void play(AudioAttributes attrs){
        audio=getSystemService(AudioManager.class);
        focus=new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK).setAudioAttributes(attrs).setOnAudioFocusChangeListener(change->{if(change==AudioManager.AUDIOFOCUS_LOSS||change==AudioManager.AUDIOFOCUS_LOSS_TRANSIENT)finishAlert(true);},handler).build();
        if(audio.requestAudioFocus(focus)!=AudioManager.AUDIOFOCUS_REQUEST_GRANTED)return;
        android.net.Uri sound=RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);if(sound==null)sound=RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        if(sound==null)return;
        try{
            MediaPlayer p=new MediaPlayer();player=p;p.setAudioAttributes(attrs);p.setDataSource(this,sound);p.setLooping(true);
            p.setOnPreparedListener(ready->{if(player==ready&&foreground)ready.start();});
            p.setOnErrorListener((broken,what,extra)->{if(player==broken){broken.release();player=null;}return true;});p.prepareAsync();
        }catch(java.io.IOException|RuntimeException e){if(player!=null){player.release();player=null;}android.util.Log.w("ShixuAlert","Alarm sound unavailable; vibration continues",e);}
    }
    private void detach(boolean keep){
        handler.removeCallbacksAndMessages(null);
        if(player!=null){player.release();player=null;}
        if(vibrator!=null){vibrator.cancel();vibrator=null;}
        if(audio!=null&&focus!=null){audio.abandonAudioFocusRequest(focus);focus=null;}
        if(wake!=null){if(wake.isHeld())wake.release();wake=null;}
        if(foreground){stopForeground(keep?STOP_FOREGROUND_DETACH:STOP_FOREGROUND_REMOVE);foreground=false;}
        if(keep&&identity!=null&&display!=null)try(Store store=new Store(this)){
            Task current=store.find(identity.id);
            if(current!=null&&current.remind&&!current.done&&current.snoozeUntil==0&&current.startMillis()==identity.startMillis()){
                Notification n=Reminders.notification(this,display,current,false);n.extras.putLong(Reminders.DELIVERED_AT,deliveredAt);n.when=deliveredAt;getSystemService(NotificationManager.class).notify((int)current.id,n);
            }else getSystemService(NotificationManager.class).cancel((int)identity.id);
        }
        identity=null;display=null;
    }
    private void finishAlert(boolean keep){detach(keep);stopSelf();}
    @Override public void onDestroy(){detach(false);if(active==this)active=null;super.onDestroy();}
}
