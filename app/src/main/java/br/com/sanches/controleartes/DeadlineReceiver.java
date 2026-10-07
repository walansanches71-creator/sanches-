package br.com.sanches.controleartes;

import android.content.*;
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
            final TextToSpeech[] holder=new TextToSpeech[1];
            holder[0]=new TextToSpeech(context,status->{
                if(status==TextToSpeech.SUCCESS){
                    holder[0].setLanguage(new Locale("pt","BR"));
                    holder[0].speak("Atenção! A demanda da empresa "+a.company+" está atrasada.",TextToSpeech.QUEUE_FLUSH,null,"demanda_atrasada");
                }
            });
        }catch(Exception ignored){}
    }
}