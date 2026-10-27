package cn.shixu.app;
import java.time.*;
import java.util.*;
public class WidgetTests {
    static int checks;static void eq(Object expected,Object actual){if(!Objects.equals(expected,actual))throw new AssertionError(expected+" != "+actual);checks++;}
    static Task task(long id,String date,String time,int priority){Task t=new Task();t.id=id;t.date=date;t.time=time;t.priority=priority;t.title="事项"+id;return t;}
    public static void main(String[] args){
        LocalDate today=LocalDate.of(2026,10,21);Task old=task(1,"2026-10-20","20:00",2),early=task(2,"2026-10-21","08:00",2),high=task(3,"2026-10-21","08:00",0),future=task(4,"2026-10-22","07:00",0),undated=task(5,"","",2),done=task(6,"2026-10-21","06:00",0);done.done=true;
        List<Task> source=Arrays.asList(future,undated,done,early,old,high);List<Task> all=WidgetAgenda.select(source,today,WidgetAgenda.ALL);eq(6,all.size());eq(1L,all.get(0).id);eq(3L,all.get(1).id);eq(2L,all.get(2).id);eq(4L,all.get(3).id);eq(5L,all.get(4).id);eq(6L,all.get(5).id);
        List<Task> twoDays=WidgetAgenda.select(source,today,WidgetAgenda.UPCOMING);eq(4,twoDays.size());eq(3L,twoDays.get(0).id);eq(false,twoDays.get(0).done);eq(2L,twoDays.get(1).id);eq(6L,twoDays.get(2).id);eq(true,twoDays.get(2).done);eq(4L,twoDays.get(3).id);
        eq(4L,source.get(0).id);all.get(0).title="副本";eq("事项1",old.title);eq(false,old.done);
        old.repeat="每天";WidgetAgenda.select(source,today,WidgetAgenda.ALL);eq("2026-10-20",old.date);eq("每天",old.repeat);
        early.done=true;eq(6,WidgetAgenda.select(source,today,WidgetAgenda.ALL).size());eq(4,WidgetAgenda.select(source,today,WidgetAgenda.UPCOMING).size());eq(0,WidgetAgenda.select(Collections.emptyList(),today,WidgetAgenda.UPCOMING).size());
        Task midnight=task(7,"2026-10-21","",2);eq(7L,WidgetAgenda.select(Arrays.asList(early,midnight,high),today,WidgetAgenda.ALL).get(0).id);
        Task nextYear=task(8,"2027-01-01","12:00",2);eq(1,WidgetAgenda.select(Arrays.asList(nextYear),LocalDate.of(2026,12,31),WidgetAgenda.UPCOMING).size());
        Task leap=task(9,"2028-02-29","09:00",2),after=task(10,"2028-03-01","09:00",2);eq(1,WidgetAgenda.select(Arrays.asList(leap,after),LocalDate.of(2028,2,28),WidgetAgenda.UPCOMING).size());eq(2,WidgetAgenda.select(Arrays.asList(leap,after),LocalDate.of(2028,2,29),WidgetAgenda.UPCOMING).size());
        Task later=task(11,"2026-10-23","09:00",2);eq(1,WidgetAgenda.select(Arrays.asList(later,old,undated,done),today,WidgetAgenda.UPCOMING).size());
        Task pastDone=task(12,"2026-10-20","09:00",0),todayDoneLate=task(13,"2026-10-21","21:00",2),tomorrowDone=task(14,"2026-10-22","06:00",0),undatedDone=task(15,"","",0);
        pastDone.done=todayDoneLate.done=tomorrowDone.done=undatedDone.done=true;
        List<Task> mixed=Arrays.asList(tomorrowDone,todayDoneLate,undatedDone,done,future,high,pastDone,undated);
        eq(Arrays.asList(3L,4L,5L,12L,6L,13L,14L,15L),ids(WidgetAgenda.select(mixed,today,WidgetAgenda.ALL)));
        eq(Arrays.asList(3L,6L,13L,4L,14L),ids(WidgetAgenda.select(mixed,today,WidgetAgenda.UPCOMING)));
        done.done=false;eq(Arrays.asList(6L,3L,4L,5L,12L,13L,14L,15L),ids(WidgetAgenda.select(mixed,today,WidgetAgenda.ALL)));
        eq(Arrays.asList(6L,3L,13L,4L,14L),ids(WidgetAgenda.select(mixed,today,WidgetAgenda.UPCOMING)));done.done=true;
        eq(Arrays.asList(12L,6L,13L,14L,15L),ids(WidgetAgenda.select(Arrays.asList(undatedDone,tomorrowDone,todayDoneLate,done,pastDone),today,WidgetAgenda.ALL)));
        eq(Arrays.asList(3L,4L,5L),ids(WidgetAgenda.select(Arrays.asList(undated,future,high),today,WidgetAgenda.ALL)));
        List<Task> copies=WidgetAgenda.select(mixed,today,WidgetAgenda.ALL);copies.get(0).done=true;eq(false,high.done);eq(14L,mixed.get(0).id);
        WidgetSizing small=WidgetSizing.forSize(240,250),medium=WidgetSizing.forSize(300,300),large=WidgetSizing.forSize(360,440);
        eq(true,small.compact);eq(false,large.compact);eq(true,small.title<medium.title&&medium.title<large.title);eq(true,small.detail<large.detail);eq(true,small.padding<large.padding);eq(true,small.rowPadding<large.rowPadding);
        eq(true,WidgetSizing.forSize(360,220).compact);eq(true,WidgetSizing.forSize(220,440).compact);eq(true,WidgetSizing.forSize(0,0).compact);eq(false,WidgetSizing.forSize(280,280).compact);eq(15,WidgetSizing.forSize(330,330).title);
        System.out.println("PASS: "+checks+" widget selection, sizing, sorting and non-mutation assertions");
    }
    static List<Long> ids(List<Task> tasks){List<Long> result=new ArrayList<>();for(Task t:tasks)result.add(t.id);return result;}
}
