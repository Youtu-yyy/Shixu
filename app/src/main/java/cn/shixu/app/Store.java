package cn.shixu.app;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.*;
import org.json.*;
import java.util.*;

public class Store extends SQLiteOpenHelper {
    private final Context context;
    public Store(Context c){super(c,"shixu.db",null,1);context=c.getApplicationContext();}
    @Override public void onCreate(SQLiteDatabase db){db.execSQL("CREATE TABLE tasks (id INTEGER PRIMARY KEY AUTOINCREMENT, payload TEXT NOT NULL)");}
    @Override public void onUpgrade(SQLiteDatabase db,int old,int version){throw new IllegalStateException("Unsupported database migration");}
    private Task read(long id,String payload){
        try{JSONObject o=new JSONObject(payload);Task t=new Task();t.id=id;t.title=o.getString("title");t.note=o.optString("note");t.location=o.optString("location");t.category=o.optString("category","生活");t.date=o.optString("date");t.time=o.optString("time");t.endTime=o.optString("endTime");t.endDayOffset=o.optInt("endDayOffset");t.deadline=o.optBoolean("deadline");t.beforeDay=o.optBoolean("beforeDay");t.reminderEveningTime=o.optString("reminderEveningTime");t.reminderPeriod=o.optString("reminderPeriod");t.reminderPeriodTime=o.optString("reminderPeriodTime");t.reminderPeriodDayOffset=o.optInt("reminderPeriodDayOffset");t.period=o.optString("period");t.repeat=o.optString("repeat","不重复");t.priority=o.optInt("priority",1);t.lead=o.optInt("lead");t.anchorDay=o.optInt("anchorDay");t.position=o.optLong("position");t.done=o.optBoolean("done");t.remind=o.optBoolean("remind");t.lastNotified=o.optLong("lastNotified");t.snoozeUntil=o.optLong("snoozeUntil");t.reminderOverrideMillis=o.optLong("reminderOverrideMillis",t.snoozeUntil);t.reminderOverrideStart=o.optLong("reminderOverrideStart");if(!o.has("beforeDay")&&t.deadline&&t.time.isEmpty()&&!t.date.isEmpty()){java.time.LocalDate boundary=java.time.LocalDate.parse(t.date);t.date=boundary.minusDays(1).toString();t.beforeDay=true;if(t.anchorDay==0)t.anchorDay=boundary.getDayOfMonth();t.refreshBeforeTitle();}return t;}catch(JSONException e){throw new IllegalStateException("Cannot read saved task",e);}
    }
    public List<Task> all(){List<Task> tasks=new ArrayList<>();try(Cursor c=getReadableDatabase().rawQuery("SELECT id,payload FROM tasks",null)){while(c.moveToNext())tasks.add(read(c.getLong(0),c.getString(1)));}tasks.sort(Comparator.comparingLong(t->t.position));return tasks;}
    public Task find(long id){try(Cursor c=getReadableDatabase().rawQuery("SELECT payload FROM tasks WHERE id=?",new String[]{String.valueOf(id)})){return c.moveToFirst()?read(id,c.getString(0)):null;}}
    public void save(Task t){
        try{JSONObject o=new JSONObject();o.put("title",t.title);o.put("note",t.note);o.put("location",t.location);o.put("category",t.category);o.put("date",t.date);o.put("time",t.time);o.put("endTime",t.endTime);o.put("endDayOffset",t.endDayOffset);o.put("deadline",t.deadline);o.put("beforeDay",t.beforeDay);o.put("reminderEveningTime",t.reminderEveningTime);o.put("reminderPeriod",t.reminderPeriod);o.put("reminderPeriodTime",t.reminderPeriodTime);o.put("reminderPeriodDayOffset",t.reminderPeriodDayOffset);o.put("period",t.period);o.put("repeat",t.repeat);o.put("priority",t.priority);o.put("lead",t.lead);o.put("anchorDay",t.anchorDay);o.put("position",t.position);o.put("done",t.done);o.put("remind",t.remind);o.put("lastNotified",t.lastNotified);o.put("snoozeUntil",t.snoozeUntil);o.put("reminderOverrideMillis",t.reminderOverrideMillis);o.put("reminderOverrideStart",t.reminderOverrideStart);ContentValues v=new ContentValues();v.put("payload",o.toString());if(t.id==0)t.id=getWritableDatabase().insertOrThrow("tasks",null,v);else getWritableDatabase().update("tasks",v,"id=?",new String[]{String.valueOf(t.id)});if(!getWritableDatabase().inTransaction())TodoWidgetProvider.requestUpdate(context);}catch(JSONException e){throw new IllegalStateException(e);}
    }
    public void reorder(List<Task> order){SQLiteDatabase db=getWritableDatabase();db.beginTransaction();try{for(int i=0;i<order.size();i++){order.get(i).position=i;save(order.get(i));}db.setTransactionSuccessful();}finally{db.endTransaction();}TodoWidgetProvider.requestUpdate(context);}
    public void saveBatch(List<Task> tasks){
        for(Task t:tasks)if(t.id!=0)throw new IllegalArgumentException("Batch must contain new tasks");
        SQLiteDatabase db=getWritableDatabase();boolean committed=false;db.beginTransaction();try{long position=System.currentTimeMillis();for(Task t:tasks){t.position=position++;save(t);}db.setTransactionSuccessful();committed=true;}finally{db.endTransaction();if(!committed)for(Task t:tasks)t.id=0;}TodoWidgetProvider.requestUpdate(context);
    }
    public void delete(long id){getWritableDatabase().delete("tasks","id=?",new String[]{String.valueOf(id)});TodoWidgetProvider.requestUpdate(context);}
    public void complete(Task t){
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();try{
            if(!t.done&&!t.repeat.equals("不重复")&&!t.date.isEmpty()){
                Task archive=t.copy();archive.id=0;archive.done=true;archive.remind=false;archive.repeat="不重复";archive.position=System.currentTimeMillis();save(archive);
                t.advance();t.advancePast(System.currentTimeMillis());
            }else{t.done=!t.done;t.lastNotified=0;t.clearSnooze();}
            save(t);db.setTransactionSuccessful();
        }finally{db.endTransaction();}TodoWidgetProvider.requestUpdate(context);
    }
}
