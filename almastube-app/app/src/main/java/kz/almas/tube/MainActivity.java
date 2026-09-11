package kz.almas.tube;

import android.app.*;
import android.content.*;
import android.database.Cursor;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.net.*;
import android.os.*;
import android.provider.OpenableColumns;
import android.text.Html;
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
    private static final int BG = Color.rgb(10,10,14);
    private static final int PANEL = Color.rgb(24,24,31);
    private static final int PANEL_2 = Color.rgb(34,34,43);
    private static final int RED = Color.rgb(255,48,64);
    private static final int MUTED = Color.rgb(170,170,182);

    private final ExecutorService worker = Executors.newFixedThreadPool(4);
    private final Handler ui = new Handler(Looper.getMainLooper());
    private SharedPreferences prefs;
    private EditText searchBox;
    private TextView status;
    private LinearLayout results, homePane, offlinePane, favPane, settingsPane;
    private LinearLayout offlineList;
    private final ArrayList<Button> navButtons = new ArrayList<>();

    static class VideoItem {
        String id, title, channel, thumb;
        VideoItem(String i,String t,String c,String th){id=i;title=t;channel=c;thumb=th;}
    }

    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        prefs=getSharedPreferences("almastube",MODE_PRIVATE);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        setContentView(buildUi());
        if(prefs.getString("api_key","").trim().isEmpty()) showApiDialog(true);
    }

    private View buildUi(){
        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        root.setPadding(dp(14),dp(10),dp(14),dp(10));

        LinearLayout top=new LinearLayout(this); top.setGravity(Gravity.CENTER_VERTICAL); top.setPadding(0,dp(2),0,dp(10));
        TextView logo=text("▶",18,Color.WHITE,true); logo.setGravity(Gravity.CENTER); logo.setBackground(round(RED,999));
        top.addView(logo,new LinearLayout.LayoutParams(dp(44),dp(44)));
        LinearLayout brand=new LinearLayout(this); brand.setOrientation(LinearLayout.VERTICAL); brand.setPadding(dp(10),0,0,0);
        brand.addView(text("AlmasTube",25,Color.WHITE,true)); brand.addView(text("watch • save • offline",11,MUTED,false));
        top.addView(brand,new LinearLayout.LayoutParams(0,dp(50),1));
        Button api=smallButton("API"); api.setOnClickListener(v->showApiDialog(false)); top.addView(api,new LinearLayout.LayoutParams(dp(72),dp(42)));
        root.addView(top);

        status=text("Дайын",12,MUTED,false); status.setPadding(dp(12),dp(8),dp(12),dp(8)); status.setBackground(round(PANEL,18));
        root.addView(status,new LinearLayout.LayoutParams(-1,-2));

        FrameLayout frame=new FrameLayout(this); root.addView(frame,new LinearLayout.LayoutParams(-1,0,1));
        homePane=buildHomePane(); offlinePane=buildOfflinePane(); favPane=buildFavPane(); settingsPane=buildSettingsPane();
        frame.addView(homePane); frame.addView(offlinePane); frame.addView(favPane); frame.addView(settingsPane);

        LinearLayout nav=new LinearLayout(this); nav.setGravity(Gravity.CENTER); nav.setPadding(dp(4),dp(7),dp(4),0); nav.setBackground(round(PANEL,24));
        Button home=bottomButton("⌂\nHome"), off=bottomButton("↓\nOffline"), fav=bottomButton("★\nSaved"), set=bottomButton("⚙\nSettings");
        navButtons.add(home);navButtons.add(off);navButtons.add(fav);navButtons.add(set);
        for(Button b:navButtons)nav.addView(b,new LinearLayout.LayoutParams(0,dp(58),1));
        root.addView(nav);

        home.setOnClickListener(v->showPane(homePane,home));
        off.setOnClickListener(v->{showPane(offlinePane,off);refreshOffline();});
        fav.setOnClickListener(v->{showPane(favPane,fav);showFavorites();});
        set.setOnClickListener(v->{showPane(settingsPane,set);refreshSettings();});
        showPane(homePane,home);
        return root;
    }

    private LinearLayout buildHomePane(){
        LinearLayout pane=new LinearLayout(this); pane.setOrientation(LinearLayout.VERTICAL); pane.setPadding(0,dp(14),0,0);
        TextView hello=text("Не көргің келеді?",23,Color.WHITE,true); pane.addView(hello);
        TextView hint=text("YouTube Data API арқылы іздеу",13,MUTED,false); hint.setPadding(0,dp(2),0,dp(12)); pane.addView(hint);

        LinearLayout row=new LinearLayout(this); row.setGravity(Gravity.CENTER_VERTICAL); row.setBackground(round(PANEL,22)); row.setPadding(dp(6),dp(4),dp(5),dp(4));
        searchBox=new EditText(this); searchBox.setHint("Видео, ән, канал..."); searchBox.setHintTextColor(Color.rgb(120,120,132)); searchBox.setTextColor(Color.WHITE); searchBox.setSingleLine(true); searchBox.setBackgroundColor(Color.TRANSPARENT); searchBox.setPadding(dp(10),0,dp(8),0);
        Button go=accentButton("Іздеу"); go.setOnClickListener(v->searchYouTube());
        row.addView(searchBox,new LinearLayout.LayoutParams(0,dp(54),1)); row.addView(go,new LinearLayout.LayoutParams(dp(96),dp(48))); pane.addView(row);
        searchBox.setOnEditorActionListener((v,a,e)->{searchYouTube();return true;});

        TextView section=text("Нәтижелер",17,Color.WHITE,true); section.setPadding(0,dp(16),0,dp(8)); pane.addView(section);
        ScrollView sc=new ScrollView(this); sc.setFillViewport(true); results=new LinearLayout(this); results.setOrientation(LinearLayout.VERTICAL); sc.addView(results); pane.addView(sc,new LinearLayout.LayoutParams(-1,0,1));
        TextView empty=text("Іздеу жолағына сұраныс жаз.",14,MUTED,false); empty.setGravity(Gravity.CENTER); empty.setPadding(0,dp(45),0,0); results.addView(empty);
        return pane;
    }

    private LinearLayout buildOfflinePane(){
        LinearLayout pane=new LinearLayout(this); pane.setOrientation(LinearLayout.VERTICAL); pane.setPadding(0,dp(14),0,0); pane.setVisibility(View.GONE);
        pane.addView(text("Offline кітапхана",23,Color.WHITE,true));
        TextView sub=text("Жүктелген немесе телефоннан импортталған видеолар интернетсіз ашылады.",13,MUTED,false); sub.setPadding(0,dp(3),0,dp(12)); pane.addView(sub);

        LinearLayout actions=new LinearLayout(this); actions.setOrientation(LinearLayout.HORIZONTAL);
        Button add=accentButton("＋ Импорт"); add.setOnClickListener(v->pickLocalVideo());
        Button dl=darkButton("↓ MP4 жүктеу"); dl.setOnClickListener(v->showDownloadDialog());
        actions.addView(add,new LinearLayout.LayoutParams(0,dp(50),1)); LinearLayout.LayoutParams dlp=new LinearLayout.LayoutParams(0,dp(50),1); dlp.setMargins(dp(8),0,0,0); actions.addView(dl,dlp); pane.addView(actions);

        LinearLayout line=new LinearLayout(this); line.setGravity(Gravity.CENTER_VERTICAL); line.setPadding(0,dp(13),0,dp(8));
        TextView h=text("Менің видеоларым",17,Color.WHITE,true); line.addView(h,new LinearLayout.LayoutParams(0,-2,1));
        Button ref=smallButton("↻ Жаңарту"); ref.setOnClickListener(v->refreshOffline()); line.addView(ref,new LinearLayout.LayoutParams(dp(108),dp(40))); pane.addView(line);

        ScrollView sc=new ScrollView(this); offlineList=new LinearLayout(this); offlineList.setOrientation(LinearLayout.VERTICAL); sc.addView(offlineList); pane.addView(sc,new LinearLayout.LayoutParams(-1,0,1));
        return pane;
    }

    private LinearLayout buildFavPane(){
        LinearLayout pane=new LinearLayout(this); pane.setOrientation(LinearLayout.VERTICAL); pane.setPadding(0,dp(14),0,0); pane.setVisibility(View.GONE);
        pane.addView(text("Saved",23,Color.WHITE,true)); TextView s=text("Ұнаған YouTube видеоларың",13,MUTED,false);s.setPadding(0,dp(3),0,dp(10));pane.addView(s);
        return pane;
    }

    private LinearLayout buildSettingsPane(){
        LinearLayout pane=new LinearLayout(this); pane.setOrientation(LinearLayout.VERTICAL); pane.setPadding(0,dp(14),0,0); pane.setVisibility(View.GONE);
        pane.addView(text("Settings",23,Color.WHITE,true)); TextView s=text("API және жергілікті деректер",13,MUTED,false);s.setPadding(0,dp(3),0,dp(14));pane.addView(s);
        return pane;
    }

    private void refreshSettings(){
        while(settingsPane.getChildCount()>2)settingsPane.removeViewAt(2);
        LinearLayout apiCard=card();
        apiCard.addView(text("YouTube API Key",16,Color.WHITE,true));
        String key=prefs.getString("api_key",""); String masked=key.length()>10?key.substring(0,5)+"••••••••"+key.substring(key.length()-4):"Орнатылмаған";
        TextView kv=text(masked,13,MUTED,false);kv.setPadding(0,dp(5),0,dp(10));apiCard.addView(kv);
        Button change=accentButton("API key ауыстыру");change.setOnClickListener(v->showApiDialog(false));apiCard.addView(change,new LinearLayout.LayoutParams(-1,dp(48)));settingsPane.addView(apiCard);

        LinearLayout data=card(); LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2);cp.setMargins(0,dp(10),0,0);
        data.addView(text("Local data",16,Color.WHITE,true)); TextView info=text("Saved: "+prefs.getStringSet("favorites",Collections.emptySet()).size()+"  •  Offline: "+offlineCount(),13,MUTED,false); info.setPadding(0,dp(5),0,dp(10)); data.addView(info);
        Button clear=darkButton("Saved тізімін тазалау");clear.setOnClickListener(v->new AlertDialog.Builder(this).setTitle("Saved тазалау").setMessage("Таңдаулы тізім өшіріледі.").setNegativeButton("Жоқ",null).setPositiveButton("Тазалау",(d,w)->{prefs.edit().remove("favorites").apply();refreshSettings();}).show());data.addView(clear,new LinearLayout.LayoutParams(-1,dp(48)));settingsPane.addView(data,cp);

        TextView ver=text("AlmasTube v2.0 • YouTube Data API v3",12,Color.rgb(105,105,116),false);ver.setGravity(Gravity.CENTER);ver.setPadding(0,dp(24),0,0);settingsPane.addView(ver);
    }

    private int offlineCount(){return prefs.getStringSet("downloads",Collections.emptySet()).size()+prefs.getStringSet("local_videos",Collections.emptySet()).size();}

    private void showPane(View pane,Button selected){
        homePane.setVisibility(pane==homePane?View.VISIBLE:View.GONE); offlinePane.setVisibility(pane==offlinePane?View.VISIBLE:View.GONE); favPane.setVisibility(pane==favPane?View.VISIBLE:View.GONE); settingsPane.setVisibility(pane==settingsPane?View.VISIBLE:View.GONE);
        for(Button b:navButtons){b.setTextColor(b==selected?Color.WHITE:MUTED);b.setBackground(b==selected?round(RED,18):round(Color.TRANSPARENT,18));}
    }

    private void showApiDialog(boolean required){
        EditText in=new EditText(this); in.setSingleLine(true); in.setHint("AIza..."); in.setTextColor(Color.WHITE); in.setHintTextColor(Color.GRAY); in.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD); in.setText(prefs.getString("api_key","")); in.setPadding(dp(18),dp(10),dp(18),dp(10)); in.setBackground(round(PANEL_2,16));
        LinearLayout wrap=new LinearLayout(this);wrap.setPadding(dp(18),0,dp(18),0);wrap.addView(in,new LinearLayout.LayoutParams(-1,dp(54)));
        AlertDialog.Builder b=new AlertDialog.Builder(this).setTitle("YouTube API Key").setMessage("Google Cloud-та алған YouTube Data API v3 кілтін енгіз. Кілт тек осы телефонда сақталады.").setView(wrap).setPositiveButton("Сақтау",null);
        if(!required)b.setNegativeButton("Бас тарту",null);
        AlertDialog d=b.create(); d.setCancelable(!required); d.setCanceledOnTouchOutside(!required);
        d.setOnShowListener(x->d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{String k=in.getText().toString().trim();if(k.length()<20){toast("API key дұрыс емес сияқты");return;}prefs.edit().putString("api_key",k).apply();status.setText("✓ API key сақталды");d.dismiss();}));d.show();
    }

    private void searchYouTube(){
        String key=prefs.getString("api_key","").trim(); String q=searchBox.getText().toString().trim();
        if(key.isEmpty()){showApiDialog(true);return;} if(q.isEmpty()){toast("Іздеу сөзін жаз");return;}
        status.setText("Ізделіп жатыр…"); results.removeAllViews();
        worker.execute(()->{
            try{
                String url="https://www.googleapis.com/youtube/v3/search?part=snippet&type=video&safeSearch=moderate&maxResults=20&q="+URLEncoder.encode(q,"UTF-8")+"&key="+URLEncoder.encode(key,"UTF-8");
                JSONObject root=new JSONObject(httpGet(url)); JSONArray a=root.getJSONArray("items"); ArrayList<VideoItem> out=new ArrayList<>();
                for(int i=0;i<a.length();i++){JSONObject o=a.getJSONObject(i),sn=o.getJSONObject("snippet");String id=o.getJSONObject("id").optString("videoId","");if(id.isEmpty())continue;JSONObject thumbs=sn.getJSONObject("thumbnails");JSONObject th=thumbs.optJSONObject("medium");if(th==null)th=thumbs.optJSONObject("default");out.add(new VideoItem(id,decode(sn.optString("title")),decode(sn.optString("channelTitle")),th==null?"":th.optString("url")));}
                ui.post(()->{status.setText("✓ "+out.size()+" видео табылды");renderResults(out,results);});
            }catch(Exception e){ui.post(()->{status.setText("Қате: "+friendlyError(e));results.addView(text("API key, интернет және YouTube Data API v3 баптауын тексер.",14,MUTED,false));});}
        });
    }

    private String httpGet(String u)throws Exception{
        HttpURLConnection c=(HttpURLConnection)new URL(u).openConnection();c.setConnectTimeout(12000);c.setReadTimeout(15000);c.setRequestProperty("Accept","application/json");c.setRequestProperty("User-Agent","AlmasTube/2.0 Android");int code=c.getResponseCode();InputStream is=code>=400?c.getErrorStream():c.getInputStream();String body=readAll(is);if(code>=400)throw new IOException("HTTP "+code+" "+body);return body;
    }

    private void renderResults(List<VideoItem> items,LinearLayout target){
        target.removeAllViews(); if(items.isEmpty()){TextView e=text("Нәтиже жоқ",14,MUTED,false);e.setPadding(0,dp(30),0,0);e.setGravity(Gravity.CENTER);target.addView(e);return;}
        for(VideoItem it:items){
            LinearLayout card=card(); card.setOrientation(LinearLayout.VERTICAL); LinearLayout.LayoutParams cpar=new LinearLayout.LayoutParams(-1,-2);cpar.setMargins(0,0,0,dp(10));target.addView(card,cpar);
            ImageView img=new ImageView(this);img.setScaleType(ImageView.ScaleType.CENTER_CROP);img.setBackgroundColor(PANEL_2);img.setClipToOutline(true);img.setBackground(round(PANEL_2,16));card.addView(img,new LinearLayout.LayoutParams(-1,dp(178)));
            TextView tt=text(it.title,16,Color.WHITE,true);tt.setPadding(0,dp(10),0,dp(3));card.addView(tt);card.addView(text(it.channel,13,MUTED,false));
            LinearLayout actions=new LinearLayout(this);actions.setPadding(0,dp(9),0,0);Button play=accentButton("▶ Ойнату");play.setOnClickListener(v->openPlayer(it));Button fav=darkButton(isFavorite(it.id)?"★ Saved":"☆ Save");fav.setOnClickListener(v->{toggleFavorite(it);fav.setText(isFavorite(it.id)?"★ Saved":"☆ Save");});actions.addView(play,new LinearLayout.LayoutParams(0,dp(46),1));LinearLayout.LayoutParams fp=new LinearLayout.LayoutParams(0,dp(46),1);fp.setMargins(dp(8),0,0,0);actions.addView(fav,fp);card.addView(actions);card.setOnClickListener(v->openPlayer(it));if(!it.thumb.isEmpty())loadImage(it.thumb,img);
        }
    }

    private void loadImage(String u,ImageView iv){worker.execute(()->{try{HttpURLConnection c=(HttpURLConnection)new URL(u).openConnection();c.setConnectTimeout(8000);c.setReadTimeout(8000);Bitmap b=BitmapFactory.decodeStream(c.getInputStream());if(b!=null)ui.post(()->iv.setImageBitmap(b));}catch(Exception ignored){}});}
    private void openPlayer(VideoItem it){Intent i=new Intent(this,PlayerActivity.class);i.putExtra("video_id",it.id);i.putExtra("title",it.title);startActivity(i);}

    private void toggleFavorite(VideoItem it){Set<String>s=new HashSet<>(prefs.getStringSet("favorites",Collections.emptySet()));String old=findFavorite(s,it.id);if(old!=null)s.remove(old);else s.add(pack(it));prefs.edit().putStringSet("favorites",s).apply();}
    private boolean isFavorite(String id){return findFavorite(prefs.getStringSet("favorites",Collections.emptySet()),id)!=null;}
    private String findFavorite(Set<String>s,String id){for(String x:s)if(x.startsWith(id+"\t"))return x;return null;}
    private String pack(VideoItem it){return it.id+"\t"+b64(it.title)+"\t"+b64(it.channel);}
    private VideoItem unpack(String x){try{String[]p=x.split("\\t",3);return new VideoItem(p[0],unb64(p[1]),unb64(p[2]),"https://i.ytimg.com/vi/"+p[0]+"/mqdefault.jpg");}catch(Exception e){return null;}}

    private void showFavorites(){
        while(favPane.getChildCount()>2)favPane.removeViewAt(2);ArrayList<VideoItem>a=new ArrayList<>();for(String x:prefs.getStringSet("favorites",Collections.emptySet())){VideoItem v=unpack(x);if(v!=null)a.add(v);}LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);ScrollView sc=new ScrollView(this);sc.addView(box);favPane.addView(sc,new LinearLayout.LayoutParams(-1,0,1));renderResults(a,box);
    }

    private void pickLocalVideo(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("video/*");i.addCategory(Intent.CATEGORY_OPENABLE);i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);startActivityForResult(i,PICK_VIDEO);}
    @Override protected void onActivityResult(int req,int res,Intent data){super.onActivityResult(req,res,data);if(req==PICK_VIDEO&&res==RESULT_OK&&data!=null&&data.getData()!=null){Uri u=data.getData();try{getContentResolver().takePersistableUriPermission(u,Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(Exception ignored){}String name=queryName(u);Set<String>s=new HashSet<>(prefs.getStringSet("local_videos",Collections.emptySet()));s.add(b64(u.toString())+"\t"+b64(name));prefs.edit().putStringSet("local_videos",s).apply();refreshOffline();openLocal(u.toString());}}

    private String queryName(Uri u){String n="Local video";try(Cursor c=getContentResolver().query(u,null,null,null,null)){if(c!=null&&c.moveToFirst()){int i=c.getColumnIndex(OpenableColumns.DISPLAY_NAME);if(i>=0)n=c.getString(i);}}catch(Exception ignored){}return n==null?"Local video":n;}

    private void showDownloadDialog(){
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(18),0,dp(18),0);EditText url=new EditText(this);url.setHint("https://site.com/video.mp4");url.setSingleLine(true);url.setTextColor(Color.WHITE);url.setHintTextColor(Color.GRAY);url.setBackground(round(PANEL_2,14));url.setPadding(dp(12),0,dp(12),0);EditText name=new EditText(this);name.setHint("video.mp4 (міндетті емес)");name.setSingleLine(true);name.setTextColor(Color.WHITE);name.setHintTextColor(Color.GRAY);name.setBackground(round(PANEL_2,14));name.setPadding(dp(12),0,dp(12),0);box.addView(url,new LinearLayout.LayoutParams(-1,dp(52)));LinearLayout.LayoutParams np=new LinearLayout.LayoutParams(-1,dp(52));np.setMargins(0,dp(8),0,0);box.addView(name,np);
        new AlertDialog.Builder(this).setTitle("Offline MP4 жүктеу").setMessage("Өзіңе тиесілі немесе жүктеуге рұқсат етілген тікелей HTTPS MP4 сілтемесін енгіз.").setView(box).setNegativeButton("Бас тарту",null).setPositiveButton("Жүктеу",(d,w)->downloadDirect(url.getText().toString().trim(),name.getText().toString().trim())).show();
    }

    private void downloadDirect(String raw,String requested){
        try{
            Uri u=Uri.parse(raw);String host=u.getHost()==null?"":u.getHost().toLowerCase(Locale.ROOT);String path=u.getPath()==null?"":u.getPath().toLowerCase(Locale.ROOT);
            if(!"https".equalsIgnoreCase(u.getScheme())){toast("Тек HTTPS сілтеме");return;}
            if(host.contains("youtube.com")||host.contains("youtu.be")||host.contains("googlevideo.com")){toast("YouTube stream тікелей жүктелмейді");return;}
            if(!path.endsWith(".mp4")){toast("Сілтеме .mp4 файлға апаруы керек");return;}
            String name=requested.trim();if(name.isEmpty())name=path.substring(path.lastIndexOf('/')+1);name=name.replaceAll("[^A-Za-z0-9._-]","_");if(!name.toLowerCase(Locale.ROOT).endsWith(".mp4"))name+=".mp4";final String finalName=name;
            DownloadManager.Request r=new DownloadManager.Request(u).setTitle(finalName).setDescription("AlmasTube Offline").setMimeType("video/mp4").setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED).setAllowedOverMetered(true).setAllowedOverRoaming(false).setDestinationInExternalPublicDir(Environment.DIRECTORY_MOVIES,"AlmasTube/"+finalName);
            long id=((DownloadManager)getSystemService(DOWNLOAD_SERVICE)).enqueue(r);Set<String>s=new HashSet<>(prefs.getStringSet("downloads",Collections.emptySet()));s.add(id+"\t"+b64(finalName));prefs.edit().putStringSet("downloads",s).apply();toast("Жүктеу басталды");refreshOffline();
        }catch(Exception e){toast("Жүктеу басталмады: "+e.getMessage());}
    }

    private void refreshOffline(){
        if(offlineList==null)return;offlineList.removeAllViews();int count=0;
        for(String raw:prefs.getStringSet("downloads",Collections.emptySet())){try{String[]p=raw.split("\\t",2);long id=Long.parseLong(p[0]);String name=unb64(p[1]);addDownloadCard(id,name);count++;}catch(Exception ignored){}}
        for(String raw:prefs.getStringSet("local_videos",Collections.emptySet())){try{String[]p=raw.split("\\t",2);String uri=unb64(p[0]),name=unb64(p[1]);addLocalCard(uri,name);count++;}catch(Exception ignored){}}
        if(count==0){TextView e=text("Offline видео әлі жоқ.\nЖоғарыдан MP4 жүкте немесе телефоннан импортта.",14,MUTED,false);e.setGravity(Gravity.CENTER);e.setPadding(dp(12),dp(55),dp(12),0);offlineList.addView(e);}
    }

    private void addDownloadCard(long id,String name){
        LinearLayout c=card();LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2);cp.setMargins(0,0,0,dp(9));offlineList.addView(c,cp);c.addView(text(name,15,Color.WHITE,true));
        DownloadManager dm=(DownloadManager)getSystemService(DOWNLOAD_SERVICE);String state="Күйі белгісіз";int statusCode=-1;long done=0,total=-1;
        try(Cursor q=dm.query(new DownloadManager.Query().setFilterById(id))){if(q!=null&&q.moveToFirst()){statusCode=q.getInt(q.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));done=q.getLong(q.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR));total=q.getLong(q.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES));if(statusCode==DownloadManager.STATUS_SUCCESSFUL)state="✓ Дайын • Offline";else if(statusCode==DownloadManager.STATUS_RUNNING)state="↓ Жүктелуде";else if(statusCode==DownloadManager.STATUS_PENDING)state="Кезекте";else if(statusCode==DownloadManager.STATUS_PAUSED)state="Кідіртілді";else if(statusCode==DownloadManager.STATUS_FAILED)state="Жүктеу қатесі";}}
        TextView st=text(state+(total>0?" • "+human(done)+" / "+human(total):""),12,statusCode==DownloadManager.STATUS_SUCCESSFUL?Color.rgb(104,220,145):MUTED,false);st.setPadding(0,dp(5),0,dp(8));c.addView(st);
        LinearLayout a=new LinearLayout(this);Button open=accentButton("▶ Ашу");final int sc=statusCode;open.setEnabled(sc==DownloadManager.STATUS_SUCCESSFUL);open.setAlpha(open.isEnabled()?1f:.45f);open.setOnClickListener(v->{Uri u=dm.getUriForDownloadedFile(id);if(u!=null)openLocal(u.toString());else toast("Файл табылмады");});Button remove=darkButton("Тізімнен өшіру");remove.setOnClickListener(v->{removeDownloadEntry(id);refreshOffline();});a.addView(open,new LinearLayout.LayoutParams(0,dp(44),1));LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(0,dp(44),1);rp.setMargins(dp(8),0,0,0);a.addView(remove,rp);c.addView(a);
    }

    private void addLocalCard(String uri,String name){
        LinearLayout c=card();LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2);cp.setMargins(0,0,0,dp(9));offlineList.addView(c,cp);c.addView(text(name,15,Color.WHITE,true));TextView st=text("✓ Телефоннан импортталған • Offline",12,Color.rgb(104,220,145),false);st.setPadding(0,dp(5),0,dp(8));c.addView(st);LinearLayout a=new LinearLayout(this);Button open=accentButton("▶ Ашу");open.setOnClickListener(v->openLocal(uri));Button rem=darkButton("Тізімнен өшіру");rem.setOnClickListener(v->{removeLocalEntry(uri);refreshOffline();});a.addView(open,new LinearLayout.LayoutParams(0,dp(44),1));LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(0,dp(44),1);rp.setMargins(dp(8),0,0,0);a.addView(rem,rp);c.addView(a);
    }

    private void removeDownloadEntry(long id){Set<String>s=new HashSet<>(prefs.getStringSet("downloads",Collections.emptySet()));String hit=null;for(String x:s)if(x.startsWith(id+"\t")){hit=x;break;}if(hit!=null)s.remove(hit);prefs.edit().putStringSet("downloads",s).apply();}
    private void removeLocalEntry(String uri){Set<String>s=new HashSet<>(prefs.getStringSet("local_videos",Collections.emptySet()));String hit=null;for(String x:s){try{if(unb64(x.split("\\t",2)[0]).equals(uri)){hit=x;break;}}catch(Exception ignored){}}if(hit!=null)s.remove(hit);prefs.edit().putStringSet("local_videos",s).apply();}
    private void openLocal(String uri){Intent p=new Intent(this,LocalPlayerActivity.class);p.putExtra("uri",uri);startActivity(p);}

    private LinearLayout card(){LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);c.setPadding(dp(12),dp(12),dp(12),dp(12));c.setBackground(round(PANEL,18));return c;}
    private TextView text(String s,int size,int color,boolean bold){TextView v=new TextView(this);v.setText(s);v.setTextSize(size);v.setTextColor(color);if(bold)v.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);return v;}
    private Button accentButton(String s){Button b=new Button(this);b.setText(s);b.setTextColor(Color.WHITE);b.setTextSize(13);b.setAllCaps(false);b.setBackground(round(RED,16));return b;}
    private Button darkButton(String s){Button b=new Button(this);b.setText(s);b.setTextColor(Color.WHITE);b.setTextSize(13);b.setAllCaps(false);b.setBackground(round(PANEL_2,16));return b;}
    private Button smallButton(String s){Button b=darkButton(s);b.setTextSize(12);return b;}
    private Button bottomButton(String s){Button b=new Button(this);b.setText(s);b.setTextSize(11);b.setAllCaps(false);b.setGravity(Gravity.CENTER);b.setPadding(0,0,0,0);b.setTextColor(MUTED);b.setBackground(round(Color.TRANSPARENT,18));return b;}
    private GradientDrawable round(int color,float radius){GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(dp((int)radius));return g;}
    private int dp(int v){return(int)(v*getResources().getDisplayMetrics().density+.5f);}
    private String b64(String s){return android.util.Base64.encodeToString(s.getBytes(StandardCharsets.UTF_8),android.util.Base64.NO_WRAP);}
    private String unb64(String s){return new String(android.util.Base64.decode(s,android.util.Base64.NO_WRAP),StandardCharsets.UTF_8);}
    private String decode(String s){if(Build.VERSION.SDK_INT>=24)return Html.fromHtml(s,Html.FROM_HTML_MODE_LEGACY).toString();return Html.fromHtml(s).toString();}
    private String readAll(InputStream is)throws Exception{if(is==null)return"";BufferedReader r=new BufferedReader(new InputStreamReader(is,StandardCharsets.UTF_8));StringBuilder b=new StringBuilder();String l;while((l=r.readLine())!=null)b.append(l);return b.toString();}
    private String friendlyError(Exception e){String s=e.getMessage();if(s==null)return"Белгісіз қате";if(s.contains("403"))return"API key/квота қатесі (403)";if(s.contains("400"))return"API сұрауы қате (400)";return s.length()>90?s.substring(0,90):s;}
    private String human(long n){if(n<0)return"?";double v=n;String[]u={"B","KB","MB","GB"};int i=0;while(v>=1024&&i<u.length-1){v/=1024;i++;}return String.format(Locale.US,"%.1f %s",v,u[i]);}
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_SHORT).show();}
}
