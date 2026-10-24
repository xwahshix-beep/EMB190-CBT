package com.wahshi.cryptoexplosionradar;
/** Monotonic, in-memory session accounting. Not CPU usage or screen-off-only energy. */
public final class WakeMetrics {
 private long start=-1, limit, total; private int cycles, expired;
 public synchronized void acquired(long now,long timeout){if(start>=0)throw new IllegalStateException("Already held");start=now;limit=timeout;}
 public synchronized void released(long now){if(start<0)return;long elapsed=Math.max(0,now-start);total+=Math.min(limit,elapsed);cycles++;if(elapsed>=limit)expired++;start=-1;}
 public synchronized long totalMs(){return total;}
 public synchronized int cycles(){return cycles;}
 public synchronized int expired(){return expired;}
}
