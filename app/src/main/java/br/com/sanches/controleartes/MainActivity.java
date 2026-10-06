package br.com.sanches.controleartes;

import android.app.*;import android.os.*;import android.content.*;import android.graphics.Color;import android.net.Uri;import android.provider.MediaStore;import android.view.*;import android.widget.*;import java.util.*;import org.json.*;

public class MainActivity extends Activity{
 LinearLayout root,list; TextView total,receber,pago,atrasado; ArrayList<JSONObject> items=new ArrayList<>(); android.content.SharedPreferences sp; Uri pendingImage;
 int green=Color.rgb(37,211,102), bg=Color.rgb(11,15,20), card=Color.rgb(22,29,38);
 @Override public void onCreate(Bundle b){super.onCreate(b);sp=getSharedPreferences("dados",0);load();build();}
 TextView tv(String s,int z){TextView t=new TextView(this);t.setText(s);t.setTextColor(Color.WHITE);t.setTextSize(z);t.setPadding(18,12,18,12);return t;}
 Button btn(String s){Button b=new Button(this);b.setText(s);return b;}
 void build(){root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(16,16,16,16);root.setBackgroundColor(bg);
  TextView title=tv("💰 CONTROLE DE ARTES VIP",22);title.setTextColor(green);root.addView(title);
  LinearLayout dash=new LinearLayout(this);dash.setOrientation(LinearLayout.VERTICAL);
  total=tv("",18);receber=tv("",18);pago=tv("",18);atrasado=tv("",18);dash.addView(total);dash.addView(receber);dash.addView(pago);dash.addView(atrasado);root.addView(dash);
  Button add=btn("＋ NOVA ARTE");add.setOnClickListener(v->form(null));root.addView(add);
  ScrollView sv=new ScrollView(this);list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);sv.addView(list);root.addView(sv,new LinearLayout.LayoutParams(-1,0,1));setContentView(root);refresh();}
 void form(JSONObject old){final Dialog d=new Dialog(this);LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);l.setPadding(24,20,24,20);l.setBackgroundColor(card);
  EditText cliente=new EditText(this);cliente.setHint("Empresa / cliente");cliente.setTextColor(Color.WHITE);cliente.setHintTextColor(Color.GRAY);
  EditText valor=new EditText(this);valor.setHint("Valor (ex.: 10,00)");valor.setInputType(2|8192);valor.setTextColor(Color.WHITE);
  EditText desc=new EditText(this);desc.setHint("Descrição da arte");desc.setTextColor(Color.WHITE);
  Button foto=btn("📸 Selecionar foto");Button salvar=btn("SALVAR");
  l.addView(tv(old==null?"Nova arte":"Editar arte",20));l.addView(cliente);l.addView(valor);l.addView(desc);l.addView(foto);l.addView(salvar);
  if(old!=null){cliente.setText(old.optString("cliente"));valor.setText(old.optString("valor"));desc.setText(old.optString("desc"));}
  foto.setOnClickListener(v->{Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("image/*");i.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(i,101);});
  salvar.setOnClickListener(v->{try{JSONObject o=old==null?new JSONObject():old;o.put("cliente",cliente.getText().toString());o.put("valor",valor.getText().toString());o.put("desc",desc.getText().toString());if(old==null)o.put("status","A receber");if(pendingImage!=null){o.put("foto",pendingImage.toString());pendingImage=null;}if(old==null)items.add(o);save();refresh();d.dismiss();}catch(Exception e){}});d.setContentView(l);Window w=d.getWindow();if(w!=null)w.setLayout(-1,-2);d.show();}
 @Override protected void onActivityResult(int r,int c,Intent data){super.onActivityResult(r,c,data);if(r==101&&c==RESULT_OK&&data!=null){pendingImage=data.getData();try{getContentResolver().takePersistableUriPermission(pendingImage,data.getFlags()&Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(Exception e){}}}
 void refresh(){list.removeAllViews();double rec=0,pag=0,atr=0;for(JSONObject o:items){double v=num(o.optString("valor"));String st=o.optString("status");if(st.equals("A receber")||st.equals("Arte entregue"))rec+=v;if(st.equals("Pago"))pag+=v;if(st.contains("atras"))atr+=v;Card(o);}total.setText("💵 Total lançado: R$ "+money(rec+pag));receber.setText("🟡 A receber: R$ "+money(rec));pago.setText("🟢 Pago: R$ "+money(pag));atrasado.setText("🔴 Atrasado: R$ "+money(atr));}
 void Card(JSONObject o){LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);c.setPadding(12,12,12,12);c.setBackgroundColor(card);String cliente=o.optString("cliente","Sem cliente"),v=o.optString("valor","0"),st=o.optString("status","A receber");c.addView(tv(cliente+"  •  R$ "+v,18));c.addView(tv(o.optString("desc","")+"\nStatus: "+st,14));LinearLayout b=new LinearLayout(this);Button status=btn("STATUS");Button zap=btn("💬 COBRAR");Button edit=btn("EDITAR");b.addView(status,new LinearLayout.LayoutParams(0,-2,1));b.addView(zap,new LinearLayout.LayoutParams(0,-2,1));b.addView(edit,new LinearLayout.LayoutParams(0,-2,1));c.addView(b);status.setOnClickListener(vw->status(o));zap.setOnClickListener(vw->whats(o));edit.setOnClickListener(vw->form(o));list.addView(c);Space s=new Space(this);list.addView(s,new LinearLayout.LayoutParams(1,10));}
 void status(JSONObject o){String[] a={"A receber","Arte entregue","Pago","Arte atrasada","Pagamento atrasado"};new AlertDialog.Builder(this).setTitle("Status").setItems(a,(d,w)->{try{o.put("status",a[w]);save();refresh();}catch(Exception e){}}).show();}
 void whats(JSONObject o){String cli=o.optString("cliente"),val=o.optString("valor"),desc=o.optString("desc");String msg="Olá, "+cli+"! 👋\n\nPassando para lembrar do pagamento da arte: "+(desc.isEmpty()?"arte":desc)+".\nValor: R$ "+val+"\n\nQuando puder, me envie o pagamento. Obrigado!";Intent i=new Intent(Intent.ACTION_SEND);i.setType("text/plain");i.putExtra(Intent.EXTRA_TEXT,msg);try{startActivity(Intent.createChooser(i,"Enviar cobrança pelo WhatsApp"));}catch(Exception e){startActivity(new Intent(Intent.ACTION_SENDTO,Uri.parse("smsto:")));}}
 double num(String s){try{return Double.parseDouble(s.replace(".","").replace(",", "."));}catch(Exception e){return 0;}}
 String money(double x){return String.format(java.util.Locale.US,"%.2f",x).replace(",", "X").replace(".", ",").replace("X",".");}
 void save(){JSONArray a=new JSONArray();for(JSONObject o:items)a.put(o);sp.edit().putString("items",a.toString()).apply();}
 void load(){try{JSONArray a=new JSONArray(sp.getString("items","[]"));for(int i=0;i<a.length();i++)items.add(a.getJSONObject(i));}catch(Exception e){}}
}
