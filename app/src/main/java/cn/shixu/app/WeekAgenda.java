package cn.shixu.app;
import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.*;

/** Pure weekly projection. Point events have no invented semantic duration. */
public final class WeekAgenda {
    public static final class Block {
        public Task task;public int start,end,lane,lanes;public boolean point,fromPrevious,toNext;
        int minimumMinutes(){return point?68:45;}
        int layoutStart(){return Math.min(start,1440-minimumMinutes());}
        int layoutEnd(){return Math.min(1440,Math.max(point?start:end,layoutStart()+minimumMinutes()));}
    }
    public static final class Day {public final LocalDate date;public final List<Task> allDay=new ArrayList<>();public final List<Block> timed=new ArrayList<>();Day(LocalDate date){this.date=date;}}
    public static LocalDate monday(LocalDate day){return day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));}
    static int minute(String value){return value.equals("24:00")?1440:LocalTime.parse(value).toSecondOfDay()/60;}
    public static List<Day> week(List<Task> tasks,LocalDate anchor,Map<String,String> ranges,boolean completed){
        LocalDate first=monday(anchor),last=first.plusDays(6);List<Day> result=new ArrayList<>();for(int i=0;i<7;i++)result.add(new Day(first.plusDays(i)));
        List<Task> occurrences=new ArrayList<>();for(YearMonth month=YearMonth.from(first.minusDays(1));!month.isAfter(YearMonth.from(last));month=month.plusMonths(1))occurrences.addAll(CalendarAgenda.month(tasks,month));
        for(Task t:occurrences){if(!completed&&t.done)continue;LocalDate date=LocalDate.parse(t.date);if(date.isBefore(first.minusDays(1))||date.isAfter(last))continue;
            if(t.time.isEmpty()){if(!date.isBefore(first))result.get((int)java.time.temporal.ChronoUnit.DAYS.between(first,date)).allDay.add(t);continue;}
            int start=minute(t.time),end=start;boolean point=t.deadline||t.endTime.isEmpty()&&t.period.isEmpty();
            if(!point){if(!t.endTime.isEmpty())end=minute(t.endTime)+t.endDayOffset*1440;else{String range=ranges.get(t.period);if(range==null)point=true;else{String[] parts=range.split("-");start=minute(parts[0]);end=minute(parts[1]);if(end<start)end+=1440;}}if(end<=start)point=true;}
            LocalDateTime begins=date.atStartOfDay().plusMinutes(start),finishes=date.atStartOfDay().plusMinutes(point?start:end);
            for(Day day:result){LocalDateTime dayStart=day.date.atStartOfDay(),dayEnd=dayStart.plusDays(1);if(point?!begins.toLocalDate().equals(day.date):!begins.isBefore(dayEnd)||!finishes.isAfter(dayStart))continue;
                Block b=new Block();b.task=t.copy();b.point=point;b.start=(int)Math.max(0,java.time.temporal.ChronoUnit.MINUTES.between(dayStart,begins));b.end=point?b.start:(int)Math.min(1440,java.time.temporal.ChronoUnit.MINUTES.between(dayStart,finishes));b.fromPrevious=begins.isBefore(dayStart);b.toNext=finishes.isAfter(dayEnd);day.timed.add(b);
            }
        }
        for(Day day:result){day.allDay.sort(TaskOrder.DAY_COMPLETION);day.timed.sort(Comparator.comparingInt((Block b)->b.start).thenComparingInt(b->b.end).thenComparingInt(b->b.task.priority).thenComparingLong(b->b.task.id));
            List<Block> cluster=new ArrayList<>();List<Integer> laneEnds=new ArrayList<>();int clusterEnd=-1;
            List<Block> layout=new ArrayList<>(day.timed);layout.sort(Comparator.comparingInt(Block::layoutStart).thenComparingInt(b->b.start));
            for(Block block:layout){if(block.layoutStart()>=clusterEnd&&!cluster.isEmpty()){for(Block b:cluster)b.lanes=laneEnds.size();cluster.clear();laneEnds.clear();clusterEnd=-1;}
                int lane=0;while(lane<laneEnds.size()&&laneEnds.get(lane)>block.layoutStart())lane++;if(lane==laneEnds.size())laneEnds.add(block.layoutEnd());else laneEnds.set(lane,block.layoutEnd());block.lane=lane;cluster.add(block);clusterEnd=Math.max(clusterEnd,block.layoutEnd());
            }for(Block b:cluster)b.lanes=laneEnds.size();
        }return result;
    }
}
