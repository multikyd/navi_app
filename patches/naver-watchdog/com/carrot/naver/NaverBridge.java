package com.carrot.naver;

import android.app.*;
import android.os.*;
import android.content.*;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.widget.*;
import android.util.Log;
import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.lang.ref.WeakReference;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;

/** Naver Map 6.11.2.2 bridge. All injected entry points contain failures inside this helper. */
public final class NaverBridge {
  private static final String TAG="CarrotNaver";
  private static final String PREFIX="com.naver.maps.navi.v2.";
  private static Application app;
  private static volatile Object session;
  private static volatile Object lastPoint;
  private static volatile long pointAt;
  private static volatile JSONObject latest;
  private static volatile CharSequence panelText="";
  private static volatile boolean simulation;
  private static volatile boolean showPanel=true;
  private static final Map<String,Peer> peers=new ConcurrentHashMap<>();
  private static final Map<String,Method> methods=new ConcurrentHashMap<>();
  private static final Handler main=new Handler(Looper.getMainLooper());
  private static WeakReference<Activity> activity=new WeakReference<>(null);
  private static WeakReference<TextView> panel=new WeakReference<>(null);
  private static volatile long lastError;
  private static long configChecked,packetNumber;
  private static Object sentRoute;
  private static long routeAt;
  private static final class Peer {
    final String ip; volatile long seen; final ArrayBlockingQueue<String> queue=new ArrayBlockingQueue<>(8);
    Peer(String address){ip=address;seen=SystemClock.elapsedRealtime(); Thread t=new Thread(()->sendLoop(this),"CarrotNaver-TCP");t.setDaemon(true);t.start();}
  }
  public static synchronized void init(Context context) {
    try {
      if(app!=null || !(context.getApplicationContext() instanceof Application))return;
      app=(Application)context.getApplicationContext();
      com.carrot.transport.NaviAppState.init(app,"naver-map");RecordingWatchdog.init(app);SpeedTrace.init(app);TmapBumpMerge.init(app);CompanionMode.init(app);NaverHealth.init(app);
      File ext=app.getExternalFilesDir(null);
      if(ext!=null){
        File cfg=new File(ext,"carrot_naver.json");
        if(!cfg.exists()) Files.write(cfg.toPath(),"{\"version\":1,\"show_panel\":true,\"targets\":[]}".getBytes(StandardCharsets.UTF_8));
      }
      app.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks(){
        public void onActivityResumed(Activity a){CompanionMode.intent(a);activity=new WeakReference<>(a);SignalLayoutGap.configure(a);RecordingWatchdog.resumed(a);}
        public void onActivityPaused(Activity a){RecordingWatchdog.paused(a);if(activity.get()==a){activity.clear();TextView p=panel.get();if(p!=null)p.setVisibility(View.GONE);}}
        public void onActivityCreated(Activity a,Bundle b){CompanionMode.intent(a);}
        public void onActivityStarted(Activity a){}
        public void onActivityStopped(Activity a){}
        public void onActivitySaveInstanceState(Activity a,Bundle b){}
        public void onActivityDestroyed(Activity a){}
      });
      Thread discover=new Thread(()->discoverLoop(),"CarrotNaver-discovery");discover.setDaemon(true);discover.start();
      ScheduledExecutorService timer=Executors.newSingleThreadScheduledExecutor(r->{Thread t=new Thread(r,"CarrotNaver-data");t.setDaemon(true);return t;});
      timer.scheduleAtFixedRate(()->tick(),500,1000,TimeUnit.MILLISECONDS);
      Log.i(TAG,"Bridge initialized: UDP 7705 discovery, TCP 7712; external JSON configuration");
    }catch(Throwable error){error(error);}
  }
  public static void onSession(Object value) {
    try {boolean changed=session!=value;if(changed){sentRoute=null;}session=value;
      Object point=call(value,"getGuidePoint");if(changed||point!=lastPoint){lastPoint=point;pointAt=point==null?0:SystemClock.elapsedRealtime();}
    }catch(Throwable error){error(error);}
  }
  public static void onPoint(Object value) {
    CompanionMode.guidance(value);
    try {if(session==value){lastPoint=call(value,"getGuidePoint");pointAt=SystemClock.elapsedRealtime();}}catch(Throwable error){error(error);}
  }
  public static void onStop(Object value) {
    try {if(session==value){SpeedTrace.session(false);session=null;pointAt=0;panelText="";sentRoute=null;publish(new JSONObject().put("source","naver-map").put("timestamp_ms",System.currentTimeMillis()).put("route",new JSONArray()));main.post(()->updatePanel());}}catch(Throwable error){error(error);}
  }
  private static Object call(Object value,String name,Object...args) throws Exception {
    if(value==null)return null;
    String key=value.getClass().getName()+"#"+name+"/"+args.length;
    Method m=methods.get(key);
    if(m==null){for(Method candidate:value.getClass().getMethods())if(candidate.getName().equals(name)&&candidate.getParameterTypes().length==args.length){m=candidate;break;}
      if(m==null)throw new NoSuchMethodException(key);m.setAccessible(true);methods.put(key,m);}
    return m.invoke(value,args);
  }
  private static double number(Object o){return o instanceof Number?((Number)o).doubleValue():0;}
  private static String name(Object o){return o instanceof Enum?((Enum<?>)o).name():String.valueOf(o);}
  private static double lat(Object o)throws Exception{return o.getClass().getField("latitude").getDouble(o);}
  private static double lon(Object o)throws Exception{return o.getClass().getField("longitude").getDouble(o);}
  private static String text(Object o){return o==null?"":String.valueOf(o);}
  private static List<?> list(Object o){return o instanceof List?(List<?>)o:Collections.emptyList();}
  private static void error(Throwable e){long now=SystemClock.elapsedRealtime();if(now-lastError>10000){lastError=now;Log.w(TAG,"Bridge operation failed",e);}}
  private static JSONObject read(File f)throws Exception{
    if(f==null || !f.isFile() || f.length()>262144)throw new IOException("Missing or oversized JSON");
    String s=new String(Files.readAllBytes(f.toPath()),StandardCharsets.UTF_8);return new JSONObject(s.startsWith("\ufeff")?s.substring(1):s);
  }
  private static void config(){
    long now=SystemClock.elapsedRealtime();if(now-configChecked<5000)return;configChecked=now;
    try{JSONObject c=read(new File(app.getExternalFilesDir(null),"carrot_naver.json"));
      showPanel=c.optBoolean("show_panel",true);
      JSONArray a=c.optJSONArray("targets");if(a!=null)for(int i=0;i<a.length();i++)touch(a.optString(i,""));
    }catch(Throwable e){error(e);}
  }
  private static void tick(){
    try{
      config();Object s=session;long now=SystemClock.elapsedRealtime();SpeedTrace.session(s!=null&&!name(call(s,"getMode")).equals("SimulGuiding"));
      publish(new JSONObject().put("timestamp_ms",System.currentTimeMillis()).put("navi_status",true));
      if(s==null||now-pointAt>5000){SpeedTrace.gap("session_or_position_stale",now-pointAt);panelText="";main.post(()->updatePanel());return;}
      Object point=call(s,"getGuidePoint");if(point==null){SpeedTrace.gap("guide_point_missing",now-pointAt);return;}
      Object pos=call(point,"getLocation");double a=lat(pos),b=lon(pos),heading=number(call(point,"getHeading"));
      if(!Double.isFinite(a)||!Double.isFinite(b)||Math.abs(a)>90||Math.abs(b)>180||(a==0&&b==0))return;
      simulation=name(call(s,"getMode")).equals("SimulGuiding");
      SpeedTrace.guidance(a,b,number(call(point,"getSpeedKmPerHour")),now-pointAt);
      JSONObject rg=new JSONObject().put("vpPosPointLat",a).put("vpPosPointLon",b).put("nPosAngle",heading).put("nPosSpeed",number(call(point,"getSpeedKmPerHour")))
        .put("nRoadLimitSpeed",(int)number(call(point,"getLinkMaxSpeed"))).put("szPosRoadName",text(call(s,"getRoadName"))).put("nSdiType",-1).put("nSdiDist",0).put("nSdiSpeedLimit",0)
        .put("nSdiBlockType",-1).put("nSdiBlockSpeed",0).put("nSdiBlockDist",0)
        .put("nSdiPlusBlockType",-1).put("nSdiPlusBlockSpeed",0).put("nSdiPlusBlockDist",0).put("nSdiPlusType",-1).put("nSdiPlusDist",0).put("nSdiPlusSpeedLimit",0).put("nTBTTurnType",-1).put("nTBTDist",0).put("nTBTTurnTypeNext",-1).put("nTBTDistNext",0);
      String roadKind=name(call(point,"getRoadKind"));rg.put("roadcate",roadKind.equals("Expressway")?0:roadKind.equals("CityExpressway")?1:3);
      JSONArray rawSafety=new JSONArray();JSONObject camera=null,extra=null,bump=null,activeSection=null;
      for(Object safety:list(call(s,"getSafetyList"))){
        JSONObject row=new JSONObject();rawSafety.put(row);
        try{
          String code=name(call(safety,"getCode"));int type=BridgeMath.safety(code);row.put("code",code).put("type",type);
          // Do not dereference optional geometry/limits on unsupported ordinary signs.
          if(type<0)continue;
          double d=number(call(safety,"distance"));
          if(!Double.isFinite(d)||d<=0){row.put("distance",JSONObject.NULL).put("ignored","not_a_positive_finite_distance");continue;}
          row.put("distance",d).put("limit",0);
          if(type!=22&&!isCamera(type)){
            if(extra==null||d<extra.optDouble("distance"))extra=row;
            continue;
          }
          boolean hasLocation=false;
          try{Object loc=call(call(safety,"getInfo"),"getLocation");double targetLat=lat(loc),targetLon=lon(loc);
            if(validLocation(targetLat,targetLon)){row.put("lat",targetLat).put("lon",targetLon);hasLocation=true;}
          }catch(Throwable ignored){}
          row.put("location_available",hasLocation);
          if(type==22){
            // Native positive distances remain usable even when optional marker geometry is absent.
            if(bump==null||d<bump.optDouble("distance"))bump=row;
          }else{
            try{Object speedLimit=call(safety,"getSpeedLimit-ujHrAxs");double limit=speedLimit==null?0:number(call(speedLimit,"getSpeed"));
              if(Double.isFinite(limit)&&limit>0&&limit<=300)row.put("limit",(int)limit);
            }catch(Throwable ignored){row.put("limit_available",false);}
            Set<String> attributes=new HashSet<>();
            try{Object attrs=call(safety,"getCameraAttributes");if(attrs instanceof Iterable)for(Object attr:(Iterable<?>)attrs)attributes.add(name(attr));}catch(Throwable ignored){}
            if(type==1){if(attributes.contains("SectionStart"))type=2;else if(attributes.contains("SectionEnd"))type=3;else if(attributes.contains("Signal"))type=0;}
            row.put("type",type).put("attributes",new JSONArray(attributes));
            // An end marker alone is an approaching camera. The native progress
            // object exists while inside the section, including a mid-section entry.
            if(type==3){
              try{
                Object section=call(safety,"getSectionSpeedCamera");
                if(section!=null){
                  JSONObject state=new JSONObject();row.put("section",state);
                  double remaining=number(call(section,"getRemainSectionDistance-Y4BO_gI"));
                  state.put("remaining_m",Double.isFinite(remaining)?remaining:JSONObject.NULL);
                  try{state.put("average_valid",Boolean.TRUE.equals(call(section,"isValid")));}catch(Throwable ignored){}
                  try{double avg=number(call(section,"getAverageSpeed-CfrO8c4"));if(Double.isFinite(avg)&&avg>=0)state.put("average_kph",avg);}catch(Throwable ignored){}
                  try{state.put("remaining_time_ms",((Number)call(section,"getRemainTimeLimit-qL7Rsds")).longValue());}catch(Throwable ignored){}
                  if(Double.isFinite(remaining)&&remaining>0&&row.optInt("limit")>0){
                    row.put("type",4).put("distance",remaining);
                    if(activeSection==null||remaining<activeSection.optDouble("distance"))activeSection=row;
                  }
                }
              }catch(NoSuchMethodException ignored){}catch(Throwable e){row.put("section_parse_error",e.getClass().getSimpleName()+": "+text(e.getMessage()));}
            }
            if(camera==null||d<camera.optDouble("distance"))camera=row;
          }
        }catch(Throwable e){row.put("parse_error",e.getClass().getSimpleName()+": "+text(e.getMessage()));}
      }
      // Keep TMAP-compatible slots: camera + bump together, or bump as primary without a camera.
      JSONObject primary=activeSection!=null?activeSection:camera!=null?camera:bump!=null?bump:extra;
      if(primary!=null)putSafety(rg,"nSdi",primary);
      if(activeSection!=null){
        rg.put("nSdiBlockType",2).put("nSdiBlockSpeed",activeSection.optInt("limit"))
          .put("nSdiBlockDist",Math.max(1,(int)Math.round(activeSection.getDouble("distance"))));
      }
      if(bump!=null&&primary!=bump)putSafety(rg,"nSdiPlus",bump);
      JSONArray turns=new JSONArray();
      try{for(Object t:list(call(s,"getTurnPointList"))){double d=number(call(t,"distance"));if(d<0)continue;Object info=call(t,"getInfo");turns.put(new JSONObject().put("distance",d).put("type",name(call(info,"getType"))).put("text",text(call(info,"getInstructions"))));if(turns.length()>=2)break;}
        for(int i=0;i<turns.length();i++){JSONObject t=turns.getJSONObject(i);String suffix=i==0?"":"Next";rg.put("nTBTTurnType"+suffix,BridgeMath.turn(t.getString("type"))).put("nTBTDist"+suffix,(int)t.getDouble("distance")).put("szTBTMainText"+suffix,t.getString("text"));}
      }catch(NoSuchMethodException ignored){}catch(Throwable e){error(e);}
      JSONObject signals;
      try{signals=signals(s,a,b,heading);}catch(Throwable e){error(e);signals=emptySignals();}
      JSONObject event=new JSONObject().put("source","naver-map").put("bridge_version",5).put("timestamp_ms",System.currentTimeMillis()).put("simulation",simulation).put("rgdata",rg)
        .put("raw_naver",new JSONObject().put("mode",name(call(s,"getMode"))).put("safety",rawSafety).put("turns",turns));
      latest=event;publish(event);
      sendRoute(s,now);
      publish(new JSONObject().put("source","naver-map").put("timestamp_ms",System.currentTimeMillis()).put("simulation",simulation).put("ssinf",signals));
      android.text.SpannableStringBuilder display=new android.text.SpannableStringBuilder();
      if(bump!=null)display.append("방지턱 ").append(String.valueOf(bump.optInt("distance"))).append(" m");
      CharSequence signalRows=com.carrot.transport.SignalPanel.render(signals,0);
      if(signalRows.length()>0){
        if(display.length()>0)display.append('\n');
        display.append(signalRows);
      }
      panelText=new android.text.SpannedString(display);
      main.post(()->updatePanel());
      if(++packetNumber%10==0)Log.i(TAG,"mode="+name(call(s,"getMode"))+" peers="+peers.size()+" sdi="+rg.optInt("nSdiType")+" signal="+signals.optInt("distance"));
    }catch(Throwable e){panelText="";main.post(()->updatePanel());error(e);}
  }
  private static void putSafety(JSONObject rg,String prefix,JSONObject r)throws Exception{
    rg.put(prefix+"Type",r.getInt("type")).put(prefix+"Dist",(int)Math.round(r.getDouble("distance"))).put(prefix+"SpeedLimit",r.optInt("limit"));
    double targetLat=r.optDouble("lat",Double.NaN),targetLon=r.optDouble("lon",Double.NaN);
    if(validLocation(targetLat,targetLon)){String coordPrefix=prefix.equals("nSdi")?"vpSdiPoint":"vpSdiPlusPoint";rg.put(coordPrefix+"Lat",targetLat).put(coordPrefix+"Lon",targetLon);}
  }
  private static boolean isCamera(int type){return type==0||type==1||type==2||type==3||type==4||type==7||type==8;}
  private static boolean validLocation(double lat,double lon){return Double.isFinite(lat)&&Double.isFinite(lon)&&Math.abs(lat)<=90&&Math.abs(lon)<=180&&(lat!=0||lon!=0);}
  private static void sendRoute(Object s,long now){
    try{if(!name(call(s,"getMode")).equals("RouteGuiding")&&!simulation)return;
      Object route=call(s,"getRoute");if(route==sentRoute&&now-routeAt<15000)return;
      List<?> points=list(call(route,"getPathPoints"));JSONArray coordinates=new JSONArray();
      // Keep both endpoints and sample very large routes to keep each protocol line bounded.
      int stride=Math.max(1,(points.size()+9999)/10000);
      for(int i=0;i<points.size();i+=stride){Object p=points.get(i);coordinates.put(new JSONObject().put("x",lon(p)).put("y",lat(p)).put("valid",true));}
      if(!points.isEmpty()&&(points.size()-1)%stride!=0){Object p=points.get(points.size()-1);coordinates.put(new JSONObject().put("x",lon(p)).put("y",lat(p)).put("valid",true));}
      publish(new JSONObject().put("source","naver-map").put("simulation",simulation).put("timestamp_ms",System.currentTimeMillis()).put("route",coordinates));sentRoute=route;routeAt=now;
    }catch(Throwable e){error(e);}
  }
  private static JSONObject emptySignals()throws Exception{
    JSONObject out=new JSONObject().put("distance",-1).put("lat",0).put("lon",0);String[] dirs={"straight","left","right","uturn","pedestrian","bicycle","bus"};
    for(String dir:dirs)out.put(dir,"UNAVAILABLE").put(dir+"_int",0).put(dir+"_remain_time",0);
    return out;
  }
  private static JSONObject signals(Object s,double a,double b,double heading)throws Exception{
    JSONObject out=emptySignals();String[] dirs={"straight","left","right","uturn","pedestrian","bicycle","bus"};
    Object best=null;double min=Double.MAX_VALUE;
    for(Object signal:list(call(s,"getTrafficSignal"))){double d=number(call(signal,"getDistance-Y4BO_gI"));Object loc=call(call(signal,"getInfo"),"getLocation");
      if(d>=0&&d<min&&d<=1000&&BridgeMath.ahead(a,b,heading,lat(loc),lon(loc))){best=signal;min=d;}}
    if(best==null)return out;
    Object info=call(best,"getInfo"),loc=call(info,"getLocation");JSONArray raw=new JSONArray();long wall=System.currentTimeMillis();boolean valid=false;
    for(Object detail:list(call(best,"getTrafficSignals"))){String dir=name(call(detail,"getType")).toLowerCase(Locale.ROOT).replace("u_turn","uturn");
      if(!Arrays.asList(dirs).contains(dir))continue;
      long expiry=((Number)call(detail,"getOutdateDtMillis")).longValue();
      Object date=call(detail,"getCurrentDt");Class<?> ext=Class.forName(PREFIX+"internal.extensions.DateExtensionsKt");long source=((Number)ext.getMethod("d",date.getClass()).invoke(null,date)).longValue();
      Set<String> states=new HashSet<>();for(Object state:(Iterable<?>)call(detail,"getState"))states.add(name(state));
      int remaining=BridgeMath.remaining(expiry,wall);raw.put(new JSONObject().put("direction",dir).put("states",new JSONArray(states)).put("source_ms",source).put("expiry_ms",expiry));
      if(expiry<=wall||expiry-wall>300000||source>wall+5000||wall-source>300000)continue;
      // A contradictory state set is not a dependable stop/go instruction.
      boolean red=states.contains("RED"),green=states.contains("GREEN"),yellow=states.contains("YELLOW");
      if((red?1:0)+(green?1:0)+(yellow?1:0)!=1)continue;
      int n=red?3:green?6:8;out.put(dir,red?"RED_LIGHT_ON":green?"GREEN_LIGHT_ON":"YELLOW_LIGHT_ON").put(dir+"_int",n).put(dir+"_remain_time",remaining);valid=true;
    }
    out.put("raw_naver_signals",raw);
    if(valid)out.put("distance",(int)Math.round(min)).put("lat",lat(loc)).put("lon",lon(loc));
    return out;
  }
  private static synchronized void touch(String ip){
    if(!ip.matches("(?:[0-9]{1,3}\\.){3}[0-9]{1,3}"))return;
    if(peers.size()>=4&&!peers.containsKey(ip))return;
    Peer peer=peers.get(ip);if(peer==null){Peer created=new Peer(ip);Peer prior=peers.putIfAbsent(ip,created);peer=prior==null?created:prior;}
    peer.seen=SystemClock.elapsedRealtime();
  }
  private static void discoverLoop(){
    while(true){try(DatagramSocket socket=new DatagramSocket(null)){
      socket.setReuseAddress(true);socket.bind(new InetSocketAddress(7705));byte[] buffer=new byte[16384];
      while(true){DatagramPacket packet=new DatagramPacket(buffer,buffer.length);socket.receive(packet);
        try{JSONObject o=new JSONObject(new String(packet.getData(),packet.getOffset(),packet.getLength(),StandardCharsets.UTF_8));if(o.has("ip"))touch(packet.getAddress().getHostAddress());}catch(Exception ignored){}
      }
    }catch(Throwable e){error(e);try{Thread.sleep(3000);}catch(InterruptedException ignored){}}}
  }
  private static void publish(JSONObject packet){String line=com.carrot.transport.NaviAppState.decorate(packet,"naver-map",session!=null&&pointAt>0&&SystemClock.elapsedRealtime()-pointAt<4000).toString();long now=SystemClock.elapsedRealtime();for(Peer peer:peers.values()){
    if(now-peer.seen>30000)continue;if(!peer.queue.offer(line)){peer.queue.poll();peer.queue.offer(line);}}
  }
  private static void sendLoop(Peer peer){
    while(true){
      if(SystemClock.elapsedRealtime()-peer.seen>30000){try{Thread.sleep(1000);}catch(InterruptedException stop){return;}continue;}
      try(Socket socket=new Socket()){
      socket.connect(new InetSocketAddress(peer.ip,7712),1500);socket.setKeepAlive(true);socket.setTcpNoDelay(true);
      try(BufferedWriter w=new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(),StandardCharsets.UTF_8))){
        while(true){String line=peer.queue.poll(10,TimeUnit.SECONDS);if(line==null){if(SystemClock.elapsedRealtime()-peer.seen>30000)break;continue;}
          if(System.currentTimeMillis()-new JSONObject(line).optLong("timestamp_ms",0)>2000)continue;
          w.write(line);w.newLine();w.flush();}
      }
    }catch(Throwable ignored){peer.queue.clear();try{Thread.sleep(1500);}catch(InterruptedException stop){return;}}}
  }
  private static void updatePanel(){
    try{Activity a=activity.get();if(a==null||a.isFinishing())return;
      TextView p=panel.get();if(p==null||p.getContext()!=a){
        if(p!=null&&p.getParent() instanceof ViewGroup)((ViewGroup)p.getParent()).removeView(p);
        p=new TextView(a);p.setTag("kisa-naver-signal");p.setTextSize(16);p.setTextColor(Color.WHITE);p.setPadding(dp(a,10),dp(a,8),dp(a,10),dp(a,8));
        GradientDrawable bg=new GradientDrawable();bg.setColor(0xdd16202c);bg.setCornerRadius(dp(a,10));p.setBackground(bg);p.setClickable(false);p.setFocusable(false);
        FrameLayout content=a.findViewById(android.R.id.content);if(content==null)return;
        FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,ViewGroup.LayoutParams.WRAP_CONTENT,Gravity.TOP|Gravity.START);lp.leftMargin=dp(a,12);lp.topMargin=dp(a,210);
        content.addView(p,lp);panel=new WeakReference<>(p);
      }
      CharSequence text=panelText;boolean appeared=p.getText().length()==0&&text.length()>0;
      p.setText(text);p.setTextColor(Color.WHITE);p.setVisibility(!CompanionMode.background()&&showPanel&&text.length()>0?View.VISIBLE:View.GONE);p.bringToFront();
      if(p.getVisibility()==View.VISIBLE){FrameLayout root=(FrameLayout)p.getParent();p.measure(View.MeasureSpec.makeMeasureSpec(root.getWidth()/2,View.MeasureSpec.AT_MOST),View.MeasureSpec.makeMeasureSpec(root.getHeight()/2,View.MeasureSpec.AT_MOST));
        if(appeared){
          // The native signal can enter the Compose layout on the next frame.
          main.postDelayed(()->{
            if(activity.get()!=a||a.isFinishing())return;
            com.carrot.transport.ComposeRegions.invalidate(root);
            updatePanel();
          },180);
        }
        com.carrot.transport.GuidancePlacement.place(root,p,true);
      }
    }catch(Throwable e){error(e);}
  }
  public static JSONObject health()throws Exception{long now=SystemClock.elapsedRealtime();Object s=session;return new JSONObject().put("version",1).put("package","com.nhn.android.nmap").put("token",CompanionMode.token).put("mode",s==null?"NONE":name(call(s,"getMode"))).put("main_age_ms",NaverHealth.mainAt==0?-1:now-NaverHealth.mainAt).put("position_age_ms",pointAt==0?-1:now-pointAt);}
  private static int dp(Context c,int v){return (int)(v*c.getResources().getDisplayMetrics().density+0.5f);}
}
