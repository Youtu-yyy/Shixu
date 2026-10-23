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
        selected.sort(TaskOrder.DAY_COMPLETION);return selected;
    }
}
