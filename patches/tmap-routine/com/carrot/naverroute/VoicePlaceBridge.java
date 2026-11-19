package com.carrot.naverroute;

import android.app.Activity;
import android.os.SystemClock;
import android.view.*;
import android.widget.*;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import java.util.*;
import org.json.*;

/** Explicit alternative to Aria's candidates; never chooses a destination itself. */
public final class VoicePlaceBridge {
 static String spoken=""; static long spokenAt;
 static volatile NaverPlaceSearch.Panel voicePanel;static volatile long suppressNativeUntil;
 static final Map<Object,Entry> entries=new WeakHashMap<>();
 static volatile Selection selection;
 static final class Selection {
  final java.lang.ref.WeakReference<Activity> activity;final JSONObject place;final long expires;final boolean explicit;
  Selection(Activity a,JSONObject p,boolean chosen)throws JSONException{explicit=chosen;activity=new java.lang.ref.WeakReference<>(a);place=new JSONObject(p.toString());expires=SystemClock.elapsedRealtime()+90000;}
 }
 static Selection pendingSelection(){Selection s=selection;Activity a=s==null?null:s.activity.get();if(s!=null&&(SystemClock.elapsedRealtime()>=s.expires||a==null||a.isFinishing()||a.isDestroyed())){selection=null;return null;}return s;}
 static void clearSelection(){selection=null;NaverPlaceSearch.MAIN.post(()->{for(Entry e:entries.values())if(e.cachedResults!=null)e.button.setText("네이버 결과 보기\n"+e.cachedResults.optJSONObject(0).optString("name"));});}
 static void selectForVoice(NaverPlaceSearch.Panel panel,JSONObject place){try{selection=new Selection(panel.a,place,true);panel.dialog.dismiss();voicePanel=null;for(Entry e:entries.values())if(e.a==panel.a)e.button.setText("네이버 선택: "+place.optString("name")+"\n‘그래’라고 하면 이 장소로 경로를 조회합니다");RouteDiagnostics.event("aria_naver_selection_armed","coordinate_destination=true");}catch(Exception e){RouteDiagnostics.event("aria_naver_selection_failed",e.getClass().getSimpleName());}}

