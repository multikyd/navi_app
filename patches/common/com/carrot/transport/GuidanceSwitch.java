package com.carrot.transport;
import android.app.*;import android.content.*;import android.content.pm.PackageManager;import android.os.*;import android.graphics.*;import android.graphics.drawable.GradientDrawable;import android.view.*;import android.widget.*;import java.lang.ref.WeakReference;import java.lang.reflect.*;import java.util.*;
/** Changes the audio/overlay owner of an existing dual trip, without recreating either route. */
public final class GuidanceSwitch {
 static final String TMAP="com.skt.tmap.ku",NAVER="com.nhn.android.nmap",ACTION="com.kisa.navi.SWITCH_OWNER";
 static final Handler MAIN=new Handler(Looper.getMainLooper());static Application app;static boolean tmap;static WeakReference<Activity> screen=new WeakReference<>(null);static WeakReference<ImageButton> button=new WeakReference<>(null);static boolean pending;
 static Object backend(String method,Class<?>[] types,Object...args)throws Exception{Class<?> type=app.getClassLoader().loadClass(tmap?"com.carrot.naverroute.DualNavigation":"com.carrot.naver.CompanionMode");return type.getMethod(method,types).invoke(null,args);}
 static String token(){try{return (String)backend("switchToken",new Class<?>[]{});}catch(Exception e){return "";}}
 static String owner(){try{return (String)backend("guidanceOwner",new Class<?>[]{});}catch(Exception e){return "";}}
 static boolean setOwner(String owner){try{return Boolean.TRUE.equals(backend("setGuidanceOwner",new Class<?>[]{String.class},owner));}catch(Exception e){return false;}}
 public static synchronized void init(Application application){if(app!=null||Build.VERSION.SDK_INT<34)return;app=application;tmap=TMAP.equals(app.getPackageName());
  app.registerReceiver(new BroadcastReceiver(){public void onReceive(Context c,Intent intent){String sender=getSentFromPackage(),peer=tmap?NAVER:TMAP;
   if(!peer.equals(sender)||getSentFromUid()<0||app.getPackageManager().checkSignatures(android.os.Process.myUid(),getSentFromUid())!=PackageManager.SIGNATURE_MATCH)return;
   String expected=token(),requested=intent.getStringExtra("owner");if(expected.isEmpty()||!expected.equals(intent.getStringExtra("trip"))||!Objects.equals(owner(),intent.getStringExtra("previous")))return;
   if(setOwner(requested))setResultCode(Activity.RESULT_OK);
  }},new IntentFilter(ACTION),Context.RECEIVER_EXPORTED);
  MAIN.post(TICK);
 }
 public static void resumed(Activity activity){if(app==null)return;screen=new WeakReference<>(activity);update();}
 public static void paused(Activity activity){if(screen.get()==activity){screen.clear();ImageButton b=button.get();if(b!=null)b.setVisibility(View.GONE);}}
 static int dp(Context c,int n){return (int)(n*c.getResources().getDisplayMetrics().density+.5f);}
 static boolean search(View v){Object tag=v.getTag();if(v.isShown()&&(v instanceof EditText&&v.hasFocus()||"kisa-navigation-search-root".equals(tag)||"carrot-voice-place-footer".equals(tag)))return true;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++)if(search(((ViewGroup)v).getChildAt(i)))return true;return false;}
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
  pending=true;update();Intent request=new Intent(ACTION).setPackage(peer).putExtra("trip",trip).putExtra("owner",next).putExtra("previous",before);
  // The OS shares sender identity; the receiving app checks the package and APK signer.
  app.sendOrderedBroadcast(request,null,BroadcastOptions.makeBasic().setShareIdentityEnabled(true).toBundle(),new BroadcastReceiver(){public void onReceive(Context c,Intent result){pending=false;if(!trip.equals(token())){update();return;}if(getResultCode()==Activity.RESULT_OK&&setOwner(next)){launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);app.startActivity(launch);android.util.Log.i("KisaNaviSwitch","owner="+next+" routes_preserved=true");}else Toast.makeText(a,"두 앱의 안내가 연결된 상태에서 전환할 수 있습니다.",Toast.LENGTH_SHORT).show();update();}},MAIN,Activity.RESULT_CANCELED,null,null);
 }
 static final Runnable TICK=new Runnable(){public void run(){try{update();}catch(Exception e){android.util.Log.w("KisaNaviSwitch","UI update failed",e);}MAIN.postDelayed(this,500);}};
 private GuidanceSwitch(){}
}
