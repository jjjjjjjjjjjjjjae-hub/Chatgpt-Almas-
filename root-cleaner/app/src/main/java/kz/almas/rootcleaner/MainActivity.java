package kz.almas.rootcleaner;

import android.app.*;
import android.graphics.Color;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

public class MainActivity extends Activity {
    TextView status, stats; LinearLayout list; ProgressBar progress;
    final ExecutorService worker=Executors.newSingleThreadExecutor();
    final Handler ui=new Handler(Looper.getMainLooper());
    final LinkedHashMap<String,CheckBox> checks=new LinkedHashMap<>();

    @Override public void onCreate(Bundle b){ super.onCreate(b);
        ScrollView sc=new ScrollView(this); LinearLayout page=new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL); page.setPadding(dp(18),dp(18),dp(18),dp(30)); page.setBackgroundColor(Color.rgb(16,17,20)); sc.addView(page);
        TextView title=t("ALMAS ROOT CLEANER",25,Color.WHITE); page.addView(title);
        TextView sub=t("Root арқылы /sdcard жадын сканерлеу және таңдалған файлдарды тазалау",14,Color.LTGRAY); sub.setPadding(0,6,0,14); page.addView(sub);
        status=t("Root тексерілмеді",15,Color.rgb(255,205,90)); status.setPadding(12,12,12,12); page.addView(status);
        Button root=new Button(this); root.setText("ROOT РҰҚСАТ"); root.setOnClickListener(v->checkRoot()); page.addView(root);
        Button scan=new Button(this); scan.setText("СКАНЕРЛЕУ"); scan.setOnClickListener(v->scan()); page.addView(scan);
        progress=new ProgressBar(this); progress.setVisibility(View.GONE); page.addView(progress);
        stats=t("20 MB-тан үлкен файлдар көрсетіледі.",14,Color.LTGRAY); stats.setPadding(0,10,0,10); page.addView(stats);
        Button all=new Button(this); all.setText("БАРЛЫҒЫН БЕЛГІЛЕУ"); all.setOnClickListener(v->{for(CheckBox c:checks.values())c.setChecked(true);}); page.addView(all);
        Button del=new Button(this); del.setText("ТАҢДАЛҒАНДЫ ӨШІРУ"); del.setOnClickListener(v->confirmDelete()); page.addView(del);
        TextView warn=t("Қорғау: /system, /vendor, /product, /data және түбірлік бөлімдер өшірілмейді. Тек /sdcard ішіндегі өзің таңдаған файлдар ғана өшіріледі.",13,Color.rgb(255,140,140)); warn.setPadding(0,12,0,10); page.addView(warn);
        list=new LinearLayout(this); list.setOrientation(LinearLayout.VERTICAL); page.addView(list); setContentView(sc);
    }
    TextView t(String s,int z,int c){TextView v=new TextView(this);v.setText(s);v.setTextSize(z);v.setTextColor(c);return v;}
    int dp(int v){return (int)(v*getResources().getDisplayMetrics().density+.5f);} void busy(boolean b){ui.post(()->progress.setVisibility(b?View.VISIBLE:View.GONE));}
    Result su(String cmd){try{java.lang.Process p=new ProcessBuilder("su","-c",cmd).redirectErrorStream(true).start();BufferedReader r=new BufferedReader(new InputStreamReader(p.getInputStream()));StringBuilder o=new StringBuilder();String l;while((l=r.readLine())!=null)o.append(l).append('\n');return new Result(p.waitFor(),o.toString());}catch(Exception e){return new Result(127,e.toString());}}
    void checkRoot(){busy(true);worker.execute(()->{Result r=su("id");ui.post(()->{busy(false);if(r.code==0&&r.out.contains("uid=0")){status.setText("✓ Root рұқсаты бар");status.setTextColor(Color.rgb(124,255,178));}else{status.setText("✕ Root рұқсаты жоқ немесе бас тартылды");status.setTextColor(Color.rgb(255,120,120));}});});}
    boolean allowed(String p){return p!=null&&(p.startsWith("/sdcard/")||p.startsWith("/storage/emulated/0/"));}

    void scan(){
        busy(true); status.setText("Сканерленіп жатыр…");
        worker.execute(()->{
            String cmd =
                "if command -v busybox >/dev/null 2>&1; then " +
                "FIND='busybox find'; STAT='busybox stat'; SORT='busybox sort'; HEAD='busybox head'; " +
                "else FIND='find'; STAT='stat'; SORT='sort'; HEAD='head'; fi; " +
                "$FIND /sdcard -type f 2>/dev/null | while IFS= read -r f; do " +
                "s=$($STAT -c %s \"$f\" 2>/dev/null); " +
                "if [ -n \"$s\" ] && [ \"$s\" -ge 20971520 ] 2>/dev/null; then printf '%s|%s\\n' \"$s\" \"$f\"; fi; " +
                "done | $SORT -t'|' -k1,1nr | $HEAD -n 250";
            Result r=su(cmd);
            ArrayList<Item> items=new ArrayList<>(); long total=0;
            for(String line:r.out.split("\\n")){
                line=line.trim(); if(line.isEmpty())continue;
                int i=line.indexOf('|'); if(i<1)continue;
                try{
                    long n=Long.parseLong(line.substring(0,i).trim());
                    String p=line.substring(i+1).trim();
                    if(allowed(p)){items.add(new Item(p,n));total+=n;}
                }catch(Exception ignored){}
            }
            long sum=total; int exit=r.code;
            ui.post(()->{
                busy(false); checks.clear(); list.removeAllViews();
                if(exit!=0 && items.isEmpty()){
                    status.setText("Сканер қатесі"); status.setTextColor(Color.rgb(255,120,120));
                    stats.setText("Root бар, бірақ storage командасы орындалмады.");
                    return;
                }
                status.setText(items.isEmpty()?"20 MB-тан үлкен файл табылмады":"✓ Сканер аяқталды");
                status.setTextColor(items.isEmpty()?Color.rgb(255,205,90):Color.rgb(124,255,178));
                stats.setText(items.size()+" файл • "+human(sum));
                for(Item it:items){CheckBox c=new CheckBox(this);c.setText(human(it.bytes)+"\n"+it.path);c.setTextColor(Color.WHITE);c.setPadding(0,7,0,7);list.addView(c);checks.put(it.path,c);}
            });
        });
    }

    void confirmDelete(){ArrayList<String>s=new ArrayList<>();for(Map.Entry<String,CheckBox>e:checks.entrySet())if(e.getValue().isChecked()&&allowed(e.getKey()))s.add(e.getKey());if(s.isEmpty()){Toast.makeText(this,"Алдымен файл таңда",Toast.LENGTH_SHORT).show();return;}new AlertDialog.Builder(this).setTitle("Өшіру").setMessage(s.size()+" файл қайтарымсыз өшіріледі.").setNegativeButton("Жоқ",null).setPositiveButton("Өшіру",(d,w)->deleteFiles(s)).show();}
    void deleteFiles(ArrayList<String> ps){busy(true);worker.execute(()->{int ok=0;for(String p:ps){if(!allowed(p))continue;String q="'"+p.replace("'","'\\''")+"'";if(su("[ -f "+q+" ] && rm -f -- "+q).code==0)ok++;}int n=ok;ui.post(()->{busy(false);Toast.makeText(this,n+" файл өшірілді",Toast.LENGTH_LONG).show();scan();});});}
    String human(long b){double n=b;String[]u={"B","KB","MB","GB","TB"};int i=0;while(n>=1024&&i<u.length-1){n/=1024;i++;}return String.format(Locale.US,"%.1f %s",n,u[i]);}
    static class Result{int code;String out;Result(int c,String o){code=c;out=o;}} static class Item{String path;long bytes;Item(String p,long b){path=p;bytes=b;}}
}
