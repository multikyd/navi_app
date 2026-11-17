package com.carrot.transport;
import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.net.*;
import android.os.*;
import com.kisa.update.DownloadStore;
import android.widget.Toast;
import org.json.*;
import java.io.*;
import java.lang.ref.WeakReference;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.concurrent.*;

/** Verified GitHub update download. The user installs the saved Downloads APK. */
public final class NaviUpdater {
  public static final long BUILD=2026111702L;
  public static final String VERSION="2026.11.17.2";
  private static final String INDEX="https://raw.githubusercontent.com/multikyd/navi_app/main/manifest.json";
  private static final Handler main=new Handler(Looper.getMainLooper());
  private static final ExecutorService worker=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"Kisa-app-update");t.setDaemon(true);return t;});
  private static WeakReference<Activity> activity=new WeakReference<>(null);
  private static Context app;
  private static String source;
  private static volatile boolean checked,busy,prompting;
  private static volatile JSONObject pending;
  private static volatile double speedKph;
  private static volatile long speedAt;
  public static synchronized void init(Context c,String id){if(app==null){app=c.getApplicationContext();source=id;}}
  public static void observe(JSONObject packet){
    JSONObject r=packet.optJSONObject("rgdata");if(r==null)return;
    double speed=r.optDouble("nPosSpeed",Double.NaN);
    if(Double.isFinite(speed)&&speed>=0&&speed<350){speedKph=speed;speedAt=SystemClock.elapsedRealtime();}
  }
  public static void onResume(Activity a){
    activity=new WeakReference<>(a);
    main.postDelayed(()->{
      if(app==null)return;
      if(busy)return;
      if(pending!=null){show();return;}
      if(checked)return;checked=true;
      long now=System.currentTimeMillis(),last=app.getSharedPreferences("carrot_updater",0).getLong("checked_ms",0);
      if(now-last>=0&&now-last<15*60000L){checked=false;return;}
      worker.execute(()->check());
    },3500);
  }
  public static void onPause(Activity a){if(activity.get()==a)activity.clear();}
  private static void check(){
    try{
      JSONObject manifest=new JSONObject(new String(fetchSmall(INDEX),StandardCharsets.UTF_8));
      pending=UpdateRules.plan(manifest,source,BUILD);
      app.getSharedPreferences("carrot_updater",0).edit().putLong("checked_ms",System.currentTimeMillis()).apply();
      if(pending==null)return;
      if(DownloadStore.find(app,source,pending.getLong("build"),pending.getLong("size"),pending.getString("sha256"))!=null){pending=null;return;}
      android.net.ConnectivityManager cm=(android.net.ConnectivityManager)app.getSystemService(Context.CONNECTIVITY_SERVICE);
      boolean unmetered=false;try{unmetered=cm!=null&&!cm.isActiveNetworkMetered();}catch(Throwable ignored){}
      if(manifest.optBoolean("auto_download_wifi",true)&&unmetered)download(false);else main.post(()->show());
    }catch(Throwable ignored){}finally{checked=false;}
  }
  private static HttpURLConnection connect(String url,long offset)throws Exception{
    for(int i=0;i<6;i++){
      URL u=new URL(url);String host=u.getHost();
      if(!u.getProtocol().equals("https")||!(host.equals("raw.githubusercontent.com")||host.equals("github.com")||host.equals("release-assets.githubusercontent.com")||host.equals("objects.githubusercontent.com")))throw new IOException("untrusted download host");
      HttpURLConnection c=(HttpURLConnection)u.openConnection();c.setInstanceFollowRedirects(false);c.setConnectTimeout(15000);c.setReadTimeout(30000);c.setRequestProperty("User-Agent","KisaNaviUpdater/2");c.setRequestProperty("Accept-Encoding","identity");
      if(offset>0)c.setRequestProperty("Range","bytes="+offset+"-");
      int status=c.getResponseCode();
      if(status==301||status==302||status==303||status==307||status==308){String next=c.getHeaderField("Location");c.disconnect();url=new URL(u,next).toString();continue;}
      return c;
    }throw new IOException("too many redirects");
  }
  private static byte[] fetchSmall(String url)throws Exception{
    HttpURLConnection c=connect(url,0);
    try{if(c.getResponseCode()!=200)throw new IOException("manifest HTTP "+c.getResponseCode());
      try(InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){
        byte[] buf=new byte[8192];int n;while((n=in.read(buf))!=-1){if(out.size()+n>131072)throw new IOException("manifest too large");out.write(buf,0,n);}return out.toByteArray();}
    }finally{c.disconnect();}
  }
  private static void download(boolean requested){
    if(busy||pending==null)return;busy=true;
    try{
      JSONObject p=pending;
      if(p.has("archive")){downloadArchive(p);return;}
      long expected=p.getLong("size");
      File dir=new File(app.getCacheDir(),"carrot_updates");if(!dir.isDirectory()&&!dir.mkdirs())throw new IOException("cannot create update cache");
      File part=new File(dir,"update-"+p.getLong("build")+".download.apk"),apk=new File(dir,"update-"+p.getLong("build")+".apk");
      if(apk.isFile()&&apk.length()==expected&&UpdateRules.sameHash(hash(apk),p.getString("sha256"))){verifyPackage(apk);downloaded(apk,p);return;}
      long offset=part.isFile()?part.length():0;
      if(offset>expected){if(!part.delete())throw new IOException("cannot reset partial update");offset=0;}
      if(dir.getUsableSpace()<expected*2-offset+16*1024*1024)throw new IOException("저장 공간이 부족합니다.");
      HttpURLConnection c=connect(p.getString("url"),offset);
      try{
        int status=c.getResponseCode();
        offset=UpdateRules.resumeOffset(status,c.getHeaderField("Content-Range"),offset,expected);
        if(offset<expected)try(InputStream in=c.getInputStream();OutputStream out=new FileOutputStream(part,offset>0)){
          byte[] buf=new byte[65536];int n;long count=offset;
          while((n=in.read(buf))!=-1){if(count+n>expected)throw new IOException("APK too large");out.write(buf,0,n);count+=n;}
        }
      }finally{c.disconnect();}
      if(part.length()!=expected)throw new IOException("다운로드가 중단됐습니다. 다시 시도하면 이어받습니다.");
      if(!UpdateRules.sameHash(hash(part),p.getString("sha256"))){part.delete();throw new IOException("APK 무결성 검사 실패");}
      verifyPackage(part);
      if(apk.exists()&&!apk.delete())throw new IOException("cannot replace update cache");
      if(!part.renameTo(apk))throw new IOException("cannot finalize update");
      downloaded(apk,p);
    }catch(Throwable error){if(requested)main.post(()->{Activity a=activity.get();if(a!=null)Toast.makeText(a,"업데이트 실패: "+error.getMessage(),Toast.LENGTH_LONG).show();});}
    finally{busy=false;}
  }
  private static void downloadFile(String url,File part,long expected,String sha)throws Exception{
    if(part.isFile()&&part.length()==expected&&UpdateRules.sameHash(hash(part),sha))return;
    long offset=part.isFile()?part.length():0;if(offset>expected){if(!part.delete())throw new IOException("cannot reset download");offset=0;}
    HttpURLConnection c=connect(url,offset);
    try{int code=c.getResponseCode();offset=UpdateRules.resumeOffset(code,c.getHeaderField("Content-Range"),offset,expected);if(offset<expected)try(InputStream in=c.getInputStream();OutputStream out=new FileOutputStream(part,offset>0)){byte[] buffer=new byte[65536];long count=offset;int n;while((n=in.read(buffer))!=-1){if(count+n>expected)throw new IOException("download exceeds expected size");out.write(buffer,0,n);count+=n;}}}finally{c.disconnect();}
    if(part.length()!=expected)throw new IOException("다운로드가 중단됐습니다. 다시 시도하면 이어받습니다.");
    if(!UpdateRules.sameHash(hash(part),sha)){part.delete();throw new IOException("분할 파일 무결성 검사 실패");}
  }
  private static void downloadArchive(JSONObject p)throws Exception{
    File dir=new File(app.getCacheDir(),"carrot_updates/"+p.getLong("build"));if(!dir.isDirectory()&&!dir.mkdirs())throw new IOException("cannot create update cache");
    File apk=new File(dir,"update.apk");long expected=p.getLong("size");
    if(apk.isFile()&&apk.length()==expected&&UpdateRules.sameHash(hash(apk),p.getString("sha256"))){verifyPackage(apk);downloaded(apk,p);return;}
    JSONObject archive=p.getJSONObject("archive");long compressed=archive.getLong("bytes");if(dir.getUsableSpace()<expected*2+compressed*2+16*1024*1024L)throw new IOException("업데이트 저장 공간이 부족합니다.");
    JSONArray parts=archive.getJSONArray("parts");File[] files=new File[parts.length()];
    for(int i=0;i<files.length;i++){JSONObject part=parts.getJSONObject(i);files[i]=new File(dir,"part-"+i+".zipdata");downloadFile("https://raw.githubusercontent.com/multikyd/navi_app/main/"+part.getString("path"),files[i],part.getLong("bytes"),part.getString("sha256"));}
    File zip=new File(dir,"archive.zip");try(OutputStream out=new FileOutputStream(zip)){byte[] buffer=new byte[65536];for(File file:files)try(InputStream in=new FileInputStream(file)){int n;while((n=in.read(buffer))!=-1)out.write(buffer,0,n);}}
    if(zip.length()!=compressed||!UpdateRules.sameHash(hash(zip),archive.getString("sha256"))){zip.delete();throw new IOException("ZIP 무결성 검사 실패");}
    File extracted=new File(dir,"update.pending.apk");
    try(java.util.zip.ZipInputStream in=new java.util.zip.ZipInputStream(new FileInputStream(zip))){java.util.zip.ZipEntry entry=in.getNextEntry();if(entry==null||entry.isDirectory()||!p.getString("filename").equals(entry.getName()))throw new IOException("예상하지 못한 ZIP 내용입니다.");
      try(OutputStream out=new FileOutputStream(extracted)){byte[] buffer=new byte[65536];long count=0;int n;while((n=in.read(buffer))!=-1){if(count+n>expected)throw new IOException("APK exceeds expected size");out.write(buffer,0,n);count+=n;}}
      in.closeEntry();if(in.getNextEntry()!=null)throw new IOException("ZIP에 추가 파일이 있습니다.");
    }
    if(extracted.length()!=expected||!UpdateRules.sameHash(hash(extracted),p.getString("sha256"))){extracted.delete();throw new IOException("APK 무결성 검사 실패");}verifyPackage(extracted);
    if(apk.exists()&&!apk.delete())throw new IOException("cannot replace update cache");if(!extracted.renameTo(apk))throw new IOException("cannot finalize APK");for(File file:files)file.delete();zip.delete();downloaded(apk,p);
  }
  private static String hash(File file)throws Exception{
    MessageDigest md=MessageDigest.getInstance("SHA-256");try(InputStream in=new FileInputStream(file)){byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1)md.update(b,0,n);}StringBuilder s=new StringBuilder();for(byte b:md.digest())s.append(String.format(java.util.Locale.ROOT,"%02x",b&255));return s.toString();
  }
  private static android.content.pm.Signature[] signatures(PackageInfo p){return Build.VERSION.SDK_INT>=28&&p.signingInfo!=null?p.signingInfo.getApkContentsSigners():p.signatures;}
  private static void verifyPackage(File apk)throws Exception{
    PackageManager pm=app.getPackageManager();int flags=Build.VERSION.SDK_INT>=28?PackageManager.GET_SIGNING_CERTIFICATES:PackageManager.GET_SIGNATURES;
    PackageInfo downloaded=pm.getPackageArchiveInfo(apk.getAbsolutePath(),flags),installed=pm.getPackageInfo(app.getPackageName(),flags);
    if(downloaded==null||!app.getPackageName().equals(downloaded.packageName))throw new IOException("다른 앱의 APK입니다.");
    android.content.pm.Signature[] a=signatures(downloaded),b=signatures(installed);
    if(a==null||b==null||a.length!=1||b.length!=1||!a[0].equals(b[0]))throw new IOException("APK 서명이 일치하지 않습니다.");
  }
  private static void downloaded(File apk,JSONObject plan)throws Exception{
    long build=plan.getLong("build");
    DownloadStore.save(app,source,build,apk,plan.getLong("size"),plan.getString("sha256"));
    pending=null;
    if(apk.getCanonicalPath().startsWith(app.getCacheDir().getCanonicalPath()+File.separator))apk.delete();
    main.post(()->{
      boolean notified=DownloadStore.notifySaved(app,source,build);
      Toast.makeText(app,"다운로드 폴더에 저장했습니다.\n"+DownloadStore.filename(source,build)+(notified?"":"\n내 파일에서 APK를 열어 설치하세요."),Toast.LENGTH_LONG).show();
    });
  }
  private static void show(){
    Activity a=activity.get();if(a==null||a.isFinishing()||prompting||pending==null||busy)return;
    if(SystemClock.elapsedRealtime()-speedAt<5000&&speedKph>1){main.postDelayed(()->show(),30000);return;}
    prompting=true;
    try{
      new AlertDialog.Builder(a).setTitle("수정본 다운로드")
        .setMessage((source.equals("naver-map")?"네이버지도":"티맵")+" "+pending.optString("version")+"\n다운로드 폴더에 APK를 저장합니다. 내 파일에서 직접 설치하세요.\n"+pending.optString("notes",""))
        .setPositiveButton("다운로드",(d,w)->{prompting=false;Toast.makeText(a,"업데이트 다운로드 중",Toast.LENGTH_LONG).show();worker.execute(()->download(true));})
        .setNegativeButton("나중에",(d,w)->{prompting=false;pending=null;})
        .setOnCancelListener(d->{prompting=false;}).show();
    }catch(Throwable ignored){prompting=false;}
  }
}
