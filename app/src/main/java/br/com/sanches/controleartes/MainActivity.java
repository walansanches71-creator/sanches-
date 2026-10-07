package br.com.sanches.controleartes;

import android.app.*;
import android.os.*;
import android.content.*;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.pdf.PdfDocument;
import android.graphics.Paint;
import android.graphics.Canvas;
import android.os.Environment;
import android.net.Uri;
import android.database.Cursor;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import java.util.*;
import java.io.*;
import org.json.*;

public class MainActivity extends Activity {
    LinearLayout root,list;
    TextView total,receber,pago,atrasado,custos,lucro,empty;
    LocalDB db;
    Uri pendingImage;
    ArrayList<Uri> pendingImages=new ArrayList<>();
    final int BG=Color.rgb(8,12,17), SURFACE=Color.rgb(17,23,31), SURFACE2=Color.rgb(22,29,38);
    int GREEN=Color.rgb(37,211,102); final int WHITE=Color.rgb(245,247,250), MUTED=Color.rgb(151,163,176);
    final int RED=Color.rgb(255,88,88), YELLOW=Color.rgb(255,190,70), BLUE=Color.rgb(90,170,255);

    static class Art { long id; String company,phone,service,desc,status,photo; double price,cost; }
    static class Company { long id; String name,phone; }
    static class Service { long id; String name; double cost,price; }

    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        getWindow().setStatusBarColor(BG); getWindow().setNavigationBarColor(BG);
        db=new LocalDB(this);
        loadThemeColor();
        migrateLegacy();
        syncDataFolder();
        build();
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
        list.removeAllViews();double rec=0,pag=0,atr=0,cst=0;
        Cursor c=db.arts();
        try{
            while(c.moveToNext()){
                Art a=art(c);double n=a.price;cst+=a.cost;
                if("A receber".equals(a.status)||"Arte entregue".equals(a.status))rec+=n;
                if("Pago".equals(a.status))pag+=n;
                if(a.status.contains("atras"))atr+=n;
                card(a);
            }
        }finally{c.close();}
        total.setText("R$ "+money(rec+pag));receber.setText("R$ "+money(rec));pago.setText("R$ "+money(pag));atrasado.setText("R$ "+money(atr));custos.setText("R$ "+money(cst));lucro.setText("R$ "+money((rec+pag)-cst));
        if(list.getChildCount()==0){empty=text("Nenhuma arte cadastrada\n\nToque em “＋ NOVA ARTE” para começar.",15,MUTED);empty.setGravity(Gravity.CENTER);empty.setPadding(0,dp(40),0,dp(40));list.addView(empty);}
    }

    Art art(Cursor c){
        Art a=new Art();a.id=c.getLong(c.getColumnIndexOrThrow("id"));a.company=c.getString(c.getColumnIndexOrThrow("company"));a.phone=c.getString(c.getColumnIndexOrThrow("phone"));
        a.service=c.getString(c.getColumnIndexOrThrow("service"));a.desc=c.getString(c.getColumnIndexOrThrow("description"));a.price=c.getDouble(c.getColumnIndexOrThrow("price"));
        a.cost=c.getDouble(c.getColumnIndexOrThrow("cost"));a.status=c.getString(c.getColumnIndexOrThrow("status"));a.photo=c.getString(c.getColumnIndexOrThrow("photo"));return a;
    }

    void card(final Art a){
        LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);c.setPadding(dp(14),dp(13),dp(14),dp(12));c.setBackground(bg(SURFACE,18));
        LinearLayout head=new LinearLayout(this);head.setGravity(Gravity.CENTER_VERTICAL);
        ImageView img=new ImageView(this);img.setScaleType(ImageView.ScaleType.CENTER_CROP);
        if(!a.photo.isEmpty())try{img.setImageURI(Uri.parse(a.photo));}catch(Exception ignored){}else img.setBackground(bg(SURFACE2,12));
        head.addView(img,new LinearLayout.LayoutParams(dp(62),dp(62)));
        LinearLayout info=new LinearLayout(this);info.setOrientation(LinearLayout.VERTICAL);info.setPadding(dp(12),0,0,0);
        TextView cli=text(a.company.isEmpty()?"Sem cliente":a.company,17,WHITE);cli.setTypeface(null,1);
        info.addView(cli);info.addView(text((a.service.isEmpty()?"Arte":a.service)+" • "+(a.desc.isEmpty()?"":a.desc),12,MUTED));
        TextView val=text("R$ "+money(a.price)+"   •   custo R$ "+money(a.cost),15,GREEN);val.setTypeface(null,1);info.addView(val);
        head.addView(info,new LinearLayout.LayoutParams(0,-2,1));c.addView(head);
        TextView chip=text("●  "+a.status,11,statusColor(a.status));chip.setTypeface(null,1);chip.setPadding(dp(10),dp(6),dp(10),dp(6));chip.setBackground(bg(statusColor(a.status),18));chip.setTextColor(BG);
        LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-2,dp(30));cp.setMargins(0,dp(10),0,dp(8));c.addView(chip,cp);
        LinearLayout b=new LinearLayout(this);Button st=action("STATUS"),z=action("💬 COBRAR"),e=action("✎ EDITAR"),del=action("🗑 APAGAR");
        b.addView(st,new LinearLayout.LayoutParams(0,dp(42),1));b.addView(z,new LinearLayout.LayoutParams(0,dp(42),1));b.addView(e,new LinearLayout.LayoutParams(0,dp(42),1));b.addView(del,new LinearLayout.LayoutParams(0,dp(42),1));c.addView(b);
        st.setOnClickListener(v->status(a));z.setOnClickListener(v->whats(a));e.setOnClickListener(v->form(a,null,null));del.setOnClickListener(v->deleteArt(a));
        list.addView(c);list.addView(new Space(this),new LinearLayout.LayoutParams(1,dp(10)));
    }

    int statusColor(String s){if("Pago".equals(s))return GREEN;if(s.contains("atras"))return RED;if("Arte entregue".equals(s))return BLUE;return YELLOW;}
    void status(final Art a){
        String[] arr={"A receber","Arte entregue","Pago","Arte atrasada","Pagamento atrasado"};
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

        Spinner serviceSpinner=new Spinner(this);ArrayList<Service> services=new ArrayList<>();services.add(blankService());Cursor sc=db.services();try{while(sc.moveToNext()){Service s=new Service();s.id=sc.getLong(0);s.name=sc.getString(1);s.cost=sc.getDouble(2);s.price=sc.getDouble(3);services.add(s);}}finally{sc.close();}
        ArrayList<String> names=new ArrayList<>();for(Service s:services)names.add(s.name);ArrayAdapter<String> sa=new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,names);serviceSpinner.setAdapter(sa);
        l.addView(serviceSpinner,lp(8));
        final double[] selectedPrice={old==null?0:old.price};EditText cost=input("Custo da arte (R$)");cost.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);
        l.addView(cost,lp(8));

        Button photo=action("📷  ADICIONAR / TROCAR FOTOS");TextView photoInfo=text("Nenhuma foto selecionada",11,MUTED);photoInfo.setPadding(dp(4),dp(5),0,0);l.addView(photo,lp(10));l.addView(photoInfo);
        Button save=action("✓  SALVAR ARTE");save.setTextColor(BG);save.setTypeface(null,1);save.setBackground(bg(GREEN,14));l.addView(save,lp(12));

        if(old!=null){
            company.setText(old.company);phone.setText(old.phone);desc.setText(old.desc);cost.setText(money(old.cost));if(!old.photo.isEmpty())photoInfo.setText("✓ Foto já vinculada");
            for(int i=0;i<services.size();i++)if(services.get(i).name.equals(old.service)){serviceSpinner.setSelection(i);selectedPrice[0]=old.price;}
            for(int i=0;i<clients.size();i++)if(clients.get(i).name.equals(old.company))clientSpinner.setSelection(i);
        }
        clientSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){public void onNothingSelected(android.widget.AdapterView<?> p){} public void onItemSelected(android.widget.AdapterView<?> p,View v,int pos,long id){if(!clients.isEmpty()&&pos<clients.size()){Company x=clients.get(pos);company.setText(x.name);phone.setText(x.phone);}}});
                serviceSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
            public void onNothingSelected(android.widget.AdapterView<?> p){}
            public void onItemSelected(android.widget.AdapterView<?> p,View v,int pos,long id){Service s=services.get(pos);if(s.id>0){selectedPrice[0]=s.price;cost.setText(money(s.cost));}}
        });
        photo.setOnClickListener(q->{Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("image/*");i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);i.addCategory(Intent.CATEGORY_OPENABLE);i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);startActivityForResult(i,101);});
        save.setOnClickListener(q->{
            String co=company.getText().toString().trim();if(co.isEmpty()){company.setError("Informe o cliente");return;}
            String ph=phone.getText().toString().trim();String sv=serviceSpinner.getSelectedItem().toString();String ct=cost.getText().toString().trim();
            String photoValue=old==null?"":old.photo;if(!pendingImages.isEmpty()){JSONArray pa=new JSONArray();for(Uri u:pendingImages)pa.put(u.toString());photoValue=pa.toString();pendingImages.clear();pendingImage=null;}
            long clientId=db.addCompany(co,ph);
            if(old==null)db.addArt(clientId,co,ph,sv,desc.getText().toString().trim(),selectedPrice[0],num(ct),"A receber",photoValue);
            else db.updateArt(old.id,clientId,co,ph,sv,desc.getText().toString().trim(),selectedPrice[0],num(ct),old.status,photoValue);
            refresh();syncDataFolder();d.dismiss();
        });
        if(presetCompany!=null&&old==null){company.setText(presetCompany);phone.setText(presetPhone==null?"":presetPhone);for(int i=0;i<clients.size();i++)if(clients.get(i).name.equals(presetCompany))clientSpinner.setSelection(i);}
        d.setContentView(l);d.show();if(d.getWindow()!=null){d.getWindow().setBackgroundDrawableResource(android.R.color.transparent);d.getWindow().setLayout((int)(getResources().getDisplayMetrics().widthPixels*.94),-2);}
    }
    Service blankService(){Service s=new Service();s.name="— Sem tabela de preço —";return s;}

    void menu(){
        final String[] a={"👥  Clientes","💰  Tabela de preços","💳  Configurar PIX","📊  Resumo financeiro","🎨  Personalizar cor","📄  Gerar PDF","💾  Backup / Restaurar"};
        new AlertDialog.Builder(this).setTitle("Controle de Artes").setItems(a,(d,w)->{if(w==0)companiesDialog();if(w==1)servicesDialog();if(w==2)pixDialog();if(w==3)summaryDialog();if(w==4)themeDialog();if(w==5)generatePdf(null);if(w==6)backupMenu();}).show();
    }

    void servicesDialog(){
        LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);l.setPadding(dp(18),dp(14),dp(18),dp(10));
        Button add=action("＋ NOVO SERVIÇO");l.addView(add);
        LinearLayout listS=new LinearLayout(this);listS.setOrientation(LinearLayout.VERTICAL);ScrollView sv=new ScrollView(this);sv.addView(listS);l.addView(sv,new LinearLayout.LayoutParams(-1,0,1));
        final Dialog d=new Dialog(this);d.setTitle("Tabela de preços");d.setContentView(l);d.show();if(d.getWindow()!=null)d.getWindow().setLayout((int)(getResources().getDisplayMetrics().widthPixels*.94),dp(520));
        final Runnable[] reload=new Runnable[1];reload[0]=()->{listS.removeAllViews();Cursor c=db.services();try{while(c.moveToNext()){long id=c.getLong(0);String n=c.getString(1);double co=c.getDouble(2),pr=c.getDouble(3);LinearLayout row=new LinearLayout(this);row.setPadding(0,dp(7),0,dp(7));LinearLayout tx=new LinearLayout(this);tx.setOrientation(LinearLayout.VERTICAL);tx.addView(text(n,15,WHITE));tx.addView(text("Custo R$ "+money(co)+"  •  Venda R$ "+money(pr)+"  •  Margem R$ "+money(pr-co),11,MUTED));row.addView(tx,new LinearLayout.LayoutParams(0,-2,1));Button del=action("🗑");row.addView(del,new LinearLayout.LayoutParams(dp(52),dp(42)));del.setOnClickListener(v->{db.deleteService(id);reload[0].run();syncDataFolder();});listS.addView(row);}}finally{c.close();}};
        add.setOnClickListener(v->serviceForm(reload[0]));reload[0].run();
    }

    void serviceForm(Runnable reload){
        LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);l.setPadding(dp(20),dp(20),dp(20),dp(15));
        EditText n=input("Nome do serviço (ex.: Criação de arte)");EditText pr=input("Valor (R$)");pr.setInputType(2|8192);
        l.addView(n);l.addView(pr,lp(8));Button save=action("SALVAR TABELA");save.setTextColor(BG);save.setBackground(bg(GREEN,14));l.addView(save,lp(12));
        Dialog d=new Dialog(this);d.setContentView(l);d.show();if(d.getWindow()!=null){d.getWindow().setBackgroundDrawableResource(android.R.color.transparent);d.getWindow().setLayout((int)(getResources().getDisplayMetrics().widthPixels*.9),-2);}
        save.setOnClickListener(v->{if(n.getText().toString().trim().isEmpty())return;db.addService(n.getText().toString(),0,num(pr.getText().toString()));d.dismiss();reload.run();syncDataFolder();});
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
        try{while(cur.moveToNext()){Art a=art(cur);if(company.equalsIgnoreCase(a.company)&&!"Pago".equals(a.status)){open.add(a);total+=a.price;cost+=a.cost;}}}finally{cur.close();}
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
        LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);l.setPadding(dp(20),dp(20),dp(20),dp(15));EditText n=input("Nome da empresa");EditText p=input("WhatsApp (opcional)");l.addView(n);l.addView(p,lp(8));
        Button s=action("SALVAR EMPRESA");s.setTextColor(BG);s.setBackground(bg(GREEN,14));l.addView(s,lp(12));Dialog d=new Dialog(this);d.setContentView(l);d.show();if(d.getWindow()!=null){d.getWindow().setBackgroundDrawableResource(android.R.color.transparent);d.getWindow().setLayout((int)(getResources().getDisplayMetrics().widthPixels*.9),-2);}
        s.setOnClickListener(v->{if(n.getText().toString().trim().isEmpty())return;db.addCompany(n.getText().toString(),p.getText().toString());d.dismiss();reload.run();syncDataFolder();});
    }

    void pixDialog(){
        LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);l.setPadding(dp(20),dp(20),dp(20),dp(15));
        l.addView(text("PIX para cobranças",20,WHITE));l.addView(text("Esses dados serão incluídos automaticamente na mensagem.",12,MUTED));
        EditText key=input("Chave PIX (CPF, CNPJ, e-mail, telefone ou aleatória)");EditText name=input("Nome do recebedor");key.setText(db.setting("pix"));name.setText(db.setting("pix_name"));l.addView(key,lp(12));l.addView(name,lp(8));
        Button s=action("✓ SALVAR PIX");s.setTextColor(BG);s.setBackground(bg(GREEN,14));l.addView(s,lp(12));Dialog d=new Dialog(this);d.setContentView(l);d.show();if(d.getWindow()!=null){d.getWindow().setBackgroundDrawableResource(android.R.color.transparent);d.getWindow().setLayout((int)(getResources().getDisplayMetrics().widthPixels*.92),-2);}
        s.setOnClickListener(v->{db.setting("pix",key.getText().toString().trim());db.setting("pix_name",name.getText().toString().trim());syncDataFolder();d.dismiss();});
    }

    void summaryDialog(){
        double revenue=0,c=0,paid=0,receivable=0;Cursor cur=db.arts();try{while(cur.moveToNext()){double p=cur.getDouble(cur.getColumnIndexOrThrow("price")),co=cur.getDouble(cur.getColumnIndexOrThrow("cost"));revenue+=p;c+=co;if("Pago".equals(cur.getString(cur.getColumnIndexOrThrow("status"))))paid+=p;else receivable+=p;}}finally{cur.close();}
        new AlertDialog.Builder(this).setTitle("Resumo financeiro").setMessage("Faturamento: R$ "+money(revenue)+"\nCustos: R$ "+money(c)+"\nLucro estimado: R$ "+money(revenue-c)+"\n\nRecebido: R$ "+money(paid)+"\nA receber: R$ "+money(receivable)+"\n\nPIX cadastrado: "+(db.setting("pix").isEmpty()?"não":"sim")).setPositiveButton("OK",null).show();
    }

    void whats(Art a){
        String pix=db.setting("pix");String pixName=db.setting("pix_name");
        String msg="Olá, "+(a.company.isEmpty()?"cliente":a.company)+"! 👋\n\nPassando para lembrar do pagamento da arte: "+(a.desc.isEmpty()?(a.service.isEmpty()?"arte":a.service):a.desc)+".\nValor: R$ "+money(a.price);
        if(!pix.isEmpty()){msg+="\n\n💳 PIX: "+pix;if(!pixName.isEmpty())msg+="\nRecebedor: "+pixName;}
        msg+="\n\nQuando puder, me envie o pagamento. Obrigado!";
        Intent i=new Intent(Intent.ACTION_SEND);String photo=a.photo;
        if(!photo.isEmpty()){Uri u=Uri.parse(photo);i.setType("image/*");i.putExtra(Intent.EXTRA_STREAM,u);i.putExtra(Intent.EXTRA_TEXT,msg);i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);i.setClipData(ClipData.newRawUri("foto",u));}
        else{i.setType("text/plain");i.putExtra(Intent.EXTRA_TEXT,msg);}
        try{startActivity(Intent.createChooser(i,"Enviar cobrança"));}catch(Exception ignored){}
    }

    ArrayList<Uri> photoUris(String value){
        ArrayList<Uri> out=new ArrayList<>();if(value==null||value.isEmpty())return out;
        try{if(value.trim().startsWith("[")){JSONArray a=new JSONArray(value);for(int i=0;i<a.length();i++)out.add(Uri.parse(a.getString(i)));}else out.add(Uri.parse(value));}catch(Exception ignored){}return out;
    }
    Uri firstPhoto(String value){ArrayList<Uri> p=photoUris(value);return p.isEmpty()?null:p.get(0);}
    void loadThemeColor(){try{String h=db.setting("theme_color");if(!h.isEmpty())GREEN=Color.parseColor(h);}catch(Exception ignored){}}
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
            File folder=dataFolder();
            String name=onlyCompany==null?"relatorio_geral":("cliente_"+onlyCompany.replaceAll("[^a-zA-Z0-9À-ÿ]+","_"));
            File file=new File(folder,name+"_"+System.currentTimeMillis()+".pdf");
            PdfDocument doc=new PdfDocument();int pageNo=1;PdfDocument.Page page=doc.startPage(new PdfDocument.PageInfo.Builder(595,842,pageNo).create());
            Canvas canvas=page.getCanvas();Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);p.setColor(Color.rgb(25,30,38));p.setTextSize(22);p.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            canvas.drawText(onlyCompany==null?"CONTROLE DE ARTES":"DEMANDAS • "+onlyCompany,36,48,p);
            p.setTypeface(android.graphics.Typeface.DEFAULT);p.setTextSize(11);p.setColor(Color.DKGRAY);
            canvas.drawText("Relatório gerado em "+new java.text.SimpleDateFormat("dd/MM/yyyy HH:mm",Locale.getDefault()).format(new Date()),36,68,p);
            float y=100;double total=0,received=0,receivable=0,cost=0;Cursor cur=onlyCompany==null?db.arts():db.clientArts(onlyCompany);
            try{
                while(cur.moveToNext()){
                    Art a=art(cur);if(y>790){doc.finishPage(page);pageNo++;page=doc.startPage(new PdfDocument.PageInfo.Builder(595,842,pageNo).create());canvas=page.getCanvas();y=50;}
                    p.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);p.setTextSize(12);p.setColor(Color.rgb(20,25,30));
                    String title=(a.company+" • "+(a.desc.isEmpty()?(a.service.isEmpty()?"Arte":a.service):a.desc));
                    canvas.drawText(title.length()>72?title.substring(0,72):title,36,y,p);y+=17;
                    p.setTypeface(android.graphics.Typeface.DEFAULT);p.setTextSize(10);
                    canvas.drawText("Valor: R$ "+money(a.price)+"   Custo: R$ "+money(a.cost)+"   Status: "+a.status,36,y,p);y+=22;
                    total+=a.price;cost+=a.cost;if("Pago".equals(a.status))received+=a.price;else receivable+=a.price;
                }
            }finally{cur.close();}
            p.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);p.setTextSize(12);y+=10;
            canvas.drawText("TOTAL: R$ "+money(total)+"   RECEBIDO: R$ "+money(received)+"   A RECEBER: R$ "+money(receivable),36,y,p);y+=18;
            canvas.drawText("CUSTOS: R$ "+money(cost)+"   LUCRO ESTIMADO: R$ "+money(total-cost),36,y,p);
            doc.finishPage(page);doc.writeTo(new FileOutputStream(file));doc.close();
            sharePdf(file);
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
        if(c==RESULT_OK&&data!=null){if(r==101){pendingImage=data.getData();try{getContentResolver().takePersistableUriPermission(pendingImage,data.getFlags()&Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(Exception ignored){}}else if(r==202)writeBackup(data.getData());else if(r==203)new AlertDialog.Builder(this).setTitle("Restaurar backup?").setMessage("Isso substituirá os dados atuais do aplicativo.").setNegativeButton("CANCELAR",null).setPositiveButton("RESTAURAR",(d,w)->readBackup(data.getData())).show();}
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
