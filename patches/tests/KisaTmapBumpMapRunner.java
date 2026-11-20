package com.kisa.tmaprouteprobe;

import android.app.Activity;
import android.app.Instrumentation;
import android.os.Bundle;
import android.os.SystemClock;
import java.lang.reflect.*;

/** Reads the renderer's actual native SDI output without starting navigation or sending C4 data. */
public final class KisaTmapBumpMapRunner extends Instrumentation {
  @Override public void onCreate(Bundle b) { super.onCreate(b); start(); }

  @Override public void onStart() {
    Bundle report = new Bundle();
    try {
      ClassLoader cl = getTargetContext().getClassLoader();
      String engine = "com.skt.tmap.engine.navigation.";
      String helper = "com.kisa.navermerge.";
      Class<?> data = cl.loadClass(engine + "data.RGData");
      Object rg = data.getConstructor().newInstance();
      data.getField("vpPosPointLat").set(rg, 37.6);
      data.getField("vpPosPointLon").set(rg, 126.7);
      Class<?> manager = cl.loadClass(engine + "NavigationManager");
      Object navigation = manager.getMethod("getInstance").invoke(null);
      Method onMap = manager.getDeclaredMethod("getSdiData", data);
      onMap.setAccessible(true);
      Object empty = onMap.invoke(navigation, rg);
      Class<?> feature = cl.loadClass(helper + "TileReader$Feature");
      Object point = feature.getConstructor().newInstance();
      feature.getField("lat").set(point, 37.6005);
      feature.getField("lon").set(point, 126.7);
      Class<?> selection = cl.loadClass(helper + "RoadMatcher$Selection");
      Object chosen = selection.getConstructor().newInstance();
      selection.getField("bump").set(chosen, point);
      selection.getField("bearing").set(chosen, 0.);
      Class<?> bridge = cl.loadClass(helper + "NaverBumpBridge");
      Field current = bridge.getDeclaredField("mapBump");
      current.setAccessible(true);
      Constructor<?> supplement = current.getType().getDeclaredConstructor(selection, double.class, double.class, long.class);
      supplement.setAccessible(true);
      try {
        current.set(null, supplement.newInstance(chosen, 37.6, 126.7, SystemClock.elapsedRealtime()));
        Object icon = onMap.invoke(navigation, rg);
        int type = (int) icon.getClass().getMethod("getSdiType").invoke(icon);
        if (type != 22) throw new AssertionError("Merged coordinate did not select the native bump symbol");
        current.set(null, supplement.newInstance(chosen, 37.6, 126.7, SystemClock.elapsedRealtime()-2100));
        if (onMap.invoke(navigation, rg) != empty) throw new AssertionError("Stale map symbol was retained");
      } finally { current.set(null, null); }
      report.putString("result", "PASS: native type 22 map signal for accepted coordinate; expired bump restores empty slot");
    } catch (Throwable error) { report.putString("error", android.util.Log.getStackTraceString(error)); }
    finish(Activity.RESULT_OK, report);
  }
}
