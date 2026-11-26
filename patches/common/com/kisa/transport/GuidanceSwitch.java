package com.kisa.transport;
import android.app.*;import android.content.*;import android.content.pm.PackageManager;import android.os.*;import android.graphics.*;import android.graphics.drawable.GradientDrawable;import android.view.*;import android.widget.*;import java.lang.ref.WeakReference;import java.lang.reflect.*;import java.util.*;
/** Changes the audio/overlay owner of an existing dual trip, without recreating either route. */
public final class GuidanceSwitch {
 static final String TMAP="com.skt.tmap.ku",NAVER="com.nhn.android.nmap",ACTION="com.kisa.navi.SWITCH_OWNER",RECOVER="com.kisa.navi.RECOVER_TMAP",PREVIEW="com.kisa.navi.PEER_PREVIEW",VERIFY="com.kisa.navi.VERIFY_TRIP";
 static final Handler MAIN=new Handler(Looper.getMainLooper());static Application app;static boolean tmap;static WeakReference<Activity> screen=new WeakReference<>(null);static WeakReference<ImageButton> button=new WeakReference<>(null);static boolean pending;
 static Object backend(String method,Class<?>[] types,Object...args)throws Exception{Class<?> type=app.getClassLoader().loadClass(tmap?"com.kisa.naverroute.DualNavigation":"com.kisa.naver.CompanionMode");return type.getMethod(method,types).invoke(null,args);}
 static String token(){try{return (String)backend("switchToken",new Class<?>[]{});}catch(Exception e){return "";}}
 static String owner(){try{return (String)backend("guidanceOwner",new Class<?>[]{});}catch(Exception e){return "";}}
 static org.json.JSONObject route(){try{return (org.json.JSONObject)backend("activeRoute",new Class<?>[]{});}catch(Exception e){return null;}}
 static boolean setOwner(String owner){try{return Boolean.TRUE.equals(backend("setGuidanceOwner",new Class<?>[]{String.class},owner));}catch(Exception e){return false;}}
 public static synchronized void init(Application application){if(app!=null||Build.VERSION.SDK_INT<34)return;app=application;tmap=TMAP.equals(app.getPackageName());
  app.registerReceiver(new BroadcastReceiver(){public void onReceive(Context c,Intent intent){String sender=getSentFromPackage(),peer=tmap?NAVER:TMAP;
   if(!peer.equals(sender)||getSentFromUid()<0||app.getPackageManager().checkSignatures(android.os.Process.myUid(),getSentFromUid())!=PackageManager.SIGNATURE_MATCH)return;
   if(PREVIEW.equals(intent.getAction())){
    if(tmap)try{
     Intent preview=(Intent)backend("peerPreview",new Class<?>[]{Context.class,String.class,String.class,double.class},app,intent.getStringExtra("trip"),intent.getStringExtra("route"),intent.getDoubleExtra("seconds",0));
     if(preview!=null){
      if(intent.getBooleanExtra("self_launch",false)){
       Activity current=screen.get();
       if(current==null||!current.hasWindowFocus()||!open(current,preview))return;
      }else{
       ActivityOptions options=ActivityOptions.makeBasic();
       if(Build.VERSION.SDK_INT>=35)options.setPendingIntentCreatorBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED);
       PendingIntent ticket=PendingIntent.getActivity(app,preview.getStringExtra("com.kisa.navi.RECOVERY_PREVIEW").hashCode(),preview,PendingIntent.FLAG_ONE_SHOT|PendingIntent.FLAG_IMMUTABLE,options.toBundle());
       getResultExtras(true).putParcelable("preview",ticket);
      }
      setResultCode(Activity.RESULT_OK);
     }
    }catch(Exception e){android.util.Log.w("KisaNaviSwitch","Peer preview failed",e);}
    return;
   }
   if(RECOVER.equals(intent.getAction())){
    if(tmap)try{if(Boolean.TRUE.equals(backend("recoverTmap",new Class<?>[]{Context.class,String.class},app,intent.getStringExtra("trip"))))setResultCode(Activity.RESULT_OK);}catch(Exception e){android.util.Log.w("KisaNaviSwitch","Recovery request failed",e);}
    return;
   }
   String expected=token(),requested=intent.getStringExtra("owner");
   try{if(expected.isEmpty()||!expected.equals(intent.getStringExtra("trip"))||!TripRoute.same(route(),new org.json.JSONObject(intent.getStringExtra("route"))))return;}catch(Exception invalid){return;}
   if(VERIFY.equals(intent.getAction())){setResultCode(Activity.RESULT_OK);return;}
   if(setOwner(requested))setResultCode(Activity.RESULT_OK);
  }},switchActions(),Context.RECEIVER_EXPORTED);
  MAIN.post(TICK);
 }
 static IntentFilter switchActions(){IntentFilter filter=new IntentFilter(ACTION);filter.addAction(RECOVER);filter.addAction(PREVIEW);filter.addAction(VERIFY);return filter;}
 public static void resumed(Activity activity){if(app==null)return;screen=new WeakReference<>(activity);update();}
 public static void paused(Activity activity){if(screen.get()==activity){screen.clear();ImageButton b=button.get();if(b!=null)b.setVisibility(View.GONE);}}
 static int dp(Context c,int n){return (int)(n*c.getResources().getDisplayMetrics().density+.5f);}
 static boolean search(View v){Object tag=v.getTag();if(v.isShown()&&(v instanceof EditText&&v.hasFocus()||"kisa-navigation-search-root".equals(tag)||"kisa-voice-place-footer".equals(tag)))return true;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++)if(search(((ViewGroup)v).getChildAt(i)))return true;return false;}
 static boolean navigation(Activity a){if(tmap)return a.getClass().getName().equals("com.skt.tmap.activity.TmapNaviActivity");View root=a.findViewById(android.R.id.content);if(root==null)return false;if(a.getClass().getName().equals("com.naver.map.MainActivity")&&!token().isEmpty()){int id=GuidancePlacement.id(root,"v_root");View nativeRoot=id==0?null:a.findViewById(id);if(nativeRoot!=null&&nativeRoot.isShown())return true;}for(String name:new String[]{"v_first_bg","v_second","v_tbt_container","v_tbt_component","tv_speed"}){int id=GuidancePlacement.id(root,name);View v=id==0?null:a.findViewById(id);if(v!=null&&v.isShown())return true;}return false;}
 static String lastDiagnostic="";
 static void diagnostic(Activity a,String reason){try{String state=new org.json.JSONObject().put("activity",a.getClass().getName()).put("owner",owner()).put("token_present",!token().isEmpty()).put("native_navigation_visible",navigation(a)).put("state",reason).toString();if(state.equals(lastDiagnostic))return;lastDiagnostic=state;java.io.File dir=app.getExternalFilesDir(null);if(dir!=null)java.nio.file.Files.write(new java.io.File(dir,"kisa_guidance_switch_state.json").toPath(),state.getBytes("UTF-8"));}catch(Exception ignored){}}
 static void update(){Activity a=screen.get();if(a==null||a.isFinishing()||a.isDestroyed())return;View content=a.findViewById(android.R.id.content);if(!(content instanceof FrameLayout))return;FrameLayout root=(FrameLayout)content;ImageButton b=button.get();
  boolean visible=navigation(a)&&!token().isEmpty()&&!search(root)&&!ComposeRegions.search(root);WindowInsets insets=root.getRootWindowInsets();if(insets!=null&&insets.isVisible(WindowInsets.Type.ime()))visible=false;
  if(b==null||b.getContext()!=a){if(!visible){diagnostic(a,"not_eligible");return;}if(b!=null&&b.getParent() instanceof ViewGroup)((ViewGroup)b.getParent()).removeView(b);b=createButton(a);root.addView(b,new FrameLayout.LayoutParams(dp(a,48),dp(a,48)));button=new WeakReference<>(b);b.setOnClickListener(v->request(a));}
  b.setEnabled(!pending);b.setAlpha(pending?.5f:1f);b.setVisibility(visible?View.VISIBLE:View.GONE);if(visible){b.measure(View.MeasureSpec.makeMeasureSpec(dp(a,48),View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(dp(a,48),View.MeasureSpec.EXACTLY));GuidancePlacement.place(root,b,false);b.bringToFront();}diagnostic(a,!visible?"not_eligible":b.getVisibility()==View.VISIBLE?"visible":"no_free_space");
 }
 public static ImageButton createButton(Activity a){ImageButton b=new ImageButton(a);b.setTag("kisa-guidance-switch");b.setScaleType(ImageView.ScaleType.FIT_CENTER);b.setElevation(dp(a,2));b.setContentDescription(tmap?"네이버로 전환":"티맵으로 전환");b.setTooltipText(tmap?"네이버로 전환":"티맵으로 전환");try{b.setImageDrawable(app.getPackageManager().getApplicationIcon(tmap?NAVER:TMAP));}catch(Exception ignored){b.setImageResource(android.R.drawable.ic_menu_directions);}GradientDrawable bg=new GradientDrawable();bg.setColor(tmap?0xe66e757b:0xf0111111);bg.setShape(GradientDrawable.OVAL);b.setBackground(new android.graphics.drawable.InsetDrawable(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x55ffffff),bg,null),dp(a,2)));b.setBackgroundTintList(null);b.setPadding(dp(a,10),dp(a,10),dp(a,10),dp(a,10));return b;}
 static void request(Activity a){if(pending)return;String trip=token(),before=owner(),next=tmap?"naver":"tmap";if(trip.isEmpty()||before.isEmpty())return;String peer=tmap?NAVER:TMAP;Intent launch=app.getPackageManager().getLaunchIntentForPackage(peer);if(launch==null){Toast.makeText(a,"전환할 내비 앱을 찾지 못했습니다.",Toast.LENGTH_SHORT).show();return;}
  org.json.JSONObject current=route();
  if(current==null)return;
  pending=true;update();exchange(peer,trip,before,next,accepted->{
   if(!trip.equals(token())||!TripRoute.same(current,route())){finish();return;}
   if(accepted&&setOwner(next)){open(a,launch);android.util.Log.i("KisaNaviSwitch","owner="+next+" routes_preserved=true");finish();return;}
   if(!accepted)GuidanceRecovery.sync(a,launch,peer,trip,next);
   else failed(a,"현재 안내 연결을 확인해 주세요.");
  });
 }
 interface Reply{void received(boolean accepted);}
 static void exchange(String peer,String trip,String before,String next,Reply reply){
  org.json.JSONObject current=route();
  if(current==null){reply.received(false);return;}
  send(new Intent(ACTION).setPackage(peer).putExtra("trip",trip).putExtra("owner",next).putExtra("route",current.toString()),reply);
 }
 public static void verifyPeer(String trip,String route,java.util.function.Consumer<Boolean> result){
  if(app==null){result.accept(false);return;}
  send(new Intent(VERIFY).setPackage(tmap?NAVER:TMAP).putExtra("trip",trip).putExtra("route",route),result::accept);
 }
 public static void completeNaverHandoff(String trip,boolean tmapWins){
  if(app==null||!tmap)return;
  MAIN.postDelayed(()->{
   if(!trip.equals(token()))return;
   Activity a=screen.get();
   if(!tmapWins){if(a!=null&&a.hasWindowFocus())a.moveTaskToBack(true);return;}
   exchange(NAVER,trip,"naver","tmap",accepted->{if(accepted&&trip.equals(token()))setOwner("tmap");});
  },700);
 }
 public static void compareFromNaver(double seconds){
  Activity a=screen.get();String trip=token();
  if(app==null||tmap||pending||a==null||!a.hasWindowFocus()||trip.isEmpty())return;
  Intent launch=app.getPackageManager().getLaunchIntentForPackage(TMAP);
  if(launch==null)return;
  pending=true;
  GuidanceRecovery.preview(a,launch,trip,seconds,false,false);
 }
 static void peerPreview(String trip,org.json.JSONObject route,double seconds,boolean selfLaunch,java.util.function.BiConsumer<Boolean,PendingIntent> reply){
  Intent request=new Intent(PREVIEW).setPackage(TMAP).putExtra("trip",trip).putExtra("route",route.toString()).putExtra("seconds",seconds).putExtra("self_launch",selfLaunch);
  app.sendOrderedBroadcast(request,null,BroadcastOptions.makeBasic().setShareIdentityEnabled(true).toBundle(),new BroadcastReceiver(){
   public void onReceive(Context c,Intent ignored){
    Bundle extras=getResultExtras(false);
    reply.accept(getResultCode()==Activity.RESULT_OK,extras==null?null:extras.getParcelable("preview",PendingIntent.class));
   }
  },MAIN,Activity.RESULT_CANCELED,null,null);
 }
 static void send(Intent intent,Reply reply){
  app.sendOrderedBroadcast(intent,null,BroadcastOptions.makeBasic().setShareIdentityEnabled(true).toBundle(),new BroadcastReceiver(){
   public void onReceive(Context c,Intent result){reply.received(getResultCode()==Activity.RESULT_OK);}
  },MAIN,Activity.RESULT_CANCELED,null,null);
 }
 static boolean open(Activity a,Intent intent){
  try{a.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));return true;}
  catch(RuntimeException e){android.util.Log.w("KisaNaviSwitch","Could not open navigation",e);return false;}
 }
 static boolean open(Activity a,PendingIntent intent){
  if(!a.hasWindowFocus())return false;
  try{
   ActivityOptions options=ActivityOptions.makeBasic();
   options.setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED);
   intent.send(a,0,null,null,null,null,options.toBundle());
   return true;
  }catch(PendingIntent.CanceledException e){return false;}
 }
 static void finish(){pending=false;update();}
 static void failed(Activity a,String message){Toast.makeText(a,message,Toast.LENGTH_SHORT).show();finish();}
 static final Runnable TICK=new Runnable(){public void run(){try{update();}catch(Exception e){android.util.Log.w("KisaNaviSwitch","UI update failed",e);}MAIN.postDelayed(this,500);}};
 private GuidanceSwitch(){}
}
