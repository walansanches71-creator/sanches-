package com.sanches.vip;

import android.app.*;
import android.os.*;
import android.content.*;
import android.net.Uri;
import android.view.*;
import android.webkit.*;
import android.widget.*;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;

public class MainActivity extends Activity {
  LinearLayout root, bar, content;
  WebView web, telegram;
  Uri sharedImage;
  String sharedText="";
  final String SITE="https://arquivo-telegram-v2.pages.dev/";
  final String TELEGRAM="https://web.telegram.org/k/";

  int dp(float n){return (int)(n*getResources().getDisplayMetrics().density+0.5f);}
  TextView tab(String title){
    TextView t=new TextView(this);
    t.setText(title); t.setTextSize(14); t.setTextColor(Color.WHITE);
    t.setGravity(Gravity.CENTER); t.setPadding(dp(8),0,dp(8),0);
    t.setLayoutParams(new LinearLayout.LayoutParams(0,dp(52),1));
    return t;
  }
  Button actionButton(String title){
    Button b=new Button(this);
    b.setText(title); b.setTextSize(14); b.setAllCaps(false);
    b.setMinHeight(dp(48)); b.setPadding(dp(8),0,dp(8),0);
    return b;
  }

  @Override public void onCreate(Bundle b){
    super.onCreate(b);
    getWindow().setStatusBarColor(Color.rgb(16,16,16));
    getWindow().setNavigationBarColor(Color.BLACK);
    build();
    handleIntent(getIntent());
  }

  void build(){
    root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
    root.setBackgroundColor(Color.rgb(245,245,245));
    setContentView(root);

    LinearLayout header=new LinearLayout(this);
    header.setGravity(Gravity.CENTER_VERTICAL);
    header.setPadding(dp(12),0,dp(12),0);
    header.setBackgroundColor(Color.rgb(18,18,18));
    header.setLayoutParams(new LinearLayout.LayoutParams(-1,dp(54)));

    TextView logo=new TextView(this);
    logo.setText("👕  VIP CAMISAS");
    logo.setTextColor(Color.WHITE); logo.setTextSize(17); logo.setGravity(Gravity.CENTER_VERTICAL);
    header.addView(logo,new LinearLayout.LayoutParams(0,-1,1));

    TextView sync=new TextView(this);
    sync.setText("● SINCRONIZADO"); sync.setTextSize(10); sync.setTextColor(Color.rgb(80,220,120));
    sync.setGravity(Gravity.CENTER);
    header.addView(sync,new LinearLayout.LayoutParams(dp(105),-1));
    root.addView(header);

    bar=new LinearLayout(this); bar.setOrientation(LinearLayout.HORIZONTAL); bar.setBackgroundColor(Color.rgb(35,35,35));
    TextView siteTab=tab("📦  SITE VIP"), tgTab=tab("✈  TELEGRAM");
    bar.addView(siteTab); bar.addView(tgTab); root.addView(bar);

    content=new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL);
    root.addView(content,new LinearLayout.LayoutParams(-1,0,1));

    web=createWebView(SITE);
    telegram=createWebView(TELEGRAM);