 static String clean(String text){
  if(text==null)return "";
  String q=text.trim().replaceAll("[.!?。？！]+$", "").trim();
  if(q.matches("(응|어|네|예|그래|좋아|아니|아니야|아니요|없어|없어요|취소|그만|다른 곳|다른 데|다시|다음|이전|[1-9일이삼사오육칠팔구] ?번째)(요)?"))return "";
  q=q.replaceFirst("^(아리아[, ]*|네이버에서 +|네이버로 +)", "");
  q=q.replaceFirst("(?:으로|로)?\\s*(?:길\\s*안내(?:해\\s*줘)?|안내해\\s*줘|검색해\\s*줘|찾아\\s*줘|가자|가줘|검색)$", "").trim();
  return q.length()>200?"":q;
 }
 public static synchronized void heard(String text){
  NaverPlaceSearch.Panel panel=voicePanel;String key=PlaceMatch.key(text);int index=PlaceMatch.voiceIndex(text);
  Selection selected=pendingSelection();
  if(selected!=null&&key.matches("(그래|응|네|예|좋아|맞아|거기로|안내해줘)(요)?")){
   selection=null;suppressNativeUntil=SystemClock.elapsedRealtime()+15000;
   NaverPlaceSearch.MAIN.post(()->{try{Activity a=selected.activity.get();if(a==null||a.isFinishing()||a.isDestroyed()){suppressNativeUntil=0;return;}a.startActivity(NaverPlaceSearch.destinationIntent(a,selected.place));RouteDiagnostics.event("aria_naver_voice_selected","explicit_selection=true coordinate_destination=true");}catch(Exception e){suppressNativeUntil=0;RouteDiagnostics.event("aria_naver_voice_selection_failed",e.getClass().getSimpleName());}});return;
  }
  if(selected!=null&&key.matches("아니|아니야|아니요|취소|그만")){NaverPlaceSearch.MAIN.post(VoicePlaceBridge::clearSelection);suppressNativeUntil=0;return;}
  if(panel!=null&&panel.dialog.isShowing()){
   if(index>=0&&index<panel.lastResults.length()){suppressNativeUntil=SystemClock.elapsedRealtime()+15000;NaverPlaceSearch.MAIN.post(()->{try{if(voicePanel!=panel||!panel.dialog.isShowing())return;JSONObject place=panel.lastResults.getJSONObject(index);android.content.Intent intent=NaverPlaceSearch.destinationIntent(panel.a,place);panel.dialog.dismiss();voicePanel=null;panel.a.startActivity(intent);RouteDiagnostics.event("aria_naver_voice_selected","index="+index+" coordinate_destination=true");}catch(Exception e){RouteDiagnostics.event("aria_naver_voice_selection_failed",e.getClass().getSimpleName());}});return;}
   if(key.matches("아니|아니야|아니요|취소|그만")){NaverPlaceSearch.MAIN.post(()->{panel.dialog.dismiss();if(voicePanel==panel)voicePanel=null;});return;}
  }
  String q=clean(text);if(!q.isEmpty()){clearSelection();spoken=q;spokenAt=SystemClock.elapsedRealtime();suppressNativeUntil=0;if(panel!=null)NaverPlaceSearch.MAIN.post(()->{panel.dialog.dismiss();if(voicePanel==panel)voicePanel=null;});}
 }
 public static boolean blocksNativeRoute(){NaverPlaceSearch.Panel panel=voicePanel;Selection selected=pendingSelection();return selected!=null&&selected.explicit||SystemClock.elapsedRealtime()<suppressNativeUntil||panel!=null&&panel.dialog.isShowing();}
 static synchronized String recent(){long age=SystemClock.elapsedRealtime()-spokenAt;return age>=0&&age<=90000?spoken:"";}
 static Object field(Object o,String name)throws Exception{return RouteRequest.field(o.getClass(),name).get(o);}
 public static void attach(Object fragment,View root){
  try{
   if(root==null||entries.containsKey(fragment))return;
   if(!root.isAttachedToWindow()){root.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener(){public void onViewAttachedToWindow(View v){v.removeOnAttachStateChangeListener(this);attach(fragment,v);}public void onViewDetachedFromWindow(View v){}});return;}
   Activity a=NaverPlaceSearch.activity(root.getContext());if(a==null)return;
   Object binding=fragment.getClass().getMethod("T0").invoke(fragment);
   View list=(View)field(binding,"a0");
   // The list's constraints stay intact; its inset reserves space for the footer.
   View content=root.getRootView().findViewById(android.R.id.content);if(!(content instanceof FrameLayout))content=a.findViewById(android.R.id.content);if(!(content instanceof FrameLayout))return;FrameLayout parent=(FrameLayout)content;
   FrameLayout footer=new FrameLayout(a);footer.setTag("carrot-voice-place-footer");
   Button button=new Button(a);button.setText("원하는 장소가 없나요? 네이버에서 찾기");button.setTextSize(14);button.setAllCaps(false);button.setTextColor(Color.WHITE);
   GradientDrawable bg=new GradientDrawable();bg.setColor(0xff00884c);bg.setCornerRadius(NaverPlaceSearch.dp(a,24));button.setBackground(bg);button.setElevation(NaverPlaceSearch.dp(a,4));button.setTag("carrot-voice-place-search");
   footer.addView(button,new FrameLayout.LayoutParams(-1,-1));
   parent.addView(footer,new FrameLayout.LayoutParams(1,NaverPlaceSearch.dp(a,48)));
   Entry entry=new Entry(a,root,list,parent,footer,button);entries.put(fragment,entry);
   button.setOnClickListener(v->open(entry));
   root.getViewTreeObserver().addOnGlobalLayoutListener(entry);
   root.addOnAttachStateChangeListener(entry);
   entry.onGlobalLayout();
   try{Object vm=RouteRequest.get(field(fragment,"e0"),"getValue"),flow=field(field(vm,"f"),"Z");update(fragment,RouteRequest.get(flow,"getValue"));}catch(Exception ignored){}
  }catch(Exception e){RouteDiagnostics.event("aria_naver_place_attach_failed",e.getClass().getSimpleName());}
 }
 public static void update(Object fragment,Object state){
  try{Entry entry=entries.get(fragment);if(entry==null)return;Object action=field(state,"a");if(action==null)return;
   long id=((Number)action.getClass().getMethod("getInternalId").invoke(action)).longValue();
   if(entry.actionId==id)return;entry.actionId=id;
   // Use what the user said, never the name of Tmap's first suggested result.
   String latestQuery=recent();if(!latestQuery.isEmpty())entry.query=latestQuery;entry.button.setContentDescription("원하는 장소가 없으면 네이버에서 찾기"+(entry.query.isEmpty()?"":" · "+entry.query));
   entry.cachedResults=null;entry.button.setText("원하는 장소가 없나요? 네이버에서 찾기");
   lookup(entry,action);
  }catch(Exception e){RouteDiagnostics.event("aria_naver_place_state_failed",e.getClass().getSimpleName());}
 }
 static void lookup(Entry entry,Object action){
  if(entry.request!=null)entry.request.cancel();if(entry.query.isEmpty())return;
  try{List<?> nativePlaces=(List<?>)action.getClass().getMethod("getPoiSearches").invoke(action);for(Object nativePlace:nativePlaces){String name=String.valueOf(nativePlace.getClass().getMethod("getName").invoke(nativePlace));if(PlaceMatch.exact(entry.query,name))return;}
   android.location.Location center=NaverPlaceSearch.searchCenter(entry.a,true);if(center==null)center=NaverPlaceSearch.searchCenter(entry.a,false);if(center==null)return;
   final android.location.Location at=center;final String query=entry.query;final long id=entry.actionId;NaverPlaceSearch.Request request=new NaverPlaceSearch.Request();entry.request=request;
   NaverPlaceSearch.WORK.execute(()->{try{JSONArray found=NaverPlaceSearch.exactFirst(query,NaverPlaceSearch.query(entry.a.getApplicationContext(),query,request,at,false));if(found.length()==0||!PlaceMatch.exact(query,found.getJSONObject(0).optString("name")))return;
    NaverPlaceSearch.MAIN.post(()->{if(request.cancelled||entry.actionId!=id||!entry.root.isShown()||!entry.root.isAttachedToWindow()||entry.a.isFinishing()||!query.equals(recent()))return;if(voicePanel!=null&&voicePanel.dialog.isShowing())return;
     entry.cachedResults=found;try{selection=new Selection(entry.a,found.getJSONObject(0),false);}catch(JSONException ignored){}entry.button.setText("네이버 추천: "+found.optJSONObject(0).optString("name")+"\n‘그래’라고 하면 이 장소로 경로를 조회합니다");entry.button.setMaxLines(2);entry.button.setEllipsize(android.text.TextUtils.TruncateAt.END);entry.button.setContentDescription("네이버 검색 결과 보기 · "+found.optJSONObject(0).optString("name"));entry.onGlobalLayout();RouteDiagnostics.event("aria_naver_exact_results","count="+found.length()+" original_utterance=true auto_popup=false exact_recommendation=true");});
   }catch(Exception e){if(!request.cancelled)RouteDiagnostics.event("aria_naver_exact_lookup_failed",e.getClass().getSimpleName());}});
  }catch(Exception e){RouteDiagnostics.event("aria_naver_candidates_unavailable",e.getClass().getSimpleName());}
 }
 static void open(Entry entry){
  if(entry.a.isFinishing()||entry.a.isDestroyed())return;
  clearSelection();NaverPlaceSearch.Panel panel=new NaverPlaceSearch.Panel(entry.a,entry.query);
  if(NaverPlaceSearch.searchCenter(entry.a,false)==null&&NaverPlaceSearch.searchCenter(entry.a,true)!=null){panel.myLocation=true;panel.position.setText("내 위치 중심 ▾");}
  voicePanel=panel;
  if(entry.cachedResults!=null&&entry.cachedResults.length()>0)panel.openResults(entry.cachedResults);else panel.open();
  RouteDiagnostics.event("aria_naver_place_opened","explicit_tap=true query_present="+!entry.query.isEmpty());
 }
 static final class Entry implements ViewTreeObserver.OnGlobalLayoutListener,View.OnAttachStateChangeListener {
  final Activity a;final View root,list;final ViewGroup parent;final FrameLayout footer;final Button button;final int bottom;String query=recent();long actionId=Long.MIN_VALUE;NaverPlaceSearch.Request request;JSONArray cachedResults;
  Entry(Activity a,View root,View list,ViewGroup parent,FrameLayout footer,Button button){this.a=a;this.root=root;this.list=list;this.parent=parent;this.footer=footer;this.button=button;bottom=list.getPaddingBottom();}
  public void onGlobalLayout(){boolean visible=root.isShown()&&!a.isFinishing()&&!a.isDestroyed();footer.setVisibility(visible?View.VISIBLE:View.GONE);if(!visible)return;
   int gap=NaverPlaceSearch.dp(a,12),height=NaverPlaceSearch.dp(a,cachedResults==null?48:64);int[] at=new int[2],host=new int[2];list.getLocationOnScreen(at);parent.getLocationOnScreen(host);
   int left=at[0]-host[0]+gap,top=at[1]-host[1]+list.getHeight()-gap-height,width=Math.max(1,list.getWidth()-gap*2);
   FrameLayout.LayoutParams p=(FrameLayout.LayoutParams)footer.getLayoutParams();if(p.leftMargin!=left||p.topMargin!=top||p.width!=width||p.height!=height){p.leftMargin=left;p.topMargin=top;p.width=width;p.height=height;footer.setLayoutParams(p);}if(parent.getChildAt(parent.getChildCount()-1)!=footer)footer.bringToFront();
   int padding=bottom+height+gap*2;if(list.getPaddingBottom()!=padding)list.setPadding(list.getPaddingLeft(),list.getPaddingTop(),list.getPaddingRight(),padding);
  }
  public void onViewAttachedToWindow(View v){onGlobalLayout();}
  public void onViewDetachedFromWindow(View v){if(request!=null)request.cancel();if(root.getViewTreeObserver().isAlive())root.getViewTreeObserver().removeOnGlobalLayoutListener(this);root.removeOnAttachStateChangeListener(this);parent.removeView(footer);list.setPadding(list.getPaddingLeft(),list.getPaddingTop(),list.getPaddingRight(),bottom);entries.values().remove(this);}
 }
}
