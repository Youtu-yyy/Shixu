package cn.shixu.app;

import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.regex.*;

/** Offline and deterministic. Unrecognized wording remains in the title for review. */
public final class TimeParser {
    public static final LinkedHashMap<String,String> DEFAULTS=new LinkedHashMap<>();
    static {DEFAULTS.put("凌晨","00:00-06:00");DEFAULTS.put("早上","06:00-09:00");DEFAULTS.put("上午","09:00-11:35");DEFAULTS.put("中午","11:35-13:30");DEFAULTS.put("下午","13:30-18:30");DEFAULTS.put("晚上","18:30-24:00");}
    public static class Result {
        public Task task=new Task();public boolean recognized=false;
        public boolean categoryRecognized,priorityRecognized,repeatRecognized,noteRecognized,reminderRecognized;
    }
    private static final int CI=Pattern.CASE_INSENSITIVE|Pattern.UNICODE_CASE;
    private static final String SPACE="[\\s\\p{Zs}]*";
    /** Normalize only recognized temporal tokens; retain the user's script in titles and notes. */
    private static String temporalVariants(String input){
        String n="[零〇一二三四五六七八九十兩两\\d０-９]+";
        String[] expressions={
            "[0-9０-９]{1,2}"+SPACE+"[:：]"+SPACE+"[0-9０-９]{2}",
            "(?:半|"+n+")"+SPACE+"(?:[個个]"+SPACE+")?(?:小[時时]|分[鐘钟]|分|秒[鐘钟]|秒|天|[週周]|[個个]星期)"+SPACE+"(?:之[後后]|以[後后]|[後后])",
            "提前"+SPACE+"(?:半|"+n+")"+SPACE+"(?:[個个]"+SPACE+")?(?:小[時时]|分[鐘钟]|分|天)",
            n+SPACE+"[點点時时](?:"+SPACE+"(?:半|一刻|三刻|"+n+"(?:"+SPACE+"分)?))?",
            "(?:下下|下|本|這|这|每)?[個个]?(?:[週周]|星期|[禮礼]拜)[一二三四五六日天1-7]?",
            "(?:下下|下|這|这|上)?[個个]?月"+SPACE+n+SPACE+"[號号日]",
            n+SPACE+"月"+SPACE+n+SPACE+"[號号日]?",
            "大後天|後天"
        };
        String s=input;
        for(String expression:expressions){Matcher m=Pattern.compile(expression).matcher(s);StringBuffer out=new StringBuffer();while(m.find()){
            String value=m.group().replace('鐘','钟').replace('後','后').replace('時','时').replace('點','点').replace('個','个').replace('兩','两').replace('號','号').replace('週','周').replace('禮','礼').replace('這','这');
            StringBuilder normalized=new StringBuilder();for(char c:value.toCharArray())normalized.append(c>='０'&&c<='９'?(char)(c-'０'+'0'):c);
            m.appendReplacement(out,Matcher.quoteReplacement(normalized.toString()));
        }m.appendTail(out);s=out.toString();}
        return s;
    }
    /** Compact only complete date/clock tokens; never join ordinary title words or note text. */
    private static String temporalSpacing(String input){
        String number="[零〇一二三四五六七八九十两\\d]+";
        String[] patterns={
            "\\d{4}"+SPACE+"[-/]"+SPACE+"\\d{1,2}"+SPACE+"[-/]"+SPACE+"\\d{1,2}",
            "(?<![\\dA-Za-z/])\\d{1,2}"+SPACE+"/"+SPACE+"\\d{1,2}(?![\\d/])",
            "\\d{1,2}"+SPACE+"月"+SPACE+"\\d{1,2}(?:"+SPACE+"[日号])?",
            "(?<!\\d)\\d{1,2}"+SPACE+"[:：]"+SPACE+"\\d{2}(?!\\d)",
            number+SPACE+"[点时](?:"+SPACE+"(?:半|一刻|三刻|"+number+"(?:"+SPACE+"分)?))?"
        };
        String s=input;
        for(String pattern:patterns){
            Matcher m=Pattern.compile(pattern).matcher(s);StringBuffer out=new StringBuffer();
            while(m.find())m.appendReplacement(out,Matcher.quoteReplacement(m.group().replaceAll("[\\s\\p{Zs}]+","")));
            m.appendTail(out);s=out.toString();
        }
        return s;
    }
    private static boolean contains(String input,String pattern){return Pattern.compile(pattern,CI).matcher(input).find();}
    private static String category(String value){
        if(value.equalsIgnoreCase("work"))return "工作";
        if(value.equalsIgnoreCase("study")||value.equalsIgnoreCase("school"))return "学习";
        if(value.equalsIgnoreCase("life")||value.equalsIgnoreCase("personal"))return "生活";
        return value;
    }
    private static String metadata(String input,Result r){
        Task t=r.task;String s=input;
        Matcher m=Pattern.compile("(?:备注|说明|\\bnotes?)\\s*[:：]\\s*(.+)$",CI).matcher(s);
        if(m.find()){t.note=m.group(1).trim();r.noteRecognized=true;s=s.substring(0,m.start());}
        m=Pattern.compile("(?:分类|\\bcategory)\\s*[:：]\\s*([^\\s，,;；#]+)|#([^\\s#，,;；]+)",CI).matcher(s);
        if(m.find()){t.category=category(m.group(1)!=null?m.group(1):m.group(2));r.categoryRecognized=true;s=s.substring(0,m.start())+" "+s.substring(m.end());}
        m=Pattern.compile("(?:优先级\\s*[:：]?\\s*|\\bpriority\\s*[:：]\\s*)(高|低|普通|中|high|low|normal|medium)(?![a-z])|(?:高|中|低|普通)优先级",CI).matcher(s);
        if(m.find()){String value=m.group().toLowerCase(Locale.ROOT);t.priority=value.contains("高")||value.contains("high")?0:value.contains("低")||value.contains("low")?2:1;r.priorityRecognized=true;s=s.substring(0,m.start())+" "+s.substring(m.end());}
        else if(contains(s,"不着急|不紧急|不重要|低优先|\\blow priority\\b|\\bnot (?:urgent|important)\\b")){t.priority=2;r.priorityRecognized=true;}
        else if(contains(s,"紧急|重要|\\burgent\\b|\\bimportant\\b|\\bhigh priority\\b")){t.priority=0;r.priorityRecognized=true;}
        if(!r.categoryRecognized){
            if(contains(s,"作业|作業|功课|功課|考试|考試|复习|複習|背单词|上课|学习|學習|读书|阅读|\\b(homework|assignment|exam|study|lesson|class|read|reading)\\b")){t.category="学习";r.categoryRecognized=true;}
            else if(contains(s,"开会|会议|项目|客户|工作|报告|报表|方案|同事|老板|采访|初稿|\\b(meeting|project|client|report|work|presentation)\\b")){t.category="工作";r.categoryRecognized=true;}
            else if(contains(s,"买菜|购物|买牛奶|取快递|家务|衣柜|散步|运动|跑步|吃药|还款|\\b(groceries|shopping|laundry|medicine|workout)\\b")){t.category="生活";r.categoryRecognized=true;}
        }
        return s;
    }
    private static int englishNumber(String value){String[] n={"zero","one","two","three","four","five","six","seven","eight","nine","ten","eleven","twelve"};for(int i=0;i<n.length;i++)if(n[i].equalsIgnoreCase(value))return i;return Integer.parseInt(value);}
    private static String replaceEnglishTimes(String s){
        Matcher m=Pattern.compile("\\b(?:at\\s+)?(one|two|three|four|five|six|seven|eight|nine|ten|eleven|twelve|\\d{1,2})(?::(\\d{2}))?\\s*(a\\.?m\\.?|p\\.?m\\.?)(?![a-z])",CI).matcher(s);StringBuffer out=new StringBuffer();
        while(m.find()){int h=englishNumber(m.group(1)),minute=m.group(2)==null?0:Integer.parseInt(m.group(2));if(h<1||h>12)throw new DateTimeException("Invalid 12-hour time");h=h%12+(m.group(3).toLowerCase(Locale.ROOT).startsWith("p")?12:0);m.appendReplacement(out,Matcher.quoteReplacement(LocalTime.of(h,minute).toString()));}m.appendTail(out);s=out.toString();
        s=s.replaceAll("(?i)\\bat\\s+midnight\\b","00:00").replaceAll("(?i)\\bat\\s+noon\\b","12:00");
        m=Pattern.compile("\\bat\\s+(one|two|three|four|five|six|seven|eight|nine|ten|eleven|twelve|\\d{1,2})(?:\\s+o['’]clock)?(?![\\d:])\\b",CI).matcher(s);out=new StringBuffer();
        while(m.find())m.appendReplacement(out,englishNumber(m.group(1))+"点");m.appendTail(out);return out.toString().replaceAll("(?i)\\bat\\s+(?=\\d{1,2}:\\d{2})","");
    }
    private static String english(String input,Result r){
        String s=input;
        Matcher reminder=Pattern.compile("(?:\\bremind(?:\\s+me)?\\s+)?\\b(half(?:\\s+an?)?|one|two|three|four|five|six|seven|eight|nine|ten|eleven|twelve|\\d+)\\s+(minutes?|mins?|hours?|days?)\\s+(?:before|early|in advance)\\b",CI).matcher(s);
        if(reminder.find()){String n=reminder.group(1).toLowerCase(Locale.ROOT),unit=reminder.group(2).toLowerCase(Locale.ROOT);int lead=n.startsWith("half")?(unit.startsWith("hour")?30:unit.startsWith("day")?720:0):englishNumber(n)*(unit.startsWith("hour")?60:unit.startsWith("day")?1440:1);if(lead<0||lead>525600)throw new IllegalArgumentException("Reminder lead out of range");r.task.remind=true;r.task.lead=lead;r.reminderRecognized=true;s=s.substring(0,reminder.start())+" "+s.substring(reminder.end());}
        s=replaceEnglishTimes(s);
        s=s.replaceAll("(?i)\\bday after tomorrow\\b","后天").replaceAll("(?i)\\btomorrow\\b","明天").replaceAll("(?i)\\btoday\\b","今天").replaceAll("(?i)\\btonight\\b","今晚");
        Matcher weekday=Pattern.compile("\\b(next|this|every)?\\s*(monday|tuesday|wednesday|thursday|friday|saturday|sunday|mon|tue|wed|thu|fri|sat|sun)\\b",CI).matcher(s);StringBuffer converted=new StringBuffer();
        while(weekday.find()){String day=weekday.group(2).toLowerCase(Locale.ROOT).substring(0,3),prefix=weekday.group(1);String key=day.equals("mon")?"一":day.equals("tue")?"二":day.equals("wed")?"三":day.equals("thu")?"四":day.equals("fri")?"五":day.equals("sat")?"六":"日";weekday.appendReplacement(converted,Matcher.quoteReplacement((prefix!=null&&prefix.equalsIgnoreCase("next")?"下周":prefix!=null&&prefix.equalsIgnoreCase("this")?"本周":prefix!=null&&prefix.equalsIgnoreCase("every")?"每周":"周")+key));}weekday.appendTail(converted);s=converted.toString();
        s=s.replaceAll("(?i)\\b(?:every\\s+day|daily)\\b","每天").replaceAll("(?i)\\b(?:every\\s+week|weekly)\\b","每周").replaceAll("(?i)\\b(?:every\\s+month|monthly)\\b","每月");
        s=s.replaceAll("(?i)\\b(?:in\\s+the\\s+)?(?:evening|night)\\b","晚上").replaceAll("(?i)\\b(?:in\\s+the\\s+)?afternoon\\b","下午").replaceAll("(?i)\\b(?:in\\s+the\\s+)?morning\\b","上午").replaceAll("(?i)\\bnoon\\b","中午");
        return s;
    }
    static int num(String s) {
        if(s.matches("\\d+"))return Integer.parseInt(s);
        s=s.replace('两','二');String digits="零一二三四五六七八九";
        if(s.equals("十"))return 10;
        if(s.contains("十")){String[] p=s.split("十",-1);return (p[0].isEmpty()?1:digits.indexOf(p[0]))*10+(p[1].isEmpty()?0:digits.indexOf(p[1]));}
        return digits.indexOf(s);
    }
    private static String removeDate(String text,Matcher match,Task task){
        return removeDate(text,match.start(),match.end(),task);
    }
    private static String removeDate(String text,int start,int end,Task task){
        String before=text.substring(0,start);
        Matcher suffix=Pattern.compile("^"+SPACE+"(?:之前|以前|前)(?!台|往|进)").matcher(text.substring(end));
        if(suffix.find()){task.deadline=true;end+=suffix.end();}
        Matcher englishBefore=Pattern.compile("\\bbefore\\s*$",CI).matcher(before);
        if(englishBefore.find()){task.deadline=true;before=before.substring(0,englishBefore.start());}
        return before+text.substring(end);
    }
    public static Result parse(String input,LocalDate today,Map<String,String> ranges) {
        return parse(input,today.atStartOfDay(),ranges);
    }
    public static Result parse(String input,LocalDateTime now,Map<String,String> ranges) {
        LocalDate today=now.toLocalDate();
        Result r=new Result();Task t=r.task;t.remind=true;t.lead=30;String s=english(temporalSpacing(temporalVariants(metadata(input.trim(),r))),r).replace("每个星期","每周").replace("每星期","每周").replace("下个星期","下周").replace("这个星期","本周").replace("下下星期","下下周").replace("下星期","下周").replace("本星期","本周").replace("礼拜","周");LocalDate d=null;int requestedAnchor=0;boolean relativeTime=false;
        PlaceParser.Match place=PlaceParser.extract(s);if(place!=null){t.location=place.value;s=s.substring(0,place.start)+" "+s.substring(place.end);}
        Matcher m=Pattern.compile("提前(半|[零一二三四五六七八九十两\\d]+)(?:个)?(小时|分钟|分|天)(?:提醒)?").matcher(s);
        if(m.find()){int n=m.group(1).equals("半")?30:num(m.group(1));t.lead=m.group(1).equals("半")?30:n*(m.group(2).equals("小时")?60:m.group(2).equals("天")?1440:1);if(t.lead<0||t.lead>525600)throw new IllegalArgumentException("Reminder lead out of range");t.remind=true;r.reminderRecognized=true;s=s.replace(m.group(),"");}
        m=Pattern.compile("(半|[零一二三四五六七八九十两\\d]+)"+SPACE+"(?:个"+SPACE+")?(小时|分钟|分|秒钟|秒)"+SPACE+"(?:之后|以后|后)").matcher(s);
        if(m.find()){
            double count=m.group(1).equals("半")?.5:num(m.group(1));String unit=m.group(2);long seconds=(long)(count*(unit.equals("小时")?3600:unit.startsWith("秒")?1:60));
            if(seconds<=0||seconds>366L*86400)throw new DateTimeException("Relative time out of range");
            LocalDateTime at=now.withNano(0).plusSeconds(seconds);d=at.toLocalDate();t.time=at.toLocalTime().toString();relativeTime=true;
            if(!r.reminderRecognized){t.lead=0;r.reminderRecognized=true;}s=s.substring(0,m.start())+s.substring(m.end());
        }
        for(String repeat:new String[]{"每天","每周","每月"})if(s.contains(repeat)){t.repeat=repeat;r.repeatRecognized=true;if(repeat.equals("每周"))s=s.replaceAll("每周(?=[一二三四五六日天])","周");s=s.replace(repeat,"");break;}
        m=Pattern.compile("#([^\\s#]+)").matcher(s);if(m.find()){t.category=m.group(1);s=s.replace(m.group(),"");}
        if(t.repeat.equals("每月")){
            m=Pattern.compile("([一二三四五六七八九十两\\d]+)[日号]|\\bon\\s+(?:the\\s+)?(\\d{1,2})(?:st|nd|rd|th)?\\b",CI).matcher(s);
            if(m.find()){requestedAnchor=num(m.group(1)!=null?m.group(1):m.group(2));if(requestedAnchor<1||requestedAnchor>31)throw new DateTimeException("Invalid monthly day");YearMonth month=YearMonth.from(today);d=month.atDay(Math.min(requestedAnchor,month.lengthOfMonth()));if(d.isBefore(today)){month=month.plusMonths(1);d=month.atDay(Math.min(requestedAnchor,month.lengthOfMonth()));}s=s.substring(0,m.start())+" "+s.substring(m.end());}
        }
        if(d==null){DateParser.DateMatch date=DateParser.extract(s,today);if(date!=null){d=date.date;s=removeDate(s,date.start,date.end,t);}}
        if(d==null)for(String word:new String[]{"大后天","后天","明天","今天","今晚","明晚"})if(s.contains(word)){d=today.plusDays(word.equals("大后天")?3:word.equals("后天")?2:word.equals("明天")||word.equals("明晚")?1:0);int at=s.indexOf(word);s=removeDate(s,at,at+word.length(),t);if(word.endsWith("晚"))s=s.substring(0,at)+"晚上"+s.substring(at);break;}
        if(d==null){m=Pattern.compile("(下下周|下周|本周|这周|周|星期)([一二三四五六日天1-7])").matcher(s);if(m.find()){int day=m.group(2).matches("[1-7]")?Integer.parseInt(m.group(2)):"一二三四五六日".indexOf(m.group(2).replace("天","日"))+1;LocalDate monday=today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));d=monday.plusDays(day-1+(m.group(1).equals("下下周")?14:m.group(1).equals("下周")?7:0));if(d.isBefore(today)&&(m.group(1).equals("周")||m.group(1).equals("星期")))d=d.plusWeeks(1);s=removeDate(s,m,t);}}
        boolean sameClock=false,inheritedClock=false;
        m=Pattern.compile("(?:的"+SPACE+")?(?:[这這]"+SPACE+"[个個]?"+SPACE+"(?:[时時]"+SPACE+"[候间間刻]|[点點])|此[时時]|同一"+SPACE+"[时時]"+SPACE+"[间間刻])").matcher(s);
        if(d!=null&&m.find()){sameClock=true;s=s.substring(0,m.start())+" "+s.substring(m.end());}
        // Date-only 'before' means a deadline, not an all-day event. Leave 前台/前往 intact.
        m=Pattern.compile("^(\\s*)(?:之前|以前|前)(?!台|往|进)").matcher(s);
        if(d!=null&&m.find()){t.deadline=true;s=s.substring(m.end());}
        TimeRangeParser.Range range=relativeTime?null:TimeRangeParser.extract(s);
        if(range!=null){t.time=range.start;t.endTime=range.end;t.endDayOffset=range.nextDay;s=removeDate(s,range.from,range.to,t);}
        String period="";
        if(range==null&&!relativeTime){
        for(String p:ranges.keySet())if(s.contains(p)){period=p;int at=s.indexOf(p);s=removeDate(s,at,at+p.length(),t);break;}
        m=Pattern.compile("(?<!\\d)(\\d{1,2})[:：](\\d{2})(?!\\d)").matcher(s);
        boolean exact=false;
        if(m.find()){int h=Integer.parseInt(m.group(1)),minute=Integer.parseInt(m.group(2));h=adjustHour(h,period);t.time=LocalTime.of(h,minute).toString();exact=true;s=removeDate(s,m,t);}
        if(!exact){m=Pattern.compile("([零一二三四五六七八九十两\\d]+)[点时](半|一刻|三刻|[零一二三四五六七八九十两\\d]+分?)?").matcher(s);if(m.find()){int h=adjustHour(num(m.group(1)),period), minute=0;String v=m.group(2);if(v!=null)minute=v.equals("半")?30:v.equals("一刻")?15:v.equals("三刻")?45:num(v.replace("分",""));t.time=LocalTime.of(h,minute).toString();exact=true;s=removeDate(s,m,t);}}
        // Only use a broad period when there is no explicit clock (晚上18:30 is a single clock).
        if(!period.isEmpty()&&!exact){t.period=period;t.time=ranges.get(period).split("-")[0];}
        }
        if(sameClock&&t.time.isEmpty()){t.time=now.withNano(0).toLocalTime().toString();inheritedClock=true;if(!r.reminderRecognized&&!t.deadline){t.lead=0;r.reminderRecognized=true;}}
        if(d==null&&(!t.time.isEmpty()||!t.repeat.equals("不重复")))d=today;
        if(d!=null){t.date=d.toString();t.anchorDay=requestedAnchor>0?requestedAnchor:d.getDayOfMonth();r.recognized=true;}
        m=Pattern.compile("^\\s*(?:之前|以前|前)(?!台|往|进)").matcher(s);if(d!=null&&m.find()){t.deadline=true;s=s.substring(m.end());}
        m=Pattern.compile("不提醒|不要提醒|\\bno reminder\\b",CI).matcher(s);if(m.find()){t.remind=false;t.reminderEveningTime="";r.reminderRecognized=true;s=s.substring(0,m.start())+s.substring(m.end());}
        if(t.deadline&&t.time.isEmpty()&&d!=null){t.beforeDay=true;t.date=d.minusDays(1).toString();}
        if(t.deadline&&!r.reminderRecognized){
            t.remind=true;
            applyDeadlineReminder(t,ranges);
            r.reminderRecognized=true;
        }
        if(!r.reminderRecognized)r.reminderRecognized=true;
        s=s.replaceAll("提醒我|提醒一下我|提醒一下","");
        t.title=s.replaceAll("^[，,、;；\\s\\p{Zs}]+|[，,、;；\\s\\p{Zs}]+$","").replaceAll("[\\s\\p{Zs}]+"," ").trim();if(inheritedClock&&t.title.isEmpty())t.title="定时提醒";t.refreshBeforeTitle();return r;
    }
    private static int adjustHour(int h,String p){if((p.equals("下午")||p.equals("晚上")||p.equals("中午"))&&h<12)h+=12;if(p.equals("凌晨")&&h==12)h=0;return h;}
    public static void applyDeadlineReminder(Task t,Map<String,String> ranges){
        t.reminderEveningTime="";t.reminderPeriod="";t.reminderPeriodTime="";t.reminderPeriodDayOffset=0;
        if(t.time.isEmpty()){t.reminderEveningTime=ranges.get("晚上").split("-")[0];LocalTime evening=LocalTime.parse(t.reminderEveningTime);t.lead=1440-evening.getHour()*60-evening.getMinute();return;}
        ArrayList<Map.Entry<String,String>> slots=new ArrayList<>(ranges.entrySet());slots.sort(Comparator.comparing(e->e.getValue().split("-")[0]));
        int cutoff=LocalTime.parse(t.time).toSecondOfDay(),current=slots.size()-1;
        for(int i=0;i<slots.size();i++){int start=LocalTime.parse(slots.get(i).getValue().split("-")[0]).toSecondOfDay();if(start<=cutoff)current=i;}
        int previous=(current+slots.size()-1)%slots.size();Map.Entry<String,String> slot=slots.get(previous);String clock=slot.getValue().split("-")[0];int start=LocalTime.parse(clock).toSecondOfDay();
        t.reminderPeriod=slot.getKey();t.reminderPeriodTime=clock;t.reminderPeriodDayOffset=start>=cutoff?-1:0;t.lead=(cutoff-start-t.reminderPeriodDayOffset*86400)/60;
    }
}
