package com.carrot.naver;

import android.app.Activity;
import android.os.*;
import android.util.Log;
import java.lang.ref.WeakReference;
import java.util.*;

/** Transient map pins only for additional bumps accepted by the active app's merge. */
public final class MergedBumpMarkers {
  private static final Handler MAIN = new Handler(Looper.getMainLooper());
  private static final Map<String,Object> markers = new HashMap<>();
  private static WeakReference<Activity> activity = new WeakReference<>(null);
  private static WeakReference<Object> naverMap = new WeakReference<>(null);
  private static Object boundMap;
  private static boolean loggedError;
  private static final Runnable EXPIRE = () -> update(Collections.emptyList());

  public static void resume(Activity screen) {
    if (!screen.getClass().getName().equals("com.naver.map.MainActivity")) return;
    activity = new WeakReference<>(screen);
  }

  public static void pause(Activity screen) {
    if (activity.get() != screen) return;
    activity.clear();
    MAIN.removeCallbacks(EXPIRE);
    update(Collections.emptyList());
  }

  public static void attachNaverMap(Object map) {
    naverMap = new WeakReference<>(map);
  }

  public static void publish(List<double[]> points) {
    MAIN.post(() -> {
      update(points);
      MAIN.removeCallbacks(EXPIRE);
      if (!points.isEmpty()) MAIN.postDelayed(EXPIRE, 2500);
    });
  }

  private static void remove(Object marker) throws ReflectiveOperationException {
    Class<?> type = marker.getClass().getClassLoader().loadClass("com.naver.maps.map.NaverMap");
    marker.getClass().getMethod("o", type).invoke(marker, (Object) null);
  }

  private static Object add(Activity activity, Object map, double lat, double lon) throws ReflectiveOperationException {
    int id = activity.getResources().getIdentifier("mapds_navigation_route_pole_speed_bump_s",
        "drawable", activity.getPackageName());
    if (id == 0) return null;
    ClassLoader loader = map.getClass().getClassLoader();
    Class<?> point = loader.loadClass("com.naver.maps.geometry.LatLng");
    Class<?> markerType = loader.loadClass("com.naver.maps.map.overlay.Marker");
    Class<?> image = loader.loadClass("com.naver.maps.map.overlay.OverlayImage");
    Object position = point.getConstructor(double.class, double.class).newInstance(lat, lon);
    Object symbol = image.getMethod("f", int.class).invoke(null, id);
    Object marker = markerType.getConstructor(point, image).newInstance(position, symbol);
    markerType.getMethod("o", loader.loadClass("com.naver.maps.map.NaverMap")).invoke(marker, map);
    return marker;
  }

  private static void update(List<double[]> points) {
    try {
      Activity a = activity.get();
      Object map = a == null || points.isEmpty() ? null : naverMap.get();
      Map<String,double[]> desired = new LinkedHashMap<>();
      if (map != null) {
        for (double[] p : points) {
          if (p[0] >= 33 && p[0] <= 39 && p[1] >= 124 && p[1] <= 132) {
            desired.put("kisa-merged-bump-" + Math.round(p[0]*100000) + "-" + Math.round(p[1]*100000), p);
          }
        }
      }
      Iterator<Map.Entry<String,Object>> existing = markers.entrySet().iterator();
      while (existing.hasNext()) {
        Map.Entry<String,Object> entry = existing.next();
        if (map == boundMap && desired.containsKey(entry.getKey())) continue;
        try { remove(entry.getValue()); } finally { existing.remove(); }
      }
      boundMap = map;
      for (Map.Entry<String,double[]> entry : desired.entrySet()) {
        if (markers.containsKey(entry.getKey())) continue;
        double[] p = entry.getValue();
        Object marker = add(a, map, p[0], p[1]);
        if (marker != null) markers.put(entry.getKey(), marker);
      }
    } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
      if (!loggedError) Log.w("KisaMergedBump", "Map pin unavailable", error);
      loggedError = true;
    }
  }

  private MergedBumpMarkers() {}
}
