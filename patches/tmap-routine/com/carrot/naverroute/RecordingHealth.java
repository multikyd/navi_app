package com.carrot.naverroute;
import android.content.*;import android.net.*;import android.os.*;import org.json.*;import java.io.*;import java.util.*;
/** Read-only loopback health channel, without GPS positions or route history. */
public final class RecordingHealth {
 static Context context;static boolean initialized;static volatile String token="",status="idle",mode="NONE";static volatile long mainAt,rgAt,started;static volatile boolean route;
 static final String SOCKET="carrot_tmap_record_health_v1";
 public static synchronized void init(Context c){if(initialized)return;initialized=true;context=c.getApplicationContext();android.content.SharedPreferences p=context.getSharedPreferences("dual_record_health",0);token=p.getString("token","");status=p.getString("status","idle");if(status.equals("active"))status="interrupted";RouteRequest.MAIN.post(TICK);Thread t=new Thread(()->serve(),"TmapRecordHealth");t.setDaemon(true);t.start();}
 static void persist(){if(context!=null)context.getSharedPreferences("dual_record_health",0).edit().putString("token",token).putString("status",status).apply();}
 public static synchronized void start(){start(UUID.randomUUID().toString());}
 static synchronized void start(String trip){if(context==null)return;token=trip;status="active";started=SystemClock.elapsedRealtime();rgAt=0;persist();RouteDiagnostics.event("dual_health_started",token);}
 public static synchronized void stop(){if(!status.equals("active"))return;status="stopped";persist();RouteDiagnostics.event("dual_health_stopped",token);}
 public static void guidance(){rgAt=SystemClock.elapsedRealtime();}
 public static JSONObject snapshot()throws Exception{long now=SystemClock.elapsedRealtime();return new JSONObject().put("version",1).put("guidance_owner",DualNavigation.owner()).put("token",token).put("status",status).put("drive_mode",mode).put("route",route).put("main_age_ms",mainAt==0?-1:now-mainAt).put("guidance_age_ms",rgAt==0?-1:now-rgAt).put("started_age_ms",started==0?-1:now-started);}
 static final Runnable TICK=new Runnable(){public void run(){mainAt=SystemClock.elapsedRealtime();try{Object mgr=NaverRuntime.manager();mode=String.valueOf(RouteRequest.get(mgr,"getDriveMode"));route=RouteRequest.get(mgr,"getRouteResult")!=null;}catch(Exception ignored){}RouteRequest.MAIN.postDelayed(this,2000);}};
 static void serve(){while(true){try(java.net.ServerSocket server=new java.net.ServerSocket()){server.setReuseAddress(true);server.bind(new java.net.InetSocketAddress(java.net.InetAddress.getByName("127.0.0.1"),18316),2);while(true)try(java.net.Socket socket=server.accept()){socket.setSoTimeout(1500);socket.getOutputStream().write((snapshot().put("package","com.skt.tmap.ku").toString()+"\n").getBytes("UTF-8"));socket.getOutputStream().flush();}catch(Exception e){android.util.Log.w("NaverRoute","health client failed",e);}}catch(Exception e){android.util.Log.w("NaverRoute","health server retry",e);try{Thread.sleep(3000);}catch(InterruptedException stopped){return;}}}}

 private RecordingHealth(){}
}
