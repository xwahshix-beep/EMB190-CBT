package com.wahshi.cryptoexplosionradar;
import org.json.JSONObject;
/** Fail closed: only the literal JSON boolean true permits production BUY pushes. */
public final class ProductionPolicy {
 public static boolean enabled(String json){try{return Boolean.TRUE.equals(new JSONObject(json).opt("recommended_production_buy_enabled"));}catch(Exception e){return false;}}
}
