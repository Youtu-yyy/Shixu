package cn.shixu.app;

import android.app.Activity;
import android.os.Bundle;
import android.content.*;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.widget.*;
import java.time.LocalDateTime;
import java.util.*;

/** Image flipbook with explicit navigation for users who do not swipe. */
public class TutorialActivity extends Activity {
    public static final String TRY_SAMPLE="cn.shixu.app.TUTORIAL_TRY_SAMPLE";
    int page;ImageView image;TextView counter,previous,next;Bitmap bitmap;float downX,downY;
    int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    TextView text(String value,int size,int color,boolean bold){TextView view=new TextView(this);view.setText(value);view.setTextSize(size);view.setTextColor(color);view.setGravity(Gravity.CENTER_VERTICAL);if(bold)view.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return view;}
    TextView button(String value,boolean primary){TextView view=text(value,14,primary?0xffffffff:0xff4561ee,true);view.setGravity(Gravity.CENTER);view.setPadding(dp(10),0,dp(10),0);GradientDrawable background=new GradientDrawable();background.setColor(primary?0xff4561ee:0xffeef1ff);background.setCornerRadius(dp(14));view.setBackground(background);view.setMinHeight(dp(44));return view;}
    @Override public void onCreate(Bundle state){super.onCreate(state);page=state==null?0:Math.max(0,Math.min(4,state.getInt("page")));LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(0xfff6f7fb);setContentView(root);
        root.setOnApplyWindowInsetsListener((v,insets)->{if(android.os.Build.VERSION.SDK_INT>=30){android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars());v.setPadding(dp(12),bars.top,dp(12),bars.bottom);}else v.setPadding(dp(12),insets.getSystemWindowInsetTop(),dp(12),insets.getSystemWindowInsetBottom());return insets;});
        LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);header.addView(text("使用教程",21,0xff20283f,true),new LinearLayout.LayoutParams(0,dp(52),1));TextView close=button("关闭",false);close.setContentDescription("关闭使用教程");close.setOnClickListener(v->finish());header.addView(close);root.addView(header);
        image=new ImageView(this);image.setScaleType(ImageView.ScaleType.FIT_CENTER);image.setAdjustViewBounds(true);image.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);image.setFocusable(true);root.addView(image,new LinearLayout.LayoutParams(-1,0,1));
        image.setOnTouchListener((v,event)->{if(event.getActionMasked()==MotionEvent.ACTION_DOWN){downX=event.getX();downY=event.getY();return true;}if(event.getActionMasked()==MotionEvent.ACTION_UP){float dx=event.getX()-downX,dy=event.getY()-downY;if(Math.abs(dx)>dp(48)&&Math.abs(dx)>Math.abs(dy)*1.3f)go(dx<0?1:-1);else v.performClick();return true;}return true;});
        counter=text("",12,0xff828aa0,false);counter.setGravity(Gravity.CENTER);counter.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);root.addView(counter,new LinearLayout.LayoutParams(-1,dp(30)));
        LinearLayout navigation=new LinearLayout(this);previous=button("上一页",false);next=button("下一页",false);previous.setContentDescription("教程上一页");next.setContentDescription("教程下一页");navigation.addView(previous,new LinearLayout.LayoutParams(0,dp(44),1));TextView copy=button("复制示例",false);LinearLayout.LayoutParams copyParams=new LinearLayout.LayoutParams(0,dp(44),1);copyParams.setMargins(dp(8),0,dp(8),0);navigation.addView(copy,copyParams);navigation.addView(next,new LinearLayout.LayoutParams(0,dp(44),1));root.addView(navigation);previous.setOnClickListener(v->go(-1));next.setOnClickListener(v->go(1));copy.setOnClickListener(v->{getSystemService(ClipboardManager.class).setPrimaryClip(ClipData.newPlainText("拾序教程示例",TutorialContent.NOTICE));Toast.makeText(this,"示例通知已复制",Toast.LENGTH_SHORT).show();});
        TextView trySample=button("用示例试一次",true);trySample.setContentDescription("试用教程示例");LinearLayout.LayoutParams tryParams=new LinearLayout.LayoutParams(-1,dp(48));tryParams.setMargins(0,dp(8),0,dp(10));root.addView(trySample,tryParams);trySample.setOnClickListener(v->{startActivity(new Intent(this,MainActivity.class).setAction(TRY_SAMPLE).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP));finish();});showPage();
    }
    void go(int delta){int target=Math.max(0,Math.min(TutorialContent.TITLES.length-1,page+delta));if(target!=page){page=target;showPage();}}
    void showPage(){LinkedHashMap<String,String> ranges=new LinkedHashMap<>();android.content.SharedPreferences prefs=getSharedPreferences("settings",MODE_PRIVATE);for(Map.Entry<String,String> entry:TimeParser.DEFAULTS.entrySet())ranges.put(entry.getKey(),prefs.getString("range_"+entry.getKey(),entry.getValue()));bitmap=TutorialSlides.render(page,LocalDateTime.now(),ranges);image.setImageBitmap(bitmap);image.setContentDescription("教程第"+(page+1)+"页："+TutorialContent.TITLES[page]+"。"+TutorialContent.CAPTIONS[page]);counter.setText("第 "+(page+1)+" / "+TutorialContent.TITLES.length+" 页  ·  "+TutorialContent.TITLES[page]);previous.setEnabled(page>0);previous.setAlpha(page>0?1:.35f);next.setEnabled(page<TutorialContent.TITLES.length-1);next.setAlpha(next.isEnabled()?1:.35f);}
    @Override protected void onSaveInstanceState(Bundle state){super.onSaveInstanceState(state);state.putInt("page",page);}
    @Override protected void onDestroy(){if(image!=null)image.setImageDrawable(null);bitmap=null;super.onDestroy();}
}
