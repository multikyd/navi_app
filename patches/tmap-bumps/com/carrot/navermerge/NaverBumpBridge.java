package com.carrot.navermerge;
import android.app.Application;import android.content.Context;import android.os.SystemClock;import android.util.Log;
import com.skt.tmap.engine.navigation.data.RGData;
import org.json.JSONObject;import java.io.*;import java.nio.charset.StandardCharsets;import java.util.*;
public final class NaverBumpBridge {
 static File diagnostic,trace;static long lastStatus,lastMoving;static double heading=Double.NaN,movingLat,movingLon,lastLat,lastLon;static RoadMatcher.Selection previous;
 static volatile MapBump mapBump;
 static final class MapBump {
  final double lat,lon,originLat,originLon,bearing;
  final long at;
  MapBump(RoadMatcher.Selection choice,double lat,double lon,long at){
   this.lat=choice.bump.lat;this.lon=choice.bump.lon;originLat=lat;originLon=lon;bearing=choice.bearing;this.at=at;
  }
 }
 public static Object mapSupplement(RGData r,Object empty){
  MapBump bump=mapBump;
  if(bump==null||r==null||empty==null||SystemClock.elapsedRealtime()-bump.at>2000
      ||TileReader.distance(r.vpPosPointLat,r.vpPosPointLon,bump.originLat,bump.originLon)>35)return empty;
  double dx=(bump.lon-r.vpPosPointLon)*Math.cos(Math.toRadians(r.vpPosPointLat));
  double dy=bump.lat-r.vpPosPointLat;
  double angle=Math.toRadians(bump.bearing);
  if(dx*Math.sin(angle)+dy*Math.cos(angle)<=0)return empty;
  try{
   Class<?> point=empty.getClass().getClassLoader().loadClass("com.skt.tmap.engine.navigation.route.data.MapPoint");
   Object at=point.getConstructor(double.class,double.class).newInstance(bump.lon,bump.lat);
   return empty.getClass().getConstructor(int.class,point,int.class).newInstance(22,at,0);
  }catch(ReflectiveOperationException|RuntimeException e){return empty;}
 }
 static final Map<String,Long> passed=new HashMap<>();
 public static void init(Context c){try{if(android.os.Build.VERSION.SDK_INT>=28&&!Application.getProcessName().equals(c.getPackageName()))return;SafetyRepository.init(c);diagnostic=new File(c.getFilesDir(),"naver_bump_status.json");trace=new File(c.getExternalFilesDir(null),"naver_bump_trace.jsonl");Log.i("NaverBumpMerge","initialized; native plus Naver merge");}catch(Throwable e){Log.w("NaverBumpMerge","init failed; native retained");}}
 public static synchronized void apply(RGData r){if(r==null)return;try{
  BumpMerger.restore(r);double lat=r.vpPosPointLat,lon=r.vpPosPointLon;if(!BumpMerger.valid(lat,lon)){mapBump=null;return;}long now=SystemClock.elapsedRealtime();
  if(lastLat!=0&&TileReader.distance(lat,lon,lastLat,lastLon)>100){passed.clear();previous=null;heading=Double.NaN;}
  lastLat=lat;lastLon=lon;SafetyRepository.ensure(lat,lon);
  if(r.nPosSpeed>=2&&r.nPosAngle>=0&&r.nPosAngle<=360){heading=r.nPosAngle%360;lastMoving=now;movingLat=lat;movingLon=lon;}
  if(now-lastMoving>120000||TileReader.distance(lat,lon,movingLat,movingLon)>20)heading=Double.NaN;
  Iterator<Map.Entry<String,Long>> it=passed.entrySet().iterator();while(it.hasNext())if(now-it.next().getValue()>120000)it.remove();
  if(previous!=null&&TileReader.distance(lat,lon,previous.bump.lat,previous.bump.lon)<35){double dx=(lon-previous.bump.lon)*111195*Math.cos(Math.toRadians(lat)),dy=(lat-previous.bump.lat)*111195;double forward=dx*Math.sin(Math.toRadians(previous.bearing))+dy*Math.cos(Math.toRadians(previous.bearing));if(forward>2)passed.put(previous.key,now);}
  SafetyRepository.Snapshot data=SafetyRepository.current();RoadMatcher.Selection choice=null;
  if(data!=null&&now-data.elapsed<6*3600000L&&TileReader.distance(lat,lon,data.lat,data.lon)<280&&Double.isFinite(heading))choice=RoadMatcher.select(data.roads,data.bumps,lat,lon,heading,passed.keySet());
  String source=BumpMerger.merge(r,choice);if(choice!=null)previous=choice;
  mapBump="naver".equals(source)?new MapBump(choice,lat,lon,now):null;
  if(now-lastStatus>=5000){lastStatus=now;String reason=!Double.isFinite(heading)?"heading_unavailable":data==null?"tiles_not_ready":now-data.elapsed>=6*3600000L?"tiles_stale":TileReader.distance(lat,lon,data.lat,data.lon)>=280?"outside_tile_snapshot":choice==null?"no_forward_road_match":source.equals("naver")?"naver_selected":"native_preferred_or_protected";
   JSONObject status=new JSONObject().put("timestamp_ms",System.currentTimeMillis()).put("reason",reason).put("last_lookup_error",SafetyRepository.lastError).put("repository",SafetyRepository.status).put("source",source).put("lat",lat).put("lon",lon).put("heading_valid",Double.isFinite(heading)).put("speed",r.nPosSpeed).put("roads",data==null?0:data.roads.size()).put("bumps",data==null?0:data.bumps.size()).put("passed_count",passed.size()).put("plus_type",r.nSdiPlusType).put("plus_distance",r.nSdiPlusDist);
   if(choice!=null)status.put("naver_id",RoadMatcher.id(choice.bump)).put("road_id",choice.roadId).put("naver_distance",Math.round(choice.distance));
   Log.i("NaverBumpMerge",status.toString());if(trace!=null)try{if(trace.length()>2*1024*1024){File old=new File(trace.getParentFile(),"naver_bump_trace.previous.jsonl");if(old.exists())old.delete();trace.renameTo(old);}try(FileOutputStream log=new FileOutputStream(trace,true)){log.write((status.toString()+"\n").getBytes(StandardCharsets.UTF_8));}}catch(Exception ignored){}if(diagnostic!=null)try(FileOutputStream out=new FileOutputStream(diagnostic)){out.write(status.toString(2).getBytes(StandardCharsets.UTF_8));}
  }
 }catch(Throwable e){mapBump=null;Log.w("NaverBumpMerge","merge failed; native retained",e);}}
}
