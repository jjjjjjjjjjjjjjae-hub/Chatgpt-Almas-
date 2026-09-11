package kz.almas.tube;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.os.*;
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

public class SciQazActivity extends Activity {
    private static final String HANDLE="@SciQaz";
    private static final int BG=Color.rgb(10,10,14), PANEL=Color.rgb(24,24,31), PANEL2=Color.rgb(34,34,43), RED=Color.rgb(255,48,64), MUTED=Color.rgb(170,170,182);
    private final ExecutorService worker=Executors.newFixedThreadPool(4);
    private final Handler ui=new Handler(Looper.getMainLooper());
    private SharedPreferences prefs;
    private LinearLayout list;
    private TextView title,sub,status;

    static class VideoItem{
        String id,title,channel,thumb;
        VideoItem(String i,String t,String c,String th){id=i;title=t;channel=c;thumb=th;}
    }

    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        prefs=getSharedPreferences("almastube",MODE_PRIVATE);
        getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);
        setContentView(buildUi());
        if(prefs.getString("api_key","").trim().isEmpty())showApiDialog(true);else loadChannel();
    }

    private View buildUi(){
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(BG);root.setPadding(dp(14),dp(10),dp(14),dp(10));
        LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);
        TextView logo=text("▶",18,Color.WHITE,true);logo.setGravity(Gravity.CENTER);logo.setBackground(round(RED,999));top.addView(logo,new LinearLayout.LayoutParams(dp(44),dp(44)));
        LinearLayout brand=new LinearLayout(this);brand.setOrientation(LinearLayout.VERTICAL);brand.setPadding(dp(10),0,0,0);brand.addView(text("AlmasTube",24,Color.WHITE,true));brand.addView(text("SciQ • @SciQaz",11,MUTED,false));top.addView(brand,new LinearLayout.LayoutParams(0,dp(50),1));
        Button app=dark("Толық");app.setOnClickListener(v->startActivity(new Intent(this,MainActivity.class)));top.addView(app,new LinearLayout.LayoutParams(dp(78),dp(42)));root.addView(top);

        LinearLayout head=card();LinearLayout line=new LinearLayout(this);line.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout names=new LinearLayout(this);names.setOrientation(LinearLayout.VERTICAL);title=text("SciQ",23,Color.WHITE,true);sub=text(HANDLE,12,MUTED,false);sub.setPadding(0,dp(3),0,0);names.addView(title);names.addView(sub);line.addView(names,new LinearLayout.LayoutParams(0,-2,1));
        Button refresh=dark("↻");refresh.setTextSize(18);refresh.setOnClickListener(v->loadChannel());line.addView(refresh,new LinearLayout.LayoutParams(dp(50),dp(44)));head.addView(line);
        TextView desc=text("Арнаның барлық ашық видеолары YouTube Data API арқылы көрсетіледі.",13,MUTED,false);desc.setPadding(0,dp(8),0,0);head.addView(desc);root.addView(head);

        status=text("Дайын",12,MUTED,false);status.setPadding(dp(12),dp(9),dp(12),dp(9));root.addView(status);
        ScrollView sc=new ScrollView(this);list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);sc.addView(list);root.addView(sc,new LinearLayout.LayoutParams(-1,0,1));

        TextView note=text("YouTube видеосы AlmasTube ішінде ресми player арқылы ойнатылады. Фондық YouTube ойнату үшін ресми YouTube қолданбасын қолдан.",11,Color.rgb(120,120,132),false);note.setGravity(Gravity.CENTER);note.setPadding(dp(8),dp(8),dp(8),0);root.addView(note);
        return root;
    }

    private void loadChannel(){
        String key=prefs.getString("api_key","").trim();if(key.isEmpty()){showApiDialog(true);return;}
        status.setText("SciQ арнасы жүктеліп жатыр…");list.removeAllViews();TextView l=text("Жүктелуде…",14,MUTED,false);l.setGravity(Gravity.CENTER);l.setPadding(0,dp(45),0,0);list.addView(l);
        worker.execute(()->{
            try{
                String channelUrl="https://www.googleapis.com/youtube/v3/channels?part=snippet,contentDetails&forHandle="+URLEncoder.encode(HANDLE,"UTF-8")+"&key="+URLEncoder.encode(key,"UTF-8");
                JSONObject root=new JSONObject(httpGet(channelUrl));JSONArray arr=root.optJSONArray("items");if(arr==null||arr.length()==0)throw new IOException("@SciQaz табылмады");
                JSONObject ch=arr.getJSONObject(0);String channelId=ch.optString("id","");JSONObject sn=ch.getJSONObject("snippet");String channelName=decode(sn.optString("title","SciQ"));String uploads=ch.getJSONObject("contentDetails").getJSONObject("relatedPlaylists").optString("uploads","");if(uploads.isEmpty())throw new IOException("Uploads playlist табылмады");
                ArrayList<VideoItem> all=new ArrayList<>();String token="";int pages=0;
                do{
                    String url="https://www.googleapis.com/youtube/v3/playlistItems?part=snippet,contentDetails&maxResults=50&playlistId="+URLEncoder.encode(uploads,"UTF-8")+"&key="+URLEncoder.encode(key,"UTF-8");if(!token.isEmpty())url+="&pageToken="+URLEncoder.encode(token,"UTF-8");
                    JSONObject page=new JSONObject(httpGet(url));JSONArray items=page.optJSONArray("items");if(items!=null)for(int i=0;i<items.length();i++){
                        JSONObject o=items.getJSONObject(i), ps=o.optJSONObject("snippet"), cd=o.optJSONObject("contentDetails");if(ps==null||cd==null)continue;String id=cd.optString("videoId","");if(id.isEmpty())continue;String vt=decode(ps.optString("title","Видео"));if("Private video".equalsIgnoreCase(vt)||"Deleted video".equalsIgnoreCase(vt))continue;JSONObject thumbs=ps.optJSONObject("thumbnails"),th=thumbs==null?null:thumbs.optJSONObject("medium");if(th==null&&thumbs!=null)th=thumbs.optJSONObject("default");all.add(new VideoItem(id,vt,channelName,th==null?"":th.optString("url")));
                    }
                    token=page.optString("nextPageToken","");pages++;final int n=all.size();ui.post(()->status.setText("Жүктелді: "+n+" видео…"));
                }while(!token.isEmpty()&&pages<100);
                ui.post(()->{title.setText(channelName);sub.setText(HANDLE+(channelId.isEmpty()?"":" • "+channelId)+" • "+all.size()+" видео");status.setText("✓ "+all.size()+" видео");render(all);});
            }catch(Exception e){ui.post(()->{list.removeAllViews();status.setText("Қате: "+friendly(e));TextView er=text("SciQ видеолары жүктелмеді. API key мен интернетті тексер.",14,MUTED,false);er.setGravity(Gravity.CENTER);er.setPadding(0,dp(40),0,0);list.addView(er);});}
        });
    }

    private void render(List<VideoItem> items){
        list.removeAllViews();for(VideoItem it:items){LinearLayout c=card();LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2);cp.setMargins(0,0,0,dp(10));list.addView(c,cp);ImageView img=new ImageView(this);img.setScaleType(ImageView.ScaleType.CENTER_CROP);img.setBackground(round(PANEL2,16));c.addView(img,new LinearLayout.LayoutParams(-1,dp(178)));TextView tt=text(it.title,16,Color.WHITE,true);tt.setPadding(0,dp(10),0,dp(3));c.addView(tt);c.addView(text(it.channel,13,MUTED,false));LinearLayout a=new LinearLayout(this);a.setPadding(0,dp(9),0,0);Button play=accent("▶ Ойнату");play.setOnClickListener(v->openPlayer(it));Button yt=dark("YouTube");yt.setOnClickListener(v->openPlayer(it));a.addView(play,new LinearLayout.LayoutParams(0,dp(46),1));LinearLayout.LayoutParams yp=new LinearLayout.LayoutParams(0,dp(46),1);yp.setMargins(dp(8),0,0,0);a.addView(yt,yp);c.addView(a);c.setOnClickListener(v->openPlayer(it));if(!it.thumb.isEmpty())loadImage(it.thumb,img);}
    }

    private void openPlayer(VideoItem it){Intent i=new Intent(this,PlayerActivity.class);i.putExtra("video_id",it.id);i.putExtra("title",it.title);startActivity(i);}
    private void loadImage(String u,ImageView iv){worker.execute(()->{try{HttpURLConnection c=(HttpURLConnection)new URL(u).openConnection();c.setConnectTimeout(8000);c.setReadTimeout(8000);Bitmap b=BitmapFactory.decodeStream(c.getInputStream());if(b!=null)ui.post(()->iv.setImageBitmap(b));}catch(Exception ignored){}});}
    private String httpGet(String u)throws Exception{HttpURLConnection c=(HttpURLConnection)new URL(u).openConnection();c.setConnectTimeout(12000);c.setReadTimeout(15000);c.setRequestProperty("Accept","application/json");c.setRequestProperty("User-Agent","AlmasTube/2.2 Android");int code=c.getResponseCode();InputStream is=code>=400?c.getErrorStream():c.getInputStream();String body=readAll(is);if(code>=400)throw new IOException("HTTP "+code+" "+body);return body;}

    private void showApiDialog(boolean required){EditText in=new EditText(this);in.setSingleLine(true);in.setHint("AIza...");in.setTextColor(Color.WHITE);in.setHintTextColor(Color.GRAY);in.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);in.setText(prefs.getString("api_key",""));in.setPadding(dp(16),dp(8),dp(16),dp(8));AlertDialog.Builder b=new AlertDialog.Builder(this).setTitle("YouTube API Key").setMessage("YouTube Data API v3 кілтін енгіз.").setView(in).setPositiveButton("Сақтау",null);if(!required)b.setNegativeButton("Бас тарту",null);AlertDialog d=b.create();d.setCancelable(!required);d.setOnShowListener(x->d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{String k=in.getText().toString().trim();if(k.length()<20){toast("API key дұрыс емес сияқты");return;}prefs.edit().putString("api_key",k).apply();d.dismiss();loadChannel();}));d.show();}

    private LinearLayout card(){LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);c.setPadding(dp(12),dp(12),dp(12),dp(12));c.setBackground(round(PANEL,18));return c;}
    private TextView text(String s,int z,int c,boolean bold){TextView v=new TextView(this);v.setText(s);v.setTextSize(z);v.setTextColor(c);if(bold)v.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);return v;}
    private Button accent(String s){Button b=new Button(this);b.setText(s);b.setTextColor(Color.WHITE);b.setAllCaps(false);b.setBackground(round(RED,16));return b;}
    private Button dark(String s){Button b=new Button(this);b.setText(s);b.setTextColor(Color.WHITE);b.setAllCaps(false);b.setBackground(round(PANEL2,16));return b;}
    private GradientDrawable round(int c,int r){GradientDrawable g=new GradientDrawable();g.setColor(c);g.setCornerRadius(dp(r));return g;}
    private int dp(int x){return(int)(x*getResources().getDisplayMetrics().density+.5f);}
    private String decode(String s){if(Build.VERSION.SDK_INT>=24)return Html.fromHtml(s,Html.FROM_HTML_MODE_LEGACY).toString();return Html.fromHtml(s).toString();}
    private String readAll(InputStream is)throws Exception{if(is==null)return"";BufferedReader r=new BufferedReader(new InputStreamReader(is,StandardCharsets.UTF_8));StringBuilder b=new StringBuilder();String line;while((line=r.readLine())!=null)b.append(line);return b.toString();}
    private String friendly(Exception e){String s=e.getMessage();if(s==null)return"Белгісіз қате";if(s.contains("403"))return"API key/квота қатесі (403)";return s.length()>95?s.substring(0,95):s;}
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_SHORT).show();}
}
