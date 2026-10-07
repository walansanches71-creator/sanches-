package br.com.sanches.controleartes;

import android.app.*;
import android.content.*;
import android.os.*;
import android.speech.tts.*;
import java.util.*;

public class AlertVoiceService extends Service {
    static void start(Context context,String message){
        android.content.SharedPreferences p=context.getSharedPreferences("voz_alerta",0);
        Intent i=new Intent(context,AlertVoiceService.class);
        i.putExtra("message",message);
        i.putExtra("repeat",p.getInt("repeat",3));
        i.putExtra("volume",p.getFloat("volume",1f));
        i.putExtra("voice_name",p.getString("voice_name",""));
        try{ if(Build.VERSION.SDK_INT>=26)context.startForegroundService(i); else context.startService(i); }
        catch(Exception ignored){ }
    }
    static final String ACTION_STOP="br.com.sanches.controleartes.STOP_VOICE";
    static final String CHANNEL="voz_atraso_v1";
    TextToSpeech tts;

    @Override public void onCreate(){
        super.onCreate();
        if(Build.VERSION.SDK_INT>=26){
            NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
            NotificationChannel ch=new NotificationChannel(CHANNEL,"Aviso de voz",NotificationManager.IMPORTANCE_LOW);
            ch.setSound(null,null); ch.enableVibration(false);
            nm.createNotificationChannel(ch);
        }
    }

    @Override public int onStartCommand(Intent intent,int flags,int startId){
        if(intent!=null && ACTION_STOP.equals(intent.getAction())){stopSelf();return START_NOT_STICKY;}
        String msg=intent==null?"Atenção! Há uma entrega atrasada.":intent.getStringExtra("message");
        if(msg==null||msg.isEmpty())msg="Atenção! Há uma entrega atrasada.";
        int repeat=intent==null?3:Math.max(1,Math.min(3,intent.getIntExtra("repeat",3)));
        float volume=intent==null?1f:Math.max(.1f,Math.min(1f,intent.getFloatExtra("volume",1f)));
        String voiceName=intent==null?"":intent.getStringExtra("voice_name");
        startForeground(7711,notification(msg));
        final String finalMsg=msg; final int finalRepeat=repeat; final float finalVolume=volume; final String finalVoice=voiceName;
        try{
            tts=new TextToSpeech(this,status->{
                if(status!=TextToSpeech.SUCCESS){stopSelf();return;}
                try{
                    tts.setLanguage(new Locale("pt","BR"));
                    if(Build.VERSION.SDK_INT>=21 && finalVoice!=null && !finalVoice.isEmpty()){
                        for(Voice v:tts.getVoices()) if(finalVoice.equals(v.getName())){tts.setVoice(v);break;}
                    }
                    Bundle params=new Bundle();
                    if(Build.VERSION.SDK_INT>=21)params.putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME,finalVolume);
                    tts.setOnUtteranceProgressListener(new UtteranceProgressListener(){
                        @Override public void onStart(String utteranceId){}
                        @Override public void onDone(String utteranceId){ if(utteranceId.equals("atraso_voz_"+(finalRepeat-1)) || finalRepeat==1) stopSelf(); }
                        @Override public void onError(String utteranceId){stopSelf();}
                    });
                    tts.speak(finalMsg,TextToSpeech.QUEUE_FLUSH,params,"atraso_voz");
                    for(int i=1;i<finalRepeat;i++)tts.speak(finalMsg,TextToSpeech.QUEUE_ADD,params,"atraso_voz_"+i);
                    new Handler(Looper.getMainLooper()).postDelayed(()->{if(tts!=null && !tts.isSpeaking())stopSelf();},45000);
                }catch(Exception e){stopSelf();}
            });
        }catch(Exception e){stopSelf();}
        return START_NOT_STICKY;
    }

    Notification notification(String msg){
        Intent stop=new Intent(this,AlertVoiceService.class);stop.setAction(ACTION_STOP);
        PendingIntent pi=PendingIntent.getService(this,7712,stop,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b=Build.VERSION.SDK_INT>=26?new Notification.Builder(this,CHANNEL):new Notification.Builder(this);
        return b.setSmallIcon(getApplicationInfo().icon)
                .setContentTitle("🔊 Aviso de atraso")
                .setContentText(msg)
                .setCategory(Notification.CATEGORY_REMINDER)
                .setOngoing(true)
                .addAction(android.R.drawable.ic_media_pause,"PARAR AVISO",pi)
                .build();
    }

    @Override public void onDestroy(){try{if(tts!=null){tts.stop();tts.shutdown();}}catch(Exception ignored){}super.onDestroy();}
    @Override public android.os.IBinder onBind(Intent intent){return null;}
}
