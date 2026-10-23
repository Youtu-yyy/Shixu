package cn.shixu.app;

import android.animation.ValueAnimator;
import android.content.Context;
import android.view.*;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;

/** Keep the card's leading edge visible while revealing a separate compact action. */
final class SwipeActionLayout extends FrameLayout {
    private final View content,action;private final int actionWidth,tileWidth,tileHeight,slop;
    private float downX,downY,start,revealed;private boolean swiping,open;private ValueAnimator animator;
    SwipeActionLayout(Context context,View content,View action,int width){
        super(context);this.content=content;this.action=action;actionWidth=width;float density=getResources().getDisplayMetrics().density;
        tileWidth=width-Math.round(10*density);tileHeight=Math.round(72*density);slop=ViewConfiguration.get(context).getScaledTouchSlop();
        addView(action,new LayoutParams(tileWidth,tileHeight));addView(content,new LayoutParams(-1,-2));action.setEnabled(false);action.setVisibility(INVISIBLE);action.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
    }
    private void reveal(float value){revealed=value;float progress=value/actionWidth;action.setVisibility(value>0?VISIBLE:INVISIBLE);action.setAlpha(progress);action.setScaleX(.92f+.08f*progress);action.setScaleY(.92f+.08f*progress);requestLayout();}
    @Override protected void onMeasure(int widthSpec,int heightSpec){
        int width=MeasureSpec.getSize(widthSpec);content.measure(MeasureSpec.makeMeasureSpec(Math.max(1,width-Math.round(revealed)),MeasureSpec.EXACTLY),getChildMeasureSpec(heightSpec,0,LayoutParams.WRAP_CONTENT));
        action.measure(MeasureSpec.makeMeasureSpec(tileWidth,MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(tileHeight,MeasureSpec.EXACTLY));
        setMeasuredDimension(resolveSize(width,widthSpec),resolveSize(Math.max(content.getMeasuredHeight(),tileHeight),heightSpec));
    }
    @Override protected void onLayout(boolean changed,int left,int top,int right,int bottom){
        int height=bottom-top,width=right-left;int y=(height-content.getMeasuredHeight())/2;content.layout(0,y,content.getMeasuredWidth(),y+content.getMeasuredHeight());
        y=(height-tileHeight)/2;action.layout(width-tileWidth,y,width,y+tileHeight);
    }
    @Override public boolean onInterceptTouchEvent(MotionEvent e){
        if(e.getActionMasked()==MotionEvent.ACTION_DOWN){if(animator!=null)animator.cancel();downX=e.getX();downY=e.getY();start=revealed;swiping=false;return open&&e.getX()<getWidth()-actionWidth;}
        if(e.getActionMasked()==MotionEvent.ACTION_MOVE){float dx=e.getX()-downX,dy=e.getY()-downY;if(Math.abs(dx)>slop&&Math.abs(dx)>Math.abs(dy)*1.4f&&(dx<0||open)){swiping=true;getParent().requestDisallowInterceptTouchEvent(true);return true;}}
        return false;
    }
    @Override public boolean onTouchEvent(MotionEvent e){
        if(e.getActionMasked()==MotionEvent.ACTION_MOVE){swiping=true;reveal(Math.max(0,Math.min(actionWidth,start+downX-e.getX())));return true;}
        if(e.getActionMasked()==MotionEvent.ACTION_UP||e.getActionMasked()==MotionEvent.ACTION_CANCEL){
            open=e.getActionMasked()!=MotionEvent.ACTION_CANCEL&&swiping&&revealed>actionWidth*.4f;action.setEnabled(open);action.setImportantForAccessibility(open?View.IMPORTANT_FOR_ACCESSIBILITY_AUTO:View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
            animator=ValueAnimator.ofFloat(revealed,open?actionWidth:0);animator.setDuration(200);animator.setInterpolator(new DecelerateInterpolator());animator.addUpdateListener(a->reveal((float)a.getAnimatedValue()));animator.start();getParent().requestDisallowInterceptTouchEvent(false);return true;
        }
        return true;
    }
    @Override protected void onDetachedFromWindow(){if(animator!=null)animator.cancel();super.onDetachedFromWindow();}
}
