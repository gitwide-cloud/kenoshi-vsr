package com.office.lanmonitor;
import android.media.*;
import java.nio.*;
import java.io.*;
import java.util.*;

/** MediaCodec hardware bridge. Native callbacks arrive on a dedicated receiver thread. */
public final class ForwardEngine implements AutoCloseable {
 public final HlsMuxer mux;
 public final LatencyProfile profile;
 public final StreamServer server;
 public volatile String status="Waiting for NDI video…";
 public volatile boolean muted=false;
 private MediaCodec video,audio;
 private byte[] parameterSets=new byte[0];
 private int width,height,rate;
 private boolean closed=false;
 private static final class Frame {final byte[] bytes;final int w,h;final long pts,arrival=System.nanoTime();Frame(byte[] b,int w,int h,long p){bytes=b;this.w=w;this.h=h;pts=p;}}
 private volatile Frame latest;
 private final java.util.ArrayDeque<byte[]> audioQueue=new java.util.ArrayDeque<>();
 private int audioOffset=0,queuedAudio=0;
 private long audioClockSamples=0;
 private final java.util.concurrent.ScheduledExecutorService clock=java.util.concurrent.Executors.newSingleThreadScheduledExecutor();
 private long lastVideoPts=-1,lastKeyRequest=0,encodedFrames=0,videoBase=-1,videoClock=-1,lastTick=-1;
 private volatile boolean signalConnected=true;

