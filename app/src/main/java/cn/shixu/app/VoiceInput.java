package cn.shixu.app;
import android.Manifest;
import android.app.*;
import android.os.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.speech.*;
import android.widget.*;
import java.util.*;

/** Uses Android's on-device recognizer for strict offline mode; never stores audio. */
public class VoiceInput {
    static final int REQUEST_MIC=42,REQUEST_SYSTEM=43;
    MainActivity owner;SpeechRecognizer recognizer;AlertDialog dialog;boolean disposed;
    public VoiceInput(MainActivity owner){this.owner=owner;}
    public void start(){
        destroy();disposed=false;
        boolean local=Build.VERSION.SDK_INT>=31&&SpeechRecognizer.isOnDeviceRecognitionAvailable(owner);
        if(!local){
            if(!owner.prefs.getBoolean("voice_network",false)){
                new AlertDialog.Builder(owner).setTitle("本机离线语音不可用").setMessage("当前系统未提供离线语音识别。可以继续打字，或到拾序设置中允许调用系统语音服务；系统服务可能联网识别。").setPositiveButton("继续打字",null).setNeutralButton("打开设置",(d,n)->owner.settings()).show();return;
            }
            systemRecognition();return;
        }
        if(owner.checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){owner.requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},REQUEST_MIC);return;}
        TextView status=new TextView(owner);status.setText("正在准备麦克风…");status.setTextSize(17);status.setPadding(owner.dp(24),owner.dp(24),owner.dp(24),owner.dp(24));
        dialog=new AlertDialog.Builder(owner).setTitle("说出你的安排").setView(status).setNegativeButton("取消",(d,n)->destroy()).setPositiveButton("说完了",null).create();
        dialog.setOnShowListener(d->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{if(recognizer!=null){status.setText("正在识别…");recognizer.stopListening();}}));dialog.setOnCancelListener(d->destroy());dialog.show();
        try{
            recognizer=SpeechRecognizer.createOnDeviceSpeechRecognizer(owner);
            recognizer.setRecognitionListener(new RecognitionListener(){
                public void onReadyForSpeech(Bundle b){status.setText("正在聆听…\n例如：周六晚上整理衣柜");}
                public void onBeginningOfSpeech(){status.setText("正在聆听…");}
                public void onRmsChanged(float rms){}public void onBufferReceived(byte[] data){}public void onEndOfSpeech(){status.setText("正在识别…");}
                public void onError(int error){if(disposed)return;boolean language=error==SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE||error==SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED;String reason=error==SpeechRecognizer.ERROR_NO_MATCH||error==SpeechRecognizer.ERROR_SPEECH_TIMEOUT?"没有听清，请再试一次":language?"本机尚未安装可用的中文离线语音模型":"语音识别暂不可用（"+error+"），可以重试或使用键盘输入";destroy();if(language&&owner.prefs.getBoolean("voice_network",false)){owner.toast("离线中文模型不可用，改用系统语音服务");systemRecognition();}else owner.toast(reason);}
                public void onResults(Bundle b){ArrayList<String> results=b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);destroy();if(results!=null&&!results.isEmpty())owner.acceptVoice(results.get(0));}
                public void onPartialResults(Bundle b){ArrayList<String> results=b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);if(results!=null&&!results.isEmpty())status.setText(results.get(0));}
                public void onEvent(int type,Bundle b){}
            });recognizer.startListening(request());
        }catch(Exception e){destroy();owner.toast("无法启动离线识别，请检查系统语音服务");}
    }
    Intent request(){return new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM).putExtra(RecognizerIntent.EXTRA_LANGUAGE,"zh-CN").putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS,true).putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE,true).putExtra(RecognizerIntent.EXTRA_MAX_RESULTS,1);}
    void systemRecognition(){Intent intent=request().putExtra(RecognizerIntent.EXTRA_PROMPT,"说出待办和时间，例如：明天中午交材料");try{owner.startActivityForResult(intent,REQUEST_SYSTEM);}catch(ActivityNotFoundException e){new AlertDialog.Builder(owner).setTitle("未找到系统语音服务").setMessage("可以使用键盘上的语音输入，或在手机系统中安装、启用语音服务后重试。").setPositiveButton("知道了",null).show();}}
    public void destroy(){disposed=true;if(recognizer!=null){recognizer.cancel();recognizer.destroy();recognizer=null;}if(dialog!=null){dialog.dismiss();dialog=null;}}
}
