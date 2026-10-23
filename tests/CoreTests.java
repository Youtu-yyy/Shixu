package cn.shixu.app;
import java.time.*;
import java.util.*;
public class CoreTests {
    static int passed;
    static void eq(Object expected,Object actual){if(!Objects.equals(expected,actual))throw new AssertionError("Expected "+expected+", got "+actual);passed++;}
    static Task parse(String s){return TimeParser.parse(s,LocalDate.of(2026,10,21),TimeParser.DEFAULTS).task;}
    public static void main(String[] args){
        for(String separator:new String[]{"-"," - ","—","–","－","−","‑","﹣","\u200b-\u200b"}){
            Task clockRange=parse("明天14：00"+separator+"20：00开会");eq("14:00",clockRange.time);eq("20:00",clockRange.endTime);eq("开会",clockRange.title);
        }
        Task fullWidthRange=parse("明天１４ ： ００ － ２０ ： ００开会，备注：１４：００－２０：００原文");eq("14:00",fullWidthRange.time);eq("20:00",fullWidthRange.endTime);eq("开会",fullWidthRange.title);eq("１４：００－２０：００原文",fullWidthRange.note);
        eq(2,new Task().priority);eq(2,parse("买牛奶").priority);
        eq(1,parse("明天交材料，中优先级").priority);eq(1,parse("明天交材料，优先级普通").priority);
        eq(2,parse("明天交材料，优先级低").priority);
        Task t=parse("明天中午交材料，提前10分钟提醒");eq("2026-10-22",t.date);eq("11:35",t.time);eq("中午",t.period);eq("交材料",t.title);eq(10,t.lead);eq(true,t.remind);
        t=parse("周六晚上整理衣柜");eq("2026-10-24",t.date);eq("18:30",t.time);eq("整理衣柜",t.title);
        t=parse("明天下午三点半开会 提前半小时提醒 #工作");eq("15:30",t.time);eq("",t.period);eq(30,t.lead);eq("工作",t.category);eq("开会",t.title);
        t=parse("买牛奶");eq("",t.date);eq("买牛奶",t.title);
        t=parse("下周一上午九点交报告");eq("2026-10-26",t.date);eq("09:00",t.time);
        t=parse("今晚看书");eq("2026-10-21",t.date);eq("18:30",t.time);
        t=parse("每天晚上八点吃药");eq("每天",t.repeat);eq("20:00",t.time);eq("吃药",t.title);
        t=parse("2027-01-31 09:00 还款");t.repeat="每月";t.advance();eq("2027-02-28",t.date);t.advance();eq("2027-03-31",t.date);
        t=parse("10月25日下午1:30整理文件");eq("13:30",t.time);eq("2026-10-25",t.date);
        LinkedHashMap<String,String> custom=new LinkedHashMap<>(TimeParser.DEFAULTS);custom.put("晚上","19:00-24:00");t=TimeParser.parse("明天晚上运动",LocalDate.of(2026,10,21),custom).task;eq("19:00",t.time);
        boolean invalid=false;try{parse("明天25:00开会");}catch(DateTimeException e){invalid=true;}eq(true,invalid);
        invalid=false;try{parse("2026-02-30办事");}catch(DateTimeException e){invalid=true;}eq(true,invalid);
        t=parse("每周三下午三点例会");eq("每周",t.repeat);eq("2026-10-21",t.date);eq("例会",t.title);
        t=parse("明天早上六点一刻跑步");eq("06:15",t.time);
        t=parse("明天凌晨十二点备份");eq("00:00",t.time);
        t=parse("明天11:35交材料");t.lead=10;long trigger=t.triggerMillis();eq(LocalDate.of(2026,10,22).atTime(11,25).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),trigger);
        t=parse("每天晚上八点吃药");t.advancePast(LocalDate.of(2026,10,24).atTime(22,0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());eq("2026-10-25",t.date);
        t=parse("Friday evening, hand out homework");eq("2026-10-23",t.date);eq("18:30",t.time);eq("晚上",t.period);eq("hand out homework",t.title);eq("学习",t.category);
        t=parse("Every Friday evening, hand out homework, remind me 30 minutes before");eq("每周",t.repeat);eq("2026-10-23",t.date);eq(30,t.lead);eq(true,t.remind);eq("hand out homework",t.title);
        t=parse("Next Monday at 3:30 pm, project meeting priority: high notes: bring the report tomorrow");eq("2026-10-26",t.date);eq("15:30",t.time);eq("工作",t.category);eq(0,t.priority);eq("bring the report tomorrow",t.note);eq("project meeting",t.title);
        t=parse("周五晚上交作业，优先级：高，提前半小时提醒，备注：带练习册");eq("2026-10-23",t.date);eq("学习",t.category);eq(0,t.priority);eq(30,t.lead);eq("带练习册",t.note);eq("交作业",t.title);
        t=parse("每月31号晚上还款");eq("2026-10-31",t.date);eq(31,t.anchorDay);eq("每月",t.repeat);eq("还款",t.title);
        t=parse("monthly on the 15th at 9 am, report");eq("2026-11-15",t.date);eq("09:00",t.time);eq("每月",t.repeat);
        t=parse("Tomorrow at 12 am, homework");eq("00:00",t.time);eq("2026-10-22",t.date);
        t=parse("tonight read notes: tomorrow 5 pm");eq("2026-10-21",t.date);eq("18:30",t.time);eq("tomorrow 5 pm",t.note);
        t=parse("明天开会 分类：个人项目");eq("个人项目",t.category);
        t=parse("Not urgent, buy milk tomorrow");eq(2,t.priority);
        t=parse("明天不紧急的工作");eq(2,t.priority);
        t=parse("每周五晚上交作业，优先级高，提前半小时提醒");eq("2026-10-23",t.date);eq("晚上",t.period);eq("每周",t.repeat);eq("学习",t.category);eq(0,t.priority);eq(30,t.lead);eq("交作业",t.title);
        t=parse("每个星期五下午三点开会，提前半个小时提醒");eq("2026-10-23",t.date);eq("15:00",t.time);eq("每周",t.repeat);eq(30,t.lead);
        t=parse("每月十五号中午还款");eq("2026-11-15",t.date);eq(15,t.anchorDay);eq("11:35",t.time);
        t=parse("下周二早上九点到十一点收快递");eq("2026-10-27",t.date);eq("09:00",t.time);eq("11:00",t.endTime);eq("收快递",t.title);eq("",t.period);eq(0,t.endDayOffset);
        t=parse("明天下午三点到五点开会，提前十分钟提醒");eq("15:00",t.time);eq("17:00",t.endTime);eq("开会",t.title);eq(10,t.lead);eq(t.startMillis()-600_000L,t.triggerMillis());
        t=parse("周五18:30—20:00运动");eq("18:30",t.time);eq("20:00",t.endTime);eq("运动",t.title);
        t=parse("明天下午3到5点开会");eq("15:00",t.time);eq("17:00",t.endTime);eq("开会",t.title);
        t=parse("明天上午十一点至下午一点开会");eq("11:00",t.time);eq("13:00",t.endTime);
        t=parse("今晚十一点到凌晨一点备份");eq("23:00",t.time);eq("01:00",t.endTime);eq(1,t.endDayOffset);eq(2*3600_000L,t.endMillis()-t.startMillis());
        t=parse("明天09:00~11:00收快递");eq("09:00",t.time);eq("11:00",t.endTime);
        t=parse("明天03:00—17:00值班");eq("03:00",t.time);eq("17:00",t.endTime);
        t=parse("Friday from 9 am to 11 am, collect parcel");eq("09:00",t.time);eq("11:00",t.endTime);eq("collect parcel",t.title);
        t=parse("Friday from 3 to 5 pm, meeting");eq("15:00",t.time);eq("17:00",t.endTime);eq("meeting",t.title);
        t=parse("明天读三到五页");eq("",t.time);eq("",t.endTime);eq("读三到五页",t.title);
        t=parse("11月7号前交老板的采访初稿");eq("2026-11-06",t.date);eq(true,t.deadline);eq("",t.time);eq("11月7日前交老板的采访初稿",t.title);eq("工作",t.category);eq(true,t.remind);eq("18:30",t.reminderEveningTime);eq(LocalDate.of(2026,11,6).atTime(18,30).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),t.triggerMillis());
        custom.put("晚上","19:00-24:00");t=TimeParser.parse("11月7号前交稿",LocalDate.of(2026,10,21),custom).task;eq("19:00",t.reminderEveningTime);
        t=parse("11月7日前往图书馆");eq(false,t.deadline);eq("前往图书馆",t.title);
        invalid=false;try{parse("明天下午五点到三点开会");}catch(DateTimeException e){invalid=true;}eq(true,invalid);
        t=parse("11月7号前交稿，不提醒");eq(true,t.deadline);eq(false,t.remind);eq("11月7日前交稿",t.title);
        t=parse("明天下午三点前交稿");eq(true,t.deadline);eq("15:00",t.time);eq("交稿",t.title);
        t=parse("交稿 before Friday");eq(true,t.deadline);eq("2026-10-22",t.date);eq("10月23日前交稿",t.title);
        t=parse("11月7号前交稿，提前半小时提醒");eq("",t.reminderEveningTime);eq(30,t.lead);
        t=parse("明天上午九点到十一点收快递");Task clone=t.copy();eq("11:00",clone.endTime);eq(0,clone.endDayOffset);eq(true,clone.dateLabel().contains("09:00—11:00"));
        TimeZone originalZone=TimeZone.getDefault();try{TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"));Task deadline=parse("11月2日前交稿");eq(LocalDate.of(2026,11,1).atTime(18,30).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),deadline.triggerMillis());}finally{TimeZone.setDefault(originalZone);}
        for(String input:new String[]{"11 月 7 日前提交文件","11\u3000月\u30007\u3000日\u3000前提交文件","11\u00a0月\u202f7\u00a0号 前提交文件"}){
            t=parse(input);eq("2026-11-06",t.date);eq(true,t.deadline);eq("11月7日前提交文件",t.title);eq("18:30",t.reminderEveningTime);
            eq(LocalDate.of(2026,11,6).atTime(18,30).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),t.triggerMillis());
        }
        t=parse("2026 / 11 / 07 前提交文件");eq("2026-11-06",t.date);eq(true,t.deadline);eq("11月7日前提交文件",t.title);
        t=parse("明天 09 : 00 到 11 ： 00 收快递");eq("09:00",t.time);eq("11:00",t.endTime);eq("收快递",t.title);
        t=parse("明天\u300009 : 00\u3000到\u00a011 : 00\u3000收快递");eq("09:00",t.time);eq("11:00",t.endTime);eq("收快递",t.title);
        t=parse("下周二早上九 点到十一 点收快递");eq("2026-10-27",t.date);eq("09:00",t.time);eq("11:00",t.endTime);eq("收快递",t.title);
        t=parse("明天下午三 点 三十 分开会");eq("15:30",t.time);eq("开会",t.title);
        t=parse("11 月 7 日 review release notes，备注：保留 11 月 7 日和 file name");eq("review release notes",t.title);eq("保留 11 月 7 日和 file name",t.note);
        t=parse("明天整理 1 2 号文件");eq("整理 1 2 号文件",t.title);eq("",t.time);
        t=parse("11 月 7 日 前往图书馆");eq(false,t.deadline);eq("前往图书馆",t.title);
        String announcement="@所有人\u200510/22晚上18:30在b2201，示例老师请两个综合课程课题的同学进行讨论，我争取明天给大家梳理一下接下来两周要做的事情，辛苦大家尽快推进，到时候准备好 PPT 汇报相关进展";
        t=TimeParser.parse(announcement,LocalDate.of(2026,10,15),TimeParser.DEFAULTS).task;
        eq("2026-10-22",t.date);eq("18:30",t.time);eq("",t.period);eq("",t.endTime);
        eq("b2201",t.location);eq(true,t.title.contains("明天"));eq(true,t.title.contains("PPT 汇报"));eq(false,t.dateLabel().contains("24:00"));
        custom.put("晚上","20:00-24:00");t=TimeParser.parse(announcement,LocalDate.of(2026,10,23),custom).task;eq("2027-10-22",t.date);eq("18:30",t.time);eq("",t.period);
        t=parse("10 / 22 晚上 18 : 30 在b2201讨论");eq("2026-10-22",t.date);eq("18:30",t.time);eq("",t.period);eq("讨论",t.title);eq("b2201",t.location);
        t=parse("10/22晚上讨论");eq("2026-10-22",t.date);eq("晚上",t.period);
        t=parse("明天下午三点到五点开会");eq("15:00",t.time);eq("17:00",t.endTime);eq("2026-10-22",t.date);
        t=parse("11/7前提交文件");eq("2026-11-06",t.date);eq(true,t.deadline);eq("11月7日前提交文件",t.title);
        t=parse("2027/01/31晚上18:30还款");eq("2027-01-31",t.date);eq("18:30",t.time);
        for(String expression:new String[]{"11.7","11-7","11/7","11 . 7","十一月七日","11月七号","今年十一月七日","2026.11.07","2026年11月7日","二零二六年十一月七日","下个月7号","下月七日","7号"}){
            t=parse(expression+"前完成交材料");eq("2026-11-06",t.date);eq(true,t.deadline);eq("11月7日前完成交材料",t.title);eq("18:30",t.reminderEveningTime);
        }
        t=parse("三天后下午三点开会");eq("2026-10-24",t.date);eq("15:00",t.time);eq("开会",t.title);
        t=parse("两周后交稿");eq("2026-11-04",t.date);eq("交稿",t.title);
        t=parse("本月25号开会");eq("2026-10-25",t.date);eq("开会",t.title);
        t=parse("下下个月七号开会");eq("2026-12-07",t.date);eq("开会",t.title);
        t=parse("月底前交稿");eq("2026-10-30",t.date);eq(true,t.deadline);eq("10月31日前交稿",t.title);
        t=parse("下个月底前交稿");eq("2026-11-29",t.date);eq(true,t.deadline);eq("11月30日前交稿",t.title);
        t=parse("明年一月三号讨论");eq("2027-01-03",t.date);eq("讨论",t.title);
        t=parse("二〇二七年一月三日讨论");eq("2027-01-03",t.date);eq("讨论",t.title);
        t=TimeParser.parse("1.3前交稿",LocalDate.of(2026,12,31),TimeParser.DEFAULTS).task;eq("2027-01-02",t.date);
        t=TimeParser.parse("2月29日开会",LocalDate.of(2026,10,21),TimeParser.DEFAULTS).task;eq("2028-02-29",t.date);
        for(String input:new String[]{"11.7元买牛奶","买11.7元的牛奶","版本11.7发布","版本 11.7 发布","v11.7发布","更新1.2.3版本","下午3-5点开会","下午3-5开会","买11.7公斤大米"}){
            t=parse(input);if(!input.startsWith("下午")){eq("",t.date);eq(input,t.title);}else eq("2026-10-21",t.date);
        }
        t=parse("明天下午3-5点开会");eq("2026-10-22",t.date);eq("15:00",t.time);eq("17:00",t.endTime);
        t=parse("11.7晚上18:30开会，我明天整理材料");eq("2026-11-07",t.date);eq("18:30",t.time);eq("",t.period);eq(true,t.title.contains("明天"));
        invalid=false;try{parse("2026.02.30开会");}catch(DateTimeException e){invalid=true;}eq(true,invalid);
        t=parse("发布版本2026.11.7");eq("",t.date);eq("发布版本2026.11.7",t.title);
        t=parse("下星期四开会");eq("2026-10-29",t.date);eq("开会",t.title);
        t=parse("下下周二开会");eq("2026-11-03",t.date);eq("开会",t.title);
        t=parse("周4开会");eq("2026-10-22",t.date);eq("开会",t.title);
        t=parse("三天之后交稿");eq("2026-10-24",t.date);eq("交稿",t.title);
        for(String[] example:new String[][]{{"晚上","18:30","13:30"},{"中午","11:35","09:00"},{"下午","13:30","11:35"},{"上午","09:00","06:00"},{"早上","06:00","00:00"},{"凌晨","00:00","18:30"}}){
            t=parse("周日"+example[0]+"之前完成概率论作业");eq("2026-10-25",t.date);eq(true,t.deadline);eq(example[1],t.time);eq(true,!t.reminderPeriod.isEmpty());eq("",t.reminderEveningTime);eq("完成概率论作业",t.title);eq(true,t.dateLabel().endsWith(example[1]+"前"));
            LocalDate reminderDay=example[0].equals("凌晨")?LocalDate.of(2026,10,24):LocalDate.of(2026,10,25);
            eq(reminderDay.atTime(LocalTime.parse(example[2])).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),t.triggerMillis());
        }
        t=parse("周日之前完成概率论作业");eq("",t.time);eq("18:30",t.reminderEveningTime);eq(LocalDate.of(2026,10,24).atTime(18,30).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),t.triggerMillis());
        t=parse("周日晚上前交稿，提前10分钟提醒");eq(10,t.lead);eq("",t.reminderEveningTime);eq(true,t.remind);
        t=parse("周日中午前交稿，不提醒");eq(false,t.remind);eq("11:35",t.time);eq(true,t.deadline);
        t=parse("周日下午三点前交稿");eq("15:00",t.time);eq(205,t.lead);eq("中午",t.reminderPeriod);eq("",t.reminderEveningTime);
        custom.put("晚上","20:00-24:00");t=TimeParser.parse("周日晚上之前交稿",LocalDate.of(2026,10,21),custom).task;eq("20:00",t.time);eq(390,t.lead);eq(LocalDate.of(2026,10,25).atTime(13,30).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),t.triggerMillis());
        t=parse("周日晚上做作业");eq(false,t.deadline);eq("晚上",t.period);eq(true,t.remind);eq(30,t.lead);
        LocalDateTime now=LocalDateTime.of(2026,10,21,10,20,15);
        for(Object[] example:new Object[][]{{"二十分钟后提醒我出门",1200},{"半小时后提醒我出门",1800},{"两小时之后提醒我出门",7200},{"20 分钟 后提醒我出门",1200},{"二十秒后提醒我出门",20}}){
            t=TimeParser.parse((String)example[0],now,TimeParser.DEFAULTS).task;eq("出门",t.title);eq(true,t.remind);eq(0,t.lead);eq("",t.period);eq(now.plusSeconds((int)example[1]).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),t.triggerMillis());
        }
        t=TimeParser.parse("二十分钟后提醒我出门",LocalDateTime.of(2026,10,21,23,50,25),TimeParser.DEFAULTS).task;eq("2026-10-22",t.date);eq("00:10:25",t.time);
        t=TimeParser.parse("今天下午提醒我出门",now,TimeParser.DEFAULTS).task;eq("2026-10-21",t.date);eq("13:30",t.time);eq("出门",t.title);eq(true,t.remind);eq(30,t.lead);eq(LocalDate.of(2026,10,21).atTime(13,0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),t.triggerMillis());
        t=TimeParser.parse("二十分钟后出门，提前十分钟提醒",now,TimeParser.DEFAULTS).task;eq(10,t.lead);eq(now.plusMinutes(10).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),t.triggerMillis());
        t=TimeParser.parse("半小时后出门，不提醒",now,TimeParser.DEFAULTS).task;eq(false,t.remind);eq("出门",t.title);
        t=parse("周日晚上前交稿，提前半小时提醒");eq(30,t.lead);eq("",t.reminderPeriod);
        t=parse("周日晚上前交稿");clone=t.copy();eq(t.reminderPeriod,clone.reminderPeriod);eq(t.triggerMillis(),clone.triggerMillis());
        try{TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"));t=TimeParser.parse("凌晨前交稿",LocalDateTime.of(2026,11,2,0,0),TimeParser.DEFAULTS).task;eq(LocalDate.of(2026,11,1).atTime(18,30).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),t.triggerMillis());}finally{TimeZone.setDefault(originalZone);}
        Task earlyLow=new Task();earlyLow.date="2026-10-22";earlyLow.time="08:00";earlyLow.priority=2;earlyLow.position=1;
        Task earlyHigh=earlyLow.copy();earlyHigh.priority=0;earlyHigh.position=2;
        Task laterHigh=earlyHigh.copy();laterHigh.time="18:00";laterHigh.position=3;laterHigh.lead=1440;
        Task undatedHigh=new Task();undatedHigh.priority=0;
        ArrayList<Task> ordered=new ArrayList<>(Arrays.asList(laterHigh,earlyLow,earlyHigh,undatedHigh));ordered.sort(TaskOrder.TIME);eq(earlyHigh,ordered.get(0));eq(earlyLow,ordered.get(1));eq(laterHigh,ordered.get(2));eq(undatedHigh,ordered.get(3));
        ordered.sort(TaskOrder.PRIORITY);eq(earlyHigh,ordered.get(0));eq(laterHigh,ordered.get(1));eq(undatedHigh,ordered.get(2));eq(earlyLow,ordered.get(3));
        Task same=earlyHigh.copy();same.position=4;eq(true,TaskOrder.TIME.compare(earlyHigh,same)<0);eq(true,TaskOrder.PRIORITY.compare(earlyHigh,same)<0);
        for(String input:new String[]{"二十分鐘後提醒我交作業","二十分鐘後交作業","二十分鐘后提醒我交作業","二十分钟後提醒我交作業","２０ 分鐘 後提醒我交作業"}){
            t=TimeParser.parse(input,now,TimeParser.DEFAULTS).task;eq("交作業",t.title);eq("学习",t.category);eq(true,t.remind);eq(0,t.lead);eq("2026-10-21",t.date);eq("10:40:15",t.time);eq(now.plusMinutes(20).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),t.triggerMillis());
        }
        t=TimeParser.parse("半小時後提醒我交作業",now,TimeParser.DEFAULTS).task;eq("10:50:15",t.time);eq("交作業",t.title);
        t=TimeParser.parse("兩個小時後提醒我整理鐘錶，备注：後續討論",now,TimeParser.DEFAULTS).task;eq("12:20:15",t.time);eq("整理鐘錶",t.title);eq("後續討論",t.note);
        t=parse("週日晚上之前交作業");eq("2026-10-25",t.date);eq("13:30",t.reminderPeriodTime);eq("交作業",t.title);
        t=parse("十一月七號前交作業");eq("2026-11-06",t.date);eq(true,t.deadline);eq("11月7日前交作業",t.title);
        t=TimeParser.parse("二十分鐘後交作業，提前十分鐘提醒",now,TimeParser.DEFAULTS).task;eq(10,t.lead);eq("交作業",t.title);
        t=TimeParser.parse("二十分鐘後交作業，不提醒",now,TimeParser.DEFAULTS).task;eq(false,t.remind);eq("交作業",t.title);
        for(String phrase:new String[]{"明天的这个时候","明天这个时候","明天这时候","明天这个时间","明天此时","明天同一时刻","明天的這個時候","明天 的 这 个 时 候"}){
            t=TimeParser.parse(phrase+"提醒我交作业",now,TimeParser.DEFAULTS).task;eq("2026-10-22",t.date);eq("10:20:15",t.time);eq("交作业",t.title);eq(0,t.lead);eq(true,t.remind);eq("",t.period);
        }
        t=TimeParser.parse("明天的这个时候提醒我",now,TimeParser.DEFAULTS).task;eq("定时提醒",t.title);eq("10:20:15",t.time);eq(0,t.lead);
        t=TimeParser.parse("后天这时候提醒我带书",now,TimeParser.DEFAULTS).task;eq("2026-10-23",t.date);eq("带书",t.title);
        t=TimeParser.parse("下周三的这个时候交材料",now,TimeParser.DEFAULTS).task;eq("2026-10-28",t.date);eq("10:20:15",t.time);
        t=TimeParser.parse("明天这个点交作业，提前十分钟提醒",now,TimeParser.DEFAULTS).task;eq(10,t.lead);eq("交作业",t.title);
        t=TimeParser.parse("明天这个时候交作业，不提醒",now,TimeParser.DEFAULTS).task;eq(false,t.remind);
        t=TimeParser.parse("明天这个时候18:30交作业",now,TimeParser.DEFAULTS).task;eq("18:30",t.time);eq("交作业",t.title);
        t=TimeParser.parse("明天这个时候前交作业",now,TimeParser.DEFAULTS).task;eq(true,t.deadline);eq("10:20:15",t.time);eq("早上",t.reminderPeriod);
        t=TimeParser.parse("明天这个时候出门",LocalDateTime.of(2026,12,31,23,59,58),TimeParser.DEFAULTS).task;eq("2027-01-01",t.date);eq("23:59:58",t.time);
        t=TimeParser.parse("明天这个时候开会",LocalDateTime.of(2028,2,28,9,5,2),TimeParser.DEFAULTS).task;eq("2028-02-29",t.date);eq("09:05:02",t.time);
        t=TimeParser.parse("记住这个时候的想法，备注：明天这个时候",now,TimeParser.DEFAULTS).task;eq("",t.date);eq("记住这个时候的想法",t.title);eq("明天这个时候",t.note);
        t=parse("明天10:00开会，提前30分钟提醒");long event=t.startMillis(),first=t.triggerMillis();t.snooze(first,event);eq(first+600000,t.triggerMillis());eq("提前20分钟提醒",t.reminderLabel());eq(30,t.lead);eq(t.triggerMillis(),t.copy().reminderOverrideMillis);
        t.snooze(t.triggerMillis(),event);eq("提前10分钟提醒",t.reminderLabel());t.snooze(t.triggerMillis(),event);eq("开始时提醒",t.reminderLabel());t.snooze(t.triggerMillis(),event);eq("开始后10分钟提醒",t.reminderLabel());t.snoozeUntil=0;eq("开始后10分钟提醒",t.reminderLabel());
        t.repeat="每天";t.advance();eq(0L,t.reminderOverrideMillis);eq(0L,t.reminderOverrideStart);eq("提前30分钟提醒",t.reminderLabel());
        t=parse("明天晚上之前交材料");first=t.triggerMillis();t.snooze(first,t.startMillis());eq("10月22日 13:40提醒",t.reminderLabel());eq(first+600000,t.triggerMillis());
        t=parse("11月7日前交材料");first=t.triggerMillis();t.snooze(first,t.startMillis());eq("11月6日 18:40提醒",t.reminderLabel());t.clearSnooze();eq(first,t.triggerMillis());
        System.out.println("PASS: "+passed+" parser, range, recurrence and reminder assertions");
    }
}
