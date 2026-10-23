package cn.shixu.app;
import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.widget.*;
import java.time.*;
import java.util.*;

/** Native accessible event controls over a coarse two-hour grid. */
public final class WeekTimeline extends FrameLayout {
    public interface Open {void open(Task task);}
    final java.util.List<WeekAgenda.Day> days;final java.util.List<WeekAgenda.Block> blocks=new ArrayList<>();final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);final float density,scaled;final int axis,hour;
    public WeekTimeline(Context context,java.util.List<WeekAgenda.Day> days,Open open){super(context);this.days=days;density=getResources().getDisplayMetrics().density;scaled=getResources().getDisplayMetrics().scaledDensity;axis=dp(42);hour=dp(32);setWillNotDraw(false);setBackgroundColor(Color.WHITE);
        for(WeekAgenda.Day day:days)for(WeekAgenda.Block b:day.timed){blocks.add(b);TextView item=new TextView(context);item.setTextSize(10);item.setPadding(dp(3),dp(2),dp(2),0);item.setGravity(Gravity.TOP);item.setMaxLines(b.point?2:4);item.setEllipsize(null);item.setTextColor(b.task.done?0xff828aa0:b.task.priority==0?Color.WHITE:0xff253163);if(b.task.done)item.setPaintFlags(item.getPaintFlags()|Paint.STRIKE_THRU_TEXT_FLAG);
            GradientDrawable background=new GradientDrawable();background.setColor(b.task.done?0xffeef0f4:new int[]{0xff3344c8,0xffa0acf5,0xffeef1ff}[b.task.priority]);background.setCornerRadius(dp(5));background.setStroke(dp(1),b.task.done?0xffdce0e9:0xffa9b5f2);item.setBackground(background);
            String clock=String.format(Locale.ROOT,"%02d:%02d",b.start/60,b.start%60);item.setText((b.task.deadline?"截止·":"")+b.task.title+(b.point?"":"\n"+clock));item.setContentDescription("周视图事项："+b.task.title+"，"+day.date+"，"+(b.point?"开始时间":"时间段")+clock+(b.point?"":"至"+String.format(Locale.ROOT,"%02d:%02d",b.end/60,b.end%60))+(b.task.location.isEmpty()?"":"，地点："+b.task.location)+(b.task.done?"，已完成":"")+(b.fromPrevious?"，承接前一天":"")+(b.toNext?"，延续到次日":""));item.setTag(item.getText().toString());item.setOnClickListener(v->open.open(b.task));addView(item,new FrameLayout.LayoutParams(1,dp(24)));
        }
    }
    int dp(int value){return Math.round(value*density);}
    public int gridHeight(){return hour*24;}
    @Override protected void onMeasure(int widthSpec,int heightSpec){int w=View.MeasureSpec.getSize(widthSpec),h=gridHeight();int[] widths=WeekColumns.widths(days,w-axis,dp(24));int index=0,dayLeft=axis;for(int day=0;day<7;day++){float column=widths[day];for(WeekAgenda.Block b:days.get(day).timed){TextView item=(TextView)getChildAt(index++);int left=Math.round(dayLeft+b.lane*column/b.lanes)+dp(1),width=Math.max(dp(4),Math.round(column/b.lanes)-dp(2));int minimum=dp(b.point?36:24),top=Math.min(h-minimum,Math.round(b.start/60f*hour));int height=b.point?minimum:Math.max(minimum,Math.round((b.end-b.start)/60f*hour));height=Math.min(height,h-top);FrameLayout.LayoutParams p=(FrameLayout.LayoutParams)item.getLayoutParams();p.width=width;p.height=height;p.leftMargin=left;p.topMargin=top;String value=String.valueOf(item.getTag());if(!value.contentEquals(item.getText()))item.setText(value);}dayLeft+=widths[day];}super.onMeasure(widthSpec,View.MeasureSpec.makeMeasureSpec(h,View.MeasureSpec.EXACTLY));}
    @Override protected void onDraw(Canvas canvas){super.onDraw(canvas);int[] widths=WeekColumns.widths(days,getWidth()-axis,dp(24));int x=axis;for(int n=0;n<7;n++){if(days.get(n).allDay.isEmpty()&&days.get(n).timed.isEmpty()){paint.setColor(0xfffafbfe);canvas.drawRect(x,0,x+widths[n],getHeight(),paint);}x+=widths[n];}paint.setStrokeWidth(dp(1));paint.setColor(0xffe8ebf3);x=axis;for(int n=0;n<=7;n++){canvas.drawLine(x,0,x,getHeight(),paint);if(n<7)x+=widths[n];}
        for(int hourOfDay=0;hourOfDay<=24;hourOfDay+=2){float y=hourOfDay*hour;paint.setColor(0xffe8ebf3);canvas.drawLine(axis,y,getWidth(),y,paint);paint.setColor(0xff828aa0);paint.setTextSize(9*scaled);canvas.drawText(String.format(Locale.ROOT,"%02d:00",hourOfDay),dp(2),Math.min(getHeight()-dp(2),y+dp(11)),paint);}
        LocalDate today=LocalDate.now();int left=axis;for(int day=0;day<7;day++){if(days.get(day).date.equals(today)){LocalTime now=LocalTime.now();float y=(now.getHour()+now.getMinute()/60f)*hour;paint.setColor(0xffdf6a61);paint.setStrokeWidth(dp(1));canvas.drawLine(left,y,left+widths[day],y,paint);canvas.drawCircle(left+dp(2),y,dp(2),paint);}left+=widths[day];}
    }
}
