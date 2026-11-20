package com.kisa.naverroute;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.util.Log;
import android.view.View;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.json.JSONArray;
import org.json.JSONObject;

/** Rebuilds an interrupted native route from the private checkpoint for that trip. */
final class TmapRecovery {
  private static final String EXTRA = "com.kisa.navi.RECOVERY_PREVIEW";
  private static final long MAX_AGE_MS = 12 * 60 * 60 * 1000;
  private static final long ATTEMPT_MS = 26000;
  private static Attempt active;

  private static final class Attempt {
    final String trip;
    final String marker = UUID.randomUUID().toString();
    final JSONObject route;
    final long expires = SystemClock.elapsedRealtime() + ATTEMPT_MS;
    Activity preview;
    boolean submitted;

    Attempt(String trip, JSONObject route) {
      this.trip = trip;
      this.route = route;
    }
  }

  static void save(Context context, String trip, Object option) {
    try {
      JSONObject goal = point(RouteRequest.get(option, "getDestination"));
      JSONArray vias = new JSONArray();
      for (Object via : (List<?>) RouteRequest.get(option, "getWayPoints")) vias.put(point(via));
      JSONObject saved = new JSONObject().put("trip", trip).put("time", System.currentTimeMillis())
          .put("goal", goal).put("vias", vias);
      context.getSharedPreferences("kisa_interrupted_route", 0).edit().putString("route", saved.toString()).apply();
    } catch (Exception error) {
      Log.w("KisaNaviSwitch", "Could not save native recovery route", error);
    }
  }

  private static JSONObject point(Object waypoint) throws Exception {
    double[] coordinate = RouteRequest.coordinate(waypoint);
    return new JSONObject().put("lat", coordinate[1]).put("lon", coordinate[0])
        .put("name", DualNavigation.name(waypoint));
  }

  static boolean open(Context context, String trip) throws Exception {
    if (trip == null || trip.isEmpty() || !trip.equals(RecordingHealth.token)
        || !"interrupted".equals(RecordingHealth.status)) return false;
    if (active != null && trip.equals(active.trip)) return true;
    String stored = context.getSharedPreferences("kisa_interrupted_route", 0).getString("route", "");
    if (stored.isEmpty()) return false;
    JSONObject route = new JSONObject(stored);
    long age = System.currentTimeMillis() - route.getLong("time");
    if (!trip.equals(route.getString("trip")) || age < 0 || age > MAX_AGE_MS) return false;
    Attempt attempt = new Attempt(trip, route);
    Intent intent = NaverPlaceSearch.destinationIntent(context, route.getJSONObject("goal"));
    JSONArray vias = route.getJSONArray("vias");
    ArrayList<Serializable> points = new ArrayList<>();
    for (int i = 0; i < vias.length(); i++) {
      Intent via = NaverPlaceSearch.destinationIntent(context, vias.getJSONObject(i));
      points.add(via.getSerializableExtra("destination"));
    }
    intent.putExtra("via_list", points);
    intent.putExtra(EXTRA, attempt.marker);
    active = attempt;
    try {
      context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    } catch (RuntimeException error) {
      active = null;
      throw error;
    }
    RouteRequest.MAIN.postDelayed(() -> expire(attempt), ATTEMPT_MS);
    return true;
  }

  static boolean owns(PreviewBridge.Session session) throws Exception {
    // The preview can receive another resume or selection after the restored route starts.
    if (session.recoveryPreview) return true;
    if (active == null) return false;
    Object fragment = session.fragment.get();
    if (fragment == null) return false;
    Activity preview = (Activity) RouteRequest.get(fragment, "getActivity");
    if (preview == null || !active.marker.equals(preview.getIntent().getStringExtra(EXTRA))) return false;
    active.preview = preview;
    session.recoveryPreview = true;
    return true;
  }

  static boolean preview(PreviewBridge.Session session) throws Exception {
    Attempt attempt = active;
    Object model = session.model.get();
    if (attempt == null || model == null || attempt.preview == null
        || SystemClock.elapsedRealtime() >= attempt.expires) return false;
    RoutineBridge.pauseCounter(model);
    if (attempt.submitted || !attempt.preview.hasWindowFocus()) return true;
    Object plan = RouteRequest.get(PreviewBridge.field(model, "T"), "getValue");
    for (Object card : (List<?>) RouteRequest.get(PreviewBridge.field(model, "j0"), "getValue")) {
      if (plan != RouteRequest.get(card, "getRoutePlanType")) continue;
      Object route = RouteRequest.get(card, "getRouteResult");
      if (route == null || !matches(attempt, route)) return true;
      Activity preview = attempt.preview;
      View start = preview.findViewById(preview.getResources().getIdentifier(
          "route_preview_drive_button", "id", preview.getPackageName()));
      if (start != null && start.isShown() && start.isEnabled() && start.hasOnClickListeners()) {
        attempt.submitted = true;
        if (!start.performClick()) attempt.submitted = false;
      }
      break;
    }
    return true;
  }

  private static boolean matches(Attempt attempt, Object route) throws Exception {
    Object option = RouteRequest.get(route, "getRouteOption");
    if (!matchesPoint(attempt.route.getJSONObject("goal"), RouteRequest.get(option, "getDestination"))) return false;
    JSONArray expected = attempt.route.getJSONArray("vias");
    List<?> actual = (List<?>) RouteRequest.get(option, "getWayPoints");
    if (actual.size() != expected.length()) return false;
    for (int i = 0; i < actual.size(); i++) {
      if (!matchesPoint(expected.getJSONObject(i), actual.get(i))) return false;
    }
    return true;
  }

  private static boolean matchesPoint(JSONObject expected, Object actual) throws Exception {
    double[] coordinate = RouteRequest.coordinate(actual);
    float[] distance = new float[1];
    android.location.Location.distanceBetween(expected.getDouble("lat"), expected.getDouble("lon"),
        coordinate[1], coordinate[0], distance);
    return distance[0] <= 15;
  }

  static boolean adopt(Object route) throws Exception {
    Attempt attempt = active;
    if (attempt == null || !attempt.submitted || SystemClock.elapsedRealtime() >= attempt.expires
        || !matches(attempt, route)) return false;
    DualNavigation.context = RecordingHealth.context;
    DualNavigation.trip = DualNavigation.key(route);
    DualNavigation.running = true;
    DualNavigation.backgroundRunning = false;
    DualNavigation.armed = null;
    RecordingHealth.start(attempt.trip);
    active = null;
    RouteDiagnostics.event("dual_native_guidance_recovered", "owner=naver until switch is acknowledged");
    return true;
  }

  private static void expire(Attempt attempt) {
    if (active != attempt) return;
    Activity preview = attempt.preview;
    if (preview != null && !preview.isFinishing()) {
      if (preview.hasWindowFocus()) preview.moveTaskToBack(true);
      preview.finish();
    }
    active = null;
  }

  private TmapRecovery() {}
}
