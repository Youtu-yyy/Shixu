package cn.shixu.app;
import java.util.*;

/** Keep the date header, all-day strip and timeline aligned in seven equal columns. */
public final class WeekColumns {
    public static int[] widths(List<WeekAgenda.Day> days,int available,int emptyWidth){
        int[] result=new int[7];available=Math.max(7,available);
        for(int n=0;n<7;n++)result[n]=available/7+(n<available%7?1:0);
        return result;
    }
}
