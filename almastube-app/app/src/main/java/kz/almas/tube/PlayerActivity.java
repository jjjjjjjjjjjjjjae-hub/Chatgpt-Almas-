package kz.almas.tube;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.*;
import android.webkit.*;
import android.widget.*;

public class PlayerActivity extends Activity {
    private String videoId;
    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        videoId=getIntent().getStringExtra("video_id");
        String title=getIntent().getStringExtra("title");
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(Color.rgb(10,10,10));root.setPadding(dp(12),dp(12),dp(12),dp(12));
        TextView t=new TextView(this);t.setText(title==null?"YouTube":title);t.setTextColor(Color.WHITE);t.setTextSize(18);t.setPadding(0,0,0,dp(8));root.addView(t);

        WebView web=new WebView(this);web.setBackgroundColor(Color.BLACK);WebSettings s=web.getSettings();s.setJavaScriptEnabled(true);s.setDomStorageEnabled(true);s.setMediaPlaybackRequiresUserGesture(false);web.setWebChromeClient(new WebChromeClient());web.setWebViewClient(new WebViewClient());
        String html="<!doctype html><html><body style='margin:0;background:#000'><iframe width='100%' height='100%' src='https://www.youtube.com/embed/"+videoId+"?playsinline=1&rel=0' title='YouTube video player' frameborder='0' allow='accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share' allowfullscreen></iframe></body></html>";
        web.loadDataWithBaseURL("https://www.youtube.com",html,"text/html","UTF-8",null);root.addView(web,new LinearLayout.LayoutParams(-1,0,1));

        LinearLayout actions=new LinearLayout(this);actions.setOrientation(LinearLayout.HORIZONTAL);
        Button yt=new Button(this);yt.setText("YouTube-та ашу");yt.setOnClickListener(v->openYouTube());
        Button off=new Button(this);off.setText("Offline");off.setOnClickListener(v->new AlertDialog.Builder(this).setTitle("Offline").setMessage("YouTube Data API видеоның MP4 файлын бермейді. Ресми YouTube қолданбасында Download мүмкіндігі қолжетімді болса, видеоны сол жерден сақтай аласың.").setPositiveButton("YouTube-та ашу",(d,w)->openYouTube()).setNegativeButton("Жабу",null).show());
        actions.addView(yt,new LinearLayout.LayoutParams(0,dp(52),1));actions.addView(off,new LinearLayout.LayoutParams(0,dp(52),1));root.addView(actions);
        setContentView(root);
    }
    private void openYouTube(){try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse("vnd.youtube:"+videoId)));}catch(Exception e){startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse("https://www.youtube.com/watch?v="+videoId)));}}
    private int dp(int x){return(int)(x*getResources().getDisplayMetrics().density+.5f);}
}
