package com.office.lanmonitor;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

/** Token-scoped local HTTP origin for HLS. No filesystem paths or uploads. */
public final class StreamServer implements AutoCloseable {
 private final ServerSocket server;
 private final HlsMuxer mux;
 private final String token=UUID.randomUUID().toString().replace("-","");
 private final ThreadPoolExecutor clients=new ThreadPoolExecutor(2,4,10,TimeUnit.SECONDS,new ArrayBlockingQueue<>(12),r->{Thread t=new Thread(r,"hls-client");t.setDaemon(true);return t;});
 private volatile boolean live=true;
 public StreamServer(HlsMuxer mux) throws IOException {this.mux=mux;server=new ServerSocket(0,8);Thread accept=new Thread(()->{while(live){try{Socket socket=server.accept();socket.setSoTimeout(4000);try{clients.execute(()->serve(socket));}catch(RejectedExecutionException e){socket.close();}}catch(IOException ignored){}}},"hls-origin");accept.setDaemon(true);accept.start();}
 public String url(String host){return "http://"+host+":"+server.getLocalPort()+"/"+token+"/live.m3u8";}
 private void serve(Socket socket){try(Socket s=socket){BufferedReader r=new BufferedReader(new InputStreamReader(s.getInputStream(),StandardCharsets.US_ASCII));String request=r.readLine();if(request==null||request.length()>2048)return;String[] parts=request.split(" ");if(parts.length<2)return;String method=parts[0],path=parts[1].split("\\?",2)[0];int headers=0;String range=null,line;while((line=r.readLine())!=null&&!line.isEmpty()){if(++headers>50||line.length()>4096)return;if(line.toLowerCase(Locale.ROOT).startsWith("range:"))range=line.substring(6).trim();}
  OutputStream out=s.getOutputStream();if(!path.startsWith("/"+token+"/")){reply(out,404,"text/plain",new byte[0],false,null);return;}if(method.equals("OPTIONS")){reply(out,204,"text/plain",new byte[0],true,null);return;}if(!method.equals("GET")&&!method.equals("HEAD")){reply(out,405,"text/plain",new byte[0],false,null);return;}
  String leaf=path.substring(token.length()+2);byte[] bytes=null;String mime="video/mp2t";int status=200;
  if(leaf.equals("live.ts")){if(method.equals("HEAD")){reply(out,200,"video/mp2t",new byte[0],true,null);return;}streamTs(out);return;}
  if(leaf.equals("live.m3u8")){mime="application/vnd.apple.mpegurl";bytes=mux.playlist();if(bytes==null){status=503;bytes="Waiting for NDI keyframes".getBytes(StandardCharsets.UTF_8);}}
  else if(leaf.matches("segment-[0-9]+\\.ts")){try{bytes=mux.segment(Long.parseLong(leaf.substring(8,leaf.length()-3)));}catch(NumberFormatException ignored){}}
  if(bytes==null){status=404;bytes=new byte[0];}
  String extra=null;if(status==200&&range!=null&&range.matches("bytes=[0-9]+-[0-9]*")){String[] b=range.substring(6).split("-",-1);long from=Long.parseLong(b[0]),to=b[1].isEmpty()?bytes.length-1:Long.parseLong(b[1]);if(from>=bytes.length||to<from){status=416;extra="Content-Range: bytes */"+bytes.length+"\r\n";bytes=new byte[0];}else{to=Math.min(to,bytes.length-1);extra="Content-Range: bytes "+from+"-"+to+"/"+bytes.length+"\r\n";bytes=Arrays.copyOfRange(bytes,(int)from,(int)to+1);status=206;}}
  reply(out,status,mime,bytes,method.equals("HEAD"),extra);
 }catch(Exception ignored){}}
 private void streamTs(OutputStream out)throws IOException,InterruptedException {HlsMuxer.LiveClient client=mux.subscribe();try{out.write("HTTP/1.1 200 OK\r\nContent-Type: video/mp2t\r\nTransfer-Encoding: chunked\r\nAccess-Control-Allow-Origin: *\r\nCache-Control: no-store\r\nConnection: close\r\n\r\n".getBytes(StandardCharsets.US_ASCII));out.flush();while(live&&!client.closed()){byte[] bytes=client.next();if(bytes==null)continue;out.write((Integer.toHexString(bytes.length)+"\r\n").getBytes(StandardCharsets.US_ASCII));out.write(bytes);out.write(new byte[]{13,10});out.flush();}}finally{mux.unsubscribe(client);}}
 private void reply(OutputStream out,int status,String mime,byte[] body,boolean head,String extra)throws IOException {String message=status==200?"OK":status==206?"Partial Content":status==204?"No Content":status==503?"Service Unavailable":status==416?"Range Not Satisfiable":"Not Found";String h="HTTP/1.1 "+status+" "+message+"\r\nContent-Type: "+mime+"\r\nContent-Length: "+body.length+"\r\nAccess-Control-Allow-Origin: *\r\nAccess-Control-Allow-Methods: GET, HEAD, OPTIONS\r\nAccess-Control-Allow-Headers: Range\r\nAccept-Ranges: bytes\r\nCache-Control: no-store\r\nConnection: close\r\n"+(status==503?"Retry-After: 1\r\n":"")+(extra==null?"":extra)+"\r\n";out.write(h.getBytes(StandardCharsets.US_ASCII));if(!head)out.write(body);out.flush();}
 @Override public void close(){live=false;mux.closeClients();try{server.close();}catch(IOException ignored){}clients.shutdownNow();}
}
