package cn.shixu.app;

import java.time.*;
import java.util.regex.*;

/** Calendar expressions only. The original span is kept so unrelated message text survives. */
final class DateParser {
    private static final String W="[\\s\\p{Zs}]*";
    private static final String N="[零〇一二三四五六七八九十两\\d]+";
    static final class DateMatch {
        final LocalDate date;final int start,end;
        DateMatch(LocalDate date,Matcher match){this.date=date;start=match.start();end=match.end();}
    }
    private static int number(String value){return TimeParser.num(value.replace('〇','零'));}
    private static int year(String value){
        StringBuilder digits=new StringBuilder();
        for(char c:value.toCharArray())digits.append(c>='0'&&c<='9'?c:(char)('0'+"零一二三四五六七八九".indexOf(c=='〇'?'零':c)));
        return Integer.parseInt(digits.toString());
    }
    private static LocalDate upcoming(int month,int day,LocalDate today){
        MonthDay target=MonthDay.of(month,day);
        for(int year=today.getYear();year<=today.getYear()+8;year++)if(target.isValidYear(year)){
            LocalDate date=target.atYear(year);if(!date.isBefore(today))return date;
        }
        throw new DateTimeException("No upcoming calendar date");
    }
    private static boolean clauseStart(String prefix){return prefix.matches("(?s).*(?:^|[，,。;；!！?？\\n])[\\s\\p{Zs}]*")||prefix.trim().isEmpty();}
    private static boolean dateContext(String prefix,String suffix){
        return clauseStart(prefix)||prefix.matches("(?s).*(?:日期|时间|截止|定于|安排在|在|于|到)[:：]?[\\s\\p{Zs}]*")
            ||suffix.matches("(?s)^[\\s\\p{Zs}]*(?:前|之前|以前|日|号|(?:周|星期)[一二三四五六日天]|早上|上午|中午|下午|晚上|凌晨|\\d{1,2}[点时:：]).*");
    }
    private static boolean versionContext(String prefix){return prefix.matches("(?is).*(?:版本|版|version)[:：]?"+W);}
    static DateMatch extract(String text,LocalDate today){
        Matcher m=Pattern.compile("(?<![\\dA-Za-z./-])(\\d{4})"+W+"([./-])"+W+"(\\d{1,2})"+W+"\\2"+W+"(\\d{1,2})(?![\\d./-])").matcher(text);
        while(m.find())if(!versionContext(text.substring(0,m.start())))return new DateMatch(LocalDate.of(year(m.group(1)),number(m.group(3)),number(m.group(4))),m);
        m=Pattern.compile("([零〇一二三四五六七八九\\d]{4})"+W+"年"+W+"("+N+")"+W+"月"+W+"("+N+")"+W+"[日号]?").matcher(text);
        if(m.find())return new DateMatch(LocalDate.of(year(m.group(1)),number(m.group(2)),number(m.group(3))),m);
        m=Pattern.compile("(今年|明年|后年)?"+W+"("+N+")"+W+"月"+W+"("+N+")(?:"+W+"[日号])?").matcher(text);
        if(m.find()){
            int month=number(m.group(2)),day=number(m.group(3));String y=m.group(1);
            return new DateMatch(y==null?upcoming(month,day,today):LocalDate.of(today.getYear()+(y.equals("明年")?1:y.equals("后年")?2:0),month,day),m);
        }
        m=Pattern.compile("(?<![\\dA-Za-z./-])(\\d{1,2})"+W+"([./-])"+W+"(\\d{1,2})(?![\\d./-])").matcher(text);
        while(m.find()){
            String prefix=text.substring(0,m.start()),suffix=text.substring(m.end());
            // A decimal quantity, software version or a clock range needs to remain ordinary text.
            if(suffix.matches("(?is)^"+W+"(?:元|块|斤|公斤|千克|米|厘米|毫米|升|毫升|%|％|kg\\b|g\\b|km\\b|cm\\b|mm\\b|ml\\b|点|时|[:：]|am\\b|pm\\b).*")
                ||prefix.matches("(?is).*(?:版本|版|version|价格|单价|金额|花费|售价|重|长|宽|高|下午|上午|晚上|早上|中午|凌晨)[:：]?"+W))continue;
            if(!m.group(2).equals("/")&&!dateContext(prefix,suffix))continue;
            return new DateMatch(upcoming(number(m.group(1)),number(m.group(3)),today),m);
        }
        m=Pattern.compile("(下下个月|下下月|下个月|下月|这个月|本月|这月|上个月|上月)"+W+"(?:的"+W+")?("+N+")"+W+"[日号]").matcher(text);
        if(m.find()){
            String unit=m.group(1);int shift=unit.startsWith("下下")?2:unit.startsWith("下")?1:unit.startsWith("上")?-1:0;
            return new DateMatch(YearMonth.from(today).plusMonths(shift).atDay(number(m.group(2))),m);
        }
        m=Pattern.compile("(下下个?|下个?|这个?|本)?"+W+"月(?:底|末)").matcher(text);
        if(m.find()){
            String unit=m.group(1);int shift=unit==null?0:unit.startsWith("下下")?2:unit.startsWith("下")?1:0;
            return new DateMatch(YearMonth.from(today).plusMonths(shift).atEndOfMonth(),m);
        }
        m=Pattern.compile("("+N+")"+W+"(天|周|个星期|星期)"+W+"(?:之后|以后|后)").matcher(text);
        if(m.find()){
            int count=number(m.group(1));if(count<0||count>3660)throw new DateTimeException("Relative date out of range");
            return new DateMatch(today.plusDays((long)count*(m.group(2).equals("天")?1:7)),m);
        }
        m=Pattern.compile("(?<![\\d一二三四五六七八九十])("+N+")"+W+"[日号]").matcher(text);
        while(m.find()){
            String prefix=text.substring(0,m.start()),suffix=text.substring(m.end());
            if(!dateContext(prefix,suffix)||prefix.matches("(?s).*"+N+"[\\s\\p{Zs}]+")||suffix.matches("^(?:文件|楼|车|线|房|选手).*") )continue;
            int day=number(m.group(1));if(day<1||day>31)throw new DateTimeException("Invalid day of month");
            for(int shift=0;shift<12;shift++){YearMonth month=YearMonth.from(today).plusMonths(shift);if(day<=month.lengthOfMonth()){LocalDate date=month.atDay(day);if(!date.isBefore(today))return new DateMatch(date,m);}}
        }
        return null;
    }
}
