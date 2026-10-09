package com.wahshi.cryptoexplosionradar;
import org.json.*;
import java.util.*;
public final class PreExplosionJson {
 public static PreExplosionEngine.Result evaluate(JSONArray raw,long now)throws Exception{
  List<PreExplosionEngine.Bar> bars=new ArrayList<>();
  for(int i=0;i<raw.length();i++){JSONArray b=raw.getJSONArray(i);if(b.getLong(6)>=now)continue;bars.add(new PreExplosionEngine.Bar(b.getLong(0),b.getDouble(1),b.getDouble(2),b.getDouble(3),b.getDouble(4),b.getDouble(7),b.getLong(8),b.getDouble(10)));}
  return PreExplosionEngine.evaluate(bars,bars.size()-1);
 }
}
