package com.carrot.naverroute;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.content.res.Configuration;
import android.content.res.ColorStateList;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.location.Location;
import android.net.Uri;
import android.os.*;
import android.text.Html;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.webkit.WebView;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.lang.reflect.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.zip.GZIPInputStream;

/** Explicit supplementary place search. Naver IDs are never used as Tmap POI IDs. */
public final class NaverPlaceSearch {
 static final Handler MAIN=new Handler(Looper.getMainLooper());
 static final ExecutorService WORK=Executors.newFixedThreadPool(2,r->{Thread t=new Thread(r,"NaverPlaceSearch");t.setDaemon(true);return t;});
 static final Map<Activity,java.lang.ref.WeakReference<Button>> BUTTONS=new WeakHashMap<>();
 static int dp(Context c,int n){return (int)(n*c.getResources().getDisplayMetrics().density+.5f);}
 static Activity activity(Context c){for(int i=0;i<12&&c!=null;i++){if(c instanceof Activity)return (Activity)c;if(!(c instanceof ContextWrapper))break;Context next=((ContextWrapper)c).getBaseContext();if(next==c)break;c=next;}return null;}
 static boolean searchWeb(View view){if(view instanceof WebView&&view.isShown()){try{Uri u=Uri.parse(((WebView)view).getUrl());if("search.tmobiweb.com".equals(u.getHost())&&u.getPath()!=null&&(u.getPath().startsWith("/app/search/inapp/")||u.getPath().equals("/app/search/hybrid/result")))return true;}catch(Exception ignored){}}if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++)if(searchWeb(group.getChildAt(i)))return true;}return false;}
 static String webQuery(View view){if(view instanceof WebView&&view.isShown()){try{Uri u=Uri.parse(((WebView)view).getUrl());if("search.tmobiweb.com".equals(u.getHost())){String q=u.getQueryParameter("searchQuery");if(q!=null&&!q.trim().isEmpty())return q.trim();}}catch(Exception ignored){}}if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++){String q=webQuery(((ViewGroup)view).getChildAt(i));if(!q.isEmpty())return q;}return "";}
 public static void attach(WebView web){attach(activity(web.getContext()));}
 public static void attach(WebView web,Activity a){attach(a);}
 public static void attach(Activity a){
  if(a==null)return;java.lang.ref.WeakReference<Button> installed=BUTTONS.get(a);if(installed!=null&&installed.get()!=null&&installed.get().getParent()!=null)return;
  View content=a.findViewById(android.R.id.content);if(!(content instanceof FrameLayout))return;FrameLayout root=(FrameLayout)content;
  Button button=new Button(a);button.setText("네이버 장소 검색");button.setTextSize(15);button.setAllCaps(false);button.setTextColor(Color.WHITE);button.setTypeface(null,Typeface.BOLD);
  GradientDrawable bg=new GradientDrawable();bg.setColor(0xff00884c);bg.setCornerRadius(dp(a,24));button.setBackground(bg);button.setElevation(dp(a,5));button.setPadding(dp(a,18),0,dp(a,18),0);button.setVisibility(View.GONE);button.setTag("carrot-naver-place-search");
  FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(-2,dp(a,48),Gravity.BOTTOM|Gravity.END);p.setMargins(dp(a,16),0,dp(a,16),dp(a,24));root.addView(button,p);BUTTONS.put(a,new java.lang.ref.WeakReference<>(button));
  int editId=a.getResources().getIdentifier("search_edit_text","id",a.getPackageName());boolean result=a.getClass().getName().equals("com.skt.tmap.activity.TmapSearchResultKtActivity");
  Runnable update=()->{WindowInsets insets=root.getRootWindowInsets();int bottom=insets==null?0:(Build.VERSION.SDK_INT>=30?insets.getInsets(WindowInsets.Type.navigationBars()).bottom:insets.getSystemWindowInsetBottom());FrameLayout.LayoutParams layout=(FrameLayout.LayoutParams)button.getLayoutParams();int margin=dp(a,24)+bottom;if(layout.bottomMargin!=margin){layout.bottomMargin=margin;button.setLayoutParams(layout);}View edit=a.findViewById(editId);button.setVisibility(!a.isFinishing()&&(result||edit instanceof EditText&&edit.isShown()||searchWeb(root))?View.VISIBLE:View.GONE);};
  root.getViewTreeObserver().addOnGlobalLayoutListener(()->update.run());
  button.setOnClickListener(v->{View edit=a.findViewById(editId);String query=webQuery(root);if(query.isEmpty()&&edit instanceof EditText)query=((EditText)edit).getText().toString().trim();if(query.isEmpty())query=a.getIntent().getStringExtra("SearchTitle");show(a,query==null?"":query);});update.run();
 }
 static final class Request {volatile boolean cancelled;volatile HttpURLConnection connection;void cancel(){cancelled=true;HttpURLConnection c=connection;if(c!=null)c.disconnect();}void check()throws IOException{if(cancelled)throw new IOException("Cancelled");}}
 static Location current(Context c){try{Class<?> type=c.getClassLoader().loadClass("com.skt.tmap.engine.navigation.location.TmapLocationManager");Object manager=type.getMethod("getInstance").invoke(null);return (Location)type.getMethod("getCurrentPosition").invoke(manager);}catch(Exception e){return null;}}
 static Location mapCenter(Context c){try{Class<?> type=c.getClassLoader().loadClass("com.skt.tmap.p");Object state=type.getMethod("a",Context.class).invoke(null,c);Object point=RouteRequest.field(type,"F").get(state);if(point==null)return null;double x=((Number)RouteRequest.get(point,"getLongitude")).doubleValue(),y=((Number)RouteRequest.get(point,"getLatitude")).doubleValue();if(!coordinate(x,y))return null;Location at=new Location("mapCenter");at.setLongitude(x);at.setLatitude(y);return at;}catch(Exception e){return null;}}
 static Location searchCenter(Context c,boolean myLocation){Location at=myLocation?current(c):mapCenter(c);return at!=null&&coordinate(at.getLongitude(),at.getLatitude())?new Location(at):null;}
 static String searchUrl(String query,Location at,boolean distanceSort)throws Exception{if(at==null||!coordinate(at.getLongitude(),at.getLatitude()))throw new IOException("Search center unavailable");return "https://apis.naver.com/mapmobileapps/maps-search/allSearch?query="+URLEncoder.encode(query,"UTF-8")+"&type=all&displayCount=20&page=1&siteSort="+(distanceSort?"1":"0")+"&searchCoord="+URLEncoder.encode(at.getLongitude()+";"+at.getLatitude(),"UTF-8");}
 static boolean coordinate(double x,double y){return Double.isFinite(x)&&Double.isFinite(y)&&x>=-180&&x<=180&&y>=-90&&y<=90&&(x!=0||y!=0);}
 static String plain(String s){return Html.fromHtml(s,Html.FROM_HTML_MODE_LEGACY).toString().trim();}
 static JSONArray parse(JSONObject response)throws Exception{
  JSONObject result=response.optJSONObject("result");if(result==null)throw new IOException("검색 응답을 확인하지 못했습니다.");
  JSONObject place=result.optJSONObject("place");JSONArray list=place==null?null:place.optJSONArray("list"),items=new JSONArray();Set<String> seen=new HashSet<>();
  if(list!=null)for(int i=0;i<list.length()&&items.length()<20;i++){JSONObject row=list.optJSONObject(i);if(row==null)continue;double x=row.optDouble("x",Double.NaN),y=row.optDouble("y",Double.NaN);String name=plain(row.optString("name",""));if(name.isEmpty()||!coordinate(x,y))continue;String key=name+":"+x+":"+y;if(!seen.add(key))continue;String road=plain(row.optString("roadAddress","")),address=plain(row.optString("address",""));JSONArray categories=row.optJSONArray("category");String category=categories==null||categories.length()==0?"":plain(categories.optString(categories.length()-1,""));JSONObject business=row.optJSONObject("businessStatus"),state=business==null?null:business.optJSONObject("status");String hours=state==null?"":plain(state.optString("text","")),detail=state==null?"":plain(state.optString("detailInfo",""));if(!detail.isEmpty())hours+=(hours.isEmpty()?"":" · ")+detail;items.put(new JSONObject().put("name",name).put("category",category).put("hours",hours).put("address",road.isEmpty()?address:road).put("lon",x).put("lat",y));}
  return items;
 }
 static JSONArray query(Context context,String query,Request request)throws Exception{return query(context,query,request,searchCenter(context,false),false);}
 static JSONArray query(Context context,String query,Request request,Location center,boolean distanceSort)throws Exception{
  query=query.trim();if(query.isEmpty()||query.length()>200)throw new IOException("검색어는 1~200자로 입력해 주세요.");
  String url=searchUrl(query,center,distanceSort);
  Method sign=context.getClassLoader().loadClass("com.naver.map.hmac.Hmac").getDeclaredMethod("nativeGetEncryptUrl",String.class);sign.setAccessible(true);String signed=(String)sign.invoke(null,url);request.check();
  HttpURLConnection c=(HttpURLConnection)new URL(signed).openConnection();request.connection=c;c.setConnectTimeout(10000);c.setReadTimeout(15000);c.setInstanceFollowRedirects(false);c.setRequestProperty("Accept-Encoding","gzip");
  try{int status=c.getResponseCode();if(status!=200)throw new IOException("네이버 검색 연결 오류 ("+status+")");InputStream stream=c.getInputStream();if("gzip".equalsIgnoreCase(c.getContentEncoding()))stream=new GZIPInputStream(stream);ByteArrayOutputStream bytes=new ByteArrayOutputStream();try(InputStream in=stream){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1){request.check();if(bytes.size()+n>4000000)throw new IOException("검색 응답이 너무 큽니다.");bytes.write(b,0,n);}}request.check();JSONArray results=parse(new JSONObject(bytes.toString("UTF-8")));for(int i=0;i<results.length();i++){JSONObject item=results.getJSONObject(i);float[] distance={0};Location.distanceBetween(center.getLatitude(),center.getLongitude(),item.getDouble("lat"),item.getDouble("lon"),distance);item.put("distance_m",distance[0]);}return results;}finally{request.connection=null;c.disconnect();}
 }
 static Intent destinationIntent(Context c,JSONObject place)throws Exception{
  double x=place.getDouble("lon"),y=place.getDouble("lat");if(!coordinate(x,y))throw new IOException("Invalid destination");ClassLoader cl=c.getClassLoader();Class<?> point=cl.loadClass("com.skt.tmap.engine.navigation.route.data.MapPoint"),waypoint=cl.loadClass("com.skt.tmap.engine.navigation.route.data.WayPoint"),search=cl.loadClass("com.skt.tmap.engine.navigation.network.RouteSearchData");
  Object wp=waypoint.getConstructor(String.class,point).newInstance(place.getString("name"),point.getConstructor(double.class,double.class).newInstance(x,y));waypoint.getMethod("setPoiId",String.class).invoke(wp,"0");waypoint.getMethod("setAddress",String.class).invoke(wp,place.optString("address",""));Object data=search.getConstructor(waypoint).newInstance(wp);search.getMethod("setCoordType",int.class).invoke(data,3);
  Intent intent=new Intent();intent.setClassName(c.getPackageName(),"com.skt.tmap.activity.TmapRoutePreviewActivity");intent.putExtra("destination",(Serializable)data);intent.putExtra("start_from_current_location",true);intent.putExtra("keep_viadata",false);return intent;
 }
 public static void show(Activity a,String query){if(!a.isFinishing()&&!a.isDestroyed())new Panel(a,query).open();}
 public static void showVoice(Activity a,String query){if(a.isFinishing()||a.isDestroyed())return;Panel p=new Panel(a,query);if(searchCenter(a,false)==null&&searchCenter(a,true)!=null){p.myLocation=true;p.position.setText("내 위치 중심 ▾");}p.open();}
 static JSONArray exactFirst(String query,JSONArray found)throws JSONException{ArrayList<JSONObject> values=new ArrayList<>();for(int i=0;i<found.length();i++)values.add(found.getJSONObject(i));values.sort((a,b)->{boolean ae=PlaceMatch.exact(query,a.optString("name")),be=PlaceMatch.exact(query,b.optString("name"));if(ae!=be)return ae?-1:1;return ae?Double.compare(a.optDouble("distance_m",Double.MAX_VALUE),b.optDouble("distance_m",Double.MAX_VALUE)):0;});JSONArray result=new JSONArray();for(JSONObject value:values)result.put(value);return result;}
 static final class Palette {
  final boolean dark;final int background,surface,text,secondary,blue,line;
  Palette(Context c){dark=(c.getResources().getConfiguration().uiMode&Configuration.UI_MODE_NIGHT_MASK)==Configuration.UI_MODE_NIGHT_YES;background=dark?0xff1f1f1f:Color.WHITE;surface=dark?0xff2c2c2c:0xfff3f4f6;text=dark?0xffeeeeef:0xff202124;secondary=dark?0xffa6a6a8:0xff777b83;blue=dark?0xff5a9cff:0xff1769e8;line=dark?0xff3a3a3c:0xffe9ebee;}
 }
 static GradientDrawable background(int color,int stroke,int radius,Context c){GradientDrawable b=new GradientDrawable();b.setColor(color);b.setCornerRadius(dp(c,radius));if(stroke!=0)b.setStroke(dp(c,1),stroke);return b;}
 static String distanceText(double meters){if(meters<1000)return Math.round(meters)+"m";return meters<10000?String.format(Locale.KOREA,"%.1fkm",meters/1000):Math.round(meters/1000)+"km";}
 static final class Panel {
  final Activity a;final Dialog dialog;final EditText input;final TextView status,scope,position,sort,close;final LinearLayout body,bar,rows;Palette palette;Request request;int generation;boolean myLocation=false,distanceSort=false;JSONArray lastResults=new JSONArray();
  Panel(Activity activity,String query){
   a=activity;palette=new Palette(a);dialog=new Dialog(a);dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
   body=new LinearLayout(a){@Override protected void onConfigurationChanged(Configuration config){super.onConfigurationChanged(config);retheme();}};body.setOrientation(1);body.setPadding(dp(a,18),dp(a,16),dp(a,18),dp(a,12));
   bar=new LinearLayout(a);bar.setGravity(Gravity.CENTER_VERTICAL);bar.setPadding(dp(a,12),0,dp(a,6),0);
   input=new EditText(a);input.setSingleLine(true);input.setTextSize(18);input.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));input.setHint("장소명 또는 주소");input.setText(query);input.setImeOptions(3);input.setBackgroundColor(Color.TRANSPARENT);input.setPadding(0,0,dp(a,4),0);bar.addView(input,new LinearLayout.LayoutParams(0,dp(a,52),1));
   TextView search=text("검색",15);search.setTypeface(Typeface.create("sans-serif-medium",0));search.setGravity(Gravity.CENTER);search.setPadding(dp(a,10),0,dp(a,10),0);search.setOnClickListener(v->search());search.setContentDescription("장소 검색 실행");bar.addView(search,new LinearLayout.LayoutParams(-2,dp(a,48)));
   close=text("×",26);close.setGravity(Gravity.CENTER);close.setContentDescription("닫기");close.setOnClickListener(v->dialog.dismiss());bar.addView(close,new LinearLayout.LayoutParams(dp(a,38),dp(a,48)));body.addView(bar);
   scope=text("장소",16);scope.setTypeface(Typeface.create("sans-serif-medium",0));scope.setPadding(0,dp(a,16),0,dp(a,12));body.addView(scope);
   View divider=new View(a);divider.setTag("separator");body.addView(divider,new LinearLayout.LayoutParams(-1,dp(a,1)));
   LinearLayout options=new LinearLayout(a);options.setGravity(Gravity.CENTER_VERTICAL);position=text("현재 지도 중심 ▾",14);position.setTypeface(Typeface.create("sans-serif-medium",0));position.setPadding(0,0,dp(a,20),0);position.setOnClickListener(v->choosePosition());position.setTag("naver-place-position");sort=text("관련도순 ▾",14);sort.setTypeface(Typeface.create("sans-serif-medium",0));sort.setOnClickListener(v->chooseSort());sort.setTag("naver-place-sort");options.addView(position,new LinearLayout.LayoutParams(-2,dp(a,48)));options.addView(sort,new LinearLayout.LayoutParams(-2,dp(a,48)));body.addView(options);
   status=text("네이버 장소 검색",12);status.setPadding(0,0,0,dp(a,10));body.addView(status);ScrollView scroll=new ScrollView(a);scroll.setFillViewport(false);rows=new LinearLayout(a);rows.setOrientation(1);scroll.addView(rows);body.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
   dialog.setContentView(body);dialog.setOnDismissListener(d->{generation++;if(request!=null)request.cancel();if(VoicePlaceBridge.voicePanel==this)VoicePlaceBridge.voicePanel=null;});input.setOnEditorActionListener((v,id,event)->{if(id==3){search();return true;}return false;});retheme();
  }
  TextView text(String s,int size){TextView t=new TextView(a);t.setText(s);t.setTextSize(size);t.setTypeface(Typeface.create("sans-serif",Typeface.NORMAL));t.setTextColor(palette.text);t.setIncludeFontPadding(false);return t;}
  void retheme(){palette=new Palette(a);body.setBackground(background(palette.background,0,20,a));bar.setBackground(background(palette.surface,0,26,a));input.setTextColor(palette.text);input.setHintTextColor(palette.secondary);close.setTextColor(palette.text);scope.setTextColor(palette.blue);position.setTextColor(palette.text);sort.setTextColor(palette.text);status.setTextColor(palette.secondary);((TextView)bar.getChildAt(1)).setTextColor(palette.blue);View divider=body.findViewWithTag("separator");if(divider!=null)divider.setBackgroundColor(palette.line);rows.removeAllViews();for(int i=0;i<lastResults.length();i++)try{add(lastResults.getJSONObject(i));}catch(JSONException ignored){} }
  void open(){dialog.show();Window w=dialog.getWindow();if(w!=null){w.setBackgroundDrawableResource(android.R.color.transparent);w.setLayout((int)(a.getResources().getDisplayMetrics().widthPixels*.96),(int)(a.getResources().getDisplayMetrics().heightPixels*.78));w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN|WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);}if(!input.getText().toString().trim().isEmpty())search();}
  void openResults(JSONArray found){String query=input.getText().toString();input.setText("");open();input.setText(query);lastResults=found;status.setText("말씀하신 장소 · 네이버 검색 결과 "+found.length()+"곳");for(int i=0;i<found.length();i++)try{add(found.getJSONObject(i));}catch(JSONException ignored){}}
  void choices(String title,String[] values,int selected,java.util.function.IntConsumer changed){ArrayAdapter<String> adapter=new ArrayAdapter<String>(a,android.R.layout.simple_list_item_single_choice,values){@Override public View getView(int i,View recycled,ViewGroup parent){CheckedTextView view=(CheckedTextView)super.getView(i,recycled,parent);view.setTextColor(palette.text);view.setTextSize(16);view.setBackgroundColor(palette.background);view.setCheckMarkTintList(ColorStateList.valueOf(palette.blue));return view;}};AlertDialog picker=new AlertDialog.Builder(a).setTitle(title).setSingleChoiceItems(adapter,selected,(d,index)->{d.dismiss();changed.accept(index);}).setNegativeButton("취소",null).create();picker.show();}
  void choosePosition(){choices("검색 위치 설정",new String[]{"현재 지도 중심","내 위치 중심"},myLocation?1:0,index->{myLocation=index==1;position.setText((myLocation?"내 위치 중심":"현재 지도 중심")+" ▾");if(!input.getText().toString().trim().isEmpty())search();});}
  void chooseSort(){choices("정렬 기준",new String[]{"관련도순","거리순"},distanceSort?1:0,index->{distanceSort=index==1;sort.setText((distanceSort?"거리순":"관련도순")+" ▾");if(!input.getText().toString().trim().isEmpty())search();});}
  void search(){String q=input.getText().toString().trim();if(q.isEmpty()){input.setError("검색어를 입력해 주세요.");return;}if(q.length()>200){input.setError("200자 이하로 입력해 주세요.");return;}if(request!=null)request.cancel();Request next=new Request();request=next;int epoch=++generation;lastResults=new JSONArray();rows.removeAllViews();Location center=searchCenter(a,myLocation);if(center==null){status.setText(myLocation?"내 위치를 확인하지 못했습니다. 위치 설정을 확인해 주세요.":"지도 중심을 확인하지 못했습니다. 지도를 연 뒤 다시 검색해 주세요.");return;}boolean byDistance=distanceSort;status.setText("검색 중…");InputMethodManager keyboard=(InputMethodManager)a.getSystemService(Context.INPUT_METHOD_SERVICE);if(keyboard!=null)keyboard.hideSoftInputFromWindow(input.getWindowToken(),0);Context app=a.getApplicationContext();WORK.execute(()->{try{JSONArray found=query(app,q,next,center,byDistance);MAIN.post(()->{if(epoch!=generation||!dialog.isShowing()||a.isDestroyed())return;lastResults=found;status.setText(found.length()==0?"검색 결과가 없습니다.":"검색 결과 "+found.length()+"곳");for(int i=0;i<found.length();i++)try{add(found.getJSONObject(i));}catch(JSONException ignored){}RouteDiagnostics.event("naver_place_search_complete","count="+found.length()+" sort="+(byDistance?"distance":"accuracy"));});}catch(Exception error){MAIN.post(()->{if(epoch!=generation||!dialog.isShowing())return;status.setText(error instanceof UnknownHostException?"인터넷 연결을 확인한 뒤 다시 검색해 주세요.":error instanceof SocketTimeoutException?"검색 응답이 늦습니다. 다시 검색해 주세요.":"장소 검색에 실패했습니다. 다시 검색해 주세요.");RouteDiagnostics.event("naver_place_search_failed",error.getClass().getSimpleName());});}});}
  void add(JSONObject place)throws JSONException{
   String name=place.getString("name"),category=place.optString("category","");LinearLayout row=new LinearLayout(a);row.setOrientation(1);row.setPadding(0,dp(a,16),0,dp(a,20));row.setContentDescription(name);row.setOnClickListener(v->confirm(place));
   TextView title=text(name,16);title.setTextColor(palette.blue);title.setLineSpacing(dp(a,3),1);if(!category.isEmpty()){SpannableString label=new SpannableString(name+" "+category);label.setSpan(new ForegroundColorSpan(palette.secondary),name.length()+1,label.length(),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);label.setSpan(new RelativeSizeSpan(.80f),name.length()+1,label.length(),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);title.setText(label);}row.addView(title);
   String hours=place.optString("hours","");if(!hours.isEmpty()){TextView state=text(hours,14);state.setTextColor(palette.text);state.setPadding(0,dp(a,8),0,0);row.addView(state);}
   String distance=place.has("distance_m")?distanceText(place.optDouble("distance_m",0)):"",address=place.optString("address","");TextView detail=text(distance+(distance.isEmpty()?"":" · ")+address,14);detail.setTextColor(palette.secondary);detail.setPadding(0,dp(a,8),0,dp(a,15));if(!distance.isEmpty()){SpannableString line=new SpannableString(detail.getText());line.setSpan(new ForegroundColorSpan(palette.text),0,distance.length(),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);line.setSpan(new StyleSpan(Typeface.BOLD),0,distance.length(),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);detail.setText(line);}row.addView(detail);
   TextView route=text("↱ 길찾기",14);route.setGravity(Gravity.CENTER);route.setPadding(dp(a,14),dp(a,8),dp(a,14),dp(a,8));route.setBackground(background(palette.background,palette.line,22,a));route.setOnClickListener(v->confirm(place));row.addView(route,new LinearLayout.LayoutParams(-2,dp(a,36)));rows.addView(row);View line=new View(a);line.setBackgroundColor(palette.line);rows.addView(line,new LinearLayout.LayoutParams(-1,dp(a,1)));
  }
  void confirm(JSONObject place){try{new AlertDialog.Builder(a).setTitle(place.getString("name")).setMessage(place.optString("address")+"\n\n이 장소를 티맵 목적지로 선택할까요?").setNegativeButton("취소",null).setPositiveButton("목적지로 선택",(d,which)->{try{Intent intent=destinationIntent(a,place);dialog.dismiss();a.startActivity(intent);RouteDiagnostics.event("naver_place_selected","coordinate_destination=true");}catch(Exception e){Toast.makeText(a,"목적지를 적용하지 못했습니다.",Toast.LENGTH_LONG).show();}}).show();}catch(JSONException ignored){}}
 }
 private NaverPlaceSearch(){}
}
