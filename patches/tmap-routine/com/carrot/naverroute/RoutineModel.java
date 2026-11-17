package com.carrot.naverroute;
import java.util.*;
/** Local adaptive destination ranking: user priors plus time/location kernels from completed trips. */
public final class RoutineModel {
 public static final class Place {public final String name;public final double lon,lat;public Place(String n,double x,double y){name=n;lon=x;lat=y;}}
 public static final class Visit {public final Place from,to;public final long when;public final double weight;public Visit(Place f,Place t,long w,double v){from=f;to=t;when=w;weight=v;}}
 public static final class Choice {public final Place from,to;public final double confidence;public final int evidence;Choice(Place f,Place t,double p,int n){from=f;to=t;confidence=p;evidence=n;}}
 public final List<Visit> visits=new ArrayList<>(),rejections=new ArrayList<>();public Place home,office,sport;
 static Calendar date(long time){Calendar c=Calendar.getInstance(TimeZone.getTimeZone("Asia/Seoul"));c.setTimeInMillis(time);return c;}
 static int minute(long time){Calendar c=date(time);return c.get(Calendar.HOUR_OF_DAY)*60+c.get(Calendar.MINUTE);}
 static int day(long time){return date(time).get(Calendar.DAY_OF_WEEK);}
 static boolean weekday(long time){return day(time)!=1&&day(time)!=7;}
 static boolean sportDay(long time){return day(time)==Calendar.TUESDAY||day(time)==Calendar.THURSDAY;}
 public static double distance(Place a,Place b){double x=Math.toRadians(a.lat),y=Math.toRadians(b.lat),d=Math.toRadians(b.lat-a.lat),e=Math.toRadians(b.lon-a.lon),v=Math.sin(d/2)*Math.sin(d/2)+Math.cos(x)*Math.cos(y)*Math.sin(e/2)*Math.sin(e/2);return 6371000*2*Math.asin(Math.sqrt(Math.min(1,v)));}
 static boolean near(Place a,Place b,double meters){return a!=null&&b!=null&&distance(a,b)<=meters;}
 static double timeKernel(int a,int b,double sigma){int d=Math.abs(a-b);d=Math.min(d,1440-d);return Math.exp(-.5*d*d/(sigma*sigma));}
 boolean commute(Place from,Place to){return near(from,home,650)&&near(to,office,450)||near(from,office,650)&&near(to,home,450);}
 boolean badminton(Place from,Place to){return near(from,home,650)&&near(to,sport,450)||near(from,sport,650)&&near(to,home,450);}
 double dayWeight(Visit v,long now){if(commute(v.from,v.to))return weekday(now)&&weekday(v.when)?1:0;if(badminton(v.from,v.to))return sportDay(now)&&sportDay(v.when)?1:0;return day(now)==day(v.when)?1:weekday(now)&&weekday(v.when)?.3:0;}

 double prior(Place from,Place to,long now){int t=minute(now);if(near(from,home,650)&&near(to,office,450))return .94*timeKernel(t,8*60+20,75);if(near(from,office,650)&&near(to,home,450))return .94*timeKernel(t,18*60,105);if(sportDay(now)&&near(from,home,650)&&near(to,sport,450))return .94*timeKernel(t,18*60+45,65);if(sportDay(now)&&near(from,sport,650)&&near(to,home,450))return .94*timeKernel(t,21*60+45,65);return 0;}
 Place label(Place p){for(Place known:new Place[]{home,office,sport})if(near(p,known,350))return known;return p;}
 public Choice predict(Place current,long now){
  if(current==null||!weekday(now))return null;List<Place> candidates=new ArrayList<>();for(Place p:new Place[]{home,office,sport})if(p!=null)candidates.add(p);for(Visit v:visits){boolean found=false;for(Place p:candidates)if(near(p,v.to,300))found=true;if(!found)candidates.add(v.to);}
  Choice best=null;double runner=0;
  for(Place target:candidates){if(near(current,target,700))continue;double base=prior(current,target,now),positive=0,negative=0;int count=0;List<Visit> cluster=new ArrayList<>();for(Visit v:visits)if(near(current,v.from,650)&&near(target,v.to,350)&&dayWeight(v,now)>0&&now>=v.when){cluster.add(v);count++;}
   double sigma=45;if(cluster.size()>1){double spread=0;for(Visit a:cluster)for(Visit b:cluster){int d=Math.abs(minute(a.when)-minute(b.when));d=Math.min(d,1440-d);spread+=d*d;}sigma=Math.max(45,Math.min(110,Math.sqrt(spread/(cluster.size()*cluster.size()))+30));}
   for(Visit v:cluster){double spatial=Math.exp(-.5*Math.pow(distance(current,v.from)/400,2));positive+=v.weight*dayWeight(v,now)*spatial*timeKernel(minute(now),minute(v.when),sigma)*Math.exp(-(now-v.when)/(90.*86400000));}
   for(Visit v:rejections)if(near(current,v.from,650)&&near(target,v.to,350)&&dayWeight(v,now)>0&&now>=v.when)negative+=v.weight*(day(now)==day(v.when)?1:.05)*timeKernel(minute(now),minute(v.when),65)*Math.exp(-(now-v.when)/(3.*86400000));
   double learned=.94*(1-Math.exp(-positive*1.8)),score=(1-(1-base)*(1-learned))*Math.exp(-negative*1.5);if(best==null||score>best.confidence){if(best!=null)runner=best.confidence;best=new Choice(current,label(target),score,count);}else runner=Math.max(runner,score);
  }
  return best!=null&&best.confidence>=.70&&best.confidence-runner>=.12?best:null;
 }
 public boolean add(Visit visit,boolean negative){List<Visit> target=negative?rejections:visits;for(Visit old:target)if(Math.abs(old.when-visit.when)<600000&&near(old.from,visit.from,350)&&near(old.to,visit.to,350))return false;target.add(visit);while(target.size()>(negative?60:200))target.remove(0);return true;}
}
