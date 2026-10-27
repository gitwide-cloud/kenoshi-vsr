package com.office.lanmonitor;
import android.view.Surface;
/** Safe Java facade; no native runtime is shipped in the UI-only APK. */
public final class NdiBridge {
 private static final boolean loaded;
 static {boolean ok=false;try{System.loadLibrary("monitor");ok=true;}catch(UnsatisfiedLinkError ignored){}loaded=ok;}
 public static boolean available(){return loaded&&nativeAvailable();}
 public static String[] discover(String groups){return available()?nativeDiscover(groups):new String[0];}
 public static boolean connect(String name,String url,Surface surface,boolean proxy){return available()&&nativeConnect(name,url,surface,proxy);}
 public static int[] videoSize(){return loaded?nativeVideoSize():new int[]{0,0};}
 public static String status(){return available()?nativeStatus():"NDI SDK not installed";}
 public static void disconnect(){if(loaded)nativeDisconnect();}
 public static void shutdown(){if(loaded)nativeShutdown();}
 private static native boolean nativeAvailable();
 private static native String[] nativeDiscover(String groups);
 private static native boolean nativeConnect(String name,String url,Surface surface,boolean proxy);
 private static native String nativeStatus();
 private static native int[] nativeVideoSize();
 private static native void nativeDisconnect();
 private static native void nativeShutdown();
 private NdiBridge(){}
}
