package cn.shixu.app;

import java.time.*;
import java.util.regex.*;

/** Parse both clocks as one unit so an end clock can never leak into the title. */
public final class TimeRangeParser {
    private static final String NUMBER="[零〇一二三四五六七八九十两\\d]+";
    private static final String PERIOD="凌晨|早上|上午|中午|下午|晚上";
    private static final String CLOCK="(?:"+NUMBER+"[:：]\\d{2}|"+NUMBER+"[点时](?:半|一刻|三刻|"+NUMBER+"分?)?|"+NUMBER+")";
    private static final Pattern RANGE=Pattern.compile("(?:从[\\s\\p{Zs}\\u200B\\uFEFF]*|\\bfrom[\\s\\p{Zs}\\u200B\\uFEFF]+)?(?<firstPeriod>"+PERIOD+")?[\\s\\p{Zs}\\u200B\\uFEFF]*(?<first>"+CLOCK+")[\\s\\p{Zs}\\u200B\\uFEFF]*(?:到|至|[-‐‑‒–—―−﹘﹣－~～]|\\bto\\b|\\buntil\\b)[\\s\\p{Zs}\\u200B\\uFEFF]*(?<nextDay>次日|翌日|第二天)?[\\s\\p{Zs}\\u200B\\uFEFF]*(?<lastPeriod>"+PERIOD+")?[\\s\\p{Zs}\\u200B\\uFEFF]*(?<last>"+CLOCK+")",Pattern.CASE_INSENSITIVE);
    public static final class Range {
        public String start,end;public int nextDay,from,to;
    }
    private static final class Clock {
        int hour,minute;boolean colon;
        Clock(String text){
            colon=text.contains(":")||text.contains("：");
            if(colon){String[] parts=text.split("[:：]");hour=TimeParser.num(parts[0]);minute=Integer.parseInt(parts[1]);}
            else if(text.contains("点")||text.contains("时")){
                String[] parts=text.split("[点时]",2);hour=TimeParser.num(parts[0]);String tail=parts.length>1?parts[1]:"";
                minute=tail.isEmpty()?0:tail.equals("半")?30:tail.equals("一刻")?15:tail.equals("三刻")?45:TimeParser.num(tail.replace("分",""));
            }else hour=TimeParser.num(text);
            if(hour<0||hour>24||minute<0||minute>59||(hour==24&&minute!=0))throw new DateTimeException("Invalid time range clock");
        }
    }
    static int hour(Clock c,String period){
        int h=c.hour;
        if((period.equals("下午")||period.equals("晚上")||period.equals("中午"))&&h<12)h+=12;
        if(period.equals("凌晨")&&h==12)h=0;
        return h;
    }
    public static Range extract(String text){
        Matcher m=RANGE.matcher(text);
        while(m.find()){
            String first=m.group("first"),last=m.group("last"),a=m.group("firstPeriod"),z=m.group("lastPeriod");
            a=a==null?"":a;z=z==null?"":z;
            // A page/count interval is not a time interval (e.g. 阅读三到五页).
            if(a.isEmpty()&&z.isEmpty()&&!first.matches(".*[:：点时].*")&&!last.matches(".*[:：点时].*"))continue;
            Clock start=new Clock(first),end=new Clock(last);
            boolean explicitNext=m.group("nextDay")!=null;
            int h1=hour(start,a),h2;
            if(!z.isEmpty())h2=hour(end,z);
            else if(explicitNext||end.hour==24)h2=end.hour;
            else h2=hour(end,a);
            // The explicit PM clock can qualify an omitted period on the first clock: 3 to 5 pm.
            if(a.isEmpty()&&!start.colon&&h1<12&&end.colon&&end.hour>=12&&h1+12<end.hour)h1+=12;
            if(a.isEmpty()&&h1<12&&(z.equals("下午")||z.equals("晚上"))&&h1+12<h2)h1+=12;
            if(h1==24)throw new DateTimeException("Start cannot be 24:00");
            int offset=explicitNext?1:0;
            if(h2==24){h2=0;offset=1;}
            int begin=h1*60+start.minute,finish=h2*60+end.minute;
            if(!explicitNext&&offset==0&&finish<=begin){
                if(z.isEmpty()&&h1>=10&&h1<=12&&end.hour>=1&&end.hour<=6&&end.hour+12<=23){h2=end.hour+12;finish=h2*60+end.minute;}
                else if(h1>=18&&end.hour<=6&&(z.isEmpty()||z.equals("凌晨")||z.equals("早上")||z.equals("上午"))){h2=end.hour;offset=1;finish=h2*60+end.minute;}
            }
            if(finish+offset*1440<=begin)throw new DateTimeException("Range end must follow start; specify next day if needed");
            Range range=new Range();range.start=LocalTime.of(h1,start.minute).toString();range.end=LocalTime.of(h2,end.minute).toString();range.nextDay=offset;range.from=m.start();range.to=m.end();return range;
        }
        return null;
    }
}
