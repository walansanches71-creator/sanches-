package br.com.sanches.controleartes;

import android.app.*;
import android.content.*;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import java.io.InputStream;

public class OverdueNotifier {
    static final String CHANNEL_ID="atrasos_silenciosos_v2";

    static void ensureChannel(Context context){
        if(Build.VERSION.SDK_INT>=26){
            NotificationManager nm=(NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);
            NotificationChannel ch=new NotificationChannel(CHANNEL_ID,"Prazos atrasados",NotificationManager.IMPORTANCE_DEFAULT);
            ch.setDescription("Avisos de demandas atrasadas. Sem som e sem vibração.");
            ch.setSound(null,null);
            ch.enableVibration(false);
            ch.enableLights(false);
            nm.createNotificationChannel(ch);
        }
    }

    static void show(Context context,long id,String company,String service,String description,String photo){
        try{
            if(Build.VERSION.SDK_INT>=33 && context.checkSelfPermission("android.permission.POST_NOTIFICATIONS")!=android.content.pm.PackageManager.PERMISSION_GRANTED)return;
            ensureChannel(context);
            String item=description==null||description.isEmpty()?(service==null||service.isEmpty()?"Arte":service):description;
            String title="🚨 Demanda atrasada";
            String body=company+" • "+item;
            Bitmap picture=null;
            if(photo!=null&&!photo.isEmpty()){
                Uri uri=firstPhoto(photo);
                if(uri!=null){
                    InputStream in=null;
                    try{in=context.getContentResolver().openInputStream(uri);picture=BitmapFactory.decodeStream(in);}
                    finally{if(in!=null)in.close();}
                }
            }
            Notification.Builder b=Build.VERSION.SDK_INT>=26
                    ?new Notification.Builder(context,CHANNEL_ID)
                    :new Notification.Builder(context);
            b.setSmallIcon(context.getApplicationInfo().icon)
             .setContentTitle(title)
             .setContentText(body)
             .setStyle(picture!=null
                     ?new Notification.BigPictureStyle().bigPicture(picture).setBigContentTitle(title).setSummaryText(body)
                     :new Notification.BigTextStyle().bigText(body))
             .setAutoCancel(true)
             .setCategory(Notification.CATEGORY_REMINDER)
             .setOnlyAlertOnce(true)
             .setWhen(System.currentTimeMillis());
            if(Build.VERSION.SDK_INT<26){
                b.setPriority(Notification.PRIORITY_DEFAULT);
                b.setSound(null);
                b.setVibrate(null);
            }
            NotificationManager nm=(NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);
            nm.notify((int)(id%1000000),b.build());
        }catch(Exception ignored){}
    }

    static Uri firstPhoto(String value){
        try{
            if(value.trim().startsWith("[")){
                org.json.JSONArray a=new org.json.JSONArray(value);
                return a.length()>0?Uri.parse(a.getString(0)):null;
            }
            return Uri.parse(value);
        }catch(Exception e){return null;}
    }
}