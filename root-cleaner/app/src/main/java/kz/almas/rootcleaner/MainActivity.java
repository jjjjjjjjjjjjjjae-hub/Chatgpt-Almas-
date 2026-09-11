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
    TextView status, stats, appStatus;
    LinearLayout fileList, appList;
    ProgressBar progress;
    Spinner focusSpinner;
    final ExecutorService worker=Executors.newSingleThreadExecutor();
    final Handler ui=new Handler(Looper.getMainLooper());
    final LinkedHashMap<String,CheckBox> fileChecks=new LinkedHashMap<>();
    final LinkedHashMap<String,CheckBox> appChecks=new LinkedHashMap<>();
    final ArrayList<String> userPackages=new ArrayList<>();
    static final String SELF="kz.almas.rootcleaner";

    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        ScrollView sc=new ScrollView(this);
        LinearLayout page=new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(18),dp(18),dp(18),dp(30));
        page.setBackgroundColor(Color.rgb(16,17,20));
        sc.addView(page);

        TextView title=t("ALMAS ROOT CONTROL",26,Color.WHITE); page.addView(title);
        TextView sub=t("Focus • Deep Sleep • RAM • Storage",14,Color.LTGRAY); sub.setPadding(0,4,0,14); page.addView(sub);

        status=t("Root тексерілмеді",15,Color.rgb(255,205,90)); status.setPadding(dp(12),dp(12),dp(12),dp(12)); page.addView(status);
        Button root=new Button(this); root.setText("ROOT РҰҚСАТ"); root.setOnClickListener(v->checkRoot()); page.addView(root);
        progress=new ProgressBar(this); progress.setVisibility(View.GONE); page.addView(progress);

        section(page,"ҚОЛДАНБАЛАРДЫ БАСҚАРУ");
        appStatus=t("Алдымен қолданбаларды жүкте.",14,Color.LTGRAY); page.addView(appStatus);
        Button load=new Button(this); load.setText("ҚОЛДАНБАЛАРДЫ ЖҮКТЕУ"); load.setOnClickListener(v->loadApps()); page.addView(load);
        focusSpinner=new Spinner(this); page.addView(focusSpinner);

        Button focusOn=new Button(this); focusOn.setText("FOCUS MODE — ҚОСУ"); focusOn.setOnClickListener(v->focusMode()); page.addView(focusOn);
        Button sleep=new Button(this); sleep.setText("БЕЛГІЛЕНГЕНДЕРДІ DEEP SLEEP"); sleep.setOnClickListener(v->sleepSelected()); page.addView(sleep);
        Button wake=new Button(this); wake.setText("БЕЛГІЛЕНГЕНДЕРДІ ОЯТУ"); wake.setOnClickListener(v->wakeSelected()); page.addView(wake);
        Button ram=new Button(this); ram.setText("RAM ТАЗАЛАУ"); ram.setOnClickListener(v->cleanRam()); page.addView(ram);
        Button cache=new Button(this); cache.setText("БЕЛГІЛЕНГЕНДЕРДІҢ CACHE-ТІ ТАЗАЛАУ"); cache.setOnClickListener(v->confirmCache()); page.addView(cache);

        TextView note=t("Focus Mode: жоғарыдан бір қолданбаны таңдайсың. Төменде белгіленген басқа қолданбалар тоқтатылып, фондық жұмысы бұғатталады. Focus қолданбаның процесс приоритеті ғана көтеріледі — overclock жасалмайды.",13,Color.rgb(170,190,205)); note.setPadding(0,8,0,8); page.addView(note);
        appList=new LinearLayout(this); appList.setOrientation(LinearLayout.VERTICAL); page.addView(appList);

        section(page,"STORAGE CLEANER");
        Button scan=new Button(this); scan.setText("ЖАДТЫ СКАНЕРЛЕУ"); scan.setOnClickListener(v->scanFiles()); page.addView(scan);
        stats=t("20 MB-тан үлкен файлдар көрсетіледі.",14,Color.LTGRAY); stats.setPadding(0,8,0,8); page.addView(stats);
        Button allFiles=new Button(this); allFiles.setText("БАРЛЫҚ ФАЙЛДЫ БЕЛГІЛЕУ"); allFiles.setOnClickListener(v->{for(CheckBox c:fileChecks.values())c.setChecked(true);}); page.addView(allFiles);
        Button del=new Button(this); del.setText("ТАҢДАЛҒАН ФАЙЛДАРДЫ ӨШІРУ"); del.setOnClickListener(v->confirmDelete()); page.addView(del);
        TextView warn=t("Қорғау: /system, /vendor, /product, /data және телефонның толық жадын жоятын команда жоқ. Storage Cleaner тек /sdcard ішінен өзің таңдаған нақты файлдарды өшіреді.",13,Color.rgb(255,140,140)); warn.setPadding(0,10,0,8); page.addView(warn);
        fileList=new LinearLayout(this); fileList.setOrientation(LinearLayout.VERTICAL); page.addView(fileList);

        setContentView(sc);
    }

    void section(LinearLayout p,String s){TextView v=t(s,18,Color.WHITE);v.setPadding(0,dp(18),0,dp(8));p.addView(v);}
    TextView t(String s,int z,int c){TextView v=new TextView(this);v.setText(s);v.setTextSize(z);v.setTextColor(c);return v;}
    int dp(int v){return (int)(v*getResources().getDisplayMetrics().density+.5f);}
    void busy(boolean b){ui.post(()->progress.setVisibility(b?View.VISIBLE:View.GONE));}

    Result su(String cmd){
        try{
            java.lang.Process p=new ProcessBuilder("su","-c",cmd).redirectErrorStream(true).start();
            BufferedReader r=new BufferedReader(new InputStreamReader(p.getInputStream()));
            StringBuilder o=new StringBuilder(); String l;
            while((l=r.readLine())!=null)o.append(l).append('\n');
            return new Result(p.waitFor(),o.toString());
        }catch(Exception e){return new Result(127,e.toString());}
    }

    void checkRoot(){
        busy(true); worker.execute(()->{
            Result r=su("id");
            ui.post(()->{busy(false); if(r.code==0&&r.out.contains("uid=0")){status.setText("✓ Root рұқсаты бар");status.setTextColor(Color.rgb(124,255,178));}else{status.setText("✕ Root рұқсаты жоқ немесе бас тартылды");status.setTextColor(Color.rgb(255,120,120));}});
        });
    }

    boolean validPkg(String p){return p!=null && p.matches("[A-Za-z0-9_.]+") && !p.equals(SELF);}
    String focusPkg(){Object o=focusSpinner.getSelectedItem(); return o==null?"":o.toString();}
    ArrayList<String> selectedApps(){ArrayList<String> a=new ArrayList<>();for(Map.Entry<String,CheckBox>e:appChecks.entrySet())if(e.getValue().isChecked()&&validPkg(e.getKey()))a.add(e.getKey());return a;}

    void loadApps(){
        busy(true); appStatus.setText("Қолданбалар оқылып жатыр…");
        worker.execute(()->{
            Result r=su("pm list packages -3 2>/dev/null | sed 's/^package://' | sort");
            ArrayList<String> pkgs=new ArrayList<>();
            for(String s:r.out.split("\\n")){s=s.trim();if(validPkg(s))pkgs.add(s);}
            ui.post(()->{
                busy(false); userPackages.clear(); userPackages.addAll(pkgs); appChecks.clear(); appList.removeAllViews();
                ArrayAdapter<String> ad=new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,userPackages); focusSpinner.setAdapter(ad);
                for(String p:userPackages){CheckBox c=new CheckBox(this);c.setText(p);c.setTextColor(Color.WHITE);c.setPadding(0,dp(4),0,dp(4));appList.addView(c);appChecks.put(p,c);}
                appStatus.setText(pkgs.size()+" пайдаланушы қолданбасы табылды");
            });
        });
    }

    String sleepCmd(String p){return "am force-stop "+p+"; cmd appops set "+p+" RUN_IN_BACKGROUND ignore 2>/dev/null; cmd appops set "+p+" RUN_ANY_IN_BACKGROUND ignore 2>/dev/null";}
    String wakeCmd(String p){return "cmd appops set "+p+" RUN_IN_BACKGROUND allow 2>/dev/null; cmd appops set "+p+" RUN_ANY_IN_BACKGROUND allow 2>/dev/null";}

    void focusMode(){
        String focus=focusPkg(); if(!validPkg(focus)){toast("Focus қолданбаны таңда");return;}
        ArrayList<String> selected=selectedApps(); busy(true);
        worker.execute(()->{
            for(String p:selected)if(!p.equals(focus))su(sleepCmd(p));
            su(wakeCmd(focus));
            su("monkey -p "+focus+" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1; sleep 1; pid=$(pidof "+focus+" | awk '{print $1}'); [ -n \"$pid\" ] && renice -10 -p \"$pid\" >/dev/null 2>&1 || true");
            ui.post(()->{busy(false);appStatus.setText("✓ Focus: "+focus+" • ұйықтатылды: "+Math.max(0,selected.size()-(selected.contains(focus)?1:0)));});
        });
    }

    void sleepSelected(){
        ArrayList<String> a=selectedApps(); if(a.isEmpty()){toast("Ұйықтататын қолданбаларды белгіле");return;} busy(true);
        worker.execute(()->{for(String p:a)su(sleepCmd(p));ui.post(()->{busy(false);appStatus.setText("✓ Deep Sleep: "+a.size()+" қолданба");});});
    }

    void wakeSelected(){
        ArrayList<String> a=selectedApps(); if(a.isEmpty()){toast("Оятатын қолданбаларды белгіле");return;} busy(true);
        worker.execute(()->{for(String p:a)su(wakeCmd(p));ui.post(()->{busy(false);appStatus.setText("✓ Фондық жұмыс қалпына келтірілді: "+a.size());});});
    }

    void cleanRam(){
        busy(true); worker.execute(()->{Result r=su("am kill-all");ui.post(()->{busy(false);appStatus.setText(r.code==0?"✓ Cached/background процестер тазаланды":"RAM тазалау командасы орындалмады");});});
    }

    void confirmCache(){
        ArrayList<String>a=selectedApps();if(a.isEmpty()){toast("Cache тазалайтын қолданбаларды белгіле");return;}
        new AlertDialog.Builder(this).setTitle("Cache тазалау").setMessage(a.size()+" қолданбаның тек cache файлдары өшіріледі. Аккаунт/қолданба деректері әдейі өшірілмейді.").setNegativeButton("Жоқ",null).setPositiveButton("Тазалау",(d,w)->clearCache(a)).show();
    }

    void clearCache(ArrayList<String>a){
        busy(true); worker.execute(()->{for(String p:a){if(!validPkg(p))continue;su("rm -rf /data/user/0/"+p+"/cache/* /data/data/"+p+"/cache/* /sdcard/Android/data/"+p+"/cache/* 2>/dev/null || true");}ui.post(()->{busy(false);appStatus.setText("✓ Cache тазаланды: "+a.size()+" қолданба");});});
    }

    boolean allowedFile(String p){return p!=null&&(p.startsWith("/sdcard/")||p.startsWith("/storage/emulated/0/"));}
    void scanFiles(){
        busy(true); stats.setText("Сканерленіп жатыр…");
        worker.execute(()->{
            String cmd="if command -v busybox >/dev/null 2>&1; then FIND='busybox find'; STAT='busybox stat'; SORT='busybox sort'; HEAD='busybox head'; else FIND='find'; STAT='stat'; SORT='sort'; HEAD='head'; fi; $FIND /sdcard -type f 2>/dev/null | while IFS= read -r f; do s=$($STAT -c %s \"$f\" 2>/dev/null); if [ -n \"$s\" ] && [ \"$s\" -ge 20971520 ] 2>/dev/null; then printf '%s|%s\\n' \"$s\" \"$f\"; fi; done | $SORT -t'|' -k1,1nr | $HEAD -n 250";
            Result r=su(cmd); ArrayList<Item> items=new ArrayList<>(); long total=0;
            for(String line:r.out.split("\\n")){line=line.trim();if(line.isEmpty())continue;int i=line.indexOf('|');if(i<1)continue;try{long n=Long.parseLong(line.substring(0,i).trim());String p=line.substring(i+1).trim();if(allowedFile(p)){items.add(new Item(p,n));total+=n;}}catch(Exception ignored){}}
            long sum=total;
            ui.post(()->{busy(false);fileChecks.clear();fileList.removeAllViews();stats.setText(items.size()+" файл • "+human(sum));for(Item it:items){CheckBox c=new CheckBox(this);c.setText(human(it.bytes)+"\n"+it.path);c.setTextColor(Color.WHITE);fileList.addView(c);fileChecks.put(it.path,c);}});
        });
    }

    void confirmDelete(){
        ArrayList<String>s=new ArrayList<>();for(Map.Entry<String,CheckBox>e:fileChecks.entrySet())if(e.getValue().isChecked()&&allowedFile(e.getKey()))s.add(e.getKey());
        if(s.isEmpty()){toast("Алдымен файл таңда");return;}
        new AlertDialog.Builder(this).setTitle("Файлдарды өшіру").setMessage(s.size()+" файл қайтарымсыз өшіріледі.").setNegativeButton("Жоқ",null).setPositiveButton("Өшіру",(d,w)->deleteFiles(s)).show();
    }

    void deleteFiles(ArrayList<String> ps){
        busy(true);worker.execute(()->{int ok=0;for(String p:ps){if(!allowedFile(p))continue;String q="'"+p.replace("'","'\\''")+"'";if(su("[ -f "+q+" ] && rm -f -- "+q).code==0)ok++;}int n=ok;ui.post(()->{busy(false);toast(n+" файл өшірілді");scanFiles();});});
    }

    void toast(String s){Toast.makeText(this,s,Toast.LENGTH_SHORT).show();}
    String human(long b){double n=b;String[]u={"B","KB","MB","GB","TB"};int i=0;while(n>=1024&&i<u.length-1){n/=1024;i++;}return String.format(Locale.US,"%.1f %s",n,u[i]);}
    static class Result{int code;String out;Result(int c,String o){code=c;out=o;}}
    static class Item{String path;long bytes;Item(String p,long b){path=p;bytes=b;}}
}
