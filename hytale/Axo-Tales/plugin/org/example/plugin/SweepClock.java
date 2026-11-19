package org.example.plugin;

import java.util.Map;
import java.util.WeakHashMap;


final class SweepClock<K> {
    private final Map<K,Long> deadlines=new WeakHashMap<>();
    synchronized boolean due(K store,long now,long interval) {
        if(deadlines.getOrDefault(store,Long.MIN_VALUE)>now)return false;
        deadlines.put(store,now+interval);return true;
    }
}
