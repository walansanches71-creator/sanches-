package com.sanches.producaovip;

import android.app.*;
import android.os.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.*;
import android.webkit.*;
import android.widget.*;

public class MainActivity extends Activity {
    private static final String SITE = "https://producao-vip.pages.dev/painel";

    private WebView web;
    private LinearLayout content;
    private TextView current;
    private int selected = -1;

    private final String[] names = {
        "COSTURA", "ESTAMPARIA", "IMPRESSÃO", "CORTE A LASER",
        "CALANDRA", "ACABAMENTO", "EXPEDIÇÃO"
    };
    private final String[] keys = {
        "costura", "estamparia", "impressao", "laser",
        "calandra", "acabamento", "expedicao"
    };

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(Color.rgb(7,16,31));
        getWindow().setNavigationBarColor(Color.rgb(7,16,31));
        buildUi();
        setupWeb();

        int saved = getPreferences(0).getInt("sector", -1);
        if (saved >= 0 && saved < keys.length) {
            selected = saved;
            openSector(selected);
        } else {
            showSectorGate();
        }
    }

    private TextView tv(String s, float size, int color) {
        TextView v = new TextView(this);
        v.setText(s);
        v.setTextSize(size);
        v.setTextColor(color);
        v.setGravity(Gravity.CENTER_VERTICAL);
        return v;
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(7,16,31));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(18,12,18,8);

        LinearLayout brand = new LinearLayout(this);
        brand.setOrientation(LinearLayout.VERTICAL);

        TextView title = tv("PRODUÇÃO VIP",20,Color.WHITE);
        title.setTypeface(null,Typeface.BOLD);
        brand.addView(title,new LinearLayout.LayoutParams(-1,30));

        current = tv("ACESSO POR SETOR",11,Color.rgb(108,210,255));
        brand.addView(current,new LinearLayout.LayoutParams(-1,22));
        header.addView(brand,new LinearLayout.LayoutParams(0,58,1));

        Button change = new Button(this);
        change.setText("SETOR");
        change.setTextSize(11);
        change.setTextColor(Color.WHITE);
        change.setOnClickListener(v -> showSectorGate());
        header.addView(change,new LinearLayout.LayoutParams(82,46));

        root.addView(header);

        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        root.addView(content,new LinearLayout.LayoutParams(-1,0,1));

        web = new WebView(this);
        content.addView(web,new LinearLayout.LayoutParams(-1,-1));

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

        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView v,String url) {
                if (url.startsWith("https://producao-vip.pages.dev/")) v.loadUrl(url);
                return true;
            }

            @Override public void onPageFinished(WebView v,String url) {
                injectSector();
            }
        });
        web.setWebChromeClient(new WebChromeClient());
    }

    private void showSectorGate() {
        content.removeAllViews();

        LinearLayout gate=new LinearLayout(this);
        gate.setOrientation(LinearLayout.VERTICAL);
        gate.setGravity(Gravity.CENTER_HORIZONTAL);
        gate.setPadding(18,24,18,18);
        gate.setBackgroundColor(Color.rgb(7,16,31));

        TextView icon=tv("▣",54,Color.rgb(75,196,255));
        icon.setGravity(Gravity.CENTER);
        gate.addView(icon,new LinearLayout.LayoutParams(-1,72));

        TextView title=tv("ACESSO DOS SETORES",22,Color.WHITE);
        title.setTypeface(null,Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        gate.addView(title,new LinearLayout.LayoutParams(-1,40));

        TextView sub=tv("Selecione o setor de trabalho para entrar no painel operacional.",13,Color.rgb(165,180,200));
        sub.setGravity(Gravity.CENTER);
        sub.setPadding(18,0,18,16);
        gate.addView(sub,new LinearLayout.LayoutParams(-1,52));

        LinearLayout grid=new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);

        for(int row=0;row<names.length;row+=2) {
            LinearLayout line=new LinearLayout(this);
            line.setOrientation(LinearLayout.HORIZONTAL);
            addSectorButton(line,row);
            if(row+1<names.length) addSectorButton(line,row+1);
            grid.addView(line,new LinearLayout.LayoutParams(-1,62));
        }

        ScrollView scroll=new ScrollView(this);
        scroll.addView(grid);
        gate.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));

        content.addView(gate,new LinearLayout.LayoutParams(-1,-1));
        web.setVisibility(View.GONE);
        current.setText("ACESSO POR SETOR");
    }

    private void addSectorButton(LinearLayout row,int idx) {
        Button b=new Button(this);
        b.setText(names[idx]);
        b.setTextSize(12);
        b.setTextColor(Color.WHITE);
        b.setAllCaps(false);
        b.setTypeface(null,Typeface.BOLD);
        b.setOnClickListener(v -> {
            selected=idx;
            getPreferences(0).edit().putInt("sector",idx).apply();
            openSector(selected);
        });

        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,56,1);
        p.setMargins(5,4,5,4);
        row.addView(b,p);
    }

    private void openSector(int idx) {
        if(idx<0 || idx>=keys.length) {
            showSectorGate();
            return;
        }

        selected=idx;
        getPreferences(0).edit().putInt("sector",idx).apply();
        current.setText("SETOR: "+names[idx]);

        content.removeAllViews();
        content.addView(web,new LinearLayout.LayoutParams(-1,-1));
        web.setVisibility(View.VISIBLE);
        web.loadUrl(SITE+"?setor="+keys[idx]);
    }

    private void injectSector() {
        if(selected<0 || selected>=keys.length) return;

        String key=keys[selected].replace("'","\\'");
        String name=names[selected].replace("'","\\'");

        String js="(function(){try{"+
            "localStorage.setItem('sanches_setor','"+key+"');"+
            "localStorage.setItem('sanches_setor_nome','"+name+"');"+
            "window.SANCHES_SETOR='"+key+"';"+
            "window.SANCHES_SETOR_NOME='"+name+"';"+
            "window.dispatchEvent(new CustomEvent('sanches:setor',{detail:{key:'"+key+"',nome:'"+name+"'}}));"+
            "}catch(e){}})();";

        web.evaluateJavascript(js,null);
    }

    @Override public void onBackPressed() {
        if(web.getVisibility()==View.VISIBLE && web.canGoBack()) web.goBack();
        else if(web.getVisibility()==View.VISIBLE) showSectorGate();
        else super.onBackPressed();
    }
}
