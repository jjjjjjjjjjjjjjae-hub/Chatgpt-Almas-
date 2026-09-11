package kz.almas.tube;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.*;

public class LocalPlayerActivity extends Activity {
    private static final int BG=Color.rgb(10,10,14), PANEL=Color.rgb(24,24,31), PANEL2=Color.rgb(34,34,43), RED=Color.rgb(255,48,64);
    private VideoView video;
    private String raw;

    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);
        raw=getIntent().getStringExtra("uri");if(raw==null||raw.isEmpty()){finish();return;}

        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(BG);root.setPadding(dp(12),dp(10),dp(12),dp(12));
        LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);
        Button back=button("‹",PANEL);back.setTextSize(22);back.setOnClickListener(v->finish());top.addView(back,new LinearLayout.LayoutParams(dp(48),dp(44)));
        TextView title=new TextView(this);title.setText("Offline Player");title.setTextSize(17);title.setTextColor(Color.WHITE);title.setPadding(dp(10),0,0,0);top.addView(title,new LinearLayout.LayoutParams(0,dp(44),1));root.addView(top);

        video=new VideoView(this);video.setBackgroundColor(Color.BLACK);video.setClipToOutline(true);video.setBackground(round(Color.BLACK,18));MediaController controls=new MediaController(this);controls.setAnchorView(video);video.setMediaController(controls);video.setOnErrorListener((mp,what,extra)->{Toast.makeText(this,"Видео ашылмады",Toast.LENGTH_LONG).show();return false;});root.addView(video,new LinearLayout.LayoutParams(-1,0,1));
        TextView badge=new TextView(this);badge.setText("✓ OFFLINE • интернет қажет емес");badge.setTextColor(Color.rgb(104,220,145));badge.setTextSize(12);badge.setPadding(dp(10),dp(10),0,dp(8));root.addView(badge);

        LinearLayout actions=new LinearLayout(this);
        Button bg=button("🎧 Фонда ойнату",RED);bg.setOnClickListener(v->startBackground());
        Button stop=button("■ Тоқтату",PANEL2);stop.setOnClickListener(v->stopBackground());
        actions.addView(bg,new LinearLayout.LayoutParams(0,dp(50),1));LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(0,dp(50),1);sp.setMargins(dp(8),0,0,0);actions.addView(stop,sp);root.addView(actions);
        TextView note=new TextView(this);note.setText("Фонда ойнатуды бассаң, экран өшсе де немесе қолданбадан шықсаң да дыбыс жалғасады. Телефон толық өшірілсе, ойнату тоқтайды.");note.setTextColor(Color.rgb(150,150,162));note.setTextSize(11);note.setPadding(dp(4),dp(8),dp(4),0);root.addView(note);

        setContentView(root);
        video.setVideoURI(Uri.parse(raw));video.requestFocus();video.start();
    }

    private void startBackground(){
        int pos=0;try{pos=video.getCurrentPosition();video.pause();}catch(Exception ignored){}
        Intent i=new Intent(this,BackgroundPlaybackService.class).setAction(BackgroundPlaybackService.ACTION_PLAY);
        i.putExtra(BackgroundPlaybackService.EXTRA_URI,raw);
        i.putExtra(BackgroundPlaybackService.EXTRA_TITLE,"AlmasTube Offline");
        i.putExtra(BackgroundPlaybackService.EXTRA_POSITION,pos);
        if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.O)startForegroundService(i);else startService(i);
        Toast.makeText(this,"Фондық ойнату қосылды",Toast.LENGTH_SHORT).show();
    }

    private void stopBackground(){
        Intent i=new Intent(this,BackgroundPlaybackService.class).setAction(BackgroundPlaybackService.ACTION_STOP);startService(i);
        Toast.makeText(this,"Фондық ойнату тоқтатылды",Toast.LENGTH_SHORT).show();
    }

    private Button button(String s,int color){Button b=new Button(this);b.setText(s);b.setTextColor(Color.WHITE);b.setAllCaps(false);b.setBackground(round(color,16));return b;}
    private GradientDrawable round(int c,int r){GradientDrawable g=new GradientDrawable();g.setColor(c);g.setCornerRadius(dp(r));return g;}
    private int dp(int x){return(int)(x*getResources().getDisplayMetrics().density+.5f);}
}
