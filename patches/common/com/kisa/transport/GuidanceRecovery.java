package com.kisa.transport;

import android.app.Activity;
import android.content.Intent;
import android.os.SystemClock;
import org.json.JSONObject;

final class GuidanceRecovery {
  static void sync(Activity activity, Intent launch, String peer, String trip, String next) {
    if (!GuidanceSwitch.tmap) {
      preview(activity, launch, trip, -1, true, false);
      return;
    }
    JSONObject route = GuidanceSwitch.route();
    try {
      Intent restore = (Intent) GuidanceSwitch.backend("naverRecoveryIntent", new Class<?>[]{String.class}, trip);
      if (route != null && restore != null && GuidanceSwitch.setOwner("tmap") && GuidanceSwitch.open(activity, restore)) {
        await(activity, launch, peer, trip, route, next, SystemClock.elapsedRealtime() + 30000);
        return;
      }
    } catch (Exception e) {
      android.util.Log.w("KisaNaviSwitch", "Could not align Naver route", e);
    }
    GuidanceSwitch.failed(activity, "상대 앱에서 현재 목적지를 열지 못했습니다.");
  }

  static void preview(Activity activity, Intent launch, String trip, double seconds, boolean manual, boolean awakened) {
    JSONObject route = GuidanceSwitch.route();
    if (route == null || !trip.equals(GuidanceSwitch.token())) {
      GuidanceSwitch.finish();
      return;
    }
    GuidanceSwitch.peerPreview(trip, route, seconds, awakened, (accepted, intent) -> {
      if (!current(trip, route)) {
        GuidanceSwitch.finish();
      } else if (accepted && (awakened || intent != null && GuidanceSwitch.open(activity, intent))) {
        GuidanceSwitch.finish();
      } else if (!awakened && GuidanceSwitch.open(activity, launch)) {
        GuidanceSwitch.MAIN.postDelayed(() -> preview(activity, launch, trip, seconds, manual, true), 2000);
      } else if (manual) {
        GuidanceSwitch.failed(activity, "티맵에서 현재 목적지를 열지 못했습니다.");
      } else {
        GuidanceSwitch.finish();
      }
    });
  }

  private static boolean current(String trip, JSONObject route) {
    return trip.equals(GuidanceSwitch.token()) && TripRoute.same(route, GuidanceSwitch.route());
  }

  private static void await(Activity activity, Intent launch, String peer, String trip, JSONObject route, String next, long deadline) {
    GuidanceSwitch.MAIN.postDelayed(() -> {
      if (!current(trip, route)) {
        GuidanceSwitch.finish();
      } else if (SystemClock.elapsedRealtime() >= deadline) {
        GuidanceSwitch.failed(activity, "경로 연결이 지연되어 현재 안내를 유지합니다.");
      } else {
        GuidanceSwitch.exchange(peer, trip, "tmap", next, accepted -> {
          if (!current(trip, route)) {
            GuidanceSwitch.finish();
          } else if (accepted && GuidanceSwitch.setOwner(next)) {
            GuidanceSwitch.open(activity, launch);
            GuidanceSwitch.finish();
          } else {
            await(activity, launch, peer, trip, route, next, deadline);
          }
        });
      }
    }, 900);
  }

  private GuidanceRecovery() {}
}
