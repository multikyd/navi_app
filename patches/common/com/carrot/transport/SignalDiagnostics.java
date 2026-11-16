package com.carrot.transport;
import android.content.Context;import android.os.SystemClock;import org.json.*;import java.io.*;import java.nio.charset.StandardCharsets;import java.util.concurrent.*;
/** Bounded local evidence of original vs locally updated signals; no signal synthesis. */
public final class SignalDiagnostics {
 static Context context;static String source;static long last;static String status="";
 static final ThreadPoolExecutor worker=new ThreadPoolExecutor(1,1,0,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(4),r->{Thread t=new Thread(r,"NaviSignalTrace");t.setDaemon(true);return t;},new ThreadPoolExecutor.DiscardPolicy());
 public static void init(Context c,String id){context=c.getApplicationContext();source=id;}
 public static synchronized void observe(JSONObject packet){if(context==null)return;JSONObject signal=packet.optJSONObject("ssinf");if(signal==null)signal=packet.optJSONObject("sinf");JSONObject update=packet.optJSONObject("signal_update");if(signal==null&&update==null)return;
  long now=SystemClock.elapsedRealtime();String state=update==null?"source_event":update.optString("status","local_update");if(now-last<2000&&state.equals(status))return;last=now;status=state;
  try{JSONObject event=new JSONObject().put("timestamp_ms",System.currentTimeMillis()).put("source",source).put("event",state);if(update!=null)event.put("signal_update",new JSONObject(update.toString()));if(signal!=null){JSONObject values=new JSONObject();for(String key:new String[]{"distance","straight","straight_remain_time","left","left_remain_time","right","right_remain_time","uturn","uturn_remain_time"})if(signal.has(key))values.put(key,signal.opt(key));event.put("signal",values);}final byte[] bytes=(event.toString()+"\n").getBytes(StandardCharsets.UTF_8);worker.execute(()->write(bytes));}catch(Exception ignored){}
 }
 static void write(byte[] bytes){try{File root=new File(context.getExternalFilesDir(null),"navi_signal_trace");if(!root.isDirectory()&&!root.mkdirs())return;File current=new File(root,"signals-0.jsonl");if(current.length()>2*1024*1024){File old=new File(root,"signals-1.jsonl");if(old.exists())old.delete();current.renameTo(old);}try(OutputStream out=new FileOutputStream(current,true)){out.write(bytes);}}catch(Exception ignored){}}
}
