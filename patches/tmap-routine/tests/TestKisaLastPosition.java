import com.kisa.routine.LastPositionPolicy;
import com.kisa.naverroute.RoutinePositionPolicy;
import com.kisa.naverroute.RoutineModel;
import java.util.*;

public final class TestKisaLastPosition {
 static int checks;
 static void check(boolean value,String label){if(!value)throw new AssertionError(label);checks++;}
 static boolean cached(long age){return LastPositionPolicy.usable(true,true,15,age,false,true);}
 public static void main(String[] args){
  long overnight=15L*60*60*1000;
  check(cached(overnight),"overnight garage position is usable for prediction");
  check(!RoutinePositionPolicy.reason(true,true,15,overnight,false,true).equals("ready"),"cached position never becomes a live GPS fix");
  check(cached(LastPositionPolicy.MAX_AGE_MS),"72-hour boundary accepted");
  check(!cached(LastPositionPolicy.MAX_AGE_MS+1),"expired position rejected");
  check(!cached(-1),"future timestamp rejected");
  check(!LastPositionPolicy.usable(false,true,15,0,false,true),"no position remains unavailable");
  check(!LastPositionPolicy.usable(true,true,15,0,true,true),"mock position rejected");
  check(!LastPositionPolicy.usable(true,true,61,0,false,true),"poor accuracy rejected");
  check(!LastPositionPolicy.usable(true,false,0,0,false,true),"unknown accuracy rejected");
  check(!LastPositionPolicy.usable(true,true,Double.NaN,0,false,true),"invalid accuracy rejected");
  check(!LastPositionPolicy.usable(true,true,15,0,false,false),"invalid coordinate rejected");
  RoutinePositionPolicy.Countdown countdown=new RoutinePositionPolicy.Countdown();
  countdown.start(1000,5000);
  check(countdown.update(6000,cached(overnight))==0,"cached position lets the proposal countdown finish without GPS");
  RoutineModel model=new RoutineModel();
  model.home=new RoutineModel.Place("home",127.,37.);
  model.office=new RoutineModel.Place("office",127.1,37.1);
  Calendar now=Calendar.getInstance(TimeZone.getTimeZone("Asia/Seoul"));
  now.clear();now.set(2026,Calendar.NOVEMBER,17,8,20);
  RoutineModel.Choice choice=model.predict(model.home,now.getTimeInMillis());
  check(choice!=null&&choice.to==model.office,"cached home origin preserves morning destination ranking");
  now.set(Calendar.HOUR_OF_DAY,18);now.set(Calendar.MINUTE,0);
  choice=model.predict(model.office,now.getTimeInMillis());
  check(choice!=null&&choice.to==model.home,"cached office origin preserves evening destination ranking");
  System.out.println("PASS "+checks+" cached-position and routine scenarios");
 }
}
