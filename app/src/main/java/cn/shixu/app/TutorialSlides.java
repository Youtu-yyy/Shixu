package cn.shixu.app;

import android.graphics.*;
import android.text.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** Code-native illustrations rendered as image pages; sample results use the real local parser. */
public final class TutorialSlides {
    static final int WIDTH=960,HEIGHT=1240,INK=0xff20283f,MUTED=0xff828aa0,BLUE=0xff4561ee,PALE=0xffeef1ff;
    final Canvas canvas;final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);final TextPaint textPaint=new TextPaint(Paint.ANTI_ALIAS_FLAG|Paint.SUBPIXEL_TEXT_FLAG);
    TutorialSlides(Canvas canvas){this.canvas=canvas;}
    float text(String value,float x,float y,int width,int size,int color,boolean bold){textPaint.setColor(color);textPaint.setTextSize(size);textPaint.setTypeface(bold?Typeface.create("sans-serif",Typeface.BOLD):Typeface.create("sans-serif",Typeface.NORMAL));StaticLayout layout=StaticLayout.Builder.obtain(value,0,value.length(),textPaint,width).setIncludePad(false).setLineSpacing(5,1).build();canvas.save();canvas.translate(x,y);layout.draw(canvas);canvas.restore();return y+layout.getHeight();}
    void box(float x,float y,float w,float h,int fill,int radius){paint.setColor(fill);paint.setStyle(Paint.Style.FILL);canvas.drawRoundRect(x,y,x+w,y+h,radius,radius,paint);}
    void label(String value,int y){text(value,72,y,816,24,BLUE,true);}
    void field(String name,String value,int y){box(48,y,864,96,0xffffffff,22);text(name,76,y+18,122,22,MUTED,false);text(value,210,y+22,670,28,INK,true);}
    public static Bitmap render(int page,LocalDateTime now,Map<String,String> ranges){
        Bitmap image=Bitmap.createBitmap(WIDTH,HEIGHT,Bitmap.Config.ARGB_8888);TutorialSlides s=new TutorialSlides(new Canvas(image));s.canvas.drawColor(0xfff6f7fb);
        s.text("拾序 / 使用教程",48,30,700,22,MUTED,false);s.text(String.format(Locale.ROOT,"0%d / 05",page+1),776,30,150,22,BLUE,true);
        s.text(TutorialContent.TITLES[page],48,94,864,46,INK,true);
        String[] intro={"把别人发来的安排，直接放进拾序。","从一句通知中，提炼出清楚的安排。","让自动识别先帮忙，最后由你确认。","不用固定格式，像平时说话一样记录。","安排在哪里、做完了没有，一眼就能看见。"};
        s.text(intro[page],48,162,864,25,MUTED,false);
        Task result=NotificationExtractor.extract(TutorialContent.NOTICE,now,ranges).task;
        switch(page){
            case 0:
                s.box(48,232,864,322,0xffffffff,28);s.label("① 复制一段通知",258);s.text(TutorialContent.NOTICE,76,310,808,32,INK,false);
                s.label("② 粘贴到首页底部，点 ＋",594);s.box(48,652,864,128,0xffffffff,28);s.text("活动通知：请大家明天上午9：00…",72,692,670,27,INK,false);s.box(804,676,80,80,BLUE,22);s.text("＋",816,681,70,46,0xffffffff,true);
                s.box(48,832,864,220,PALE,28);s.label("③ 自动填写，再核对",858);s.text(result.title,76,912,808,36,INK,true);s.text("日期 · 时间段 · 地点 · 原文备注",76,978,808,30,BLUE,false);
                break;
            case 1:
                s.field("事项",result.title,238);s.field("日期",LocalDate.parse(result.date).format(DateTimeFormatter.ofPattern("M月d日"))+" · 明天",348);s.field("时间",result.time+" — "+result.endTime,458);s.field("地点",result.location,568);s.field("提醒",result.reminderLabel(),678);
                s.box(48,826,864,232,PALE,28);s.label("准确时间优先，时间段保留起止",852);s.text("“上午9：00－10：30”会识别为09:00—10:30。\n只有“明天下午”时，才使用设置里的下午时段。",76,906,808,32,INK,false);
                break;
            case 2:
                s.box(48,238,864,190,PALE,28);s.label("识别结果都能改",264);s.text("修改标题、日期、时间或地点；\n分类、优先级、重复也可以自己选择。",76,316,808,32,INK,false);
                s.box(48,466,864,322,0xffffffff,28);s.label("备注直接保留通知原文",492);s.text(TutorialContent.NOTICE,76,546,808,32,INK,false);
                s.box(48,826,864,232,PALE,28);s.label("开启提醒 → 选择时间 → 保存事项",852);s.text("多项安排可先勾选，再逐项核对。\n不需要提醒时，关闭提醒开关即可。\n粘贴旧通知时请核对日期。",76,906,808,32,INK,false);
                break;
            case 3:
                String[] examples={"十分钟后提醒我浇花","明天下午整理书架","周六中午前归还图书"};String[] explanations={"相对时间 → 当前时刻往后10分钟","模糊时段 → 使用设置里的下午范围","截止表达 → 识别截止，并提前一个时段提醒"};
                for(int n=0;n<3;n++){int y=242+n*216;s.box(48,y,864,184,n==1?PALE:0xffffffff,28);s.text(examples[n],76,y+28,808,31,INK,true);s.text(explanations[n],76,y+92,808,30,BLUE,false);}
                s.box(48,916,864,180,PALE,28);s.label("11月7日前交材料",936);s.text("安排在11月6日，标题保留“11月7日前”。\n默认6日晚上18:30提醒，时间可调整。",76,986,808,29,INK,false);
                break;
            default:
                s.box(48,238,864,236,0xffffffff,28);s.label("首页  /  日历  /  周视图",264);s.text("列表查找与筛选 · 整月安排 · 一周时间分布\n首页左滑事项，确认后标记完成。",76,322,808,32,INK,false);
                s.box(48,510,864,230,PALE,28);s.label("放到桌面，当作随手记事本",536);s.text("设置 → 添加桌面小组件\n今明事项一起看，也可切换全部；\n右侧勾选并确认后，完成事项保留划线。",76,590,808,32,INK,false);
                s.box(48,778,864,288,0xffffffff,28);s.label("需要提醒？先开启相应权限",804);s.text("在设置里开启通知与准时提醒，\n开启自启动、无限制电池策略和后台运行。\n通知出现时可选“完成”或“10分钟后再提醒”。\n不同手机的系统选项名称可能不同。",76,862,808,30,INK,false);
        }
        s.text("左右滑动翻页，也可使用下方按钮",48,1164,864,22,MUTED,false);return image;
    }
}
