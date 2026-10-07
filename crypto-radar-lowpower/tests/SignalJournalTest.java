package com.wahshi.cryptoexplosionradar;
import org.json.*;
public final class SignalJournalTest {
 static int n;static void check(boolean ok,String message){n++;if(!ok)throw new AssertionError(message);}
 public static void main(String[] args)throws Exception{
  JSONObject book=new JSONObject();
  SignalJournal.record(book,"ZECUSDT",1000,1334.62,1333,1338,500,76000,4000);
  check(book.length()==1,"first BUY saved");
  SignalJournal.record(book,"ZECUSDT",1000,1335,1334,1339,600,80000,8000);
  JSONObject z=book.getJSONObject("ZECUSDT");
  check(book.length()==1&&z.getDouble("price")==1334.62,"rechecks do not duplicate or change initial price");
  check(z.getLong("recordedAt")==4000&&z.getLong("until")==80000,"original confirmation time preserved, data freshness renewed");
  for(int i=0;i<12;i++)SignalJournal.record(book,"COIN"+i+"USDT",2000+i,10,9,11,500,76000,9000+i);
  check(SignalJournal.render(book,10000,true).contains("ZEC/USDT"),"ZEC remains after more than seven new candidates");
  check(SignalJournal.render(book,80000,true).contains("ZEC/USDT"),"expiry does not remove ZEC");
  check(!SignalJournal.state(z,80000,true).contains("🟢"),"expired record is not live BUY");
  JSONObject restarted=new JSONObject(book.toString());
  check(restarted.getJSONObject("ZECUSDT").getDouble("price")==1334.62,"restart preserves price and symbol");
  SignalJournal.end(restarted,"ZECUSDT","CANCELLED","خرج السعر من النطاق",20000);
  check(SignalJournal.render(restarted,21000,true).contains("خرج السعر من النطاق"),"cancellation reason visible");
  SignalJournal.end(restarted,"ZECUSDT","WAIT","other reason",22000);
  check(restarted.getJSONObject("ZECUSDT").getString("reason").equals("خرج السعر من النطاق"),"later WAIT preserves reason for cancellation");
  check(SignalJournal.render(restarted,90000,false).contains("ZEC/USDT"),"stop does not hide historical card");
  SignalJournal.record(restarted,"ZECUSDT",90000,1340,1339,1345,90000,160000,100000);
  check(restarted.length()==13&&SignalJournal.rows(restarted).get(0).getString("symbol").equals("ZECUSDT"),"new signal replaces same symbol and moves to top");
  check(SignalJournal.state(restarted.getJSONObject("ZECUSDT"),120000,true).contains("يلزم تحديث"),"dropping from scan cannot imply fresh BUY");
  check(SignalJournal.render(new JSONObject(),10000,true).contains("لا توجد"),"empty history explanatory");
  System.out.println("SignalJournal: "+n+" tests passed");
 }
}
