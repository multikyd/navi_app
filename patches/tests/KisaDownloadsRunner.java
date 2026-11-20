package com.kisa.tmaprouteprobe;
import android.app.*;import android.content.*;import android.database.Cursor;import android.net.Uri;import android.os.*;import android.provider.MediaStore;import org.json.*;import java.io.*;import java.lang.reflect.*;import java.nio.file.Files;import java.security.*;

public class KisaDownloadsRunner extends Instrumentation {
 public void onCreate(Bundle b){super.onCreate(b);start();}
 static void check(boolean value,String reason){if(!value)throw new IllegalStateException(reason);}
 static String hash(File f)throws Exception{MessageDigest md=MessageDigest.getInstance("SHA-256");try(InputStream in=new FileInputStream(f)){byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1)md.update(b,0,n);}StringBuilder out=new StringBuilder();for(byte b:md.digest())out.append(String.format(java.util.Locale.ROOT,"%02x",b&255));return out.toString();}
 public void onStart(){JSONObject report=new JSONObject();Bundle answer=new Bundle();try{
  Context c=getTargetContext();String source=c.getPackageName().equals("com.skt.tmap.ku")?"tmap":"naver-map";ClassLoader cl=c.getClassLoader();
  Class<?> updater=cl.loadClass("com.kisa.transport.NaviUpdater"),store=cl.loadClass("com.kisa.update.DownloadStore");long build=updater.getField("BUILD").getLong(null);check(build==2026111702L,"Unexpected installed build");
  updater.getMethod("init",Context.class,String.class).invoke(null,c,source);
  File apk=new File(c.getApplicationInfo().sourceDir);String sha=hash(apk);
  Method verify=updater.getDeclaredMethod("verifyPackage",File.class);verify.setAccessible(true);verify.invoke(null,apk);
  Method save=store.getMethod("save",Context.class,String.class,long.class,File.class,long.class,String.class);
  Method find=store.getMethod("find",Context.class,String.class,long.class,long.class,String.class);
  Uri uri=(Uri)save.invoke(null,c,source,build,apk,apk.length(),sha);
  check(uri.equals(save.invoke(null,c,source,build,apk,apk.length(),sha)),"Repeated export created a duplicate");
  check(uri.equals(find.invoke(null,c,source,build,apk.length(),sha)),"Saved file verification failed");
  try(Cursor row=c.getContentResolver().query(uri,new String[]{MediaStore.MediaColumns.DISPLAY_NAME,MediaStore.MediaColumns.RELATIVE_PATH,MediaStore.MediaColumns.IS_PENDING,MediaStore.MediaColumns.SIZE},null,null,null)){
   check(row!=null&&row.moveToFirst(),"Downloads row missing");check(row.getString(0).equals("kisa-"+source+"-"+build+".apk"),"Wrong filename");check(row.getString(1).equals("Download/"),"Not in public Downloads");check(row.getInt(2)==0,"APK not published");check(row.getLong(3)==apk.length(),"Published file length mismatch");
   report.put("filename",row.getString(0));
  }
  String wrong=new String(new char[64]).replace('\0','0');
  try{save.invoke(null,c,source,build+1,apk,apk.length(),wrong);throw new IllegalStateException("Wrong hash accepted");}catch(InvocationTargetException e){check(e.getCause() instanceof IOException,"Unexpected hash rejection");}
  check(find.invoke(null,c,source,build+1,apk.length(),wrong)==null,"Broken download remained visible");
  check(c.getPackageManager().checkPermission("android.permission.REQUEST_INSTALL_PACKAGES",c.getPackageName())!=android.content.pm.PackageManager.PERMISSION_GRANTED,"Updater install permission remains");
  report.put("same_signer_verified",true).put("downloads_publication_and_hash_verified",true).put("duplicate_export_reused",true).put("wrong_hash_rejected",true).put("install_permission_removed",true).put("installer_not_started",true);
  report.put("completion_notification_posted",store.getMethod("notifySaved",Context.class,String.class,long.class).invoke(null,c,source,build));
  report.put("build",build).put("uri",uri.toString());
 }catch(Throwable e){while(e.getCause()!=null)e=e.getCause();try{report.put("error",e.toString());}catch(Exception ignored){}}
 try{File dir=new File(getTargetContext().getExternalFilesDir(null),"updater_probe");dir.mkdirs();Files.write(new File(dir,"downloads-report.json").toPath(),report.toString(2).getBytes("UTF-8"));}catch(Exception ignored){}
 answer.putString("report",report.toString());finish(Activity.RESULT_OK,answer);
 }
}
