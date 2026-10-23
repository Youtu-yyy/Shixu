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

public final class TaskEditor extends ContextThemeWrapper {
    static final int BG=MainActivity.BG,INK=MainActivity.INK,MUTED=MainActivity.MUTED,BLUE=MainActivity.BLUE,LINE=MainActivity.LINE,WHITE=Color.WHITE;
    final SharedPreferences prefs;
    final Handler handler=new Handler(Looper.getMainLooper());
    public TaskEditor(Activity owner){super(owner,owner.getTheme());prefs=owner.getSharedPreferences("settings",Context.MODE_PRIVATE);}
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
    interface Change {void accept(String text);}
    TextWatcher watcher(Change c){return new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int count,int after){}public void onTextChanged(CharSequence s,int a,int before,int count){c.accept(s.toString());}public void afterTextChanged(Editable e){}};}
    LinkedHashMap<String,String> ranges(){LinkedHashMap<String,String> result=new LinkedHashMap<>();for(Map.Entry<String,String> e:TimeParser.DEFAULTS.entrySet())result.put(e.getKey(),prefs.getString("range_"+e.getKey(),e.getValue()));return result;}
    String timeLabel(Task t){return t.dateLabel()+(t.period.isEmpty()||t.deadline?"":" "+ranges().get(t.period).replace("-","—"));}
    List<String> categories(){
        LinkedHashSet<String> names=new LinkedHashSet<>(Arrays.asList("工作","生活","学习"));
        try{org.json.JSONArray stored=new org.json.JSONArray(prefs.getString("categories","[]"));for(int i=0;i<stored.length();i++){String name=stored.optString(i).trim();if(!name.isEmpty()&&!name.equals("全部"))names.add(name);}}catch(org.json.JSONException ignored){}
        try(Store store=new Store(this)){for(Task task:store.all())if(!task.category.isEmpty()&&!task.category.equals("全部"))names.add(task.category);}
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
        dialog.setOnShowListener(v->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(x->{String value=name.getText().toString().trim();if(value.isEmpty()||value.length()>24||value.equals("全部")){name.setError("请输入1—24个字，不能使用“全部”");return;}rememberCategory(value);draft.category=value;label.setText(value);selected.run();dialog.dismiss();}));dialog.show();
    }
    TimeParser.Result parseInput(String input){
        if(!NotificationExtractor.looksLikeNotice(input))return TimeParser.parse(input,LocalDateTime.now(),ranges());
        NotificationExtractor.Result extracted=NotificationExtractor.extract(input,LocalDateTime.now(),ranges());TimeParser.Result result=new TimeParser.Result();result.task=extracted.task;result.recognized=!result.task.date.isEmpty();result.noteRecognized=true;result.reminderRecognized=true;result.categoryRecognized=true;result.priorityRecognized=true;result.repeatRecognized=true;return result;
    }
    EditText field(LinearLayout form,String label,String value,boolean multiline){TextView caption=text(label,12,MUTED,true);caption.setPadding(0,dp(12),0,dp(6));form.addView(caption);EditText e=new EditText(this);e.setText(value);e.setTextColor(INK);e.setHintTextColor(MUTED);e.setTextSize(15);e.setPadding(dp(12),dp(10),dp(12),dp(10));e.setBackground(border(BG,10));e.setSingleLine(!multiline);if(multiline)e.setMinLines(2);form.addView(e,new LinearLayout.LayoutParams(-1,-2));return e;}
    TextView selectionLabel(String value){TextView label=text(value,15,INK,false);label.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);label.setPadding(dp(12),dp(10),dp(12),dp(10));label.setSingleLine(true);label.setEllipsize(android.text.TextUtils.TruncateAt.END);label.setCompoundDrawablesRelativeWithIntrinsicBounds(null,null,getDrawable(R.drawable.ic_dropdown),null);label.setCompoundDrawablePadding(dp(8));return label;}
    Spinner spinner(LinearLayout form,String label,String[] options,String current){TextView caption=text(label,12,MUTED,true);caption.setPadding(0,dp(12),0,dp(6));form.addView(caption);Spinner s=new Spinner(this);ArrayAdapter<String> a=new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,options){@Override public View getView(int position,View convertView,ViewGroup parent){TextView selected=selectionLabel(getItem(position));selected.setLayoutParams(new ViewGroup.LayoutParams(-1,dp(48)));return selected;}};s.setAdapter(a);for(int n=0;n<options.length;n++)if(options[n].equals(current))s.setSelection(n);s.setPadding(0,0,0,0);s.setBackground(border(BG,10));form.addView(s,new LinearLayout.LayoutParams(-1,dp(48)));return s;}
    public void edit(Task original,boolean batch,java.util.function.Consumer<Task> accepted){
        Task draft=original.copy();boolean fresh=draft.id==0&&!batch;
        boolean[] manual=new boolean[9];ReminderFields[] reminderFields={null}; // date, time, category, priority, recurrence, note, deadline kind
        LinearLayout form=vertical();form.setPadding(dp(20),dp(2),dp(20),dp(18));
        EditText title=field(form,"事项标题 *",draft.title,false);title.setContentDescription("事项标题");title.setHint("例如：每周五晚上交作业，提前半小时提醒");
        TextView recognized=text(fresh?recognitionSummary(draft):"可手动调整事项信息"+(draft.reminderOverrideMillis>0?"\n当前提醒："+draft.reminderLabel():""),12,BLUE,false);recognized.setPadding(0,dp(10),0,0);form.addView(recognized);
        boolean[] updatingKind={false};Switch kind=new Switch(this);kind.setText("截止事项（在指定日期或时间前完成）");kind.setTextSize(12);kind.setChecked(draft.deadline);form.addView(kind);kind.setOnCheckedChangeListener((v,checked)->{if(!updatingKind[0]){manual[6]=true;draft.deadline=checked;if(checked&&!manual[8])TimeParser.applyDeadlineReminder(draft,ranges());else if(!checked){draft.beforeDay=false;copyPeriodReminder(new Task(),draft);if(!manual[8])draft.lead=30;}if(reminderFields[0]!=null)reminderFields[0].refresh();}});
        LinearLayout dateRow=row();TextView dateButton=button(draft.date.isEmpty()?"选择日期":draft.date,BLUE,0xffeef1ff);addWeighted(dateRow,dateButton);
        dateButton.setContentDescription("选择事项日期");TextView clear=button("清除",MUTED,BG);dateRow.addView(clear);gap(form,12);form.addView(dateRow);
        TextView timeButton=button(timeLabel(draft),INK,BG);timeButton.setContentDescription("选择事项时间");gap(form,8);form.addView(timeButton);
        Runnable update=()->{dateButton.setText(draft.date.isEmpty()?"选择日期":draft.date);timeButton.setText(timeLabel(draft));if(draft.startMillis()!=original.startMillis())draft.clearSnooze();if(fresh&&!manual[8]&&!draft.date.isEmpty()&&!draft.time.isEmpty()&&draft.triggerMillis()>System.currentTimeMillis())draft.remind=true;if(reminderFields[0]!=null)reminderFields[0].refresh();};
        dateButton.setOnClickListener(v->{LocalDate d=draft.date.isEmpty()?LocalDate.now():LocalDate.parse(draft.date);new DatePickerDialog(this,(p,y,m,day)->{manual[0]=true;draft.date=LocalDate.of(y,m+1,day).toString();draft.anchorDay=LocalDate.of(y,m+1,day).plusDays(draft.beforeDay&&draft.deadline?1:0).getDayOfMonth();update.run();},d.getYear(),d.getMonthValue()-1,d.getDayOfMonth()).show();});
        clear.setOnClickListener(v->{manual[0]=true;manual[1]=true;draft.beforeDay=false;draft.date="";draft.time="";draft.endTime="";draft.endDayOffset=0;draft.period="";draft.remind=false;copyPeriodReminder(new Task(),draft);update.run();});
        timeButton.setOnClickListener(v->chooseTime(draft,()->{manual[0]=true;manual[1]=true;update.run();}));
        EditText location=field(form,"地点（可选）",draft.location,false);location.setContentDescription("事项地点");location.setHint("例如：H3301、图书馆");
        TextView categoryLabel=text("分类",12,MUTED,true);categoryLabel.setPadding(0,dp(12),0,dp(6));form.addView(categoryLabel);
        TextView cat=selectionLabel(draft.category);cat.setBackground(border(BG,10));cat.setContentDescription("选择分类");form.addView(cat,new LinearLayout.LayoutParams(-1,dp(48)));cat.setOnClickListener(v->chooseCategory(draft,cat,()->manual[2]=true));
        Spinner priority=spinner(form,"优先级",new String[]{"低","中","高"},new String[]{"高","中","低"}[draft.priority]);
        Spinner repeat=spinner(form,"重复",new String[]{"不重复","每天","每周","每月"},draft.repeat);
        EditText note=field(form,"备注",draft.note,true);note.setHint("可在输入中写：备注：带练习册");note.setMaxLines(5);note.setGravity(Gravity.TOP);reminderFields[0]=new ReminderFields(draft,form,()->{manual[8]=true;recognized.setText(recognitionSummary(draft));});recognized.setText(fresh?recognitionSummary(draft):recognized.getText());
        String[] normalized={title.getText().toString()};TimeParser.Result[] last={null};
        Runnable infer=()->{
            String input=title.getText().toString().trim();normalized[0]=input;if(!fresh||input.isEmpty())return;
            try{
                TimeParser.Result result=parseInput(input);Task parsed=result.task;
                manual[7]|=!location.getText().toString().equals(draft.location);
                if(manual[7])draft.location=location.getText().toString();
                else{draft.location=parsed.location.isEmpty()?original.location:parsed.location;location.setText(draft.location);}
                manual[3]|=2-priority.getSelectedItemPosition()!=draft.priority;
                manual[4]|=!repeat.getSelectedItem().toString().equals(draft.repeat);
                manual[5]|=!note.getText().toString().equals(draft.note);
                if(manual[3])draft.priority=2-priority.getSelectedItemPosition();
                if(manual[4])draft.repeat=repeat.getSelectedItem().toString();
                if(manual[5])draft.note=note.getText().toString();
                if(!manual[0]){if(result.recognized){draft.date=parsed.date;draft.anchorDay=parsed.anchorDay;if(!manual[1]&&!manual[6])draft.beforeDay=parsed.beforeDay;}else if(last[0]!=null&&last[0].recognized){draft.date=original.date;draft.anchorDay=original.anchorDay;}}
                if(!manual[6]){draft.deadline=result.recognized?parsed.deadline:original.deadline;updatingKind[0]=true;kind.setChecked(draft.deadline);updatingKind[0]=false;}
                if(!manual[1]){if(!parsed.time.isEmpty()){draft.time=parsed.time;draft.endTime=parsed.endTime;draft.endDayOffset=parsed.endDayOffset;draft.period=parsed.period;}else if(last[0]!=null&&!last[0].task.time.isEmpty()){draft.time=original.time;draft.endTime=original.endTime;draft.endDayOffset=original.endDayOffset;draft.period=original.period;}}
                if(!manual[2]){if(result.categoryRecognized)draft.category=parsed.category;else if(last[0]!=null&&last[0].categoryRecognized)draft.category=original.category;cat.setText(draft.category);}
                if(!manual[3]){if(result.priorityRecognized)draft.priority=parsed.priority;else if(last[0]!=null&&last[0].priorityRecognized)draft.priority=original.priority;priority.setSelection(2-draft.priority);}
                if(!manual[4]){if(result.repeatRecognized)draft.repeat=parsed.repeat;else if(last[0]!=null&&last[0].repeatRecognized)draft.repeat=original.repeat;String[] options={"不重复","每天","每周","每月"};for(int n=0;n<options.length;n++)if(options[n].equals(draft.repeat))repeat.setSelection(n);}
                if(!manual[5]){if(result.noteRecognized)draft.note=parsed.note;else if(last[0]!=null&&last[0].noteRecognized)draft.note=original.note;note.setText(draft.note);}
                if(!manual[8]&&result.reminderRecognized){draft.remind=parsed.remind;draft.lead=parsed.lead;copyPeriodReminder(parsed,draft);}else if(!manual[8]&&last[0]!=null&&last[0].reminderRecognized){draft.remind=original.remind;draft.lead=original.lead;copyPeriodReminder(original,draft);}
                if(!parsed.title.isEmpty())normalized[0]=parsed.title;
                draft.recognitionWarning=parsed.recognitionWarning;update.run();recognized.setText(recognitionSummary(draft)+"\n自动建议可修改；手动选择优先。");last[0]=result;
            }catch(Exception e){recognized.setText("这段时间暂未识别，请检查表达或手动选择。原文会保留。");}
        };
        if(fresh)title.addTextChangedListener(watcher(s->{handler.removeCallbacks(infer);handler.postDelayed(infer,500);}));
        ScrollView sc=new ScrollView(this);sc.addView(form);
        AlertDialog dialog=new AlertDialog.Builder(this).setCustomTitle(dialogHeading(fresh?"新建事项":"编辑事项")).setView(sc).setNegativeButton("取消",null).setPositiveButton(batch?"保留修改":"保存事项",null).create();
        dialog.setOnDismissListener(d->handler.removeCallbacks(infer));
        dialog.setOnShowListener(v->{dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(x->{
            handler.removeCallbacks(infer);
            // Do not reinterpret the already-normalized title of a quick-entry task unless edited.
            if(fresh&&(last[0]!=null||!title.getText().toString().equals(original.title)))infer.run();else normalized[0]=title.getText().toString().trim();
            String name=normalized[0];if(name.isEmpty()){title.setError("请填写标题");return;}
            draft.title=name;draft.refreshBeforeTitle();if(draft.category.isEmpty())draft.category="生活";
            draft.priority=2-priority.getSelectedItemPosition();draft.repeat=repeat.getSelectedItem().toString();draft.note=note.getText().toString().trim();draft.location=location.getText().toString().trim();
            if(!draft.repeat.equals("不重复")&&draft.date.isEmpty()){toast("重复事项需要先选择开始日期");return;}
            if(draft.startMillis()!=original.startMillis())draft.clearSnooze();
            if(!reminderFields[0].validate())return;try{accepted.accept(draft.copy());}catch(RuntimeException error){toast("保存失败，修改仍保留，请重试");return;}dialog.dismiss();((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(title.getWindowToken(),0);
        });dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);});dialog.show();polishDialog(dialog);
    }
    String recognitionSummary(Task t){
        String summary=t.date.isEmpty()?"在标题中写日期、时间等信息，可自动识别":(t.deadline?"截止时间：":"已识别：")+timeLabel(t);
        if(!t.location.isEmpty())summary+=" · 地点："+t.location;
        summary+=" · 分类："+t.category;
        summary+=" · "+new String[]{"高","中","低"}[t.priority]+"优先级";
        if(!t.repeat.equals("不重复"))summary+=" · "+t.repeat;
        if(t.remind)summary+=" · "+t.reminderLabel();
        if(t.deadline&&t.remind&&!t.date.isEmpty())summary+="\n提醒时间："+Instant.ofEpochMilli(t.triggerMillis()).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("M月d日 HH:mm"));
        if(!t.note.isEmpty())summary+=" · 已提取备注";
        if(!t.recognitionWarning.isEmpty())summary+="\n待核对："+t.recognitionWarning;
        return summary;
    }
    void ensureDate(Task t){if(t.date.isEmpty()){t.date=LocalDate.now().toString();t.anchorDay=LocalDate.now().getDayOfMonth();}}
    void chooseTime(Task draft,Runnable update){
        ArrayList<String> options=new ArrayList<>();options.add("精确时间…");options.add("全天（只记录日期）");options.add("自定义时间段…");
        for(Map.Entry<String,String> e:ranges().entrySet())options.add(e.getKey()+"  "+e.getValue().replace("-","—"));
        new AlertDialog.Builder(this).setTitle("选择时间或时间段").setItems(options.toArray(new String[0]),(d,n)->{
            if(n==0){LocalTime time=draft.time.isEmpty()?LocalTime.now():LocalTime.parse(draft.time);new TimePickerDialog(this,(picker,h,m)->{ensureDate(draft);draft.beforeDay=false;draft.time=String.format(Locale.ROOT,"%02d:%02d",h,m);draft.endTime="";draft.endDayOffset=0;draft.period="";update.run();},time.getHour(),time.getMinute(),true).show();}
            else if(n==1){ensureDate(draft);draft.time="";draft.endTime="";draft.endDayOffset=0;draft.period="";update.run();}
            else if(n==2)chooseRange(draft,update);
            else{String key=new ArrayList<>(ranges().keySet()).get(n-3);ensureDate(draft);draft.beforeDay=false;draft.period=key;draft.time=ranges().get(key).split("-")[0];draft.endTime="";draft.endDayOffset=0;update.run();}
        }).setNegativeButton("取消",null).show();
    }
    void chooseRange(Task draft,Runnable update){
        LinearLayout form=vertical();form.setPadding(dp(22),0,dp(22),dp(16));
        EditText start=field(form,"开始时间（如09:00）",draft.time.isEmpty()?"09:00":draft.time,false);
        EditText end=field(form,"结束时间（如11:00）",draft.endTime.isEmpty()?"11:00":draft.endTime,false);
        Switch overnight=new Switch(this);overnight.setText("次日结束");overnight.setChecked(draft.endDayOffset>0);form.addView(overnight);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("自定义时间段").setView(form).setNegativeButton("取消",null).setPositiveButton("确定",null).create();
        dialog.setOnShowListener(v->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(x->{try{
            DateTimeFormatter format=DateTimeFormatter.ofPattern("H:mm").withResolverStyle(java.time.format.ResolverStyle.STRICT);LocalTime first=LocalTime.parse(start.getText().toString().trim(),format);
            String raw=end.getText().toString().trim();LocalTime last=raw.equals("24:00")?LocalTime.MIDNIGHT:LocalTime.parse(raw,format);
            int offset=raw.equals("24:00")||overnight.isChecked()?1:0;
            if(last.toSecondOfDay()+offset*86400<=first.toSecondOfDay()){end.setError("结束须晚于开始；跨午夜请开启次日结束");return;}
            ensureDate(draft);draft.beforeDay=false;draft.time=first.format(DateTimeFormatter.ofPattern("HH:mm"));draft.endTime=last.format(DateTimeFormatter.ofPattern("HH:mm"));draft.endDayOffset=offset;draft.period="";update.run();dialog.dismiss();
        }catch(Exception e){end.setError("请输入有效时间，例如09:00—11:00");}}));dialog.show();
    }
    int previousEveningLead(Task t){LocalTime at=t.time.isEmpty()?LocalTime.MIDNIGHT:LocalTime.parse(t.time);LocalTime evening=LocalTime.parse(ranges().get("晚上").split("-")[0]);return 1440+at.getHour()*60+at.getMinute()-evening.getHour()*60-evening.getMinute();}
    void copyPeriodReminder(Task from,Task to){to.reminderEveningTime=from.reminderEveningTime;to.reminderPeriod=from.reminderPeriod;to.reminderPeriodTime=from.reminderPeriodTime;to.reminderPeriodDayOffset=from.reminderPeriodDayOffset;}

    /** Same inline reminder controls for both persisted tasks and uncommitted batch drafts. */
    final class ReminderFields {
        final Task task;final Runnable changed;final Switch enabled;final LinearLayout details;
        EditText custom;TextView actual;Spinner preset;int choice;boolean building;
        int[] leads;Task previous;String evening;int snoozeChoice=-1;
        ReminderFields(Task t,LinearLayout form,Runnable changed){
            task=t;this.changed=changed;if(t.id==0&&(t.date.isEmpty()||(t.time.isEmpty()&&!t.deadline)))t.remind=false;
            gap(form,14);enabled=new Switch(TaskEditor.this);enabled.setText("提醒我");enabled.setTextSize(15);enabled.setTextColor(INK);enabled.setContentDescription("开启或关闭事项提醒");form.addView(enabled);
            details=vertical();form.addView(details);enabled.setOnCheckedChangeListener((v,on)->{if(building)return;task.remind=on;changed.run();details.setVisibility(on?View.VISIBLE:View.GONE);showActual();});refresh();
        }
        void refresh(){
            String customText=custom!=null&&choice==7?custom.getText().toString():null;building=true;if(task.deadline&&!task.reminderPeriod.isEmpty())TimeParser.applyDeadlineReminder(task,ranges());enabled.setChecked(task.remind);details.removeAllViews();details.setVisibility(task.remind?View.VISIBLE:View.GONE);
            TextView explanation=text(task.date.isEmpty()?"选择日期和时间后才能提醒。":(task.deadline?"截止时间：":"事项开始：")+timeLabel(task),12,MUTED,false);details.addView(explanation);
            evening=ranges().get("晚上").split("-")[0];previous=task.copy();TimeParser.applyDeadlineReminder(previous,ranges());boolean period=task.deadline&&!task.time.isEmpty();
            String[] basics={task.deadline?"截止时":"开始时","提前5分钟","提前10分钟","提前30分钟","提前1小时","提前1天",(task.beforeDay&&task.deadline?"当天晚上 ":"前一天晚上 ")+evening,"自定义分钟数"};
            String[] labels=period?Arrays.copyOf(basics,9):basics;if(period)labels[8]="前一时段（"+previous.reminderPeriod+" "+previous.reminderPeriodTime+"）";
            leads=new int[]{0,5,10,30,60,1440,previousEveningLead(task),-1,previous.lead};choice=7;snoozeChoice=-1;
            if(period&&!task.reminderPeriod.isEmpty())choice=8;else if(!task.reminderEveningTime.isEmpty())choice=6;else for(int n=0;n<6;n++)if(leads[n]==task.lead)choice=n;
            if(task.reminderOverrideMillis>0){snoozeChoice=labels.length;labels=Arrays.copyOf(labels,labels.length+1);labels[snoozeChoice]="当前："+task.reminderLabel();choice=snoozeChoice;}
            preset=spinner(details,"提醒时间",labels,labels[choice]);preset.setContentDescription("选择提醒时间");LinearLayout customBox=vertical();custom=field(customBox,"提前分钟数",String.valueOf(task.lead),false);custom.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);if(choice==7&&customText!=null)custom.setText(customText);details.addView(customBox);customBox.setVisibility(choice==7?View.VISIBLE:View.GONE);
            actual=text("",12,BLUE,true);actual.setPadding(0,dp(10),0,0);details.addView(actual);
            preset.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onNothingSelected(AdapterView<?> p){}public void onItemSelected(AdapterView<?> p,View v,int n,long id){if(building||n==choice)return;choice=n;customBox.setVisibility(n==7?View.VISIBLE:View.GONE);if(choice!=snoozeChoice){task.clearSnooze();Task mode=new Task();if(choice==8)copyPeriodReminder(previous,mode);else if(choice==6)mode.reminderEveningTime=evening;copyPeriodReminder(mode,task);if(choice!=7)task.lead=leads[choice];else readCustom(false);}changed.run();showActual();}});
            custom.addTextChangedListener(watcher(value->{if(building||choice!=7)return;readCustom(false);changed.run();showActual();}));building=false;showActual();
        }
        boolean readCustom(boolean error){try{int value=Integer.parseInt(custom.getText().toString().trim());if(value<0||value>525600)throw new NumberFormatException();task.lead=value;return true;}catch(NumberFormatException invalid){if(error)custom.setError("请输入0—525600分钟");return false;}}
        void showActual(){if(actual==null)return;if(!task.remind){actual.setText("");return;}if(task.date.isEmpty()){actual.setText("请先选择日期");return;}if(choice==7&&!readCustom(false)){actual.setText("请输入有效提前分钟数");return;}actual.setText("提醒时间："+Instant.ofEpochMilli(task.triggerMillis()).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("M月d日 HH:mm"))+(task.triggerMillis()<=System.currentTimeMillis()?"（已过，请调整）":""));}
        boolean validate(){
            if(!task.remind){task.clearSnooze();task.lastNotified=0;return true;}
            if(choice==7&&!readCustom(true))return false;
            if(task.date.isEmpty()||(task.time.isEmpty()&&!task.deadline&&task.reminderEveningTime.isEmpty())){toast("请选择日期和时间，或关闭提醒");return false;}
            if(task.triggerMillis()<=System.currentTimeMillis()){toast("提醒时间已过，请调整时间或关闭提醒");return false;}
            if(task.reminderOverrideMillis==0)task.lastNotified=0;return true;
        }
    }

}
