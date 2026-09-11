package kz.almas.tube;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.*;

public class LocalPlayerActivity extends Activity {
    private static final int BG=Color.rgb(10,10,14), PANEL=Color.rgb(24,24,31), RED=Color.rgb(255,48,64), MUTED=Color.rgb(170,170,182);
    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);
        String raw=getIntent().getStringExtra("uri");if(raw==null||raw.isEmpty()){finish();return;}

        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(BG);root.setPadding(dp(12),dp(10),dp(12),dp(12));
        LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);
        Button back=new Button(this);back.setText("‹");back.setTextSize(22);back.setTextColor(Color.WHITE);back.setAllCaps(false);back.setBackground(round(PANEL,16));back.setOnClickListener(v->finish());top.addView(back,new LinearLayout.LayoutParams(dp(48),dp(44)));
        TextView title=new TextView(this);title.setText("Offline Player");title.setTextSize(17);title.setTextColor(Color.WHITE);title.setPadding(dp(10),0,0,0);top.addView(title,new LinearLayout.LayoutParams(0,dp(44),1));root.addView(top);

        VideoView video=new VideoView(this);video.setBackgroundColor(Color.BLACK);video.setClipToOutline(true);video.setBackground(round(Color.BLACK,18));MediaController controls=new MediaController(this);controls.setAnchorView(video);video.setMediaController(controls);video.setOnErrorListener((mp,what,extra)->{Toast.makeText(this,"Видео ашылмады",Toast.LENGTH_LONG).show();return false;});root.addView(video,new LinearLayout.LayoutParams(-1,0,1));
        TextView badge=new TextView(this);badge.setText("✓ OFFLINE • интернет қажет емес");badge.setTextColor(Color.rgb(104,220,145));badge.setTextSize(12);badge.setPadding(dp(10),dp(10),0,0);root.addView(badge);
        setContentView(root);
        video.setVideoURI(Uri.parse(raw));video.requestFocus();video.start();
    }
    private GradientDrawable round(int c,int r){GradientDrawable g=new GradientDrawable();g.setColor(c);g.setCornerRadius(dp(r));return g;}
    private int dp(int x){return(int)(x*getResources().getDisplayMetrics().density+.5f);}
}
