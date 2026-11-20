package com.carrot.naverroute;
import android.content.*;import android.net.Uri;import android.os.*;import android.widget.Toast;
import org.json.*;import java.util.*;
/** Naver owns guidance. Tmap keeps its untouched native route and recording session. */
public final class DualNavigation {
 static final Map<Object,JSONObject> SUMMARIES=Collections.synchronizedMap(new WeakHashMap<>());
 static final Map<Object,Object> PREVIEW_NATIVE=Collections.synchronizedMap(new WeakHashMap<>());
 static final LinkedHashMap<String,Object> PREVIEW_IDS=new LinkedHashMap<>();
 static Object nativeCopy(Object preview)throws Exception{Class<?> cls=preview.getClass();Object result=cls.getMethod("createSingleRoute",cls,int.class).invoke(null,preview,0);byte[] bytes=(byte[])get(result,"getBinaryData");Object engine=cls.getClassLoader().loadClass("com.skt.tmap.engine.navigation.TmapNavigation").getMethod("getInstance").invoke(null);RouteRequest.set(result,"routeInfos",new ArrayList<>((List<?>)engine.getClass().getMethod("getRouteData",byte[].class,int[].class).invoke(engine,bytes,new int[]{bytes.length})));return result;}
 public static Object forDriving(Object preview){if(preview==null)return null;Object nativeResult=PREVIEW_NATIVE.get(preview);try{if(nativeResult==null)for(Object info:(List<?>)RouteRequest.field(preview.getClass(),"routeInfos").get(preview)){nativeResult=PREVIEW_IDS.get(String.valueOf(RouteRequest.field(info.getClass(),"id").get(info)));if(nativeResult!=null)break;}}catch(Exception ignored){}return nativeResult==null?preview:nativeResult;}
 public static boolean recordOnly(){return running;}
 public static boolean suppressPopup(Object presenter){if(!running)return false;try{Object reference=RouteRequest.field(presenter.getClass(),"A0").get(presenter),popup=reference==null?null:((java.lang.ref.Reference<?>)reference).get();if(popup!=null){Context c=(Context)RouteRequest.field(presenter.getClass(),"a0").get(presenter);popup.getClass().getMethod("a",Context.class).invoke(popup,c);}RouteRequest.field(presenter.getClass(),"D0").setBoolean(presenter,false);}catch(Exception e){RouteDiagnostics.event("dual_popup_hide_failed",e.toString());}return true;}
 static Object armed;static Context context;static volatile boolean running,backgroundRunning;static boolean armedNaver=true;static String trip="";static long selection;
 static Object get(Object o,String name)throws Exception{return RouteRequest.get(o,name);}
 static String key(Object result)throws Exception{return NaverRuntime.destination(get(result,"getRouteOption"));}
 public static void select(Context c,Object result){select(c,result,true);}
 public static void select(Context c,Object result,boolean naverOwner){context=c.getApplicationContext();armed=result;armedNaver=naverOwner;selection++;RecordingHealth.stop();running=false;backgroundRunning=false;trip="";RouteDiagnostics.event("dual_navigation_selected","owner="+(naverOwner?"naver":"tmap")+" armed="+(result!=null));}
 public static String owner(){return running?"naver":backgroundRunning?"tmap":"";}
 public static String guidanceOwner(){return owner();}
 public static String switchToken(){return (running||backgroundRunning)&&"active".equals(RecordingHealth.status)?RecordingHealth.token:"";}
 public static boolean setGuidanceOwner(String next){if(switchToken().isEmpty()||!("tmap".equals(next)||"naver".equals(next)))return false;running="naver".equals(next);backgroundRunning=!running;RouteDiagnostics.event("dual_owner_switched","owner="+next+" native_route_preserved=true");return true;}
 public static boolean mute(){if(running)return true;try{return armedNaver&&armed!=null&&!"SIMULATION_DRIVE".equals(String.valueOf(get(NaverRuntime.manager(),"getDriveMode")));}catch(Exception e){return false;}}
 static boolean matches(Object result)throws Exception{return result!=null&&armed!=null&&key(result).equals(key(armed))&&Arrays.equals((byte[])get(result,"getBinaryData"),(byte[])get(armed,"getBinaryData"));}
 static String name(Object waypoint){try{Object value=get(waypoint,"getName");return value==null?"":value.toString();}catch(Exception e){return "";}}
 public static Intent intent(Object option,boolean autoStart)throws Exception{return intentForOwner(option,autoStart,"naver");}
 public static Intent naverRecoveryIntent(String token)throws Exception{
  if(!token.equals(switchToken())||!"tmap".equals(owner()))return null;
  Object manager=NaverRuntime.manager();
  if(!"REAL_DRIVE".equals(String.valueOf(get(manager,"getDriveMode"))))return null;
  return intentForOwner(get(get(manager,"getRouteResult"),"getRouteOption"),true,"tmap");
 }
 public static boolean recoverTmap(Context c,String token)throws Exception{
  NaverRuntime.init(c);
  return TmapRecovery.open(c,token);
 }
 public static Intent intentForOwner(Object option,boolean autoStart,String owner)throws Exception{
  double[] end=RouteRequest.coordinate(get(option,"getDestination"));Uri.Builder uri=new Uri.Builder().scheme("nmap").authority("navigation").appendQueryParameter("dlat",Double.toString(end[1])).appendQueryParameter("dlng",Double.toString(end[0])).appendQueryParameter("dname",name(get(option,"getDestination"))).appendQueryParameter("appname","com.skt.tmap.ku").appendQueryParameter("autostart",Boolean.toString(autoStart)).appendQueryParameter("carrot_owner",owner).appendQueryParameter("carrot_launch",UUID.randomUUID().toString());
  if((running||backgroundRunning)&&!RecordingHealth.token.isEmpty())uri.appendQueryParameter("carrot_recording",RecordingHealth.token);
  List<?> vias=(List<?>)get(option,"getWayPoints");if(vias.size()>5)throw new IllegalArgumentException("At most five waypoints");for(int i=0;i<vias.size();i++){double[] p=RouteRequest.coordinate(vias.get(i));String v="v"+(i+1);uri.appendQueryParameter(v+"lat",Double.toString(p[1])).appendQueryParameter(v+"lng",Double.toString(p[0])).appendQueryParameter(v+"name",name(vias.get(i)));}
  // Omit the departure coordinates: the Naver app must use its current real GPS fix.
  return new Intent(Intent.ACTION_VIEW,uri.build()).setPackage("com.nhn.android.nmap").addCategory(Intent.CATEGORY_BROWSABLE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
 }
 public static void onStarted(Object manager){try{
  Object route=get(manager,"getRouteResult");String mode=String.valueOf(get(manager,"getDriveMode"));
  if(!"REAL_DRIVE".equals(mode)){running=false;backgroundRunning=false;armed=null;return;}
  if(TmapRecovery.adopt(route))return;
  if((running||backgroundRunning)&&trip.equals(key(route)))return;
  running=false;backgroundRunning=false;if(!matches(route))return;Object selected=armed;boolean naverOwner=armedNaver;Context app=context;long epoch=selection;trip=key(route);running=naverOwner;backgroundRunning=!naverOwner;RecordingHealth.start();
  TmapRecovery.save(app,RecordingHealth.token,get(route,"getRouteOption"));
  RouteDiagnostics.event("dual_native_guidance_started","native_binary_unchanged=true session_present="+!String.valueOf(get(route,"getSessionId")).isEmpty());
  RouteRequest.MAIN.postDelayed(()->{if((!running&&!backgroundRunning)||selection!=epoch)return;try{if(!trip.equals(key(get(manager,"getRouteResult"))))return;app.startActivity(intentForOwner(get(selected,"getRouteOption"),true,naverOwner?"naver":"tmap"));armed=null;RouteDiagnostics.event("dual_naver_app_opened","coordinate_navigation=true owner="+(naverOwner?"naver":"tmap"));}catch(Exception e){RecordingHealth.stop();running=false;backgroundRunning=false;armed=null;Toast.makeText(app,"네이버 지도를 열지 못했습니다. 앱 설치 상태를 확인해 주세요.",Toast.LENGTH_LONG).show();RouteDiagnostics.event("dual_naver_app_open_failed",e.toString());}},1000);
 }catch(Exception e){running=false;backgroundRunning=false;RouteDiagnostics.event("dual_start_failed",e.toString());}}
 public static void stop(){RecordingHealth.stop();running=false;backgroundRunning=false;armed=null;selection++;trip="";}
 private DualNavigation(){}
}
