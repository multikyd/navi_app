package com.kisa.naver;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import com.kisa.transport.TripRoute;
import org.json.JSONArray;
import org.json.JSONObject;

/** Recent destinations stay on the phone and are opened only after selection. */
final class DestinationSuggestions {
  private static final String TAG = "kisa-destination-suggestions";

  private static android.content.SharedPreferences store(Context context) {
    return context.getSharedPreferences(TAG, Context.MODE_PRIVATE);
  }

  private static JSONArray recent(Context context) {
    try {
      return new JSONArray(store(context).getString("recent", "[]"));
    } catch (Exception invalid) {
      return new JSONArray();
    }
  }

  static void remember(Context context, JSONObject goal) {
    try {
      JSONArray previous = recent(context), next = new JSONArray().put(goal);
      JSONObject newest = new JSONObject().put("goal", goal).put("vias", new JSONArray());
      for (int i = 0; i < previous.length() && next.length() < 3; i++) {
        JSONObject place = previous.getJSONObject(i);
        JSONObject route = new JSONObject().put("goal", place).put("vias", new JSONArray());
        if (!TripRoute.same(newest, route)) next.put(place);
      }
      store(context).edit().putString("recent", next.toString()).apply();
    } catch (Exception ignored) {}
  }

  static void refresh(Activity activity) {
    if (activity == null || !activity.getClass().getName().equals("com.naver.map.MainActivity")) return;
    FrameLayout root = activity.findViewById(android.R.id.content);
    if (root == null) return;
    View old = root.findViewWithTag(TAG);
    if (old != null) root.removeView(old);
    if (CompanionMode.activeRoute() != null || recent(activity).length() == 0) return;
    Button action = new Button(activity);
    action.setTag(TAG);
    action.setText("목적지 제안");
    action.setOnClickListener(view -> {
      if (CompanionMode.activeRoute() == null) choose(activity);
      else refresh(activity);
    });
    FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
    params.bottomMargin = Math.round(96 * activity.getResources().getDisplayMetrics().density);
    root.addView(action, params);
  }

  private static void choose(Activity activity) {
    JSONArray rows = recent(activity);
    String[] labels = new String[rows.length()];
    for (int i = 0; i < labels.length; i++) {
      String label = rows.optJSONObject(i).optString("name");
      labels[i] = label.isEmpty() ? "최근 목적지 " + (i + 1) : label;
    }
    new AlertDialog.Builder(activity).setTitle("최근 목적지 제안").setItems(labels, (dialog, i) -> {
      JSONObject place = rows.optJSONObject(i);
      Uri uri = new Uri.Builder().scheme("nmap").authority("navigation")
          .appendQueryParameter("dlat", place.optString("lat"))
          .appendQueryParameter("dlng", place.optString("lon"))
          .appendQueryParameter("dname", labels[i])
          .appendQueryParameter("appname", activity.getPackageName())
          .appendQueryParameter("autostart", "false").build();
      activity.startActivity(new Intent(Intent.ACTION_VIEW, uri).setPackage(activity.getPackageName())
          .addCategory(Intent.CATEGORY_BROWSABLE));
    }).setNegativeButton("닫기", null).show();
  }

  private DestinationSuggestions() {}
}
