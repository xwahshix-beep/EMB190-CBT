package com.wahshi.cryptoexplosionradar;
import org.json.JSONArray;
public class EntryWindowTest {
 static int count;static final long START=60000000,NOW=START+60*60000+1000;
 interface Operation{void run()throws Exception;}
 static void fail(Operation op)throws Exception{try{op.run();throw new AssertionError("Expected invalid series");}catch(AssertionError e){throw e;}catch(Exception expected){count++;}}
 static JSONArray rows(){JSONArray a=new JSONArray();for(int i=0;i<60;i++)a.put(new JSONArray().put(START+i*60000).put(99.8).put(100).put(99.6).put(99.9).put(10).put(START+(i+1)*60000-1).put(1000).put(20).put(5).put(600));return a;}
 public static void main(String[] args)throws Exception{
  EntryWindow w=EntryWindow.parse(rows(),NOW);if(w.resistance!=100||w.closedAt!=START+60*60000-1||w.reason(100.2,NOW)!=null)throw new AssertionError("valid window");count++;
  JSONArray partial=rows();partial.put(new JSONArray().put(START+60*60000).put(99).put(999).put(1).put(900).put(1).put(START+61*60000-1));
  EntryWindow v=EntryWindow.parse(partial,NOW);if(v.resistance!=100||v.closedAt!=w.closedAt)throw new AssertionError("Open candle used");count++;
  JSONArray gap=rows();gap.remove(20);fail(()->EntryWindow.parse(gap,NOW));
  JSONArray duplicate=rows();duplicate.put(40,duplicate.getJSONArray(39));fail(()->EntryWindow.parse(duplicate,NOW));
  JSONArray broken=rows();broken.getJSONArray(59).put(3,101);fail(()->EntryWindow.parse(broken,NOW));
  fail(()->EntryWindow.parse(new JSONArray(),NOW));
  if(w.reason(100.2,NOW+80000)==null)throw new AssertionError("stale window accepted");count++;
  System.out.println("EntryWindow: "+count+" cases passed");
 }
}
