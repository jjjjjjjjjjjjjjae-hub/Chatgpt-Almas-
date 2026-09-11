package kz.almas.tube;

import android.app.Activity;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.MediaController;
import android.widget.Toast;
import android.widget.VideoView;

public class LocalPlayerActivity extends Activity {
    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        String raw=getIntent().getStringExtra("uri");
        if(raw==null||raw.isEmpty()){finish();return;}
        VideoView v=new VideoView(this);v.setBackgroundColor(Color.BLACK);v.setLayoutParams(new ViewGroup.LayoutParams(-1,-1));
        MediaController c=new MediaController(this);c.setAnchorView(v);v.setMediaController(c);
        v.setOnErrorListener((mp,what,extra)->{Toast.makeText(this,"Видео ашылмады",Toast.LENGTH_LONG).show();return false;});
        v.setVideoURI(Uri.parse(raw));setContentView(v);v.requestFocus();v.start();
    }
}
