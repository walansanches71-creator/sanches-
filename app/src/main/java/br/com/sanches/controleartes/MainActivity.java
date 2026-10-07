package br.com.sanches.controleartes;

import android.app.*;
import android.os.*;
import android.content.*;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import java.util.*;
import org.json.*;

public class MainActivity extends Activity {
    LinearLayout root, list;
    TextView total, receber, pago, atrasado, empty;
    ArrayList<JSONObject> items = new ArrayList<>();
    SharedPreferences sp;
    Uri pendingImage;
    final int BG=Color.rgb(8,12,17), SURFACE=Color.rgb(17,23,31), SURFACE2=Color.rgb(22,29,38);
    final int GREEN=Color.rgb(37,211,102), WHITE=Color.rgb(245,247,250), MUTED=Color.rgb(151,163,176);
    final int RED=Color.rgb(255,88,88), YELLOW=Color.rgb(255,190,70);

    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        sp=getSharedPreferences("dados",0);
        load();
        build();
    }

    int dp(int n){return (int)(n*getResources().getDisplayMetrics().density+0.5f);}
    TextView text(String s,float size,int color){
        TextView t=new TextView(this); t.setText(s); t.setTextSize(size); t.setTextColor(color);
        t.setFontFeatureSettings("kern"); return t;
    }
    GradientDrawable bg(int color,float radius){
        GradientDrawable g=new GradientDrawable(); g.setColor(color); g.setCornerRadius(dp((int)radius)); return g;
    }
    GradientDrawable strokeBg(int color,int stroke,float radius){
        GradientDrawable g=bg(color,radius); g.setStroke(dp(1),stroke); return g;
    }
    Button action(String s){
        Button b=new Button(this); b.setText(s); b.setTextColor(WHITE); b.setTextSize(12); b.setAllCaps(false);
        b.setPadding(dp(8),0,dp(8),0); b.setMinHeight(dp(42)); b.setMinWidth(0); b.setBackground(bg(SURFACE2,12));
        return b;
    }
    void margin(View v,int l,int t,int r,int bottom){
        ViewGroup.MarginLayoutParams p=(ViewGroup.MarginLayoutParams)v.getLayoutParams();
        if(p!=null){p.setMargins(dp(l),dp(t),dp(r),dp(bottom));v.setLayoutParams(p);}
    }

    void build(){
        root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(dp(16),dp(12),dp(16),0); root.setBackgroundColor(BG);

        LinearLayout top=new LinearLayout(this); top.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout titles=new LinearLayout(this); titles.setOrientation(LinearLayout.VERTICAL);
        TextView brand=text("CONTROLE DE ARTES",22,WHITE); brand.setTypeface(null,1);
        TextView sub=text("Financeiro simples para suas artes",12,MUTED);
        titles.addView(brand); titles.addView(sub);
        top.addView(titles,new LinearLayout.LayoutParams(0,-2,1));
        TextView logo=text("✦",27,GREEN); logo.setGravity(Gravity.CENTER); logo.setBackground(bg(SURFACE,18));
        top.addView(logo,new LinearLayout.LayoutParams(dp(48),dp(48)));
        root.addView(top,new LinearLayout.LayoutParams(-1,dp(58)));

        LinearLayout dash=new LinearLayout(this); dash.setOrientation(LinearLayout.VERTICAL); dash.setPadding(0,dp(10),0,dp(6));
        LinearLayout row1=new LinearLayout(this), row2=new LinearLayout(this);
        row1.setOrientation(LinearLayout.HORIZONTAL); row2.setOrientation(LinearLayout.HORIZONTAL);
        total=metric(row1,"TOTAL","R$ 0,00",WHITE);
        receber=metric(row1,"A RECEBER","R$ 0,00",YELLOW);
        pago=metric(row2,"RECEBIDO","R$ 0,00",GREEN);
        atrasado=metric(row2,"ATRASADO","R$ 0,00",RED);
        dash.addView(row1,new LinearLayout.LayoutParams(-1,dp(88)));
        dash.addView(row2,new LinearLayout.LayoutParams(-1,dp(88)));
        root.addView(dash);

        Button add=action("＋  NOVA ARTE");
        add.setTextSize(15); add.setTypeface(null,1); add.setTextColor(BG); add.setBackground(bg(GREEN,16));
        add.setOnClickListener(v->form(null));
        root.addView(add,new LinearLayout.LayoutParams(-1,dp(52)));

        TextView section=text("MINHAS ARTES",13,MUTED); section.setTypeface(null,1);
        section.setPadding(dp(3),dp(16),0,dp(8)); root.addView(section,new LinearLayout.LayoutParams(-1,dp(42)));

        ScrollView sv=new ScrollView(this); sv.setFillViewport(true);
        list=new LinearLayout(this); list.setOrientation(LinearLayout.VERTICAL); list.setPadding(0,0,0,dp(20));
        sv.addView(list); root.addView(sv,new LinearLayout.LayoutParams(-1,0,1));
        setContentView(root); refresh();
    }

    TextView metric(LinearLayout row,String label,String value,int color){
        LinearLayout box=new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(dp(14),dp(10),dp(10),dp(8)); box.setBackground(bg(SURFACE,16));
        TextView a=text(label,10,MUTED); a.setTypeface(null,1);
        TextView b=text(value,18,color); b.setTypeface(null,1);
        box.addView(a); box.addView(b);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-1,1); p.setMargins(0,0,dp(6),0); row.addView(box,p);
        return b;
    }

    void form(JSONObject old){
        final Dialog d=new Dialog(this);
        LinearLayout l=new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); l.setPadding(dp(20),dp(20),dp(20),dp(18)); l.setBackground(bg(SURFACE,22));
        TextView title=text(old==null?"Nova arte":"Editar arte",22,WHITE); title.setTypeface(null,1); l.addView(title);
        TextView hint=text("Cadastre o trabalho e acompanhe o pagamento.",12,MUTED); l.addView(hint);

        EditText c=input("Empresa / cliente");
        EditText v=input("Valor (ex.: 10,00)"); v.setInputType(2|8192);
        EditText x=input("Descrição da arte");
        l.addView(c,lp(0,12)); l.addView(v,lp(0,8)); l.addView(x,lp(0,8));

        Button photo=action("📷  ADICIONAR FOTO");
        TextView photoInfo=text("Nenhuma foto selecionada",11,MUTED); photoInfo.setPadding(dp(4),dp(5),0,dp(2));
        l.addView(photo,lp(0,10)); l.addView(photoInfo);

        Button save=action("✓  SALVAR ARTE"); save.setTextColor(BG); save.setTypeface(null,1); save.setBackground(bg(GREEN,14));
        l.addView(save,lp(0,14));

        if(old!=null){
            c.setText(old.optString("cliente")); v.setText(old.optString("valor")); x.setText(old.optString("desc"));
            if(!old.optString("foto").isEmpty()) photoInfo.setText("✓ Foto já vinculada");
        }

        photo.setOnClickListener(q->{
            Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT); i.setType("image/*"); i.addCategory(Intent.CATEGORY_OPENABLE);
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
            startActivityForResult(i,101);
        });
        save.setOnClickListener(q->{
            try{
                JSONObject o=old==null?new JSONObject():old;
                o.put("cliente",c.getText().toString().trim());
                o.put("valor",v.getText().toString().trim());
                o.put("desc",x.getText().toString().trim());
                if(old==null)o.put("status","A receber");
                if(pendingImage!=null){o.put("foto",pendingImage.toString()); pendingImage=null;}
                if(old==null)items.add(o); save(); refresh(); d.dismiss();
            }catch(Exception ignored){}
        });
        d.setContentView(l);
        Window w=d.getWindow(); if(w!=null){w.setBackgroundDrawableResource(android.R.color.transparent); w.setLayout(-1,-2);}
        d.show(); if(d.getWindow()!=null)d.getWindow().setLayout((int)(getResources().getDisplayMetrics().widthPixels*.92),-2);
    }

    EditText input(String hint){
        EditText e=new EditText(this); e.setHint(hint); e.setHintTextColor(MUTED); e.setTextColor(WHITE); e.setTextSize(14);
        e.setSingleLine(true); e.setPadding(dp(14),0,dp(14),0); e.setBackground(strokeBg(SURFACE2,Color.rgb(47,59,73),13)); return e;
    }
    LinearLayout.LayoutParams lp(int l,int t){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(48));p.setMargins(0,dp(t),0,0);return p;}

    @Override protected void onActivityResult(int r,int c,Intent data){
        super.onActivityResult(r,c,data);
        if(r==101&&c==RESULT_OK&&data!=null){
            pendingImage=data.getData();
            try{getContentResolver().takePersistableUriPermission(pendingImage,data.getFlags()&Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(Exception ignored){}
        }
    }

    void refresh(){
        list.removeAllViews(); double rec=0,pag=0,atr=0;
        for(JSONObject o:items){
            double n=num(o.optString("valor")); String st=o.optString("status");
            if(st.equals("A receber")||st.equals("Arte entregue"))rec+=n;
            if(st.equals("Pago"))pag+=n;
            if(st.contains("atras"))atr+=n;
            card(o);
        }
        total.setText("R$ "+money(rec+pag)); receber.setText("R$ "+money(rec)); pago.setText("R$ "+money(pag)); atrasado.setText("R$ "+money(atr));
        if(items.isEmpty()){
            empty=text("Nenhuma arte cadastrada\n\nToque em “＋ NOVA ARTE” para começar.",15,MUTED); empty.setGravity(Gravity.CENTER); empty.setPadding(0,dp(45),0,dp(45)); list.addView(empty);
        }
    }

    void card(JSONObject o){
        LinearLayout c=new LinearLayout(this); c.setOrientation(LinearLayout.VERTICAL); c.setPadding(dp(14),dp(13),dp(14),dp(12)); c.setBackground(bg(SURFACE,18));
        LinearLayout head=new LinearLayout(this); head.setGravity(Gravity.CENTER_VERTICAL);
        ImageView img=new ImageView(this); img.setScaleType(ImageView.ScaleType.CENTER_CROP);
        String photo=o.optString("foto");
        if(!photo.isEmpty())try{img.setImageURI(Uri.parse(photo));}catch(Exception ignored){}
        if(photo.isEmpty())img.setBackground(bg(SURFACE2,12));
        head.addView(img,new LinearLayout.LayoutParams(dp(62),dp(62)));
        LinearLayout info=new LinearLayout(this); info.setOrientation(LinearLayout.VERTICAL); info.setPadding(dp(12),0,0,0);
        TextView cli=text(o.optString("cliente").isEmpty()?"Sem cliente":o.optString("cliente"),17,WHITE);cli.setTypeface(null,1);
        TextView desc=text(o.optString("desc").isEmpty()?"Arte":o.optString("desc"),12,MUTED);
        TextView val=text("R$ "+o.optString("valor","0,00"),16,GREEN);val.setTypeface(null,1);
        info.addView(cli);info.addView(desc);info.addView(val);head.addView(info,new LinearLayout.LayoutParams(0,-2,1));c.addView(head);
        TextView chip=text("●  "+o.optString("status"),11,statusColor(o.optString("status")));chip.setTypeface(null,1);chip.setPadding(dp(10),dp(6),dp(10),dp(6));chip.setBackground(bg(statusColor(o.optString("status")),18));chip.setTextColor(BG);
        LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-2,dp(30));cp.setMargins(0,dp(10),0,dp(8));c.addView(chip,cp);
        LinearLayout b=new LinearLayout(this);b.setGravity(Gravity.CENTER);
        Button st=action("STATUS"), z=action("💬  COBRAR"), e=action("✎  EDITAR");
        b.addView(st,new LinearLayout.LayoutParams(0,dp(42),1));b.addView(z,new LinearLayout.LayoutParams(0,dp(42),1));b.addView(e,new LinearLayout.LayoutParams(0,dp(42),1));
        c.addView(b);
        st.setOnClickListener(v->status(o)); z.setOnClickListener(v->whats(o)); e.setOnClickListener(v->form(o));
        list.addView(c);Space s=new Space(this);list.addView(s,new LinearLayout.LayoutParams(1,dp(10)));
    }

    int statusColor(String s){
        if("Pago".equals(s))return GREEN;
        if(s.contains("atras"))return RED;
        if("Arte entregue".equals(s))return Color.rgb(90,170,255);
        return YELLOW;
    }

    void status(JSONObject o){
        String[] a={"A receber","Arte entregue","Pago","Arte atrasada","Pagamento atrasado"};
        new AlertDialog.Builder(this).setTitle("Alterar status").setItems(a,(d,w)->{try{o.put("status",a[w]);save();refresh();}catch(Exception ignored){}}).show();
    }

    void whats(JSONObject o){
        String msg="Olá, "+o.optString("cliente","cliente")+"! 👋\n\nPassando para lembrar do pagamento da arte: "+(o.optString("desc").isEmpty()?"arte":o.optString("desc"))+".\nValor: R$ "+o.optString("valor","0,00")+"\n\nQuando puder, me envie o pagamento. Obrigado!";
        Intent i=new Intent(Intent.ACTION_SEND);
        String photo=o.optString("foto");
        if(!photo.isEmpty()){
            Uri u=Uri.parse(photo); i.setType("image/*"); i.putExtra(Intent.EXTRA_STREAM,u); i.putExtra(Intent.EXTRA_TEXT,msg);
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            i.setClipData(ClipData.newRawUri("foto",u));
        }else{i.setType("text/plain");i.putExtra(Intent.EXTRA_TEXT,msg);}
        try{startActivity(Intent.createChooser(i,"Enviar cobrança"));}catch(Exception ignored){}
    }

    double num(String s){try{return Double.parseDouble(s.replace(".","").replace(",","."));}catch(Exception e){return 0;}}
    String money(double x){return String.format(Locale.US,"%.2f",x).replace(".",",");}
    void save(){JSONArray a=new JSONArray();for(JSONObject o:items)a.put(o);sp.edit().putString("items",a.toString()).apply();}
    void load(){try{JSONArray a=new JSONArray(sp.getString("items","[]"));for(int i=0;i<a.length();i++)items.add(a.getJSONObject(i));}catch(Exception ignored){}}
}
