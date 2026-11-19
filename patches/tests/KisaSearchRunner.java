package com.carrot.tmaprouteprobe;
import android.app.*;import android.content.*;import android.os.*;import android.view.*;import android.widget.*;import java.io.*;import java.lang.reflect.*;import java.nio.file.*;import java.util.*;import org.json.*;
/** Exercises the real safe-drive host and native Compose/Aria fragments, without fake GPS. */
public final class KisaSearchRunner extends Instrumentation {
 String hostMode="safe";JSONObject report=new JSONObject();Activity home,navi;Object manager,fragment;FrameLayout host;File dir;ClassLoader cl;
 static Object get(Object o,String name)throws Exception{Field f=o instanceof Class?((Class<?>)o).getDeclaredField(name):o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o instanceof Class?null:o);}
 static void check(boolean ok,String why)throws Exception{if(!ok)throw new Exception(why);}
 static Object call(Object target,String name,Class<?>[] types,Object...args)throws Exception{Method m=target.getClass().getMethod(name,types);m.setAccessible(true);return m.invoke(target,args);}
 View tag(View root,String value){if(value.equals(root.getTag()))return root;if(root instanceof ViewGroup)for(int i=0;i<((ViewGroup)root).getChildCount();i++){View found=tag(((ViewGroup)root).getChildAt(i),value);if(found!=null)return found;}return null;}
 void main(Throwing action){runOnMainSync(()->{try{action.run();}catch(Exception e){throw new RuntimeException(e);}});}interface Throwing{void run()throws Exception;}
 void shot(String name)throws Exception{android.graphics.Bitmap b=getUiAutomation().takeScreenshot();try(FileOutputStream out=new FileOutputStream(new File(dir,name))){b.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}b.recycle();}
 void addFragment(String type)throws Exception{main(()->{fragment=cl.loadClass(type).getConstructor().newInstance();host=new FrameLayout(navi);host.setId(View.generateViewId());((FrameLayout)navi.findViewById(android.R.id.content)).addView(host,new FrameLayout.LayoutParams(-1,-1));manager=navi.getClass().getMethod("getSupportFragmentManager").invoke(navi);Object tx=cl.loadClass("androidx.fragment.app.c").getConstructor(cl.loadClass("androidx.fragment.app.FragmentManager")).newInstance(manager);call(tx,"c",new Class<?>[]{int.class,cl.loadClass("androidx.fragment.app.Fragment"),String.class,int.class},host.getId(),fragment,"kisa-search-test",1);call(tx,"j",new Class<?>[]{});});Thread.sleep(1200);}
 void removeFragment()throws Exception{main(()->{Object tx=cl.loadClass("androidx.fragment.app.c").getConstructor(cl.loadClass("androidx.fragment.app.FragmentManager")).newInstance(manager);call(tx,"n",new Class<?>[]{cl.loadClass("androidx.fragment.app.Fragment")},fragment);call(tx,"j",new Class<?>[]{});((ViewGroup)host.getParent()).removeView(host);});Thread.sleep(300);}

 void startRoute(Context c)throws Exception{
  JSONObject goal=new JSONObject().put("name","수학의힘 신검단캠퍼스학원").put("lat",37.6034634).put("lon",126.6981604);
  Method destination=cl.loadClass("com.carrot.naverroute.NaverPlaceSearch").getDeclaredMethod("destinationIntent",Context.class,JSONObject.class);destination.setAccessible(true);
  Activity preview=startActivitySync(((Intent)destination.invoke(null,c,goal)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
  Object vm=call(preview,"p0",new Class<?>[]{}),counter=get(vm,"u0"),session=null,card=null;Class<?> bridge=cl.loadClass("com.carrot.naverroute.PreviewBridge");long until=SystemClock.elapsedRealtime()+45000;
  while(SystemClock.elapsedRealtime()<until){final Object ctr=counter;main(()->ctr.getClass().getMethod("setValue",Object.class).invoke(ctr,false));session=((Map<?,?>)get(bridge,"SESSIONS")).get(vm);if(session!=null&&Boolean.TRUE.equals(get(session,"choiceDone"))){for(Object item:(List<?>)call(get(vm,"j0"),"getValue",new Class<?>[]{}))if("Traffic_Recommend".equals(String.valueOf(call(item,"getRoutePlanType",new Class<?>[]{}))))card=item;if(card!=null)break;}Thread.sleep(200);}
  check(card!=null,"Native route not ready");Object fragment=((java.lang.ref.Reference<?>)get(session,"fragment")).get();Class<?> callback=cl.loadClass("com.skt.tmap.mvp.fragment.TmapRoutePreviewFragment$e");Object cb=callback.getConstructor(fragment.getClass()).newInstance(fragment);
  List<?> ordered=(List<?>)call(get(get(vm,"k0"),"Z"),"getValue",new Class<?>[]{});int index=-1;for(int i=0;i<ordered.size();i++)if(call(ordered.get(i),"getRoutePlanType",new Class<?>[]{})==call(card,"getRoutePlanType",new Class<?>[]{}))index=i;
  final Object chosen=card;final int at=index;main(()->callback.getMethod("e",int.class,chosen.getClass()).invoke(cb,at,chosen));Thread.sleep(300);
  ActivityMonitor monitor=addMonitor("com.skt.tmap.activity.TmapNaviActivity",null,false);main(()->{View startButton=preview.findViewById(preview.getResources().getIdentifier("route_preview_drive_button","id",preview.getPackageName()));check(startButton!=null&&startButton.isShown()&&startButton.performClick(),"Native start guidance button missing");});navi=waitForMonitorWithTimeout(monitor,10000);check(navi!=null,"Route guidance host missing");Thread.sleep(4000);main(()->c.startActivity(c.getPackageManager().getLaunchIntentForPackage(c.getPackageName()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)));Thread.sleep(1000);
  Object manager=cl.loadClass("com.skt.tmap.engine.navigation.NavigationManager").getMethod("getInstance").invoke(null);report.put("native_drive_mode",String.valueOf(call(manager,"getDriveMode",new Class<?>[]{})));
 }
 android.view.accessibility.AccessibilityNodeInfo findText(android.view.accessibility.AccessibilityNodeInfo node,String text){if(node==null)return null;if(text.replace(" ","").equals(node.getText()==null?"":node.getText().toString().replace(" ",""))||text.replace(" ","").equals(node.getContentDescription()==null?"":node.getContentDescription().toString().replace(" ","")))return node;for(int i=0;i<node.getChildCount();i++){android.view.accessibility.AccessibilityNodeInfo hit=findText(node.getChild(i),text);if(hit!=null)return hit;}return null;}
 void completeSpeech(String phrase)throws Exception{
  Class<?> header=cl.loadClass("com.skt.nugu.sdk.core.interfaces.message.Header");
  Object event=header.getConstructor(String.class,String.class,String.class,String.class,String.class,String.class)
      .newInstance("probe-dialog","probe-message","ASR","CompleteResult","1.0",null);
  Class<?> flow=cl.loadClass("com.tmapmobility.adot.core.core.sdk.state.AsrResultStateFlow");
  flow.getMethod("onCompleteResult",String.class,header).invoke(flow.getConstructor().newInstance(),phrase,event);
 }
 android.view.accessibility.AccessibilityNodeInfo awaitText(String text,long timeout)throws Exception{
  long deadline=SystemClock.elapsedRealtime()+timeout;
  do{
   for(android.view.accessibility.AccessibilityWindowInfo window:getUiAutomation().getWindows()){
    android.view.accessibility.AccessibilityNodeInfo found=findText(window.getRoot(),text);
    if(found!=null)return found;
   }
   android.view.accessibility.AccessibilityNodeInfo found=findText(getUiAutomation().getRootInActiveWindow(),text);
   if(found!=null)return found;
   Thread.sleep(150);
  }while(SystemClock.elapsedRealtime()<deadline);
  return null;
 }
 boolean clickNode(android.view.accessibility.AccessibilityNodeInfo node){
  for(;node!=null;node=node.getParent())if(node.isClickable()){
   if(node.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK))return true;
   break;
  }
  return false;
 }
 void verifyDetach(Class<?> voice)throws Exception{
  FrameLayout[] container=new FrameLayout[1],footer=new FrameLayout[1],inner=new FrameLayout[1];
  Dialog[] dialog=new Dialog[1];
  main(()->{
   Dialog d=new Dialog(navi);
   FrameLayout content=new FrameLayout(navi),root=new FrameLayout(navi),extra=new FrameLayout(navi);
   content.addView(root,new FrameLayout.LayoutParams(200,200));
   content.addView(extra,new FrameLayout.LayoutParams(100,100));
   container[0]=content;footer[0]=extra;inner[0]=root;dialog[0]=d;
   d.setContentView(content);
   Class<?> entryType=cl.loadClass(voice.getName()+"$Entry");
   Constructor<?> c=entryType.getDeclaredConstructor(Activity.class,View.class,View.class,ViewGroup.class,FrameLayout.class,Button.class);
   c.setAccessible(true);
   View.OnAttachStateChangeListener entry=(View.OnAttachStateChangeListener)c.newInstance(navi,root,root,content,extra,new Button(navi));
   root.addOnAttachStateChangeListener(entry);
   d.show();
  });
  for(int i=0;i<20&&!inner[0].isAttachedToWindow();i++)Thread.sleep(100);
  check(inner[0].isAttachedToWindow(),"Detach fixture was not attached");
  boolean[] preserved={false};
  main(()->{dialog[0].dismiss();preserved[0]=footer[0].getParent()==container[0];});
  check(preserved[0],"Footer removed during window's detach iteration");
  for(int i=0;i<20&&footer[0].getParent()==container[0];i++)Thread.sleep(100);
  check(footer[0].getParent()!=container[0],"Deferred footer cleanup did not finish");
 }
 public void onCreate(Bundle b){super.onCreate(b);hostMode=b.getString("host","safe");start();}
 public void onStart(){Bundle result=new Bundle();try{
  Context context=getTargetContext();cl=context.getClassLoader();dir=new File(context.getExternalFilesDir(null),"kisa_search_probe");dir.mkdirs();ActivityMonitor homeMonitor=addMonitor("com.skt.tmap.activity.TmapNewMainActivity",null,false);startActivitySync(context.getPackageManager().getLaunchIntentForPackage(context.getPackageName()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));Thread.sleep(2500);home=homeMonitor.getLastActivity();if(home==null)home=waitForMonitorWithTimeout(homeMonitor,5000);check(home!=null,"Home missing");
  main(()->cl.loadClass("com.carrot.naverroute.HomeIdleBridge").getMethod("pause",Activity.class).invoke(null,home));if(hostMode.equals("home")){navi=home;}else if(hostMode.equals("route")){startRoute(context);}else{ActivityMonitor navMonitor=addMonitor("com.skt.tmap.activity.TmapNaviActivity",null,false);main(()->{View safety=home.findViewById(home.getResources().getIdentifier("button_safety_driving","id",home.getPackageName()));check(safety!=null,"Native safe drive button missing");safety.performClick();});navi=waitForMonitorWithTimeout(navMonitor,7000);check(navi!=null,"Native navigation host missing");Thread.sleep(1000);}report.put("host",hostMode);
  Class<?> voice=cl.loadClass("com.carrot.naverroute.VoicePlaceBridge");main(()->completeSpeech("수학의 힘 신 검단캠퍼스"));addFragment("com.tmapmobility.adot.ui.view.fragment.AdotSelectPlaceFragment");View footer=tag(navi.findViewById(android.R.id.content),"carrot-voice-place-search");check(footer!=null&&footer.isShown(),"Aria footer missing in navigation window");report.put("native_asr_completion_callback",true);
  Object entry=((Map<?,?>)get(voice,"entries")).get(fragment);
  View list=(View)get(entry,"list"),area=(View)get(entry,"footer");
  int[] listAt=new int[2],buttonAt=new int[2];
  main(()->list.setMinimumHeight(Math.round(navi.getResources().getDisplayMetrics().density*56)));
  Thread.sleep(300);
  main(()->{list.getLocationOnScreen(listAt);area.getLocationOnScreen(buttonAt);});
  check(list.getHeight()>0&&area.getWidth()>0&&buttonAt[1]>=listAt[1]+list.getHeight(),"Naver button overlaps the native candidate list");
  report.put("button_has_its_own_row_below_candidates",true);
  Class<?> actionType=cl.loadClass("com.tmapmobility.adot.data.navigation.action.NavigationAction$SelectPlace$DMContentsList"),directive=cl.loadClass("com.tmapmobility.adot.data.navigation.action.AdotDirective"),stateType=cl.loadClass("com.tmapmobility.adot.ui.model.state.m0"),theme=cl.loadClass("com.tmapmobility.adot.ui.model.state.AdotSelectPlaceThemeState");Object wrong=cl.loadClass("com.skt.tmap.adot.AdotPoiSearch").getConstructor().newInstance();Field name=wrong.getClass().getDeclaredField("name");name.setAccessible(true);name.set(wrong,"수학의힘 검단캠퍼스");Object action=actionType.getConstructor(String.class,directive,String.class,String.class,String.class,List.class).newInstance("probe",null,"","장소를 선택해 주세요","",Collections.singletonList(wrong));Object state=stateType.getConstructor(actionType,String.class,List.class,theme).newInstance(action,"장소를 선택해 주세요",Collections.emptyList(),theme.getField("DAY").get(null));main(()->voice.getMethod("update",Object.class,Object.class).invoke(null,fragment,state));
  android.accessibilityservice.AccessibilityServiceInfo info=getUiAutomation().getServiceInfo();
  info.flags|=android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
      |android.accessibilityservice.AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS;
  getUiAutomation().setServiceInfo(info);
  ActivityMonitor searchMonitor=addMonitor("com.skt.tmap.activity.TmapHybridSearchActivity",null,false);
  ActivityMonitor previewMonitor=addMonitor("com.skt.tmap.activity.TmapRoutePreviewActivity",null,false);
  main(()->completeSpeech("그래"));
  Thread.sleep(1200);
  check(previewMonitor.getHits()==0,"Aria yes opened a Naver coordinate preview");
  check(searchMonitor.getHits()==0,"Aria yes opened Naver search");
  report.put("aria_yes_does_not_open_naver",true);
  main(()->footer.performClick());
  Activity search=waitForMonitorWithTimeout(searchMonitor,6500);
  check(search!=null,"Button did not open an independent search screen");
  check("수학의 힘 신 검단캠퍼스".equals(search.getIntent().getStringExtra("com.kisa.navi.ARIA_NAVER_QUERY")),"Original utterance not retained");
  removeFragment();
  android.view.accessibility.AccessibilityNodeInfo resultRow=awaitText("수학의힘신검단캠퍼스학원",12000);
  check(resultRow!=null,"Exact result did not survive the Aria candidate window closing");
  report.put("explicit_button_passes_original_query",true).put("naver_search_survives_candidate_detach",true);
  main(()->completeSpeech("그래"));
  Thread.sleep(1200);
  check(previewMonitor.getHits()==0,"Voice selected place inside Naver results");
  resultRow=awaitText("수학의힘신검단캠퍼스학원",1500);
  check(resultRow!=null&&clickNode(resultRow),"Manual result selection missing");
  check(clickNode(awaitText("목적지로선택",3500)),"Manual destination confirmation missing");
  Activity preview=waitForMonitorWithTimeout(previewMonitor,7000);
  check(preview!=null,"Manual Naver selection did not open coordinate preview");
  main(()->preview.finish());
  report.put("manual_destination_confirmation_opens_preview",true);
  verifyDetach(voice);
  report.put("dialog_detach_removes_footer_after_traversal",true);

 }catch(Throwable error){while(error.getCause()!=null)error=error.getCause();try{report.put("error",error.toString());shot("failure.png");}catch(Exception ignored){}}try{if(hostMode.equals("route")){Object navManager=cl.loadClass("com.skt.tmap.engine.navigation.NavigationManager").getMethod("getInstance").invoke(null);main(()->navManager.getClass().getMethod("stopDriving",boolean.class).invoke(navManager,false));}}catch(Exception ignored){}try{Files.write(new File(dir,"report.json").toPath(),report.toString(2).getBytes("UTF-8"));}catch(Exception ignored){}result.putString("report",report.toString());finish(Activity.RESULT_OK,result);}
}
