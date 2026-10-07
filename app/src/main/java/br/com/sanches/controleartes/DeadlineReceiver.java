package br.com.sanches.controleartes;

import android.content.*;

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
        OverdueNotifier.show(context,id,a.company,a.service,a.description,a.photo);
        AlertVoiceService.start(context,"Atenção! A entrega da arte da empresa "+a.company+" está atrasada.");
    }
}