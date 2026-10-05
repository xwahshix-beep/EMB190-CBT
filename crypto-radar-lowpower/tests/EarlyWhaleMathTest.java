package com.wahshi.cryptoexplosionradar;
public class EarlyWhaleMathTest {
    static void check(boolean ok,String why) { if(!ok) throw new AssertionError(why); }
    public static void main(String[] args) {
        check(EarlyWhaleMath.score(2.2,.65,.2,3,100000,10000)>=1.9,"quiet accumulation missed");
        check(EarlyWhaleMath.score(2.2,.35,.2,0,0,100000)==0,"sell flow labeled buy");
        check(EarlyWhaleMath.score(3,.8,4,3,100000,0)==0,"chasing pump");
        check(EarlyWhaleMath.score(1,.5,0,1,100000,0)==0,"single order sufficient");
        check(EarlyWhaleMath.score(1,.5,0,3,100000,150000)==0,"distribution sufficient");
        check(EarlyWhaleMath.score(Double.NaN,.7,0,3,100000,0)==0,"NaN accepted");
        check(EarlyWhaleMath.score(3,1.2,0,3,100000,0)==0,"invalid taker ratio");
        System.out.println("PASS: 7 Early Whale behavior checks");
    }
}
