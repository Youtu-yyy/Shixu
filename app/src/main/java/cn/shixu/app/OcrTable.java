package cn.shixu.app;

import java.util.*;
import java.util.regex.*;

/** Reconstruct schedule rows before flattening OCR into text. No Android dependencies. */
public final class OcrTable {
    public static final class Cell {
        public final String text; public final float x,y,w,h;
        public Cell(String text,float x,float y,float w,float h){this.text=text;this.x=x;this.y=y;this.w=w;this.h=h;}
        float cx(){return x+w/2;} float cy(){return y+h/2;}
    }
    static final Pattern DATE=Pattern.compile("(?:\\d{4}年)?\\d{1,2}月\\d{1,2}[日号]|(?:\\d{4}[-/])?\\d{1,2}[/.-]\\d{1,2}");
    static String compact(String s){return s.replaceAll("[\\s\\p{Zs}]+","").replaceAll("^[|｜]+|[|｜]+$","").replace('：',':');}
    static String repairDate(String s){
        Matcher m=Pattern.compile("^(\\d{1,2})月(\\d{2,4}):(\\d{2})$").matcher(s);if(!m.matches())return s;
        String digits=m.group(2),fixed="";int count=0;for(int split=1;split<=2&&split<digits.length();split++){String day=digits.substring(0,split),hour=digits.substring(split);if(hour.length()>2)continue;try{java.time.LocalDate.of(2000,Integer.parseInt(m.group(1)),Integer.parseInt(day));java.time.LocalTime.of(Integer.parseInt(hour),Integer.parseInt(m.group(3)));fixed=m.group(1)+"月"+day+"日"+hour+":"+m.group(3);count++;}catch(java.time.DateTimeException invalid){}}
        return count==1?fixed:"[日期待核对]"+s;
    }
    static String join(List<Cell> cells){
        cells.sort(Comparator.comparingDouble((Cell c)->c.cy()).thenComparingDouble(c->c.x));
        // OCR elements on the same baseline may differ slightly in vertical position.
        List<List<Cell>> lines=new ArrayList<>();
        for(Cell c:cells){List<Cell> line=lines.isEmpty()?null:lines.get(lines.size()-1);if(line==null||Math.abs(c.cy()-line.get(0).cy())>Math.max(c.h,line.get(0).h)*.65f){line=new ArrayList<>();lines.add(line);}line.add(c);}
        StringBuilder text=new StringBuilder();for(List<Cell> line:lines){line.sort(Comparator.comparingDouble(c->c.x));if(text.length()>0)text.append(' ');for(Cell c:line){if(text.length()>0&&c.text.length()>0){char a=text.charAt(text.length()-1),b=c.text.charAt(0);if(a<128&&b<128&&Character.isLetterOrDigit(a)&&Character.isLetterOrDigit(b))text.append(' ');}text.append(c.text);}}
        return text.toString().replaceAll("[|｜]","").trim();
    }
    /** Empty means this is not a reliably recognized table; caller keeps ordinary OCR text. */
    public static String reconstruct(List<Cell> lines,List<Cell> elements){
        Cell date=null,title=null,place=null,speaker=null;
        for(Cell c:lines){String s=compact(c.text).replace('內','内').replace('時','时').replace('間','间').replace('點','点').replace('講','讲').replace('負','负').replace('責','责').replace('動','动').replace('項','项');if(s.matches("时间|日期|日期时间"))date=c;else if(s.matches("内容|活动内容|活动安排|事项"))title=c;else if(s.matches("地点|活动地点"))place=c;else if(s.matches("主讲人|主讲|负责人"))speaker=c;}
        // Glare often damages the first header. Other aligned headers still identify its column.
        if(date==null&&title!=null&&place!=null)for(Cell c:lines)if(c.cx()<title.cx()&&Math.abs(c.cy()-title.cy())<Math.max(c.h,title.h)*2&&!DATE.matcher(compact(c.text)).find()){date=c;break;}
        if(date==null||title==null||place==null||date.cx()>=title.cx()||title.cx()>=place.cx())return "";
        if(Math.abs(date.cy()-title.cy())>Math.max(date.h,title.h)*2||Math.abs(date.cy()-place.cy())>Math.max(date.h,place.h)*2)return "";
        float left=(date.cx()+title.cx())/2, right=(title.cx()+(speaker==null?place.cx():speaker.cx()))/2;
        float locationLeft=((speaker==null?title.cx():speaker.cx())+place.cx())/2;
        float headerBottom=Math.max(date.y+date.h,Math.max(title.y+title.h,place.y+place.h));
        List<Cell> leftTokens=new ArrayList<>();for(Cell c:elements)if(c.cy()>headerBottom&&c.cx()<left)leftTokens.add(c);
        leftTokens.sort(Comparator.comparingDouble(c->c.cy()));List<List<Cell>> groups=new ArrayList<>();
        for(Cell c:leftTokens){List<Cell> g=groups.isEmpty()?null:groups.get(groups.size()-1);if(g==null||Math.abs(c.cy()-g.get(0).cy())>Math.max(c.h,g.get(0).h)*.8f){g=new ArrayList<>();groups.add(g);}g.add(c);}
        List<Cell> anchors=new ArrayList<>();for(List<Cell> g:groups){String s=repairDate(compact(join(g)));if(DATE.matcher(s).find()||s.startsWith("[日期待核对]")){Cell c=g.get(0);anchors.add(new Cell(s,c.x,c.y,c.w,c.h));}}
        if(anchors.size()<2)return "";
        StringBuilder output=new StringBuilder();for(int i=0;i<anchors.size();i++){
            Cell a=anchors.get(i);float top=i==0?headerBottom:(anchors.get(i-1).cy()+a.cy())/2,bottom=i+1==anchors.size()?Float.MAX_VALUE:(a.cy()+anchors.get(i+1).cy())/2;
            List<Cell> names=new ArrayList<>(),locations=new ArrayList<>(),people=new ArrayList<>();for(Cell c:elements)if(c.cy()>=top&&c.cy()<bottom){if(c.cx()>=left&&c.cx()<right)names.add(c);else if(c.cx()>=locationLeft)locations.add(c);else if(c.cx()>=right)people.add(c);}
            String name=join(names),location=join(locations);if(name.isEmpty())name="待补充标题";
            if(output.length()>0)output.append('\n');output.append(a.text).append(" | ").append(name).append(" | 地点：").append(location);if(!people.isEmpty())output.append(" | 主讲人：").append(join(people));
        }
        return output.toString();
    }
    private OcrTable(){}
}
