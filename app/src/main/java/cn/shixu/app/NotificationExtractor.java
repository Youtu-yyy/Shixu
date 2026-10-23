package cn.shixu.app;

import java.time.*;
import java.util.*;
import java.util.regex.*;

/** Conservative local notice extraction. Always retains the source for human review. */
public final class NotificationExtractor {
    private static final String ACTION="提交|上交|交齐|交材料|交作业|完成|缴费|报名|签到|参加|开会|会议|讨论|讲座|上课|考试|答辩|面试|领取|集合|培训|汇报|办理|归还|报送|交给";
    private static final String TEMPORAL="\\d{1,4}\\s*[月/.-]\\s*\\d{1,2}|今天|明天|后天|今晚|明晚|周[一二三四五六日天]|星期[一二三四五六日天]|\\d{1,2}[:：]\\d{2}|[零一二三四五六七八九十两\\d]+点|上午|下午|中午|晚上|早上|截止";
    private static final String TENTATIVE="争取|可能|暂定|待定|另行通知|拟于|计划|预计|取消|推迟|延期|不再|不用|无需";
    public static final class Result {
        public Task task;
        public String location="",warning="";
    }
    private static boolean has(String text,String pattern){return Pattern.compile(pattern,Pattern.CASE_INSENSITIVE).matcher(text).find();}
    private static String clean(String text){return text.replaceAll("@[所有人全体成员]+[\\s\\p{Zs}]*", "").replaceAll("^[\\s\\p{Zs}【\\[]*(?:通知|温馨提示|重要通知)[】\\]:：\\s]*", "").trim();}
    public static boolean looksLikeNotice(String text){return (text.length()>45&&(has(text,ACTION)||has(text,"通知|请各|请同学|请大家")))||(text.length()>12&&has(text,"@所有人|@全体成员|通知[：:]|请同学|请大家|请各位")&&has(text,ACTION));}
    private static String location(String text){
        PlaceParser.Match match=PlaceParser.extract(text);return match==null?"":match.value;
    }
    private static String title(String parsed,String source,String place){
        String s=clean(parsed);
        s=s.replaceAll("[（(]\\s*(?:具\\s*体|名单|请|备注|注意|说明)[\\s\\S]*$", "");
        if(!place.isEmpty())s=s.replaceAll("(?:地点[:：]?|在|于)?\\s*"+Pattern.quote(place),"");
        s=s.replaceAll("(?:时间|日期|地点)[：:]", "").replaceAll("^[\\s，,:：]*(?:请于|定于|将于|安排在|于|在|请大家|请各位同学|请同学们|请同学|请各位|请各|请)+", "").trim();
        Matcher action=Pattern.compile("(提交|上交|交齐|缴费|报名|签到|领取|集合|办理|归还|报送|完成|参加)(.{0,45})").matcher(s);
        if(action.find()){
            s=action.group().replaceAll("(?:的同学|的同事|的老师|的人员).*$", "").replaceAll("(?:并|同时|届时|到时候|辛苦|谢谢|感谢|请大家|请各位).*$", "");
        }else{
            Matcher event=Pattern.compile("(?:召开|举行|开展|进行|组织)([^，,。；;]{0,28}?(?:会议|讨论|讲座|培训|考试|答辩|面试|汇报))").matcher(s);
            if(event.find())s="参加"+(event.group(1).equals("讨论")&&has(source,"课题")?"课题讨论":event.group(1));
            else if(has(s,"讨论"))s="参加"+(has(source,"课题")?"课题":"")+"讨论";
            else if(has(s,"会议|开会"))s="参加会议";
            else if(has(s,"讲座"))s="参加"+s.replaceFirst("^有","");
        }
        s=s.replaceAll("^[\\s，,:：、]+|[\\s，,:：、]+$", "");
        if(s.isEmpty())return "待确认事项";
        return s.length()>40?s.substring(0,39)+"…":s;
    }
    public static Result extract(String original,LocalDateTime now,Map<String,String> ranges){
        if(original==null||original.trim().isEmpty())throw new IllegalArgumentException("请先粘贴通知");
        if(original.length()>20000)throw new IllegalArgumentException("通知过长，请分段提炼（最多20000字）");
        String source=clean(original),text=source.replaceAll("[\\p{Zs}\\t]+"," ");
        // Explanatory brackets may contain hard OCR line breaks; keep them in notes,
        // but remove them before splitting the actionable sentence into clauses.
        text=text.replaceAll("[（(]\\s*(?:具\\s*体|名单|请|备注|注意|说明)[^）)]*[）)]", "，");
        String[] clauses=text.split("[，,。；;！!\\r\\n]+",-1);
        int best=-1,bestScore=Integer.MIN_VALUE,actions=0;boolean certain=false;
        for(int i=0;i<clauses.length;i++){
            String c=clauses[i].trim();if(c.isEmpty()||!has(c,ACTION))continue;
            actions++;boolean tentative=has(c,TENTATIVE)||has(c,"(?:我|老师|负责人|主办方)(?:会|将|明天|今天|负责).*?(?:整理|梳理|发给|发送)");
            int score=10+(has(c,TEMPORAL)?8:0)+(has(c,"请|务必|必须|要求|定于|召开|举行")?5:0);
            if(i>0&&has(clauses[i-1],TEMPORAL)&&!has(clauses[i-1],ACTION))score+=8;
            if(tentative)score-=40;else certain=true;
            if(score>bestScore){bestScore=score;best=i;}
        }
        Result result=new Result();List<String> warnings=new ArrayList<>();
        if(best<0){result.task=new Task();result.task.title="待确认事项";warnings.add("未找到明确的主要事项，请补充标题和时间。");}
        else{
            String selected=clauses[best].trim(),context="";
            // Only inherit nearby date/place context without another action or uncertain wording.
            for(int n=best-1;n>=Math.max(0,best-2);n--){String before=clauses[n].trim();if(has(before,ACTION)||has(before,TENTATIVE))break;if(has(before,TEMPORAL)||!location(before).isEmpty())context=before+"，"+context;}
            if(best+1<clauses.length){String after=clauses[best+1].trim();if(has(after,"^(?:时间|地点)[：:]")&&!has(after,ACTION)&&!has(after,TENTATIVE))context+= "，"+after;}
            String evidence=context+selected;
            try{result.task=TimeParser.parse(evidence,now,ranges).task;}
            catch(IllegalArgumentException|DateTimeException invalid){result.task=new Task();warnings.add("通知中的日期或时间不合法，请手动确认。");}
            result.location=location(evidence);result.task.location=result.location;
            result.task.title=title(result.task.title,evidence,result.location);result.task.refreshBeforeTitle();
            if(!certain||has(selected,TENTATIVE)){result.task.date="";result.task.time="";result.task.endTime="";result.task.period="";result.task.deadline=false;warnings.add("主要安排含暂定、取消或变更表述，未自动安排时间，请核对。");}
            if(result.task.date.isEmpty())warnings.add("主要事项未识别到日期，请补充。");
            if(has(evidence,"今天|明天|后天|周[一二三四五六日天]|星期"))warnings.add("相对日期按当前日期计算；旧通知请核对发送日期。");
            if(actions>1)warnings.add("已选一个主要事项，其他安排保留在原文中，请核对取舍。");
        }
        if(!result.task.date.isEmpty()&&result.task.reminderBaseMillis()<now.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())warnings.add("识别时间已过，请修改后再开启提醒。");
        result.warning=String.join("\n",warnings);
        result.task.recognitionWarning=result.warning;
        result.task.note=original;
        if(result.task.date.isEmpty()||(result.task.time.isEmpty()&&!result.task.beforeDay)||(!result.warning.isEmpty()&&has(result.warning,"时间已过|未自动安排时间")))result.task.remind=false;
        return result;
    }
}

