package cn.shixu.app;
import java.time.*;

public final class NoticeTests {
    static int checks;
    static final LocalDateTime NOW=LocalDateTime.of(2026,10,21,10,0);
    static void eq(Object expected,Object actual){if(!expected.equals(actual))throw new AssertionError(expected+" != "+actual);checks++;}
    static void ok(boolean actual){eq(true,actual);}
    static NotificationExtractor.Result parse(String raw){NotificationExtractor.Result r=NotificationExtractor.extract(raw,NOW,TimeParser.DEFAULTS);eq(raw,r.task.note);return r;}
    public static void main(String[] args){
        NotificationExtractor.Result tutorial=parse(TutorialContent.NOTICE);eq("参加读书交流会",tutorial.task.title);eq("2026-10-22",tutorial.task.date);eq("09:00",tutorial.task.time);eq("10:30",tutorial.task.endTime);eq("教学楼301教室",tutorial.task.location);eq(30,tutorial.task.lead);eq(true,tutorial.task.remind);
        String raw="@所有人\u200510/22晚上18:30在b2201，示例老师请两个综合课程课题的同学进行讨论，我争取明天给大家梳理一下接下来两周要做的事情，辛苦大家尽快推进，到时候准备好 PPT 汇报相关进展";
        NotificationExtractor.Result r=parse(raw);eq("2026-10-22",r.task.date);eq("18:30",r.task.time);eq("b2201",r.location);ok(r.task.title.contains("讨论"));ok(!r.task.title.contains("示例老师"));ok(!r.task.title.contains("争取"));ok(r.task.note.contains("准备好 PPT"));eq(30,r.task.lead);ok(r.task.remind);
        r=parse("请大家11 月 7 日前提交采访初稿，需附采访录音，谢谢配合。");eq("2026-11-06",r.task.date);ok(r.task.deadline);eq("11月7日前提交采访初稿",r.task.title);eq("18:30",r.task.reminderEveningTime);
        r=parse("通知：10月23日下午14:00在A301，举行课题讨论，请携带笔记本。");eq("2026-10-23",r.task.date);eq("14:00",r.task.time);eq("A301",r.location);ok(r.task.title.contains("讨论"));ok(r.task.note.contains("请携带笔记本"));
        r=parse("明天下午两点参加面试，带好身份证。");eq("2026-10-22",r.task.date);eq("14:00",r.task.time);ok(r.warning.contains("发送日期"));eq("参加面试",r.task.title);
        r=parse("请明天上午提交申请材料，后天下午参加面试。");eq("2026-10-22",r.task.date);ok(r.task.title.contains("提交申请材料"));ok(r.warning.contains("一个主要事项"));ok(r.task.note.contains("后天下午参加面试"));
        r=parse("明天的培训取消，请等候后续通知。");eq("",r.task.date);eq(false,r.task.remind);ok(r.warning.contains("未自动安排时间"));
        r=parse("暂定明天下午进行课题讨论，具体时间另行通知。");eq("",r.task.date);eq(false,r.task.remind);
        r=parse("大家好，辛苦啦，谢谢大家！");eq("待确认事项",r.task.title);eq("",r.task.date);eq(false,r.task.remind);
        r=parse("请提交材料，准备好两份复印件。");eq("",r.task.date);eq("提交材料",r.task.title);ok(r.warning.contains("未识别到日期"));
        r=parse("通知：2026-10-20下午14:00参加会议。");eq("2026-10-20",r.task.date);eq(false,r.task.remind);ok(r.warning.contains("时间已过"));
        r=parse("请于11月31日下午参加培训。");eq("",r.task.date);ok(r.warning.contains("不合法"));
        ok(NotificationExtractor.looksLikeNotice(raw));eq(false,NotificationExtractor.looksLikeNotice("明天交作业"));
        try{NotificationExtractor.extract(" ",NOW,TimeParser.DEFAULTS);throw new AssertionError("Empty accepted");}catch(IllegalArgumentException expected){checks++;}
        try{NotificationExtractor.extract("字".repeat(20001),NOW,TimeParser.DEFAULTS);throw new AssertionError("Too long accepted");}catch(IllegalArgumentException expected){checks++;}
        Task t=TimeParser.parse("明天下午两点在H3301开会",NOW,TimeParser.DEFAULTS).task;eq("H3301",t.location);eq("开会",t.title);eq("14:00",t.time);eq("H3301",t.copy().location);
        t=TimeParser.parse("明天上午在图书馆看书",NOW,TimeParser.DEFAULTS).task;eq("图书馆",t.location);eq("看书",t.title);
        t=TimeParser.parse("今天下午在第三教学楼A301参加讨论",NOW,TimeParser.DEFAULTS).task;eq("第三教学楼A301",t.location);eq("参加讨论",t.title);
        r=parse("请于10月23日14:00参加课题讨论，地点：H3301，带好笔记。");eq("H3301",r.task.location);eq("14:00",r.task.time);
        t=TimeParser.parse("明天讨论H3301方案",NOW,TimeParser.DEFAULTS).task;eq("",t.location);eq("讨论H3301方案",t.title);
        String lecture="明天下午13:30-15:00在东辅楼202报告厅有黄永刚院士的讲座。\n讲座主题：Multi-Modal Haptic Actuators\n\n请同学们积极参与~[太阳]";
        r=parse(lecture);eq("2026-10-22",r.task.date);eq("13:30",r.task.time);eq("15:00",r.task.endTime);eq("东辅楼202报告厅",r.task.location);eq("参加黄永刚院士的讲座",r.task.title);ok(r.task.recognitionWarning.contains("相对日期"));eq(lecture,r.task.copy().note);eq(r.warning,r.task.copy().recognitionWarning);
        System.out.println("PASS: "+checks+" notice extraction, source preservation, uncertainty and primary-event checks");
    }
}
