package cn.shixu.app;

import android.Manifest;
import android.app.*;
import android.os.*;
import android.content.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.content.res.ColorStateList;
import android.provider.Settings;
import android.text.*;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class MainActivity extends Activity {
    static final int BG=0xfff6f7fb,INK=0xff20283f,MUTED=0xff828aa0,BLUE=0xff4561ee,LINE=0xffe8ebf3,WHITE=Color.WHITE;
    Store store; android.content.SharedPreferences prefs;VoiceInput voice;
    LinearLayout root,list,summary,filterBar,footer;TextView countLabel,subtitle,preview,orderLabel;EditText quick,search;
    int tab=0,sort=0;String category="全部",query=""; boolean reordering=false;Task dragging;
    List<Task> visible=new ArrayList<>();
    LocalDate weekAnchor=LocalDate.now();LinearLayout weekBody;ScrollView weekScroll;
    int page=0; YearMonth calendarMonth=YearMonth.now(); LocalDate selectedDay; LinearLayout calendarContent;
    final Handler handler=new Handler(Looper.getMainLooper());
    final Runnable ticker=new Runnable(){public void run(){if(list!=null)renderTasks();handler.postDelayed(this,60_000);}};

    @Override public void onCreate(Bundle state){super.onCreate(state);TodoWidgetProvider.preparePreview(this);store=new Store(this);prefs=getSharedPreferences("settings",MODE_PRIVATE);if(!prefs.getBoolean("start_time_sort_default",false))prefs.edit().putInt("sort",1).putBoolean("start_time_sort_default",true).apply();sort=prefs.getInt("sort",1);build();Reminders.restore(this);openFromNotification(getIntent());handler.postDelayed(ticker,60_000);}
    @Override protected void onNewIntent(Intent i){super.onNewIntent(i);setIntent(i);openFromNotification(i);}
    void openFromNotification(Intent i){if(OcrActivity.CREATE.equals(i.getAction())){String value=i.getStringExtra(OcrActivity.TEXT);if(value!=null&&!value.trim().isEmpty()&&value.length()<=20000){quick.setText(value);handler.post(()->newTask());}return;}if(TutorialActivity.TRY_SAMPLE.equals(i.getAction())){quick.setText(TutorialContent.NOTICE);handler.post(()->newTask());return;}if(TodoWidgetProvider.NEW_TASK.equals(i.getAction())){quick.setText("");handler.post(()->newTask());return;}long id=i.getLongExtra("taskId",0);if(id!=0){ReminderAlertService.stopTask(this,id,true);Task t=store.find(id);if(t!=null)edit(t);}}
    @Override protected void onResume(){super.onResume();if(store!=null){Reminders.restore(this);if(list!=null)renderTasks();}}
    @Override protected void onDestroy(){handler.removeCallbacks(ticker);if(voice!=null)voice.destroy();if(store!=null)store.close();super.onDestroy();}
    @Override protected void onPause(){if(voice!=null)voice.destroy();super.onPause();}
    void startVoice(){if(voice==null)voice=new VoiceInput(this);voice.start();}
    void acceptVoice(String spoken){String existing=quick.getText().toString().trim();quick.setText((existing.isEmpty()?"":existing+" ")+spoken);quick.setSelection(quick.length());toast("已转成文字，可修改后添加");}
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request==71&&result==RESULT_OK){quick.setText("");renderCategories();renderTasks();if(data!=null&&data.getBooleanExtra("reminders",false)&&Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},41);}if(request==VoiceInput.REQUEST_SYSTEM&&result==RESULT_OK&&data!=null){ArrayList<String> words=data.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS);if(words!=null&&!words.isEmpty())acceptVoice(words.get(0));}}
    int dp(float n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
    GradientDrawable bg(int color,int radius){GradientDrawable b=new GradientDrawable();b.setColor(color);b.setCornerRadius(dp(radius));return b;}
    GradientDrawable border(int color,int radius){GradientDrawable b=bg(color,radius);b.setStroke(dp(1),LINE);return b;}
    TextView text(String s,int size,int color,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);t.setGravity(Gravity.CENTER_VERTICAL);return t;}
    TextView button(String s,int color,int fill){TextView t=text(s,14,color,true);t.setGravity(Gravity.CENTER);t.setPadding(dp(14),dp(11),dp(14),dp(11));t.setBackground(bg(fill,12));t.setMinHeight(dp(44));return t;}
    TextView dialogHeading(String title){TextView heading=text(title,20,INK,true);heading.setPadding(dp(24),dp(24),dp(24),dp(12));return heading;}
    void polishDialog(AlertDialog dialog){
        Window window=dialog.getWindow();if(window==null)return;
        window.setBackgroundDrawable(border(WHITE,28));window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);window.setDimAmount(.38f);
        View decor=window.getDecorView();decor.setClipToOutline(true);decor.setElevation(dp(24));
        int width=Math.min(getResources().getDisplayMetrics().widthPixels-dp(40),dp(520));window.setLayout(width,-2);
        for(int which:new int[]{AlertDialog.BUTTON_NEGATIVE,AlertDialog.BUTTON_POSITIVE}){
            Button action=dialog.getButton(which);if(action==null)continue;boolean primary=which==AlertDialog.BUTTON_POSITIVE;
            action.setAllCaps(false);action.setTextSize(14);action.setTypeface(Typeface.DEFAULT,primary?Typeface.BOLD:Typeface.NORMAL);action.setTextColor(primary?WHITE:INK);
            action.setBackgroundTintList(null);action.setBackground(new android.graphics.drawable.RippleDrawable(ColorStateList.valueOf(0x224561ee),bg(primary?BLUE:BG,14),null));action.setPadding(dp(18),dp(10),dp(18),dp(10));action.setMinHeight(dp(44));
            action.setMinimumHeight(dp(44));ViewGroup.LayoutParams params=action.getLayoutParams();params.height=dp(44);if(params instanceof ViewGroup.MarginLayoutParams){ViewGroup.MarginLayoutParams margins=(ViewGroup.MarginLayoutParams)params;margins.setMargins(dp(4),0,dp(4),0);}action.setLayoutParams(params);
        }
    }
    LinearLayout vertical(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    LinearLayout row(){LinearLayout l=new LinearLayout(this);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
    void gap(LinearLayout l,int height){View v=new View(this);l.addView(v,new LinearLayout.LayoutParams(1,dp(height)));}
    void addWeighted(LinearLayout l,View v){l.addView(v,new LinearLayout.LayoutParams(0,-2,1));}
    void toast(String s){Toast.makeText(this,s,Toast.LENGTH_SHORT).show();}
    void build(){
        String draft=quick==null?"":quick.getText().toString(); list=null; summary=null; filterBar=null;
        root=vertical();root.setBackgroundColor(BG);setContentView(root);
        root.setOnApplyWindowInsetsListener((v,insets)->{if(Build.VERSION.SDK_INT>=30){android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.ime());v.setPadding(0,bars.top,0,bars.bottom);}else{v.setPadding(0,insets.getSystemWindowInsetTop(),0,insets.getSystemWindowInsetBottom());}return insets;});
        if(page==2){buildWeek();buildFooter(draft);return;}
        if(page==1){buildCalendar();buildFooter(draft);return;}
        LinearLayout header=vertical();header.setPadding(dp(18),dp(4),dp(18),dp(6));root.addView(header);
        LinearLayout brand=row();ImageView logo=new ImageView(this);logo.setImageResource(R.drawable.ic_launcher);logo.setScaleType(ImageView.ScaleType.FIT_CENTER);logo.setContentDescription("拾序");logo.setBackground(bg(BLUE,7));logo.setClipToOutline(true);brand.addView(logo,new LinearLayout.LayoutParams(dp(30),dp(30)));TextView name=text("  拾序",22,INK,true);addWeighted(brand,name);TextView settings=button("设置",MUTED,WHITE);settings.setTextSize(12);settings.setPadding(dp(12),0,dp(12),0);settings.setOnClickListener(v->settings());brand.addView(settings);header.addView(brand);
        gap(header,2);subtitle=text("把琐事安放，让今天有序。",11,MUTED,false);header.addView(subtitle);gap(header,8);
        summary=row();header.addView(summary);gap(header,6);
        LinearLayout titleRow=row();TextView title=text("事项清单",18,INK,true);addWeighted(titleRow,title);orderLabel=button(sortLabel()+"  ⇅",BLUE,0xffeef1ff);orderLabel.setTextSize(12);orderLabel.setPadding(dp(10),0,dp(10),0);orderLabel.setOnClickListener(v->chooseSort());titleRow.addView(orderLabel);header.addView(titleRow);gap(header,4);
        LinearLayout tabs=row();String[] names={"待办","今天","已完成"};for(int i=0;i<names.length;i++){final int index=i;TextView b=button(names[i],i==tab?WHITE:MUTED,i==tab?BLUE:BG);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(42),1);p.setMargins(0,0,dp(6),0);tabs.addView(b,p);b.setOnClickListener(v->{tab=index;build();});}header.addView(tabs);gap(header,10);
        search=new EditText(this);search.setSingleLine(true);search.setTextSize(14);search.setTextColor(INK);search.setHintTextColor(MUTED);search.setHint("搜索标题、地点、备注或分类");search.setBackground(border(WHITE,12));search.setPadding(dp(14),0,dp(14),0);search.setText(query);header.addView(search,new LinearLayout.LayoutParams(-1,dp(43)));search.addTextChangedListener(watcher(s->{query=s;renderTasks();}));gap(header,8);
        HorizontalScrollView categoryScroll=new HorizontalScrollView(this);categoryScroll.setHorizontalScrollBarEnabled(false);filterBar=row();categoryScroll.addView(filterBar);header.addView(categoryScroll);renderCategories();
        countLabel=text("",12,MUTED,false);countLabel.setPadding(dp(2),dp(9),0,0);header.addView(countLabel);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setClipToPadding(false);scroll.setPadding(dp(18),0,dp(18),dp(16));list=vertical();scroll.addView(list);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        buildFooter(draft);
    }
    void buildFooter(String draft){
        footer=vertical();footer.setPadding(dp(18),dp(10),dp(18),dp(12));footer.setBackgroundColor(WHITE);root.addView(footer);
        preview=text("试试：明天中午交材料，提前10分钟提醒",11,MUTED,false);footer.addView(preview);gap(footer,6);
        LinearLayout input=row();input.setPadding(dp(12),0,dp(6),0);input.setBackground(border(BG,16));quick=new EditText(this);quick.setSingleLine(true);quick.setTextSize(14);quick.setTextColor(INK);quick.setHintTextColor(MUTED);quick.setBackgroundColor(Color.TRANSPARENT);quick.setHint("记下一件事，时间也可以一起写…");quick.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_DONE);input.addView(quick,new LinearLayout.LayoutParams(0,dp(52),1));TextView plus=button("＋",WHITE,BLUE);input.addView(plus,new LinearLayout.LayoutParams(dp(43),dp(40)));footer.addView(input);
        quick.addTextChangedListener(watcher(s->{if(s.trim().isEmpty()){preview.setText("试试：明天中午交材料，提前10分钟提醒");return;}try{int candidates=MultiEventParser.extract(s,LocalDateTime.now(),ranges()).size();if(candidates>1){preview.setText("识别到 "+candidates+" 项安排，点击 + 勾选生成");return;}Task t=parseInput(s).task;preview.setText(t.date.isEmpty()?"待办 · 未定日期 · 保存前可补充信息":"已识别："+timeLabel(t)+(t.remind?" · 提前"+t.lead+"分钟":""));}catch(Exception e){preview.setText("时间暂未识别，添加后可手动选择");}}));
        TextView mic=button("语音",BLUE,0xffeef1ff);mic.setContentDescription("语音输入");mic.setTextSize(12);mic.setPadding(0,0,0,0);LinearLayout.LayoutParams mp=new LinearLayout.LayoutParams(dp(48),dp(40));mp.setMargins(0,0,dp(6),0);input.addView(mic,1,mp);mic.setOnClickListener(v->startVoice());TextView photo=button("图片",BLUE,0xffeef1ff);photo.setContentDescription("从图片识别事项");photo.setTextSize(12);photo.setPadding(0,0,0,0);LinearLayout.LayoutParams pp=new LinearLayout.LayoutParams(dp(44),dp(40));pp.setMargins(0,0,dp(4),0);input.addView(photo,1,pp);photo.setOnClickListener(v->startActivity(new Intent(this,OcrActivity.class)));
        plus.setOnClickListener(v->newTask());quick.setOnEditorActionListener((v,action,event)->{if(action==android.view.inputmethod.EditorInfo.IME_ACTION_DONE){newTask();return true;}return false;});quick.setText(draft);buildNavigation();renderTasks();
    }
    void buildNavigation(){
        LinearLayout nav=row();nav.setPadding(dp(18),dp(5),dp(18),dp(5));nav.setBackgroundColor(WHITE);
        for(int n=0;n<3;n++){
            final int destination=n;
            TextView item=button(new String[]{"首页","日历","周视图"}[n],page==n?BLUE:MUTED,page==n?0xffeef1ff:WHITE);
            item.setContentDescription(new String[]{"切换首页","切换日历","切换周视图"}[n]);
            LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(44),1);p.setMargins(dp(4),0,dp(4),0);nav.addView(item,p);
            item.setOnClickListener(v->{if(page==destination)return;((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(quick.getWindowToken(),0);page=destination;build();});
        }
        root.addView(nav);
    }
    void buildWeek(){
        LinearLayout heading=row();heading.setPadding(dp(16),dp(4),dp(12),dp(2));addWeighted(heading,text("周视图",22,INK,true));
        Switch completed=new Switch(this);completed.setText("显示已完成");completed.setTextSize(11);completed.setTextColor(MUTED);completed.setSwitchPadding(dp(5));completed.setContentDescription("显示已完成周事项");completed.setPadding(0,0,dp(10),0);completed.setChecked(prefs.getBoolean("calendar_show_completed",true));completed.setOnCheckedChangeListener((v,on)->{prefs.edit().putBoolean("calendar_show_completed",on).apply();renderWeek();});heading.addView(completed);
        TextView today=button("回到本周",BLUE,0xffeef1ff);today.setTextSize(12);today.setPadding(dp(10),0,dp(10),0);today.setOnClickListener(v->{weekAnchor=LocalDate.now();weekScroll=null;renderWeek();});heading.addView(today);root.addView(heading);
        weekBody=vertical();list=weekBody;weekScroll=null;weekBody.setPadding(dp(6),0,dp(6),0);root.addView(weekBody,new LinearLayout.LayoutParams(-1,0,1));
    }
    void openWeekTask(Task projection){Task saved=store.find(projection.id);if(saved!=null){if(!saved.date.equals(projection.date))toast("重复预览：编辑将修改原重复事项");edit(saved);}}
    void showAllDay(WeekAgenda.Day day){String[] titles=day.allDay.stream().map(t->(t.done?"✓ ":"")+t.title).toArray(String[]::new);AlertDialog dialog=new AlertDialog.Builder(this).setCustomTitle(dialogHeading(day.date.format(DateTimeFormatter.ofPattern("M月d日"))+" · 全天事项")).setItems(titles,(d,n)->openWeekTask(day.allDay.get(n))).setNegativeButton("关闭",null).create();dialog.show();polishDialog(dialog);}
    void renderWeek(){
        if(weekBody==null)return;int savedScroll=weekScroll==null||weekScroll.getHeight()==0?-1:weekScroll.getScrollY();List<WeekAgenda.Day> days=WeekAgenda.week(store.all(),weekAnchor,ranges(),prefs.getBoolean("calendar_show_completed",true));weekBody.removeAllViews();
        int innerWidth=weekBody.getWidth()>0?weekBody.getWidth()-weekBody.getPaddingLeft()-weekBody.getPaddingRight():getResources().getDisplayMetrics().widthPixels-dp(12);int[] dayWidths=WeekColumns.widths(days,innerWidth-dp(42),dp(24));
        LinearLayout controls=row();controls.setPadding(dp(10),dp(2),dp(4),dp(6));
        LinearLayout rangeLabel=vertical();LocalDate start=days.get(0).date,end=days.get(6).date;
        rangeLabel.addView(text(start.getYear()==end.getYear()?start.getYear()+"年":start.getYear()+" — "+end.getYear()+"年",11,MUTED,false));
        String endPattern=start.getYear()!=end.getYear()?"M月d日":start.getMonth()!=end.getMonth()?"M月d日":"d日";
        TextView period=text(start.format(DateTimeFormatter.ofPattern("M月d日"))+" — "+end.format(DateTimeFormatter.ofPattern(endPattern)),19,INK,true);rangeLabel.addView(period);addWeighted(controls,rangeLabel);
        TextView previous=button("‹",INK,WHITE);previous.setTextSize(25);previous.setPadding(0,0,0,0);previous.setContentDescription("上一周");controls.addView(previous,new LinearLayout.LayoutParams(dp(40),dp(40)));
        TextView next=button("›",INK,WHITE);next.setTextSize(25);next.setPadding(0,0,0,0);next.setContentDescription("下一周");LinearLayout.LayoutParams nextParams=new LinearLayout.LayoutParams(dp(40),dp(40));nextParams.setMargins(dp(6),0,0,0);controls.addView(next,nextParams);weekBody.addView(controls);
        previous.setOnClickListener(v->{weekAnchor=weekAnchor.minusWeeks(1);weekScroll=null;renderWeek();});next.setOnClickListener(v->{weekAnchor=weekAnchor.plusWeeks(1);weekScroll=null;renderWeek();});
        LinearLayout dates=row();dates.setBackgroundColor(WHITE);dates.setPadding(0,dp(4),0,dp(4));dates.addView(text("",10,MUTED,false),new LinearLayout.LayoutParams(dp(42),dp(56)));String[] names={"一","二","三","四","五","六","日"};
        for(int i=0;i<7;i++){WeekAgenda.Day day=days.get(i);boolean today=day.date.equals(LocalDate.now());LinearLayout column=vertical();column.setGravity(Gravity.CENTER);column.setContentDescription("周日期："+day.date);column.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
            TextView weekday=text(names[i],11,today?BLUE:MUTED,false);weekday.setGravity(Gravity.CENTER);weekday.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);column.addView(weekday,new LinearLayout.LayoutParams(-1,dp(20)));
            TextView number=text(String.valueOf(day.date.getDayOfMonth()),16,today?WHITE:i>=5?BLUE:INK,true);number.setGravity(Gravity.CENTER);number.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);if(today)number.setBackground(bg(BLUE,18));column.addView(number,new LinearLayout.LayoutParams(dp(32),dp(32)));dates.addView(column,new LinearLayout.LayoutParams(dayWidths[i],dp(56)));}weekBody.addView(dates);
        int allHeight=0;for(WeekAgenda.Day day:days)allHeight=Math.max(allHeight,Math.min(3,day.allDay.size()));
        if(allHeight>0){LinearLayout all=row();all.setGravity(Gravity.TOP);all.setBackgroundColor(WHITE);all.addView(text("全天",10,MUTED,false),new LinearLayout.LayoutParams(dp(42),dp(24)));for(int dayIndex=0;dayIndex<days.size();dayIndex++){WeekAgenda.Day day=days.get(dayIndex);LinearLayout column=vertical();column.setPadding(dp(1),dp(3),dp(1),dp(3));all.addView(column,new LinearLayout.LayoutParams(dayWidths[dayIndex],-2));int visible=day.allDay.size()>3?2:day.allDay.size();for(int n=0;n<visible;n++){Task t=day.allDay.get(n);TextView chip=text(t.title,10,t.done?MUTED:BLUE,false);chip.setSingleLine(true);chip.setEllipsize(null);chip.setBackground(bg(t.done?0xffeef0f4:0xffeef1ff,4));chip.setContentDescription("周全天事项："+t.title+"，"+day.date);if(t.done)chip.setPaintFlags(chip.getPaintFlags()|android.graphics.Paint.STRIKE_THRU_TEXT_FLAG);chip.setOnClickListener(v->openWeekTask(t));column.addView(chip,new LinearLayout.LayoutParams(-1,dp(23)));}if(day.allDay.size()>3){TextView more=text("+"+(day.allDay.size()-2)+"项",10,BLUE,true);more.setGravity(Gravity.CENTER_VERTICAL);more.setContentDescription("查看全部全天事项："+day.date);more.setOnClickListener(v->showAllDay(day));column.addView(more,new LinearLayout.LayoutParams(-1,dp(24)));}}weekBody.addView(all);}
        WeekTimeline timeline=new WeekTimeline(this,days,this::openWeekTask);weekScroll=new ScrollView(this);weekScroll.setFillViewport(false);weekScroll.addView(timeline,new ScrollView.LayoutParams(-1,timeline.gridHeight()));weekBody.addView(weekScroll,new LinearLayout.LayoutParams(-1,0,1));
        int first=480;for(WeekAgenda.Day day:days)for(WeekAgenda.Block block:day.timed)first=Math.min(first,Math.max(0,block.start-60));final int target=savedScroll>=0?savedScroll:dp(Math.round(first/60f*32));ScrollView scroll=weekScroll;scroll.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener(){public void onGlobalLayout(){if(scroll.getHeight()==0)return;scroll.getViewTreeObserver().removeOnGlobalLayoutListener(this);if(scroll==weekScroll)scroll.scrollTo(0,target);}});
    }
    void buildCalendar(){
        LinearLayout titleRow=row();titleRow.setPadding(dp(16),dp(4),dp(12),dp(6));
        addWeighted(titleRow,text("日历",22,INK,true));
        Switch completedToggle=new Switch(this);completedToggle.setText("显示已完成");completedToggle.setTextSize(11);completedToggle.setTextColor(MUTED);completedToggle.setSwitchPadding(dp(5));completedToggle.setContentDescription("显示已完成日程");
        completedToggle.setPadding(0,0,dp(10),0);completedToggle.setChecked(prefs.getBoolean("calendar_show_completed",true));titleRow.addView(completedToggle);
        TextView today=button("回到今天",BLUE,0xffeef1ff);today.setTextSize(12);today.setPadding(dp(10),0,dp(10),0);titleRow.addView(today);
        today.setOnClickListener(v->{calendarMonth=YearMonth.now();selectedDay=LocalDate.now();renderCalendar();});root.addView(titleRow);
        completedToggle.setOnCheckedChangeListener((v,checked)->{prefs.edit().putBoolean("calendar_show_completed",checked).apply();renderCalendar();});
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setPadding(dp(18),0,dp(18),dp(14));
        calendarContent=vertical();list=calendarContent;scroll.addView(calendarContent);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
    }
    void renderCalendar(){
        if(calendarContent==null)return;
        boolean showCompleted=prefs.getBoolean("calendar_show_completed",true);
        List<Task> tasks=store.all();if(!showCompleted)tasks.removeIf(t->t.done);
        List<Task> entries=CalendarAgenda.month(tasks,calendarMonth);
        calendarContent.removeAllViews();
        LinearLayout monthCard=vertical();monthCard.setPadding(dp(10),dp(10),dp(10),dp(10));monthCard.setBackground(border(WHITE,16));calendarContent.addView(monthCard);
        LinearLayout monthRow=row();TextView prev=button("‹",BLUE,BG);prev.setContentDescription("上个月");monthRow.addView(prev,new LinearLayout.LayoutParams(dp(44),dp(44)));
        TextView monthTitle=text(calendarMonth.getYear()+"年"+calendarMonth.getMonthValue()+"月",20,INK,true);monthTitle.setGravity(Gravity.CENTER);addWeighted(monthRow,monthTitle);
        TextView next=button("›",BLUE,BG);next.setContentDescription("下个月");monthRow.addView(next,new LinearLayout.LayoutParams(dp(44),dp(44)));monthCard.addView(monthRow);
        prev.setOnClickListener(v->{calendarMonth=calendarMonth.minusMonths(1);selectedDay=null;renderCalendar();});next.setOnClickListener(v->{calendarMonth=calendarMonth.plusMonths(1);selectedDay=null;renderCalendar();});
        gap(monthCard,10);LinearLayout week=row();String[] names={"一","二","三","四","五","六","日"};
        for(int i=0;i<7;i++){TextView day=text(names[i],11,i>=5?BLUE:MUTED,false);day.setGravity(Gravity.CENTER);week.addView(day,new LinearLayout.LayoutParams(0,dp(25),1));}monthCard.addView(week);
        Map<String,List<Task>> byDate=new HashMap<>();for(Task t:entries)byDate.computeIfAbsent(t.date,k->new ArrayList<>()).add(t);
        int offset=calendarMonth.atDay(1).getDayOfWeek().getValue()-1;
        int weeks=(offset+calendarMonth.lengthOfMonth()+6)/7;
        for(int w=0;w<weeks;w++){
            LinearLayout days=row();days.setGravity(Gravity.TOP);monthCard.addView(days);
            for(int c=0;c<7;c++){
                int number=w*7+c-offset+1;LinearLayout cell=vertical();cell.setPadding(dp(3),dp(5),dp(3),dp(4));
                LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(70),1);p.setMargins(dp(1),dp(2),dp(1),dp(2));days.addView(cell,p);
                if(number<1||number>calendarMonth.lengthOfMonth())continue;
                LocalDate date=calendarMonth.atDay(number);boolean selected=date.equals(selectedDay),isToday=date.equals(LocalDate.now());
                cell.setBackground(selected?bg(0xffeef1ff,10):isToday?border(0xfff6f8ff,10):bg(WHITE,10));
                TextView day=text(String.valueOf(number)+(isToday?"·":""),14,selected||isToday?BLUE:INK,true);day.setGravity(Gravity.CENTER);cell.addView(day,new LinearLayout.LayoutParams(-1,dp(22)));
                List<Task> items=byDate.getOrDefault(date.toString(),Collections.emptyList());
                if(!items.isEmpty()){
                    TextView label=text(items.size()+"项",9,BLUE,true);label.setGravity(Gravity.CENTER);cell.addView(label,new LinearLayout.LayoutParams(-1,dp(15)));
                    TextView title=text(items.get(0).title,9,items.get(0).done?MUTED:INK,false);title.setSingleLine(true);title.setEllipsize(TextUtils.TruncateAt.END);title.setGravity(Gravity.CENTER);cell.addView(title,new LinearLayout.LayoutParams(-1,dp(15)));
                }
                cell.setContentDescription(date+"，"+items.size()+"项日程");cell.setOnClickListener(v->{selectedDay=date;renderCalendar();});
            }
        }
        gap(calendarContent,12);LinearLayout heading=row();
        TextView headingTitle=text(selectedDay==null?"本月全部日程":selectedDay.format(DateTimeFormatter.ofPattern("M月d日"))+"的日程",18,INK,true);addWeighted(heading,headingTitle);
        if(selectedDay!=null){TextView all=button("查看整月",BLUE,0xffeef1ff);heading.addView(all);all.setOnClickListener(v->{selectedDay=null;renderCalendar();});}
        calendarContent.addView(heading);gap(calendarContent,5);
        List<Task> shown=new ArrayList<>();for(Task t:entries)if(selectedDay==null||t.date.equals(selectedDay.toString()))shown.add(t);
        calendarContent.addView(text(shown.size()+(showCompleted?"项 · 包含已完成及重复安排":"项 · 仅未完成及重复安排"),11,MUTED,false));gap(calendarContent,8);
        if(shown.isEmpty()){
            TextView empty=text(selectedDay==null?"这个月还没有日程":"这一天还没有日程",15,MUTED,false);empty.setPadding(dp(8),dp(22),dp(8),dp(22));calendarContent.addView(empty);
        }else{
            String last="";for(Task task:shown){if(selectedDay==null&&!task.date.equals(last)){TextView day=text(LocalDate.parse(task.date).format(DateTimeFormatter.ofPattern("M月d日 E",Locale.CHINA)),12,MUTED,true);day.setPadding(dp(4),dp(10),0,dp(5));calendarContent.addView(day);last=task.date;}calendarContent.addView(card(task));}
        }
        TextView hint=text("未定日期的待办在首页查看。",11,MUTED,false);hint.setPadding(dp(4),dp(12),0,dp(8));calendarContent.addView(hint);
    }
    interface Change {void accept(String text);}
    TextWatcher watcher(Change c){return new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int count,int after){}public void onTextChanged(CharSequence s,int a,int before,int count){c.accept(s.toString());}public void afterTextChanged(Editable e){}};}
    LinkedHashMap<String,String> ranges(){LinkedHashMap<String,String> result=new LinkedHashMap<>();for(Map.Entry<String,String> e:TimeParser.DEFAULTS.entrySet())result.put(e.getKey(),prefs.getString("range_"+e.getKey(),e.getValue()));return result;}
    String timeLabel(Task t){return t.dateLabel()+(t.period.isEmpty()||t.deadline?"":" "+ranges().get(t.period).replace("-","—"));}
    List<String> categories(){
        LinkedHashSet<String> names=new LinkedHashSet<>(Arrays.asList("工作","生活","学习"));
        try{org.json.JSONArray stored=new org.json.JSONArray(prefs.getString("categories","[]"));for(int i=0;i<stored.length();i++){String name=stored.optString(i).trim();if(!name.isEmpty()&&!name.equals("全部"))names.add(name);}}catch(org.json.JSONException ignored){}
        for(Task task:store.all())if(!task.category.isEmpty()&&!task.category.equals("全部"))names.add(task.category);
        return new ArrayList<>(names);
    }
    void rememberCategory(String name){List<String> names=categories();if(!names.contains(name))names.add(name);prefs.edit().putString("categories",new org.json.JSONArray(names).toString()).apply();}
    void chooseCategory(Task draft,TextView label,Runnable selected){
        List<String> names=categories();if(!names.contains(draft.category))names.add(draft.category);
        ArrayList<String> options=new ArrayList<>(names);options.add("＋新建分类");
        ListPopupWindow dropdown=new ListPopupWindow(this);dropdown.setAnchorView(label);dropdown.setWidth(label.getWidth());dropdown.setHeight(-2);dropdown.setModal(true);dropdown.setInputMethodMode(ListPopupWindow.INPUT_METHOD_NOT_NEEDED);dropdown.setVerticalOffset(dp(4));dropdown.setBackgroundDrawable(border(WHITE,10));dropdown.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,options));
        dropdown.setOnItemClickListener((parent,view,index,id)->{
            dropdown.dismiss();if(index==names.size()){createCategory(draft,label,selected);return;}
            draft.category=names.get(index);label.setText(draft.category);selected.run();
        });dropdown.show();dropdown.getListView().setChoiceMode(ListView.CHOICE_MODE_SINGLE);dropdown.setSelection(names.indexOf(draft.category));
    }
    void createCategory(Task draft,TextView label,Runnable selected){
        LinearLayout form=vertical();form.setPadding(dp(22),0,dp(22),dp(16));EditText name=field(form,"分类名称","",false);name.setHint("例如：个人项目、健康、家庭");
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("新建分类").setView(form).setNegativeButton("取消",null).setPositiveButton("创建",null).create();
        dialog.setOnShowListener(v->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(x->{String value=name.getText().toString().trim();if(value.isEmpty()||value.length()>24||value.equals("全部")){name.setError("请输入1—24个字，不能使用“全部”");return;}rememberCategory(value);draft.category=value;label.setText(value);selected.run();renderCategories();dialog.dismiss();}));dialog.show();
    }
    void renderCategories(){if(page==1||filterBar==null)return;filterBar.removeAllViews();LinkedHashSet<String> cats=new LinkedHashSet<>();cats.add("全部");cats.addAll(categories());for(String name:cats){TextView chip=text(name,12,category.equals(name)?BLUE:MUTED,category.equals(name));chip.setPadding(dp(12),dp(7),dp(12),dp(7));chip.setBackground(bg(category.equals(name)?0xffe9edff:BG,20));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-2,dp(34));p.setMargins(0,0,dp(6),0);filterBar.addView(chip,p);chip.setOnClickListener(v->{category=name;renderCategories();renderTasks();});}}
    void renderTasks(){
        if(page==2){renderWeek();return;}
        if(page==1){renderCalendar();return;}
        if(orderLabel!=null)orderLabel.setText(sortLabel()+"  ⇅");
        if(reordering)return;List<Task> all=store.all();LocalDate today=LocalDate.now();long active=0,todayCount=0,done=0;
        for(Task t:all){if(t.done){done++;continue;}active++;if(t.date.equals(today.toString()))todayCount++;}
        summary.removeAllViews();stat("未完成",active,BLUE);stat("今日安排",todayCount,0xff289b88);stat("已完成",done,MUTED);
        visible=new ArrayList<>();for(Task t:all){if(tab==2?!t.done:t.done)continue;if(tab==1&&!t.date.equals(today.toString()))continue;if(!category.equals("全部")&&!category.equals(t.category))continue;if(!query.isEmpty()&&!(t.title+" "+t.location+" "+t.note+" "+t.category).toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT)))continue;visible.add(t);}
        if(sort==1)visible.sort(TaskOrder.TIME);else if(sort==2)visible.sort(Comparator.comparing((Task t)->t.category).thenComparingLong(t->t.position));else if(sort==3)visible.sort(TaskOrder.PRIORITY);
        String[] sorts={"手动排序 · 长按拖动","按事件开始时间排列","按分类排列","按优先级排列"};countLabel.setText(visible.size()+" 个事项  ·  "+sorts[sort]);
        list.removeAllViews();if(visible.isEmpty()){LinearLayout empty=vertical();empty.setGravity(Gravity.CENTER);empty.setPadding(dp(24),dp(50),dp(24),dp(30));TextView icon=text("✓",48,0xffbdc8f9,true);empty.addView(icon);gap(empty,16);empty.addView(text(query.isEmpty()?(tab==2?"完成的每一步，都算数":"给今天留一点条理"):"没有找到相关事项",18,INK,true));gap(empty,10);TextView desc=text(query.isEmpty()?"从下方写下一句话，开始记录。":"试试其他关键词或分类。",13,MUTED,false);empty.addView(desc);list.addView(empty);return;}
        String group="";for(Task t:visible){String g=sort==1?dateGroup(t):sort==2?t.category:sort==3?new String[]{"高优先级","中优先级","低优先级"}[t.priority]:"";if(!g.isEmpty()&&!g.equals(group)){TextView heading=text(g,12,MUTED,true);heading.setPadding(dp(5),dp(12),0,dp(8));list.addView(heading);group=g;}list.addView(card(t));}
    }
    void stat(String label,long number,int color){LinearLayout block=row();block.setPadding(dp(10),0,dp(6),0);block.setBackground(bg(WHITE,12));TextView value=text(String.valueOf(number),20,color,true);block.addView(value);TextView caption=text(label,11,MUTED,false);caption.setPadding(dp(6),0,0,0);block.addView(caption);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(44),1);p.setMargins(0,0,dp(6),0);summary.addView(block,p);}
    String dateGroup(Task t){if(t.date.isEmpty())return "未定日期";LocalDate d=LocalDate.parse(t.date),today=LocalDate.now();return d.isBefore(today)?"已逾期":d.equals(today)?"今天":d.equals(today.plusDays(1))?"明天":"之后";}
    View card(Task t){
        Task saved=page==1?store.find(t.id):t;Task actual=saved==null?t:saved;
        boolean projected=page==1&&!t.date.equals(actual.date);
        LinearLayout card=row();card.setPadding(dp(12),dp(12),dp(10),dp(12));card.setBackground(border(WHITE,16));LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2);cp.setMargins(0,dp(3),0,dp(7));card.setLayoutParams(cp);card.setTag(t.id);
        int rank=page==0?visible.indexOf(t)+1:0;String level=new String[]{"高","中","低"}[t.priority];boolean priorityMarker=page==0&&sort==3;
        TextView check=text(priorityMarker?level:rank>0?String.valueOf(rank):projected?"↻":"·",14,BLUE,true);check.setGravity(Gravity.CENTER);check.setBackground(bg(0xffeef1ff,12));check.setContentDescription(priorityMarker?"事项优先级："+level:rank>0?"当前排序第"+rank+"项":"日程标记");card.addView(check,new LinearLayout.LayoutParams(dp(39),dp(40)));
        LinearLayout body=vertical();body.setPadding(dp(12),0,dp(3),0);TextView title=text(t.title,15,t.done?MUTED:INK,true);title.setMaxLines(3);if(t.done)title.setPaintFlags(title.getPaintFlags()|android.graphics.Paint.STRIKE_THRU_TEXT_FLAG);body.addView(title);gap(body,5);
        TextView time=text(timeLabel(t),11,!t.done&&!t.date.isEmpty()&&t.endMillis()<System.currentTimeMillis()?0xffc07958:MUTED,false);body.addView(time);if(!t.location.isEmpty()){gap(body,4);TextView place=text("地点："+t.location,11,MUTED,false);place.setMaxLines(2);body.addView(place);}gap(body,6);
        LinearLayout tags=row();TextView cat=text(t.category,10,BLUE,true);cat.setPadding(dp(7),dp(3),dp(7),dp(3));cat.setBackground(bg(0xffeef1ff,5));tags.addView(cat);
        TextView priorityTag=text(level+"优先级",10,new int[]{WHITE,0xff253163,0xff5b6599}[t.priority],true);priorityTag.setPadding(dp(7),dp(3),dp(7),dp(3));priorityTag.setBackground(bg(new int[]{0xff3344c8,0xffa0acf5,0xfff0f2ff}[t.priority],5));priorityTag.setContentDescription("优先级标签："+level);LinearLayout.LayoutParams tagParams=new LinearLayout.LayoutParams(-2,-2);tagParams.setMarginStart(dp(6));tags.addView(priorityTag,tagParams);body.addView(tags);
        String extras=(!t.repeat.equals("不重复")?"↻ "+t.repeat:"")+(t.remind?(!t.repeat.equals("不重复")?"  ":"")+"◷ "+t.reminderLabel():"");if(!extras.isEmpty()){gap(body,4);body.addView(text(extras,10,MUTED,false));}card.addView(body,new LinearLayout.LayoutParams(0,-2,1));
        body.setOnClickListener(v->edit(actual));
        if(projected){TextView badge=text("重复预览 · 编辑将修改原重复事项",10,BLUE,false);body.addView(badge);}
        ImageView more=new ImageView(this);more.setImageResource(R.drawable.ic_more);more.setPadding(dp(10),dp(10),dp(10),dp(10));more.setContentDescription("更多操作："+t.title);more.setBackground(new android.graphics.drawable.RippleDrawable(ColorStateList.valueOf(0x184561ee),bg(Color.TRANSPARENT,12),null));card.addView(more,new LinearLayout.LayoutParams(dp(40),dp(44)));more.setOnClickListener(v->{if(projected)edit(actual);else taskMenu(actual,more);});
        if(page==0&&sort==0){View.OnLongClickListener drag=v->{dragging=t;reordering=true;android.content.ClipData data=android.content.ClipData.newPlainText("task",String.valueOf(t.id));card.startDragAndDrop(data,new View.DragShadowBuilder(card),card,0);return true;};card.setOnLongClickListener(drag);body.setOnLongClickListener(drag);
            card.setOnDragListener((v,event)->{switch(event.getAction()){case DragEvent.ACTION_DRAG_STARTED:return true;case DragEvent.ACTION_DRAG_ENTERED:card.setAlpha(.55f);return true;case DragEvent.ACTION_DRAG_EXITED:card.setAlpha(1);return true;case DragEvent.ACTION_DROP:card.setAlpha(1);if(dragging!=null&&dragging.id!=t.id){List<Task> order=store.all();Task from=null;for(Task item:order)if(item.id==dragging.id)from=item;if(from!=null){order.remove(from);int at=0;while(at<order.size()&&order.get(at).id!=t.id)at++;order.add(at,from);store.reorder(order);}}return true;case DragEvent.ACTION_DRAG_ENDED:card.setAlpha(1);if(reordering){reordering=false;dragging=null;handler.post(()->renderTasks());}return true;default:return true;}});
        }if(projected)return card;
        LinearLayout complete=vertical();complete.setGravity(Gravity.CENTER);complete.setPadding(dp(8),dp(8),dp(8),dp(8));int accent=actual.done?BLUE:0xff278773;
        GradientDrawable tile=bg(actual.done?0xffeef1ff:0xffeaf6f1,16);tile.setStroke(dp(1),actual.done?0xffdce3fc:0xffd4eade);complete.setBackground(new android.graphics.drawable.RippleDrawable(ColorStateList.valueOf(0x184561ee),tile,null));
        ImageView icon=new ImageView(this);icon.setImageResource(actual.done?R.drawable.ic_swipe_restore:R.drawable.ic_swipe_done);icon.setBackground(bg(WHITE,16));icon.setPadding(dp(6),dp(6),dp(6),dp(6));icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);complete.addView(icon,new LinearLayout.LayoutParams(dp(32),dp(32)));gap(complete,2);
        TextView label=text(actual.done?"恢复":"完成",12,accent,true);label.setGravity(Gravity.CENTER);complete.addView(label);complete.setContentDescription(actual.done?"左滑恢复事项":"左滑完成事项");complete.setOnClickListener(v->confirmCompletion(actual));
        card.setLayoutParams(new FrameLayout.LayoutParams(-1,-2));SwipeActionLayout swipe=new SwipeActionLayout(this,card,complete,dp(76));swipe.setLayoutParams(cp);return swipe;
    }
    String sortLabel(){return new String[]{"手动排序","时间排序","分类排序","优先级排序"}[sort];}
    void confirmCompletion(Task task){AlertDialog confirm=new AlertDialog.Builder(this).setCustomTitle(dialogHeading(task.done?"恢复为待办？":"确认完成？")).setMessage(task.title).setNegativeButton("取消",null).setPositiveButton(task.done?"确认恢复":"确认完成",(d,n)->{Task current=store.find(task.id);if(current==null)return;Reminders.cancel(this,current);store.complete(current);Reminders.schedule(this,current);renderTasks();View content=page==2?weekBody:page==1?calendarContent:list;if(content!=null){content.setAlpha(.35f);content.animate().alpha(1f).setDuration(240).start();}}).create();confirm.show();polishDialog(confirm);}
    void chooseSort(){String[] options={"手动排序（长按事项拖动）","按事件开始时间排序","按分类分组","按优先级分组"};new AlertDialog.Builder(this).setTitle("排列方式").setSingleChoiceItems(options,sort,(d,n)->{sort=n;prefs.edit().putInt("sort",n).apply();d.dismiss();renderTasks();}).setNegativeButton("取消",null).show();}
    void taskMenu(Task t,View anchor){
        LinearLayout menu=vertical();menu.setPadding(dp(8),dp(8),dp(8),dp(8));menu.setBackground(border(WHITE,18));menu.setClipToOutline(true);menu.setContentDescription("事项操作菜单");
        LinearLayout heading=vertical();heading.setPadding(dp(12),dp(8),dp(12),dp(10));heading.addView(text("事项操作",11,MUTED,false));gap(heading,4);TextView name=text(t.title,13,INK,true);name.setMaxLines(2);name.setEllipsize(android.text.TextUtils.TruncateAt.END);heading.addView(name);menu.addView(heading);
        int width=Math.min(dp(236),getResources().getDisplayMetrics().widthPixels-dp(32));PopupWindow popup=new PopupWindow(menu,width,ViewGroup.LayoutParams.WRAP_CONTENT,true);popup.setBackgroundDrawable(border(WHITE,18));popup.setElevation(dp(12));popup.setOutsideTouchable(true);popup.setInputMethodMode(PopupWindow.INPUT_METHOD_NOT_NEEDED);
        menu.addView(taskMenuAction("编辑事项",R.drawable.ic_menu_edit,INK,()->{popup.dismiss();edit(t);}));
        View divider=new View(this);divider.setBackgroundColor(LINE);LinearLayout.LayoutParams dividerParams=new LinearLayout.LayoutParams(-1,dp(1));dividerParams.setMargins(dp(12),dp(5),dp(12),dp(5));menu.addView(divider,dividerParams);
        menu.addView(taskMenuAction("删除事项",R.drawable.ic_menu_delete,0xffc44b52,()->{popup.dismiss();confirmDelete(t);}));
        popup.showAsDropDown(anchor,0,dp(6),Gravity.END);
    }
    View taskMenuAction(String label,int drawable,int color,Runnable action){
        LinearLayout item=row();item.setPadding(dp(12),0,dp(12),0);item.setMinimumHeight(dp(48));item.setBackground(new android.graphics.drawable.RippleDrawable(ColorStateList.valueOf(0x184561ee),bg(Color.TRANSPARENT,10),null));
        ImageView icon=new ImageView(this);icon.setImageResource(drawable);icon.setImageTintList(ColorStateList.valueOf(color));icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);item.addView(icon,new LinearLayout.LayoutParams(dp(20),dp(20)));
        TextView title=text(label,14,color,false);LinearLayout.LayoutParams titleParams=new LinearLayout.LayoutParams(-2,-2);titleParams.setMarginStart(dp(12));item.addView(title,titleParams);item.setOnClickListener(v->action.run());return item;
    }
    void confirmDelete(Task task){
        AlertDialog dialog=new AlertDialog.Builder(this).setCustomTitle(dialogHeading("删除这条事项？")).setMessage(task.title+"\n\n删除后无法恢复。").setNegativeButton("取消",null).setPositiveButton("删除",(d,n)->{Task current=store.find(task.id);if(current==null)return;Reminders.cancel(this,current);store.delete(current.id);renderTasks();renderCategories();}).create();dialog.show();polishDialog(dialog);dialog.getButton(AlertDialog.BUTTON_POSITIVE).setBackground(new android.graphics.drawable.RippleDrawable(ColorStateList.valueOf(0x22ffffff),bg(0xffc44b52,14),null));
    }
    TimeParser.Result parseInput(String input){
        if(!NotificationExtractor.looksLikeNotice(input))return TimeParser.parse(input,LocalDateTime.now(),ranges());
        NotificationExtractor.Result extracted=NotificationExtractor.extract(input,LocalDateTime.now(),ranges());TimeParser.Result result=new TimeParser.Result();result.task=extracted.task;result.recognized=!result.task.date.isEmpty();result.noteRecognized=true;result.reminderRecognized=true;result.categoryRecognized=true;result.priorityRecognized=true;result.repeatRecognized=true;return result;
    }
    void newTask(){String s=quick.getText().toString().trim();if(s.length()>20000){toast("内容过长，请分段输入（最多20000字）");return;}if(s.isEmpty()){Task t=new Task();t.remind=true;t.lead=30;if(page==1&&selectedDay!=null){t.date=selectedDay.toString();t.anchorDay=selectedDay.getDayOfMonth();}edit(t);return;}if(MultiEventParser.extract(s,LocalDateTime.now(),ranges()).size()>1){startActivityForResult(new Intent(this,BatchImportActivity.class).putExtra("source",s),71);return;}try{Task t=parseInput(s).task;if(t.title.isEmpty())t.title=s;edit(t);}catch(Exception e){toast("这段时间不合法，请手动选择日期和时间");Task t=new Task();t.remind=true;t.lead=30;t.title=s;edit(t);}}
    EditText field(LinearLayout form,String label,String value,boolean multiline){TextView caption=text(label,12,MUTED,true);caption.setPadding(0,dp(12),0,dp(6));form.addView(caption);EditText e=new EditText(this);e.setText(value);e.setTextColor(INK);e.setHintTextColor(MUTED);e.setTextSize(15);e.setPadding(dp(12),dp(10),dp(12),dp(10));e.setBackground(border(BG,10));e.setSingleLine(!multiline);if(multiline)e.setMinLines(2);form.addView(e,new LinearLayout.LayoutParams(-1,-2));return e;}
    TextView selectionLabel(String value){TextView label=text(value,15,INK,false);label.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);label.setPadding(dp(12),dp(10),dp(12),dp(10));label.setSingleLine(true);label.setEllipsize(android.text.TextUtils.TruncateAt.END);label.setCompoundDrawablesRelativeWithIntrinsicBounds(null,null,getDrawable(R.drawable.ic_dropdown),null);label.setCompoundDrawablePadding(dp(8));return label;}
    Spinner spinner(LinearLayout form,String label,String[] options,String current){TextView caption=text(label,12,MUTED,true);caption.setPadding(0,dp(12),0,dp(6));form.addView(caption);Spinner s=new Spinner(this);ArrayAdapter<String> a=new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,options){@Override public View getView(int position,View convertView,ViewGroup parent){TextView selected=selectionLabel(getItem(position));selected.setLayoutParams(new ViewGroup.LayoutParams(-1,dp(48)));return selected;}};s.setAdapter(a);for(int n=0;n<options.length;n++)if(options[n].equals(current))s.setSelection(n);s.setPadding(0,0,0,0);s.setBackground(border(BG,10));form.addView(s,new LinearLayout.LayoutParams(-1,dp(48)));return s;}
    void edit(Task original){new TaskEditor(this).edit(original,false,t->{
        if(t.id==0)t.position=System.currentTimeMillis();else Reminders.cancel(this,t);rememberCategory(t.category);store.save(t);Reminders.schedule(this,t);
        quick.setText("");renderCategories();renderTasks();toast("已保存"+(t.remind&&!Reminders.exactAllowed(this)?" · 未开启准时提醒，通知可能延后":""));
        if(t.remind&&Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},41);
        else if(t.remind&&!Reminders.notificationsAllowed(this))new AlertDialog.Builder(this).setTitle("通知尚未开启").setMessage("事项已保存。请在系统设置中允许通知和横幅，否则无法收到提醒。").setPositiveButton("打开通知设置",(d,n)->notificationSettings()).setNegativeButton("稍后",null).show();
    });}
    int previousEveningLead(Task t){LocalTime at=t.time.isEmpty()?LocalTime.MIDNIGHT:LocalTime.parse(t.time);LocalTime evening=LocalTime.parse(ranges().get("晚上").split("-")[0]);return 1440+at.getHour()*60+at.getMinute()-evening.getHour()*60-evening.getMinute();}
    void notificationSettings(){startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,getPackageName()));}
    void addDesktopWidget(){
        android.appwidget.AppWidgetManager manager=android.appwidget.AppWidgetManager.getInstance(this);
        boolean supported=manager.isRequestPinAppWidgetSupported();
        AlertDialog guide=new AlertDialog.Builder(this).setCustomTitle(dialogHeading("添加桌面小组件")).setMessage("长按手机桌面空白处 → 小组件（或添加工具）→ 找到拾序 · 桌面待办 → 添加到桌面。\n\n可查看今日与明日、切换全部、勾选完成，长按组件可调整大小。\n\n"+(supported?"也可点击下方按钮请求桌面添加。若桌面没有弹出确认，请按上面的方法手动添加。":"当前桌面不支持软件内直接添加，请使用上面的手动方法。")).setNegativeButton("知道了",null).create();
        if(supported)guide.setButton(AlertDialog.BUTTON_POSITIVE,"请求桌面添加",(d,n)->{});
        guide.show();polishDialog(guide);
        if(supported)guide.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{try{boolean requested=manager.requestPinAppWidget(new ComponentName(this,TodoWidgetProvider.class),null,null);toast(requested?"已请求桌面添加；若没有弹窗，请按说明手动添加":"桌面未接受请求，请按说明手动添加");}catch(IllegalStateException|SecurityException error){toast("当前无法直接添加，请按说明手动添加");}});
    }
    void backgroundSettings(){
        try{startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,android.net.Uri.parse("package:"+getPackageName())));}
        catch(ActivityNotFoundException|SecurityException error){
            try{startActivity(new Intent(Settings.ACTION_SETTINGS));toast("请在系统设置中找到拾序，检查后台运行和省电策略");}
            catch(ActivityNotFoundException|SecurityException unavailable){toast("请手动进入系统设置，找到拾序并检查后台运行和省电策略");}
        }
    }
    @Override public void onRequestPermissionsResult(int code,String[] permissions,int[] grants){super.onRequestPermissionsResult(code,permissions,grants);if(code==41){if(!Reminders.notificationsAllowed(this))toast("尚未获得通知权限，可在设置中开启");else toast("通知已开启");}if(code==VoiceInput.REQUEST_MIC){if(grants.length>0&&grants[0]==android.content.pm.PackageManager.PERMISSION_GRANTED)startVoice();else toast("未开启麦克风权限，仍可打字记录");}}
    void settings(){
        LinearLayout form=vertical();form.setPadding(dp(22),dp(8),dp(22),dp(18));TextView tutorial=button("使用教程 · 自动识别",BLUE,0xffeef1ff);tutorial.setContentDescription("打开使用教程");form.addView(tutorial);gap(form,6);form.addView(text("左右翻图，看看如何把通知自动变成事项。",12,MUTED,false));gap(form,18);form.addView(text("时间有范围，安排更从容",18,INK,true));gap(form,8);TextView info=text("模糊时间按以下范围显示，提醒从范围起点计算。点击可修改。",13,MUTED,false);form.addView(info);gap(form,8);
        for(Map.Entry<String,String> e:ranges().entrySet()){TextView range=button(e.getKey()+"     "+e.getValue().replace("-","—"),INK,BG);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(48));p.setMargins(0,dp(6),0,0);form.addView(range,p);range.setOnClickListener(v->editRange(e.getKey(),range));}
        alertSettings(form);
        gap(form,16);TextView notif=button("通知与横幅设置  ↗",BLUE,0xffeef1ff);form.addView(notif);notif.setOnClickListener(v->notificationSettings());gap(form,8);TextView exact=button(Reminders.exactAllowed(this)?"准时提醒已开启":"开启准时提醒  ↗",BLUE,0xffeef1ff);form.addView(exact);exact.setOnClickListener(v->{if(!Reminders.exactAllowed(this))Reminders.openExact(this);else toast("准时提醒已开启");});gap(form,14);
        TextView background=button("后台运行设置  ↗",BLUE,0xffeef1ff);form.addView(background);background.setOnClickListener(v->backgroundSettings());gap(form,8);TextView backgroundHint=text("为确保提醒正常送达，请开启通知权限和自启动权限，将电池策略设为“无限制”，并允许后台运行。不同手机选项名称可能不同。",12,MUTED,false);form.addView(backgroundHint);gap(form,14);
        TextView widget=button("添加桌面小组件  ↗",BLUE,0xffeef1ff);form.addView(widget);widget.setOnClickListener(v->addDesktopWidget());gap(form,8);form.addView(text("今日和明日事项一起显示，可切换查看全部。点击标题编辑，右侧勾选完成后保留划线记录；长按桌面组件可调整大小。",12,MUTED,false));gap(form,14);
        Switch network=new Switch(this);network.setText("允许系统语音服务联网识别");network.setTextSize(13);network.setChecked(prefs.getBoolean("voice_network",false));network.setOnCheckedChangeListener((view,on)->prefs.edit().putBoolean("voice_network",on).apply());form.addView(network);gap(form,8);
        TextView local=text("事项仅本机保存 · 不保存录音 · 无账号\n语音优先离线；开启上方选项时，系统语音服务可能将音频发送给服务提供方。\n拾序 1.0.15 · 卸载应用会删除本地记录。",12,MUTED,false);form.addView(local);ScrollView sc=new ScrollView(this);sc.addView(form);AlertDialog settingsDialog=new AlertDialog.Builder(this).setTitle("设置").setView(sc).setPositiveButton("完成",(d,n)->renderTasks()).create();tutorial.setOnClickListener(v->{settingsDialog.dismiss();startActivity(new Intent(this,TutorialActivity.class));});settingsDialog.show();
    }
    void alertSettings(LinearLayout form){
        gap(form,16);form.addView(text("提醒方式",18,INK,true));
        Switch sound=new Switch(this);sound.setText("提醒时响铃");sound.setTextSize(14);sound.setChecked(ReminderAlertService.sound(this));form.addView(sound);
        Switch vibration=new Switch(this);vibration.setText("提醒时振动");vibration.setTextSize(14);vibration.setChecked(ReminderAlertService.vibration(this));form.addView(vibration);
        gap(form,4);form.addView(text("弹窗消息始终保留（系统横幅）",13,INK,false));
        String[] durations={"2秒","5秒","15秒","30秒"};Spinner duration=spinner(form,"响铃与振动持续时间",durations,ReminderAlertService.seconds(this)+"秒");duration.setContentDescription("选择提醒持续时间");
        Runnable visible=()->duration.setEnabled(sound.isChecked()||vibration.isChecked());visible.run();
        sound.setOnCheckedChangeListener((v,on)->{prefs.edit().putBoolean("alert_sound",on).apply();visible.run();ReminderAlertService.stopAll();});
        vibration.setOnCheckedChangeListener((v,on)->{prefs.edit().putBoolean("alert_vibration",on).apply();visible.run();ReminderAlertService.stopAll();});
        duration.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onNothingSelected(AdapterView<?> p){}public void onItemSelected(AdapterView<?> p,View v,int at,long id){int seconds=new int[]{2,5,15,30}[at];if(seconds!=ReminderAlertService.seconds(MainActivity.this)){prefs.edit().putInt("alert_seconds",seconds).apply();ReminderAlertService.stopAll();}}});
        gap(form,6);form.addView(text("默认开启弹窗消息与振动，响铃可单独开启。两项都关闭时只显示消息。到时可停止、完成或延后10分钟；超时自动停止。响铃使用手机闹钟音量，振动受硬件与系统设置影响；请允许系统横幅通知。",12,MUTED,false));
    }
    void editRange(String key,TextView label){LinearLayout form=vertical();form.setPadding(dp(22),0,dp(22),dp(15));String[] old=ranges().get(key).split("-");EditText start=field(form,"开始时间（24小时制）",old[0],false),end=field(form,"结束时间（当天最晚24:00）",old[1],false);AlertDialog d=new AlertDialog.Builder(this).setTitle(key+"的时间范围").setView(form).setNegativeButton("取消",null).setPositiveButton("保存",null).create();d.setOnShowListener(v->d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(b->{String a=start.getText().toString().trim(),z=end.getText().toString().trim();try{LocalTime first=LocalTime.parse(a);int begin=first.toSecondOfDay(),finish=z.equals("24:00")?86400:LocalTime.parse(z).toSecondOfDay();if(finish<=begin)throw new IllegalArgumentException();a=first.format(DateTimeFormatter.ofPattern("HH:mm"));if(!z.equals("24:00"))z=LocalTime.parse(z).format(DateTimeFormatter.ofPattern("HH:mm"));prefs.edit().putString("range_"+key,a+"-"+z).apply();for(Task t:store.all())if(t.period.equals(key)||!t.reminderPeriod.isEmpty()||(key.equals("晚上")&&!t.reminderEveningTime.isEmpty())){Reminders.cancel(this,t);if(t.period.equals(key))t.time=a;if(!t.reminderPeriod.isEmpty())TimeParser.applyDeadlineReminder(t,ranges());if(key.equals("晚上")&&!t.reminderEveningTime.isEmpty()){t.reminderEveningTime=a;t.lead=previousEveningLead(t);}t.lastNotified=0;store.save(t);Reminders.schedule(this,t);}label.setText(key+"     "+a+"—"+z);d.dismiss();renderTasks();}catch(Exception e){end.setError("请输入有效范围，例如18:30至24:00，结束须晚于开始");}}));d.show();}
}