    siteTab.setOnClickListener(v->{showSite();});
    tgTab.setOnClickListener(v->{showTelegram();});
    showSite();
  }

  WebView createWebView(String url){
    WebView v=new WebView(this);
    WebSettings s=v.getSettings();
    s.setJavaScriptEnabled(true); s.setDomStorageEnabled(true);
    s.setAllowFileAccess(true); s.setBuiltInZoomControls(false); s.setDisplayZoomControls(false);
    s.setLoadWithOverviewMode(false); s.setUseWideViewPort(false);
    v.setWebViewClient(new WebViewClient(){@Override public void onPageFinished(WebView x,String u){inject();}});
    v.setWebChromeClient(new WebChromeClient(){
      @Override public boolean onShowFileChooser(WebView x,ValueCallback<Uri[]> cb,FileChooserParams p){
        if(sharedImage!=null){Uri img=sharedImage; sharedImage=null; cb.onReceiveValue(new Uri[]{img}); return true;}
        return super.onShowFileChooser(x,cb,p);
      }
    });
    v.addJavascriptInterface(new Bridge(),"VIPAndroid");
    v.loadUrl(url);
    return v;
  }

  void clearContent(){content.removeAllViews();}
  void showSite(){
    clearContent();
    content.addView(web,new LinearLayout.LayoutParams(-1,0,1));
    if(sharedText.length()>0 || sharedImage!=null) showShareBar();
  }
  void showTelegram(){
    clearContent();
    LinearLayout info=new LinearLayout(this); info.setGravity(Gravity.CENTER_VERTICAL); info.setPadding(dp(8),0,dp(8),0);
    info.setBackgroundColor(Color.rgb(30,30,30));
    TextView tx=new TextView(this);
    tx.setText("Telegram aberto dentro do VIP • escolha a arte e depois toque em ENVIAR PARA O SITE");
    tx.setTextColor(Color.WHITE); tx.setTextSize(12);
    info.addView(tx,new LinearLayout.LayoutParams(0,dp(46),1));
    Button send=actionButton("ENVIAR PARA O SITE");
    send.setOnClickListener(v->{showSite(); Toast.makeText(this,"Agora escolha a foto no site e cole o link da arte.",Toast.LENGTH_LONG).show();});
    info.addView(send,new LinearLayout.LayoutParams(dp(150),dp(46)));
    content.addView(info);
    content.addView(telegram,new LinearLayout.LayoutParams(-1,0,1));
  }
  void showShareBar(){
    LinearLayout box=new LinearLayout(this); box.setPadding(dp(8),dp(4),dp(8),dp(4));
    box.setBackgroundColor(Color.rgb(20,20,20));
    TextView t=new TextView(this);
    t.setText("📎 Conteúdo recebido: "+(sharedImage!=null?"FOTO ":"")+(sharedText.length()>0?"LINK":""));
    t.setTextColor(Color.WHITE); t.setTextSize(12); t.setGravity(Gravity.CENTER_VERTICAL);
    box.addView(t,new LinearLayout.LayoutParams(0,dp(48),1));
    Button b=actionButton("USAR NO SITE");
    b.setOnClickListener(v->{web.reload();});
    box.addView(b,new LinearLayout.LayoutParams(dp(115),dp(48)));
    content.addView(box,0);
  }

  void handleIntent(Intent i){
    if(i==null)return;
    if(Intent.ACTION_SEND.equals(i.getAction()) || Intent.ACTION_SEND_MULTIPLE.equals(i.getAction())){
      CharSequence t=i.getCharSequenceExtra(Intent.EXTRA_TEXT);
      if(t!=null) sharedText=t.toString();
      Uri u=i.getParcelableExtra(Intent.EXTRA_STREAM);
      if(u!=null) sharedImage=u;
      if(web!=null) inject();
    }
  }

  void inject(){
    if(web==null)return;
    String text=(sharedText==null?"":sharedText).replace("\\","\\\\").replace("'","\\'").replace("\n","\\n").replace("\r","\\r");
    String js="(function(){window.__VIP_SHARED_LINK='"+text+"';try{document.querySelectorAll('input,textarea').forEach(function(e){var n=((e.name||'')+' '+(e.id||'')+' '+(e.placeholder||'')).toLowerCase();if(/telegram|link|url|arte|arquivo/.test(n)&&/https?:\\/\\//.test(window.__VIP_SHARED_LINK)){e.value=window.__VIP_SHARED_LINK;e.dispatchEvent(new Event('input',{bubbles:true}));e.dispatchEvent(new Event('change',{bubbles:true));}})}catch(e){}})();";
    web.evaluateJavascript(js,null);
  }

  public class Bridge{
    @JavascriptInterface public String getSharedLink(){return sharedText==null?"":sharedText;}
    @JavascriptInterface public boolean hasImage(){return sharedImage!=null;}
    @JavascriptInterface public void clear(){sharedText="";sharedImage=null;}
  }
  @Override public void onNewIntent(Intent i){super.onNewIntent(i);setIntent(i);handleIntent(i);showSite();}
  @Override public void onBackPressed(){
    WebView active=(content.indexOfChild(telegram)>=0)?telegram:web;
    if(active.canGoBack()) active.goBack(); else super.onBackPressed();
  }
}
