package cn.shixu.app;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.util.*;

/** Human-authored acceptance examples from the product's iteration history. */
public final class HistoryRegressionTests {
    private static final LocalDateTime NOW=LocalDateTime.of(2026,10,22,10,0);
    private static final ZoneId ZONE=ZoneId.of("Asia/Shanghai");
    private static String unescape(String s){return s.replace("\\n","\n").replace("\\t","\t");}
    private static String json(String s){return "\""+s.replace("\\","\\\\").replace("\"","\\\"").replace("\n","\\n").replace("\r","\\r").replace("\t","\\t")+"\"";}
    private static Object field(Task t,String key,String input)throws Exception {
        if(key.equals("trigger"))return t.triggerMillis()==0?"":Instant.ofEpochMilli(t.triggerMillis()).atZone(ZONE).toLocalDateTime().toString();
        if(key.equals("noteSource"))return t.note.equals(input);
        return Task.class.getField(key).get(t);
    }
    private static int verify(String engine,String input,String expectations)throws Exception {
        Task task=null;List<MultiEventParser.Candidate> candidates=null;Exception thrown=null;
        try{
            switch(engine){
                case "parse":case "error":task=TimeParser.parse(input,NOW,TimeParser.DEFAULTS).task;break;
                case "notice":task=NotificationExtractor.extract(input,NOW,TimeParser.DEFAULTS).task;break;
                case "multi":candidates=MultiEventParser.extract(input,NOW,TimeParser.DEFAULTS);break;
                default:throw new AssertionError("Unknown engine: "+engine);
            }
        }catch(IllegalArgumentException|DateTimeException e){thrown=e;}
        if(engine.equals("error")){
            if(thrown==null)throw new AssertionError("Expected invalid date/time to be rejected");
            if(!expectations.equals("reject=true"))throw new AssertionError("Invalid error expectation");
            return 1;
        }
        if(thrown!=null)throw thrown;
        int checks=0;
        for(String rule:expectations.split(";",-1)){
            int split=rule.indexOf('=');if(split<1)throw new AssertionError("Invalid rule: "+rule);
            String key=rule.substring(0,split),expected=unescape(rule.substring(split+1));Object actual;
            if(key.equals("count")){if(candidates==null)throw new AssertionError("count needs multi engine");actual=candidates.size();}
            else {
                Task target=task;
                if(candidates!=null){int dot=key.indexOf('.');if(dot<1)throw new AssertionError("Candidate field needs index: "+key);target=candidates.get(Integer.parseInt(key.substring(0,dot))).task;key=key.substring(dot+1);}
                actual=field(target,key,input);
            }
            if(!expected.equals(String.valueOf(actual)))throw new AssertionError(rule+"; actual="+actual);
            checks++;
        }
        return checks;
    }
    public static void main(String[] args)throws Exception {
        TimeZone.setDefault(TimeZone.getTimeZone(ZONE));
        Path data=Path.of("tests/regression/history-cases.tsv"),out=Path.of("build/regression");Files.createDirectories(out);
        List<String> lines=Files.readAllLines(data,StandardCharsets.UTF_8),results=new ArrayList<>();Set<String> ids=new HashSet<>();
        StringBuilder md=new StringBuilder("# 对话重点自动回归结果\n\n固定基准：2026-10-22 10:00，Asia/Shanghai。仅代表下列规则案例，不代表真机、OCR 图片或 UI 验收。\n\n| 编号 | 重点 | 结果 | 详情 |\n|---|---|---|---|\n");
        int failed=0,checks=0,cases=0;
        for(int n=1;n<lines.size();n++){
            String line=lines.get(n);if(line.isBlank()||line.startsWith("#"))continue;
            String[] cells=line.split("\t",-1);if(cells.length!=5)throw new AssertionError("Expected 5 TSV columns at line "+(n+1));
            String id=cells[0],group=cells[1],detail="";boolean pass=true;if(!ids.add(id))throw new AssertionError("Duplicate id "+id);
            try{checks+=verify(cells[2],unescape(cells[3]),cells[4]);}catch(Exception|AssertionError e){pass=false;failed++;detail=e.toString();}
            cases++;md.append('|').append(id).append('|').append(group).append('|').append(pass?"PASS":"FAIL").append('|').append(detail.replace("|","\\|").replace("\n"," ")).append("|\n");
            results.add("{\"id\":"+json(id)+",\"group\":"+json(group)+",\"status\":"+json(pass?"PASS":"FAIL")+",\"detail\":"+json(detail)+"}");
        }
        if(cases==0)throw new AssertionError("Empty test corpus");
        String summary=cases+" cases, "+checks+" passed assertions, "+failed+" failed cases";
        Files.writeString(out.resolve("history-results.md"),md+"\n"+summary+"\n",StandardCharsets.UTF_8);
        Files.writeString(out.resolve("history-results.json"),"{\"now\":\""+NOW+"\",\"zone\":\""+ZONE+"\",\"cases\":"+cases+",\"failed\":"+failed+",\"results\":["+String.join(",",results)+"]}\n",StandardCharsets.UTF_8);
        if(failed>0)throw new AssertionError(summary+"; see build/regression/history-results.md");
        System.out.println("HistoryRegressionTests PASS: "+summary);
    }
}
