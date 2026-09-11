package kz.almas.tube;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.net.*;
import android.os.*;
import android.provider.Settings;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

public class MainActivity extends Activity {
    private static final int PICK_VIDEO = 41;
    private final ExecutorService worker = Executors.newFixedThreadPool(4);
    private final Handler ui = new Handler(Looper.getMainLooper());
    private SharedPreferences prefs;
    private EditText searchBox;
    private TextView status;
    private LinearLayout results, searchPane, offlinePane, favPane;

    static class VideoItem {
        String id, title, channel, thumb;
        VideoItem(String i,String t,String c,String th){id=i;title=t;channel=c;thumb=th;}
    }

    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        prefs=getSharedPreferences("almastube",MODE_PRIVATE);
        setContentView(buildUi());
        if(prefs.getString("api_key","").trim().isEmpty()) showApiDialog(true);
    }

    private View buildUi(){
        LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(dp(14),dp(14),dp(14),dp(14)); root.setBackgroundColor(Color.rgb(15,15,15));

        LinearLayout top=new LinearLayout(this); top.setGravity(Gravity.CENTER_VERTICAL);
        TextView title=text("AlmasTube",26,Color.WHITE,true); top.addView(title,new LinearLayout.LayoutParams(0,dp(50),1));
        Button key=new Button(this); key.setText("API KEY"); key.setOnClickListener(v->showApiDialog(false)); top.addView(key,new LinearLayout.LayoutParams(dp(105),dp(48)));
        root.addView(top);

        LinearLayout nav=new LinearLayout(this); nav.setOrientation(LinearLayout.HORIZONTAL);
        Button n1=navButton("Іздеу"), n2=navButton("Offline"), n3=navButton("Таңдаулы");
        nav.addView(n1,new LinearLayout.LayoutParams(0,dp(46),1)); nav.addView(n2,new LinearLayout.LayoutParams(0,dp(46),1)); nav.addView(n3,new LinearLayout.LayoutParams(0,dp(46),1));
        root.addView(nav);

        status=text("Дайын",13,Color.LTGRAY,false); status.setPadding(0,dp(8),0,dp(8)); root.addView(status);

        FrameLayout frame=new FrameLayout(this); root.addView(frame,new LinearLayout.LayoutParams(-1,0,1));
        searchPane=buildSearchPane(); offlinePane=buildOfflinePane(); favPane=buildFavPane();
        frame.addView(searchPane); frame.addView(offlinePane); frame.addView(favPane);
        showPane(searchPane);
        n1.setOnClickListener(v->showPane(searchPane)); n2.setOnClickListener(v->showPane(offlinePane)); n3.setOnClickListener(v->{showPane(favPane);showFavorites();});
        return root;
    }

    private Button navButton(String s){ Button b=new Button(this); b.setText(s); b.setAllCaps(false); return b; }

    private LinearLayout buildSearchPane(){
        LinearLayout pane=new LinearLayout(this); pane.setOrientation(LinearLayout.VERTICAL);
        LinearLayout row=new LinearLayout(this); row.setGravity(Gravity.CENTER_VERTICAL);
        searchBox=new EditText(this); searchBox.setHint("YouTube-тан іздеу"); searchBox.setHintTextColor(Color.GRAY); searchBox.setTextColor(Color.WHITE); searchBox.setSingleLine(true);
        Button go=new Button(this); go.setText("ІЗДЕУ"); go.setOnClickListener(v->searchYouTube());
        row.addView(searchBox,new LinearLayout.LayoutParams(0,dp(54),1)); row.addView(go,new LinearLayout.LayoutParams(dp(105),dp(54))); pane.addView(row);
        ScrollView sc=new ScrollView(this); results=new LinearLayout(this); results.setOrientation(LinearLayout.VERTICAL); sc.addView(results); pane.addView(sc,new LinearLayout.LayoutParams(-1,0,1));
        return pane;
    }

    private LinearLayout buildOfflinePane(){
        LinearLayout pane=new LinearLayout(this); pane.setOrientation(LinearLayout.VERTICAL); pane.setVisibility(View.GONE);
        TextView h=text("Offline",22,Color.WHITE,true); h.setPadding(0,dp(12),0,dp(8)); pane.addView(h);
        TextView info=text("YouTube Data API YouTube видеосын MP4 файл ретінде бермейді. Бұл бөлім телефондағы видеоларды офлайн ойнатады және өзіңе тиесілі/жүктеуге рұқсат етілген тікелей MP4 сілтемелерін жүктейді.",14,Color.LTGRAY,false); info.setPadding(0,0,0,dp(12)); pane.addView(info);
        Button pick=new Button(this); pick.setText("ТЕЛЕФОНДАҒЫ ВИДЕОНЫ АШУ"); pick.setOnClickListener(v->pickLocalVideo()); pane.addView(pick);
        Button download=new Button(this); download.setText("ТІКЕЛЕЙ MP4 ЖҮКТЕУ"); download.setOnClickListener(v->showDownloadDialog()); pane.addView(download);
        Button downloads=new Button(this); downloads.setText("ЖҮКТЕУЛЕРДІ АШУ"); downloads.setOnClickListener(v->{try{startActivity(new Intent(DownloadManager.ACTION_VIEW_DOWNLOADS));}catch(Exception e){toast("Downloads ашылмады");}}); pane.addView(downloads);
        TextView yt=text("YouTube видеосын ресми офлайн режимге сақтау қолжетімді болса, видеоны YouTube қолданбасынан ашып Download батырмасын қолдан.",13,Color.rgb(180,180,180),false); yt.setPadding(0,dp(14),0,0); pane.addView(yt);
        return pane;
    }

    private LinearLayout buildFavPane(){
        LinearLayout pane=new LinearLayout(this); pane.setOrientation(LinearLayout.VERTICAL); pane.setVisibility(View.GONE);
        TextView h=text("Таңдаулы",22,Color.WHITE,true); h.setPadding(0,dp(12),0,dp(8)); pane.addView(h);
        return pane;
    }

    private void showPane(View p){ searchPane.setVisibility(p==searchPane?View.VISIBLE:View.GONE); offlinePane.setVisibility(p==offlinePane?View.VISIBLE:View.GONE); favPane.setVisibility(p==favPane?View.VISIBLE:View.GONE); }

    private void showApiDialog(boolean required){
        final EditText in=new EditText(this); in.setSingleLine(true); in.setHint("AIza..."); in.setTextColor(Color.WHITE); in.setHintTextColor(Color.GRAY); in.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD); in.setText(prefs.getString("api_key","")); in.setPadding(dp(20),dp(8),dp(20),dp(8));
        AlertDialog d=new AlertDialog.Builder(this).setTitle("YouTube API Key").setMessage("Google Cloud-та алған YouTube Data API v3 кілтін енгіз.").setView(in).setPositiveButton("Сақтау",null).create();
        d.setCancelable(!required); if(!required)d.setButton(AlertDialog.BUTTON_NEGATIVE,"Бас тарту",(x,w)->{});
        d.setOnShowListener(x->d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{String k=in.getText().toString().trim(); if(k.length()<20){toast("API key дұрыс емес сияқты");return;} prefs.edit().putString("api_key",k).apply(); status.setText("API key сақталды"); d.dismiss();})); d.show();
    }

    private void searchYouTube(){
        final String key=prefs.getString("api_key","").trim(); final String q=searchBox.getText().toString().trim();
        if(key.isEmpty()){showApiDialog(true);return;} if(q.isEmpty()){toast("Іздеу сөзін жаз");return;}
        status.setText("Ізделіп жатыр…"); results.removeAllViews();
        worker.execute(()->{
            try{
                String url="https://www.googleapis.com/youtube/v3/search?part=snippet&type=video&safeSearch=moderate&maxResults=15&q="+URLEncoder.encode(q,"UTF-8")+"&key="+URLEncoder.encode(key,"UTF-8");
                String json=httpGet(url); JSONObject root=new JSONObject(json); JSONArray a=root.getJSONArray("items"); ArrayList<VideoItem> out=new ArrayList<>();
                for(int i=0;i<a.length();i++){JSONObject o=a.getJSONObject(i), sn=o.getJSONObject("snippet"); String id=o.getJSONObject("id").optString("videoId",""); if(id.isEmpty())continue; JSONObject thumbs=sn.getJSONObject("thumbnails"); JSONObject th=thumbs.optJSONObject("medium"); if(th==null)th=thumbs.optJSONObject("default"); out.add(new VideoItem(id,decode(sn.optString("title")),decode(sn.optString("channelTitle")),th==null?"":th.optString("url")));}
                ui.post(()->{status.setText(out.size()+" видео табылды");renderResults(out,results);});
            }catch(Exception e){ui.post(()->status.setText("Қате: "+friendlyError(e)));}
        });
    }

    private String httpGet(String u) throws Exception{
        HttpURLConnection c=(HttpURLConnection)new URL(u).openConnection(); c.setConnectTimeout(12000); c.setReadTimeout(15000); c.setRequestProperty("Accept","application/json"); c.setRequestProperty("User-Agent","AlmasTube/1.0 Android"); int code=c.getResponseCode(); InputStream is=code>=400?c.getErrorStream():c.getInputStream(); String body=readAll(is); if(code>=400)throw new IOException("HTTP "+code+" "+body); return body;
    }

    private void renderResults(List<VideoItem> items,LinearLayout target){
        target.removeAllViews(); if(items.isEmpty()){target.addView(text("Нәтиже жоқ",15,Color.LTGRAY,false));return;}
        for(VideoItem it:items){
            LinearLayout card=new LinearLayout(this); card.setOrientation(LinearLayout.HORIZONTAL); card.setPadding(0,dp(8),0,dp(8));
            ImageView img=new ImageView(this); img.setScaleType(ImageView.ScaleType.CENTER_CROP); img.setBackgroundColor(Color.DKGRAY); card.addView(img,new LinearLayout.LayoutParams(dp(145),dp(82)));
            LinearLayout right=new LinearLayout(this); right.setOrientation(LinearLayout.VERTICAL); right.setPadding(dp(10),0,0,0); TextView tt=text(it.title,15,Color.WHITE,true); TextView ch=text(it.channel,12,Color.LTGRAY,false); right.addView(tt); right.addView(ch);
            LinearLayout actions=new LinearLayout(this); Button play=new Button(this); play.setText("▶"); play.setOnClickListener(v->openPlayer(it)); Button fav=new Button(this); fav.setText(isFavorite(it.id)?"★":"☆"); fav.setOnClickListener(v->{toggleFavorite(it); fav.setText(isFavorite(it.id)?"★":"☆");}); actions.addView(play,new LinearLayout.LayoutParams(0,dp(42),1)); actions.addView(fav,new LinearLayout.LayoutParams(0,dp(42),1)); right.addView(actions); card.addView(right,new LinearLayout.LayoutParams(0,-2,1)); card.setOnClickListener(v->openPlayer(it)); target.addView(card);
            if(!it.thumb.isEmpty())loadImage(it.thumb,img);
        }
    }

    private void loadImage(String u,ImageView iv){ worker.execute(()->{try{HttpURLConnection c=(HttpURLConnection)new URL(u).openConnection();c.setConnectTimeout(8000);c.setReadTimeout(8000);Bitmap b=BitmapFactory.decodeStream(c.getInputStream());if(b!=null)ui.post(()->iv.setImageBitmap(b));}catch(Exception ignored){}}); }

    private void openPlayer(VideoItem it){ Intent i=new Intent(this,PlayerActivity.class); i.putExtra("video_id",it.id); i.putExtra("title",it.title); startActivity(i); }

    private void toggleFavorite(VideoItem it){Set<String>s=new HashSet<>(prefs.getStringSet("favorites",Collections.emptySet()));String old=findFavorite(s,it.id);if(old!=null)s.remove(old);else s.add(pack(it));prefs.edit().putStringSet("favorites",s).apply();}
    private boolean isFavorite(String id){return findFavorite(prefs.getStringSet("favorites",Collections.emptySet()),id)!=null;}
    private String findFavorite(Set<String>s,String id){for(String x:s)if(x.startsWith(id+"\t"))return x;return null;}
    private String pack(VideoItem it){return it.id+"\t"+b64(it.title)+"\t"+b64(it.channel);}
    private VideoItem unpack(String x){try{String[]p=x.split("\\t",3);return new VideoItem(p[0],unb64(p[1]),unb64(p[2]),"https://i.ytimg.com/vi/"+p[0]+"/mqdefault.jpg");}catch(Exception e){return null;}}
    private String b64(String s){return android.util.Base64.encodeToString(s.getBytes(StandardCharsets.UTF_8),android.util.Base64.NO_WRAP);}
    private String unb64(String s){return new String(android.util.Base64.decode(s,android.util.Base64.NO_WRAP),StandardCharsets.UTF_8);}

    private void showFavorites(){
        while(favPane.getChildCount()>1)favPane.removeViewAt(1); ArrayList<VideoItem>a=new ArrayList<>(); for(String x:prefs.getStringSet("favorites",Collections.emptySet())){VideoItem v=unpack(x);if(v!=null)a.add(v);} LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);ScrollView sc=new ScrollView(this);sc.addView(box);favPane.addView(sc,new LinearLayout.LayoutParams(-1,0,1));renderResults(a,box);
    }

    private void pickLocalVideo(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("video/*");i.addCategory(Intent.CATEGORY_OPENABLE);i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);startActivityForResult(i,PICK_VIDEO);}
    @Override protected void onActivityResult(int req,int res,Intent data){super.onActivityResult(req,res,data);if(req==PICK_VIDEO&&res==RESULT_OK&&data!=null&&data.getData()!=null){Uri u=data.getData();try{getContentResolver().takePersistableUriPermission(u,Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(Exception ignored){}Intent p=new Intent(this,LocalPlayerActivity.class);p.putExtra("uri",u.toString());startActivity(p);}}

    private void showDownloadDialog(){
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(18),0,dp(18),0);EditText url=new EditText(this);url.setHint("https://example.com/video.mp4");url.setSingleLine(true);EditText name=new EditText(this);name.setHint("video.mp4");name.setSingleLine(true);box.addView(url);box.addView(name);
        new AlertDialog.Builder(this).setTitle("Тікелей MP4 жүктеу").setMessage("Тек өзіңе тиесілі немесе жүктеуге рұқсат етілген файл сілтемесін қолдан.").setView(box).setNegativeButton("Бас тарту",null).setPositiveButton("Жүктеу",(d,w)->downloadDirect(url.getText().toString().trim(),name.getText().toString().trim())).show();
    }

    private void downloadDirect(String raw,String name){
        try{Uri u=Uri.parse(raw);String host=u.getHost()==null?"":u.getHost().toLowerCase(Locale.ROOT);String path=u.getPath()==null?"":u.getPath().toLowerCase(Locale.ROOT);if(!"https".equalsIgnoreCase(u.getScheme())){toast("Тек HTTPS сілтеме");return;}if(host.contains("youtube.com")||host.contains("youtu.be")||host.contains("googlevideo.com")){toast("YouTube stream жүктеу бұл бөлімде қолдау таппайды");return;}if(!path.endsWith(".mp4")){toast("Сілтеме .mp4 файлға апаруы керек");return;}if(name.isEmpty())name=path.substring(path.lastIndexOf('/')+1);name=name.replaceAll("[^A-Za-z0-9._-]","_");if(!name.toLowerCase(Locale.ROOT).endsWith(".mp4"))name+=".mp4";DownloadManager.Request r=new DownloadManager.Request(u).setTitle(name).setDescription("AlmasTube offline video").setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED).setAllowedOverMetered(true).setAllowedOverRoaming(false).setDestinationInExternalPublicDir(Environment.DIRECTORY_MOVIES,"AlmasTube/"+name);((DownloadManager)getSystemService(DOWNLOAD_SERVICE)).enqueue(r);toast("Жүктеу басталды");}catch(Exception e){toast("Жүктеу қатесі: "+e.getMessage());}
    }

    private String readAll(InputStream in)throws Exception{if(in==null)return"";BufferedReader r=new BufferedReader(new InputStreamReader(in,StandardCharsets.UTF_8));StringBuilder b=new StringBuilder();String l;while((l=r.readLine())!=null)b.append(l);return b.toString();}
    private String decode(String s){return s.replace("&amp;","&").replace("&quot;","\"").replace("&#39;","'").replace("&lt;","<").replace("&gt;",">");}
    private String friendlyError(Exception e){String m=e.getMessage()==null?e.getClass().getSimpleName():e.getMessage();if(m.contains("quotaExceeded"))return"YouTube API квотасы бітті";if(m.length()>180)m=m.substring(0,180);return m;}
    private TextView text(String s,int size,int color,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);if(bold)t.setTypeface(Typeface.DEFAULT_BOLD);return t;}
    private int dp(int x){return(int)(x*getResources().getDisplayMetrics().density+.5f);}
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
}
