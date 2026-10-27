package cn.shixu.app;
import java.time.*;
import java.util.*;
public class MultiEventTests {
    static int checks;static final LocalDateTime NOW=LocalDateTime.of(2026,10,22,10,0);
    static void eq(Object a,Object b){if(!a.equals(b))throw new AssertionError(a+" != "+b);checks++;}
    static List<MultiEventParser.Candidate> parse(String s){return MultiEventParser.extract(s,NOW,TimeParser.DEFAULTS);}
    static OcrTable.Cell cell(String s,int x,int y,int w){return new OcrTable.Cell(s,x,y,w,24);}
    public static void main(String[] args){
        String table="10月16日18:30 | 开营典礼 | 地点：A101 | 主讲人：陈老师\n10月24日9:30 | 阅读分享 | 地点：A202\n10月24日10:30 | 办公软件基本操作 | 地点：A202\n11月1日20:00 | 结营考试补考 | 地点：A106";
        List<MultiEventParser.Candidate> r=parse(table);eq(4,r.size());eq("2026-10-16",r.get(0).task.date);eq(false,r.get(0).task.remind);eq("18:30",r.get(0).task.time);eq("开营典礼",r.get(0).task.title);eq("A101",r.get(0).task.location);eq(table,r.get(0).task.note);eq(true,r.get(0).yearless);eq("2026-10-24",r.get(1).task.date);eq("09:30",r.get(1).task.time);eq(true,r.get(1).task.remind);eq(30,r.get(1).task.lead);eq(2,r.get(1).task.priority);eq("A106",r.get(3).task.location);
        r=MultiEventParser.extract(table,NOW,TimeParser.DEFAULTS,2027);eq("2027-10-16",r.get(0).task.date);eq(true,r.get(0).task.remind);
        r=parse("明天上午9:00参加阅读交流会；后天下午14:00参加培训。");eq(2,r.size());eq("2026-10-23",r.get(0).task.date);eq("09:00",r.get(0).task.time);eq("2026-10-24",r.get(1).task.date);eq("14:00",r.get(1).task.time);
        eq(0,parse(TutorialContent.NOTICE).size());eq(0,parse("明天14：00-20：00参加培训").size());eq(0,parse("明天上午9:00参加培训，后天发资料。").size());
        r=parse("2026-10-23 9:00 | 读书交流 | 地点：A101\n2026-10-24 14:00-16:00 | 练习 | 地点：A202");eq(2,r.size());eq(false,r.get(0).yearless);eq("14:00",r.get(1).task.time);eq("16:00",r.get(1).task.endTime);
        r=parse("11/7 14:00-15:00 | 分享 | 地点：A101\n11.8 15:00 | 考试 | 地点：A102");eq(2,r.size());eq("2026-11-07",r.get(0).task.date);eq("15:00",r.get(0).task.endTime);eq("2026-11-08",r.get(1).task.date);
        List<OcrTable.Cell> headers=Arrays.asList(cell("时间",30,20,120),cell("内容",300,20,200),cell("主讲人",600,20,100),cell("地点",800,20,100));
        List<OcrTable.Cell> tokens=new ArrayList<>(Arrays.asList(cell("10月24日9:30",10,100,160),cell("阅读",300,95,60),cell("分享",300,125,60),cell("陈老师",600,100,100),cell("A101",800,100,80),cell("10月24日10:30",10,220,160),cell("电脑培训",300,220,160),cell("A202",800,220,80)));
        Collections.shuffle(tokens,new Random(4));String assembled=OcrTable.reconstruct(headers,tokens);r=parse(assembled);eq(2,r.size());eq("阅读 分享",r.get(0).task.title);eq("A101",r.get(0).task.location);eq("电脑培训",r.get(1).task.title);eq("A202",r.get(1).task.location);eq(true,assembled.contains("陈老师"));
        List<OcrTable.Cell> traditionalHeaders=Arrays.asList(cell("時間",30,20,120),cell("內容",300,20,200),cell("主講人",600,20,100),cell("地點",800,20,100));
        List<MultiEventParser.Candidate> traditionalRows=parse(OcrTable.reconstruct(traditionalHeaders,tokens));eq(2,traditionalRows.size());eq("A101",traditionalRows.get(0).task.location);eq("电脑培训",traditionalRows.get(1).task.title);
        eq("",OcrTable.reconstruct(Collections.emptyList(),tokens));eq("",OcrTable.reconstruct(headers,tokens.subList(0,1)));
        r=parse("2月30日14:00 | 错误日期测试 | 地点：A101\n11月8日15:00 | 考试 | 地点：A102");eq(2,r.size());eq("",r.get(0).task.date);eq(false,r.get(0).task.remind);
        r=parse("明天下午的培训取消；后天下午参加阅读交流会。");eq(0,r.size());
        eq("10月24日10:30",OcrTable.repairDate("10月2410:30"));eq("[日期待核对]10月111:30",OcrTable.repairDate("10月111:30"));
        List<OcrTable.Cell> damagedHeaders=Arrays.asList(cell("|叶间",30,20,120),cell("内容",300,20,200),cell("主讲人",600,20,100),cell("|地点",800,20,100));
        r=parse(OcrTable.reconstruct(damagedHeaders,tokens));eq(2,r.size());eq("A101",r.get(0).task.location);
        String ambiguous="[日期待核对]10月111:30 | 阅读分享 | 地点：A101\n11月7日15:00 | 待补充标题 | 地点：A202";r=parse(ambiguous);eq(2,r.size());eq("",r.get(0).task.date);eq(false,r.get(0).task.remind);eq("待补充标题",r.get(1).task.title);
        r=parse(table+"\n\n识别原文（供核对）：\n"+table);eq(4,r.size());eq(true,r.get(0).task.note.contains("识别原文"));
        System.out.println("MultiEventTests PASS: "+checks+" checks");
    }
}
