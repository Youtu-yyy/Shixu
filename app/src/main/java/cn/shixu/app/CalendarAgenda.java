package cn.shixu.app;

import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

/** Calendar occurrences are projections; rendering never creates or advances saved tasks. */
public final class CalendarAgenda {
    public static List<Task> month(List<Task> tasks, YearMonth month) {
        List<Task> result = new ArrayList<>();
        LocalDate first = month.atDay(1), last = month.atEndOfMonth();
        for (Task task : tasks) {
            if (task.date.isEmpty()) continue;
            LocalDate base = LocalDate.parse(task.date), occurrence = base;
            if (base.isAfter(last)) continue;
            boolean repeating = !task.done && !task.repeat.equals("不重复");
            if (repeating && base.isBefore(first)) {
                if (task.repeat.equals("每天")) occurrence = first;
                else if (task.repeat.equals("每周")) {
                    long days = ChronoUnit.DAYS.between(base, first);
                    occurrence = base.plusWeeks((days + 6) / 7);
                } else if (task.repeat.equals("每月")) {
                    int anchor = task.anchorDay > 0 ? task.anchorDay : base.plusDays(task.beforeDay&&task.deadline?1:0).getDayOfMonth();
                    occurrence = month.atDay(Math.min(anchor, month.lengthOfMonth())).minusDays(task.beforeDay&&task.deadline?1:0);
                    if(task.beforeDay&&task.deadline&&occurrence.isBefore(first))occurrence=task.nextDate(occurrence);
                }
            }
            while (!occurrence.isAfter(last)) {
                if (!occurrence.isBefore(first)) {
                    Task display = task.copy();
                    display.date = occurrence.toString();
                    display.refreshBeforeTitle();
                    result.add(display);
                }
                if (!repeating) break;
                LocalDate next = task.nextDate(occurrence);
                if (!next.isAfter(occurrence)) break;
                occurrence = next;
            }
        }
        result.sort(TaskOrder.DAY_COMPLETION);
        return result;
    }
}
