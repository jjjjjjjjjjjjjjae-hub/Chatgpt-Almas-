package kz.almas.tube;

import android.app.*;
import android.content.*;
import android.media.*;
import android.net.Uri;
import android.os.*;

public class BackgroundPlaybackService extends Service {
    public static final String ACTION_PLAY = "kz.almas.tube.PLAY_LOCAL";
    public static final String ACTION_STOP = "kz.almas.tube.STOP_LOCAL";
    public static final String EXTRA_URI = "uri";
    public static final String EXTRA_TITLE = "title";
    public static final String EXTRA_POSITION = "position";
    private static final String CHANNEL_ID = "almastube_playback";
    private static final int NOTIFICATION_ID = 2202;
    private MediaPlayer player;

    @Override public void onCreate(){
        super.onCreate();
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O){
            NotificationChannel c=new NotificationChannel(CHANNEL_ID,"AlmasTube playback",NotificationManager.IMPORTANCE_LOW);
            c.setDescription("Offline media background playback");
            ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(c);
        }
    }

    @Override public int onStartCommand(Intent intent,int flags,int startId){
        if(intent==null)return START_NOT_STICKY;
        String action=intent.getAction();
        if(ACTION_STOP.equals(action)){
            stopPlayback();
            stopSelf();
            return START_NOT_STICKY;
        }
        if(!ACTION_PLAY.equals(action))return START_NOT_STICKY;
        String raw=intent.getStringExtra(EXTRA_URI);
        String title=intent.getStringExtra(EXTRA_TITLE);
        int position=Math.max(0,intent.getIntExtra(EXTRA_POSITION,0));
        if(raw==null||raw.trim().isEmpty()){
            stopSelf();
            return START_NOT_STICKY;
        }
        startForeground(NOTIFICATION_ID,notification(title==null?"Offline media":title,"Дайындалуда…"));
        play(Uri.parse(raw),title==null?"Offline media":title,position);
        return START_NOT_STICKY;
    }

    private void play(Uri uri,String title,int position){
        stopPlayback();
        try{
            player=new MediaPlayer();
            player.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build());
            player.setWakeMode(getApplicationContext(),PowerManager.PARTIAL_WAKE_LOCK);
            player.setDataSource(this,uri);
            player.setOnPreparedListener(mp->{
                if(position>0){try{mp.seekTo(position);}catch(Exception ignored){}}
                mp.start();
                NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
                nm.notify(NOTIFICATION_ID,notification(title,"▶ Фонда ойнатылып жатыр"));
            });
            player.setOnCompletionListener(mp->{stopPlayback();stopSelf();});
            player.setOnErrorListener((mp,w,e)->{stopPlayback();stopSelf();return true;});
            player.prepareAsync();
        }catch(Exception e){
            stopPlayback();
            stopSelf();
        }
    }

    private Notification notification(String title,String text){
        Intent stop=new Intent(this,BackgroundPlaybackService.class).setAction(ACTION_STOP);
        PendingIntent stopPi=PendingIntent.getService(this,2,stop,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Intent open=new Intent(this,MainActivity.class);
        PendingIntent openPi=PendingIntent.getActivity(this,1,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b=Build.VERSION.SDK_INT>=Build.VERSION_CODES.O?new Notification.Builder(this,CHANNEL_ID):new Notification.Builder(this);
        return b.setSmallIcon(android.R.drawable.ic_media_play)
                .setContentTitle(title)
                .setContentText(text)
                .setContentIntent(openPi)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .addAction(android.R.drawable.ic_media_pause,"Тоқтату",stopPi)
                .build();
    }

    private void stopPlayback(){
        if(player!=null){
            try{if(player.isPlaying())player.stop();}catch(Exception ignored){}
            try{player.release();}catch(Exception ignored){}
            player=null;
        }
    }

    @Override public void onDestroy(){stopPlayback();super.onDestroy();}
    @Override public android.os.IBinder onBind(Intent i){return null;}
}
