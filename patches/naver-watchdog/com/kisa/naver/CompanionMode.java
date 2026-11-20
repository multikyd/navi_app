package com.kisa.naver;
import android.app.*;import android.content.*;import android.net.Uri;import android.os.*;import android.util.Log;import org.json.*;import java.util.*;
/** Quiet Naver guidance companion while Tmap owns screen, speech and C4. */
public final class CompanionMode {
 static Application app;static volatile boolean quiet;static String launch="",token="";static long epoch;static boolean returned;static double goalLat,goalLon;
 static final Handler MAIN=new Handler(Looper.getMainLooper());
 public static synchronized void init(Application a){if(app!=null)return;app=a;android.content.SharedPreferences p=a.getSharedPreferences("kisa_companion",0);quiet=p.getBoolean("quiet",false);launch=p.getString("launch","");token=p.getString("token","");}
 public static boolean background(){return quiet;}
 public static String guidanceOwner(){return token.isEmpty()?"":quiet?"tmap":"naver";}
 public static String switchToken(){if(token.isEmpty())return "";try{Class<?> navi=app.getClassLoader().loadClass("com.naver.maps.navi.NaverNavi");Object control=TmapBumpMerge.call(navi.getField("a").get(null),"j"),session=TmapBumpMerge.call(control,"getCurrentSession");return "RouteGuiding".equals(String.valueOf(TmapBumpMerge.call(session,"getMode")))?token:"";}catch(Exception e){return "";}}
 public static synchronized boolean setGuidanceOwner(String owner){if(switchToken().isEmpty()||!("tmap".equals(owner)||"naver".equals(owner)))return false;quiet="tmap".equals(owner);returned=true;epoch++;save();setAudioQuiet(quiet);refreshOverlay();return true;}
 static void setAudioQuiet(boolean muted){try{Class<?> navi=app.getClassLoader().loadClass("com.naver.maps.navi.NaverNavi");Object control=TmapBumpMerge.call(navi.getField("a").get(null),"j"),session=TmapBumpMerge.call(control,"getCurrentSession"),audio=TmapBumpMerge.call(session,"b");if(audio!=null)TmapBumpMerge.call(audio,"f",muted);}catch(Exception e){Log.w("KisaNaviSwitch","audio mode update failed",e);}}
 static void save(){if(app!=null)app.getSharedPreferences("kisa_companion",0).edit().putBoolean("quiet",quiet).putString("launch",launch).putString("token",token).apply();}
 public static synchronized void intent(Activity a){handle(a.getIntent());}
 public static synchronized void handle(Intent intent){try{
  Uri u=intent==null?null:intent.getData();if(u==null||!"nmap".equals(u.getScheme())||!"navigation".equals(u.getHost()))return;
  String id=u.getQueryParameter("kisa_launch"),owner=u.getQueryParameter("kisa_owner");
  if(id!=null&&id.equals(launch))return;
  boolean background="com.skt.tmap.ku".equals(u.getQueryParameter("appname"))&&"tmap".equals(owner)&&id!=null;
  quiet=background;launch=id==null?"":id;token=u.getQueryParameter("kisa_recording");if(token==null)token="";returned=false;long version=++epoch;save();refreshOverlay();
  if(quiet){goalLat=Double.parseDouble(u.getQueryParameter("dlat"));goalLon=Double.parseDouble(u.getQueryParameter("dlng"));cancelAudio();MAIN.postDelayed(()->returnToTmap(version),12000);}
  Log.i("KisaCompanion","owner="+(quiet?"tmap":"naver"));
 }catch(Exception e){Log.w("KisaCompanion","intent failed",e);}}
 public static void refreshOverlay(){MAIN.post(()->{try{if(app==null)return;Class<?> type=app.getClassLoader().loadClass("com.naver.map.feature.navigation.minitbt.MiniTbtManager");Object manager=type.getField("i").get(null);if(manager!=null)type.getMethod("q").invoke(manager);}catch(Exception e){Log.w("KisaCompanion","mini TBT refresh failed",e);}});}
 static void cancelAudio(){try{Class<?> navi=app.getClassLoader().loadClass("com.naver.maps.navi.NaverNavi");Object singleton=navi.getField("a").get(null),control=TmapBumpMerge.call(singleton,"j"),session=TmapBumpMerge.call(control,"getCurrentSession"),audio=TmapBumpMerge.call(session,"b");if(audio!=null)TmapBumpMerge.call(audio,"f",true);}catch(Exception ignored){}}
 public static void guidance(Object session){if(!quiet||returned)return;try{
  if(!"RouteGuiding".equals(String.valueOf(TmapBumpMerge.call(session,"getMode"))))return;
  List<?> points=TmapBumpMerge.list(TmapBumpMerge.call(TmapBumpMerge.call(session,"getRoute"),"getPathPoints"));if(points.isEmpty())return;TmapBumpPolicy.Point end=TmapBumpMerge.location(points.get(points.size()-1));
  if(TmapBumpPolicy.distance(end,new TmapBumpPolicy.Point(goalLat,goalLon))>500)return;
  long version=epoch;MAIN.postDelayed(()->returnToTmap(version),1200);
 }catch(Exception ignored){}}
 static synchronized void returnToTmap(long version){if(!quiet||returned||epoch!=version||app==null)return;try{Intent open=app.getPackageManager().getLaunchIntentForPackage("com.skt.tmap.ku");if(open==null)return;open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);refreshOverlay();app.startActivity(open);returned=true;Log.i("KisaCompanion","tmap_foreground_restored; naver_guidance_kept");}catch(Exception e){Log.w("KisaCompanion","return to Tmap failed",e);}}
 public static void health(JSONObject row){if(token.isEmpty()||!token.equals(row.optString("token")))return;if("active".equals(row.optString("status"))){String owner=row.optString("guidance_owner");if(("naver".equals(owner)||"tmap".equals(owner))&&!owner.equals(guidanceOwner())){long version=epoch;MAIN.post(()->{if(version==epoch)setGuidanceOwner(owner);});}return;}if(quiet&&("stopped".equals(row.optString("status"))||"interrupted".equals(row.optString("status")))){long version=epoch;MAIN.post(()->stopCompanion(version));}}
 static synchronized void stopCompanion(long version){if(!quiet||epoch!=version)return;try{Class<?> navi=app.getClassLoader().loadClass("com.naver.maps.navi.NaverNavi");TmapBumpMerge.call(navi.getField("a").get(null),"g");Log.i("KisaCompanion","background_navigation_stopped_with_tmap");}catch(Exception e){Log.w("KisaCompanion","stop companion failed",e);return;}quiet=false;returned=true;epoch++;save();}
 private CompanionMode(){}
}
