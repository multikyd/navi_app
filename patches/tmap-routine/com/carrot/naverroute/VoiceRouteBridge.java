package com.carrot.naverroute;
import android.content.Context;import android.os.SystemClock;import android.util.Log;import java.lang.reflect.*;import java.util.*;import org.json.*;
/** Defers confirmed Aria route callbacks; never changes speech recognition or native route data. */
public final class VoiceRouteBridge {
 interface Estimator {RouteRequest.Ticket fetch(Context c,Object option,Object original,RouteRequest.Callback callback);}
 static final Estimator DEFAULT_ESTIMATOR=RouteRequest::fetchEstimate;static Estimator estimator=DEFAULT_ESTIMATOR;
 static final ThreadLocal<Object> replaying=new ThreadLocal<>();
 static final Map<Object,Boolean> seen=Collections.synchronizedMap(new WeakHashMap<>());
 static volatile Job pending;static final java.util.concurrent.atomic.AtomicLong sequence=new java.util.concurrent.atomic.AtomicLong();static final long DEADLINE_MS=8000;
 static final class Job {final Object callback,route;final Context context;final long id,started;final boolean diagnostic;RouteRequest.Ticket ticket;boolean done;Job(Object cb,Object r,Context c,long generation){callback=cb;route=r;context=c;id=generation;diagnostic=estimator!=DEFAULT_ESTIMATOR;started=SystemClock.elapsedRealtime();}}
 public static boolean intercept(Object callback,Object route){
  if(replaying.get()==callback)return false;
  if(callback==null||route==null)return false;
  try{Object handler=RouteRequest.field(callback.getClass(),"a").get(callback);Context c=(Context)RouteRequest.field(handler.getClass(),"a").get(handler);if(c==null)return false;
   synchronized(seen){if(seen.containsKey(callback))return true;seen.put(callback,false);}
   long generation=sequence.incrementAndGet();RouteRequest.MAIN.post(()->begin(callback,route,c.getApplicationContext(),generation));return true;
  }catch(Exception e){Log.w("AriaRoute","Route context unavailable; native callback retained",e);return false;}
 }
 static void begin(Object callback,Object route,Context context,long generation){
  if(Boolean.TRUE.equals(seen.get(callback)))return;if(generation!=sequence.get()){seen.put(callback,true);notifyCancelled(callback);return;}
  cancelNow("superseded_voice_destination",true);NaverRuntime.init(context);Job job=new Job(callback,route,context,generation);pending=job;
  try{Object option=RouteRequest.get(route,"getRouteOption");RouteDiagnostics.event("voice_route_compare_started","id="+job.id+" diagnostic="+job.diagnostic+" native_seconds="+PreviewBridge.Session.seconds(route)+" vias="+((List<?>)RouteRequest.get(option,"getWayPoints")).size());
   job.ticket=estimator.fetch(context,option,route,new RouteRequest.Callback(){public void success(Object ignored,JSONObject response){RouteRequest.MAIN.post(()->{try{long naver=(long)Math.ceil(response.getJSONArray("routes").getJSONObject(0).getJSONObject("summary").getDouble("duration")/1000);finish(job,naver,"estimate_ready");}catch(Exception e){finish(job,0,"invalid_estimate");}});}public void failure(String reason){RouteRequest.MAIN.post(()->finish(job,0,"estimate_failed"));}});
   RouteRequest.MAIN.postDelayed(()->finish(job,0,"estimate_timeout"),DEADLINE_MS);
  }catch(Exception e){RouteDiagnostics.event("voice_route_compare_error",e.toString());finish(job,0,"comparison_error");}
 }
 static void finish(Job job,long naverSeconds,String reason){
  if(pending!=job||job.done||job.id!=sequence.get())return;job.done=true;pending=null;seen.put(job.callback,true);if(job.ticket!=null)job.ticket.cancel();
  try{long nativeSeconds=PreviewBridge.Session.seconds(job.route);boolean naver=RouteChoicePolicy.naver(nativeSeconds,naverSeconds);DualNavigation.select(job.context,job.route,naver);
   RouteDiagnostics.event("voice_route_selected","id="+job.id+" diagnostic="+job.diagnostic+" tmap_seconds="+nativeSeconds+" naver_seconds="+naverSeconds+" owner="+(naver?"naver":"tmap")+" reason="+reason+" elapsed_ms="+(SystemClock.elapsedRealtime()-job.started));
   replaying.set(job.callback);try{Method complete=null;for(Method candidate:job.callback.getClass().getMethods())if(candidate.getName().equals("onComplete")&&candidate.getParameterTypes().length==1&&candidate.getParameterTypes()[0].isInstance(job.route)){complete=candidate;break;}if(complete==null)throw new NoSuchMethodException("onComplete route continuation");complete.setAccessible(true);complete.invoke(job.callback,job.route);}finally{replaying.remove();}
  }catch(Exception e){RouteDiagnostics.event("voice_route_resume_failed",e.toString());Log.e("AriaRoute","Native voice continuation failed",e);if(DualNavigation.armed==job.route)DualNavigation.stop();notifyCancelled(job.callback);}
 }
 static void notifyCancelled(Object callback){try{Method method=callback.getClass().getMethod("onCancelAction");method.setAccessible(true);method.invoke(callback);}catch(Exception e){Log.w("AriaRoute","Voice cancellation failed",e);}}
 static void cancelNow(String reason,boolean notify){Job job=pending;if(job==null)return;pending=null;job.done=true;seen.put(job.callback,true);if(job.ticket!=null)job.ticket.cancel();RouteDiagnostics.event("voice_route_cancelled","id="+job.id+" diagnostic="+job.diagnostic+" reason="+reason);if(notify)notifyCancelled(job.callback);}
 public static void cancel(String reason){long generation=sequence.incrementAndGet();RouteRequest.MAIN.post(()->{if(pending!=null&&pending.id<generation)cancelNow(reason,true);});}
 public static void cancelCallback(Object callback){seen.put(callback,true);RouteRequest.MAIN.post(()->{if(pending!=null&&pending.callback==callback)cancelNow("native_voice_cancelled",false);});}
 private VoiceRouteBridge(){}
}
