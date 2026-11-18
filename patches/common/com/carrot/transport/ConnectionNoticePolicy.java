package com.carrot.transport;
/** One short notice per outage; four stable seconds of recovery arm the next notice. */
public final class ConnectionNoticePolicy {
 String trip="";boolean announced;long healthyAt=-1;public long until;
 public boolean update(String key,String message,long now){if(!key.equals(trip)){trip=key;announced=false;healthyAt=-1;until=0;}if(message.isEmpty()){if(healthyAt<0)healthyAt=now;if(now-healthyAt>=4000){announced=false;until=0;}return false;}healthyAt=-1;if(announced)return false;announced=true;until=now+5000;return true;}
 public void dismiss(){until=0;}
 public boolean visible(long now){return now<until;}
}
