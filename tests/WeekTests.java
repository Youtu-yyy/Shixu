package cn.shixu.app;
import java.time.*;
import java.util.*;

public final class WeekTests {
    static int checks;
    static void eq(Object expected,Object value){if(!Objects.equals(expected,value))throw new AssertionError(expected+" != "+value);checks++;}
    static Task task(long id,String date,String start,String end,int offset){Task t=new Task();t.id=id;t.title="周事项"+id;t.date=date;t.time=start;t.endTime=end;t.endDayOffset=offset;return t;}
    static List<WeekAgenda.Day> week(Task... tasks){return WeekAgenda.week(Arrays.asList(tasks),LocalDate.of(2026,10,21),TimeParser.DEFAULTS,true);}
    public static void main(String[] args){
        eq(LocalDate.of(2026,10,19),WeekAgenda.monday(LocalDate.of(2026,10,25)));eq(7,week().size());
        Task all=task(1,"2026-10-21","","",0),range=task(2,"2026-10-21","13:30","15:00",0),point=task(3,"2026-10-21","10:00","",0),undated=task(4,"","","",0);
        List<WeekAgenda.Day> days=week(all,range,point,undated);eq(1,days.get(2).allDay.size());eq(2,days.get(2).timed.size());eq(true,days.get(2).timed.get(0).point);eq(600,days.get(2).timed.get(0).start);eq(600,days.get(2).timed.get(0).end);eq(810,days.get(2).timed.get(1).start);eq(900,days.get(2).timed.get(1).end);eq(1,days.get(2).timed.get(1).lanes);
        Task overlap=task(5,"2026-10-21","14:00","16:00",0);days=week(range,overlap);eq(2,days.get(2).timed.get(0).lanes);eq(2,days.get(2).timed.get(1).lanes);eq(false,days.get(2).timed.get(0).lane==days.get(2).timed.get(1).lane);
        Task later=task(6,"2026-10-21","16:00","17:00",0);days=week(range,overlap,later);eq(1,days.get(2).timed.get(2).lanes);
        Task night=task(7,"2026-10-21","23:00","01:00",1);days=week(night);eq(1380,days.get(2).timed.get(0).start);eq(1440,days.get(2).timed.get(0).end);eq(true,days.get(2).timed.get(0).toNext);eq(0,days.get(3).timed.get(0).start);eq(60,days.get(3).timed.get(0).end);eq(true,days.get(3).timed.get(0).fromPrevious);
        Task sunday=task(8,"2026-10-18","23:00","02:00",1);days=week(sunday);eq(1,days.get(0).timed.size());eq(true,days.get(0).timed.get(0).fromPrevious);
        Task evening=task(9,"2026-10-21","18:30","",0);evening.period="晚上";days=week(evening);eq(1110,days.get(2).timed.get(0).start);eq(1440,days.get(2).timed.get(0).end);eq(false,days.get(2).timed.get(0).point);eq(0,days.get(3).timed.size());
        evening.deadline=true;days=week(evening);eq(true,days.get(2).timed.get(0).point);eq(1110,days.get(2).timed.get(0).end);
        Map<String,String> custom=new LinkedHashMap<>(TimeParser.DEFAULTS);custom.put("晚上","19:00-24:00");evening.deadline=false;days=WeekAgenda.week(Arrays.asList(evening),LocalDate.of(2026,10,21),custom,true);eq(1140,days.get(2).timed.get(0).start);
        all.done=true;range.done=true;days=WeekAgenda.week(Arrays.asList(all,range),LocalDate.of(2026,10,21),TimeParser.DEFAULTS,false);eq(0,days.get(2).allDay.size());eq(0,days.get(2).timed.size());eq(1,week(all).get(2).allDay.size());
        Task repeat=task(10,"2026-09-01","09:00","10:00",0);repeat.repeat="每天";days=week(repeat);for(WeekAgenda.Day day:days)eq(1,day.timed.size());eq("2026-09-01",repeat.date);eq("09:00",repeat.time);eq(0L,repeat.reminderOverrideMillis);
        Task monthly=task(11,"2025-01-31","10:00","11:00",0);monthly.repeat="每月";monthly.anchorDay=31;days=WeekAgenda.week(Arrays.asList(monthly),LocalDate.of(2026,2,28),TimeParser.DEFAULTS,true);eq(1,days.get(5).timed.size());
        Task crossYear=task(12,"2026-12-31","23:00","01:00",1);days=WeekAgenda.week(Arrays.asList(crossYear),LocalDate.of(2027,1,1),TimeParser.DEFAULTS,true);eq(1,days.get(3).timed.size());eq(1,days.get(4).timed.size());eq("2026-12-31",crossYear.date);
        eq(0,week(undated).stream().mapToInt(d->d.allDay.size()+d.timed.size()).sum());
        Task edgeA=task(13,"2026-10-21","23:10","",0),edgeB=task(14,"2026-10-21","23:59","",0);days=week(edgeA,edgeB);eq(2,days.get(2).timed.get(0).lanes);eq(2,days.get(2).timed.get(1).lanes);eq(1439,days.get(2).timed.get(1).start);eq(1439,days.get(2).timed.get(1).end);
        days=week(all,range,point,overlap);int[] widths=WeekColumns.widths(days,350,24);eq(350,Arrays.stream(widths).sum());eq(true,Arrays.stream(widths).allMatch(w->w==50));
        widths=WeekColumns.widths(days,353,24);eq(353,Arrays.stream(widths).sum());eq(1,Arrays.stream(widths).max().getAsInt()-Arrays.stream(widths).min().getAsInt());
        widths=WeekColumns.widths(week(),350,24);eq(350,Arrays.stream(widths).sum());eq(50,widths[0]);eq(50,widths[6]);
        List<Task> full=new ArrayList<>();for(int n=0;n<7;n++)full.add(task(100+n,LocalDate.of(2026,10,19).plusDays(n).toString(),"09:00","10:00",0));widths=WeekColumns.widths(WeekAgenda.week(full,LocalDate.of(2026,10,21),TimeParser.DEFAULTS,true),350,24);eq(350,Arrays.stream(widths).sum());eq(true,Arrays.stream(widths).allMatch(w->w>0));
        System.out.println("PASS: "+checks+" week all-day, interval, overlap, equal columns, point, recurrence and cross-midnight checks");
    }
}
