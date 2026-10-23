package cn.shixu.app;
import java.time.*;
import java.util.*;

public class CalendarTests {
    static int passed;
    static void eq(Object expected,Object actual){if(!Objects.equals(expected,actual))throw new AssertionError("Expected "+expected+", got "+actual);passed++;}
    static Task task(String date,String repeat){Task t=new Task();t.date=date;t.repeat=repeat;t.title="日程";return t;}
    public static void main(String[] args){
        YearMonth october=YearMonth.of(2026,10);
        Task one=task("2026-10-21","不重复"), undated=task("","不重复"), outside=task("2026-11-01","不重复");
        List<Task> items=CalendarAgenda.month(Arrays.asList(one,undated,outside),october);eq(1,items.size());eq("2026-10-21",items.get(0).date);
        Task daily=task("2025-01-01","每天");daily.id=42;items=CalendarAgenda.month(Arrays.asList(daily),october);eq(31,items.size());eq("2026-10-01",items.get(0).date);eq("2026-10-31",items.get(30).date);eq(42L,items.get(30).id);eq("2025-01-01",daily.date);
        Task weekly=task("2026-09-23","每周");items=CalendarAgenda.month(Arrays.asList(weekly),october);eq(4,items.size());eq("2026-10-07",items.get(0).date);eq("2026-10-28",items.get(3).date);
        Task monthly=task("2027-01-31","每月");monthly.anchorDay=31;items=CalendarAgenda.month(Arrays.asList(monthly),YearMonth.of(2027,2));eq(1,items.size());eq("2027-02-28",items.get(0).date);eq("2027-03-31",CalendarAgenda.month(Arrays.asList(monthly),YearMonth.of(2027,3)).get(0).date);
        eq("2028-02-29",CalendarAgenda.month(Arrays.asList(monthly),YearMonth.of(2028,2)).get(0).date);
        Task future=task("2026-10-29","每天");eq(3,CalendarAgenda.month(Arrays.asList(future),october).size());eq(0,CalendarAgenda.month(Arrays.asList(future),YearMonth.of(2026,9)).size());
        Task complete=task("2026-10-02","每天");complete.done=true;items=CalendarAgenda.month(Arrays.asList(complete),october);eq(1,items.size());eq(true,items.get(0).done);
        Task completedPast=task("2026-09-01","每天");completedPast.done=true;eq(0,CalendarAgenda.month(Arrays.asList(completedPast),october).size());
        Task a=task("2026-10-22","不重复"),b=task("2026-10-21","不重复");a.time="11:35";b.time="18:30";items=CalendarAgenda.month(Arrays.asList(a,b),october);eq("2026-10-21",items.get(0).date);
        Task shifted=task("2027-02-28","每月");shifted.anchorDay=31;eq("2027-03-31",CalendarAgenda.month(Arrays.asList(shifted),YearMonth.of(2027,3)).get(0).date);
        System.out.println("PASS: "+passed+" calendar month, recurrence, leap-year and non-mutation assertions");
    }
}
