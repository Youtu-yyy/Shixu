package cn.shixu.app;

import java.time.*;
import java.time.format.DateTimeFormatter;

public class Task implements java.io.Serializable {
    private static final long serialVersionUID=1L;
    public long id, position, lastNotified, snoozeUntil;
    public long reminderOverrideMillis,reminderOverrideStart;
    public String title="", note="", location="", category="生活", date="", time="", endTime="", period="", repeat="不重复";
    // Transient recognition guidance, never part of the user's saved note.
    public String recognitionWarning="";
    public int endDayOffset=0;
    public boolean deadline=false;
    // Date-only "before D" belongs to D-1; its deadline remains D at midnight.
    public boolean beforeDay=false;
    public String reminderEveningTime="";
    public String reminderPeriod="",reminderPeriodTime="";
    public int reminderPeriodDayOffset=0;
    public int priority=2, lead=0, anchorDay=0;
    public boolean done=false, remind=false;

    public Task copy() {
        Task t=new Task(); t.id=id;t.position=position;t.lastNotified=lastNotified;t.snoozeUntil=snoozeUntil;t.reminderOverrideMillis=reminderOverrideMillis;t.reminderOverrideStart=reminderOverrideStart;t.title=title;t.note=note;t.location=location;t.category=category;t.deadline=deadline;t.reminderEveningTime=reminderEveningTime;
        t.beforeDay=beforeDay;t.reminderPeriod=reminderPeriod;t.reminderPeriodTime=reminderPeriodTime;t.reminderPeriodDayOffset=reminderPeriodDayOffset;
        t.date=date;t.time=time;t.endTime=endTime;t.endDayOffset=endDayOffset;t.period=period;t.repeat=repeat;t.priority=priority;t.lead=lead;t.anchorDay=anchorDay;t.done=done;t.remind=remind;t.recognitionWarning=recognitionWarning;return t;
    }
    public long startMillis() {
        if(date.isEmpty()) return 0;
        return LocalDate.parse(date).atTime(time.isEmpty()?LocalTime.MIDNIGHT:LocalTime.parse(time)).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }
    public long triggerMillis() {
        if(reminderOverrideMillis>0)return reminderOverrideMillis;
        if(!reminderPeriodTime.isEmpty()&&!date.isEmpty())return LocalDate.parse(date).plusDays(reminderPeriodDayOffset).atTime(LocalTime.parse(reminderPeriodTime)).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        if(!reminderEveningTime.isEmpty()&&!date.isEmpty())return LocalDate.parse(date).plusDays(beforeDay&&deadline?0:-1).atTime(LocalTime.parse(reminderEveningTime)).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        return reminderBaseMillis()-lead*60_000L;
    }
    public long reminderBaseMillis(){return beforeDay&&deadline&&!date.isEmpty()?LocalDate.parse(date).plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli():startMillis();}
    public String beforePrefix(){return date.isEmpty()?"":LocalDate.parse(date).plusDays(1).format(DateTimeFormatter.ofPattern("M月d日"))+"前";}
    public void refreshBeforeTitle(){if(beforeDay&&deadline&&!date.isEmpty())title=beforePrefix()+title.replaceFirst("^\\d{1,2}月\\d{1,2}日前","");}
    public void snooze(long now,long occurrenceStart){snoozeUntil=now+600_000L;reminderOverrideMillis=snoozeUntil;reminderOverrideStart=occurrenceStart>0?occurrenceStart:startMillis();if(beforeDay&&deadline)reminderOverrideStart=Instant.ofEpochMilli(reminderOverrideStart).atZone(ZoneId.systemDefault()).plusDays(1).toInstant().toEpochMilli();}
    public void clearSnooze(){snoozeUntil=0;reminderOverrideMillis=0;reminderOverrideStart=0;}
    public String reminderLabel(){
        if(!remind)return "不提醒";
        if(!reminderPeriodTime.isEmpty()||!reminderEveningTime.isEmpty())return Instant.ofEpochMilli(triggerMillis()).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("M月d日 HH:mm"))+"提醒";
        long minutes=reminderOverrideMillis>0?Math.round(((reminderOverrideStart>0?reminderOverrideStart:reminderBaseMillis())-reminderOverrideMillis)/60000d):lead;
        return minutes>0?"提前"+minutes+"分钟提醒":minutes<0?"开始后"+(-minutes)+"分钟提醒":"开始时提醒";
    }
    public long endMillis() {
        if(beforeDay&&deadline&&endTime.isEmpty())return reminderBaseMillis();
        if(date.isEmpty()||endTime.isEmpty())return startMillis();
        return LocalDate.parse(date).plusDays(endDayOffset).atTime(LocalTime.parse(endTime)).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }
    public LocalDate nextDate(LocalDate d) {
        if(beforeDay&&deadline){LocalDate boundary=d.plusDays(1);switch(repeat){case "每天":return d.plusDays(1);case "每周":return d.plusWeeks(1);case "每月":YearMonth next=YearMonth.from(boundary).plusMonths(1);return next.atDay(Math.min(anchorDay>0?anchorDay:boundary.getDayOfMonth(),next.lengthOfMonth())).minusDays(1);default:return d;}}
        switch(repeat) {
            case "每天": return d.plusDays(1);
            case "每周": return d.plusWeeks(1);
            case "每月": YearMonth m=YearMonth.from(d).plusMonths(1); return m.atDay(Math.min(anchorDay>0?anchorDay:d.getDayOfMonth(),m.lengthOfMonth()));
            default: return d;
        }
    }
    public boolean advance() {
        if(repeat.equals("不重复")||date.isEmpty()) return false;
        if(anchorDay==0) anchorDay=LocalDate.parse(date).plusDays(beforeDay&&deadline?1:0).getDayOfMonth();
        date=nextDate(LocalDate.parse(date)).toString();refreshBeforeTitle();lastNotified=0;clearSnooze();return true;
    }
    public void advancePast(long now) {
        if(repeat.equals("不重复")) return;
        int guard=0;
        while(!date.isEmpty()&&triggerMillis()<=now&&guard++<40000) advance();
    }
    public String dateLabel() {
        if(date.isEmpty()) return "未定日期";
        LocalDate d=LocalDate.parse(date), today=LocalDate.now();
        String s=d.equals(today)?"今天":d.equals(today.plusDays(1))?"明天":d.format(DateTimeFormatter.ofPattern("M月d日"));
        String clock=time+(endTime.isEmpty()?"":"—"+(endDayOffset>0?"次日":"")+endTime);
        if(beforeDay&&deadline)return s+" · 当天结束前";
        if(deadline&&!period.isEmpty())return s+" · "+time+"前";
        return s+(period.isEmpty()?(time.isEmpty()?(deadline?"前":" · 全天"):" · "+clock+(deadline?"前":"")):" · "+period+(deadline?"前":""));
    }
}
