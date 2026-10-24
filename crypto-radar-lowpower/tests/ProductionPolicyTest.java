package com.wahshi.cryptoexplosionradar;
public final class ProductionPolicyTest {
 public static void main(String[] args){
  String[] denied={null,"","bad","{}","{\"recommended_production_buy_enabled\":false}","{\"recommended_production_buy_enabled\":\"true\"}","{\"recommended_production_buy_enabled\":1}","{\"recommended_production_buy_enabled\":null}"};
  for(String x:denied)if(ProductionPolicy.enabled(x))throw new AssertionError(x);
  if(!ProductionPolicy.enabled("{\"recommended_production_buy_enabled\":true}"))throw new AssertionError("explicit true");
  System.out.println("ProductionPolicyTest: 9 cases passed");
 }
}
