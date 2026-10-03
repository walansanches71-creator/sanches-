package com.sanches.vip;

import android.app.*;
import android.os.*;
import android.content.*;
import android.net.Uri;
import android.provider.MediaStore;
import android.view.*;
import android.webkit.*;
import android.widget.*;
import android.graphics.Color;
import android.graphics.BitmapFactory;
import android.util.Base64;
import java.io.*;
import java.net.*;
import java.util.*;

public class MainActivity extends Activity {
    LinearLayout root,content;
    WebView web,telegram;
    Uri sharedImage;
    String sharedText="";
    String capturedLink="";
    String capturedTitle="";
    String capturedImageUrl="";
    File capturedFile;
    boolean captureMode=false;
    final String SITE="https://arquivo-telegram-v2.pages.dev/";
    final String TELEGRAM="https://web.telegram.org/k/";
    int dp(float n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}

    TextView tab(String s){
        TextView t=new TextView(this); t.setText(s); t.setTextSize(14); t.setTextColor(Color.WHITE);
        t.setGravity(17); t.setPadding(dp(8),0,dp(8),0);
        t.setLayoutParams(new LinearLayout.LayoutParams(0,dp(52),1)); return t;
    }
    Button btn(String s){
        Button b=new Button(this); b.setText(s); b.setTextSize(13); b.setAllCaps(false); b.setMinHeight(dp(46)); return b;
    }

    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        getWindow().setStatusBarColor(Color.rgb(16,16,16));
        getWindow().setNavigationBarColor(Color.BLACK);
        build(); handleIntent(getIntent());
    }

    void build(){
        root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(245,245,245)); setContentView(root);

        LinearLayout head=new LinearLayout(this); head.setGravity(16);
        head.setPadding(dp(12),0,dp(8),0); head.setBackgroundColor(Color.rgb(18,18,18));
        root.addView(head,new LinearLayout.LayoutParams(-1,dp(54)));

        TextView logo=new TextView(this); logo.setText("👕  VIP CAMISAS");
        logo.setTextColor(Color.WHITE); logo.setTextSize(17);
        head.addView(logo,new LinearLayout.LayoutParams(0,-1,1));

        TextView sync=new TextView(this); sync.setText("● SINCRONIZADO");
        sync.setTextSize(10); sync.setTextColor(Color.rgb(80,220,120)); sync.setGravity(17);
        head.addView(sync,new LinearLayout.LayoutParams(dp(105),-1));

        LinearLayout tabs=new LinearLayout(this); tabs.setBackgroundColor(Color.rgb(35,35,35));
        TextView st=tab("📦  SITE VIP"), tt=tab("✈  TELEGRAM");
        tabs.addView(st); tabs.addView(tt); root.addView(tabs);

        content=new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL);
        root.addView(content,new LinearLayout.LayoutParams(-1,0,1));

        web=createWeb(SITE); telegram=createWeb(TELEGRAM);
        st.setOnClickListener(v->showSite()); tt.setOnClickListener(v->showTelegram());
        showSite();
    }

    WebView createWeb(String url){
        WebView v=new WebView(this);
        WebSettings s=v.getSettings();
        s.setJavaScriptEnabled(true); s.setDomStorageEnabled(true); s.setAllowFileAccess(true);
        s.setAllowContentAccess(true); s.setBuiltInZoomControls(false); s.setDisplayZoomControls(false);
        s.setLoadWithOverviewMode(false); s.setUseWideViewPort(false);

        v.setWebViewClient(new WebViewClient(){
            @Override public void onPageFinished(WebView x,String u){ if(x==web) injectSiteData(); }
        });
        v.setWebChromeClient(new WebChromeClient(){
            @Override public boolean onShowFileChooser(WebView x,ValueCallback<Uri[]> cb,FileChooserParams p){
                if(capturedFile!=null && capturedFile.exists()){
                    Uri img=Uri.fromFile(capturedFile);
                    cb.onReceiveValue(new Uri[]{img});
                    return true;
                }
                if(sharedImage!=null){
                    Uri img=sharedImage; sharedImage=null; cb.onReceiveValue(new Uri[]{img}); return true;
                }
                return super.onShowFileChooser(x,cb,p);
            }
        });
        v.addJavascriptInterface(new Bridge(),"VIPAndroid");
        v.loadUrl(url);
        return v;
    }

    void showSite(){
        content.removeAllViews();
        content.addView(web,new LinearLayout.LayoutParams(-1,0,1));
        if(sharedText.length()>0||sharedImage!=null) shareBar();
        injectSiteData();
    }

    void showTelegram(){
        content.removeAllViews();
        LinearLayout info=new LinearLayout(this); info.setGravity(16);
        info.setPadding(dp(8),0,dp(8),0); info.setBackgroundColor(Color.rgb(30,30,30));

        TextView tx=new TextView(this);
        tx.setText(captureMode ? "⚡ CAPTURA ATIVA • toque na FOTO da camisa" : "Telegram dentro do app");
        tx.setTextColor(Color.WHITE); tx.setTextSize(12);
        info.addView(tx,new LinearLayout.LayoutParams(0,dp(46),1));

        Button capture=btn(captureMode ? "🛑 PARAR" : "⚡ CAPTURAR");
        capture.setOnClickListener(v->{
            captureMode=!captureMode;
            tx.setText(captureMode ? "⚡ CAPTURA ATIVA • toque na FOTO da camisa" : "Telegram dentro do app");
            capture.setText(captureMode ? "🛑 PARAR" : "⚡ CAPTURAR");
            setCaptureMode(captureMode);
        });
        info.addView(capture,new LinearLayout.LayoutParams(dp(125),dp(46)));

        content.addView(info);
        content.addView(telegram,new LinearLayout.LayoutParams(-1,0,1));
        if(captureMode) setCaptureMode(true);
    }

    void setCaptureMode(boolean on){
        captureMode=on;
        String js="(function(){window.__VIP_CAPTURE="+(on?"true":"false")+";"+
            "if(window.__VIP_CAPTURE_BOUND)return;window.__VIP_CAPTURE_BOUND=true;"+
            "document.addEventListener('click',function(ev){if(!window.__VIP_CAPTURE)return;"+
            "var el=ev.target;if(!el)return;var img=el.closest?el.closest('img'):null;"+
            "if(!img)return;ev.preventDefault();ev.stopPropagation();"+
            "var box=img.closest('[data-message-id]')||img.closest('[class*=message]')||img.parentElement;"+
            "var link='';var a=(box&&box.querySelector)?box.querySelector('a[href*="/c/"],a[href*="t.me/"]'):null;"+
            "if(a)link=a.href; if(!link){var as=document.querySelectorAll('a[href*="/c/"],a[href*="t.me/"]');"+
            "for(var i=0;i<as.length;i++){if(as[i].getBoundingClientRect().top<=img.getBoundingClientRect().bottom){link=as[i].href;}}}"+
            "var text=box?box.innerText:''; if(!text)text=document.title||'';"+
            "var src=img.currentSrc||img.src||'';"+
            "VIPAndroid.captureCandidate(src,link,text);"+
            "},true);"+
            "})();";
        telegram.evaluateJavascript(js,null);
    }

    void shareBar(){
        LinearLayout box=new LinearLayout(this); box.setPadding(dp(8),dp(3),dp(8),dp(3));
        box.setBackgroundColor(Color.rgb(20,20,20));
        TextView t=new TextView(this);
        t.setText("📎 "+(sharedImage!=null?"FOTO ":"")+(sharedText.length()>0?"LINK":"")+" RECEBIDO");
        t.setTextColor(Color.WHITE); t.setTextSize(12); t.setGravity(16);
        box.addView(t,new LinearLayout.LayoutParams(0,dp(48),1));
        Button b=btn("USAR NO SITE"); b.setOnClickListener(v->injectSiteData());
        box.addView(b,new LinearLayout.LayoutParams(dp(115),dp(48)));
        content.addView(box,0);
    }

    void handleIntent(Intent i){
        if(i==null)return;
        if(Intent.ACTION_SEND.equals(i.getAction())||Intent.ACTION_SEND_MULTIPLE.equals(i.getAction())){
            CharSequence t=i.getCharSequenceExtra(Intent.EXTRA_TEXT); if(t!=null)sharedText=t.toString();
            Uri u=i.getParcelableExtra(Intent.EXTRA_STREAM); if(u!=null)sharedImage=u;
            if(web!=null)injectSiteData();
        }
    }

    String esc(String s){
        if(s==null)return "";
        return s.replace("\\","\\\\").replace("'","\\'").replace("\n","\\n").replace("\r","\\r");
    }

    void injectSiteData(){
        if(web==null)return;
        String text=esc(sharedText);
        String js="(function(){window.__VIP_SHARED_LINK='"+text+"';try{"+
            "document.querySelectorAll('input,textarea').forEach(function(e){var n=((e.name||'')+' '+(e.id||'')+' '+(e.placeholder||'')).toLowerCase();"+
            "if(/telegram|link|url/.test(n)&&/https?:\\/\\//.test(window.__VIP_SHARED_LINK)){e.value=window.__VIP_SHARED_LINK;e.dispatchEvent(new Event('input',{bubbles:true}));e.dispatchEvent(new Event('change',{bubbles:true}));}})}catch(e){}})();";
        web.evaluateJavascript(js,null);
    }

    void captureCandidate(String src,String link,String text){
        capturedImageUrl=src==null?"":src;
        capturedLink=link==null?"":link;
        capturedTitle=cleanTitle(text);
        if(capturedImageUrl.startsWith("blob:") || capturedImageUrl.startsWith("data:")){
            fetchImageInTelegram(capturedImageUrl);
        }else{
            capturedFile=null;
            publishCaptured();
        }
    }

    String cleanTitle(String s){
        if(s==null)return "";
        String x=s.replace("\n"," ").replace("\r"," ").trim();
        if(x.length()>90)x=x.substring(0,90);
        return x;
    }

    void fetchImageInTelegram(String src){
        String js="(async function(){try{var r=await fetch('"+esc(src)+"',{credentials:'include'});var b=await r.blob();"+
            "var rd=new FileReader();rd.onload=function(){VIPAndroid.captureImageData(rd.result)};rd.readAsDataURL(b);}catch(e){VIPAndroid.captureCandidateFailed(''+e);}})();";
        telegram.evaluateJavascript(js,null);
    }

    void publishCaptured(){
        if(capturedImageUrl.length()==0){Toast.makeText(this,"Não consegui pegar a foto.",Toast.LENGTH_LONG).show();return;}
        runOnUiThread(()->{
            Toast.makeText(this,"⚡ Capturada! Publicando no site...",Toast.LENGTH_SHORT).show();
            showSite();
            new Handler().postDelayed(()->autoPublishOnSite(),900);
        });
    }

    void autoPublishOnSite(){
        String link=esc(capturedLink);
        String title=esc(capturedTitle);
        String image=esc(capturedImageUrl);
        String js="(function(){"+
            "var inputs=[...document.querySelectorAll('input,textarea')];"+
            "function set(re,val){var e=inputs.find(x=>re.test(((x.name||'')+' '+(x.id||'')+' '+(x.placeholder||'')).toLowerCase()));"+
            "if(e){e.focus();e.value=val;e.dispatchEvent(new Event('input',{bubbles:true}));e.dispatchEvent(new Event('change',{bubbles:true}));return true}return false}"+
            "set(/telegram|mensagem.*telegram|link.*telegram/,'"+link+"');"+
            "set(/imagem.*url|url.*imagem|image.*url/,'"+image+"');"+
            "set(/título|titulo|title/,'"+title+"');"+
            "var file=document.querySelector('input[type="file"]');"+
            "if(file){file.click();setTimeout(function(){var buttons=[...document.querySelectorAll('button')];var save=buttons.find(b=>/salvar/i.test((b.innerText||'').trim()));if(save)save.click();},1800);return 'FILE'}"+
            "var buttons=[...document.querySelectorAll('button')];"+
            "var save=buttons.find(b=>/salvar/i.test((b.innerText||'').trim()));"+
            "if(save){save.click();return 'OK'} return 'FORM_NOT_FOUND';"+
            "})()";
        web.evaluateJavascript(js,res->{
            if(res!=null && res.contains("FORM_NOT_FOUND"))
                Toast.makeText(this,"Formulário não encontrado. Deixei a foto/link prontos para publicar.",Toast.LENGTH_LONG).show();
            else
                Toast.makeText(this,"🚀 Arte enviada para publicação!",Toast.LENGTH_LONG).show();
        });
    }

    public class Bridge{
        @JavascriptInterface public String getSharedLink(){return sharedText==null?"":sharedText;}
        @JavascriptInterface public boolean hasImage(){return sharedImage!=null;}
        @JavascriptInterface public void clear(){sharedText="";sharedImage=null;}

        @JavascriptInterface public void captureCandidate(String src,String link,String text){
            captureCandidate(src,link,text);
        }

        @JavascriptInterface public void captureImageData(String data){
            try{
                if(data==null)return;
                String raw=data;
                int comma=raw.indexOf(',');
                if(comma>=0)raw=raw.substring(comma+1);
                byte[] bytes=Base64.decode(raw,Base64.DEFAULT);
                capturedFile=new File(getCacheDir(),"vip_captura_"+System.currentTimeMillis()+".jpg");
                FileOutputStream out=new FileOutputStream(capturedFile); out.write(bytes); out.close();
                capturedImageUrl="";
                publishCaptured();
            }catch(Exception e){
                Toast.makeText(MainActivity.this,"Falha ao salvar a imagem capturada.",Toast.LENGTH_LONG).show();
            }
        }

        @JavascriptInterface public void captureCandidateFailed(String error){
            runOnUiThread(()->Toast.makeText(MainActivity.this,"A imagem está protegida pelo Telegram. Use Compartilhar > VIP Camisas.",Toast.LENGTH_LONG).show());
        }
    }

    @Override public void onNewIntent(Intent i){
        super.onNewIntent(i); setIntent(i); handleIntent(i); showSite();
    }

    @Override public void onBackPressed(){
        WebView active=content.indexOfChild(telegram)>=0?telegram:web;
        if(active.canGoBack())active.goBack(); else super.onBackPressed();
    }
}
