package cn.shixu.app;
import java.time.*;
import java.util.*;
public final class BeforeDayTests {
    static int checks;static LocalDateTime NOW=LocalDateTime.of(2026,10,22,10,0);
    static void eq(Object expected,Object actual){if(!expected.equals(actual))throw new AssertionError(expected+" != "+actual);checks++;}
    static Task parse(String s){return TimeParser.parse(s,NOW,TimeParser.DEFAULTS).task;}
    static long at(String date,int h,int m){return LocalDate.parse(date).atTime(h,m).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();}
    public static void main(String[] args){
        for(String suffix:new String[]{"前","之前","以前"})for(String prefix:new String[]{"提醒我","请",""}){
            Task p=parse(prefix+"周一晚上"+suffix+"交材料");eq(true,p.deadline);eq(false,p.beforeDay);eq("2026-10-26",p.date);eq("18:30",p.time);eq(at("2026-10-26",13,30),p.triggerMillis());eq(false,p.title.contains(suffix));
            p=parse(prefix+"明天中午"+suffix+"交材料");eq(true,p.deadline);eq(at("2026-10-23",9,0),p.triggerMillis());
            p=parse(prefix+"明天下午15:00"+suffix+"交材料");eq(true,p.deadline);eq("15:00",p.time);
            p=parse(prefix+"明天三点"+suffix+"交材料");eq(true,p.deadline);eq("03:00",p.time);
        }
        Task ordinary=parse("提醒我周一晚上整理之前的材料");eq(false,ordinary.deadline);
        for(String s:new String[]{"11月7日前交材料","11月7日之前交材料","11/7前交材料","11.7以前交材料","2026-11-07前交材料"}){Task t=parse(s);eq("2026-11-06",t.date);eq(true,t.beforeDay);eq("11月7日前交材料",t.title);eq("",t.time);eq(at("2026-11-06",18,30),t.triggerMillis());eq(true,t.dateLabel().contains("当天结束前"));eq(at("2026-11-07",0,0),t.reminderBaseMillis());eq(true,t.copy().beforeDay);}
        Task t=parse("明天前交材料");eq("2026-10-22",t.date);eq("10月23日前交材料",t.title);eq(at("2026-10-22",18,30),t.triggerMillis());
        t=parse("2027年1月1日之前交材料");eq("2026-12-31",t.date);eq("1月1日前交材料",t.title);
        t=parse("2028年3月1日前交材料");eq("2028-02-29",t.date);
        t=parse("11月7日前交材料，提前30分钟提醒");eq(at("2026-11-06",23,30),t.triggerMillis());t.snooze(at("2026-11-06",23,30),t.startMillis());eq("提前20分钟提醒",t.reminderLabel());
        t=parse("11月7日前交材料，不提醒");eq(false,t.remind);eq(true,t.beforeDay);
        t=parse("11月7日中午前交材料");eq(false,t.beforeDay);eq("2026-11-07",t.date);eq("11:35",t.time);eq(at("2026-11-07",9,0),t.triggerMillis());
        t=parse("11月7日下午15:00之前交材料");eq("2026-11-07",t.date);eq(false,t.beforeDay);
        t=parse("11月7日交材料");eq("2026-11-07",t.date);eq(false,t.beforeDay);eq("交材料",t.title);
        t=TimeParser.parse("每月1号之前交材料",LocalDateTime.of(2026,10,22,10,0),TimeParser.DEFAULTS).task;eq("2026-10-31",t.date);eq(1,t.anchorDay);t.advance();eq("2026-11-30",t.date);eq("12月1日前交材料",t.title);t.advance();eq("2026-12-31",t.date);
        t=parse("11月7日前交材料");eq(true,CalendarAgenda.month(Arrays.asList(t),java.time.YearMonth.of(2026,11)).stream().anyMatch(x->x.date.equals("2026-11-06")));
        eq(at("2026-11-07",0,0),t.endMillis());
        t=TimeParser.parse("每月1号前交材料",NOW,TimeParser.DEFAULTS).task;List<Task> projected=CalendarAgenda.month(Arrays.asList(t),YearMonth.of(2026,11));eq(1,projected.size());eq("2026-11-30",projected.get(0).date);eq("12月1日前交材料",projected.get(0).title);
        NotificationExtractor.Result notice=NotificationExtractor.extract("通知：请大家明天前提交材料，需附报名表，请按时办理。",NOW,TimeParser.DEFAULTS);eq("2026-10-22",notice.task.date);eq(true,notice.task.remind);eq(true,notice.task.title.startsWith("10月23日前"));
        System.out.println("BeforeDayTests PASS: "+checks+" assignment, title, boundary, reminder, recurrence and notice checks");
    }
}
