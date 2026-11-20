package com.kisa.tmaprouteprobe;

import android.app.Activity;
import android.app.Instrumentation;
import android.os.Bundle;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/** Checks the actual patched Naver slot getters and installed native bump resource. */
public final class KisaNaverSafetyLayoutRunner extends Instrumentation {
  @Override public void onCreate(Bundle b) { super.onCreate(b); start(); }

  @Override public void onStart() {
    Bundle report = new Bundle();
    runOnMainSync(() -> {
      try {
        ClassLoader cl = getTargetContext().getClassLoader();
        String base = "com.naver.map.feature.navigation.renewal.component.compose.layout.";
        Class<?> slot = cl.loadClass(base + "SlotPlacement");
        Class<?> alignment = cl.loadClass("androidx.compose.ui.Alignment");
        Object align = Proxy.newProxyInstance(cl, new Class<?>[]{alignment}, (obj, m, args) -> 0L);
        Object sample = null;
        for (Constructor<?> ctor : slot.getConstructors()) {
          if (ctor.getParameterCount() == 9) sample = ctor.newInstance(true, align, null, null, null, null, true, 124, null);
        }
        if (sample == null) throw new AssertionError("Slot fixture missing");
        Class<?> gap = cl.loadClass("com.kisa.naver.SignalLayoutGap");
        Method set = gap.getDeclaredMethod("setHeight", float.class);
        set.setAccessible(true);
        Method padding = slot.getMethod("n");
        Method top = padding.getReturnType().getMethod("a");
        float original = (float) top.invoke(padding.invoke(sample));
        try {
          for (String mode : new String[]{"RouteGuidance", "SafeDriving"}) {
            Class<?> layout = cl.loadClass("com.naver.map.feature.navigation.renewal.rg.compose." + mode + "Placements");
            Object fixture = null;
            for (Constructor<?> ctor : layout.getConstructors()) {
              Class<?>[] types = ctor.getParameterTypes();
              Object[] values = new Object[types.length];
              boolean supported = true;
              for (int i = 0; i < types.length; i++) {
                if (types[i] == slot) values[i] = sample;
                else if (types[i] == float.class) values[i] = 0f;
                else if (types[i] == boolean.class) values[i] = false;
                else supported = false;
              }
              if (supported) { fixture = ctor.newInstance(values); break; }
            }
            if (fixture == null) throw new AssertionError("Missing layout fixture for " + mode);
            Method getter = layout.getMethod(mode.equals("SafeDriving") ? "p" : "G");
            set.invoke(null, 72f);
            float moved = (float) top.invoke(padding.invoke(getter.invoke(fixture)));
            if (Math.abs(moved - original - 72f) > .01f) throw new AssertionError(mode + " safety slot did not move");
            set.invoke(null, 0f);
            if (getter.invoke(fixture) != sample) throw new AssertionError(mode + " did not restore native spacing");
          }
        } finally { set.invoke(null, 0f); }
        int icon = getTargetContext().getResources().getIdentifier("mapds_navigation_route_pole_speed_bump_s",
            "drawable", getTargetContext().getPackageName());
        if (icon == 0) throw new AssertionError("Native bump icon missing");
        report.putString("result", "PASS: native route/safe safety slots reserve and release space; native bump icon exists");
      } catch (Throwable error) { report.putString("error", android.util.Log.getStackTraceString(error)); }
    });
    finish(Activity.RESULT_OK, report);
  }
}
