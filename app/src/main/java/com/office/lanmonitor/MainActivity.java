package com.office.lanmonitor;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaPlayer;
import android.net.Uri;
import android.net.nsd.NsdManager;
import android.net.wifi.WifiManager;
import android.os.*;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.util.*;
import java.util.concurrent.*;

/** Native Android UI. Never substitutes simulated feeds for a missing receiver. */
public class MainActivity extends androidx.fragment.app.FragmentActivity {
 private final Handler main=new Handler(Looper.getMainLooper());
 private final ScheduledExecutorService worker=Executors.newSingleThreadScheduledExecutor();
 private final Session[] sessions=new Session[2];
 private final Button[] tabs=new Button[3];
 private final Map<String,String> discovered=new LinkedHashMap<>();
 private LinearLayout page,header,tabbar,setup,content;
 private Spinner mode,category,type,group,feed,destination,themePicker,device,latency,receiveQuality;
 private EditText address,sessionName,customGroup;
 private TextView hint,route,slots,sourceLabel,footer;
 private Button open,refresh;
 private LinearLayout groupField,feedField,addressField,deviceField,customGroupField,latencyField,qualityField;
 private String theme="normal";
 private int bg,panel,ink,sub,line,blue,green,purple,active=0;
 private CastOutput cast;
 private boolean updating=false,full=false;
 private volatile boolean resumed=false,dead=false;
 private WifiManager.MulticastLock multicast;
 private NsdManager nsd;
 private long scanGeneration=0;
 private volatile int ndiOwner=-1;

