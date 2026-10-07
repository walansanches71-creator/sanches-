package br.com.sanches.controleartes;

import android.content.*;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.speech.tts.TextToSpeech;
import java.util.Locale;

public class DeadlineReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        long id=intent.getLongExtra("art_id",0);
        if(id<=0)return;
        LocalDB db=new LocalDB(context);
        LocalDB.ArtSnapshot a=db.getArtSnapshot(id);
        if(a==null || a.dueAt<=0 || a.dueAt>System.currentTimeMillis() || "Pago".equals(a.status) || "Arte entregue".equals(a.status))return;
        if(a.alertedAt>0)return;
        db.updateStatus(id,"Arte atrasada");
        db.markAlerted(id,System.currentTimeMillis());
        try{
            Ringtone r=RingtoneManager.getRingtone(context,RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM));
            if(r!=null)r.play();
        }catch(Exception ignored){}
        try{
            TextToSpeech tts=new TextToSpeech(context,status->{
                if(status==TextToSpeech.SUCCESS){
                    tts.setLanguage(new Locale("pt","BR"));
                    tts.speak("Atenção! A demanda da empresa "+a.company+" está atrasada.",TextToSpeech.QUEUE_FLUSH,null,"demanda_atrasada");
                }
            });
        }catch(Exception ignored){}
    }
}