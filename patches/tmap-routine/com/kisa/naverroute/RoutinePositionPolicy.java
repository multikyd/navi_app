package com.kisa.naverroute;
/** Pure position-readiness policy; never requests or substitutes a GPS fix. */
public final class RoutinePositionPolicy {
 public static String reason(boolean present,boolean accuracyKnown,double accuracy,long ageMs,boolean mock,boolean coordinateValid){
  if(!present)return "gps_missing";
  if(mock)return "gps_mock";
  if(!accuracyKnown)return "gps_accuracy_missing";
  if(!Double.isFinite(accuracy)||accuracy<0||accuracy>60)return "gps_accuracy_poor";
  if(ageMs<0)return "gps_timestamp_invalid";
  if(ageMs>15000)return "gps_stale";
  if(!coordinateValid)return "gps_coordinate_invalid";
  return "ready";
 }
 /** Pauses remaining countdown across missing-position intervals, including the recovery edge. */
 public static final class Countdown {
  long remaining,last;boolean wasReady;
  public void start(long now,long duration){remaining=duration;last=now;wasReady=true;}
  public long update(long now,boolean ready){long delta=Math.max(0,now-last);if(ready&&wasReady)remaining=Math.max(0,remaining-delta);last=now;wasReady=ready;return remaining;}
 }
 private RoutinePositionPolicy(){}
}
