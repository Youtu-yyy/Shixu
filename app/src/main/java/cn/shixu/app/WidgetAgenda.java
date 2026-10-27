package cn.shixu.app;
import java.time.LocalDate;
import java.util.*;

/** Widget selection never changes dates or completion state of saved tasks. */
public final class WidgetAgenda {
    public static final int ALL=0,UPCOMING=1;
    public static List<Task> select(List<Task> tasks,LocalDate today,int range){
        List<Task> selected=new ArrayList<>();
        LocalDate tomorrow=today.plusDays(1);
        for(Task task:tasks)if(range==ALL||task.date.equals(today.toString())||task.date.equals(tomorrow.toString()))selected.add(task.copy());
        // All tasks form two time-sorted groups; upcoming keeps completion within each day.
        selected.sort(range==ALL?Comparator.comparing((Task t)->t.done).thenComparing(TaskOrder.TIME):TaskOrder.DAY_COMPLETION);return selected;
    }
}
