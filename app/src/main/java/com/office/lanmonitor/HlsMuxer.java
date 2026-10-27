package com.office.lanmonitor;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Small live MPEG-TS/HLS muxer. Each segment starts with PAT/PMT and an IDR. */
public final class HlsMuxer {
 public static final class Segment {final long number;final byte[] bytes;final double duration;Segment(long n,byte[] b,double d){number=n;bytes=b;duration=d;}}
 private final LatencyProfile profile;
 public HlsMuxer(){this(LatencyProfile.STABLE);}
 public HlsMuxer(LatencyProfile profile){this.profile=profile;}
 public static final class LiveClient implements AutoCloseable {
  final java.util.concurrent.ArrayBlockingQueue<byte[]> packets=new java.util.concurrent.ArrayBlockingQueue<>(24);
  volatile boolean active=false,closed=false;
  public byte[] next()throws InterruptedException{return packets.poll(1,java.util.concurrent.TimeUnit.SECONDS);}
  public boolean closed(){return closed;}
  public void close(){closed=true;packets.clear();}
 }
 private final java.util.ArrayList<LiveClient> viewers=new java.util.ArrayList<>();
 public synchronized LiveClient subscribe(){LiveClient c=new LiveClient();viewers.add(c);return c;}
 public synchronized void unsubscribe(LiveClient client){client.close();viewers.remove(client);}
 public synchronized void closeClients(){for(LiveClient c:viewers)c.close();viewers.clear();}
 private void publish(byte[] data){for(java.util.Iterator<LiveClient> i=viewers.iterator();i.hasNext();){LiveClient c=i.next();if(c.closed||(c.active&&!c.packets.offer(data))){c.close();i.remove();}}}
 private final LinkedHashMap<Long,Segment> segments=new LinkedHashMap<>();
 private final int[] cc=new int[8192];
 private ByteArrayOutputStream current;
 private long firstPts,lastPts,sequence=0;
 private boolean audio;
 private int audioRate=48000;
 public synchronized void setAudio(int sampleRate){audio=true;audioRate=sampleRate;}
 public synchronized int count(){return segments.size();}
 public synchronized byte[] segment(long n){Segment s=segments.get(n);return s==null?null:s.bytes;}
 public synchronized byte[] playlist(){
  if(segments.isEmpty())return null;
  int target=profile.targetSeconds;
  StringBuilder b=new StringBuilder("#EXTM3U\n#EXT-X-VERSION:3\n#EXT-X-TARGETDURATION:"+target+"\n#EXT-X-MEDIA-SEQUENCE:"+segments.keySet().iterator().next()+"\n#EXT-X-INDEPENDENT-SEGMENTS\n");
  if(profile==LatencyProfile.LOW)b.append("#EXT-X-START:TIME-OFFSET=-1.5,PRECISE=YES\n");
  for(Segment s:segments.values())b.append(String.format(Locale.US,"#EXTINF:%.3f,\nsegment-%d.ts\n",s.duration,s.number));
  return b.toString().getBytes(StandardCharsets.US_ASCII);
 }
 public synchronized void video(byte[] bytes,long ptsUs,boolean key){
  long pts=Math.max(0,ptsUs)*90/1000;
  if(current==null){if(!key)return;begin(pts);}
  else if(key&&pts-firstPts>=profile.minSegmentUs*90/1000){finish(pts);begin(pts);}
  // Bound memory when an encoder fails to produce keyframes.
  if(current.size()>12*1024*1024){current=null;return;}
  lastPts=pts;pes(256,0xE0,bytes,pts,true);
 }
 public synchronized void audio(byte[] raw,long ptsUs){if(current==null)return;byte[] framed=new byte[raw.length+7];int index=rateIndex(audioRate),len=framed.length;
  framed[0]=(byte)0xff;framed[1]=(byte)0xf1;framed[2]=(byte)((1<<6)|(index<<2));framed[3]=(byte)((2<<6)|(len>>11));framed[4]=(byte)(len>>3);framed[5]=(byte)(((len&7)<<5)|31);framed[6]=(byte)0xfc;System.arraycopy(raw,0,framed,7,raw.length);pes(257,0xC0,framed,Math.max(0,ptsUs)*90/1000,false);
 }
 private static int rateIndex(int rate){int[] rates={96000,88200,64000,48000,44100,32000,24000,22050,16000,12000,11025,8000,7350};for(int i=0;i<rates.length;i++)if(rates[i]==rate)return i;return 3;}
 private void begin(long pts){current=new ByteArrayOutputStream();firstPts=pts;lastPts=pts;for(LiveClient c:viewers)if(!c.closed)c.active=true;
  section(0,new byte[]{0, (byte)0xb0,13,0,1,(byte)0xc1,0,0,0,1,(byte)0xf0,0});
  byte[] pmt=audio?new byte[]{2,(byte)0xb0,23,0,1,(byte)0xc1,0,0,(byte)0xe1,0,(byte)0xf0,0,0x1b,(byte)0xe1,0,(byte)0xf0,0,0x0f,(byte)0xe1,1,(byte)0xf0,0}:new byte[]{2,(byte)0xb0,18,0,1,(byte)0xc1,0,0,(byte)0xe1,0,(byte)0xf0,0,0x1b,(byte)0xe1,0,(byte)0xf0,0};section(4096,pmt);
 }
 private void finish(long nextPts){segments.put(sequence,new Segment(sequence++,current.toByteArray(),Math.max(.04,(nextPts-firstPts)/90000.0)));while(segments.size()>profile.windowSegments)segments.remove(segments.keySet().iterator().next());}
 private void section(int pid,byte[] table){int crc=crc(table);byte[] payload=new byte[table.length+5];System.arraycopy(table,0,payload,1,table.length);for(int i=0;i<4;i++)payload[table.length+1+i]=(byte)(crc>>>(24-i*8));packets(pid,payload,true,-1);}
 static int crc(byte[] bytes){int crc=0xffffffff;for(byte x:bytes){crc^=(x&255)<<24;for(int bit=0;bit<8;bit++)crc=(crc<<1)^((crc<0)?0x04c11db7:0);}return crc;}
 private void pes(int pid,int stream,byte[] bytes,long pts,boolean pcr){ByteArrayOutputStream b=new ByteArrayOutputStream();int length=bytes.length+8;if(stream==0xE0||length>65535)length=0;
  b.write(0);b.write(0);b.write(1);b.write(stream);b.write(length>>8);b.write(length);b.write(0x80);b.write(0x80);b.write(5);
  b.write(0x21|(int)((pts>>29)&14));b.write((int)(pts>>22));b.write((int)((pts>>14)&254)|1);b.write((int)(pts>>7));b.write((int)((pts<<1)&254)|1);b.write(bytes,0,bytes.length);packets(pid,b.toByteArray(),true,pcr?pts:-1);
 }
 private void packets(int pid,byte[] data,boolean start,long pcr){ByteArrayOutputStream batch=new ByteArrayOutputStream();int offset=0;boolean first=true;while(offset<data.length){byte[] ts=new byte[188];Arrays.fill(ts,(byte)0xff);ts[0]=0x47;ts[1]=(byte)(((pid>>8)&31)|((start&&first)?64:0));ts[2]=(byte)pid;int capacity=first&&pcr>=0?176:184;int amount=Math.min(capacity,data.length-offset);int adaptation=184-amount;
   ts[3]=(byte)((adaptation>0?0x30:0x10)|(cc[pid]++&15));int pos=4;
   if(adaptation>0){ts[pos++]=(byte)(adaptation-1);if(adaptation>1){ts[pos++]=(byte)(first&&pcr>=0?0x10:0);if(first&&pcr>=0){long base=pcr&0x1ffffffffL;ts[pos++]=(byte)(base>>25);ts[pos++]=(byte)(base>>17);ts[pos++]=(byte)(base>>9);ts[pos++]=(byte)(base>>1);ts[pos++]=(byte)((base<<7)|0x7e);ts[pos++]=0;}pos=4+adaptation;}}
   System.arraycopy(data,offset,ts,pos,amount);current.write(ts,0,ts.length);batch.write(ts,0,ts.length);offset+=amount;first=false;
  }publish(batch.toByteArray());}
}
