package com.carrot.naverroute;

import android.app.Activity;
import android.os.SystemClock;
import android.view.*;
import android.widget.*;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import java.util.*;

/** Explicit alternative to Aria's candidates; never chooses a destination itself. */
public final class VoicePlaceBridge {
 static String spoken=""; static long spokenAt;
 static final Map<Object,Entry> entries=new WeakHashMap<>();
 static String clean(String text){
  if(text==null)return "";
  String q=text.trim().replaceAll("[.!?。？！]+$", "").trim();
  if(q.matches("(응|어|네|예|그래|좋아|아니|아니야|아니요|없어|없어요|취소|그만|다른 곳|다른 데|다시|다음|이전|맞아|거기로|안내해 ?줘|첫 ?번째|두 ?번째|세 ?번째|[1-9일이삼사오육칠팔구] ?번(?:째)?)(요)?"))return "";
  q=q.replaceFirst("^(아리아[, ]*|네이버에서 +|네이버로 +)", "");
  q=q.replaceFirst("(?:으로|로)?\\s*(?:길\\s*안내(?:해\\s*줘)?|안내해\\s*줘|검색해\\s*줘|찾아\\s*줘|가자|가줘|검색)$", "").trim();
  return q.length()>200?"":q;
 }
 public static synchronized void heard(String text){
  String query=clean(text);
  if(!query.isEmpty()){
   spoken=query;
   spokenAt=SystemClock.elapsedRealtime();
  }
 }
 static synchronized String recent(){long age=SystemClock.elapsedRealtime()-spokenAt;return age>=0&&age<=90000?spoken:"";}
 static Object field(Object o,String name)throws Exception{return RouteRequest.field(o.getClass(),name).get(o);}
 public static void attach(Object fragment,View root){
  try{
   if(root==null||entries.containsKey(fragment))return;
   if(!root.isAttachedToWindow()){root.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener(){public void onViewAttachedToWindow(View v){v.removeOnAttachStateChangeListener(this);NaverPlaceSearch.MAIN.post(()->{if(v.isAttachedToWindow())attach(fragment,v);});}public void onViewDetachedFromWindow(View v){}});return;}
   Activity a=NaverPlaceSearch.activity(root.getContext());if(a==null)return;
   Object binding=fragment.getClass().getMethod("T0").invoke(fragment);
   View list=(View)field(binding,"a0");
   if(!(root instanceof ViewGroup)||list.getParent()!=root)return;
   ViewGroup parent=(ViewGroup)root;
   ViewGroup.LayoutParams listParams=list.getLayoutParams();
   Class<?> paramsClass=listParams.getClass();
   // ConstraintLayout fields in Tmap 11.8.2.4051: k=bottomToTop, l=bottomToBottom.
   java.lang.reflect.Field bottomToTop=paramsClass.getField("k");
   java.lang.reflect.Field bottomToBottom=paramsClass.getField("l");
   int originalTop=bottomToTop.getInt(listParams),originalBottom=bottomToBottom.getInt(listParams);
   if(originalTop!=-1||originalBottom!=0)return;
   FrameLayout footer=new FrameLayout(a);
   footer.setId(View.generateViewId());
   footer.setTag("carrot-voice-place-footer");
   Button button=new Button(a);
   button.setText("원하는 장소가 없나요? 네이버에서 찾기");
   button.setTextSize(14);
   button.setAllCaps(false);
   button.setTextColor(Color.WHITE);
   GradientDrawable bg=new GradientDrawable();
   bg.setColor(0xff00884c);
   bg.setCornerRadius(NaverPlaceSearch.dp(a,24));
   button.setBackground(bg);
   button.setTag("carrot-voice-place-search");
   footer.addView(button,new FrameLayout.LayoutParams(-1,-1));

   ViewGroup.MarginLayoutParams footerParams=(ViewGroup.MarginLayoutParams)paramsClass
       .getConstructor(int.class,int.class).newInstance(0,NaverPlaceSearch.dp(a,48));
   paramsClass.getField("e").setInt(footerParams,0); // leftToLeft
   paramsClass.getField("h").setInt(footerParams,0); // rightToRight
   paramsClass.getField("j").setInt(footerParams,list.getId()); // topToBottom
   bottomToBottom.setInt(footerParams,0);
   int gap=NaverPlaceSearch.dp(a,8);
   footerParams.setMargins(gap,gap,gap,gap);
   parent.addView(footer,footerParams);
   bottomToTop.setInt(listParams,footer.getId());
   bottomToBottom.setInt(listParams,-1);
   list.setLayoutParams(listParams);

   Entry entry=new Entry(a,root,list,parent,footer,button);
   entry.restoreConstraints=()->{
    try{
     bottomToTop.setInt(listParams,originalTop);
     bottomToBottom.setInt(listParams,originalBottom);
     list.setLayoutParams(listParams);
    }catch(IllegalAccessException ignored){}
   };
   entries.put(fragment,entry);
   button.setOnClickListener(v->open(entry));
   root.addOnAttachStateChangeListener(entry);
   try{Object vm=RouteRequest.get(field(fragment,"e0"),"getValue"),flow=field(field(vm,"f"),"Z");update(fragment,RouteRequest.get(flow,"getValue"));}catch(Exception ignored){}
  }catch(Exception e){RouteDiagnostics.event("aria_naver_place_attach_failed",e.getClass().getSimpleName());}
 }
 public static void update(Object fragment,Object state){
  try{Entry entry=entries.get(fragment);if(entry==null)return;Object action=field(state,"a");if(action==null)return;
   long id=((Number)action.getClass().getMethod("getInternalId").invoke(action)).longValue();
   if(entry.actionId==id)return;entry.actionId=id;
   // Use what the user said, never the name of Tmap's first suggested result.
   String latestQuery=recent();if(!latestQuery.isEmpty())entry.query=latestQuery;entry.button.setContentDescription("원하는 장소가 없으면 네이버에서 찾기"+(entry.query.isEmpty()?"":" · "+entry.query));
  }catch(Exception e){RouteDiagnostics.event("aria_naver_place_state_failed",e.getClass().getSimpleName());}
 }
 static void open(Entry entry){
  if(!entry.root.isAttachedToWindow()||entry.a.isFinishing()||entry.a.isDestroyed())return;
  if(entry.query.isEmpty())entry.query=recent();
  try{
   android.content.Intent intent=new android.content.Intent();
   intent.setClassName(entry.a.getPackageName(),"com.skt.tmap.activity.TmapHybridSearchActivity");
   intent.putExtra(NaverPlaceSearch.ARIA_QUERY,entry.query);
   entry.a.startActivity(intent);
   RouteDiagnostics.event("aria_naver_place_opened","explicit_tap=true query_present="+!entry.query.isEmpty());
  }catch(RuntimeException error){
   RouteDiagnostics.event("aria_naver_place_open_failed",error.getClass().getSimpleName());
  }
 }
 static final class Entry implements View.OnAttachStateChangeListener {
  final Activity a;
  final View root,list;
  final ViewGroup parent;
  final FrameLayout footer;
  final Button button;
  String query=recent();
  long actionId=Long.MIN_VALUE;
  Runnable restoreConstraints=()->{};

  Entry(Activity a,View root,View list,ViewGroup parent,FrameLayout footer,Button button){
   this.a=a;this.root=root;this.list=list;this.parent=parent;this.footer=footer;this.button=button;
  }

  public void onViewAttachedToWindow(View v){}
  public void onViewDetachedFromWindow(View v){
   root.removeOnAttachStateChangeListener(this);
   // Keep this entry registered until the window finishes traversing its children.
   NaverPlaceSearch.MAIN.post(()->{
    if(root.isAttachedToWindow()){
     root.addOnAttachStateChangeListener(this);
     return;
    }
    if(footer.getParent()==parent)parent.removeView(footer);
    restoreConstraints.run();
    entries.values().remove(this);
   });
  }
 }
}
