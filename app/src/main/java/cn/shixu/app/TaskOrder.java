package cn.shixu.app;

import java.time.*;
import java.util.Comparator;

public final class TaskOrder {
    private static LocalDateTime start(Task task){return task.date.isEmpty()?LocalDateTime.MAX:LocalDate.parse(task.date).atTime(task.time.isEmpty()?LocalTime.MIDNIGHT:LocalTime.parse(task.time));}
    public static final Comparator<Task> TIME=Comparator.comparing(TaskOrder::start).thenComparingInt(t->t.priority).thenComparingLong(t->t.position).thenComparingLong(t->t.id);
    public static final Comparator<Task> PRIORITY=Comparator.comparingInt((Task t)->t.priority).thenComparing(TaskOrder::start).thenComparingLong(t->t.position).thenComparingLong(t->t.id);
    public static final Comparator<Task> DAY_COMPLETION=Comparator.comparing((Task t)->t.date.isEmpty()?LocalDate.MAX:LocalDate.parse(t.date)).thenComparing(t->t.done).thenComparing(TIME);
    private TaskOrder(){}
}
