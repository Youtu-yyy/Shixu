package cn.shixu.app;
import android.appwidget.AppWidgetManager;
import android.content.*;
import android.widget.*;
import java.util.*;

/** Scrollable collection for older Android versions and large lists; rows load on demand. */
public class TodoWidgetService extends RemoteViewsService {
    @Override public RemoteViewsFactory onGetViewFactory(Intent intent){return new Factory(getApplicationContext(),intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,0));}
    static class Factory implements RemoteViewsFactory {
        final Context context;final int id;List<Task> rows=new ArrayList<>();WidgetSizing size;Factory(Context c,int id){context=c;this.id=id;}
        public void onCreate(){onDataSetChanged();}public void onDataSetChanged(){size=TodoWidgetProvider.sizing(context,id);rows=TodoWidgetProvider.tasks(context,id);}public void onDestroy(){rows.clear();}
        public int getCount(){return rows.size();}public RemoteViews getViewAt(int at){return at>=0&&at<getCount()?TodoWidgetProvider.row(context,rows.get(at),size):null;}
        public RemoteViews getLoadingView(){return null;}public int getViewTypeCount(){return 1;}public long getItemId(int at){return rows.get(at).id;}public boolean hasStableIds(){return true;}
    }
}
