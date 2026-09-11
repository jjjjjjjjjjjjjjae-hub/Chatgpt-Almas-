package kz.almas.tube;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.*;
import android.webkit.*;
import android.widget.*;

public class PlayerActivity extends Activity {
    private String videoId;
    private static final int BG=Color.rgb(10,10,14), PANEL=Color.rgb(24,24,31), RED=Color.rgb(255,48,64), MUTED=Color.rgb(170,170,182);

    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);
        videoId=getIntent().getStringExtra("video_id");String title=getIntent().getStringExtra("title");
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(BG);root.setPadding(dp(12),dp(10),dp(12),dp(12));

        LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);
        Button back=dark("‹");back.setTextSize(22);back.setOnClickListener(v->finish());top.addView(back,new LinearLayout.LayoutParams(dp(48),dp(44)));
        TextView brand=text("AlmasTube Player",17,Color.WHITE,true);brand.setPadding(dp(10),0,0,0);top.addView(brand,new LinearLayout.LayoutParams(0,dp(44),1));root.addView(top);

        WebView web=new WebView(this);web.setBackgroundColor(Color.BLACK);web.setClipToOutline(true);web.setBackground(round(Color.BLACK,18));WebSettings s=web.getSettings();s.setJavaScriptEnabled(true);s.setDomStorageEnabled(true);s.setMediaPlaybackRequiresUserGesture(false);web.setWebChromeClient(new WebChromeClient());web.setWebViewClient(new WebViewClient());
        String html="<!doctype html><html><head><meta name='viewport' content='width=device-width,initial-scale=1'></head><body style='margin:0;background:#000;height:100vh'><iframe width='100%' height='100%' src='https://www.youtube.com/embed/"+videoId+"?playsinline=1&rel=0&modestbranding=1' frameborder='0' allow='accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share' allowfullscreen></iframe></body></html>";
        web.loadDataWithBaseURL("https://www.youtube.com",html,"text/html","UTF-8",null);root.addView(web,new LinearLayout.LayoutParams(-1,0,1));

        LinearLayout info=new LinearLayout(this);info.setOrientation(LinearLayout.VERTICAL);info.setPadding(dp(2),dp(12),dp(2),dp(8));TextView t=text(title==null?"YouTube видео":title,18,Color.WHITE,true);info.addView(t);TextView sub=text("YouTube ресми player арқылы ойнатылуда",12,MUTED,false);sub.setPadding(0,dp(4),0,0);info.addView(sub);root.addView(info);

        LinearLayout actions=new LinearLayout(this);actions.setOrientation(LinearLayout.HORIZONTAL);
        Button yt=accent("YouTube-та ашу");yt.setOnClickListener(v->openYouTube());
        Button off=dark("Offline");off.setOnClickListener(v->new AlertDialog.Builder(this).setTitle("Offline режим").setMessage("YouTube Data API видеоны MP4 файл ретінде бермейді. Егер YouTube қолданбасында ресми Download қолжетімді болса, видеоны сол жерден сақтай аласың. AlmasTube Offline бөлімі тікелей MP4 және телефондағы видеоларды интернетсіз ойнатады.").setPositiveButton("YouTube-та ашу",(d,w)->openYouTube()).setNegativeButton("Жабу",null).show());
        actions.addView(yt,new LinearLayout.LayoutParams(0,dp(50),1));LinearLayout.LayoutParams op=new LinearLayout.LayoutParams(0,dp(50),1);op.setMargins(dp(8),0,0,0);actions.addView(off,op);root.addView(actions);
        setContentView(root);
    }

    private void openYouTube(){try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse("vnd.youtube:"+videoId)));}catch(Exception e){startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse("https://www.youtube.com/watch?v="+videoId)));}}
    private TextView text(String s,int z,int c,boolean bold){TextView v=new TextView(this);v.setText(s);v.setTextSize(z);v.setTextColor(c);if(bold)v.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);return v;}
    private Button accent(String s){Button b=new Button(this);b.setText(s);b.setTextColor(Color.WHITE);b.setAllCaps(false);b.setBackground(round(RED,16));return b;}
    private Button dark(String s){Button b=new Button(this);b.setText(s);b.setTextColor(Color.WHITE);b.setAllCaps(false);b.setBackground(round(PANEL,16));return b;}
    private GradientDrawable round(int c,int r){GradientDrawable g=new GradientDrawable();g.setColor(c);g.setCornerRadius(dp(r));return g;}
    private int dp(int x){return(int)(x*getResources().getDisplayMetrics().density+.5f);}
}
