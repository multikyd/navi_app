package com.carrot.tmaprouteprobe;
import android.app.*;
import android.content.*;
import android.os.*;
import android.view.*;
import java.lang.reflect.*;
import java.util.*;
public final class KisaComposeScanRunner extends Instrumentation {
  String diagnostic="";
  void inspect(View v){
    if(v.getClass().getName().equals("androidx.compose.ui.platform.AndroidComposeView"))try{
      Object owner=v.getClass().getMethod("getSemanticsOwner").invoke(v);
      Object node=owner.getClass().getMethod("d").invoke(owner);
      Object config=node.getClass().getMethod("r").invoke(node);
      StringBuilder s=new StringBuilder(config.getClass().getName()+" iterable="+(config instanceof Iterable<?>));
      for(Field f:config.getClass().getDeclaredFields())s.append(" field:").append(f.getName()).append(":").append(f.getType().getName());
      for(Method m:config.getClass().getMethods())if(m.getDeclaringClass()!=Object.class)s.append(" method:").append(m.getName()).append(":").append(m.getParameterTypes().length).append(":").append(m.getReturnType().getName());
      if(config instanceof Iterable<?>)for(Object entry:(Iterable<?>)config){if(entry instanceof Map.Entry<?,?>){Object key=((Map.Entry<?,?>)entry).getKey();s.append(" keyclass:").append(key.getClass().getName());for(Field f:key.getClass().getDeclaredFields())s.append(" keyfield:").append(f.getName()).append(":").append(f.getType().getName());}break;}
      diagnostic=s.toString();
    }catch(Throwable e){diagnostic="inspect error "+e.getClass().getName();}
    if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++)inspect(((ViewGroup)v).getChildAt(i));
  }

  public void onCreate(Bundle b){super.onCreate(b);start();}
  public void onStart(){Bundle out=new Bundle();try{
    Context c=getTargetContext();ActivityMonitor monitor=addMonitor("com.naver.map.MainActivity",null,false);
    startActivitySync(c.getPackageManager().getLaunchIntentForPackage(c.getPackageName()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    Activity a=waitForMonitorWithTimeout(monitor,10000);
    if(a==null)a=monitor.getLastActivity();if(a==null)throw new Exception("Main activity not available");
    final Activity screen=a;Class<?> regions=c.getClassLoader().loadClass("com.carrot.transport.ComposeRegions");
    Method get=regions.getMethod("get",View.class);int[] max={0},described={0};Throwable[] failure={null};
    for(int n=0;n<20;n++){
      Thread.sleep(1000);runOnMainSync(()->{try{
        List<?> rows=(List<?>)get.invoke(null,screen.getWindow().getDecorView());max[0]=Math.max(max[0],rows.size());
        for(Object row:rows){String s=(String)row.getClass().getField("description").get(row);if(!s.isEmpty())described[0]++;}
      }catch(Throwable e){failure[0]=e;}});
      if(failure[0]!=null)throw new Exception(failure[0]);
    }
    if(max[0]==0||described[0]==0){runOnMainSync(()->inspect(screen.getWindow().getDecorView()));throw new Exception("No labelled Compose regions scanned; "+diagnostic);}
    out.putString("report","PASS: 20 real-screen scans; max regions="+max[0]+", labelled samples="+described[0]+"; no fake navigation or GPS");
  }catch(Throwable e){out.putString("error",e.toString());}finish(Activity.RESULT_OK,out);}
}