 static class Session {
  String name,mode,category,type,group,source,url,destination;
  String id=java.util.UUID.randomUUID().toString();
  transient TextView link;
  boolean muted=false,proxy=false;
  String latency="stable";
  transient LinearLayout view,toolbar;
  transient HorizontalScrollView controls;
  transient FrameLayout stage;
  transient SurfaceView surface;
  transient TextView status,overlay;
  transient MediaPlayer player;
  transient volatile boolean surfaceReady=false,started=false;
  transient volatile long generation=0;
  JSONObject json() throws JSONException {JSONObject o=new JSONObject();o.put("id",id).put("name",name).put("mode",mode).put("category",category).put("type",type).put("group",group).put("source",source).put("url",url).put("destination",destination).put("muted",muted).put("proxy",proxy).put("latency",latency);return o;}
  static Session parse(JSONObject o){Session s=new Session();s.id=o.optString("id",s.id);s.name=o.optString("name","Session");s.mode=o.optString("mode","Receive");s.category=o.optString("category","Feed");s.type=o.optString("type","NDI");s.group=o.optString("group","Public");s.source=o.optString("source","");s.url=o.optString("url","");s.destination=o.optString("destination","View in tab");s.muted=o.optBoolean("muted");s.proxy=o.optBoolean("proxy");s.latency=LatencyProfile.from(o.optString("latency")).id;return s;}
 }
 @Override public void onCreate(Bundle state){
  theme=getPreferences(0).getString("theme","normal");setTheme(theme.equals("dark")?R.style.MonitorDark:R.style.MonitorLight);
  super.onCreate(state);palette();getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
  getWindow().setStatusBarColor(panel);getWindow().setNavigationBarColor(bg);
  if(!theme.equals("dark"))getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
  nsd=(NsdManager)getSystemService(NSD_SERVICE);
  WifiManager wifi=(WifiManager)getApplicationContext().getSystemService(WIFI_SERVICE);
  if(wifi!=null){multicast=wifi.createMulticastLock("lan-monitor");multicast.setReferenceCounted(false);}
  getOnBackPressedDispatcher().addCallback(this,new androidx.activity.OnBackPressedCallback(true){public void handleOnBackPressed(){if(full)toggleFull();else if(active!=0)select(0);else{setEnabled(false);getOnBackPressedDispatcher().onBackPressed();setEnabled(true);}}});
  restore();build();cast=new CastOutput(this);main.post(forwardPoll);
  worker.scheduleWithFixedDelay(()->{if(resumed&&!dead&&ndiOwner>=0){int owner=ndiOwner;String status=NdiBridge.status();main.post(()->{if(!dead&&ndiOwner==owner&&sessions[owner]!=null){Session s=sessions[owner];s.status.setText(status);s.overlay.setVisibility(status.startsWith("Receiving")?View.GONE:View.VISIBLE);s.overlay.setText(status);fit(s);}});}},1,1,TimeUnit.SECONDS);
 }
 private void showNotices(){String notice="Independent development app. NDI is a registered trademark of Vizrt NDI AB. This app is not sponsored by NDI.\n\nNDI SDK copyright © 2023–2026 Vizrt NDI AB. All rights reserved.\n\nNDI local viewing is video-only. NDI output uses H.264/AAC HLS for VLC and Google Cast. Camera/screen broadcasting is unfinished.\n\n";try(java.io.InputStream in=getAssets().open("licenses/libndi_licenses.txt")){java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)out.write(b,0,n);notice+=out.toString("UTF-8");}catch(Exception ignored){}TextView content=text(notice,12,ink);content.setPadding(dp(18),dp(18),dp(18),dp(18));ScrollView scroll=new ScrollView(this);scroll.addView(content);new AlertDialog.Builder(this).setTitle("About & third-party notices").setView(scroll).setPositiveButton("Close",null).show();}
 private void palette(){boolean dark=theme.equals("dark"),white=theme.equals("white");bg=c(dark?"#141B28":white?"#FFFFFF":"#EDF4FB");panel=c(dark?"#1E2839":"#FFFFFF");ink=c(dark?"#ECF2FC":"#20334C");sub=c(dark?"#B0BED1":"#596E88");line=c(dark?"#35445B":"#D2E1EF");blue=c(dark?"#8CB8FF":"#326BDE");green=c(dark?"#6DE2CB":"#078579");purple=c(dark?"#D4A0FF":"#9655CC");}
 private int c(String x){return Color.parseColor(x);}private int dp(int x){return Math.round(x*getResources().getDisplayMetrics().density);}
 private GradientDrawable shape(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
 private LinearLayout vertical(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
 private TextView text(String s,int size,int color){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);return t;}
 private Button button(String s,View.OnClickListener click){Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setTextSize(14);b.setTextColor(blue);b.setMinHeight(dp(48));b.setOnClickListener(click);return b;}
 private void build(){
  page=vertical();page.setBackgroundColor(bg);header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);header.setPadding(dp(16),dp(8),dp(10),dp(4));header.setBackgroundColor(panel);
  TextView brand=text("KENOSHI VSR",16,blue);brand.setTypeface(null,android.graphics.Typeface.BOLD);header.addView(brand,new LinearLayout.LayoutParams(0,-2,1));
  themePicker=spinner(new String[]{"Normal","White","Dark"});themePicker.setContentDescription("App theme");themePicker.setSelection(theme.equals("dark")?2:theme.equals("white")?1:0);header.addView(themePicker,new LinearLayout.LayoutParams(dp(115),dp(48)));
  themePicker.setOnItemSelectedListener(listener(()->{String selected=value(themePicker).toLowerCase(Locale.ROOT);if(!selected.equals(theme)){getPreferences(0).edit().putString("theme",selected).apply();save();recreate();}}));page.addView(header);
  tabbar=new LinearLayout(this);tabbar.setPadding(dp(6),0,dp(6),0);tabbar.setBackgroundColor(panel);
  for(int i=0;i<3;i++){final int index=i;tabs[i]=button(i==0?"Setup":"",v->select(index));tabs[i].setTextSize(12);tabs[i].setMaxLines(2);tabbar.addView(tabs[i],new LinearLayout.LayoutParams(0,dp(58),1));}page.addView(tabbar);
  content=vertical();page.addView(content,new LinearLayout.LayoutParams(-1,0,1));
  footer=text("Development build · NDI SDK "+(NdiBridge.available()?"loaded":"not installed"),11,sub);footer.setPadding(dp(16),dp(6),dp(16),dp(6));footer.setOnClickListener(v->showNotices());footer.setContentDescription("NDI runtime status and third-party notices");page.addView(footer);
  setContentView(page);setup=buildSetup();content.addView(setup,new LinearLayout.LayoutParams(-1,-1));for(int i=0;i<2;i++)if(sessions[i]!=null)createSessionView(i);active=0;select(0);
 }
 private Spinner spinner(String[] values){Spinner s=new Spinner(this);setOptions(s,values);s.setMinimumHeight(dp(48));s.setBackground(shape(panel,8));s.setBackgroundTintList(ColorStateList.valueOf(line));return s;}
 private void setOptions(Spinner s,String[] values){String previous=s.getSelectedItem()==null?"":s.getSelectedItem().toString();ArrayAdapter<String> a=new ArrayAdapter<String>(this,android.R.layout.simple_spinner_item,values);a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);s.setAdapter(a);for(int i=0;i<values.length;i++)if(values[i].equals(previous)){s.setSelection(i);break;}}
 private AdapterView.OnItemSelectedListener listener(Runnable run){return new AdapterView.OnItemSelectedListener(){public void onItemSelected(AdapterView<?> p,View v,int pos,long id){if(!updating)run.run();}public void onNothingSelected(AdapterView<?> p){}};}
 private String value(Spinner s){return s.getSelectedItem()==null?"":s.getSelectedItem().toString();}
 private LinearLayout field(LinearLayout parent,String label,View control){LinearLayout box=vertical();TextView t=text(label,12,sub);t.setPadding(0,dp(8),0,dp(6));box.addView(t);box.addView(control,new LinearLayout.LayoutParams(-1,dp(50)));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=dp(6);parent.addView(box,lp);return box;}
 private EditText input(String placeholder){EditText e=new EditText(this);e.setSingleLine(true);e.setTextSize(16);e.setTextColor(ink);e.setHintTextColor(sub);e.setHint(placeholder);e.setPadding(dp(12),0,dp(12),0);e.setBackground(shape(panel,8));return e;}
 private LinearLayout buildSetup(){
  LinearLayout outer=vertical();ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);LinearLayout f=vertical();f.setPadding(dp(18),dp(18),dp(18),dp(12));scroll.addView(f);outer.addView(scroll,new LinearLayout.LayoutParams(-1,-1));
  f.addView(text("Set up a session",23,blue));TextView intro=text("Choose a source, then where it goes.",13,sub);intro.setPadding(0,dp(4),0,dp(10));f.addView(intro);
  mode=spinner(new String[]{"Receive","Send"});field(f,"NDI control",mode);
  category=spinner(new String[]{"Feed","Capture"});category.setContentDescription("Source category");field(f,"Source category",category);
  type=spinner(new String[]{"NDI / NDI HX","Stream URL"});type.setContentDescription("Source type");field(f,"Source type",type);
  receiveQuality=spinner(new String[]{"Full quality","Low bandwidth proxy"});receiveQuality.setContentDescription("NDI receive bandwidth");qualityField=field(f,"Receive bandwidth",receiveQuality);
  group=spinner(new String[]{"Public","Office","Production","Custom…"});groupField=field(f,"Group",group);
  customGroup=input("NDI group name");customGroupField=field(f,"Custom group",customGroup);customGroupField.setVisibility(View.GONE);
  feed=spinner(new String[]{"Scan for NDI sources…"});feed.setContentDescription("NDI feed");feedField=field(f,"Feed",feed);
  refresh=button("Scan local network",v->scan());f.addView(refresh);
  address=input("rtsp://server:8554/feed or https://…");address.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_URI);addressField=field(f,"Stream URL",address);
  destination=spinner(new String[]{"View in tab","Chromecast","HTTP stream (HLS / TS)"});destination.setContentDescription("Output destination");field(f,"Destination",destination);
  latency=spinner(new String[]{"Stable","Low latency (experimental)"});latency.setContentDescription("Latency mode");latencyField=field(f,"Latency mode",latency);
  device=inputSpinner();deviceField=field(f,"Cast device",device);
  sessionName=input("Optional session name");field(f,"Tab name",sessionName);
  route=text("",13,sub);route.setPadding(0,dp(16),0,dp(8));f.addView(route);
  hint=text("",13,sub);hint.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);f.addView(hint);
  slots=text("",12,sub);slots.setPadding(0,dp(18),0,dp(8));f.addView(slots);
  open=button("Open session",v->openSession());open.setBackground(shape(blue,9));open.setTextColor(theme.equals("dark")?c("#14233C"):Color.WHITE);f.addView(open);
  mode.setOnItemSelectedListener(listener(()->{updating=true;boolean send=value(mode).equals("Send");category.setSelection(send?1:0);configureTypes(send);updating=false;updateSetup();}));
  category.setOnItemSelectedListener(listener(()->{updating=true;boolean capture=value(category).equals("Capture");mode.setSelection(capture?1:0);configureTypes(capture);updating=false;updateSetup();}));
  type.setOnItemSelectedListener(listener(()->{updating=true;updateSources();updating=false;updateSetup();}));
  group.setOnItemSelectedListener(listener(()->{scanGeneration++;discovered.clear();updating=true;if(isNdi(value(type)))setOptions(feed,new String[]{"Scan for NDI sources…"});updating=false;updateSetup();}));
  feed.setOnItemSelectedListener(listener(this::updateSetup));destination.setOnItemSelectedListener(listener(this::updateSetup));latency.setOnItemSelectedListener(listener(this::updateSetup));updateSetup();return outer;
 }
 private Spinner inputSpinner(){return spinner(new String[]{"Choose device in the session tab"});}
 private void configureTypes(boolean capture){setOptions(type,capture?new String[]{"Camera","Screen","App"}:new String[]{"NDI / NDI HX","Stream URL"});setOptions(destination,capture?new String[]{"NDI broadcast","View in tab","Chromecast","HTTP stream (HLS / TS)"}:new String[]{"View in tab","Chromecast","HTTP stream (HLS / TS)"});updateSources();}
 private void updateSources(){String t=value(type);if(t.equals("Camera"))setOptions(feed,new String[]{"Rear camera","Front camera"});else if(t.equals("Screen"))setOptions(feed,new String[]{"Device screen"});else if(t.equals("App"))setOptions(feed,new String[]{"Choose app when capture starts"});else if(isNdi(t))setOptions(feed,discovered.isEmpty()?new String[]{"Scan for NDI sources…"}:discovered.keySet().toArray(new String[0]));else setOptions(feed,new String[]{"Enter stream URL below"});}
 private String groupName(){return value(group).equals("Custom…")?customGroup.getText().toString().trim():value(group);}
 private void updateSetup(){
  String t=value(type),dest=value(destination);qualityField.setVisibility(isNdi(t)?View.VISIBLE:View.GONE);latencyField.setVisibility(dest.equals("Chromecast")||isHttp(dest)?View.VISIBLE:View.GONE);boolean ndi=isNdi(t),send=value(mode).equals("Send");
  groupField.setVisibility(ndi||send?View.VISIBLE:View.GONE);customGroupField.setVisibility((ndi||send)&&value(group).equals("Custom…")?View.VISIBLE:View.GONE);
  refresh.setVisibility(ndi?View.VISIBLE:View.GONE);addressField.setVisibility(isUrl(t)?View.VISIBLE:View.GONE);deviceField.setVisibility(dest.equals("Chromecast")?View.VISIBLE:View.GONE);
  route.setText(t+" → "+dest);
  String note;
  if(dest.equals("Chromecast")||isHttp(dest))note="NDI is encoded on this phone as H.264/AAC HLS (up to 720p30). Chromecast/VLC must share its LAN. NDI/HX decoding is automatic; the sender sets the format. Low latency uses shorter HLS segments and a continuous HTTP-TS link for players such as VLC. It can rebuffer more. One forwarding session at a time.";
  else if(send)note="Capture and NDI sending are planned. This build saves the session without starting capture.";
  else if(ndi&&!NdiBridge.available())note="NDI runtime is not installed. You can create a setup tab, but NDI discovery and playback are unavailable.";
  else if(t.equals("VLC stream"))note="Opens compatible RTSP/HTTP media with Android's player. Codec support depends on your device; this does not embed VLC.";
  else note="Uses your local network. Connect to the sender's LAN.";
  hint.setText(note);int count=(sessions[0]!=null?1:0)+(sessions[1]!=null?1:0);slots.setText(count+" of 2 session slots occupied");open.setText(count==2?"Replace a session…":"Open session");
 }
 private void scan(){
  if(!NdiBridge.available()){new AlertDialog.Builder(this).setTitle("NDI SDK required").setMessage("This APK contains the interface but not the proprietary NDI runtime. No real NDI sources can be scanned until that integration is built.").setPositiveButton("OK",null).show();return;}
  String g=groupName();if(g.isEmpty()){customGroup.setError("Enter a group name");return;}long generation=++scanGeneration;refresh.setEnabled(false);hint.setText("Searching local network…");
  worker.execute(()->{String[] found=NdiBridge.discover(g.equals("Public")?"":g);main.post(()->{if(dead)return;refresh.setEnabled(true);if(generation!=scanGeneration)return;discovered.clear();for(int i=0;i+1<found.length;i+=2)discovered.put(found[i],found[i+1]);updating=true;updateSources();updating=false;updateSetup();if(found.length==0)hint.setText("No sources found. Check the sender, group, same-LAN connection and multicast filtering.");});});
 }
 private void openSession(){
  Session s=new Session();s.latency=latency.getSelectedItemPosition()==1?"low":"stable";s.proxy=receiveQuality.getSelectedItemPosition()==1;s.mode=value(mode);s.category=value(category);s.type=isNdi(value(type))?"NDI":value(type);s.group=groupName();s.destination=value(destination);s.source=value(feed);s.url=discovered.getOrDefault(s.source,"");
  if(isUrl(s.type)){s.url=address.getText().toString().trim();Uri uri=Uri.parse(s.url);if(!Arrays.asList("http","https","rtsp").contains(uri.getScheme())||uri.getHost()==null){address.setError("Enter a valid HTTP, HTTPS or RTSP URL");return;}s.source=uri.getHost();}
  if(s.type.equals("NDI")&&NdiBridge.available()&&!discovered.containsKey(s.source)){hint.setText("Scan and select a real NDI source first.");return;}
  if(s.type.equals("NDI")&&!NdiBridge.available())s.source="NDI source";
  s.name=sessionName.getText().toString().trim();if(s.name.isEmpty())s.name=s.source;
  int vacant=sessions[0]==null?0:sessions[1]==null?1:-1;
  if(vacant>=0){install(vacant,s);return;}
  new AlertDialog.Builder(this).setTitle("Replace which session?").setItems(new String[]{sessions[0].name,sessions[1].name},(d,i)->new AlertDialog.Builder(this).setTitle("Stop and replace “"+sessions[i].name+"”?").setMessage("The existing session will close.").setPositiveButton("Replace",(x,y)->install(i,s)).setNegativeButton("Cancel",null).show()).setNegativeButton("Cancel",null).show();
 }
 private void install(int i,Session s){if(sessions[i]!=null){stop(sessions[i]);content.removeView(sessions[i].view);}sessions[i]=s;createSessionView(i);save();select(i+1);updateSetup();}
 private void createSessionView(int index){
  Session s=sessions[index];s.view=vertical();s.view.setBackgroundColor(bg);s.view.setVisibility(View.GONE);
  LinearLayout toolbar=new LinearLayout(this);s.toolbar=toolbar;toolbar.setGravity(Gravity.CENTER_VERTICAL);toolbar.setPadding(dp(12),dp(4),dp(8),dp(4));toolbar.setBackgroundColor(panel);
  TextView name=text(s.name,15,ink);name.setMaxLines(2);toolbar.addView(name,new LinearLayout.LayoutParams(0,-2,1));toolbar.addView(button("Source ▾",v->select(0)));toolbar.addView(button("Display ▾",v->new AlertDialog.Builder(this).setTitle("Display").setItems(new String[]{"Fit (preserve proportions)","Full screen"},(d,n)->{if(n==1)toggleFull();else fit(s);}).show()));s.view.addView(toolbar);
  s.stage=new FrameLayout(this);s.stage.setBackgroundColor(Color.BLACK);s.surface=new SurfaceView(this);s.stage.addView(s.surface,new FrameLayout.LayoutParams(-1,-1,Gravity.CENTER));
  s.overlay=text("",16,Color.rgb(192,206,225));s.overlay.setGravity(Gravity.CENTER);s.overlay.setPadding(dp(22),dp(22),dp(22),dp(22));s.stage.addView(s.overlay,new FrameLayout.LayoutParams(-1,-1));s.stage.setOnClickListener(v->{if(full)toggleFull();});s.view.addView(s.stage,new LinearLayout.LayoutParams(-1,0,1));
  s.status=text(s.type+" → "+s.destination,12,sub);s.status.setPadding(dp(14),dp(12),dp(14),dp(8));s.status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);s.view.addView(s.status);
  s.link=text("",12,blue);s.link.setPadding(dp(14),dp(4),dp(14),dp(8));s.link.setTextIsSelectable(true);s.link.setVisibility(View.GONE);s.view.addView(s.link);
  HorizontalScrollView rowScroll=new HorizontalScrollView(this);s.controls=rowScroll;LinearLayout controls=new LinearLayout(this);controls.setPadding(dp(6),0,dp(6),dp(8));
  Button mute=button(s.muted?"Unmute":"Mute",null);mute.setOnClickListener(v->{s.muted=!s.muted;mute.setText(s.muted?"Unmute":"Mute");if(s.player!=null)s.player.setVolume(s.muted?0:1,s.muted?0:1);if(isForward(s)&&ForwardService.instance!=null&&s.id.equals(ForwardService.sessionId))ForwardService.instance.setMuted(s.muted);save();});mute.setEnabled((s.type.equals("VLC stream")&&s.destination.equals("View in tab"))||isForward(s));controls.addView(mute);
  if(isForward(s)){
   controls.addView(button("Latency mode",v->new AlertDialog.Builder(this).setTitle("Latency mode").setSingleChoiceItems(new String[]{"Stable","Low latency (experimental)"},s.latency.equals("low")?1:0,(d,choice)->{d.dismiss();String selected=choice==1?"low":"stable";if(!selected.equals(s.latency))new AlertDialog.Builder(this).setTitle("Restart output with this latency mode?").setMessage("The player will reconnect. Low latency can rebuffer more often.").setPositiveButton("Restart",(dialog,which)->{s.latency=selected;save();launchForward(s);}).setNegativeButton("Cancel",null).show();}).setNegativeButton("Cancel",null).show()));
   controls.addView(button("Start output",v->beginForward(s)));
   controls.addView(button("Stop output",v->stopForward(s)));
   controls.addView(button("Copy stream URL",v->{if(!s.id.equals(ForwardService.sessionId)||ForwardService.streamUrl.isEmpty()){s.status.setText("Start output and wait for the stream URL.");return;}android.content.ClipboardManager cb=(android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE);cb.setPrimaryClip(ClipData.newPlainText("Kenoshi stream",outputUrl(s)));Toast.makeText(this,"Stream URL copied",Toast.LENGTH_SHORT).show();}));
   controls.addView(button("Choose Cast device",v->{s.destination="Chromecast";save();cast.choose();}));
  }
  controls.addView(button("Full screen",v->toggleFull()));controls.addView(button("Retry",v->{if(isForward(s))beginForward(s);else{stop(s);start(s,index);}}));controls.addView(button("Close tab",v->new AlertDialog.Builder(this).setTitle("Close “"+s.name+"”?").setMessage("This session will stop.").setPositiveButton("Close",(d,w)->{if(isForward(s))stopForward(s);stop(s);content.removeView(s.view);sessions[index]=null;save();select(0);updateSetup();}).setNegativeButton("Cancel",null).show()));rowScroll.addView(controls);s.view.addView(rowScroll);
  s.surface.getHolder().addCallback(new SurfaceHolder.Callback(){public void surfaceCreated(SurfaceHolder h){s.surfaceReady=true;if(resumed&&active==index+1)start(s,index);}public void surfaceChanged(SurfaceHolder h,int f,int w,int height){}public void surfaceDestroyed(SurfaceHolder h){s.surfaceReady=false;stop(s);}});
  content.addView(s.view,new LinearLayout.LayoutParams(-1,-1));
 }
 private void select(int which){
  if(which>0&&sessions[which-1]==null)return;
  if(full)toggleFull();if(active>0&&active!=which&&sessions[active-1]!=null)stop(sessions[active-1]);active=which;
  setup.setVisibility(which==0?View.VISIBLE:View.GONE);
  for(int i=0;i<2;i++){tabs[i+1].setVisibility(sessions[i]==null?View.GONE:View.VISIBLE);if(sessions[i]!=null){tabs[i+1].setText(sessions[i].name+"\n"+sessions[i].type);sessions[i].view.setVisibility(which==i+1?View.VISIBLE:View.GONE);}}
  for(int i=0;i<3;i++){int accent=i==0?blue:i==1?green:purple;tabs[i].setTextColor(accent);tabs[i].setBackground(shape(i==which?blend(accent,panel):panel,8));tabs[i].setSelected(i==which);}
  if(which>0)start(sessions[which-1],which-1);
 }
 private int blend(int accent,int background){return Color.rgb((Color.red(accent)+Color.red(background)*5)/6,(Color.green(accent)+Color.green(background)*5)/6,(Color.blue(accent)+Color.blue(background)*5)/6);}
 private void start(Session s,int i){
  if(!resumed||!s.surfaceReady||s.started)return;
  if(isForward(s)){s.started=true;if(!ForwardService.running)beginForward(s);else updateForward(s);return;}
  if(!s.destination.equals("View in tab")){unavailable(s,"Forwarding currently requires an NDI feed source.");return;}
  if(s.category.equals("Capture")){unavailable(s,"Capture preview and broadcasting are not implemented yet.\nYour configuration is saved.");return;}
  if(s.type.equals("Cast")){unavailable(s,"Cast reception is not implemented yet.");return;}
  if(s.type.equals("NDI")){
   if(!NdiBridge.available()){unavailable(s,"NDI SDK required\n\nThis build cannot receive NDI yet.");return;}
   s.started=true;ndiOwner=i;long generation=++s.generation;unavailable(s,"Connecting to "+s.source+"…");
   worker.execute(()->{if(!dead&&resumed&&s.started&&s.generation==generation)NdiBridge.connect(s.source,s.url,s.surface.getHolder().getSurface(),s.proxy);});return;
  }
  if(s.type.equals("VLC stream")){
   s.started=true;long generation=++s.generation;unavailable(s,"Connecting…");
   try{MediaPlayer player=new MediaPlayer();s.player=player;player.setDataSource(this,Uri.parse(s.url));player.setDisplay(s.surface.getHolder());player.setVolume(s.muted?0:1,s.muted?0:1);player.setOnVideoSizeChangedListener((mp,w,h)->fit(s));player.setOnPreparedListener(mp->{if(s.player!=mp||s.generation!=generation)return;mp.start();s.overlay.setVisibility(View.GONE);s.status.setText("Playing · "+s.source+" · "+mp.getVideoWidth()+" × "+mp.getVideoHeight());fit(s);});player.setOnErrorListener((mp,what,extra)->{if(s.player==mp){stop(s);unavailable(s,"Cannot play this stream.\nCheck the URL, network and device codec support.");}return true;});player.setOnCompletionListener(mp->{if(s.player==mp){stop(s);unavailable(s,"Stream ended. Tap Retry to reconnect.");}});player.prepareAsync();}
   catch(Exception error){stop(s);unavailable(s,"Cannot open stream. Check the URL and network.");}
  }
 }
 private static boolean isNdi(String type){return type.equals("NDI")||type.equals("NDI / NDI HX");}
 private static boolean isUrl(String type){return type.equals("Stream URL")||type.equals("VLC stream");}
 private static boolean isHttp(String dest){return dest.equals("HTTP stream (HLS / TS)")||dest.equals("VLC-compatible stream");}
 private String outputUrl(Session s){return isHttp(s.destination)&&s.latency.equals("low")?ForwardService.streamUrl.replace("live.m3u8","live.ts"):ForwardService.streamUrl;}
 private boolean isForward(Session s){return s.type.equals("NDI")&&(s.destination.equals("Chromecast")||isHttp(s.destination));}
 private void beginForward(Session s){
  if(!NdiBridge.available()){unavailable(s,"NDI SDK is unavailable");return;}
  if(ForwardService.running&&!s.id.equals(ForwardService.sessionId)){new AlertDialog.Builder(this).setTitle("Replace active forwarding output?").setMessage("Only one NDI forwarding encoder runs at a time on this phone.").setPositiveButton("Replace",(d,n)->launchForward(s)).setNegativeButton("Cancel",null).show();return;}launchForward(s);
 }
 private void launchForward(Session s){if(!NdiBridge.available()){unavailable(s,"NDI SDK is unavailable in this build");return;}Intent intent=new Intent(this,ForwardService.class).putExtra("source",s.source).putExtra("url",s.url).putExtra("id",s.id).putExtra("latency",s.latency).putExtra("proxy",s.proxy);startForegroundService(intent);unavailable(s,"Starting output…");}
 private void stopForward(Session s){if(s.id.equals(ForwardService.sessionId)){if(cast!=null&&s.destination.equals("Chromecast"))cast.stop();stopService(new Intent(this,ForwardService.class));}s.started=false;unavailable(s,"Output stopped. Tap Start output to resume.");s.link.setVisibility(View.GONE);}
 private void updateForward(Session s){if(!s.id.equals(ForwardService.sessionId)||!ForwardService.running){unavailable(s,"Output stopped. Tap Start output to resume.");s.link.setVisibility(View.GONE);return;}
  ForwardService f=ForwardService.instance;if(f!=null)f.setMuted(s.muted);s.overlay.setText("NDI → "+(s.destination.equals("Chromecast")?"CHROMECAST":"VLC / HLS")+"\n\n"+ForwardService.state);s.overlay.setVisibility(View.VISIBLE);s.status.setText(ForwardService.state);s.link.setText(outputUrl(s));s.link.setVisibility(ForwardService.streamUrl.isEmpty()?View.GONE:View.VISIBLE);
  if(s.destination.equals("Chromecast")&&cast!=null){if(f!=null&&f.readySegments()>=LatencyProfile.from(ForwardService.latencyId).startSegments)cast.offer(ForwardService.streamUrl,s.name);s.status.setText(ForwardService.state+" · "+cast.status);}
 }
 private final Runnable forwardPoll=new Runnable(){public void run(){if(dead)return;for(Session s:sessions)if(s!=null&&isForward(s))updateForward(s);main.postDelayed(this,1000);}};
 private void unavailable(Session s,String message){s.overlay.setText(message);s.overlay.setVisibility(View.VISIBLE);s.status.setText(message.replace('\n',' '));}
 private void fit(Session s){s.stage.post(()->{int[] size=s.type.equals("NDI")?NdiBridge.videoSize():new int[]{0,0};int vw=s.player!=null?s.player.getVideoWidth():size[0],vh=s.player!=null?s.player.getVideoHeight():size[1],w=s.stage.getWidth(),h=s.stage.getHeight();if(vw<=0||vh<=0||w<=0||h<=0)return;float scale=Math.min((float)w/vw,(float)h/vh);s.surface.setLayoutParams(new FrameLayout.LayoutParams((int)(vw*scale),(int)(vh*scale),Gravity.CENTER));});}
 private void stop(Session s){s.started=false;s.generation++;if(s.player!=null){MediaPlayer p=s.player;s.player=null;p.setOnPreparedListener(null);p.setOnErrorListener(null);p.setOnCompletionListener(null);p.release();}if(ndiOwner>=0&&sessions[ndiOwner]==s){ndiOwner=-1;worker.execute(NdiBridge::disconnect);}}
 private void toggleFull(){if(active==0)return;full=!full;header.setVisibility(full?View.GONE:View.VISIBLE);tabbar.setVisibility(full?View.GONE:View.VISIBLE);footer.setVisibility(full?View.GONE:View.VISIBLE);Session session=sessions[active-1];session.toolbar.setVisibility(full?View.GONE:View.VISIBLE);session.controls.setVisibility(full?View.GONE:View.VISIBLE);session.status.setVisibility(full?View.GONE:View.VISIBLE);if(session.player!=null)fit(session);getWindow().getDecorView().setSystemUiVisibility(full?(View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY):(theme.equals("dark")?0:View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR));}
 private void save(){JSONArray a=new JSONArray();for(Session s:sessions){try{a.put(s==null?JSONObject.NULL:s.json());}catch(JSONException ignored){a.put(JSONObject.NULL);}}getPreferences(0).edit().putString("sessions",a.toString()).apply();}
 private void restore(){try{JSONArray a=new JSONArray(getPreferences(0).getString("sessions","[]"));for(int i=0;i<Math.min(2,a.length());i++)if(!a.isNull(i))sessions[i]=Session.parse(a.getJSONObject(i));}catch(JSONException ignored){}}
 @Override protected void onResume(){super.onResume();resumed=true;if(multicast!=null&&!multicast.isHeld())multicast.acquire();if(active>0&&sessions[active-1]!=null)start(sessions[active-1],active-1);}
 @Override protected void onPause(){resumed=false;for(Session s:sessions)if(s!=null)stop(s);if(multicast!=null&&multicast.isHeld())multicast.release();save();super.onPause();}
 @Override protected void onDestroy(){dead=true;if(cast!=null)cast.close();worker.execute(NdiBridge::shutdown);worker.shutdown();main.removeCallbacksAndMessages(null);super.onDestroy();}
}
