package com.kisa.naver;
import android.app.*;import android.content.*;import android.net.Uri;import android.os.*;import android.util.Log;import org.json.*;import java.util.*;
import com.kisa.transport.GuidanceSwitch;
import com.kisa.transport.TripRoute;
/** Quiet Naver guidance companion while Tmap owns screen, speech and C4. */
public final class CompanionMode {
 static Application app;static volatile boolean quiet;static String launch="";static volatile String token="";static long epoch;static boolean returned;
 static final Handler MAIN=new Handler(Looper.getMainLooper());
 static JSONObject active, expected;
 static Object nativeRoute, nativeSession;
 static String expectedToken="", expectedOwner="";
 static long expectedUntil;
 public static synchronized void init(Application a){if(app!=null)return;app=a;launch=a.getSharedPreferences("kisa_companion",0).getString("launch","");}
 public static boolean background(){return quiet;}
 public static String guidanceOwner(){return token.isEmpty()?"":quiet?"tmap":"naver";}
 public static String switchToken(){return activeRoute()==null?"":token;}
 public static JSONObject activeRoute(){try{
  Class<?> navi=app.getClassLoader().loadClass("com.naver.maps.navi.NaverNavi");
  Object control=TmapBumpMerge.call(navi.getField("a").get(null),"j"),session=TmapBumpMerge.call(control,"getCurrentSession");
  return "RouteGuiding".equals(String.valueOf(TmapBumpMerge.call(session,"getMode")))
      &&nativeRoute==TmapBumpMerge.call(session,"getRoute")?active:null;
 }catch(Exception e){return null;}}
 public static synchronized boolean setGuidanceOwner(String owner){if(switchToken().isEmpty()||!("tmap".equals(owner)||"naver".equals(owner)))return false;quiet="tmap".equals(owner);returned=true;epoch++;save();setAudioQuiet(quiet);refreshOverlay();return true;}
 static void setAudioQuiet(boolean muted){try{Class<?> navi=app.getClassLoader().loadClass("com.naver.maps.navi.NaverNavi");Object control=TmapBumpMerge.call(navi.getField("a").get(null),"j"),session=TmapBumpMerge.call(control,"getCurrentSession"),audio=TmapBumpMerge.call(session,"b");if(audio!=null)TmapBumpMerge.call(audio,"f",muted);}catch(Exception e){Log.w("KisaNaviSwitch","audio mode update failed",e);}}
 static void save(){if(app!=null)app.getSharedPreferences("kisa_companion",0).edit().putBoolean("quiet",quiet).putString("launch",launch).putString("token",token).apply();}
 public static synchronized void intent(Activity a){handle(a.getIntent());}
 public static synchronized void handle(Intent intent){try{
  Uri u=intent==null?null:intent.getData();if(u==null||!"nmap".equals(u.getScheme())||!"navigation".equals(u.getHost()))return;
  String id=u.getQueryParameter("kisa_launch"),owner=u.getQueryParameter("kisa_owner");
  String trip=u.getQueryParameter("kisa_recording");
  if(id==null||id.equals(launch)||trip==null||trip.isEmpty()||!"com.skt.tmap.ku".equals(u.getQueryParameter("appname")))return;
  JSONObject goal=uriPoint(u,"d");JSONArray vias=new JSONArray();
  for(int i=1;i<=5&&u.getQueryParameter("v"+i+"lat")!=null;i++)vias.put(uriPoint(u,"v"+i));
  expected=new JSONObject().put("goal",goal).put("vias",vias);
  expectedUntil=SystemClock.elapsedRealtime()+30000;
  expectedOwner=owner;expectedToken=trip;launch=id;save();
 }catch(Exception e){Log.w("KisaCompanion","intent failed",e);}}
 public static void refreshOverlay(){MAIN.post(()->{try{if(app==null)return;Class<?> type=app.getClassLoader().loadClass("com.naver.map.feature.navigation.minitbt.MiniTbtManager");Object manager=type.getField("i").get(null);if(manager!=null)type.getMethod("q").invoke(manager);}catch(Exception e){Log.w("KisaCompanion","mini TBT refresh failed",e);}});}
 static JSONObject uriPoint(Uri uri,String prefix)throws Exception{
  return TripRoute.point(Double.parseDouble(uri.getQueryParameter(prefix+"lat")),
      Double.parseDouble(uri.getQueryParameter(prefix+"lng")),uri.getQueryParameter(prefix+"name"));
 }
 static JSONObject point(Object position)throws Exception{
  Object where=TmapBumpMerge.call(position,"getLocation");
  Object request=TmapBumpMerge.call(position,"getRequest");
  Object name=request==null?null:TmapBumpMerge.call(request,"getName");
  return TripRoute.point(where.getClass().getField("latitude").getDouble(where),
      where.getClass().getField("longitude").getDouble(where),name==null?"":name.toString());
 }
 public static void guidance(Object session){
  if(Looper.myLooper()!=Looper.getMainLooper()){MAIN.post(()->guidance(session));return;}
  try{
   if(!"RouteGuiding".equals(String.valueOf(TmapBumpMerge.call(session,"getMode"))))return;
   Object route=TmapBumpMerge.call(session,"getRoute");
   if(route==null||route==nativeRoute)return;
   Object summary=TmapBumpMerge.call(route,"getSummary");
   JSONArray vias=new JSONArray();
   for(Object via:TmapBumpMerge.list(TmapBumpMerge.call(summary,"getWaypoints")))vias.put(point(via));
   JSONObject next=new JSONObject().put("goal",point(TmapBumpMerge.call(summary,"getGoal"))).put("vias",vias);
   boolean linked=expected!=null&&SystemClock.elapsedRealtime()<expectedUntil&&TripRoute.same(expected,next);
   boolean changed=!TripRoute.same(active,next);
   active=next;nativeRoute=route;nativeSession=session;
   if(!linked&&!changed)return;
   expected=null;
   token=linked?expectedToken:UUID.randomUUID().toString();
   quiet=linked&&"tmap".equals(expectedOwner);
   returned=!quiet;
   long version=++epoch;
   save();setAudioQuiet(quiet);refreshOverlay();
   RecordingWatchdog.expect(linked?token:"");
   if(quiet)MAIN.postDelayed(()->returnToTmap(version),900);
   else if(!linked){
    DestinationSuggestions.remember(app,next.getJSONObject("goal"));
    Class<?> interval=app.getClassLoader().loadClass("com.naver.maps.navi.v2.shared.api.utils.TimeInterval");
    long duration=((Number)TmapBumpMerge.call(summary,"duration")).longValue();
    double seconds=((Number)interval.getMethod("seconds-impl",long.class).invoke(null,duration)).doubleValue();
    MAIN.postDelayed(()->{if(epoch==version)GuidanceSwitch.compareFromNaver(seconds);},600);
   }
  }catch(Exception e){Log.w("KisaCompanion","Could not read native route",e);}
 }
 static void stopped(Object session){
  MAIN.post(()->{
   if(session!=nativeSession)return;
   active=null;nativeRoute=null;nativeSession=null;token="";quiet=false;returned=true;epoch++;
   save();refreshOverlay();RecordingWatchdog.expect("");
  });
 }
 static synchronized void returnToTmap(long version){if(!quiet||returned||epoch!=version||app==null)return;try{Intent open=app.getPackageManager().getLaunchIntentForPackage("com.skt.tmap.ku");if(open==null)return;open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);refreshOverlay();app.startActivity(open);returned=true;Log.i("KisaCompanion","tmap_foreground_restored; naver_guidance_kept");}catch(Exception e){Log.w("KisaCompanion","return to Tmap failed",e);}}
 public static void health(JSONObject row){
  String ended=row.optString("token");
  if(!quiet||ended.isEmpty()||!ended.equals(token)||!"stopped".equals(row.optString("status")))return;
  MAIN.post(()->{if(quiet&&ended.equals(token)&&activeRoute()!=null)setGuidanceOwner("naver");});
 }
 private CompanionMode(){}
}
