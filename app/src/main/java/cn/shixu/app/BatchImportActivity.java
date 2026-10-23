package cn.shixu.app;

import android.Manifest;
import android.app.*;
import android.os.*;
import android.content.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.widget.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** Review-only drafts until the explicit, transactional import. */
public class BatchImportActivity extends Activity {
    static final int BLUE=0xff4561ee,INK=0xff20283f,MUTED=0xff828aa0,BG=0xfff6f7fb;
    ArrayList<Task> tasks=new ArrayList<>();List<MultiEventParser.Candidate> candidates;
    boolean[] selected,manualDate;String source;int year;LinearLayout cards;TextView save,count;boolean saving;
    final LinkedHashMap<String,String> ranges=new LinkedHashMap<>();
    int dp(float v){return Math.round(v*getResources().getDisplayMetrics().density);}
    GradientDrawable bg(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
    LinearLayout column(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    TextView text(String s,int size,int color,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return t;}
    TextView button(String s){TextView t=text(s,14,BLUE,true);t.setGravity(Gravity.CENTER);t.setPadding(dp(14),dp(12),dp(14),dp(12));t.setBackground(bg(0xffedf0ff,14));return t;}
    void gap(LinearLayout l,int h){l.addView(new View(this),new LinearLayout.LayoutParams(1,dp(h)));}
    @Override public void onCreate(Bundle state){
        super.onCreate(state);getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE|WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        source=getIntent().getStringExtra("source");if(source==null||source.length()>20000){finish();return;}year=state==null?LocalDate.now().getYear():state.getInt("year",LocalDate.now().getYear());
        android.content.SharedPreferences prefs=getSharedPreferences("settings",MODE_PRIVATE);for(Map.Entry<String,String> e:TimeParser.DEFAULTS.entrySet())ranges.put(e.getKey(),prefs.getString("range_"+e.getKey(),e.getValue()));
        candidates=MultiEventParser.extract(source,LocalDateTime.now(),ranges,year);if(candidates.size()<2){finish();return;}
        for(MultiEventParser.Candidate c:candidates)tasks.add(c.task);selected=new boolean[tasks.size()];Arrays.fill(selected,true);manualDate=new boolean[tasks.size()];
        if(state!=null){Object saved=state.getSerializable("drafts");if(saved instanceof ArrayList&&((ArrayList<?>)saved).size()==tasks.size())tasks=(ArrayList<Task>)saved;boolean[] checks=state.getBooleanArray("selected"),dates=state.getBooleanArray("manualDate");if(checks!=null&&checks.length==selected.length)selected=checks;if(dates!=null&&dates.length==manualDate.length)manualDate=dates;}
        LinearLayout root=column();root.setBackgroundColor(BG);root.setFocusableInTouchMode(true);root.requestFocus();setContentView(root);root.setOnApplyWindowInsetsListener((v,insets)->{if(Build.VERSION.SDK_INT>=30){android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.ime());v.setPadding(dp(18),bars.top+dp(8),dp(18),bars.bottom+dp(10));}else v.setPadding(dp(18),insets.getSystemWindowInsetTop()+dp(8),dp(18),insets.getSystemWindowInsetBottom()+dp(10));return insets;});
        LinearLayout heading=new LinearLayout(this);heading.setGravity(Gravity.CENTER_VERTICAL);TextView title=text("选择要生成的事项",22,INK,true);heading.addView(title,new LinearLayout.LayoutParams(0,-2,1));TextView close=button("取消");close.setOnClickListener(v->finish());heading.addView(close);root.addView(heading);gap(root,8);
        root.addView(text("发现 "+tasks.size()+" 项安排。可多选，也可以只保留一项；点击事项可修改，原文会保留在备注。",13,MUTED,false));gap(root,10);
        if(candidates.stream().anyMatch(c->c.yearless)){
            LinearLayout yearRow=new LinearLayout(this);yearRow.setGravity(Gravity.CENTER_VERTICAL);yearRow.addView(text("未写年份，暂按",13,MUTED,false));Spinner years=new Spinner(this);ArrayList<String> options=new ArrayList<>();int base=LocalDate.now().getYear();for(int n=base-3;n<=base+5;n++)options.add(n+"年");if(!options.contains(year+"年"))options.add(year+"年");years.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,options));years.setSelection(options.indexOf(year+"年"));years.setContentDescription("安排年份");yearRow.addView(years);root.addView(yearRow);years.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onNothingSelected(AdapterView<?> p){}public void onItemSelected(AdapterView<?> p,View v,int n,long id){int value=Integer.parseInt(options.get(n).replace("年",""));if(value!=year){year=value;for(int i=0;i<tasks.size();i++)if(candidates.get(i).yearless&&!manualDate[i]){Task t=tasks.get(i);try{if(!t.date.isEmpty()){LocalDate boundary=LocalDate.parse(t.date).plusDays(t.beforeDay&&t.deadline?1:0);t.date=LocalDate.of(year,boundary.getMonthValue(),boundary.getDayOfMonth()).minusDays(t.beforeDay&&t.deadline?1:0).toString();}}catch(DateTimeException invalid){t.date="";}t.refreshBeforeTitle();safeReminder(t);}render();}}});
        }
        LinearLayout tools=new LinearLayout(this);count=text("",13,BLUE,true);tools.addView(count,new LinearLayout.LayoutParams(0,-2,1));TextView all=button("全选");all.setOnClickListener(v->{Arrays.fill(selected,true);render();});tools.addView(all);TextView none=button("清空选择");none.setOnClickListener(v->{Arrays.fill(selected,false);render();});tools.addView(none);root.addView(tools);gap(root,8);
        ScrollView scroll=new ScrollView(this);cards=column();scroll.addView(cards);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));gap(root,8);
        save=button("生成所选事项");save.setTextColor(Color.WHITE);save.setBackground(bg(BLUE,16));save.setOnClickListener(v->save());root.addView(save,new LinearLayout.LayoutParams(-1,dp(50)));render();
    }
    void safeReminder(Task t){if(t.date.isEmpty()||(t.time.isEmpty()&&!t.deadline)||t.triggerMillis()<=System.currentTimeMillis())t.remind=false;}
    String details(Task t){String s=t.date.isEmpty()?"日期待补充":t.date;s+=" · "+(t.beforeDay&&t.deadline?"当天结束前":t.time.isEmpty()?(t.deadline?"该日期前":"全天"):t.time+(t.endTime.isEmpty()?"":"—"+(t.endDayOffset>0?"次日":"")+t.endTime)+(t.deadline?"前":""));if(!t.location.isEmpty())s+="\n地点："+t.location;s+="\n"+t.reminderLabel();if(!t.date.isEmpty()&&t.reminderBaseMillis()<System.currentTimeMillis())s+=" · 时间已过";if(t.title.equals("待补充标题"))s+="\n标题未识别，请对照图片补充";int index=tasks.indexOf(t);if(index>0&&!t.date.isEmpty()&&!tasks.get(index-1).date.isEmpty()&&t.date.compareTo(tasks.get(index-1).date)<0)s+="\n日期早于上一项，请核对";return s;}
    void render(){if(cards==null)return;cards.removeAllViews();for(int i=0;i<tasks.size();i++){
        final int index=i;Task t=tasks.get(i);LinearLayout card=new LinearLayout(this);card.setGravity(Gravity.TOP);card.setPadding(dp(8),dp(12),dp(12),dp(12));card.setBackground(bg(Color.WHITE,18));
        CheckBox checked=new CheckBox(this);checked.setChecked(selected[i]);checked.setContentDescription("选择第"+(i+1)+"项");checked.setOnCheckedChangeListener((v,on)->{selected[index]=on;updateCount();});card.addView(checked);
        LinearLayout content=column();content.setPadding(dp(3),dp(3),0,0);TextView name=text(t.title,16,INK,true);content.addView(name);gap(content,6);content.addView(text(details(t),13,MUTED,false));TextView edit=text("修改事项 ›",12,BLUE,true);edit.setPadding(0,dp(9),0,0);content.addView(edit);content.setContentDescription("修改第"+(i+1)+"项");content.setOnClickListener(v->edit(index));card.addView(content,new LinearLayout.LayoutParams(0,-2,1));cards.addView(card);gap(cards,10);
        }updateCount();}
    void updateCount(){int n=0;for(boolean check:selected)if(check)n++;count.setText("已选 "+n+" / "+tasks.size()+" 项");if(save!=null){save.setText("生成所选 "+n+" 项");save.setEnabled(n>0&&!saving);save.setAlpha(n>0?1f:.45f);}}
    void edit(int index){
        Task original=tasks.get(index);new TaskEditor(this).edit(original,true,t->{manualDate[index]|=!t.date.equals(original.date);tasks.set(index,t);render();});
    }
    void save(){
        if(saving)return;ArrayList<Task> chosen=new ArrayList<>();for(int i=0;i<tasks.size();i++)if(selected[i]){Task t=tasks.get(i).copy();if(t.date.isEmpty()||t.title.equals("待补充标题")){Toast.makeText(this,"请先补充第"+(i+1)+"项的日期或标题，或取消勾选",Toast.LENGTH_LONG).show();return;}safeReminder(t);chosen.add(t);}if(chosen.isEmpty())return;saving=true;updateCount();
        try(Store store=new Store(this)){store.saveBatch(chosen);}catch(RuntimeException error){saving=false;updateCount();Toast.makeText(this,"保存失败，草稿仍保留，请重试",Toast.LENGTH_LONG).show();return;}
        for(Task t:chosen)Reminders.schedule(this,t);setResult(RESULT_OK,new Intent().putExtra("reminders",chosen.stream().anyMatch(t->t.remind)));Toast.makeText(this,"已生成 "+chosen.size()+" 项事项",Toast.LENGTH_LONG).show();finish();
    }
    @Override protected void onSaveInstanceState(Bundle state){state.putSerializable("drafts",tasks);state.putBooleanArray("selected",selected);state.putBooleanArray("manualDate",manualDate);state.putInt("year",year);super.onSaveInstanceState(state);}
}
