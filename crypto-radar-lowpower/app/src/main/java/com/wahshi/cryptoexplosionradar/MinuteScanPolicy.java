package com.wahshi.cryptoexplosionradar;

import java.util.*;

/** Bounded priority lane plus oldest-attempt-first discovery, independent of hourly score. */
public final class MinuteScanPolicy {
    private MinuteScanPolicy() {}
    public static List<String> select(List<String> ranked, Set<String> priority,
                                      Map<String,Long> attempts, int limit) {
        List<String> unique=new ArrayList<>(new LinkedHashSet<>(ranked));
        List<String> out=new ArrayList<>();
        if(limit<=0)return out;
        int reserved=Math.min(2,limit/2);
        for(String s:unique)if(priority.contains(s)&&out.size()<reserved)out.add(s);
        unique.removeAll(out);
        unique.sort((a,b)->{
            int age=Long.compare(attempts.getOrDefault(a,0L),attempts.getOrDefault(b,0L));
            return age!=0?age:a.compareTo(b);
        });
        for(String s:unique){if(out.size()>=limit)break;out.add(s);}
        return out;
    }
}
