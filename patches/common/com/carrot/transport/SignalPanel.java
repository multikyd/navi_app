package com.carrot.transport;
import android.app.*;import android.os.*;import android.graphics.Color;import android.graphics.drawable.GradientDrawable;import android.text.*;import android.text.style.ForegroundColorSpan;import android.view.*;import android.widget.*;import java.lang.ref.WeakReference;import org.json.*;
/** Per-movement times. Never predicts a subsequent phase or copies another movement's timer. */
public final class SignalPanel {
 static final Handler MAIN=new Handler(Looper.getMainLooper());static volatile Sample detailed,basic;static volatile BumpSample bump;static boolean refreshPending;static WeakReference<Activity> activity=new WeakReference<>(null);static WeakReference<TextView> view=new WeakReference<>(null);static boolean started;
 static final class Sample {
  final JSONObject data;
  final long received;
  Sample(JSONObject data,long received){this.data=data;this.received=received;}
 }
 static JSONObject basicSignal(JSONObject value)throws JSONException{
  JSONObject out=new JSONObject().put("distance",value.optInt("distance",-1));
  String[] movements={"straight","left","right","uturn"};
  String[] keys={"green","left","right","uturn"};
  for(int i=0;i<keys.length;i++){
   String key=keys[i];
   boolean green=value.optBoolean(key+"LightOn");
   boolean red=i==0&&!green&&value.optBoolean("redLightOn");
   if(green||red){
    out.put(movements[i],green?"GREEN_LIGHT_ON":"RED_LIGHT_ON");
    out.put(movements[i]+"_remain_time",value.optInt((red?"red":key)+"LightRemainTime"));
   }
  }
  return out;
 }
 static final class BumpSample {
  final int meters;
  final long at;
  BumpSample(int meters,long at){this.meters=meters;this.at=at;}
 }
 static int nearestBump(JSONObject route){
  int nearest=Integer.MAX_VALUE;
  for(String slot:new String[]{"nSdi","nSdiPlus"}){
   int meters=route.optInt(slot+"Dist",0);
   if(route.optInt(slot+"Type",-1)==22&&meters>0)nearest=Math.min(nearest,meters);
  }
  return nearest==Integer.MAX_VALUE?0:nearest;
 }
 static CharSequence renderPanel(long now){
  CharSequence signals=renderLatest(now);
  BumpSample sample=bump;
  if(sample==null||sample.meters<=0||now-sample.at>3000)return signals;
  SpannableStringBuilder text=new SpannableStringBuilder("방지턱 "+sample.meters+" m");
  if(signals.length()>0)text.append('\n').append(signals);
  return text;
 }
 public static synchronized void observe(JSONObject packet,String source){
  if(!"tmap".equals(source))return;
  JSONObject detail=packet.optJSONObject("ssinf"),legacy=packet.optJSONObject("sinf");
  JSONObject route=packet.optJSONObject("rgdata");
  if(detail==null&&legacy==null&&route==null)return;
  long now=SystemClock.elapsedRealtime();
  if(route!=null){
   int meters=nearestBump(route);
   BumpSample previous=bump;
   bump=meters>0?new BumpSample(meters,now):null;
   if(detail==null&&legacy==null&&(previous==null?meters==0:previous.meters==meters))return;
  }
  try{
   if(detail!=null){
    detailed=new Sample(new JSONObject(detail.toString()),now);
    if(detail.optInt("distance",-1)<0)basic=null;
   }
   if(legacy!=null)basic=new Sample(basicSignal(legacy),now);
   if(!refreshPending){
    refreshPending=true;
    MAIN.post(()->{
     synchronized(SignalPanel.class){refreshPending=false;}
     refresh();
    });
   }
  }catch(JSONException ignored){}
 }
 static CharSequence renderLatest(long now){
  Sample sample=detailed;
  CharSequence text=sample==null?"":render(sample.data,now-sample.received);
  if(text.length()!=0)return text;
  sample=basic;
  return sample==null?"":render(sample.data,now-sample.received);
 }
 public static void resumed(Activity a){if(!a.getPackageName().equals("com.skt.tmap.ku"))return;activity=new WeakReference<>(a);if(a.getClass().getName().equals("com.skt.tmap.activity.TmapNaviActivity")){View root=a.findViewById(android.R.id.content);if(root!=null&&root.getTag(0x7f0a1274)==null){root.setTag(0x7f0a1274,Boolean.TRUE);root.getViewTreeObserver().addOnPreDrawListener(()->{stockCountdown(a);return true;});}}if(!started){started=true;MAIN.post(TICK);}}
 static final java.util.Map<TextView,Integer> hiddenCountdown=new java.util.WeakHashMap<>();
 /** Restore stock's short-countdown hiding only on the original remain_time label. */
 static void stockCountdown(Activity a){int id=a.getResources().getIdentifier("remain_time","id",a.getPackageName());View found=id==0?null:a.findViewById(id);if(!(found instanceof TextView))return;TextView time=(TextView)found;int seconds=0;try{seconds=Integer.parseInt(time.getText().toString().replaceAll("[^0-9]",""));}catch(Exception ignored){}if(seconds>0&&seconds<=5){if(!hiddenCountdown.containsKey(time))hiddenCountdown.put(time,time.getVisibility());if(time.getVisibility()==View.VISIBLE)time.setVisibility(View.INVISIBLE);}else{Integer previous=hiddenCountdown.remove(time);if(previous!=null&&time.getVisibility()==View.INVISIBLE)time.setVisibility(previous);}}
 public static void paused(Activity a){if(activity.get()==a){activity.clear();TextView p=view.get();if(p!=null)p.setVisibility(View.GONE);}}
 static int dp(Activity a,int n){return Math.round(a.getResources().getDisplayMetrics().density*n);}
 static boolean companion(Activity a){try{return Boolean.TRUE.equals(a.getClassLoader().loadClass("com.carrot.naverroute.DualNavigation").getMethod("recordOnly").invoke(null));}catch(Exception e){return false;}}
 public static CharSequence render(JSONObject data,long age){SpannableStringBuilder text=new SpannableStringBuilder();if(data==null||age>4000||data.optInt("distance",-1)<0)return text;int distance=data.optInt("distance");String[] keys={"straight","left","right","uturn"},arrows={"↑","←","→","↶"};for(int i=0;i<keys.length;i++){String state=data.optString(keys[i]);int color=state.equals("RED_LIGHT_ON")?0xffff6961:state.equals("GREEN_LIGHT_ON")?0xff42df85:state.equals("YELLOW_LIGHT_ON")?0xffffcb45:0;if(color==0)continue;int provided=data.optInt(keys[i]+"_remain_time",0);int remain=provided-(int)(Math.max(0,age)/1000);if(provided>0&&remain<=0)continue;if(text.length()==0)text.append("신호 ").append(String.valueOf(distance)).append("m");text.append('\n');int start=text.length();text.append(arrows[i]).append(" ●");if(remain>0)text.append(" ").append(String.valueOf(remain)).append("초");text.setSpan(new ForegroundColorSpan(color),start,text.length(),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);}return text;}
 static void update(){Activity a=activity.get();TextView p=view.get();if(a==null||a.isFinishing()||a.isDestroyed()||!a.getClass().getName().equals("com.skt.tmap.activity.TmapNaviActivity")||companion(a)){if(p!=null)p.setVisibility(View.GONE);return;}FrameLayout root=a.findViewById(android.R.id.content);if(root==null)return;CharSequence text=renderPanel(SystemClock.elapsedRealtime());if(text.length()==0){if(p!=null)p.setVisibility(View.GONE);return;}if(p==null||p.getContext()!=a){if(p!=null&&p.getParent() instanceof ViewGroup)((ViewGroup)p.getParent()).removeView(p);p=new TextView(a);p.setTag("kisa-direction-signal");p.setTextColor(Color.WHITE);p.setTextSize(16);p.setPadding(dp(a,10),dp(a,8),dp(a,10),dp(a,8));p.setIncludeFontPadding(false);GradientDrawable bg=new GradientDrawable();bg.setColor(0xe616202c);bg.setCornerRadius(dp(a,10));p.setBackground(bg);root.addView(p,new FrameLayout.LayoutParams(-2,-2));view=new WeakReference<>(p);}p.setText(text);p.setVisibility(GuidanceSwitch.search(root)?View.GONE:View.VISIBLE);if(p.getVisibility()==View.VISIBLE){p.measure(View.MeasureSpec.makeMeasureSpec(root.getWidth()/2,View.MeasureSpec.AT_MOST),View.MeasureSpec.makeMeasureSpec(root.getHeight()/2,View.MeasureSpec.AT_MOST));GuidancePlacement.place(root,p,true);p.bringToFront();}}
 static void refresh(){
  try{update();}catch(Exception e){android.util.Log.w("KisaSignalPanel","update failed",e);}
 }
 static final Runnable TICK=new Runnable(){public void run(){refresh();MAIN.postDelayed(this,500);}};
 private SignalPanel(){}
}
