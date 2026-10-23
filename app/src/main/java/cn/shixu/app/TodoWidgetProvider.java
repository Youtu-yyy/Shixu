package cn.shixu.app;

import android.app.*;
import android.appwidget.*;
import android.content.*;
import android.os.*;
import android.net.Uri;
import android.widget.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class TodoWidgetProvider extends AppWidgetProvider {
    static final java.util.concurrent.ExecutorService UPDATES=java.util.concurrent.Executors.newSingleThreadExecutor();
    /** Use the density-aware image consistently, including after upgrading from generated previews. */
    public static void preparePreview(Context c){
        android.content.SharedPreferences prefs=c.getSharedPreferences("widgets",Context.MODE_PRIVATE);
        if(prefs.getBoolean("sized_preview_v3",false))return;
        try{AppWidgetManager manager=AppWidgetManager.getInstance(c);ComponentName provider=new ComponentName(c,TodoWidgetProvider.class);if(Build.VERSION.SDK_INT>=35)manager.removeWidgetPreview(provider,AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN);if(Build.VERSION.SDK_INT>=28)manager.updateAppWidgetProviderInfo(provider,"cn.shixu.app.widget.preview.v3");prefs.edit().putBoolean("sized_preview_v3",true).remove("sample_preview_v1").apply();}
        catch(RuntimeException e){android.util.Log.w("ShixuWidget","Unable to clear previous widget preview",e);}
    }
    static final String REFRESH="cn.shixu.app.WIDGET_REFRESH",UPCOMING="cn.shixu.app.WIDGET_UPCOMING",ALL="cn.shixu.app.WIDGET_ALL",CHANGED="cn.shixu.app.WIDGET_DATA_CHANGED",CONFIRM="cn.shixu.app.WIDGET_CONFIRM",CANCEL="cn.shixu.app.WIDGET_CANCEL",BEGIN_CONFIRM="cn.shixu.app.WIDGET_BEGIN_CONFIRM";
    public static final String NEW_TASK="cn.shixu.app.WIDGET_NEW_TASK";
    static int range(Context c,int id){android.content.SharedPreferences preferences=c.getSharedPreferences("widgets",Context.MODE_PRIVATE);return preferences.getInt("range_"+id,WidgetAgenda.UPCOMING);}
    static void clearConfirmation(Context c,int id){c.getSharedPreferences("widgets",Context.MODE_PRIVATE).edit().remove("confirm_id_"+id).remove("confirm_start_"+id).remove("confirm_done_"+id).apply();}
    static Task confirmation(Context c,int id){android.content.SharedPreferences prefs=c.getSharedPreferences("widgets",Context.MODE_PRIVATE);long taskId=prefs.getLong("confirm_id_"+id,0);if(taskId==0)return null;try(Store store=new Store(c)){Task task=store.find(taskId);if(task!=null&&task.done==prefs.getBoolean("confirm_done_"+id,false)&&task.startMillis()==prefs.getLong("confirm_start_"+id,0))return task;}clearConfirmation(c,id);return null;}
    public static void beginConfirmation(Context c,int id,Task task){c.sendBroadcast(new Intent(c,TodoWidgetProvider.class).setAction(BEGIN_CONFIRM).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,id).putExtra("taskId",task.id).putExtra("expectedStart",task.startMillis()).putExtra("expectedDone",task.done));}
    public static void requestUpdate(Context c){
        if(AppWidgetManager.getInstance(c).getAppWidgetIds(new ComponentName(c,TodoWidgetProvider.class)).length>0)c.sendBroadcast(new Intent(c,TodoWidgetProvider.class).setAction(CHANGED));
    }
    @Override public void onUpdate(Context c,AppWidgetManager manager,int[] ids){for(int id:ids)update(c,manager,id);}
    @Override public void onAppWidgetOptionsChanged(Context c,AppWidgetManager manager,int id,Bundle options){update(c,manager,id);}
    @Override public void onDeleted(Context c,int[] ids){android.content.SharedPreferences.Editor edit=c.getSharedPreferences("widgets",Context.MODE_PRIVATE).edit();for(int id:ids)edit.remove("all_"+id).remove("range_"+id).remove("page_"+id).remove("confirm_id_"+id).remove("confirm_start_"+id).remove("confirm_done_"+id);edit.apply();}
    @Override public void onReceive(Context c,Intent i){
        PendingResult result=goAsync();Context app=c.getApplicationContext();
        UPDATES.execute(()->{try{super.onReceive(app,i);handleAction(app,i);}catch(RuntimeException e){android.util.Log.e("ShixuWidget","Widget action failed: "+i.getAction(),e);}finally{result.finish();}});
    }
    void handleAction(Context c,Intent i){
        String action=i.getAction();
        if(BEGIN_CONFIRM.equals(action)){
            int id=i.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,AppWidgetManager.INVALID_APPWIDGET_ID);AppWidgetManager manager=AppWidgetManager.getInstance(c);AppWidgetProviderInfo info=manager.getAppWidgetInfo(id);if(info==null||!new ComponentName(c,TodoWidgetProvider.class).equals(info.provider))return;
            try(Store store=new Store(c)){Task task=store.find(i.getLongExtra("taskId",0));if(task==null||task.done!=i.getBooleanExtra("expectedDone",task.done)||task.startMillis()!=i.getLongExtra("expectedStart",-1))return;c.getSharedPreferences("widgets",Context.MODE_PRIVATE).edit().putLong("confirm_id_"+id,task.id).putLong("confirm_start_"+id,task.startMillis()).putBoolean("confirm_done_"+id,task.done).apply();}update(c,manager,id);
        }else if(CONFIRM.equals(action)||CANCEL.equals(action)){
            int id=i.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,AppWidgetManager.INVALID_APPWIDGET_ID);Task task=confirmation(c,id);clearConfirmation(c,id);
            if(CONFIRM.equals(action)&&task!=null){try(Store store=new Store(c)){Task current=store.find(task.id);if(current!=null&&current.done==task.done&&current.startMillis()==task.startMillis()){Reminders.cancel(c,current);store.complete(current);Reminders.schedule(c,current);}}}
            if(AppWidgetManager.getInstance(c).getAppWidgetInfo(id)!=null)update(c,AppWidgetManager.getInstance(c),id);
        }else if(UPCOMING.equals(action)||ALL.equals(action)){
            int id=i.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,AppWidgetManager.INVALID_APPWIDGET_ID);
            if(AppWidgetManager.getInstance(c).getAppWidgetInfo(id)==null)return;
            clearConfirmation(c,id);c.getSharedPreferences("widgets",Context.MODE_PRIVATE).edit().putInt("range_"+id,UPCOMING.equals(action)?WidgetAgenda.UPCOMING:WidgetAgenda.ALL).apply();update(c,AppWidgetManager.getInstance(c),id);
        }else if(REFRESH.equals(action)||CHANGED.equals(action)||Intent.ACTION_DATE_CHANGED.equals(action)||Intent.ACTION_TIME_CHANGED.equals(action)||Intent.ACTION_TIMEZONE_CHANGED.equals(action)||Intent.ACTION_BOOT_COMPLETED.equals(action)||Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)){
            if(Intent.ACTION_MY_PACKAGE_REPLACED.equals(action))preparePreview(c);
            AppWidgetManager manager=AppWidgetManager.getInstance(c);onUpdate(c,manager,manager.getAppWidgetIds(new ComponentName(c,TodoWidgetProvider.class)));
        }
    }
    static PendingIntent broadcast(Context c,int id,String action){return PendingIntent.getBroadcast(c,id,new Intent(c,TodoWidgetProvider.class).setAction(action).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,id),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);}
    static PendingIntent open(Context c,int id,String kind){Intent i=new Intent(c,MainActivity.class).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP).setData(Uri.parse("shixu://widget/"+id+"/"+kind));if(kind.equals("new"))i.setAction(NEW_TASK);return PendingIntent.getActivity(c,id,i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);}
    static List<Task> tasks(Context c,int id){try(Store store=new Store(c)){return WidgetAgenda.select(store.all(),LocalDate.now(),range(c,id));}}
    static WidgetSizing sizing(Context c,int id){Bundle options=AppWidgetManager.getInstance(c).getAppWidgetOptions(id);boolean landscape=c.getResources().getConfiguration().orientation==android.content.res.Configuration.ORIENTATION_LANDSCAPE;int minWidth=options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH,250),minHeight=options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT,250);return WidgetSizing.forSize(landscape?options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH,minWidth):minWidth,landscape?minHeight:options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT,minHeight));}
    static int pixels(Context c,int dp){return Math.round(dp*c.getResources().getDisplayMetrics().density);}
    public static RemoteViews row(Context c,Task task){
        return row(c,task,WidgetSizing.forSize(300,300));
    }
    public static RemoteViews row(Context c,Task task,WidgetSizing size){
        RemoteViews row=new RemoteViews(c.getPackageName(),R.layout.widget_task);
        row.setViewPadding(R.id.widget_task_root,pixels(c,size.rowPadding),pixels(c,size.rowPadding),pixels(c,size.rowPadding),pixels(c,size.rowPadding));
        row.setTextViewTextSize(R.id.widget_task_title,android.util.TypedValue.COMPLEX_UNIT_SP,size.title);row.setTextViewTextSize(R.id.widget_task_time,android.util.TypedValue.COMPLEX_UNIT_SP,size.detail);row.setTextViewTextSize(R.id.widget_task_location,android.util.TypedValue.COMPLEX_UNIT_SP,size.detail);
        row.setTextViewTextSize(R.id.widget_task_category,android.util.TypedValue.COMPLEX_UNIT_SP,size.tag);row.setTextViewTextSize(R.id.widget_task_priority,android.util.TypedValue.COMPLEX_UNIT_SP,size.tag);row.setInt(R.id.widget_task_category,"setMaxWidth",pixels(c,size.categoryWidth));row.setInt(R.id.widget_task_time,"setMaxLines",size.compact?1:2);
        android.text.SpannableString title=new android.text.SpannableString(task.title);if(task.done&&!task.title.isEmpty())title.setSpan(new android.text.style.StrikethroughSpan(),0,title.length(),android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);row.setTextViewText(R.id.widget_task_title,title);row.setInt(R.id.widget_task_title,"setPaintFlags",android.graphics.Paint.ANTI_ALIAS_FLAG|android.graphics.Paint.SUBPIXEL_TEXT_FLAG|(task.done?android.graphics.Paint.STRIKE_THRU_TEXT_FLAG:0));row.setTextColor(R.id.widget_task_title,task.done?0xff828aa0:0xff20283f);row.setTextViewText(R.id.widget_complete,task.done?"✓":"○");row.setTextColor(R.id.widget_complete,task.done?0xff278773:0xff828aa0);row.setContentDescription(R.id.widget_complete,(task.done?"恢复事项：":"完成事项：")+task.title);row.setOnClickFillInIntent(R.id.widget_complete,new Intent().putExtra("taskId",task.id).putExtra("widgetAction","complete").putExtra("expectedDone",task.done).putExtra("expectedStart",task.startMillis()));row.setTextViewText(R.id.widget_task_time,task.dateLabel());row.setTextViewText(R.id.widget_task_reminder,task.reminderLabel());row.setTextViewTextSize(R.id.widget_task_reminder,android.util.TypedValue.COMPLEX_UNIT_SP,size.detail);row.setViewVisibility(R.id.widget_task_reminder,task.remind&&!task.done?android.view.View.VISIBLE:android.view.View.GONE);row.setTextViewText(R.id.widget_task_location,"地点："+task.location);row.setViewVisibility(R.id.widget_task_location,task.location.isEmpty()?android.view.View.GONE:android.view.View.VISIBLE);
        String level=new String[]{"高","中","低"}[task.priority];row.setTextViewText(R.id.widget_task_category,task.category);row.setTextViewText(R.id.widget_task_priority,level+"优先级");
        row.setInt(R.id.widget_task_priority,"setBackgroundResource",new int[]{R.drawable.widget_priority_high,R.drawable.widget_priority_medium,R.drawable.widget_priority_low}[task.priority]);row.setTextColor(R.id.widget_task_priority,new int[]{0xffffffff,0xff253163,0xff5b6599}[task.priority]);
        row.setOnClickFillInIntent(R.id.widget_task_title,new Intent().putExtra("taskId",task.id).putExtra("widgetAction","edit"));row.setContentDescription(R.id.widget_task_title,task.title+"，点击标题编辑");return row;
    }
    public static void update(Context c,AppWidgetManager manager,int id){
        List<Task> tasks=tasks(c,id);boolean inline=Build.VERSION.SDK_INT>=31&&tasks.size()<=50;RemoteViews views=new RemoteViews(c.getPackageName(),R.layout.widget_todo_stable);
        WidgetSizing size=sizing(c,id);views.setViewPadding(R.id.widget_root,pixels(c,size.padding),pixels(c,size.padding),pixels(c,size.padding),pixels(c,size.padding));
        views.setTextViewTextSize(R.id.widget_brand,android.util.TypedValue.COMPLEX_UNIT_SP,size.heading);views.setTextViewTextSize(R.id.widget_date,android.util.TypedValue.COMPLEX_UNIT_SP,size.detail);views.setTextViewTextSize(R.id.widget_count,android.util.TypedValue.COMPLEX_UNIT_SP,size.detail);
        views.setTextViewTextSize(R.id.widget_all,android.util.TypedValue.COMPLEX_UNIT_SP,size.compact?11:12);views.setTextViewTextSize(R.id.widget_upcoming,android.util.TypedValue.COMPLEX_UNIT_SP,size.compact?11:12);views.setTextViewTextSize(R.id.widget_empty,android.util.TypedValue.COMPLEX_UNIT_SP,size.compact?11:12);
        views.setTextViewTextSize(R.id.widget_confirm_heading,android.util.TypedValue.COMPLEX_UNIT_SP,size.heading);views.setTextViewTextSize(R.id.widget_confirm_title,android.util.TypedValue.COMPLEX_UNIT_SP,size.title);views.setTextViewTextSize(R.id.widget_confirm_note,android.util.TypedValue.COMPLEX_UNIT_SP,size.detail);views.setViewVisibility(R.id.widget_hint,size.compact?android.view.View.GONE:android.view.View.VISIBLE);
        views.setTextViewText(R.id.widget_date,LocalDate.now().format(DateTimeFormatter.ofPattern("M月d日 EEEE",Locale.CHINA)));
        views.setTextViewText(R.id.widget_count,tasks.size()+" 项事项");
        views.setTextViewText(R.id.widget_upcoming,"今明事项");views.setTextViewText(R.id.widget_all,"全部事项");
        views.setTextViewText(R.id.widget_empty,range(c,id)==WidgetAgenda.ALL?"还没有事项，点击＋记下一件事。":"今日和明日没有事项安排。");
        views.setTextViewText(R.id.widget_hint,"点击标题编辑 · 与应用同步");
        views.setOnClickPendingIntent(R.id.widget_open,open(c,id,"home"));views.setOnClickPendingIntent(R.id.widget_add,open(c,id,"new"));views.setOnClickPendingIntent(R.id.widget_refresh,broadcast(c,id,REFRESH));views.setEmptyView(R.id.widget_list,R.id.widget_empty);
        int[] tabs={R.id.widget_all,R.id.widget_upcoming};String[] actions={ALL,UPCOMING};String[] labels={"全部","今明"};for(int n=0;n<tabs.length;n++){boolean selected=range(c,id)==n;views.setOnClickPendingIntent(tabs[n],broadcast(c,id,actions[n]));views.setTextColor(tabs[n],selected?0xff4561ee:0xff828aa0);views.setInt(tabs[n],"setBackgroundResource",selected?R.drawable.widget_tab_selected:R.drawable.widget_tab_clear);views.setContentDescription(tabs[n],"显示"+labels[n]+"事项"+(selected?"，已选中":""));}
        Task pending=confirmation(c,id);views.setViewVisibility(R.id.widget_content,pending==null?android.view.View.VISIBLE:android.view.View.GONE);views.setViewVisibility(R.id.widget_confirmation,pending==null?android.view.View.GONE:android.view.View.VISIBLE);
        if(pending!=null){views.setTextViewText(R.id.widget_confirm_heading,pending.done?"恢复为待办？":"确认完成？");views.setTextViewText(R.id.widget_confirm_title,pending.title);views.setTextViewText(R.id.widget_confirm_note,pending.done?"恢复后将按设置重新安排提醒。":"完成后保留划线记录，不再发送这次提醒。");views.setTextViewText(R.id.widget_confirm_yes,pending.done?"确认恢复":"确认完成");views.setOnClickPendingIntent(R.id.widget_confirm_no,broadcast(c,id,CANCEL));views.setOnClickPendingIntent(R.id.widget_confirm_yes,broadcast(c,id,CONFIRM));}
        Intent detail=new Intent(c,WidgetActionActivity.class).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,id).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP).setData(Uri.parse("shixu://widget/"+id+"/task"));int mutable=Build.VERSION.SDK_INT>=31?PendingIntent.FLAG_MUTABLE:0;
        views.setPendingIntentTemplate(R.id.widget_list,PendingIntent.getActivity(c,id,detail,PendingIntent.FLAG_UPDATE_CURRENT|mutable));
        if(inline){RemoteViews.RemoteCollectionItems.Builder items=new RemoteViews.RemoteCollectionItems.Builder().setHasStableIds(true).setViewTypeCount(1);for(int n=0;n<tasks.size();n++)items.addItem(tasks.get(n).id,row(c,tasks.get(n),size));views.setRemoteAdapter(R.id.widget_list,items.build());}
        else{Intent service=new Intent(c,TodoWidgetService.class).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,id).setData(Uri.parse("shixu://widget/"+id+"/list"));views.setRemoteAdapter(R.id.widget_list,service);}
        manager.updateAppWidget(id,views);if(!inline)manager.notifyAppWidgetViewDataChanged(id,R.id.widget_list);
    }
}
