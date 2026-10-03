package com.sanches.vip;

import android.app.*;import android.os.*;import android.content.*;import android.net.Uri;import android.view.*;import android.webkit.*;import android.widget.*;import android.graphics.Color;

public class MainActivity extends Activity {
 LinearLayout root,content; WebView web,telegram; Uri sharedImage; String sharedText=""; final String SITE="https://arquivo-telegram-v2.pages.dev/"; final String TELEGRAM="https://web.telegram.org/k/";
 int dp(float n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
 TextView tab(String s){TextView t=new TextView(this);t.setText(s);t.setTextSize(14);t.setTextColor(Color.WHITE);t.setGravity(17);t.setPadding(dp(8),0,dp(8),0);t.setLayoutParams(new LinearLayout.LayoutParams(0,dp(52),1));return t;}
 Button btn(String s){Button b=new Button(this);b.setText(s);b.setTextSize(13);b.setAllCaps(false);b.setMinHeight(dp(46));return b;}
 @Override public void onCreate(Bundle b){super.onCreate(b);getWindow().setStatusBarColor(Color.rgb(16,16,16));getWindow().setNavigationBarColor(Color.BLACK);build();handleIntent(getIntent());}
 void build(){
  root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(Color.rgb(245,245,245));setContentView(root);
  LinearLayout head=new LinearLayout(this);head.setGravity(16);head.setPadding(dp(12),0,dp(8),0);head.setBackgroundColor(Color.rgb(18,18,18));root.addView(head,new LinearLayout.LayoutParams(-1,dp(54)));
  TextView logo=new TextView(this);logo.setText("👕  VIP CAMISAS");logo.setTextColor(Color.WHITE);logo.setTextSize(17);head.addView(logo,new LinearLayout.LayoutParams(0,-1,1));
  TextView sync=new TextView(this);sync.setText("● SINCRONIZADO");sync.setTextSize(10);sync.setTextColor(Color.rgb(80,220,120));sync.setGravity(17);head.addView(sync,new LinearLayout.LayoutParams(dp(105),-1));
  LinearLayout tabs=new LinearLayout(this);tabs.setBackgroundColor(Color.rgb(35,35,35));TextView st=tab("📦  SITE VIP"),tt=tab("✈  TELEGRAM");tabs.addView(st);tabs.addView(tt);root.addView(tabs);
  content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);root.addView(content,new LinearLayout.LayoutParams(-1,0,1));
  web=createWeb(SITE);telegram=createWeb(TELEGRAM);st.setOnClickListener(v->showSite());tt.setOnClickListener(v->showTelegram());showSite();
 }
 WebView createWeb(String url){
  WebView v=new WebView(this);WebSettings s=v.getSettings();s.setJavaScriptEnabled(true);s.setDomStorageEnabled(true);s.setAllowFileAccess(true);s.setBuiltInZoomControls(false);s.setDisplayZoomControls(false);s.setLoadWithOverviewMode(false);s.setUseWideViewPort(false);
  v.setWebViewClient(new WebViewClient(){@Override public void onPageFinished(WebView x,String u){inject();}});
  v.setWebChromeClient(new WebChromeClient(){@Override public boolean onShowFileChooser(WebView x,ValueCallback<Uri[]> cb,FileChooserParams p){if(sharedImage!=null){Uri img=sharedImage;sharedImage=null;cb.onReceiveValue(new Uri[]{img});return true;}return super.onShowFileChooser(x,cb,p);}});
  v.addJavascriptInterface(new Bridge(),"VIPAndroid");v.loadUrl(url);return v;
 }
 void showSite(){content.removeAllViews();content.addView(web,new LinearLayout.LayoutParams(-1,0,1));if(sharedText.length()>0||sharedImage!=null)shareBar();}
 void showTelegram(){
  content.removeAllViews();LinearLayout info=new LinearLayout(this);info.setGravity(16);info.setPadding(dp(8),0,dp(8),0);info.setBackgroundColor(Color.rgb(30,30,30));
  TextView tx=new TextView(this);tx.setText("Telegram dentro do app • escolha a camisa/arte");tx.setTextColor(Color.WHITE);tx.setTextSize(12);info.addView(tx,new LinearLayout.LayoutParams(0,dp(46),1));
  Button send=btn("ENVIAR PARA O SITE");send.setOnClickListener(v->{showSite();Toast.makeText(this,"Agora publique a foto e o link no site.",Toast.LENGTH_LONG).show();});info.addView(send,new LinearLayout.LayoutParams(dp(150),dp(46)));
  content.addView(info);content.addView(telegram,new LinearLayout.LayoutParams(-1,0,1));
 }
 void shareBar(){LinearLayout box=new LinearLayout(this);box.setPadding(dp(8),dp(3),dp(8),dp(3));box.setBackgroundColor(Color.rgb(20,20,20));TextView t=new TextView(this);t.setText("📎 "+(sharedImage!=null?"FOTO ":"")+(sharedText.length()>0?"LINK":"")+" RECEBIDO");t.setTextColor(Color.WHITE);t.setTextSize(12);t.setGravity(16);box.addView(t,new LinearLayout.LayoutParams(0,dp(48),1));Button b=btn("USAR NO SITE");b.setOnClickListener(v->web.reload());box.addView(b,new LinearLayout.LayoutParams(dp(115),dp(48)));content.addView(box,0);}
 void handleIntent(Intent i){if(i==null)return;if(Intent.ACTION_SEND.equals(i.getAction())||Intent.ACTION_SEND_MULTIPLE.equals(i.getAction())){CharSequence t=i.getCharSequenceExtra(Intent.EXTRA_TEXT);if(t!=null)sharedText=t.toString();Uri u=i.getParcelableExtra(Intent.EXTRA_STREAM);if(u!=null)sharedImage=u;if(web!=null)inject();}}
 void inject(){if(web==null)return;String text=(sharedText==null?"":sharedText).replace("\\","\\\\").replace("'","\\'").replace("\n","\\n").replace("\r","\\r");String js="(function(){window.__VIP_SHARED_LINK='"+text+"';try{document.querySelectorAll('input,textarea').forEach(function(e){var n=((e.name||'')+' '+(e.id||'')+' '+(e.placeholder||'')).toLowerCase();if(/telegram|link|url|arte|arquivo/.test(n)&&/https?:\\/\\//.test(window.__VIP_SHARED_LINK)){e.value=window.__VIP_SHARED_LINK;e.dispatchEvent(new Event('input',{bubbles:true}));e.dispatchEvent(new Event('change',{bubbles:true}));}})}catch(e){}})();";web.evaluateJavascript(js,null);}
 public class Bridge{@JavascriptInterface public String getSharedLink(){return sharedText==null?"":sharedText;}@JavascriptInterface public boolean hasImage(){return sharedImage!=null;}@JavascriptInterface public void clear(){sharedText="";sharedImage=null;}}
 @Override public void onNewIntent(Intent i){super.onNewIntent(i);setIntent(i);handleIntent(i);showSite();}
 @Override public void onBackPressed(){WebView active=content.indexOfChild(telegram)>=0?telegram:web;if(active.canGoBack())active.goBack();else super.onBackPressed();}
}