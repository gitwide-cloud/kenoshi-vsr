package com.office.lanmonitor;
import android.app.Activity;
import android.content.Context;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class)
@Config(sdk=28)
public class MainActivityTest {
 @Before public void clear(){RuntimeEnvironment.getApplication().getSharedPreferences("MainActivity",Context.MODE_PRIVATE).edit().clear().commit();}
 private TextView find(View root,String value){if(root instanceof TextView&&value.equals(((TextView)root).getText().toString()))return (TextView)root;if(root instanceof ViewGroup){ViewGroup g=(ViewGroup)root;for(int i=0;i<g.getChildCount();i++){TextView v=find(g.getChildAt(i),value);if(v!=null)return v;}}return null;}
 @Test public void coldStartExplainsUnavailableNdiWithoutNativeLibrary(){try(var controller=Robolectric.buildActivity(MainActivity.class).setup()){MainActivity a=controller.get();Shadows.shadowOf(Looper.getMainLooper()).idle();assertNotNull(find(a.getWindow().getDecorView(),"Set up a session"));assertNotNull(find(a.getWindow().getDecorView(),"0 of 2 session slots occupied"));assertFalse(NdiBridge.available());}}
 @Test public void twoSessionsRestoreAndRequireReplacement(){String session="{\"name\":\"Saved feed\",\"mode\":\"Receive\",\"category\":\"Feed\",\"type\":\"NDI\",\"source\":\"NDI source\",\"destination\":\"View in tab\"}";RuntimeEnvironment.getApplication().getSharedPreferences("MainActivity",Context.MODE_PRIVATE).edit().putString("sessions","["+session+","+session+"]").commit();try(var controller=Robolectric.buildActivity(MainActivity.class).setup()){MainActivity a=controller.get();Shadows.shadowOf(Looper.getMainLooper()).idle();View root=a.getWindow().getDecorView();assertNotNull(find(root,"2 of 2 session slots occupied"));assertNotNull(find(root,"Replace a session\u2026"));assertEquals(View.VISIBLE,find(root,"Set up a session").getVisibility());}}

 private android.widget.Spinner control(View root,String label){if(root instanceof android.widget.Spinner&&label.contentEquals(root.getContentDescription()==null?"":root.getContentDescription()))return (android.widget.Spinner)root;if(root instanceof ViewGroup){ViewGroup g=(ViewGroup)root;for(int n=0;n<g.getChildCount();n++){android.widget.Spinner result=control(g.getChildAt(n),label);if(result!=null)return result;}}return null;}
 @Test public void feedSourcesExcludeCastAndDestinationDoesNotChangeSource(){try(var controller=Robolectric.buildActivity(MainActivity.class).setup()){
  View root=controller.get().getWindow().getDecorView();Shadows.shadowOf(Looper.getMainLooper()).idle();
  android.widget.Spinner category=control(root,"Source category"),type=control(root,"Source type"),destination=control(root,"Output destination");
  assertEquals("NDI / NDI HX",type.getSelectedItem().toString());
  for(int n=0;n<type.getCount();n++)assertNotEquals("Cast",type.getItemAtPosition(n).toString());
  category.setSelection(1);Shadows.shadowOf(Looper.getMainLooper()).idle();assertEquals("Camera",type.getSelectedItem().toString());
  category.setSelection(0);Shadows.shadowOf(Looper.getMainLooper()).idle();assertEquals("NDI / NDI HX",type.getSelectedItem().toString());
  destination.setSelection(1);Shadows.shadowOf(Looper.getMainLooper()).idle();assertEquals("Chromecast",destination.getSelectedItem().toString());assertEquals("NDI / NDI HX",type.getSelectedItem().toString());
  destination.setSelection(2);Shadows.shadowOf(Looper.getMainLooper()).idle();assertEquals("HTTP stream (HLS / TS)",destination.getSelectedItem().toString());assertEquals("NDI / NDI HX",type.getSelectedItem().toString());
 }}

 @Test public void oldSessionsDefaultStableAndNewOptionsPersist()throws Exception{MainActivity.Session old=MainActivity.Session.parse(new org.json.JSONObject());assertEquals("stable",old.latency);assertFalse(old.proxy);old.latency="low";old.proxy=true;MainActivity.Session restored=MainActivity.Session.parse(old.json());assertEquals("low",restored.latency);assertTrue(restored.proxy);}
}
