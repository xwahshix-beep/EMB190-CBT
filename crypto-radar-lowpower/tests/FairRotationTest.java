package com.wahshi.cryptoexplosionradar;
import java.util.*;
public class FairRotationTest {
    static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    public static void main(String[] args){
        List<String> all=Arrays.asList("G","A","B","C","D","E","F");
        String cursor=""; Set<String> seen=new HashSet<>();
        for(int cycle=0;cycle<3;cycle++) for(String s:FairRotation.next(all,cursor,3)){seen.add(s);cursor=s;}
        check(seen.size()==7,"all symbols covered, not just quiet head");
        check(FairRotation.next(all,"A",3).equals(Arrays.asList("B","C","D")),"interrupted batch resumes after last attempt");
        check(FairRotation.next(Arrays.asList("A","D","G"),"C",3).equals(Arrays.asList("D","G","A")),"removed cursor symbol");
        check(FairRotation.next(Arrays.asList("A","A","B"),"B",3).equals(Arrays.asList("A","B")),"wrap and dedup");
        check(FairRotation.next(Collections.emptyList(),"",3).isEmpty(),"empty universe");
        System.out.println("FairRotationTest passed (5 scenarios)");
    }
}
