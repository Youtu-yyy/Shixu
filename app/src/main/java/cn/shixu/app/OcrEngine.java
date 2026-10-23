package cn.shixu.app;

import android.content.Context;
import android.graphics.*;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.*;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.*;
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Bundled on-device OCR. Only reads the image explicitly selected by the user. */
public final class OcrEngine {
    public interface Listener {void preview(Bitmap bitmap);void text(String value);void error(String message);}
    public static final class Job {final AtomicBoolean cancelled=new AtomicBoolean();public void cancel(){cancelled.set(true);}}
    static final ExecutorService WORK=Executors.newSingleThreadExecutor();
    static final Handler MAIN=new Handler(Looper.getMainLooper());
    static InputStream stream(Context c,Uri uri)throws IOException{InputStream in=c.getContentResolver().openInputStream(uri);if(in==null)throw new IOException("Cannot open selected image");return in;}
    static Bitmap decode(Context c,Uri uri)throws IOException{
        BitmapFactory.Options options=new BitmapFactory.Options();options.inJustDecodeBounds=true;try(InputStream in=stream(c,uri)){BitmapFactory.decodeStream(in,null,options);}
        if(options.outWidth<=0||options.outHeight<=0)throw new IOException("Unsupported image");
        int sample=1;while(Math.max(options.outWidth,options.outHeight)/sample>4096||(long)(options.outWidth/sample)*(options.outHeight/sample)>6_000_000L)sample*=2;
        options.inJustDecodeBounds=false;options.inSampleSize=sample;options.inPreferredConfig=Bitmap.Config.ARGB_8888;Bitmap bitmap;try(InputStream in=stream(c,uri)){bitmap=BitmapFactory.decodeStream(in,null,options);}if(bitmap==null)throw new IOException("Cannot decode image");
        int orientation=ExifInterface.ORIENTATION_NORMAL;try(InputStream in=stream(c,uri)){orientation=new ExifInterface(in).getAttributeInt(ExifInterface.TAG_ORIENTATION,ExifInterface.ORIENTATION_NORMAL);}catch(IOException ignored){}
        Matrix rotation=new Matrix();switch(orientation){case ExifInterface.ORIENTATION_ROTATE_90:rotation.postRotate(90);break;case ExifInterface.ORIENTATION_ROTATE_180:rotation.postRotate(180);break;case ExifInterface.ORIENTATION_ROTATE_270:rotation.postRotate(270);break;case ExifInterface.ORIENTATION_FLIP_HORIZONTAL:rotation.postScale(-1,1);break;case ExifInterface.ORIENTATION_FLIP_VERTICAL:rotation.postScale(1,-1);break;case ExifInterface.ORIENTATION_TRANSPOSE:rotation.postRotate(90);rotation.postScale(-1,1);break;case ExifInterface.ORIENTATION_TRANSVERSE:rotation.postRotate(270);rotation.postScale(-1,1);break;}
        if(!rotation.isIdentity()){Bitmap adjusted=Bitmap.createBitmap(bitmap,0,0,bitmap.getWidth(),bitmap.getHeight(),rotation,true);if(adjusted!=bitmap){bitmap.recycle();bitmap=adjusted;}}
        return bitmap;
    }
    static String readable(Text result){
        List<OcrTable.Cell> lines=new ArrayList<>(),elements=new ArrayList<>();for(Text.TextBlock block:result.getTextBlocks())for(Text.Line line:block.getLines()){Rect r=line.getBoundingBox();if(r!=null)lines.add(new OcrTable.Cell(line.getText(),r.left,r.top,r.width(),r.height()));for(Text.Element e:line.getElements()){Rect b=e.getBoundingBox();if(b!=null)elements.add(new OcrTable.Cell(e.getText(),b.left,b.top,b.width(),b.height()));}}
        String table=OcrTable.reconstruct(lines,elements);
        List<Text.TextBlock> blocks=new ArrayList<>(result.getTextBlocks());blocks.sort(Comparator.comparingInt((Text.TextBlock b)->b.getBoundingBox()==null?0:b.getBoundingBox().top).thenComparingInt(b->b.getBoundingBox()==null?0:b.getBoundingBox().left));StringBuilder out=new StringBuilder();
        for(Text.TextBlock block:blocks){StringBuilder paragraph=new StringBuilder();for(Text.Line line:block.getLines()){String value=line.getText().trim();if(value.isEmpty())continue;if(paragraph.length()>0){char last=paragraph.charAt(paragraph.length()-1),first=value.charAt(0);if("。！？!?；;".indexOf(last)>=0||value.matches("^(?:时间|地点|主题|备注|注意事项)[：:].*"))paragraph.append('\n');else if(last<128&&first<128&&Character.isLetterOrDigit(last)&&Character.isLetterOrDigit(first))paragraph.append(' ');}paragraph.append(value);}if(paragraph.length()>0){if(out.length()>0)out.append('\n');out.append(paragraph);}}
        String raw=out.toString().trim();return table.isEmpty()?raw:table+"\n\n识别原文（供核对）：\n"+raw;
    }
    static void fail(Job job,Listener listener,String message){MAIN.post(()->{if(!job.cancelled.get())listener.error(message);});}
    public static Job recognize(Context context,Uri uri,Listener listener){
        return recognize(context,uri,false,listener);
    }
    public static Job recognize(Context context,Uri uri,boolean enhanced,Listener listener){
        Job job=new Job();Context app=context.getApplicationContext();WORK.execute(()->{
            Bitmap bitmap=null;TextRecognizer recognizer=null;
            try{
                if(job.cancelled.get())return;bitmap=decode(app,uri);if(job.cancelled.get()){bitmap.recycle();return;}
                float ratio=Math.min(1f,320f/Math.max(bitmap.getWidth(),bitmap.getHeight()));Bitmap thumb=Bitmap.createScaledBitmap(bitmap,Math.max(1,Math.round(bitmap.getWidth()*ratio)),Math.max(1,Math.round(bitmap.getHeight()*ratio)),true);if(thumb==bitmap)thumb=bitmap.copy(Bitmap.Config.ARGB_8888,false);Bitmap preview=thumb;MAIN.post(()->{if(!job.cancelled.get())listener.preview(preview);});
                if(enhanced){Bitmap adjusted=OcrEnhancement.apply(bitmap);bitmap.recycle();bitmap=adjusted;}if(job.cancelled.get()){bitmap.recycle();return;}
                recognizer=TextRecognition.getClient(new ChineseTextRecognizerOptions.Builder().build());Bitmap input=bitmap;TextRecognizer client=recognizer;
                recognizer.process(InputImage.fromBitmap(input,0)).addOnCompleteListener(WORK,task->{try{if(job.cancelled.get())return;if(task.isSuccessful()){String value=readable(task.getResult());if(value.isEmpty())fail(job,listener,"没有识别到文字，请选择清晰的通知截图，或先裁剪到正文。");else if(value.length()>20000)fail(job,listener,"文字过多，请把图片裁剪为一段通知后重试。");else MAIN.post(()->{if(!job.cancelled.get())listener.text(value);});}else fail(job,listener,"图片文字识别失败，请换一张清晰图片后重试。");}finally{client.close();input.recycle();}});
            }catch(IOException|SecurityException e){if(bitmap!=null&&!bitmap.isRecycled())bitmap.recycle();if(recognizer!=null)recognizer.close();fail(job,listener,"无法读取这张图片，请重新选择 JPG、PNG 等常见格式的图片。");}
            catch(RuntimeException|OutOfMemoryError e){if(bitmap!=null&&!bitmap.isRecycled())bitmap.recycle();if(recognizer!=null)recognizer.close();fail(job,listener,"暂时无法识别这张图片，请裁剪或缩小图片后重试。");}
        });return job;
    }
    private OcrEngine(){}
}
