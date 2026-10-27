package com.office.lanmonitor;
import android.app.*;
import android.content.*;
import androidx.mediarouter.media.*;
import com.google.android.gms.cast.*;
import com.google.android.gms.cast.framework.*;
import java.util.*;

/** Official Cast framework session; default media receiver plays our local HLS origin. */
public final class CastOutput {
 private final Activity activity;
 private CastContext context;
 private MediaRouter router;
 private MediaRouteSelector selector;
 private String pendingUrl="",pendingTitle="",loadedUrl="";
 private boolean closed=false;
 public String status="Choose a Chromecast";
 private final MediaRouter.Callback discovery=new MediaRouter.Callback(){};
 private final SessionManagerListener<CastSession> listener=new SessionManagerListener<CastSession>(){
  public void onSessionStarting(CastSession s){status="Connecting to Chromecast…";}
  public void onSessionStarted(CastSession s,String id){status="Chromecast connected";load();}
  public void onSessionStartFailed(CastSession s,int error){status="Cast connection failed ("+error+")";}
  public void onSessionEnding(CastSession s){}
  public void onSessionEnded(CastSession s,int error){loadedUrl="";status="Cast disconnected";}
  public void onSessionResuming(CastSession s,String id){}
  public void onSessionResumed(CastSession s,boolean suspended){load();}
  public void onSessionResumeFailed(CastSession s,int error){status="Could not resume Cast ("+error+")";}
  public void onSessionSuspended(CastSession s,int reason){status="Cast connection suspended";}
 };
 public CastOutput(Activity a){activity=a;try{context=CastContext.getSharedInstance(a);context.getSessionManager().addSessionManagerListener(listener,CastSession.class);router=MediaRouter.getInstance(a);selector=new MediaRouteSelector.Builder().addControlCategory(CastMediaControlIntent.categoryForCast(CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID)).build();router.addCallback(selector,discovery,MediaRouter.CALLBACK_FLAG_REQUEST_DISCOVERY);}catch(Exception e){status="Cast unavailable: "+e.getMessage();}}
 public void choose(){if(context==null||!(activity instanceof androidx.fragment.app.FragmentActivity)){new AlertDialog.Builder(activity).setMessage(status).setPositiveButton("OK",null).show();return;}androidx.mediarouter.app.MediaRouteButton button=new androidx.mediarouter.app.MediaRouteButton(activity);CastButtonFactory.setUpMediaRouteButton(activity,button);androidx.mediarouter.app.MediaRouteChooserDialogFragment dialog=new androidx.mediarouter.app.MediaRouteChooserDialogFragment();dialog.setRouteSelector(button.getRouteSelector());dialog.show(((androidx.fragment.app.FragmentActivity)activity).getSupportFragmentManager(),"cast-route-chooser");}
 public void offer(String url,String title){pendingUrl=url;pendingTitle=title;if(!url.equals(loadedUrl))load();}
 private void load(){if(closed||context==null||pendingUrl.isEmpty()||pendingUrl.equals(loadedUrl))return;CastSession session=context.getSessionManager().getCurrentCastSession();if(session==null||!session.isConnected()||session.getRemoteMediaClient()==null)return;String url=pendingUrl;MediaMetadata metadata=new MediaMetadata(MediaMetadata.MEDIA_TYPE_MOVIE);metadata.putString(MediaMetadata.KEY_TITLE,pendingTitle);MediaInfo info=new MediaInfo.Builder(url).setContentType("application/x-mpegURL").setStreamType(MediaInfo.STREAM_TYPE_LIVE).setMetadata(metadata).build();loadedUrl=url;status="Loading live stream…";session.getRemoteMediaClient().load(new MediaLoadRequestData.Builder().setMediaInfo(info).setAutoplay(true).build()).setResultCallback(result->{if(result.getStatus().isSuccess())status="Casting · "+session.getCastDevice().getFriendlyName();else{status="Cast could not load stream ("+result.getStatus().getStatusCode()+")";}});}
 public void stop(){pendingUrl="";loadedUrl="";if(context!=null){CastSession s=context.getSessionManager().getCurrentCastSession();if(s!=null&&s.getRemoteMediaClient()!=null)s.getRemoteMediaClient().stop();}}
 public void close(){closed=true;if(context!=null)context.getSessionManager().removeSessionManagerListener(listener,CastSession.class);if(router!=null)router.removeCallback(discovery);}
}
