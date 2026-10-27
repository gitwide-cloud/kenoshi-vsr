package com.office.lanmonitor;
/** Immutable, shared encoder/muxer/player policy. Low latency trades buffering margin for speed. */
public final class LatencyProfile {
 public static final LatencyProfile STABLE=new LatencyProfile("stable",2000000L,1000000L,2,3,6);
 public static final LatencyProfile LOW=new LatencyProfile("low",500000L,400000L,1,2,10);
 public final String id;public final long keyframeUs,minSegmentUs;public final int targetSeconds,startSegments,windowSegments;
 private LatencyProfile(String id,long key,long min,int target,int start,int window){this.id=id;keyframeUs=key;minSegmentUs=min;targetSeconds=target;startSegments=start;windowSegments=window;}
 public static LatencyProfile from(String id){return "low".equals(id)?LOW:STABLE;}
}
