package com.carrot.naver;
import android.app.Application;import android.os.*;import java.net.*;
/** Independent app/UI heartbeat; no location coordinates are exposed. */
public final class NaverHealth {
 static volatile long mainAt;static boolean started;static final Handler MAIN=new Handler(Looper.getMainLooper());
 public static synchronized void init(Application app){if(started)return;started=true;MAIN.post(new Runnable(){public void run(){mainAt=SystemClock.elapsedRealtime();MAIN.postDelayed(this,2000);}});Thread thread=new Thread(()->{while(true){try(ServerSocket server=new ServerSocket()){server.setReuseAddress(true);server.bind(new InetSocketAddress(InetAddress.getByName("127.0.0.1"),18319),2);while(true)try(Socket socket=server.accept()){socket.setSoTimeout(1000);socket.getOutputStream().write((NaverBridge.health().toString()+"\n").getBytes("UTF-8"));socket.getOutputStream().flush();}catch(Exception ignored){}}catch(Exception e){try{Thread.sleep(3000);}catch(InterruptedException stopped){return;}}}},"KisaNaverHealthServer");thread.setDaemon(true);thread.start();}
 private NaverHealth(){}
}
