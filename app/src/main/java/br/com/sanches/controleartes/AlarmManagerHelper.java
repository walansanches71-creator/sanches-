package br.com.sanches.controleartes;

import android.app.*;
import android.content.*;
import android.os.Build;
import android.database.Cursor;

public class AlarmManagerHelper {
    static void scheduleAll(Context context, LocalDB db){
        AlarmManager am=(AlarmManager)context.getSystemService(Context.ALARM_SERVICE);
        if(Build.VERSION.SDK_INT>=31 && !am.canScheduleExactAlarms()) return;
        long now=System.currentTimeMillis();
        Cursor c=db.arts();
        try{
            while(c.moveToNext()){
                long id=c.getLong(c.getColumnIndexOrThrow("id"));
                long due=c.getLong(c.getColumnIndexOrThrow("due_at"));
                String status=c.getString(c.getColumnIndexOrThrow("status"));
                if(due<=now||"Pago".equals(status)||"Arte entregue".equals(status)||"Pronta".equals(status))continue;
                Intent i=new Intent(context,DeadlineReceiver.class);
                i.putExtra("art_id",id);
                PendingIntent pi=PendingIntent.getBroadcast(context,(int)(id%1000000),i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
                try{
                    if(Build.VERSION.SDK_INT>=23)am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,due,pi);
                    else am.setExact(AlarmManager.RTC_WAKEUP,due,pi);
                }catch(Exception ignored){}
            }
        }finally{c.close();}
    }
}
