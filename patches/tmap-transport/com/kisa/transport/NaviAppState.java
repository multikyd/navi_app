package com.kisa.transport;

import android.app.*;
import android.content.Context;
import android.os.*;
import org.json.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.lang.reflect.*;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/** Lifecycle hints and app-specific discovery; no vehicle control. */
public final class NaviAppState {
  private static final String INSTANCE=UUID.randomUUID().toString();
  private static final AtomicLong sequence=new AtomicLong();
  private static volatile boolean foreground;
  private static volatile long foregroundSince, lastGuidance;
  private static String source="tmap";
  private static boolean initialized;
  public static synchronized void init(Context context,String appSource) {
    if(initialized)return;
    try {
      Context c=context.getApplicationContext();
      if(!(c instanceof Application))return;
      if(Build.VERSION.SDK_INT>=28&&!Application.getProcessName().equals(c.getPackageName()))return;
      source=appSource;initialized=true;NaviUpdater.init(c,appSource);SignalDiagnostics.init(c,appSource);GuidanceSwitch.init((Application)c);CompanionHealth.init((Application)c);
      ((Application)c).registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks(){
        public void onActivityResumed(Activity a){if(!foreground)foregroundSince=SystemClock.elapsedRealtime();foreground=true;NaviUpdater.onResume(a);GuidanceSwitch.resumed(a);SignalPanel.resumed(a);TransientNotice.resumed(a);}
        public void onActivityPaused(Activity a){foreground=false;NaviUpdater.onPause(a);GuidanceSwitch.paused(a);SignalPanel.paused(a);TransientNotice.paused(a);}
        public void onActivityCreated(Activity a,Bundle b){}
        public void onActivityStarted(Activity a){}
        public void onActivityStopped(Activity a){}
        public void onActivitySaveInstanceState(Activity a,Bundle b){}
        public void onActivityDestroyed(Activity a){}
      });
      Thread t=new Thread(()->discovery(),"Kisa-app-discovery");t.setDaemon(true);t.start();
    }catch(Throwable ignored){}
  }
  public static JSONObject decorate(JSONObject packet,String appSource,boolean active) {
    try {
      boolean recordOnly=false;try{recordOnly=appSource.equals("tmap")&&Boolean.TRUE.equals(Class.forName("com.kisa.naverroute.DualNavigation").getMethod("recordOnly").invoke(null));}catch(Throwable ignored){}
      packet.put("source",appSource).put("navi_state",new JSONObject()
        .put("version",1).put("instance_id",INSTANCE).put("sequence",sequence.incrementAndGet())
        .put("foreground",foreground).put("foreground_since_elapsed_ms",foregroundSince).put("active",active&&!recordOnly).put("record_only",recordOnly));
    }catch(Throwable ignored){}
    NaviUpdater.observe(packet);SignalDiagnostics.observe(packet);SignalPanel.observe(packet,appSource);
    return packet;
  }
  private static boolean validGuidance(JSONObject r) {
    double lat=r.optDouble("vpPosPointLat",Double.NaN),lon=r.optDouble("vpPosPointLon",Double.NaN);
    return Double.isFinite(lat)&&Double.isFinite(lon)&&Math.abs(lat)<=90&&Math.abs(lon)<=180&&(lat!=0||lon!=0);
  }
  public static byte[] decorateTmap(byte[] bytes) {
    try {
      JSONObject p=new JSONObject(new String(bytes,StandardCharsets.UTF_8).trim());
      long stamp=p.optLong("timestamp_ms",0);
      // Do not drain old queued positions as if they were current after reconnect.
      if(stamp>0&&System.currentTimeMillis()-stamp>3000)return heartbeatTmap();
      JSONObject r=p.optJSONObject("rgdata");
      if(r!=null)lastGuidance=validGuidance(r)?SystemClock.elapsedRealtime():0;
      return encode(decorate(p,"tmap",lastGuidance>0&&SystemClock.elapsedRealtime()-lastGuidance<4000));
    }catch(Throwable ignored){return heartbeatTmap();}
  }
  public static byte[] heartbeatTmap() {
    try{return encode(decorate(new JSONObject().put("timestamp_ms",System.currentTimeMillis()).put("navi_status",true),
      "tmap",lastGuidance>0&&SystemClock.elapsedRealtime()-lastGuidance<4000));}
    catch(Throwable ignored){return "{}\n".getBytes(StandardCharsets.UTF_8);}
  }
  private static byte[] encode(JSONObject p){return (p.toString()+"\n").getBytes(StandardCharsets.UTF_8);}
  public static void refreshTmapSocket(Socket socket) {
    try{String ip=(String)field("ip").get(null);if(socket!=null&&socket.isConnected()&&ip!=null&&!socket.getInetAddress().getHostAddress().equals(ip))socket.close();}
    catch(Throwable ignored){}
  }
  private static Field field(String name)throws Exception {
    Field f=Class.forName("com.skt.tmap.engine.navigation.util.KisaUtilj").getDeclaredField(name);f.setAccessible(true);return f;
  }
  private static void discovery() {
    int port=source.equals("naver-map")?17705:17706;
    while(true)try(DatagramSocket s=new DatagramSocket(port)){
      byte[] bytes=new byte[16384];
      while(true){DatagramPacket p=new DatagramPacket(bytes,bytes.length);s.receive(p);
        try{
          JSONObject obj=new JSONObject(new String(p.getData(),p.getOffset(),p.getLength(),StandardCharsets.UTF_8));
          if(!obj.has("ip"))continue;
          String ip=p.getAddress().getHostAddress();
          if(source.equals("naver-map")){
            Method m=Class.forName("com.kisa.naver.NaverBridge").getDeclaredMethod("touch",String.class);m.setAccessible(true);m.invoke(null,ip);
          }else{
            field("ip").set(null,ip);field("socketTimeout").set(null,Integer.valueOf(0));
            field("naviDebug").setInt(null,obj.optInt("navi_debug",0));
          }
        }catch(Throwable ignored){}
      }
    }catch(Throwable ignored){try{Thread.sleep(3000);}catch(InterruptedException stop){return;}}
  }
}