 private long audioBase=-1,audioSamples=0;
 private final MediaCodec.BufferInfo info=new MediaCodec.BufferInfo();
 public ForwardEngine()throws IOException{this(LatencyProfile.STABLE);}
 public ForwardEngine(LatencyProfile profile)throws IOException{this.profile=profile;this.mux=new HlsMuxer(profile);server=new StreamServer(mux);clock.scheduleWithFixedDelay(()->{Frame f=latest;if(f!=null){long now=System.nanoTime();if(videoBase<0){videoBase=f.pts;videoClock=now;}long tick=(now-videoClock)*30/1000000000;if(tick>lastTick){lastTick=tick;encodeVideo(f.bytes,f.w,f.h,videoBase+tick*1000000/30);}pumpAudio(now);}},0,10,java.util.concurrent.TimeUnit.MILLISECONDS);}
 public void onSignal(boolean connected){signalConnected=connected;}
 public void onVideo(byte[] bytes,int w,int h,long ptsUs){signalConnected=true;latest=new Frame(bytes,w,h,ptsUs);}
 private synchronized void encodeVideo(byte[] i420,int w,int h,long ptsUs){if(closed)return;try{
  if(video==null){width=w;height=h;MediaFormat f=MediaFormat.createVideoFormat("video/avc",w,h);f.setInteger(MediaFormat.KEY_COLOR_FORMAT,MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible);f.setInteger(MediaFormat.KEY_BIT_RATE,3500000);f.setInteger(MediaFormat.KEY_FRAME_RATE,30);f.setFloat(MediaFormat.KEY_I_FRAME_INTERVAL,profile.keyframeUs/1000000f);if(android.os.Build.VERSION.SDK_INT>=29)f.setInteger(MediaFormat.KEY_MAX_B_FRAMES,0);f.setInteger(MediaFormat.KEY_PROFILE,MediaCodecInfo.CodecProfileLevel.AVCProfileBaseline);video=MediaCodec.createEncoderByType("video/avc");video.configure(f,null,null,MediaCodec.CONFIGURE_FLAG_ENCODE);video.start();}
  if(w!=width||h!=height){status="Source format changed — restart output";return;}
  if(ptsUs-lastKeyRequest>=profile.keyframeUs){android.os.Bundle request=new android.os.Bundle();request.putInt(MediaCodec.PARAMETER_KEY_REQUEST_SYNC_FRAME,0);video.setParameters(request);lastKeyRequest=ptsUs;}ptsUs=Math.max(lastVideoPts+1,ptsUs);lastVideoPts=ptsUs;
  int index=video.dequeueInputBuffer(0);if(index>=0){Image image=video.getInputImage(index);if(image==null)throw new IOException("Encoder does not expose flexible YUV input");Image.Plane[] planes=image.getPlanes();int offset=0;for(int p=0;p<3;p++){int pw=p==0?w:w/2,ph=p==0?h:h/2;ByteBuffer b=planes[p].getBuffer();int base=b.position(),rs=planes[p].getRowStride(),ps=planes[p].getPixelStride();for(int y=0;y<ph;y++){if(ps==1){b.position(base+y*rs);b.put(i420,offset+y*pw,pw);}else for(int x=0;x<pw;x++)b.put(base+y*rs+x*ps,i420[offset+y*pw+x]);}offset+=pw*ph;}video.queueInputBuffer(index,0,i420.length,ptsUs,0);}
  drainVideo();status=(signalConnected?"Encoding H.264 · ":"Source disconnected — last frame · ")+w+" × "+h+" · "+mux.count()+" segments · "+encodedFrames+" encoded frames";
 }catch(Exception e){status="Video encoder error: "+e.getMessage();android.util.Log.e("LanForward",status,e);}}
 public synchronized void onAudio(byte[] pcm,int sampleRate,long ptsUs){if(closed||sampleRate<=0)return;
  if(sampleRate!=48000){int input=pcm.length/4,output=(int)((long)input*48000/sampleRate);byte[] converted=new byte[output*4];for(int i=0;i<output;i++){int source=Math.min(input-1,(int)((long)i*sampleRate/48000));System.arraycopy(pcm,source*4,converted,i*4,4);}pcm=converted;}
  while(queuedAudio>96000&&!audioQueue.isEmpty()){byte[] old=audioQueue.removeFirst();queuedAudio-=old.length-audioOffset;audioOffset=0;}
  audioQueue.addLast(pcm);queuedAudio+=pcm.length;
 }
 private synchronized void pumpAudio(long now){if(closed||videoClock<0)return;long target=(now-videoClock)*48000/1000000000;int loops=0;while(audioClockSamples+960<=target&&loops++<4){byte[] pcm=new byte[960*4];int at=0;while(at<pcm.length&&!audioQueue.isEmpty()){byte[] first=audioQueue.peekFirst();int n=Math.min(pcm.length-at,first.length-audioOffset);System.arraycopy(first,audioOffset,pcm,at,n);at+=n;audioOffset+=n;queuedAudio-=n;if(audioOffset==first.length){audioQueue.removeFirst();audioOffset=0;}}encodeAudio(pcm,48000,videoBase+audioClockSamples*1000000/48000);audioClockSamples+=960;}}
 private synchronized void encodeAudio(byte[] pcm,int sampleRate,long ptsUs){if(closed)return;try{
  if(audio==null){rate=sampleRate;MediaFormat f=MediaFormat.createAudioFormat("audio/mp4a-latm",rate,2);f.setInteger(MediaFormat.KEY_AAC_PROFILE,MediaCodecInfo.CodecProfileLevel.AACObjectLC);f.setInteger(MediaFormat.KEY_BIT_RATE,128000);f.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE,65536);audio=MediaCodec.createEncoderByType("audio/mp4a-latm");audio.configure(f,null,null,MediaCodec.CONFIGURE_FLAG_ENCODE);audio.start();mux.setAudio(rate);}
  if(rate!=sampleRate)return;
  drainAudio();int offset=0;while(offset<pcm.length){int index=audio.dequeueInputBuffer(0);if(index<0)break;ByteBuffer b=audio.getInputBuffer(index);b.clear();int n=Math.min(b.remaining()/4*4,pcm.length-offset);if(muted){for(int i=0;i<n;i++)b.put((byte)0);}else b.put(pcm,offset,n);if(audioBase<0||Math.abs(ptsUs-(audioBase+audioSamples*1000000/rate))>500000){audioBase=Math.max(0,ptsUs);audioSamples=0;}long stamp=audioBase+audioSamples*1000000/rate;audioSamples+=n/4;audio.queueInputBuffer(index,0,n,stamp,0);offset+=n;drainAudio();}
 }catch(Exception e){android.util.Log.e("LanForward","AAC encode failed",e);}}
 private void drainVideo(){for(int i=0;i<20;i++){int n=video.dequeueOutputBuffer(info,0);if(n==MediaCodec.INFO_OUTPUT_FORMAT_CHANGED){MediaFormat f=video.getOutputFormat();parameterSets=join(copy(f.getByteBuffer("csd-0")),copy(f.getByteBuffer("csd-1")));continue;}if(n<0)break;ByteBuffer b=video.getOutputBuffer(n);byte[] bytes=new byte[info.size];b.position(info.offset);b.get(bytes);boolean config=(info.flags&MediaCodec.BUFFER_FLAG_CODEC_CONFIG)!=0,key=(info.flags&MediaCodec.BUFFER_FLAG_KEY_FRAME)!=0;long pts=info.presentationTimeUs;video.releaseOutputBuffer(n,false);if(config){parameterSets=annexB(bytes);continue;}if(bytes.length>0){encodedFrames++;bytes=annexB(bytes);byte[] aud={0,0,0,1,9,(byte)0xf0};mux.video(join(aud,key?join(parameterSets,bytes):bytes),pts,key);}}}
 private void drainAudio(){for(int i=0;i<20;i++){int n=audio.dequeueOutputBuffer(info,0);if(n==MediaCodec.INFO_OUTPUT_FORMAT_CHANGED)continue;if(n<0)break;byte[] bytes=new byte[info.size];ByteBuffer b=audio.getOutputBuffer(n);b.position(info.offset);b.get(bytes);boolean config=(info.flags&MediaCodec.BUFFER_FLAG_CODEC_CONFIG)!=0;long pts=info.presentationTimeUs;audio.releaseOutputBuffer(n,false);if(!config&&bytes.length>0)mux.audio(bytes,pts);}}
 private static byte[] copy(ByteBuffer b){if(b==null)return new byte[0];ByteBuffer d=b.duplicate();byte[] out=new byte[d.remaining()];d.get(out);return annexB(out);}
 static byte[] annexB(byte[] bytes){if(bytes.length<4)return bytes;if(bytes[0]==0&&bytes[1]==0&&(bytes[2]==1||(bytes[2]==0&&bytes[3]==1)))return bytes;ByteArrayOutputStream out=new ByteArrayOutputStream();int pos=0;while(pos+4<=bytes.length){int len=((bytes[pos]&255)<<24)|((bytes[pos+1]&255)<<16)|((bytes[pos+2]&255)<<8)|(bytes[pos+3]&255);pos+=4;if(len<=0||len>bytes.length-pos)return bytes;out.write(0);out.write(0);out.write(0);out.write(1);out.write(bytes,pos,len);pos+=len;}return pos==bytes.length?out.toByteArray():bytes;}
 private static byte[] join(byte[] a,byte[] b){byte[] out=Arrays.copyOf(a,a.length+b.length);System.arraycopy(b,0,out,a.length,b.length);return out;}
 @Override public synchronized void close(){closed=true;latest=null;audioQueue.clear();clock.shutdownNow();if(video!=null){try{video.stop();}catch(Exception ignored){}video.release();video=null;}if(audio!=null){try{audio.stop();}catch(Exception ignored){}audio.release();audio=null;}server.close();}
}
