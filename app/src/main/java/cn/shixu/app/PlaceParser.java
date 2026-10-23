package cn.shixu.app;
import java.util.regex.*;

/** Extract only explicit location phrases; leave names and unmarked numbers untouched. */
final class PlaceParser {
    static final class Match {final String value;final int start,end;Match(String value,int start,int end){this.value=value;this.start=start;this.end=end;}}
    static Match extract(String text){
        Matcher m=Pattern.compile("(?:地点|地址)[：:]\\s*([^，,。；;！!\\r\\n]{1,40})").matcher(text);
        if(m.find()){String value=m.group(1).replaceAll("\\s*(?:请|参加|召开|举行|进行|提交|开会|讨论).*$", "").trim();if(!value.isEmpty())return new Match(value,m.start(),m.start(1)+m.group(1).indexOf(value)+value.length());}
        String named="[\\p{IsHan}A-Za-z0-9-]{0,22}?(?:教室|会议室|报告厅|礼堂|操场|体育馆|图书馆|实验室|办公室|食堂|活动中心|教学楼|办公楼)(?:[A-Za-z0-9-]+)?(?:教室|会议室|报告厅|实验室|办公室)?";
        m=Pattern.compile("(?:在|于|至|到|去)\\s*("+named+"|[A-Za-z]{1,5}\\s*-?\\s*\\d[0-9Oo\\s]{1,5}(?:教室)?|[一二三四五六七八九十]教\\d{2,4})",Pattern.CASE_INSENSITIVE).matcher(text);
        if(!m.find())return null;
        String value=m.group(1).trim();
        // Only repair O/0 inside the numeric suffix of an explicitly marked room code.
        if(value.matches("[A-Za-z]{1,5}\\s*-?\\s*\\d[0-9Oo\\s]{1,5}(?:教室)?")){
            value=value.replaceAll("\\s+","");int first=0;while(first<value.length()&&!Character.isDigit(value.charAt(first)))first++;
            value=value.substring(0,first)+value.substring(first).replace('O','0').replace('o','0');
        }
        return new Match(value,m.start(),m.end());
    }
}
