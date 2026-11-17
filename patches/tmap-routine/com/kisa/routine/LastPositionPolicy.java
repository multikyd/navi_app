package com.kisa.routine;

/** Only destination prediction may use a cached position; this is not a GPS fix. */
public final class LastPositionPolicy {
 public static final long MAX_AGE_MS=72L*60*60*1000;
 public static boolean usable(boolean present,boolean accuracyKnown,double accuracy,long ageMs,
                              boolean mock,boolean coordinateValid){
  return present&&accuracyKnown&&Double.isFinite(accuracy)&&accuracy>=0&&accuracy<=60&&
         ageMs>=0&&ageMs<=MAX_AGE_MS&&!mock&&coordinateValid;
 }
 private LastPositionPolicy(){}
}
