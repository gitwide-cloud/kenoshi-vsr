#include <jni.h>
#include <atomic>
#include <thread>
#include <mutex>
#include <chrono>
#include <vector>
#include <algorithm>
#include <Processing.NDI.Lib.h>
namespace {
std::mutex gate;
std::atomic<bool> live{false};
std::thread capture;
int clamp(int v){return std::max(0,std::min(255,v));}
void stopForward(){live=false;if(capture.joinable())capture.join();}
}
extern "C" JNIEXPORT void JNICALL Java_com_office_lanmonitor_ForwardService_nativeStop(JNIEnv*,jclass){std::lock_guard<std::mutex> l(gate);stopForward();}
extern "C" JNIEXPORT jboolean JNICALL Java_com_office_lanmonitor_ForwardService_nativeStart(JNIEnv* env,jclass,jstring jname,jstring jurl,jobject listener,jboolean proxy){
 std::lock_guard<std::mutex> l(gate);stopForward();if(!NDIlib_initialize())return false;
 auto utf=[&](jstring s){const char* p=env->GetStringUTFChars(s,nullptr);std::string v(p);env->ReleaseStringUTFChars(s,p);return v;};std::string name=utf(jname),url=utf(jurl);
 NDIlib_recv_create_v3_t config{};config.color_format=NDIlib_recv_color_format_RGBX_RGBA;config.bandwidth=proxy?NDIlib_recv_bandwidth_lowest:NDIlib_recv_bandwidth_highest;config.allow_video_fields=false;
 auto recv=NDIlib_recv_create_v3(&config);if(!recv)return false;
 NDIlib_source_t source{};source.p_ndi_name=name.c_str();source.p_url_address=url.empty()?nullptr:url.c_str();NDIlib_recv_connect(recv,&source);
 jobject target=env->NewGlobalRef(listener);jclass klass=env->GetObjectClass(listener);jmethodID video=env->GetMethodID(klass,"onVideo","([BIIJ)V"),audio=env->GetMethodID(klass,"onAudio","([BIJ)V"),signal=env->GetMethodID(klass,"onSignal","(Z)V");JavaVM* vm;env->GetJavaVM(&vm);live=true;
 capture=std::thread([recv,target,video,audio,signal,vm]{JNIEnv* e=nullptr;vm->AttachCurrentThread(&e,nullptr);auto epoch=std::chrono::steady_clock::now();int64_t previous=-100000;std::vector<uint8_t> pixels;std::vector<int16_t> pcm;
 while(live){NDIlib_video_frame_v2_t v{};NDIlib_audio_frame_v2_t a{};auto type=NDIlib_recv_capture_v2(recv,&v,&a,nullptr,100);int64_t pts=std::chrono::duration_cast<std::chrono::microseconds>(std::chrono::steady_clock::now()-epoch).count();
 if(type==NDIlib_frame_type_video){
  if(v.p_data&&v.xres>0&&v.yres>0&&v.line_stride_in_bytes>=v.xres*4&&(v.FourCC==NDIlib_FourCC_type_RGBX||v.FourCC==NDIlib_FourCC_type_RGBA)&&pts-previous>=32000){previous=pts;
   double scale=std::min(1.0,std::min(1280.0/v.xres,720.0/v.yres));int w=std::max(2,(int)(v.xres*scale)/2*2),h=std::max(2,(int)(v.yres*scale)/2*2);pixels.resize(w*h*3/2);int area=w*h;
   for(int y=0;y<h;y++)for(int x=0;x<w;x++){const uint8_t* rgb=v.p_data+(y*v.yres/h)*v.line_stride_in_bytes+(x*v.xres/w)*4;int r=rgb[0],g=rgb[1],b=rgb[2];pixels[y*w+x]=clamp(((66*r+129*g+25*b+128)>>8)+16);if(!(y&1)&&!(x&1)){int uv=(y/2)*(w/2)+x/2;pixels[area+uv]=clamp(((-38*r-74*g+112*b+128)>>8)+128);pixels[area+area/4+uv]=clamp(((112*r-94*g-18*b+128)>>8)+128);}}
   jbyteArray bytes=e->NewByteArray(pixels.size());e->SetByteArrayRegion(bytes,0,pixels.size(),(jbyte*)pixels.data());e->CallVoidMethod(target,video,bytes,w,h,(jlong)pts);e->DeleteLocalRef(bytes);
  }NDIlib_recv_free_video_v2(recv,&v);
 }else if(type==NDIlib_frame_type_audio){if(a.p_data&&a.no_samples>0&&a.no_channels>0&&a.sample_rate>0){pcm.resize(a.no_samples*2);for(int i=0;i<a.no_samples;i++)for(int ch=0;ch<2;ch++){auto channel=(float*)((uint8_t*)a.p_data+std::min(ch,a.no_channels-1)*a.channel_stride_in_bytes);float value=std::max(-1.f,std::min(1.f,channel[i]));pcm[i*2+ch]=(int16_t)(value*32767.f);}jbyteArray bytes=e->NewByteArray(pcm.size()*2);e->SetByteArrayRegion(bytes,0,pcm.size()*2,(jbyte*)pcm.data());int64_t start=std::max((int64_t)0,pts-(int64_t)a.no_samples*1000000/a.sample_rate);e->CallVoidMethod(target,audio,bytes,a.sample_rate,(jlong)start);e->DeleteLocalRef(bytes);}NDIlib_recv_free_audio_v2(recv,&a);
 }else if(type==NDIlib_frame_type_error){e->CallVoidMethod(target,signal,JNI_FALSE);break;}else if(type==NDIlib_frame_type_none){e->CallVoidMethod(target,signal,NDIlib_recv_get_no_connections(recv)?JNI_TRUE:JNI_FALSE);}
 if(e->ExceptionCheck()){e->ExceptionDescribe();e->ExceptionClear();break;}
 }
 NDIlib_recv_destroy(recv);e->DeleteGlobalRef(target);vm->DetachCurrentThread();});return true;
}
