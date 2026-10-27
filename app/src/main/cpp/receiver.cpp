#include <jni.h>
#include <android/native_window_jni.h>
#include <atomic>
#include <thread>
#include <mutex>
#include <string>
#include <vector>
#include <cstring>
#include <algorithm>
#ifdef HAS_NDI
#include <Processing.NDI.Lib.h>
#endif
namespace {
std::mutex api,state;std::string msg="Disconnected";
std::atomic<int> videoWidth{0},videoHeight{0};
std::atomic<bool> running{false};std::thread thread;
void report(std::string s){std::lock_guard<std::mutex> l(state);msg=s;}
void stop(){running=false;if(thread.joinable())thread.join();videoWidth=0;videoHeight=0;report("Disconnected");}
std::string str(JNIEnv* e,jstring s){if(!s)return {};const char* p=e->GetStringUTFChars(s,nullptr);std::string r(p);e->ReleaseStringUTFChars(s,p);return r;}
#ifdef HAS_NDI
bool initialized=false;NDIlib_find_instance_t finder=nullptr;
bool init(){if(!initialized)initialized=NDIlib_initialize();return initialized;}
#endif
}
extern "C" JNIEXPORT jboolean JNICALL Java_com_office_lanmonitor_NdiBridge_nativeAvailable(JNIEnv*,jclass){
#ifdef HAS_NDI
std::lock_guard<std::mutex> l(api);return init();
#else
return false;
#endif
}
extern "C" JNIEXPORT jobjectArray JNICALL Java_com_office_lanmonitor_NdiBridge_nativeDiscover(JNIEnv* e,jclass,jstring groups){
std::lock_guard<std::mutex> l(api);std::vector<std::string> found;
#ifdef HAS_NDI
if(init()){if(finder){NDIlib_find_destroy(finder);finder=nullptr;}std::string groupName=str(e,groups);{NDIlib_find_create_t c{};c.show_local_sources=true;c.p_groups=groupName.empty()?nullptr:groupName.c_str();finder=NDIlib_find_create_v2(&c);}if(finder){NDIlib_find_wait_for_sources(finder,1500);uint32_t count=0;auto s=NDIlib_find_get_current_sources(finder,&count);for(uint32_t i=0;i<count;i++){found.emplace_back(s[i].p_ndi_name?s[i].p_ndi_name:"");found.emplace_back(s[i].p_url_address?s[i].p_url_address:"");}}}
#endif
auto out=e->NewObjectArray((jsize)found.size(),e->FindClass("java/lang/String"),nullptr);for(size_t i=0;i<found.size();i++){auto s=e->NewStringUTF(found[i].c_str());e->SetObjectArrayElement(out,(jsize)i,s);e->DeleteLocalRef(s);}return out;
}
extern "C" JNIEXPORT jboolean JNICALL Java_com_office_lanmonitor_NdiBridge_nativeConnect(JNIEnv* e,jclass,jstring name,jstring url,jobject surface,jboolean proxy){
std::lock_guard<std::mutex> l(api);stop();
#ifdef HAS_NDI
if(!init()||!surface){report("Initialization failed");return false;}
std::string n=str(e,name),u=str(e,url);auto window=ANativeWindow_fromSurface(e,surface);if(!window){report("Display unavailable");return false;}
NDIlib_recv_create_v3_t c{};c.color_format=NDIlib_recv_color_format_RGBX_RGBA;c.bandwidth=proxy?NDIlib_recv_bandwidth_lowest:NDIlib_recv_bandwidth_highest;c.allow_video_fields=false;
auto recv=NDIlib_recv_create_v3(&c);if(!recv){ANativeWindow_release(window);report("Receiver creation failed");return false;}
NDIlib_source_t source{};source.p_ndi_name=n.c_str();source.p_url_address=u.empty()?nullptr:u.c_str();NDIlib_recv_connect(recv,&source);
running=true;report("Connecting — waiting for video");
thread=std::thread([recv,window]{int w=0,h=0,waits=0;while(running){NDIlib_video_frame_v2_t f{};auto type=NDIlib_recv_capture_v2(recv,&f,nullptr,nullptr,250);if(type==NDIlib_frame_type_video){waits=0;
if(f.p_data&&f.xres>0&&f.yres>0&&f.line_stride_in_bytes>=f.xres*4&&(f.FourCC==NDIlib_FourCC_type_RGBX||f.FourCC==NDIlib_FourCC_type_RGBA)){
if(w!=f.xres||h!=f.yres){w=f.xres;h=f.yres;videoWidth=w;videoHeight=h;ANativeWindow_setBuffersGeometry(window,w,h,WINDOW_FORMAT_RGBX_8888);}
ANativeWindow_Buffer b{};if(ANativeWindow_lock(window,&b,nullptr)==0){for(int y=0;y<std::min(h,b.height);y++)std::memcpy((char*)b.bits+y*b.stride*4,f.p_data+y*f.line_stride_in_bytes,std::min(w,b.width)*4);ANativeWindow_unlockAndPost(window);report("Receiving "+std::to_string(w)+" x "+std::to_string(h)+" • video only");}
}else report("Unsupported pixel format");NDIlib_recv_free_video_v2(recv,&f);
}else if(type==NDIlib_frame_type_error){report("Receiver error — select source again");break;}else if(type==NDIlib_frame_type_none&&++waits>=12)report("No video — waiting for source");}
NDIlib_recv_destroy(recv);ANativeWindow_release(window);});return true;
#else
report("NDI SDK missing — playback unavailable");return false;
#endif
}
extern "C" JNIEXPORT jstring JNICALL Java_com_office_lanmonitor_NdiBridge_nativeStatus(JNIEnv* e,jclass){
#ifdef HAS_NDI
std::lock_guard<std::mutex> l(state);return e->NewStringUTF(msg.c_str());
#else
return e->NewStringUTF("NDI SDK missing — playback unavailable");
#endif
}
extern "C" JNIEXPORT void JNICALL Java_com_office_lanmonitor_NdiBridge_nativeDisconnect(JNIEnv*,jclass){std::lock_guard<std::mutex> l(api);stop();}
extern "C" JNIEXPORT void JNICALL Java_com_office_lanmonitor_NdiBridge_nativeShutdown(JNIEnv*,jclass){std::lock_guard<std::mutex> l(api);stop();
#ifdef HAS_NDI
if(finder){NDIlib_find_destroy(finder);finder=nullptr;}// Runtime stays initialized while the forwarding service may own another receiver.
#endif
}

extern "C" JNIEXPORT jintArray JNICALL Java_com_office_lanmonitor_NdiBridge_nativeVideoSize(JNIEnv* e,jclass){jint dimensions[2]={videoWidth.load(),videoHeight.load()};auto out=e->NewIntArray(2);e->SetIntArrayRegion(out,0,2,dimensions);return out;}
