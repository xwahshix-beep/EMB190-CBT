package com.wahshi.cryptoexplosionradar;
public class WatchPolicyTest {
 static int count=0;
 static void expect(String expected,double price,double change,double rv,double taker,double book,double spread,boolean c3,boolean c5){String actual=WatchPolicy.state(price,100,change,rv,taker,book,spread,c3,c5);if(!expected.equals(actual))throw new AssertionError(expected+" != "+actual);count++;}
 public static void main(String[] args){
 expect("BUY",100.2,.4,2,.65,.6,5,true,true);
 expect("WATCH",99.9,.4,2,.65,.6,5,true,true);
 expect("TOO_LATE",101,.4,2,.65,.6,5,true,true);
 expect("TOO_LATE",100.2,2.1,2,.65,.6,5,true,true);
 expect("WATCH",100.2,.4,2,.65,.6,5,false,true);
 expect("WATCH",100.2,.4,2,.65,.6,5,true,false);
 expect("WAIT",100.2,.4,1,.65,.6,5,true,true);
 expect("WAIT",100.2,.4,2,.45,.6,5,true,true);
 expect("WAIT",100.2,.4,2,.65,.4,5,true,true);
 expect("WAIT",100.2,.4,2,.65,.6,30,true,true);
 expect("DATA",Double.NaN,.4,2,.65,.6,5,true,true);
 expect("DATA",0,.4,2,.65,.6,5,true,true);
 System.out.println("WatchPolicy: "+count+" cases passed");
 }
}
