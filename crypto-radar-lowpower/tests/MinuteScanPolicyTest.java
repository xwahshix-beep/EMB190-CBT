package com.wahshi.cryptoexplosionradar;
import java.util.*;
public final class MinuteScanPolicyTest {
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    public static void main(String[] args) {
        List<String> universe=new ArrayList<>();for(int i=0;i<150;i++)universe.add(String.format("COIN%03dUSDT",i));
        Set<String> priority=new HashSet<>(universe.subList(0,10));
        Map<String,Long> attempts=new HashMap<>();Set<String> seen=new HashSet<>();
        for(int cycle=1;cycle<=25;cycle++) {
            List<String> batch=MinuteScanPolicy.select(universe,priority,attempts,8);
            check(batch.size()==8&&new HashSet<>(batch).size()==8,"bounded distinct batch");
            check(batch.contains(universe.get(0))&&batch.contains(universe.get(1)),"priority slots");
            for(String s:batch){attempts.put(s,(long)cycle);seen.add(s);}
        }
        check(seen.size()==150,"persistent leaders must not starve discovery, including other priorities");
        // Interrupted cycles: only the first scheduled request was actually attempted.
        attempts.clear();seen.clear();
        for(int cycle=1;cycle<=150;cycle++) {
            List<String> batch=MinuteScanPolicy.select(universe,Collections.emptySet(),attempts,8);
            String attempted=batch.get(0);attempts.put(attempted,(long)cycle);seen.add(attempted);
        }
        check(seen.size()==150,"deadline must not advance past unattempted symbols");
        attempts.clear();attempts.put(universe.get(0),100L);
        check(!MinuteScanPolicy.select(universe,Collections.emptySet(),attempts,8).contains(universe.get(0)),"failed request rotates too");
        check(MinuteScanPolicy.select(Arrays.asList("A","A","B"),Collections.emptySet(),attempts,8).size()==2,"deduplicate");
        check(MinuteScanPolicy.select(universe,priority,attempts,0).isEmpty(),"zero budget");
        System.out.println("MinuteScanPolicyTest passed: coverage, priority, interruption, failure, bounds");
    }
}
