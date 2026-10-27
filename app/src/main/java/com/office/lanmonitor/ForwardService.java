package com.office.lanmonitor;
import android.app.*;
import android.content.*;
import android.net.*;
import android.net.nsd.NsdManager;
import android.net.wifi.WifiManager;
import android.os.*;
import java.net.*;
import java.util.concurrent.*;

public final class ForwardService extends Service {
 static {NdiBridge.available();}
 public static volatile ForwardService instance;
 public static volatile String state="Stopped",streamUrl="",sourceName="",sessionId="";
 public static volatile boolean running=false;
 private final ExecutorService worker=Executors.newSingleThreadExecutor();
 private ForwardEngine engine;
 public static volatile String latencyId="stable";
 private NsdManager nsd;
 private WifiManager.MulticastLock multicast;
 private WifiManager.WifiLock wifiLock;
 private PowerManager.WakeLock cpuLock;
 private final Handler main=new Handler(Looper.getMainLooper());
 private boolean destroyed=false;
 private static native boolean nativeStart(String name,String url,ForwardEngine engine,boolean proxy);
 private static native void nativeStop();
 @Override public void onCreate(){super.onCreate();instance=this;nsd=(NsdManager)getSystemService(NSD_SERVICE);NotificationManager n=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);n.createNotificationChannel(new NotificationChannel("forward","NDI forwarding",NotificationManager.IMPORTANCE_LOW));}
 @Override public int onStartCommand(Intent intent,int flags,int id){if(intent==null){stopSelf();return START_NOT_STICKY;}if("STOP".equals(intent.getAction())){stopSelf();return START_NOT_STICKY;}
  if(!NdiBridge.available()){state="NDI SDK unavailable";stopSelf();return START_NOT_STICKY;}
  String name=intent.getStringExtra("source"),url=intent.getStringExtra("url");latencyId=LatencyProfile.from(intent.getStringExtra("latency")).id;boolean proxy=intent.getBooleanExtra("proxy",false);sourceName=name;sessionId=intent.getStringExtra("id");state="Starting NDI forwarding…";running=true;
  Intent stop=new Intent(this,ForwardService.class).setAction("STOP");PendingIntent stopAction=PendingIntent.getService(this,0,stop,PendingIntent.FLAG_IMMUTABLE);
  Intent open=new Intent(this,MainActivity.class);PendingIntent openAction=PendingIntent.getActivity(this,0,open,PendingIntent.FLAG_IMMUTABLE);
  Notification notification=new Notification.Builder(this,"forward").setSmallIcon(com.office.lanmonitor.R.drawable.ic_monitor).setContentTitle("Kenoshi VSR · forwarding NDI").setContentText(name).setContentIntent(openAction).setOngoing(true).addAction(new Notification.Action.Builder(null,"Stop",stopAction).build()).build();startForeground(24,notification);streamUrl="";main.removeCallbacks(poll);
  worker.execute(()->{try{nativeStop();if(engine!=null)engine.close();engine=new ForwardEngine(LatencyProfile.from(latencyId));String host=localAddress();streamUrl=host.isEmpty()?"":engine.server.url(host);if(streamUrl.isEmpty())throw new Exception("No local IPv4 address. Connect to Wi-Fi or Ethernet.");if(!NdiBridge.available()||!nativeStart(name,url==null?"":url,engine,proxy))throw new Exception("Could not initialize NDI forward receiver");acquire();main.post(poll);}catch(Exception e){state="Forwarding failed: "+e.getMessage();android.util.Log.e("LanForward",state,e);main.post(this::stopSelf);}});return START_NOT_STICKY;
 }
 private final Runnable poll=new Runnable(){public void run(){if(destroyed)return;if(engine!=null)state=engine.status;main.postDelayed(this,1000);}};
 public int readySegments(){ForwardEngine e=engine;return e==null?0:e.mux.count();}
 public void setMuted(boolean muted){ForwardEngine e=engine;if(e!=null)e.muted=muted;}
 private void acquire(){WifiManager w=(WifiManager)getApplicationContext().getSystemService(WIFI_SERVICE);if(w!=null){multicast=w.createMulticastLock("ndi-forward");multicast.setReferenceCounted(false);multicast.acquire();wifiLock=w.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF,"ndi-forward");wifiLock.setReferenceCounted(false);wifiLock.acquire();}PowerManager pm=(PowerManager)getSystemService(POWER_SERVICE);cpuLock=pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"LanMonitor:Forward");cpuLock.acquire(6*60*60*1000L);}
 private String localAddress(){ConnectivityManager cm=(ConnectivityManager)getSystemService(CONNECTIVITY_SERVICE);for(Network n:cm.getAllNetworks()){NetworkCapabilities caps=cm.getNetworkCapabilities(n);if(caps==null||(!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)&&!caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)))continue;LinkProperties lp=cm.getLinkProperties(n);if(lp!=null)for(LinkAddress a:lp.getLinkAddresses())if(a.getAddress() instanceof Inet4Address&&!a.getAddress().isLoopbackAddress())return a.getAddress().getHostAddress();}return "";}
 @Override public void onDestroy(){destroyed=true;running=false;instance=null;streamUrl="";main.removeCallbacksAndMessages(null);worker.execute(()->{if(NdiBridge.available())nativeStop();if(engine!=null){engine.close();engine=null;}if(multicast!=null&&multicast.isHeld())multicast.release();if(wifiLock!=null&&wifiLock.isHeld())wifiLock.release();if(cpuLock!=null&&cpuLock.isHeld())cpuLock.release();});worker.shutdown();stopForeground(STOP_FOREGROUND_REMOVE);super.onDestroy();}
 @Override public IBinder onBind(Intent i){return null;}
}
