package com.kisa.transport;

import android.app.Activity;
import android.content.Intent;
import android.os.SystemClock;
import android.util.Log;
import java.io.ByteArrayOutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import org.json.JSONObject;

/** Reconnects a stopped peer only after a user presses the navigation switch. */
final class GuidanceRecovery {
  private static final int TIMEOUT_MS = 35000;
  private static final int RETRY_MS = 900;

  static void check(Activity activity, Intent launch, String peer, String trip, String before, String next) {
    Thread status = new Thread(() -> {
      boolean canRecover = peerStopped(peer, trip);
      GuidanceSwitch.MAIN.post(() -> {
        if (!sameTrip(trip, before)) {
          GuidanceSwitch.finish();
        } else if (!canRecover || activity.isFinishing() || activity.isDestroyed()) {
          GuidanceSwitch.failed(activity, "상대 앱의 안내 연결을 확인해 주세요.");
        } else {
          String survivor = GuidanceSwitch.tmap ? "tmap" : "naver";
          if (!survivor.equals(before) && !GuidanceSwitch.setOwner(survivor)) {
            GuidanceSwitch.failed(activity, "현재 안내를 확인해 주세요.");
            return;
          }
          start(activity, launch, peer, trip, survivor, next);
        }
      });
    }, "KisaRecoveryCheck");
    status.setDaemon(true);
    status.start();
  }

  private static boolean peerStopped(String peer, String trip) {
    boolean tmap = GuidanceSwitch.TMAP.equals(peer);
    try (Socket socket = new Socket()) {
      socket.connect(new InetSocketAddress("127.0.0.1", tmap ? 18316 : 18319), 750);
      socket.setSoTimeout(1000);
      ByteArrayOutputStream bytes = new ByteArrayOutputStream();
      int next;
      while ((next = socket.getInputStream().read()) != -1 && next != '\n') {
        if (bytes.size() >= 4096) return false;
        bytes.write(next);
      }
      JSONObject health = new JSONObject(new String(bytes.toByteArray(), StandardCharsets.UTF_8));
      return tmap && peer.equals(health.optString("package"))
          && trip.equals(health.optString("token")) && "interrupted".equals(health.optString("status"));
    } catch (java.net.ConnectException missing) {
      return true;
    } catch (Exception unreadable) {
      return false;
    }
  }

  private static boolean sameTrip(String trip, String owner) {
    return trip.equals(GuidanceSwitch.token()) && owner.equals(GuidanceSwitch.owner());
  }

  private static void start(Activity activity, Intent launch, String peer, String trip, String before, String next) {
    try {
      Intent restore = GuidanceSwitch.tmap
          ? (Intent) GuidanceSwitch.backend("naverRecoveryIntent", new Class<?>[]{String.class}, trip)
          : new Intent(launch);
      if (restore == null || !GuidanceSwitch.open(activity, restore)) {
        GuidanceSwitch.failed(activity, "상대 앱의 안내를 다시 시작하지 못했습니다.");
        return;
      }
      Log.i("KisaNaviSwitch", "Recovery started for " + next);
      await(activity, launch, peer, trip, before, next, SystemClock.elapsedRealtime() + TIMEOUT_MS);
    } catch (Exception error) {
      Log.w("KisaNaviSwitch", "Could not start recovery", error);
      GuidanceSwitch.failed(activity, "상대 앱의 안내를 다시 시작하지 못했습니다.");
    }
  }

  private static void await(Activity activity, Intent launch, String peer, String trip, String before, String next, long deadline) {
    GuidanceSwitch.MAIN.postDelayed(() -> {
      if (!sameTrip(trip, before)) {
        GuidanceSwitch.finish();
        return;
      }
      if (SystemClock.elapsedRealtime() >= deadline) {
        GuidanceSwitch.failed(activity, "안내 복구가 지연되어 현재 안내를 유지합니다.");
        return;
      }
      GuidanceSwitch.exchange(peer, trip, before, next, accepted -> {
        if (!sameTrip(trip, before)) {
          GuidanceSwitch.finish();
          return;
        }
        if (accepted && GuidanceSwitch.setOwner(next)) {
          // The recovered Tmap preview already opened its native guidance activity.
          if (GuidanceSwitch.NAVER.equals(peer)) GuidanceSwitch.open(activity, launch);
          Log.i("KisaNaviSwitch", "Recovery completed for " + next);
          GuidanceSwitch.finish();
        } else if (GuidanceSwitch.NAVER.equals(peer)) {
          await(activity, launch, peer, trip, before, next, deadline);
        } else {
          Intent request = new Intent(GuidanceSwitch.RECOVER).setPackage(peer).putExtra("trip", trip);
          GuidanceSwitch.send(request, ignored -> await(activity, launch, peer, trip, before, next, deadline));
        }
      });
    }, RETRY_MS);
  }

  private GuidanceRecovery() {}
}
