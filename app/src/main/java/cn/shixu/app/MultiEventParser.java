package cn.shixu.app;

import java.time.*;
import java.util.*;
import java.util.regex.*;

/** Conservative, local candidates. Selection is always required before any database write. */
public final class MultiEventParser {
    static final String DAY="(?:(?:\\d{4}年)?\\d{1,2}\\s*月\\s*\\d{1,2}\\s*[日号]?|\\d{4}[-/]\\d{1,2}[-/]\\d{1,2}|(?<![\\d:.])\\d{1,2}[/\\.]\\d{1,2}(?!\\d)|大后天|后天|明天|今天|(?:下下|下|本)?(?:周|星期)[一二三四五六日天])";
    static final Pattern ANCHOR=Pattern.compile(DAY);
    static final String ACTION="参加|提交|完成|上交|会议|讨论|讲座|培训|考试|典礼|仪式|分享|拓展|汇报|报名|签到|面试|上课|领取|归还|办理|交流|出门|作业|材料";
    public static final class Candidate {
        public Task task; public String source; public boolean yearless;
        Candidate(Task t,String source,boolean yearless){task=t;this.source=source;this.yearless=yearless;}
    }
    public static List<Candidate> extract(String input,LocalDateTime now,Map<String,String> ranges){return extract(input,now,ranges,now.getYear());}
    public static List<Candidate> extract(String input,LocalDateTime now,Map<String,String> ranges,int year){
        List<Candidate> out=new ArrayList<>();if(input==null||input.length()>20000)return out;
        String text=input.replace('：',':').replace('／','/').replace('．','.');
        int appendix=text.indexOf("\n\n识别原文（供核对）:");if(appendix>=0)text=text.substring(0,appendix);
        // Each candidate needs its own date plus substantive event text. End clocks are never new events.
        Matcher dates=ANCHOR.matcher(text);List<Integer> positions=new ArrayList<>();
        boolean structured=Pattern.compile("(?m)^.*\\|.*\\|\\s*地点[:：]").matcher(text).find();
        if(structured){int offset=0;for(String line:text.split("\n",-1)){if(line.contains("|")&&(ANCHOR.matcher(line).find()||line.startsWith("[日期待核对]")))positions.add(offset);offset+=line.length()+1;}}else while(dates.find()){
            // Inspect the complete clause before dropping its prefix: “争取明天...” is
            // supporting context, not a second confirmed event merely because it has a date.
            int left=dates.start(),right=dates.end();String delimiters="，,。；;！!\r\n";
            while(left>0&&delimiters.indexOf(text.charAt(left-1))<0)left--;
            while(right<text.length()&&delimiters.indexOf(text.charAt(right))<0)right++;
            if(!NotificationExtractor.tentative(text.substring(left,right)))positions.add(dates.start());
        }
        if(positions.size()<2||positions.size()>100)return out;
        for(int i=0;i<positions.size();i++){
            String part=text.substring(positions.get(i),i+1==positions.size()?text.length():positions.get(i+1)).replaceAll("[，,。；;\\s]+$","").trim();
            boolean table=part.contains("|");String[] columns=part.split("\\|",-1);
            String dateAndTitle=table?columns[0].trim()+" "+(columns.length>1?columns[1].trim():""):part;
            if(!table&&!Pattern.compile(ACTION).matcher(part).find())continue;
            boolean yearless=Pattern.compile("^\\d{1,2}\\s*(?:月|[/\\.])").matcher(part).find();
            String evidence=dateAndTitle;if(yearless){evidence=evidence.replaceFirst("^(\\d{1,2})\\s*[/\\.]\\s*(\\d{1,2})(?!\\d)","$1月$2日");evidence=year+"年"+evidence;}
            try{
                Task task=table?TimeParser.parse(evidence,now,ranges).task:NotificationExtractor.extract(evidence,now,ranges).task;
                if(table){task.title=columns[1].trim();task.refreshBeforeTitle();if(columns.length>2)task.location=columns[2].replaceFirst("^\\s*地点[：:]","").trim();}
                if(part.startsWith("[日期待核对]")){task.date="";task.time="";task.endTime="";task.remind=false;}
                if((task.date.isEmpty()&&!table)||task.title.isEmpty()||task.title.equals("待确认事项"))continue;
                task.note=input;task.recognitionWarning=yearless?"原文未写年份，暂按"+year+"年，请核对。":"";
                if(task.title.equals("待补充标题"))task.recognitionWarning+=" 标题未识别，请对照图片补充。";
                if(task.date.isEmpty())task.recognitionWarning+=" 日期不确定，请补充。";
                if(task.date.compareTo(now.toLocalDate().toString())<0||task.triggerMillis()<=now.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()){task.remind=false;task.recognitionWarning+=" 时间已过，不开启提醒。";}
                if(task.time.isEmpty())task.remind=false;
                out.add(new Candidate(task,part,yearless));
            }catch(IllegalArgumentException|DateTimeException invalid){
                Task task=new Task();task.title=table&&columns.length>1?columns[1].trim():part;task.note=input;task.recognitionWarning="日期或时间不合法，请修改后保存。";out.add(new Candidate(task,part,yearless));
            }
        }
        // One useful candidate among several incidental dates retains the original single-event workflow.
        return out.size()>1?out:new ArrayList<>();
    }
    private MultiEventParser(){}
}
