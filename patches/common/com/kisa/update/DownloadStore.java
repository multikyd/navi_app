package com.kisa.update;

import android.app.*;
import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.os.*;
import android.provider.MediaStore;
import java.io.*;
import java.security.MessageDigest;
import java.util.Locale;

/** Publishes verified APKs to Downloads. It never requests package installation. */
public final class DownloadStore {
 private static final String MIME="application/vnd.android.package-archive",CHANNEL="kisa_navigation_updates";
 public static String filename(String source,long build){
  if((!"tmap".equals(source)&&!"naver-map".equals(source))||build<=0)throw new IllegalArgumentException("invalid update identity");
  return "kisa-"+source+"-"+build+".apk";
 }
 private static String hex(byte[] value){StringBuilder b=new StringBuilder();for(byte x:value)b.append(String.format(Locale.ROOT,"%02x",x&255));return b.toString();}
 private static String hash(InputStream input)throws Exception{MessageDigest md=MessageDigest.getInstance("SHA-256");byte[] b=new byte[65536];int n;while((n=input.read(b))!=-1)md.update(b,0,n);return hex(md.digest());}
 public static Uri find(Context c,String source,long build,long bytes,String sha){
  if(Build.VERSION.SDK_INT<29)return null;
  try{
   String saved=c.getSharedPreferences("kisa_downloads",0).getString(filename(source,build),"");
   Uri uri=Uri.parse(saved),collection=MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
   if(!saved.startsWith(collection.toString()+"/")||!uri.getLastPathSegment().matches("[0-9]+"))return null;
   try(Cursor row=c.getContentResolver().query(uri,new String[]{MediaStore.MediaColumns.SIZE},null,null,null)){
    if(row==null||!row.moveToFirst()||row.getLong(0)!=bytes)return null;
   }
   try(InputStream input=c.getContentResolver().openInputStream(uri)){if(input!=null&&sha.equalsIgnoreCase(hash(input)))return uri;}
  }catch(Exception ignored){}
  return null;
 }
 public static Uri save(Context c,String source,long build,File apk,long bytes,String sha)throws Exception{
  if(Build.VERSION.SDK_INT<29)throw new IOException("다운로드 폴더 저장은 Android 10 이상에서 지원합니다.");
  if(!apk.isFile()||apk.length()!=bytes||bytes<=0||!sha.matches("[a-fA-F0-9]{64}"))throw new IOException("APK 파일 정보가 일치하지 않습니다.");
  Uri existing=find(c,source,build,bytes,sha);if(existing!=null)return existing;
  ContentResolver resolver=c.getContentResolver();ContentValues values=new ContentValues();
  values.put(MediaStore.MediaColumns.DISPLAY_NAME,filename(source,build));values.put(MediaStore.MediaColumns.MIME_TYPE,MIME);
  values.put(MediaStore.MediaColumns.RELATIVE_PATH,Environment.DIRECTORY_DOWNLOADS+"/");values.put(MediaStore.MediaColumns.IS_PENDING,1);
  Uri uri=resolver.insert(MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),values);
  if(uri==null)throw new IOException("다운로드 파일을 만들지 못했습니다.");
  boolean published=false;
  try{
   MessageDigest md=MessageDigest.getInstance("SHA-256");long count=0;
   try(InputStream in=new FileInputStream(apk);OutputStream out=resolver.openOutputStream(uri,"w")){
    if(out==null)throw new IOException("다운로드 파일을 열지 못했습니다.");
    byte[] buffer=new byte[65536];int n;while((n=in.read(buffer))!=-1){count+=n;if(count>bytes)throw new IOException("APK 크기가 변경됐습니다.");out.write(buffer,0,n);md.update(buffer,0,n);}
   }
   if(count!=bytes||!sha.equalsIgnoreCase(hex(md.digest())))throw new IOException("저장한 APK 무결성 검사 실패");
   values.clear();values.put(MediaStore.MediaColumns.IS_PENDING,0);if(resolver.update(uri,values,null,null)!=1)throw new IOException("다운로드 저장을 완료하지 못했습니다.");
   published=true;c.getSharedPreferences("kisa_downloads",0).edit().putString(filename(source,build),uri.toString()).apply();return uri;
  }finally{if(!published)try{resolver.delete(uri,null,null);}catch(Exception ignored){}}
 }
 public static void openFolder(Context c){
  try{c.startActivity(new Intent(DownloadManager.ACTION_VIEW_DOWNLOADS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));}
  catch(Exception ignored){android.widget.Toast.makeText(c,"내 파일 → 다운로드에서 APK를 열어 주세요.",android.widget.Toast.LENGTH_LONG).show();}
 }
 public static boolean notifySaved(Context c,String source,long build){
  try{
   NotificationManager manager=(NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE);
   if(manager==null||!manager.areNotificationsEnabled())return false;
   manager.createNotificationChannel(new NotificationChannel(CHANNEL,"내비앱 업데이트 다운로드",NotificationManager.IMPORTANCE_DEFAULT));
   Intent folder=new Intent(DownloadManager.ACTION_VIEW_DOWNLOADS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
   PendingIntent click=PendingIntent.getActivity(c,7301,folder,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
   Notification n=new Notification.Builder(c,CHANNEL).setSmallIcon(android.R.drawable.stat_sys_download_done)
     .setContentTitle((source.equals("tmap")?"티맵":"네이버지도")+" 수정본 저장 완료")
     .setContentText("다운로드 폴더에서 "+filename(source,build)+" 파일을 눌러 설치하세요.")
     .setStyle(new Notification.BigTextStyle().bigText("다운로드 폴더에서 "+filename(source,build)+" 파일을 눌러 설치하세요."))
     .setContentIntent(click).setOnlyAlertOnce(true).setAutoCancel(true).build();
   manager.notify(7301,n);return true;
  }catch(Exception ignored){return false;}
 }
 private DownloadStore(){}
}
