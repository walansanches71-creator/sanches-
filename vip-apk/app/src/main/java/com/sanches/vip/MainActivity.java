package com.sanches.vip;

import android.app.*;import android.os.*;import android.content.*;import android.net.Uri;import android.view.*;import android.webkit.*;

public class MainActivity extends Activity {
  WebView web; Uri sharedImage; String sharedText=""; ValueCallback<Uri[]> fileCallback; final String SITE="https://arquivo-telegram-v2.pages.dev/";
  @Override public void onCreate(Bundle b){super.onCreate(b);setup();handleIntent(getIntent());}
  void setup(){
    web=new WebView(this);setContentView(web);WebSettings s=web.getSettings();s.setJavaScriptEnabled(true);s.setDomStorageEnabled(true);s.setAllowFileAccess(true);
    web.setWebViewClient(new WebViewClient(){@Override public void onPageFinished(WebView v,String u){inject();}});
    web.setWebChromeClient(new WebChromeClient(){@Override public boolean onShowFileChooser(WebView v,ValueCallback<Uri[]> cb,FileChooserParams p){fileCallback=cb;if(sharedImage!=null){Uri x=sharedImage;sharedImage=null;cb.onReceiveValue(new Uri[]{x});return true;}return super.onShowFileChooser(v,cb,p);}});
    web.addJavascriptInterface(new Bridge(),"VIPAndroid");web.loadUrl(SITE);
  }
  void handleIntent(Intent i){if(i==null)return;if(Intent.ACTION_SEND.equals(i.getAction())){CharSequence t=i.getCharSequenceExtra(Intent.EXTRA_TEXT);sharedText=t==null?"":t.toString();Uri u=i.getParcelableExtra(Intent.EXTRA_STREAM);if(u!=null)sharedImage=u;}}
  void inject(){
    String text=(sharedText==null?"":sharedText).replace("\\","\\\\").replace("'","\\'").replace("\n","\\n").replace("\r","\\r");
    String js="(function(){window.__VIP_SHARED_LINK='"+text+"';window.__VIP_SHARED_READY=true;try{document.querySelectorAll('input,textarea').forEach(function(e){var n=((e.name||'')+' '+(e.id||'')+' '+(e.placeholder||'')).toLowerCase();if(/telegram|link|url|arte|arquivo/.test(n)&&/https?:\\/\\//.test(window.__VIP_SHARED_LINK)){e.value=window.__VIP_SHARED_LINK;e.dispatchEvent(new Event('input',{bubbles:true}));e.dispatchEvent(new Event('change',{bubbles:true));}})}catch(e){}})();";
    web.evaluateJavascript(js,null);
  }
  public class Bridge{@JavascriptInterface public String getSharedLink(){return sharedText==null?"":sharedText;}@JavascriptInterface public boolean hasImage(){return sharedImage!=null;}@JavascriptInterface public void clear(){sharedText="";sharedImage=null;}}
  @Override public void onNewIntent(Intent i){super.onNewIntent(i);setIntent(i);handleIntent(i);web.reload();}
  @Override public void onBackPressed(){if(web.canGoBack())web.goBack();else super.onBackPressed();}
}
