package br.com.sanches.controleartes;

import android.app.*;
import android.os.*;
import android.content.*;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.pdf.PdfDocument;
import android.graphics.Paint;
import android.graphics.Canvas;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.RectF;
import android.os.Environment;
import android.net.Uri;
import android.database.Cursor;
import android.text.InputType;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.speech.tts.TextToSpeech;
import android.view.*;
import android.widget.*;
import java.util.*;
import java.io.*;
import org.json.*;

public class MainActivity extends Activity {
    LinearLayout root,list;
    TextView total,receber,pago,atrasado,custos,lucro,empty,todaySummary,todayNext;
    LocalDB db;
    Uri pendingImage;
    ArrayList<Uri> pendingImages=new ArrayList<>();
    final int BG=Color.rgb(8,12,17), SURFACE=Color.rgb(17,23,31), SURFACE2=Color.rgb(22,29,38);
    int GREEN=Color.rgb(37,211,102); final int WHITE=Color.rgb(245,247,250), MUTED=Color.rgb(151,163,176);
    final int RED=Color.rgb(255,88,88), YELLOW=Color.rgb(255,190,70), BLUE=Color.rgb(90,170,255);

    static class Art { long id; String company,phone,service,desc,status,photo,quoteNumber; double price,cost,paidAmount; long receivedAt,dueAt,alertedAt; }
    static class Company { long id; String name,phone; }
    static class Service { long id; String name,desc; double cost,price; }

    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        getWindow().setStatusBarColor(BG); getWindow().setNavigationBarColor(BG);
        db=new LocalDB(this);
        loadThemeColor();
        migrateLegacy();
        syncDataFolder();
        build();
        OverdueNotifier.ensureChannel(this);
        requestNotificationPermission();
    }

    int dp(int n){return (int)(n*getResources().getDisplayMetrics().density+0.5f);}
    TextView text(String s,float size,int color){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);return t;}
    GradientDrawable bg(int color,float radius){GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(dp((int)radius));return g;}
    GradientDrawable strokeBg(int color,int stroke,float radius){GradientDrawable g=bg(color,radius);g.setStroke(dp(1),stroke);return g;}
    Button action(String s){Button b=new Button(this);b.setText(s);b.setTextColor(WHITE);b.setTextSize(12);b.setAllCaps(false);b.setPadding(dp(8),0,dp(8),0);b.setMinHeight(dp(42));b.setMinWidth(0);b.setBackground(bg(SURFACE2,12));return b;}
    LinearLayout.LayoutParams lp(int top){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(48));p.setMargins(0,dp(top),0,0);return p;}
    EditText input(String hint){EditText e=new EditText(this);e.setHint(hint);e.setHintTextColor(MUTED);e.setTextColor(WHITE);e.setTextSize(14);e.setSingleLine(true);e.setPadding(dp(14),0,dp(14),0);e.setBackground(strokeBg(SURFACE2,Color.rgb(47,59,73),13));return e;}
    double num(String s){try{return Double.parseDouble(s.replace(".","").replace(",","."));}catch(Exception e){return 0;}}
    String money(double x){return String.format(Locale.US,"%.2f",x).replace(".",",");}

    void build(){
        root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(16),dp(12),dp(16),0);root.setBackgroundColor(BG);
        LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout titles=new LinearLayout(this);titles.setOrientation(LinearLayout.VERTICAL);
        TextView brand=text("CONTROLE DE ARTES",22,WHITE);brand.setTypeface(null,1);
        titles.addView(brand);titles.addView(text("Seu financeiro de artes, agora completo",12,MUTED));
        top.addView(titles,new LinearLayout.LayoutParams(0,-2,1));
        Button menu=action("☰");menu.setTextSize(22);menu.setBackground(bg(SURFACE,16));menu.setOnClickListener(v->menu());
        top.addView(menu,new LinearLayout.LayoutParams(dp(48),dp(48)));
        root.addView(top,new LinearLayout.LayoutParams(-1,dp(58)));

        LinearLayout dash=new LinearLayout(this);dash.setOrientation(LinearLayout.VERTICAL);dash.setPadding(0,dp(10),0,dp(6));
        LinearLayout r1=new LinearLayout(this),r2=new LinearLayout(this),r3=new LinearLayout(this);r1.setOrientation(LinearLayout.HORIZONTAL);r2.setOrientation(LinearLayout.HORIZONTAL);r3.setOrientation(LinearLayout.HORIZONTAL);
        total=metric(r1,"TOTAL","R$ 0,00",WHITE);receber=metric(r1,"A RECEBER","R$ 0,00",YELLOW);
        pago=metric(r2,"RECEBIDO","R$ 0,00",GREEN);atrasado=metric(r2,"ATRASADO","R$ 0,00",RED);
        custos=metric(r3,"CUSTOS","R$ 0,00",BLUE);lucro=metric(r3,"LUCRO","R$ 0,00",GREEN);
        dash.addView(r1,new LinearLayout.LayoutParams(-1,dp(76)));dash.addView(r2,new LinearLayout.LayoutParams(-1,dp(76)));dash.addView(r3,new LinearLayout.LayoutParams(-1,dp(76)));
        root.addView(dash);
        root.addView(todayPanel(),new LinearLayout.LayoutParams(-1,dp(126)));

        Button add=action("＋  NOVA ARTE");add.setTextSize(15);add.setTypeface(null,1);add.setTextColor(BG);add.setBackground(bg(GREEN,16));add.setOnClickListener(v->form(null,null,null));
        root.addView(add,new LinearLayout.LayoutParams(-1,dp(52)));
        TextView section=text("MINHAS ARTES",13,MUTED);section.setTypeface(null,1);section.setPadding(dp(3),dp(16),0,dp(8));root.addView(section,new LinearLayout.LayoutParams(-1,dp(42)));

        ScrollView sv=new ScrollView(this);sv.setFillViewport(true);list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);list.setPadding(0,0,0,dp(20));sv.addView(list);root.addView(sv,new LinearLayout.LayoutParams(-1,0,1));
        setContentView(root);refresh();
    }

    TextView metric(LinearLayout row,String label,String value,int color){
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(14),dp(8),dp(8),dp(6));box.setBackground(bg(SURFACE,16));
        TextView a=text(label,9,MUTED);a.setTypeface(null,1);TextView b=text(value,16,color);b.setTypeface(null,1);box.addView(a);box.addView(b);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-1,1);p.setMargins(0,0,dp(6),0);row.addView(box,p);return b;
    }

    void refresh(){
        checkDeadlines();
        list.removeAllViews();double rec=0,pag=0,atr=0,cst=0,totalBilled=0;
        Cursor c=db.arts();
        try{
            while(c.moveToNext()){
                Art a=art(c);double n=a.price;double remaining=Math.max(0,n-a.paidAmount);cst+=a.cost;totalBilled+=n;
                rec+=remaining;
                pag+=a.paidAmount;
                if(a.status.contains("atras"))atr+=remaining;
                card(a);
            }
        }finally{c.close();}
        total.setText("R$ "+money(totalBilled));receber.setText("R$ "+money(rec));pago.setText("R$ "+money(pag));atrasado.setText("R$ "+money(atr));custos.setText("R$ "+money(cst));lucro.setText("R$ "+money(totalBilled-cst));
        updateTodayPanel();
        if(list.getChildCount()==0){empty=text("Nenhuma arte cadastrada\n\nToque em “＋ NOVA ARTE” para começar.",15,MUTED);empty.setGravity(Gravity.CENTER);empty.setPadding(0,dp(40),0,dp(40));list.addView(empty);}
    }

    Art art(Cursor c){
        Art a=new Art();a.id=c.getLong(c.getColumnIndexOrThrow("id"));a.company=c.getString(c.getColumnIndexOrThrow("company"));a.phone=c.getString(c.getColumnIndexOrThrow("phone"));
        a.service=c.getString(c.getColumnIndexOrThrow("service"));a.desc=c.getString(c.getColumnIndexOrThrow("description"));a.price=c.getDouble(c.getColumnIndexOrThrow("price"));
        a.cost=c.getDouble(c.getColumnIndexOrThrow("cost"));a.paidAmount=c.getDouble(c.getColumnIndexOrThrow("paid_amount"));a.quoteNumber=c.getString(c.getColumnIndexOrThrow("quote_number"));a.status=c.getString(c.getColumnIndexOrThrow("status"));a.photo=c.getString(c.getColumnIndexOrThrow("photo"));a.receivedAt=c.getLong(c.getColumnIndexOrThrow("received_at"));a.dueAt=c.getLong(c.getColumnIndexOrThrow("due_at"));a.alertedAt=c.getLong(c.getColumnIndexOrThrow("alerted_at"));return a;
    }

    void card(final Art a){
        LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);c.setPadding(dp(14),dp(13),dp(14),dp(12));c.setBackground(bg(SURFACE,18));
        LinearLayout head=new LinearLayout(this);head.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout gallery=new LinearLayout(this);gallery.setOrientation(LinearLayout.HORIZONTAL);gallery.setGravity(Gravity.CENTER_VERTICAL);gallery.setPadding(0,0,dp(2),0);
        ArrayList<Uri> cardPhotos=photoUris(a.photo);
        if(cardPhotos.isEmpty()){ImageView img=new ImageView(this);img.setBackground(bg(SURFACE2,12));gallery.addView(img,new LinearLayout.LayoutParams(dp(76),dp(62)));}
        else{
            int show=Math.min(3,cardPhotos.size());
            for(int pi=0;pi<show;pi++){ImageView img=new ImageView(this);img.setScaleType(ImageView.ScaleType.CENTER_CROP);try{img.setImageURI(cardPhotos.get(pi));}catch(Exception ignored){}LinearLayout.LayoutParams ip=new LinearLayout.LayoutParams(dp(show==1?76:52),dp(62));ip.setMargins(0,0,dp(3),0);gallery.addView(img,ip);}
            if(cardPhotos.size()>3){TextView more=text("+"+(cardPhotos.size()-3),11,WHITE);more.setGravity(Gravity.CENTER);more.setBackground(bg(SURFACE2,12));gallery.addView(more,new LinearLayout.LayoutParams(dp(34),dp(62)));}
        }
        head.addView(gallery,new LinearLayout.LayoutParams(dp(184),dp(62)));
        LinearLayout info=new LinearLayout(this);info.setOrientation(LinearLayout.VERTICAL);info.setPadding(dp(12),0,0,0);
        TextView cli=text(a.company.isEmpty()?"Sem cliente":a.company,17,WHITE);cli.setTypeface(null,1);
        info.addView(cli);info.addView(text((a.service.isEmpty()?"Arte":a.service)+" • "+(a.desc.isEmpty()?"":a.desc),12,MUTED));
        if(a.dueAt>0){String ds="Entrega: "+dateTime(a.dueAt);int dc=a.dueAt<System.currentTimeMillis()&&!isDelivered(a.status)&&!"Pago".equals(a.status)?RED:BLUE;TextView dt=text(ds,11,dc);dt.setTypeface(null,1);info.addView(dt);}
        double remaining=Math.max(0,a.price-a.paidAmount);
        TextView val=text("A receber: R$ "+money(remaining)+"   •   pago: R$ "+money(a.paidAmount)+"   •   custo: R$ "+money(a.cost),13,GREEN);val.setTypeface(null,1);info.addView(val);
        head.addView(info,new LinearLayout.LayoutParams(0,-2,1));c.addView(head);
        TextView chip=text("●  "+a.status,11,statusColor(a.status));chip.setTypeface(null,1);chip.setPadding(dp(10),dp(6),dp(10),dp(6));chip.setBackground(bg(statusColor(a.status),18));chip.setTextColor(BG);
        LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-2,dp(30));cp.setMargins(0,dp(10),0,dp(8));c.addView(chip,cp);
        LinearLayout b=new LinearLayout(this);Button st=action("STATUS"),pay=action("💰 PAGAR"),z=action("💬 COBRAR"),e=action("✎ EDITAR"),del=action("🗑");
        b.addView(st,new LinearLayout.LayoutParams(0,dp(42),1));b.addView(pay,new LinearLayout.LayoutParams(0,dp(42),1));b.addView(z,new LinearLayout.LayoutParams(0,dp(42),1));b.addView(e,new LinearLayout.LayoutParams(0,dp(42),1));b.addView(del,new LinearLayout.LayoutParams(0,dp(42),1));c.addView(b);
        st.setOnClickListener(v->status(a));pay.setOnClickListener(v->paymentDialog(a));z.setOnClickListener(v->whats(a));e.setOnClickListener(v->form(a,null,null));del.setOnClickListener(v->deleteArt(a));
        list.addView(c);list.addView(new Space(this),new LinearLayout.LayoutParams(1,dp(10)));
    }

    int statusColor(String s){if("Pago".equals(s))return GREEN;if(s.contains("atras"))return RED;if("Arte entregue".equals(s))return BLUE;return YELLOW;}
    void status(final Art a){
        String[] arr={"Nova demanda","Em produção","Pronta","Arte entregue","A receber","Pagamento atrasado","Pago","Arte atrasada"};
        new AlertDialog.Builder(this).setTitle("Alterar status").setItems(arr,(d,w)->{db.updateStatus(a.id,arr[w]);refresh();syncDataFolder();}).show();
    }
    void deleteArt(final Art a){
        new AlertDialog.Builder(this).setTitle("Apagar demanda?").setMessage("Esta arte será removida do banco local. Não dá para desfazer.").setNegativeButton("CANCELAR",null).setPositiveButton("APAGAR",(d,w)->{db.deleteArt(a.id);refresh();syncDataFolder();}).show();
    }

    void form(final Art old, final String presetCompany, final String presetPhone){
        final Dialog d=new Dialog(this);LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);l.setPadding(dp(20),dp(20),dp(20),dp(18));l.setBackground(bg(SURFACE,22));
        TextView title=text(old==null?"Novo pedido":"Editar pedido",22,WHITE);title.setTypeface(null,1);l.addView(title);l.addView(text("O preço de cobrança vem da tabela de serviços. Aqui você controla o custo.",12,MUTED));
        TextView clientLabel=text("CLIENTE",10,MUTED);clientLabel.setTypeface(null,1);l.addView(clientLabel,lp(10));
        Spinner clientSpinner=new Spinner(this);ArrayList<Company> clients=new ArrayList<>();Cursor cc=db.companies();try{while(cc.moveToNext()){Company x=new Company();x.id=cc.getLong(0);x.name=cc.getString(1);x.phone=cc.getString(2);clients.add(x);}}finally{cc.close();}
        ArrayList<String> clientNames=new ArrayList<>();for(Company x:clients)clientNames.add(x.name);if(clientNames.isEmpty())clientNames.add("Nenhum cliente cadastrado");
        ArrayAdapter<String> ca=new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,clientNames);clientSpinner.setAdapter(ca);l.addView(clientSpinner,lp(4));
        EditText company=input("Cliente / empresa");company.setVisibility(View.GONE);EditText phone=input("WhatsApp do cliente");phone.setVisibility(View.GONE);EditText desc=input("Descrição da demanda");l.addView(company);l.addView(phone);l.addView(desc,lp(8));

        Spinner serviceSpinner=new Spinner(this);ArrayList<Service> services=new ArrayList<>();services.add(blankService());Cursor sc=db.services();try{while(sc.moveToNext()){Service s=new Service();s.id=sc.getLong(0);s.name=sc.getString(1);s.desc=sc.getString(2);s.cost=sc.getDouble(3);s.price=sc.getDouble(4);services.add(s);}}finally{sc.close();}
        ArrayList<String> names=new ArrayList<>();for(Service s:services)names.add(s.id==0?s.name:s.name+" • R$ "+money(s.price));ArrayAdapter<String> sa=new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,names);serviceSpinner.setAdapter(sa);
        l.addView(serviceSpinner,lp(8));
        TextView serviceInfo=text("Selecione um serviço para ver descrição e valor.",11,MUTED);serviceInfo.setPadding(dp(4),dp(4),0,0);l.addView(serviceInfo);
        final double[] selectedPrice={old==null?0:old.price};
        TextView valueLabel=text("VALOR DA ARTE: R$ "+money(selectedPrice[0]),15,GREEN);valueLabel.setTypeface(null,1);l.addView(valueLabel,lp(6));
        EditText cost=input("Custo interno da arte (R$)");cost.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);
        EditText paid=input("Valor já recebido (R$)");paid.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);
        EditText quote=input("Nº do orçamento (opcional)");
        l.addView(cost,lp(8));l.addView(paid,lp(8));l.addView(quote,lp(8));
        final long[] receivedAt={old!=null&&old.receivedAt>0?old.receivedAt:System.currentTimeMillis()};
        final long[] dueAt={old!=null?old.dueAt:0};
        Button receivedBtn=action("📥  DEMANDA RECEBIDA: "+dateTime(receivedAt[0]));
        Button dueDateBtn=action("📅  DATA DE ENTREGA: "+(dueAt[0]>0?dateOnly(dueAt[0]):"Definir data"));
        Button dueTimeBtn=action("🕐  HORA DE ENTREGA: "+(dueAt[0]>0?timeOnly(dueAt[0]):"Digite a hora"));
        l.addView(receivedBtn,lp(8));
        l.addView(dueDateBtn,lp(6));
        l.addView(dueTimeBtn,lp(6));
        receivedBtn.setOnClickListener(v->pickDateTime(receivedAt,"Data em que peguei a demanda",receivedBtn));
        dueDateBtn.setOnClickListener(v->pickDeliveryDate(dueAt,dueDateBtn,dueTimeBtn));
        dueTimeBtn.setOnClickListener(v->editDeliveryTime(dueAt,dueDateBtn,dueTimeBtn));

        Button photo=action("📷  ADICIONAR / TROCAR FOTOS");TextView photoInfo=text("Nenhuma foto selecionada",11,MUTED);photoInfo.setPadding(dp(4),dp(5),0,0);l.addView(photo,lp(10));l.addView(photoInfo);
        Button save=action("✓  SALVAR ARTE");save.setTextColor(BG);save.setTypeface(null,1);save.setBackground(bg(GREEN,14));l.addView(save,lp(12));

        if(old!=null){
            valueLabel.setText("VALOR DA ARTE: R$ "+money(old.price));
            company.setText(old.company);phone.setText(old.phone);desc.setText(old.desc);cost.setText(old.cost>0?money(old.cost):"");paid.setText(old.paidAmount>0?money(old.paidAmount):"");quote.setText(old.quoteNumber==null?"":old.quoteNumber);receivedBtn.setText("📥  DEMANDA RECEBIDA: "+dateTime(receivedAt[0]));
            dueDateBtn.setText("📅  DATA DE ENTREGA: "+(dueAt[0]>0?dateOnly(dueAt[0]):"Definir data"));
            dueTimeBtn.setText("🕐  HORA DE ENTREGA: "+(dueAt[0]>0?timeOnly(dueAt[0]):"Digite a hora"));if(!old.photo.isEmpty())photoInfo.setText(photoUris(old.photo).size()+" foto(s) vinculada(s)");
            for(int i=0;i<services.size();i++)if(services.get(i).name.equals(old.service)){serviceSpinner.setSelection(i);selectedPrice[0]=old.price;}
            for(int i=0;i<clients.size();i++)if(clients.get(i).name.equals(old.company))clientSpinner.setSelection(i);
        }
        clientSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){public void onNothingSelected(android.widget.AdapterView<?> p){} public void onItemSelected(android.widget.AdapterView<?> p,View v,int pos,long id){if(!clients.isEmpty()&&pos<clients.size()){Company x=clients.get(pos);company.setText(x.name);phone.setText(x.phone);}}});
                serviceSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
            public void onNothingSelected(android.widget.AdapterView<?> p){}
            public void onItemSelected(android.widget.AdapterView<?> p,View v,int pos,long id){Service s=services.get(pos);if(s.id>0){selectedPrice[0]=s.price;valueLabel.setText("VALOR DA ARTE: R$ "+money(selectedPrice[0]));serviceInfo.setText((s.desc==null||s.desc.isEmpty()?s.name:s.desc)+" • R$ "+money(s.price));}}
        });
        photo.setOnClickListener(q->{Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("image/*");i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);i.addCategory(Intent.CATEGORY_OPENABLE);i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);startActivityForResult(i,101);});
        save.setOnClickListener(q->{
            String co=company.getText().toString().trim();if(co.isEmpty()){company.setError("Informe o cliente");return;}
            String ph=phone.getText().toString().trim();String sv=serviceSpinner.getSelectedItem().toString();String ct=cost.getText().toString().trim();double paidNow=Math.min(selectedPrice[0],Math.max(0,num(paid.getText().toString())));String quoteNo=quote.getText().toString().trim();String finalStatus=paidNow>=selectedPrice[0]&&selectedPrice[0]>0?"Pago":(old==null?"A receber":old.status);
            String photoValue=old==null?"":old.photo;if(!pendingImages.isEmpty()){JSONArray pa=new JSONArray();for(Uri u:pendingImages)pa.put(u.toString());photoValue=pa.toString();pendingImages.clear();pendingImage=null;}
            long clientId=db.addCompany(co,ph);
            if(old==null)db.addArt(clientId,co,ph,sv,desc.getText().toString().trim(),selectedPrice[0],num(ct),paidNow,quoteNo,finalStatus,photoValue,receivedAt[0],dueAt[0]);
            else db.updateArt(old.id,clientId,co,ph,sv,desc.getText().toString().trim(),selectedPrice[0],num(ct),paidNow,quoteNo,finalStatus,photoValue,receivedAt[0],dueAt[0]);
            refresh();syncDataFolder();if(dueAt[0]>System.currentTimeMillis())scheduleDeadline(old==null?db.lastArtId():old.id,dueAt[0]);d.dismiss();
        });
        if(presetCompany!=null&&old==null){company.setText(presetCompany);phone.setText(presetPhone==null?"":presetPhone);for(int i=0;i<clients.size();i++)if(clients.get(i).name.equals(presetCompany))clientSpinner.setSelection(i);}
        d.setContentView(l);d.show();if(d.getWindow()!=null){d.getWindow().setBackgroundDrawableResource(android.R.color.transparent);d.getWindow().setLayout((int)(getResources().getDisplayMetrics().widthPixels*.94),-2);}
    }
    String dateTime(long ms){if(ms<=0)return "";return new java.text.SimpleDateFormat("dd/MM/yyyy HH:mm",Locale.getDefault()).format(new Date(ms));}
    String dateOnly(long ms){if(ms<=0)return "";return new java.text.SimpleDateFormat("dd/MM/yyyy",Locale.getDefault()).format(new Date(ms));}
    String timeOnly(long ms){if(ms<=0)return "";return new java.text.SimpleDateFormat("HH:mm",Locale.getDefault()).format(new Date(ms));}
    boolean isDelivered(String s){return "Arte entregue".equals(s)||"Entregue".equals(s)||"Pronta".equals(s);}
    void pickDeliveryDate(final long[] target,final Button dateBtn,final Button timeBtn){
        Calendar base=Calendar.getInstance();
        if(target[0]>0)base.setTimeInMillis(target[0]);
        DatePickerDialog dpd=new DatePickerDialog(this,(v,y,m,day)->{
            Calendar cal=Calendar.getInstance();
            cal.set(y,m,day,target[0]>0?base.get(Calendar.HOUR_OF_DAY):12,target[0]>0?base.get(Calendar.MINUTE):0,0);
            cal.set(Calendar.MILLISECOND,0);
            target[0]=cal.getTimeInMillis();
            dateBtn.setText("📅  DATA DE ENTREGA: "+dateOnly(target[0]));
            timeBtn.setText("🕐  HORA DE ENTREGA: "+timeOnly(target[0]));
        },base.get(Calendar.YEAR),base.get(Calendar.MONTH),base.get(Calendar.DAY_OF_MONTH));
        dpd.setTitle("Escolha a data de entrega");
        dpd.show();
    }

    void editDeliveryTime(final long[] target,final Button dateBtn,final Button timeBtn){
        final EditText e=input("Ex.: 14:30");
        e.setInputType(InputType.TYPE_CLASS_DATETIME|InputType.TYPE_DATETIME_VARIATION_TIME);
        e.setText(target[0]>0?timeOnly(target[0]):"");
        e.setSelectAllOnFocus(true);
        new AlertDialog.Builder(this).setTitle("🕐 Hora de entrega")
            .setMessage("Digite a hora no formato HH:mm")
            .setView(e)
            .setNegativeButton("CANCELAR",null)
            .setPositiveButton("SALVAR",(d,w)->{
                String value=e.getText().toString().trim().replace("h",":").replace("H",":");
                try{
                    String[] parts=value.split(":");
                    if(parts.length!=2)throw new Exception();
                    int h=Integer.parseInt(parts[0].trim()),m=Integer.parseInt(parts[1].trim());
                    if(h<0||h>23||m<0||m>59)throw new Exception();
                    Calendar cal=Calendar.getInstance();
                    if(target[0]>0)cal.setTimeInMillis(target[0]);
                    cal.set(Calendar.HOUR_OF_DAY,h);cal.set(Calendar.MINUTE,m);cal.set(Calendar.SECOND,0);cal.set(Calendar.MILLISECOND,0);
                    target[0]=cal.getTimeInMillis();
                    dateBtn.setText("📅  DATA DE ENTREGA: "+dateOnly(target[0]));
                    timeBtn.setText("🕐  HORA DE ENTREGA: "+timeOnly(target[0]));
                }catch(Exception ex){
                    Toast.makeText(this,"Hora inválida. Digite, por exemplo, 14:30.",Toast.LENGTH_LONG).show();
                }
            }).show();
    }

    void pickDateTime(final long[] target,String title,final Button button){
        Calendar base=Calendar.getInstance();if(target[0]>0)base.setTimeInMillis(target[0]);
        DatePickerDialog dpd=new DatePickerDialog(this,(v,y,m,day)->{
            Calendar cal=Calendar.getInstance();cal.set(y,m,day,base.get(Calendar.HOUR_OF_DAY),base.get(Calendar.MINUTE),0);cal.set(Calendar.MILLISECOND,0);
            TimePickerDialog tpd=new TimePickerDialog(this,(tv,h,min)->{cal.set(Calendar.HOUR_OF_DAY,h);cal.set(Calendar.MINUTE,min);target[0]=cal.getTimeInMillis();button.setText("📥  DEMANDA RECEBIDA: "+dateTime(target[0]));},base.get(Calendar.HOUR_OF_DAY),base.get(Calendar.MINUTE),true);
            tpd.setTitle("Hora da demanda recebida");tpd.show();
        },base.get(Calendar.YEAR),base.get(Calendar.MONTH),base.get(Calendar.DAY_OF_MONTH));
        dpd.setTitle(title);dpd.show();
    }

    void requestNotificationPermission(){
        if(Build.VERSION.SDK_INT>=33 && checkSelfPermission("android.permission.POST_NOTIFICATIONS")!=android.content.pm.PackageManager.PERMISSION_GRANTED) requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"},909);
    }
    void checkDeadlines(){
        long now=System.currentTimeMillis();Cursor cur=db.arts();try{while(cur.moveToNext()){Art a=art(cur);if(a.dueAt>0&&a.dueAt<now&&!isDelivered(a.status)&&!"Pago".equals(a.status)&&!"Pagamento atrasado".equals(a.status)){db.updateStatus(a.id,"Arte atrasada");if(a.alertedAt==0){showOverdueAlert(a);OverdueNotifier.show(this,a.id,a.company,a.service,a.desc,a.photo);db.markAlerted(a.id,now);}}}}finally{cur.close();}
    }
    void showOverdueAlert(Art a){
        AlertVoiceService.start(this,"Atenção! A entrega da arte da empresa "+a.company+" está atrasada.");
        new AlertDialog.Builder(this).setTitle("🚨 DEMANDA ATRASADA").setMessage(a.company+" • "+(a.service.isEmpty()?"Arte":a.service)+"\nPrazo: "+dateTime(a.dueAt)).setPositiveButton("OK",null).show();
    }
    void scheduleDeadline(long id,long when){
        if(when<=System.currentTimeMillis())return;
        AlarmManager am=(AlarmManager)getSystemService(ALARM_SERVICE);
        Intent i=new Intent(this,DeadlineReceiver.class);i.putExtra("art_id",id);
        PendingIntent pi=PendingIntent.getBroadcast(this,(int)(id%1000000),i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        try{
            if(Build.VERSION.SDK_INT>=31){
                if(!am.canScheduleExactAlarms())return;
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,when,pi);
            }else if(Build.VERSION.SDK_INT>=23) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,when,pi);
            else am.setExact(AlarmManager.RTC_WAKEUP,when,pi);
        }catch(Exception ignored){}
    }
    void scheduleAllDeadlines(){
        Cursor c=db.arts();
        try{
            while(c.moveToNext()){
                Art a=art(c);
                if(a.dueAt>System.currentTimeMillis()&&!isDelivered(a.status)&&!"Pago".equals(a.status))scheduleDeadline(a.id,a.dueAt);
            }
        }finally{c.close();}
    }
    @Override protected void onResume(){
        super.onResume();
        if(db!=null){
            if(Build.VERSION.SDK_INT>=31){
                AlarmManager am=(AlarmManager)getSystemService(ALARM_SERVICE);
                if(!am.canScheduleExactAlarms()){
                    try{
                        Intent s=new Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
                        s.setData(Uri.parse("package:"+getPackageName()));
                        startActivity(s);
                    }catch(Exception ignored){}
                }else scheduleAllDeadlines();
            }else scheduleAllDeadlines();
        }
    }
    Service blankService(){Service s=new Service();s.name="— Sem tabela de preço —";s.desc="";return s;}


    LinearLayout todayPanel(){
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(14),dp(10),dp(14),dp(8));box.setBackground(bg(SURFACE,18));
        LinearLayout top=new LinearLayout(this);TextView title=text("BOM DIA, SANCHES 👊",15,WHITE);title.setTypeface(null,1);top.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        Button k=action("📋 KANBAN");k.setMinHeight(dp(36));top.addView(k,new LinearLayout.LayoutParams(dp(105),dp(38)));k.setOnClickListener(v->kanbanDialog());box.addView(top);
        todaySummary=text("Carregando resumo de hoje...",11,MUTED);box.addView(todaySummary);
        todayNext=text("Próxima entrega: —",11,YELLOW);todayNext.setTypeface(null,1);box.addView(todayNext);
        return box;
    }
    void updateTodayPanel(){
        if(todaySummary==null)return;
        Calendar start=Calendar.getInstance();start.set(Calendar.HOUR_OF_DAY,0);start.set(Calendar.MINUTE,0);start.set(Calendar.SECOND,0);start.set(Calendar.MILLISECOND,0);
        long dayStart=start.getTimeInMillis(),dayEnd=dayStart+86400000L,now=System.currentTimeMillis();
        int overdue=0,today=0;double receive=0,paidToday=0;Art next=null;
        Cursor c=db.arts();try{while(c.moveToNext()){Art a=art(c);double rem=Math.max(0,a.price-a.paidAmount);if(a.dueAt>0&&a.dueAt<now&&!isDelivered(a.status)&&!"Pago".equals(a.status))overdue++;if(a.dueAt>=dayStart&&a.dueAt<dayEnd&&!isDelivered(a.status)&&!"Pago".equals(a.status))today++;receive+=rem;if(a.paidAmount>0&&a.receivedAt>=dayStart&&a.receivedAt<dayEnd)paidToday+=a.paidAmount;if(a.dueAt>now&&!isDelivered(a.status)&&!"Pago".equals(a.status)&&(next==null||a.dueAt<next.dueAt))next=a;}}finally{c.close();}
        todaySummary.setText("🔴 "+overdue+" atrasada(s)   🟠 "+today+" entrega(s) hoje   💰 R$ "+money(receive)+" a receber   💵 R$ "+money(paidToday)+" recebido hoje");
        if(next==null)todayNext.setText("Próxima entrega: nenhuma cadastrada"); else todayNext.setText("PRÓXIMA: "+next.company+" — "+dateTime(next.dueAt)+" • "+(next.service.isEmpty()?"Arte":next.service));
    }
    void paymentDialog(final Art a){
        EditText v=input("Valor recebido agora (R$)");v.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);
        double remaining=Math.max(0,a.price-a.paidAmount);v.setText(money(remaining));
        new AlertDialog.Builder(this).setTitle("💰 Registrar pagamento").setMessage("Total: R$ "+money(a.price)+"\nJá recebido: R$ "+money(a.paidAmount)+"\nRestante: R$ "+money(remaining)).setView(v).setNegativeButton("CANCELAR",null).setPositiveButton("REGISTRAR",(d,w)->{double add=num(v.getText().toString());if(add<0)return;double paid=Math.min(a.price,a.paidAmount+add);db.updatePaid(a.id,paid);refresh();syncDataFolder();}).show();
    }
    void kanbanDialog(){
        final String[] cols={"NOVAS","PRODUÇÃO","PRONTAS","ENTREGUES","A RECEBER","PAGAS"};final String[] statuses={"Nova demanda","Em produção","Pronta","Arte entregue","A receber","Pago"};
        final Dialog d=new Dialog(this);LinearLayout outer=new LinearLayout(this);outer.setOrientation(LinearLayout.VERTICAL);outer.setPadding(dp(12),dp(12),dp(12),dp(12));outer.addView(text("📋 Fluxo de produção • segure e arraste",18,WHITE));
        HorizontalScrollView hsv=new HorizontalScrollView(this);LinearLayout board=new LinearLayout(this);board.setOrientation(LinearLayout.HORIZONTAL);hsv.addView(board);outer.addView(hsv,new LinearLayout.LayoutParams(-1,0,1));
        ArrayList<LinearLayout> columns=new ArrayList<>();
        for(int i=0;i<cols.length;i++){LinearLayout col=new LinearLayout(this);col.setOrientation(LinearLayout.VERTICAL);col.setPadding(dp(8),dp(8),dp(8),dp(8));col.setBackground(bg(SURFACE,16));TextView h=text(cols[i],11,WHITE);h.setTypeface(null,1);col.addView(h);final String target=statuses[i];col.setOnDragListener((v,e)->{if(e.getAction()==DragEvent.ACTION_DROP){try{long id=(Long)e.getLocalState();db.updateStatus(id,target);refresh();syncDataFolder();loadKanban(columns,statuses);}catch(Exception ignored){}return true;}return true;});board.addView(col,new LinearLayout.LayoutParams(dp(170),-1));columns.add(col);}
        loadKanban(columns,statuses);d.setContentView(outer);d.show();if(d.getWindow()!=null)d.getWindow().setLayout((int)(getResources().getDisplayMetrics().widthPixels*.98),dp(620));
    }
    void loadKanban(ArrayList<LinearLayout> columns,String[] statuses){
        for(LinearLayout c:columns)while(c.getChildCount()>1)c.removeViewAt(1);
        Cursor cur=db.arts();try{while(cur.moveToNext()){Art a=art(cur);int idx=0;if("Em produção".equals(a.status))idx=1;else if("Pronta".equals(a.status))idx=2;else if("Arte entregue".equals(a.status))idx=3;else if("A receber".equals(a.status)||"Pagamento atrasado".equals(a.status)||"Arte atrasada".equals(a.status))idx=4;else if("Pago".equals(a.status))idx=5;TextView card=text(a.company+"\n"+(a.service.isEmpty()?"Arte":a.service)+"\nR$ "+money(Math.max(0,a.price-a.paidAmount)),12,WHITE);card.setPadding(dp(9),dp(8),dp(9),dp(8));card.setBackground(bg(SURFACE2,12));card.setOnLongClickListener(v->{v.startDragAndDrop(ClipData.newPlainText("art_id",String.valueOf(a.id)),new View.DragShadowBuilder(v),a.id,0);return true;});columns.get(idx).addView(card,new LinearLayout.LayoutParams(-1,dp(76)));}}finally{cur.close();}
    }
    void financeDialog(){
        Calendar st=Calendar.getInstance();st.set(Calendar.DAY_OF_MONTH,1);st.set(Calendar.HOUR_OF_DAY,0);st.set(Calendar.MINUTE,0);st.set(Calendar.SECOND,0);st.set(Calendar.MILLISECOND,0);long start=st.getTimeInMillis();
        double billed=0,paid=0,cost=0,expenses=0;Cursor c=db.arts();try{while(c.moveToNext()){Art a=art(c);if(a.receivedAt>=start){billed+=a.price;paid+=a.paidAmount;cost+=a.cost;}}}finally{c.close();}
        Cursor e=db.expenses();try{while(e.moveToNext())if(e.getLong(3)>=start)expenses+=e.getDouble(2);}finally{e.close();}
        double receivable=Math.max(0,billed-paid);
        String msg="FATURAMENTO DO MÊS\nR$ "+money(billed)+"\n\nRECEBIDO\nR$ "+money(paid)+"\n\nA RECEBER\nR$ "+money(receivable)+"\n\nCUSTOS DAS ARTES\nR$ "+money(cost)+"\n\nDESPESAS\nR$ "+money(expenses)+"\n\nLUCRO ESTIMADO\nR$ "+money(billed-cost-expenses)+"\n\nLUCRO REALIZADO\nR$ "+money(paid-cost-expenses);
        new AlertDialog.Builder(this).setTitle("💰 Financeiro do mês").setMessage(msg).setPositiveButton("OK",null).setNeutralButton("DESPESAS",(d,w)->expensesDialog()).show();
    }
    void quoteDialog(){
        Cursor c=db.companies();ArrayList<Company> clients=new ArrayList<>();try{while(c.moveToNext()){Company x=new Company();x.id=c.getLong(0);x.name=c.getString(1);x.phone=c.getString(2);clients.add(x);}}finally{c.close();}
        LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);l.setPadding(dp(18),dp(18),dp(18),dp(16));Spinner sp=new Spinner(this);ArrayList<String> names=new ArrayList<>();for(Company x:clients)names.add(x.name);if(names.isEmpty())names.add("Cliente não cadastrado");sp.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,names));
        EditText service=input("Serviço / descrição");EditText qty=input("Quantidade");qty.setInputType(InputType.TYPE_CLASS_NUMBER);EditText value=input("Valor total (R$)");value.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);EditText prazo=input("Prazo (ex.: 08/10/2026)");
        l.addView(text("EMPRESA",10,MUTED));l.addView(sp,lp(4));l.addView(service,lp(8));l.addView(qty,lp(8));l.addView(value,lp(8));l.addView(prazo,lp(8));Button pdf=action("🧾 GERAR ORÇAMENTO PDF");pdf.setTextColor(BG);pdf.setBackground(bg(GREEN,14));l.addView(pdf,lp(12));
        Dialog d=new Dialog(this);d.setContentView(l);d.show();if(d.getWindow()!=null)d.getWindow().setLayout((int)(getResources().getDisplayMetrics().widthPixels*.94),-2);
        pdf.setOnClickListener(v->{String co=clients.isEmpty()?"Cliente":clients.get(Math.max(0,sp.getSelectedItemPosition())).name;String sv=service.getText().toString().trim();int q=(int)num(qty.getText().toString());double val=num(value.getText().toString());if(sv.isEmpty()||val<=0){Toast.makeText(this,"Informe serviço e valor.",Toast.LENGTH_SHORT).show();return;}generateQuotePdf(co,sv,q,val,prazo.getText().toString());d.dismiss();});
    }
    void generateQuotePdf(String company,String service,int qty,double value,String deadline){
        try{File file=new File(dataFolder(),"orcamento_"+System.currentTimeMillis()+".pdf");PdfDocument doc=new PdfDocument();PdfDocument.Page page=doc.startPage(new PdfDocument.PageInfo.Builder(595,842,1).create());Canvas canvas=page.getCanvas();Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);canvas.drawColor(Color.rgb(247,249,252));p.setColor(GREEN);canvas.drawRect(0,0,595,120,p);p.setColor(Color.WHITE);p.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);p.setTextSize(27);canvas.drawText("ORÇAMENTO",36,52,p);p.setTypeface(android.graphics.Typeface.DEFAULT);p.setTextSize(11);canvas.drawText("CONTROLE DE ARTES VIP",36,76,p);p.setTextSize(9);canvas.drawText("ORÇAMENTO #"+new java.text.SimpleDateFormat("yyyyMMddHHmmss",Locale.getDefault()).format(new Date()),36,98,p);p.setColor(Color.rgb(30,36,45));p.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);p.setTextSize(17);canvas.drawText(company,36,165,p);p.setTypeface(android.graphics.Typeface.DEFAULT);p.setTextSize(12);canvas.drawText("Serviço: "+service,36,194,p);canvas.drawText("Quantidade: "+qty,36,218,p);canvas.drawText("Prazo: "+(deadline.isEmpty()?"A combinar":deadline),36,242,p);p.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);p.setTextSize(22);canvas.drawText("TOTAL: R$ "+money(value),36,300,p);p.setTypeface(android.graphics.Typeface.DEFAULT);p.setTextSize(11);canvas.drawText("Validade e condições podem ser combinadas pelo WhatsApp.",36,340,p);if(!db.setting("pix").isEmpty())canvas.drawText("PIX: "+db.setting("pix"),36,370,p);doc.finishPage(page);doc.writeTo(new FileOutputStream(file));doc.close();sharePdf(file);}catch(Exception e){Toast.makeText(this,"Não foi possível gerar o orçamento.",Toast.LENGTH_LONG).show();}
    }
    void menu(){
        final String[] a={"👥  Clientes","💰  Tabela de preços","💳  Configurar PIX","📊  Resumo financeiro","📅  Agenda de prazos","📋  Kanban de produção","💵  Financeiro completo","🧾  Novo orçamento PDF","🔊  Configurar voz","🎨  Personalizar cor","📄  Gerar PDF","💾  Backup / Restaurar"};
        new AlertDialog.Builder(this).setTitle("Controle de Artes").setItems(a,(d,w)->{if(w==0)companiesDialog();if(w==1)servicesDialog();if(w==2)pixDialog();if(w==3)summaryDialog();if(w==4)agendaDialog();if(w==5)kanbanDialog();if(w==6)financeDialog();if(w==7)quoteDialog();if(w==8)voiceSettingsDialog();if(w==9)themeDialog();if(w==10)generatePdf(null);if(w==11)backupMenu();}).show();
    }

    void servicesDialog(){
        LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);l.setPadding(dp(18),dp(14),dp(18),dp(10));
        Button add=action("＋ NOVO SERVIÇO");l.addView(add);
        LinearLayout listS=new LinearLayout(this);listS.setOrientation(LinearLayout.VERTICAL);ScrollView sv=new ScrollView(this);sv.addView(listS);l.addView(sv,new LinearLayout.LayoutParams(-1,0,1));
        final Dialog d=new Dialog(this);d.setTitle("Tabela de preços");d.setContentView(l);d.show();if(d.getWindow()!=null)d.getWindow().setLayout((int)(getResources().getDisplayMetrics().widthPixels*.94),dp(520));
        final Runnable[] reload=new Runnable[1];reload[0]=()->{listS.removeAllViews();Cursor c=db.services();try{while(c.moveToNext()){long id=c.getLong(0);String n=c.getString(1),sd=c.getString(2);double co=c.getDouble(3),pr=c.getDouble(4);LinearLayout row=new LinearLayout(this);row.setPadding(0,dp(7),0,dp(7));LinearLayout tx=new LinearLayout(this);tx.setOrientation(LinearLayout.VERTICAL);tx.addView(text(n+" • R$ "+money(pr),15,WHITE));tx.addView(text(sd==null||sd.isEmpty()?"Sem descrição":sd,11,MUTED));row.addView(tx,new LinearLayout.LayoutParams(0,-2,1));Button del=action("🗑");row.addView(del,new LinearLayout.LayoutParams(dp(52),dp(42)));del.setOnClickListener(v->{db.deleteService(id);reload[0].run();syncDataFolder();});listS.addView(row);}}finally{c.close();}};
        add.setOnClickListener(v->serviceForm(reload[0]));reload[0].run();
    }

    void serviceForm(Runnable reload){
        LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);l.setPadding(dp(20),dp(20),dp(20),dp(15));
        EditText n=input("Nome do serviço (ex.: Cópia de arte)");EditText sd=input("Descrição do serviço (ex.: cópia de arte simples)");EditText pr=input("Valor (R$)");pr.setInputType(2|8192);
        l.addView(n);l.addView(sd,lp(8));l.addView(pr,lp(8));Button save=action("SALVAR TABELA");save.setTextColor(BG);save.setBackground(bg(GREEN,14));l.addView(save,lp(12));
        Dialog d=new Dialog(this);d.setContentView(l);d.show();if(d.getWindow()!=null){d.getWindow().setBackgroundDrawableResource(android.R.color.transparent);d.getWindow().setLayout((int)(getResources().getDisplayMetrics().widthPixels*.9),-2);}
        save.setOnClickListener(v->{if(n.getText().toString().trim().isEmpty())return;db.addService(n.getText().toString(),sd.getText().toString(),0,num(pr.getText().toString()));d.dismiss();reload.run();syncDataFolder();});
    }

    void companiesDialog(){
        LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);l.setPadding(dp(18),dp(14),dp(18),dp(10));Button add=action("＋ NOVO CLIENTE");l.addView(add);
        LinearLayout ls=new LinearLayout(this);ls.setOrientation(LinearLayout.VERTICAL);ScrollView sv=new ScrollView(this);sv.addView(ls);l.addView(sv,new LinearLayout.LayoutParams(-1,0,1));
        Dialog d=new Dialog(this);d.setTitle("Clientes");d.setContentView(l);d.show();if(d.getWindow()!=null)d.getWindow().setLayout((int)(getResources().getDisplayMetrics().widthPixels*.94),dp(520));
        final Runnable[] reload=new Runnable[1];reload[0]=()->{ls.removeAllViews();Cursor c=db.companies();try{while(c.moveToNext()){long id=c.getLong(0);String n=c.getString(1),ph=c.getString(2);LinearLayout row=new LinearLayout(this);row.setPadding(0,dp(7),0,dp(7));LinearLayout tx=new LinearLayout(this);tx.setOrientation(LinearLayout.VERTICAL);tx.addView(text(n,15,WHITE));tx.addView(text(ph.isEmpty()?"Sem WhatsApp cadastrado":ph,11,MUTED));row.addView(tx,new LinearLayout.LayoutParams(0,-2,1));Button order=action("＋ PEDIDO");Button demands=action("ARTES");Button pdf=action("PDF");Button del=action("🗑");row.addView(order,new LinearLayout.LayoutParams(dp(78),dp(42)));row.addView(demands,new LinearLayout.LayoutParams(dp(68),dp(42)));row.addView(pdf,new LinearLayout.LayoutParams(dp(52),dp(42)));row.addView(del,new LinearLayout.LayoutParams(dp(52),dp(42)));order.setOnClickListener(v->form(null,n,ph));demands.setOnClickListener(v->clientDemands(n,ph));pdf.setOnClickListener(v->generatePdf(n));del.setOnClickListener(v->{db.deleteCompany(id);reload[0].run();refresh();syncDataFolder();});ls.addView(row);}}finally{c.close();}};
        add.setOnClickListener(v->companyForm(reload[0]));reload[0].run();
    }

    void chargeCompany(String company,String phone){
        ArrayList<Art> open=new ArrayList<>();double total=0,cost=0;Cursor cur=db.arts();
        try{while(cur.moveToNext()){Art a=art(cur);double rem=Math.max(0,a.price-a.paidAmount);if(company.equalsIgnoreCase(a.company)&&rem>0){open.add(a);total+=rem;cost+=a.cost;}}}finally{cur.close();}
        if(open.isEmpty()){new AlertDialog.Builder(this).setTitle("Nenhuma cobrança").setMessage("Não há artes em aberto para "+company+".").setPositiveButton("OK",null).show();return;}
        StringBuilder msg=new StringBuilder("Olá, "+company+"! 👋\\n\\nSegue o fechamento das artes: \\n");
        for(int i=0;i<open.size();i++){Art a=open.get(i);msg.append(i+1).append(". ").append(a.service.isEmpty()?(a.desc.isEmpty()?"Arte":a.desc):a.service).append(" — R$ ").append(money(a.price)).append("\\n");}
        msg.append("\\nTOTAL: R$ ").append(money(total));
        String pix=db.setting("pix");if(!pix.isEmpty()){msg.append("\\n\\n💳 PIX: ").append(pix);if(!db.setting("pix_name").isEmpty())msg.append("\\nRecebedor: ").append(db.setting("pix_name"));}
        msg.append("\\n\\nQuando puder, me envie o pagamento. Obrigado!");
        new AlertDialog.Builder(this).setTitle("Cobrança conjunta")
          .setMessage(open.size()+" artes em uma única cobrança\\nTotal: R$ "+money(total)+"\\n\\nAs cobranças individuais continuam disponíveis no botão COBRAR de cada arte.")
          .setNegativeButton("CANCELAR",null).setPositiveButton("ENVIAR NO WHATSAPP",(d,w)->{
              Intent i=new Intent(Intent.ACTION_SEND);i.setType("text/plain");i.putExtra(Intent.EXTRA_TEXT,msg.toString());
              try{startActivity(Intent.createChooser(i,"Cobrar "+company));}catch(Exception ignored){}
          }).show();
    }

    void expensesDialog(){
        LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);l.setPadding(dp(18),dp(14),dp(18),dp(10));
        Button add=action("＋ NOVA DESPESA");l.addView(add);
        LinearLayout ls=new LinearLayout(this);ls.setOrientation(LinearLayout.VERTICAL);ScrollView sv=new ScrollView(this);sv.addView(ls);l.addView(sv,new LinearLayout.LayoutParams(-1,0,1));
        Dialog d=new Dialog(this);d.setTitle("Despesas gerais");d.setContentView(l);d.show();if(d.getWindow()!=null)d.getWindow().setLayout((int)(getResources().getDisplayMetrics().widthPixels*.94),dp(520));
        final Runnable[] reload=new Runnable[1];reload[0]=()->{ls.removeAllViews();double total=0;Cursor c=db.expenses();try{while(c.moveToNext()){long id=c.getLong(0);String n=c.getString(1);double v=c.getDouble(2);total+=v;LinearLayout row=new LinearLayout(this);row.setPadding(0,dp(7),0,dp(7));LinearLayout tx=new LinearLayout(this);tx.setOrientation(LinearLayout.VERTICAL);tx.addView(text(n,15,WHITE));tx.addView(text("R$ "+money(v),12,RED));row.addView(tx,new LinearLayout.LayoutParams(0,-2,1));Button del=action("🗑");row.addView(del,new LinearLayout.LayoutParams(dp(52),dp(42)));del.setOnClickListener(x->{db.deleteExpense(id);reload[0].run();refresh();syncDataFolder();});ls.addView(row);}}finally{c.close();}TextView t=text("TOTAL DE DESPESAS: R$ "+money(total),15,WHITE);t.setTypeface(null,1);ls.addView(t,0);};
        add.setOnClickListener(v->expenseForm(reload[0]));reload[0].run();
    }
    void expenseForm(Runnable reload){
        LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);l.setPadding(dp(20),dp(20),dp(20),dp(15));
        EditText n=input("Descrição (ex.: tinta, transporte, energia)");EditText v=input("Valor (R$)");v.setInputType(2|8192);l.addView(n);l.addView(v,lp(8));
        Button s=action("SALVAR DESPESA");s.setTextColor(BG);s.setBackground(bg(GREEN,14));l.addView(s,lp(12));Dialog d=new Dialog(this);d.setContentView(l);d.show();if(d.getWindow()!=null){d.getWindow().setBackgroundDrawableResource(android.R.color.transparent);d.getWindow().setLayout((int)(getResources().getDisplayMetrics().widthPixels*.9),-2);}
        s.setOnClickListener(x->{if(n.getText().toString().trim().isEmpty())return;db.addExpense(n.getText().toString(),num(v.getText().toString()));d.dismiss();reload.run();refresh();syncDataFolder();});
    }

    void backupMenu(){
        new AlertDialog.Builder(this).setTitle("Backup local").setItems(new String[]{"💾 Fazer backup agora","♻ Restaurar backup"},(d,w)->{if(w==0)createBackup();else restoreBackup();}).show();
    }
    void createBackup(){
        Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.setType("application/json");i.putExtra(Intent.EXTRA_TITLE,"controle-artes-backup.json");startActivityForResult(i,202);
    }
    void restoreBackup(){
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("application/json");i.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(i,203);
    }
    void writeBackup(Uri u){
        try{OutputStream out=getContentResolver().openOutputStream(u);out.write(db.exportJson().toString(2).getBytes("UTF-8"));out.close();Toast.makeText(this,"Backup salvo com sucesso!",Toast.LENGTH_LONG).show();}catch(Exception e){Toast.makeText(this,"Não foi possível salvar o backup.",Toast.LENGTH_LONG).show();}
    }
    void readBackup(Uri u){
        try{InputStream in=getContentResolver().openInputStream(u);ByteArrayOutputStream b=new ByteArrayOutputStream();byte[] buf=new byte[4096];int n;while((n=in.read(buf))>0)b.write(buf,0,n);in.close();JSONObject j=new JSONObject(new String(b.toByteArray(),"UTF-8"));if(db.importJson(j)){Toast.makeText(this,"Backup restaurado!",Toast.LENGTH_LONG).show();refresh();}else Toast.makeText(this,"Backup inválido.",Toast.LENGTH_LONG).show();}catch(Exception e){Toast.makeText(this,"Erro ao restaurar backup.",Toast.LENGTH_LONG).show();}
    }

    void clientDemands(String company,String phone){
        LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);l.setPadding(dp(18),dp(14),dp(18),dp(10));
        LinearLayout buttons=new LinearLayout(this);Button order=action("＋ NOVO PEDIDO");Button pdf=action("📄 PDF");Button charge=action("💬 COBRAR TUDO");buttons.addView(order,new LinearLayout.LayoutParams(0,dp(44),1));buttons.addView(pdf,new LinearLayout.LayoutParams(0,dp(44),1));buttons.addView(charge,new LinearLayout.LayoutParams(0,dp(44),1));l.addView(buttons);
        LinearLayout ls=new LinearLayout(this);ls.setOrientation(LinearLayout.VERTICAL);ScrollView sv=new ScrollView(this);sv.addView(ls);l.addView(sv,new LinearLayout.LayoutParams(-1,0,1));
        Dialog d=new Dialog(this);d.setTitle("Demandas • "+company);d.setContentView(l);d.show();if(d.getWindow()!=null)d.getWindow().setLayout((int)(getResources().getDisplayMetrics().widthPixels*.94),dp(560));
        order.setOnClickListener(v->form(null,company,phone));pdf.setOnClickListener(v->generatePdf(company));charge.setOnClickListener(v->chargeCompany(company,phone));
        Cursor c=db.clientArts(company);try{while(c.moveToNext()){Art a=art(c);LinearLayout row=new LinearLayout(this);row.setPadding(0,dp(8),0,dp(8));LinearLayout tx=new LinearLayout(this);tx.setOrientation(LinearLayout.VERTICAL);tx.addView(text(a.desc.isEmpty()?"Arte":a.desc,15,WHITE));tx.addView(text(a.service+" • R$ "+money(a.price)+" • "+a.status,12,MUTED));row.addView(tx,new LinearLayout.LayoutParams(0,-2,1));Button del=action("🗑");row.addView(del,new LinearLayout.LayoutParams(dp(52),dp(42)));del.setOnClickListener(v->{db.deleteArt(a.id);d.dismiss();refresh();syncDataFolder();clientDemands(company,phone);});ls.addView(row);}}finally{c.close();}
    }

    void companyForm(Runnable reload){
        LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);l.setPadding(dp(20),dp(20),dp(20),dp(15));EditText n=input("Nome da empresa");EditText p=input("WhatsApp (opcional)");EditText address=input("Endereço (opcional)");EditText notes=input("Observações do cliente");l.addView(n);l.addView(p,lp(8));l.addView(address,lp(8));l.addView(notes,lp(8));
        Button s=action("SALVAR EMPRESA");s.setTextColor(BG);s.setBackground(bg(GREEN,14));l.addView(s,lp(12));Dialog d=new Dialog(this);d.setContentView(l);d.show();if(d.getWindow()!=null){d.getWindow().setBackgroundDrawableResource(android.R.color.transparent);d.getWindow().setLayout((int)(getResources().getDisplayMetrics().widthPixels*.9),-2);}
        s.setOnClickListener(v->{if(n.getText().toString().trim().isEmpty())return;db.addCompany(n.getText().toString(),p.getText().toString(),address.getText().toString(),notes.getText().toString());d.dismiss();reload.run();syncDataFolder();});
    }

    void pixDialog(){
        LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);l.setPadding(dp(20),dp(20),dp(20),dp(15));
        l.addView(text("PIX para cobranças",20,WHITE));l.addView(text("Esses dados serão incluídos automaticamente na mensagem.",12,MUTED));
        EditText key=input("Chave PIX (CPF, CNPJ, e-mail, telefone ou aleatória)");EditText name=input("Nome do recebedor");key.setText(db.setting("pix"));name.setText(db.setting("pix_name"));l.addView(key,lp(12));l.addView(name,lp(8));
        Button s=action("✓ SALVAR PIX");s.setTextColor(BG);s.setBackground(bg(GREEN,14));l.addView(s,lp(12));Dialog d=new Dialog(this);d.setContentView(l);d.show();if(d.getWindow()!=null){d.getWindow().setBackgroundDrawableResource(android.R.color.transparent);d.getWindow().setLayout((int)(getResources().getDisplayMetrics().widthPixels*.92),-2);}
        s.setOnClickListener(v->{db.setting("pix",key.getText().toString().trim());db.setting("pix_name",name.getText().toString().trim());syncDataFolder();d.dismiss();});
    }

    void agendaDialog(){
        LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);l.setPadding(dp(18),dp(14),dp(18),dp(10));
        TextView h=text("📅 Próximas entregas",19,WHITE);h.setTypeface(null,1);l.addView(h);
        LinearLayout ls=new LinearLayout(this);ls.setOrientation(LinearLayout.VERTICAL);ScrollView sv=new ScrollView(this);sv.addView(ls);l.addView(sv,new LinearLayout.LayoutParams(-1,0,1));
        Cursor c=db.arts();ArrayList<Art> items=new ArrayList<>();try{while(c.moveToNext()){Art a=art(c);if(a.dueAt>0&&!"Pago".equals(a.status)&&!"Arte entregue".equals(a.status))items.add(a);}}finally{c.close();}
        Collections.sort(items,(x,y)->Long.compare(x.dueAt,y.dueAt));
        if(items.isEmpty())ls.addView(text("Nenhum prazo cadastrado.",14,MUTED));
        for(Art a:items){LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.VERTICAL);row.setPadding(dp(12),dp(10),dp(12),dp(10));row.setBackground(bg(SURFACE2,14));String prefix=a.dueAt<System.currentTimeMillis()?"🚨 ATRASADA":"⏰ "+dateTime(a.dueAt);TextView t=text(prefix,13,a.dueAt<System.currentTimeMillis()?RED:YELLOW);t.setTypeface(null,1);row.addView(t);row.addView(text(a.company+" • "+(a.service.isEmpty()?"Arte":a.service),14,WHITE));row.addView(text("Valor: R$ "+money(a.price)+" • "+a.status,11,MUTED));LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,dp(82));rp.setMargins(0,0,0,dp(8));ls.addView(row,rp);}
        Dialog d=new Dialog(this);d.setContentView(l);d.show();if(d.getWindow()!=null)d.getWindow().setLayout((int)(getResources().getDisplayMetrics().widthPixels*.94),dp(560));
    }
    void summaryDialog(){
        double revenue=0,c=0,paid=0,receivable=0;Cursor cur=db.arts();try{while(cur.moveToNext()){double p=cur.getDouble(cur.getColumnIndexOrThrow("price")),co=cur.getDouble(cur.getColumnIndexOrThrow("cost")),pg=cur.getDouble(cur.getColumnIndexOrThrow("paid_amount"));revenue+=p;c+=co;paid+=pg;receivable+=Math.max(0,p-pg);}}finally{cur.close();}
        new AlertDialog.Builder(this).setTitle("Resumo financeiro").setMessage("Faturamento: R$ "+money(revenue)+"\nCustos: R$ "+money(c)+"\nLucro estimado: R$ "+money(revenue-c)+"\n\nRecebido: R$ "+money(paid)+"\nA receber: R$ "+money(receivable)+"\n\nPIX cadastrado: "+(db.setting("pix").isEmpty()?"não":"sim")).setPositiveButton("OK",null).show();
    }

    void whats(Art a){
        String pix=db.setting("pix"),pixName=db.setting("pix_name");
        String msg="Olá, "+(a.company.isEmpty()?"cliente":a.company)+"! 👋\n\n"+
                "Passando para lembrar do pagamento da arte: "+(a.desc.isEmpty()?(a.service.isEmpty()?"arte":a.service):a.desc)+".\n"+
                "Valor restante: R$ "+money(Math.max(0,a.price-a.paidAmount));
        if(!pix.isEmpty()){msg+="\n\n💳 PIX: "+pix;if(!pixName.isEmpty())msg+="\nRecebedor: "+pixName;}
        msg+="\n\nQuando puder, me envie o pagamento. Obrigado!";

        ArrayList<Uri> photos=photoUris(a.photo);
        try{
            Intent i;
            if(photos.size() > 1){
                i=new Intent(Intent.ACTION_SEND_MULTIPLE);
                i.setType("image/*");
                i.putParcelableArrayListExtra(Intent.EXTRA_STREAM,photos);
                i.putExtra(Intent.EXTRA_TEXT,msg);
                i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                ClipData clip=ClipData.newRawUri("arte-1",photos.get(0));
                for(int n=1;n<photos.size();n++) clip.addItem(new ClipData.Item(photos.get(n)));
                i.setClipData(clip);
            }else if(photos.size()==1){
                i=new Intent(Intent.ACTION_SEND);
                i.setType("image/*");
                i.putExtra(Intent.EXTRA_STREAM,photos.get(0));
                i.putExtra(Intent.EXTRA_TEXT,msg);
                i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                i.setClipData(ClipData.newRawUri("arte",photos.get(0)));
            }else{
                i=new Intent(Intent.ACTION_SEND);
                i.setType("text/plain");
                i.putExtra(Intent.EXTRA_TEXT,msg);
            }
            i.setPackage("com.whatsapp");
            try{
                startActivity(i);
            }catch(Exception noWhats){
                i.setPackage(null);
                startActivity(Intent.createChooser(i,"Enviar cobrança"));
            }
        }catch(Exception ex){
            Toast.makeText(this,"Não foi possível preparar a cobrança.",Toast.LENGTH_LONG).show();
        }
    }

    ArrayList<Uri> photoUris(String value){
        ArrayList<Uri> out=new ArrayList<>();if(value==null||value.isEmpty())return out;
        try{if(value.trim().startsWith("[")){JSONArray a=new JSONArray(value);for(int i=0;i<a.length();i++)out.add(Uri.parse(a.getString(i)));}else out.add(Uri.parse(value));}catch(Exception ignored){}return out;
    }
    Uri firstPhoto(String value){ArrayList<Uri> p=photoUris(value);return p.isEmpty()?null:p.get(0);}
    void loadThemeColor(){try{String h=db.setting("theme_color");if(!h.isEmpty())GREEN=Color.parseColor(h);}catch(Exception ignored){}}

    void voiceSettingsDialog(){
        final SharedPreferences p=getSharedPreferences("voz_alerta",0);
        LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);l.setPadding(dp(18),dp(18),dp(18),dp(14));
        l.addView(text("🔊 Voz dos avisos de atraso",19,WHITE));
        l.addView(text("O aviso fala até 3 vezes e pode ser parado pela notificação.",11,MUTED),lp(8));
        Spinner repeat=new Spinner(this);repeat.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"1 vez","2 vezes","3 vezes"}));repeat.setSelection(Math.max(0,Math.min(2,p.getInt("repeat",3)-1)));l.addView(repeat,lp(8));
        Spinner voice=new Spinner(this);ArrayList<String> voiceNames=new ArrayList<>();ArrayList<String> voiceIds=new ArrayList<>();voiceNames.add("Voz padrão do Android");voiceIds.add("");l.addView(voice,lp(8));
        SeekBar volume=new SeekBar(this);volume.setMax(100);volume.setProgress((int)(p.getFloat("volume",1f)*100));l.addView(text("Volume do aviso",11,MUTED),lp(8));l.addView(volume);
        Button save=action("✓ SALVAR CONFIGURAÇÃO");save.setTextColor(BG);save.setBackground(bg(GREEN,14));l.addView(save,lp(12));
        final TextToSpeech[] temp=new TextToSpeech[1];
        temp[0]=new TextToSpeech(this,status->{if(status==TextToSpeech.SUCCESS&&Build.VERSION.SDK_INT>=21){try{for(android.speech.tts.Voice v:temp[0].getVoices()){if(v.getLocale()!=null&&"pt".equals(v.getLocale().getLanguage())){voiceIds.add(v.getName());voiceNames.add(v.getName());}}ArrayAdapter<String> va=new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,voiceNames);voice.setAdapter(va);String saved=p.getString("voice_name","");int pos=voiceIds.indexOf(saved);if(pos>=0)voice.setSelection(pos);}catch(Exception ignored){}}});
        Dialog d=new Dialog(this);d.setContentView(l);d.show();if(d.getWindow()!=null)d.getWindow().setLayout((int)(getResources().getDisplayMetrics().widthPixels*.92),-2);
        save.setOnClickListener(v->{p.edit().putInt("repeat",repeat.getSelectedItemPosition()+1).putFloat("volume",volume.getProgress()/100f).putString("voice_name",voiceIds.get(Math.max(0,voice.getSelectedItemPosition()))).apply();try{if(temp[0]!=null)temp[0].shutdown();}catch(Exception ignored){}d.dismiss();});
    }

    void themeDialog(){
        String[] names={"Verde","Azul","Roxo","Laranja","Vermelho","Ciano","Personalizada"};
        String[] colors={"#25D366","#4F8CFF","#9B59FF","#FF9F43","#FF5C5C","#20C7C9",""};
        new AlertDialog.Builder(this).setTitle("🎨 Cor do aplicativo").setItems(names,(d,w)->{
            if(w<6){db.setting("theme_color",colors[w]);GREEN=Color.parseColor(colors[w]);build();}
            else{
                EditText e=input("HEX (ex.: #FF4D8D)");e.setText("#25D366");
                new AlertDialog.Builder(this).setTitle("Cor personalizada").setView(e).setNegativeButton("CANCELAR",null).setPositiveButton("SALVAR",(x,z)->{try{String h=e.getText().toString().trim();GREEN=Color.parseColor(h);db.setting("theme_color",h);build();}catch(Exception ignored){Toast.makeText(this,"Cor inválida.",Toast.LENGTH_SHORT).show();}}).show();
            }
        }).show();
    }

    File dataFolder(){
        File base=getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);
        File folder=new File(base,"ControleArtesVIP");
        if(!folder.exists())folder.mkdirs();
        return folder;
    }
    void syncDataFolder(){
        try{
            File folder=dataFolder();
            File json=new File(folder,"banco_local.json");
            OutputStream out=new FileOutputStream(json);
            out.write(db.exportJson().toString(2).getBytes("UTF-8"));out.close();
            File info=new File(folder,"LEIA-ME.txt");
            OutputStream inf=new FileOutputStream(info);
            inf.write("Controle de Artes VIP - dados locais e backups do aplicativo.".getBytes("UTF-8"));inf.close();
        }catch(Exception ignored){}
    }
    void generatePdf(String onlyCompany){
        try{
            File folder=dataFolder();String name=onlyCompany==null?"relatorio_geral":("cliente_"+onlyCompany.replaceAll("[^a-zA-Z0-9À-ÿ]+","_"));
            File file=new File(folder,name+"_"+System.currentTimeMillis()+".pdf");
            PdfDocument doc=new PdfDocument();int pageNo=1;
            PdfDocument.Page page=doc.startPage(new PdfDocument.PageInfo.Builder(595,842,pageNo).create());Canvas canvas=page.getCanvas();
            double total=0,received=0,receivable=0,cost=0;ArrayList<Art> arts=new ArrayList<>();Cursor cur=onlyCompany==null?db.arts():db.clientArts(onlyCompany);
            try{while(cur.moveToNext()){Art a=art(cur);arts.add(a);total+=a.price;cost+=a.cost;received+=a.paidAmount;receivable+=Math.max(0,a.price-a.paidAmount);}}finally{cur.close();}
            int accent=GREEN;Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);p.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            canvas.drawColor(Color.rgb(247,249,252));
            p.setColor(accent);canvas.drawRect(0,0,595,110,p);
            p.setColor(Color.WHITE);p.setTextSize(25);canvas.drawText("CONTROLE DE ARTES",36,43,p);p.setTypeface(android.graphics.Typeface.DEFAULT);p.setTextSize(11);canvas.drawText("RELATÓRIO FINANCEIRO • USO PESSOAL",36,66,p);
            p.setTextSize(10);canvas.drawText(new java.text.SimpleDateFormat("dd/MM/yyyy • HH:mm",Locale.getDefault()).format(new Date()),36,88,p);
            p.setColor(Color.rgb(30,36,45));p.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);p.setTextSize(19);canvas.drawText(onlyCompany==null?"Resumo geral":onlyCompany,36,145,p);
            p.setTypeface(android.graphics.Typeface.DEFAULT);p.setTextSize(10);p.setColor(Color.rgb(100,110,122));canvas.drawText(arts.size()+" demanda(s) registrada(s)",36,164,p);
            // summary cards
            float[] xs={36,180,324,468};String[] labels={"FATURAMENTO","RECEBIDO","A RECEBER","CUSTOS"};double[] vals={total,received,receivable,cost};
            for(int i=0;i<4;i++){float x=xs[i];p.setColor(Color.WHITE);canvas.drawRoundRect(new RectF(x,185,x+112,255),12,12,p);p.setColor(Color.rgb(100,110,122));p.setTextSize(8);p.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);canvas.drawText(labels[i],x+10,205,p);p.setColor(i==2?Color.rgb(220,150,20):(i==3?Color.rgb(60,130,220):accent));p.setTextSize(12);canvas.drawText("R$ "+money(vals[i]),x+10,229,p);}
            float y=285;
            p.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);p.setTextSize(12);p.setColor(Color.rgb(30,36,45));canvas.drawText("DEMANDAS",36,y,p);y+=18;
            for(Art a:arts){
                if(y>735){p.setColor(Color.rgb(100,110,122));p.setTextSize(8);canvas.drawText("Controle de Artes • página "+pageNo,36,815,p);doc.finishPage(page);pageNo++;page=doc.startPage(new PdfDocument.PageInfo.Builder(595,842,pageNo).create());canvas=page.getCanvas();canvas.drawColor(Color.rgb(247,249,252));p.setColor(accent);canvas.drawRect(0,0,595,70,p);p.setColor(Color.WHITE);p.setTextSize(16);canvas.drawText("CONTROLE DE ARTES",36,42,p);y=100;}
                p.setColor(Color.WHITE);canvas.drawRoundRect(new RectF(36,y,559,y+76),12,12,p);
                p.setColor(Color.rgb(30,36,45));p.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);p.setTextSize(11);
                String title=(a.desc.isEmpty()?(a.service.isEmpty()?"Arte":a.service):a.desc);canvas.drawText((a.company+" • "+title).length()>66?(a.company+" • "+title).substring(0,66):(a.company+" • "+title),50,y+22,p);
                p.setTypeface(android.graphics.Typeface.DEFAULT);p.setTextSize(9);p.setColor(Color.rgb(100,110,122));canvas.drawText("Status: "+a.status,50,y+42,p);canvas.drawText("Valor R$ "+money(a.price)+"   •   Custo R$ "+money(a.cost)+"   •   Resultado R$ "+money(a.price-a.cost),50,y+58,p);
                y+=88;
            }
            p.setColor(Color.rgb(30,36,45));p.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);p.setTextSize(12);canvas.drawText("RESULTADO",36,y+5,p);p.setTextSize(10);canvas.drawText("Lucro estimado: R$ "+money(total-cost)+"   •   Margem: "+(total>0?money((total-cost)*100/total):"0,00")+"%",36,y+24,p);
            p.setColor(Color.rgb(100,110,122));p.setTypeface(android.graphics.Typeface.DEFAULT);p.setTextSize(8);canvas.drawText("Controle de Artes VIP • Relatório interno • "+new java.text.SimpleDateFormat("dd/MM/yyyy",Locale.getDefault()).format(new Date()),36,815,p);
            doc.finishPage(page);doc.writeTo(new FileOutputStream(file));doc.close();sharePdf(file);
        }catch(Exception e){Toast.makeText(this,"Não foi possível gerar o PDF.",Toast.LENGTH_LONG).show();}
    }
    void sharePdf(File file){
        try{
            Uri uri=androidx.core.content.FileProvider.getUriForFile(this,getPackageName()+".fileprovider",file);
            Intent i=new Intent(Intent.ACTION_SEND);i.setType("application/pdf");i.putExtra(Intent.EXTRA_STREAM,uri);i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(i,"Enviar PDF"));
        }catch(Exception e){Toast.makeText(this,"PDF salvo na pasta do app.",Toast.LENGTH_LONG).show();}
    }

    @Override protected void onActivityResult(int r,int c,Intent data){
        super.onActivityResult(r,c,data);
        if(c==RESULT_OK&&data!=null){
            if(r==101){
                pendingImages.clear();
                try{
                    if(data.getClipData()!=null){
                        for(int k=0;k<data.getClipData().getItemCount();k++){
                            Uri u=data.getClipData().getItemAt(k).getUri();pendingImages.add(u);
                            try{getContentResolver().takePersistableUriPermission(u,data.getFlags()&Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(Exception ignored){}
                        }
                    }else if(data.getData()!=null){
                        Uri u=data.getData();pendingImages.add(u);
                        try{getContentResolver().takePersistableUriPermission(u,data.getFlags()&Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(Exception ignored){}
                    }
                    Toast.makeText(this,pendingImages.size()+" foto(s) selecionada(s)",Toast.LENGTH_SHORT).show();
                }catch(Exception ignored){}
            }else if(r==202)writeBackup(data.getData());
            else if(r==203)new AlertDialog.Builder(this).setTitle("Restaurar backup?").setMessage("Isso substituirá os dados atuais do aplicativo.").setNegativeButton("CANCELAR",null).setPositiveButton("RESTAURAR",(d,w)->readBackup(data.getData())).show();
        }
    }

    void migrateLegacy(){
        SharedPreferences old=getSharedPreferences("dados",0);
        if(old.getBoolean("migrated_sqlite",false))return;
        try{
            JSONArray a=new JSONArray(old.getString("items","[]"));
            for(int i=0;i<a.length();i++){JSONObject o=a.getJSONObject(i);db.addCompany(o.optString("cliente"),"");db.addArt(db.addCompany(o.optString("cliente"),""),o.optString("cliente"),"", "",o.optString("desc"),num(o.optString("valor")),0,o.optString("status","A receber"),o.optString("foto"));}
        }catch(Exception ignored){}
        old.edit().putBoolean("migrated_sqlite",true).apply();
    }
}
