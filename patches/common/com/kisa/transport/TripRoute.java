package com.kisa.transport;

import android.location.Location;
import org.json.JSONArray;
import org.json.JSONObject;

/** Destination and ordered stops shared only through the signed switch channel. */
public final class TripRoute {
  public static JSONObject point(double lat, double lon, String name) throws Exception {
    if (!Double.isFinite(lat) || !Double.isFinite(lon) || Math.abs(lat) > 90 || Math.abs(lon) > 180)
      throw new IllegalArgumentException("Invalid route coordinate");
    return new JSONObject().put("lat", lat).put("lon", lon).put("name", name);
  }

  public static boolean same(JSONObject a, JSONObject b) {
    try {
      if (!near(a.getJSONObject("goal"), b.getJSONObject("goal"))) return false;
      JSONArray x = a.getJSONArray("vias"), y = b.getJSONArray("vias");
      if (x.length() != y.length()) return false;
      for (int i = 0; i < x.length(); i++)
        if (!near(x.getJSONObject(i), y.getJSONObject(i))) return false;
      return true;
    } catch (Exception invalid) {
      return false;
    }
  }

  private static boolean near(JSONObject a, JSONObject b) throws Exception {
    float[] meters = new float[1];
    Location.distanceBetween(a.getDouble("lat"), a.getDouble("lon"),
        b.getDouble("lat"), b.getDouble("lon"), meters);
    return meters[0] <= 25;
  }

  private TripRoute() {}
}
