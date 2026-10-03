package com.sanches.producaovip;

import android.app.*;
import android.os.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.content.*;
import android.view.*;
import android.webkit.*;
import android.widget.*;
import java.util.*;

public class MainActivity extends Activity {
    private static final String SITE = "https://producao-vip.pages.dev/painel";
    private WebView web;
    private LinearLayout sectors;
    private TextView current;
    private final String[] names = {"PRODUÇÃO GERAL","COSTURA","ESTAMPARIA","IMPRESSÃO","CORTE A LASER","CALANDRA","ACABAMENTO","EXPEDIÇÃO"};
    private final String[] keys = {"geral","costura","estamparia","impressao","laser","calandra","acabamento","expedicao"};
    private int selected = 0;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(Color.rgb(7,16,31));
        getWindow().setNavigationBarColor(Color.rgb(7,16,31));
        buildUi();
        setupWeb();
        selected = getPreferences(0).getInt("sector", 0);
        selectSector(selected);
    }

    private TextView tv(String s, float size, int color) {
        TextView v = new TextView(this);
        v.setText(s); v.setTextSize(size); v.setTextColor(color);
        v.setGravity(Gravity.CENTER_VERTICAL);
        return v;
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(7,16,31));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(18,12,18,8);

        TextView title = tv("PRODUÇÃO VIP",20,Color.WHITE);
        title.setTypeface(null, Typeface.BOLD);
        header.addView(title, new LinearLayout.LayoutParams(-1,34));
        current = tv("SETOR: PRODUÇÃO GERAL",12,Color.rgb(108,210,255));
        header.addView(current, new LinearLayout.LayoutParams(-1,24));
        root.addView(header);

        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        sectors = new LinearLayout(this);
        sectors.setOrientation(LinearLayout.HORIZONTAL);
        sectors.setPadding(12,4,12,10);
        for (int i=0;i<names.length;i++) {
            final int idx=i;
            TextView chip=tv(names[i],12,Color.WHITE);
            chip.setGravity(Gravity.CENTER);
            chip.setTypeface(null,Typeface.BOLD);
            chip.setPadding(18,0,18,0);
            chip.setBackgroundColor(Color.rgb(25,38,61));
            LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-2,44);
            p.setMargins(4,0,4,0);
            sectors.addView(chip,p);
            chip.setOnClickListener(v->selectSector(idx));
        }
        hs.addView(sectors);
        root.addView(hs,new LinearLayout.LayoutParams(-1,58));

        LinearLayout bar=new LinearLayout(this);
        bar.setPadding(14,2,14,8);
        Button refresh=new Button(this);
        refresh.setText("↻  ATUALIZAR");
        refresh.setOnClickListener(v->web.reload());
        Button home=new Button(this);
        home.setText("⌂  PAINEL");
        home.setOnClickListener(v->selectSector(selected));
        bar.addView(refresh,new LinearLayout.LayoutParams(0,48,1));
        LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(0,48,1); hp.setMargins(8,0,0,0);
        bar.addView(home,hp);
        root.addView(bar);

        web=new WebView(this);
        root.addView(web,new LinearLayout.LayoutParams(-1,0,1));
        setContentView(root);
    }

    private void setupWeb() {
        WebSettings s=web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setSupportZoom(false);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);
        s.setLoadsImagesAutomatically(true);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        web.setWebViewClient(new WebViewClient(){
            @Override public boolean shouldOverrideUrlLoading(WebView v,String url){ v.loadUrl(url); return true; }
            @Override public void onPageFinished(WebView v,String url){ injectSector(); }
        });
        web.setWebChromeClient(new WebChromeClient());
    }

    private void selectSector(int idx) {
        if(idx<0||idx>=keys.length) idx=0;
        selected=idx;
        getPreferences(0).edit().putInt("sector",idx).apply();
        current.setText("SETOR: "+names[idx]);
        for(int i=0;i<sectors.getChildCount();i++) {
            TextView c=(TextView)sectors.getChildAt(i);
            c.setBackgroundColor(i==idx?Color.rgb(25,126,196):Color.rgb(25,38,61));
        }
        web.loadUrl(SITE+"?setor="+keys[idx]);
    }

    private void injectSector() {
        String key=keys[selected].replace("'","\\'");
        String name=names[selected].replace("'","\\'");
        String js="(function(){try{localStorage.setItem('sanches_setor','"+key+"');localStorage.setItem('sanches_setor_nome','"+name+"');window.SANCHES_SETOR='"+key+"';window.SANCHES_SETOR_NOME='"+name+"';window.dispatchEvent(new CustomEvent('sanches:setor',{detail:{key:'"+key+"',nome:'"+name+"'}}));}catch(e){}})();";
        web.evaluateJavascript(js,null);
    }

    @Override public void onBackPressed() {
        if(web.canGoBack()) web.goBack(); else super.onBackPressed();
    }
}
