package cn.shixu.app;
import android.app.*;
import android.appwidget.AppWidgetManager;
import android.content.*;
import android.os.Bundle;
import android.widget.Toast;

/** Routes widget taps; completion confirmation is rendered inside the home-screen widget. */
public class WidgetActionActivity extends Activity {
    @Override public void onCreate(Bundle state){
        super.onCreate(state);handleTap();
    }
    @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);handleTap();}
    void handleTap(){long id=getIntent().getLongExtra("taskId",0);
        String action=getIntent().getStringExtra("widgetAction");if(id<=0||(!"edit".equals(action)&&!"complete".equals(action))){finish();return;}
        if("edit".equals(action)){startActivity(new Intent(this,MainActivity.class).putExtra("taskId",id).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP));finish();return;}
        Task task;try(Store store=new Store(this)){task=store.find(id);}if(task==null){finish();return;}
        if(task.done!=getIntent().getBooleanExtra("expectedDone",task.done)||task.startMillis()!=getIntent().getLongExtra("expectedStart",task.startMillis())){Toast.makeText(this,"事项已更新，请刷新后重试",Toast.LENGTH_SHORT).show();finish();return;}
        TodoWidgetProvider.beginConfirmation(this,getIntent().getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,AppWidgetManager.INVALID_APPWIDGET_ID),task);finish();
    }
}
