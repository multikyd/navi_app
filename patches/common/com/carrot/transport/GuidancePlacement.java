package com.carrot.transport;
import android.graphics.Rect;import android.view.*;import android.widget.*;import java.util.*;
/** Places additional UI in free map space; hides it if every candidate covers native guidance. */
public final class GuidancePlacement {
 static int dp(View v,int n){return (int)(v.getResources().getDisplayMetrics().density*n+.5f);}
 public static int id(View root,String name){for(String pkg:new String[]{root.getContext().getPackageName(),"com.naver.map","com.skt.tmap.ku"}){int found=root.getResources().getIdentifier(name,"id",pkg);if(found!=0)return found;}return 0;}
 public static Rect nativeSignal(View root){int[] origin=new int[2];root.getLocationOnScreen(origin);for(String name:new String[]{"traffic_signal_view","v_traffic_light","v_traffic_signal","v_signal_info"}){int id=id(root,name);View v=id==0?null:root.findViewById(id);if(v!=null&&v.isShown()&&v.getWidth()>0&&v.getHeight()>0&&v.getHeight()<root.getHeight()/3){int[] at=new int[2];v.getLocationOnScreen(at);return new Rect(at[0]-origin[0],at[1]-origin[1],at[0]-origin[0]+v.getWidth(),at[1]-origin[1]+v.getHeight());}}return ComposeRegions.signal(root);}
 static boolean free(Rect candidate,List<Rect> occupied,int width,int height,int gap){if(candidate.left<gap||candidate.right>width-gap||candidate.top<gap||candidate.bottom>height-gap)return false;for(Rect r:occupied)if(Rect.intersects(candidate,r))return false;return true;}
 static Integer naverSearchCenter(View root){
  Rect best=null;
  for(ComposeRegions.Region region:ComposeRegions.get(root)){
   Rect bounds=region.bounds;
   if(bounds.width()>dp(root,88)||bounds.height()>dp(root,88)
       ||bounds.centerX()<root.getWidth()*.75
       ||!region.description.contains("ContentDescription 검색 "))continue;
   if(best==null||bounds.top<best.top)best=bounds;
  }
  return best==null?null:best.centerX();
 }
 static void position(View extra,int x,int y){FrameLayout.LayoutParams p=(FrameLayout.LayoutParams)extra.getLayoutParams();if(p.gravity!=(Gravity.TOP|Gravity.LEFT)||p.leftMargin!=x||p.topMargin!=y){p.gravity=Gravity.TOP|Gravity.LEFT;p.leftMargin=x;p.topMargin=y;extra.setLayoutParams(p);}}
 static void obstacles(View v,View extra,List<Rect> out,int[] origin){if(v==extra||!v.isShown())return;String id="";try{id=v.getResources().getResourceEntryName(v.getId()).toLowerCase(Locale.ROOT);}catch(Exception ignored){}
  boolean protectedName=id.matches(".*(speed|traffic|signal|lane|tbt|first_bg|second|guide_distance|turn|crossroad|safety|camera|limit_sign).*"),added=v.getTag()!=null&&String.valueOf(v.getTag()).matches(".*(signal|guidance-switch|naver-place).*"),leaf=!(v instanceof ViewGroup);
  boolean largeGroup=!leaf&&v.getWidth()>extra.getRootView().getWidth()*.75&&v.getHeight()>extra.getRootView().getHeight()*.25;
  if((protectedName||added||v.isClickable())&&!largeGroup&&v.getWidth()>0&&v.getHeight()>0&&v.getWidth()*v.getHeight()<extra.getRootView().getWidth()*extra.getRootView().getHeight()*.65){int[] at=new int[2];v.getLocationOnScreen(at);Rect r=new Rect(at[0]-origin[0],at[1]-origin[1],at[0]-origin[0]+v.getWidth(),at[1]-origin[1]+v.getHeight());r.inset(-dp(v,6),-dp(v,6));out.add(r);if(protectedName)return;}
  if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++)obstacles(((ViewGroup)v).getChildAt(i),extra,out,origin);
 }
 public static void place(FrameLayout root,View extra,boolean left){if(root.getWidth()==0||root.getHeight()==0)return;int[] origin=new int[2];root.getLocationOnScreen(origin);List<Rect> occupied=new ArrayList<>();obstacles(root,extra,occupied,origin);int gap=dp(root,12),w=extra.getMeasuredWidth(),h=extra.getMeasuredHeight();if(w<=0||h<=0)return;int first=Math.max(dp(root,180),(int)(root.getHeight()*.38));
  for(ComposeRegions.Region region:ComposeRegions.get(root)){Rect r=region.bounds;if(!(region.clickable||region.description.contains("Text")||region.description.contains("속도")||region.description.contains("km/h")||region.description.contains("안전운전")||region.description.contains("카메라")||region.description.contains("단속")||region.description.contains("표지판"))||r.width()<=0||r.height()<=0||r.width()*r.height()>root.getWidth()*root.getHeight()*.4)continue;Rect padded=new Rect(r);padded.inset(-dp(root,6),-dp(root,6));occupied.add(padded);}
  if(String.valueOf(extra.getTag()).contains("signal")){Rect anchor=nativeSignal(root);if(anchor!=null){
   if("kisa-direction-signal".equals(extra.getTag())||"kisa-naver-signal".equals(extra.getTag())){
    int x=Math.max(0,anchor.left);
    for(int top=anchor.bottom+gap;top<=anchor.bottom+gap*3;top+=gap){
     Rect below=new Rect(x,top,x+w,top+h);
     if(free(below,occupied,root.getWidth(),root.getHeight(),0)){position(extra,x,top);return;}
    }
    extra.setVisibility(View.GONE);
    return;
   }
   for(int x:new int[]{root.getWidth()-w-gap,anchor.right+gap}){if(x<anchor.right+gap)continue;Rect candidate=new Rect(x,anchor.top,x+w,anchor.top+h);if(free(candidate,occupied,root.getWidth(),root.getHeight(),gap)){position(extra,candidate.left,candidate.top);return;}}}left="kisa-naver-signal".equals(extra.getTag());}
  if(String.valueOf(extra.getTag()).contains("signal")){first=dp(root,180);for(int id:new int[]{id(root,"v_second"),id(root,"v_first_bg"),0x7f0a0fb0,0x7f0a06ce}){View anchor=id==0?null:root.findViewById(id);if(anchor!=null&&anchor.isShown()&&anchor.getHeight()>0&&anchor.getHeight()<root.getHeight()/2){int[] pos=new int[2];anchor.getLocationOnScreen(pos);first=pos[1]+anchor.getHeight()-origin[1]+gap;break;}}}
  boolean guidanceSwitch="kisa-guidance-switch".equals(extra.getTag());
  int sides=guidanceSwitch||"kisa-naver-signal".equals(extra.getTag())?1:2;
  for(int side=0;side<sides;side++){
   boolean atLeft=!guidanceSwitch&&(side==0?left:!left);
   int x=atLeft?gap:root.getWidth()-w-gap;
   if(guidanceSwitch){
    int searchId=id(root,"search_button");View search=searchId==0?null:root.findViewById(searchId);
    Integer center=null;
    if(search!=null&&search.isShown()){
     int[] at=new int[2];search.getLocationOnScreen(at);
     center=at[0]-origin[0]+search.getWidth()/2;
    }else if(root.getContext().getPackageName().equals("com.nhn.android.nmap")){
     center=naverSearchCenter(root);
    }
    if(center!=null&&center>=root.getWidth()/2)x=Math.min(root.getWidth()-w,center-w/2);
   }
   for(int y=first;y+h<root.getHeight()-dp(root,130);y+=dp(root,24)){
    Rect candidate=new Rect(x,y,x+w,y+h);
    if(free(candidate,occupied,root.getWidth(),root.getHeight(),0)){position(extra,x,y);return;}
   }
  }
  extra.setVisibility(View.GONE);
 }
 private GuidancePlacement(){}
}
