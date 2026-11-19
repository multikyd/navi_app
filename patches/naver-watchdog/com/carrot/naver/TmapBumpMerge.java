package com.carrot.naver;
import android.app.Application;import android.os.SystemClock;import android.util.Log;import org.json.*;import java.io.*;import java.net.*;import java.lang.reflect.*;import java.util.*;import java.util.concurrent.*;
/** Adds original Tmap bumps before native Naver safety UI/audio processing. */
public final class TmapBumpMerge {
 static Application app;static volatile TmapBumpPolicy.Snapshot source;static TmapBumpPolicy policy=new TmapBumpPolicy();static Object lastSession,lastRoute;static List<TmapBumpPolicy.Point> path=Collections.emptyList();static long lastDiagnostic,lastError;static volatile String status="waiting_for_tmap";
 public static synchronized void init(Application a){if(app!=null)return;app=a;ScheduledExecutorService timer=Executors.newSingleThreadScheduledExecutor(r->{Thread t=new Thread(r,"TmapBumpReceive");t.setDaemon(true);return t;});timer.scheduleWithFixedDelay(TmapBumpMerge::poll,0,1000,TimeUnit.MILLISECONDS);}
 static void poll(){try(Socket socket=new Socket()){
  socket.connect(new InetSocketAddress("127.0.0.1",18317),400);socket.setSoTimeout(400);ByteArrayOutputStream bytes=new ByteArrayOutputStream();int c;InputStream in=socket.getInputStream();while((c=in.read())!=-1&&c!='\n'){if(bytes.size()>32768)throw new IOException("Native bump packet too large");bytes.write(c);}JSONObject row=new JSONObject(bytes.toString("UTF-8"));
  if(!"tmap-native".equals(row.optString("source"))||row.optInt("version")!=1)throw new IOException("Unexpected source");
  TmapBumpPolicy.Snapshot s=new TmapBumpPolicy.Snapshot();s.elapsed=row.getLong("elapsed_ms");String mode=row.getString("mode");s.active=mode.equals("REAL_DRIVE")||mode.equals("SAFE_DRIVE")||mode.equals("SIMULATION_DRIVE");s.simulation=mode.equals("SIMULATION_DRIVE");s.origin=new TmapBumpPolicy.Point(row.getDouble("lat"),row.getDouble("lon"));s.heading=row.getDouble("heading");List<TmapBumpPolicy.Bump> bumps=new ArrayList<>();JSONArray rows=row.getJSONArray("bumps");
  for(int i=0;i<Math.min(32,rows.length());i++){JSONObject b=rows.getJSONObject(i);double lat=b.getDouble("lat"),lon=b.getDouble("lon");if(lat<33||lat>39||lon<124||lon>132||!"tmap-native".equals(b.optString("source")))continue;bumps.add(new TmapBumpPolicy.Bump(b.optString("id"),lat,lon,b.getDouble("distance")));}s.bumps=bumps;source=s;
 }catch(Exception e){source=null;}}
 static Object call(Object o,String name,Object... args)throws Exception{Class<?> c=o instanceof Class?(Class<?>)o:o.getClass();for(Method m:c.getMethods())if(m.getName().equals(name)&&m.getParameterTypes().length==args.length){m.setAccessible(true);return m.invoke(o instanceof Class?null:o,args);}throw new NoSuchMethodException(c.getName()+"."+name);}
 static double num(Object o){return ((Number)o).doubleValue();}
 static TmapBumpPolicy.Point location(Object loc)throws Exception{return new TmapBumpPolicy.Point(loc.getClass().getField("latitude").getDouble(loc),loc.getClass().getField("longitude").getDouble(loc));}
 static List<?> list(Object o){return o instanceof List?(List<?>)o:Collections.emptyList();}
 static boolean bump(Object o)throws Exception{return String.valueOf(call(o,"getCode")).equals("SpeedBump");}
 static boolean duplicate(TmapBumpPolicy.Point p,List<?> values,boolean guidance)throws Exception{for(Object value:values){Object info=guidance?call(value,"getInfo"):value;if(!String.valueOf(call(info,"code")).equals("SpeedBump"))continue;if(TmapBumpPolicy.distance(p,location(call(info,"getLocation")))<15)return true;}return false;}
 static Object construct(Class<?> c,int count,Object... args)throws Exception{for(Constructor<?> ctor:c.getDeclaredConstructors())if(ctor.getParameterTypes().length==count){ctor.setAccessible(true);return ctor.newInstance(args);}throw new NoSuchMethodException(c.getName()+" constructor "+count);}
 static Object nativeSafety(ClassLoader cl,Object point,Object context,TmapBumpPolicy.Choice choice)throws Exception{
  Object code=cl.loadClass("com.naver.maps.navi.v2.shared.api.route.constants.SafetyCode").getField("SpeedBump").get(null);Class<?> speed=cl.loadClass("com.naver.maps.navi.v2.shared.api.geometry.KmPerHour");Object boxed=call(speed,"box-impl",num(call(point,"getLinkMaxSpeed")));Object loc=cl.loadClass("com.naver.maps.geometry.LatLng").getConstructor(double.class,double.class).newInstance(choice.bump.location.lat,choice.bump.location.lon);Class<?> info=cl.loadClass("com.naver.maps.navi.direction.internal.route.model.InternalRouteSafety");double remaining=num(call(point,"getGoalDistance-Y4BO_gI"));
  Object route=construct(info,15,null,choice.bump.id.hashCode(),code,Collections.emptySet(),null,null,null,remaining-choice.distance,Collections.emptyList(),null,Collections.emptyList(),Collections.emptySet(),call(point,"getRoadKind"),loc,boxed);
  return construct(cl.loadClass("com.naver.maps.navi.v2.internal.guidance.model.InternalGuidanceSafety"),10,route,choice.distance,num(call(point,"getSpeedKmPerHour")),null,false,code,Collections.emptySet(),call(context,"x"),call(context,"n"),null);
 }
 /** No socket I/O here. Original list retained on any failure. Native route is read-only. */
 public static synchronized List<?> merge(Object session,List<?> original){List<double[]> markers=new ArrayList<>();try{
  Object point=call(session,"getGuidePoint");if(point==null)return original;ClassLoader cl=session.getClass().getClassLoader();Object context=call(session,"getSessionContext"),setting=call(context,"n");Object code=cl.loadClass("com.naver.maps.navi.v2.shared.api.route.constants.SafetyCode").getField("SpeedBump").get(null);
  if(!Boolean.TRUE.equals(call(cl.loadClass("com.naver.maps.navi.v2.internal.route.extensions.SafetyCodeExtensionsKt"),"b",code,setting)))return original;
  boolean simulation=String.valueOf(call(session,"getMode")).equals("SimulGuiding");Object route=call(session,"getRoute");
  if(lastSession!=session||lastRoute!=route){if(lastSession!=session)policy=new TmapBumpPolicy();lastSession=session;lastRoute=route;path=Collections.emptyList();if(!String.valueOf(call(session,"getMode")).equals("SafeGuiding")){List<TmapBumpPolicy.Point> coordinates=new ArrayList<>();for(Object p:list(call(route,"getPathPoints")))coordinates.add(location(p));path=coordinates;}}
  if(!String.valueOf(call(session,"getMode")).equals("SafeGuiding")&&path.size()<2)return original;
  TmapBumpPolicy.Point here=location(call(point,"getLocation"));long now=SystemClock.elapsedRealtime();List<TmapBumpPolicy.Choice> choices=policy.select(source,now,here,num(call(point,"getHeading")),simulation,path);
  List<Object> all=new ArrayList<>(original),added=new ArrayList<>();Object bumpSetting=call(cl.loadClass("com.naver.maps.navi.v2.internal.guidance.controller.setting.SpeedBumpSetting").getField("d").get(null),"a",num(call(point,"getLinkMaxSpeed")));
  for(TmapBumpPolicy.Choice choice:choices){if(!Boolean.TRUE.equals(call(bumpSetting,"l",choice.distance)))continue;if(duplicate(choice.bump.location,original,true)||duplicate(choice.bump.location,list(call(route,"getSafeties")),false))continue;Object nativeBump=nativeSafety(cl,point,context,choice);added.add(nativeBump);all.add(nativeBump);}
  if(!added.isEmpty()){
   List<Object> bumps=new ArrayList<>();for(Object o:all)if(bump(o))bumps.add(o);bumps.sort((a,b)->{try{return Double.compare(num(call(a,"distance")),num(call(b,"distance")));}catch(Exception e){return 0;}});
   Object generator=cl.loadClass("com.naver.maps.navi.v2.internal.guidance.controller.general.generator.SpeedBumpGuidanceGenerator").getConstructor().newInstance();List<Object> selected=new ArrayList<>();for(Object o:bumps){if(selected.isEmpty()||(selected.size()<2&&Boolean.TRUE.equals(call(generator,"f",call(selected.get(selected.size()-1),"getInfo"),call(o,"getInfo")))))selected.add(o);}all.removeIf(o->bumps.contains(o)&&!selected.contains(o));
  }
  for(Object item:added){
   if(!all.contains(item))continue;
   TmapBumpPolicy.Point p=location(call(call(item,"getInfo"),"getLocation"));
   markers.add(new double[]{p.lat,p.lon});
  }
  status=source==null?"tmap_unavailable":choices.isEmpty()?"no_matching_tmap_bump":added.isEmpty()?"native_duplicate_or_out_of_range":"merged";
  if(app!=null&&now-lastDiagnostic>=5000){lastDiagnostic=now;JSONObject log=new JSONObject().put("timestamp_ms",System.currentTimeMillis()).put("status",status).put("tmap_candidates",source==null?0:source.bumps.size()).put("matching",choices.size()).put("added",added.size()).put("native_count",original.size()).put("result_count",all.size());File dir=new File(app.getExternalFilesDir(null),"tmap_bump_merge");dir.mkdirs();java.nio.file.Files.write(new File(dir,"status.json").toPath(),log.toString(2).getBytes("UTF-8"));Log.i("TmapBumpMerge",log.toString());}
  return added.isEmpty()?original:all;
 }catch(Throwable e){long now=SystemClock.elapsedRealtime();if(now-lastError>10000){lastError=now;Log.w("TmapBumpMerge","Native list retained after merge error",e);}markers.clear();return original;}finally{MergedBumpMarkers.publish(markers);}}
 private TmapBumpMerge(){}
}
