package com.wahshi.cryptoexplosionradar;
import org.json.JSONObject;
import java.math.BigDecimal;
import java.util.Map;
public class HolderNetTest {
    static JSONObject account(String owner,String amount) {return new JSONObject().put("owner",owner).put("amount",amount);}
    static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    public static void main(String[] args) throws Exception {
        JSONObject before=new JSONObject().put("a",account("owner1","200000"))
            .put("b",account("owner1","50000")).put("c",account("owner2","10"));
        JSONObject old=new JSONObject().put("at",1000).put("slot",10).put("accounts",before);
        JSONObject current=new JSONObject().put("a",account("owner1","100000"))
            .put("b",account("owner1","150000")).put("c",account("owner2","9"));
        Map<String,BigDecimal> net=HolderNet.calculate(old,current,2000,11);
        check(net.get("owner1").signum()==0,"internal transfer must net to zero");
        check(net.get("owner2").compareTo(new BigDecimal("-1"))==0,"negative flow preserved");
        current.put("new",account("owner3","1000000"));
        check(!HolderNet.calculate(old,current,2000,11).containsKey("owner3"),"new top holder is not zero baseline");
        current.put("a",account("owner1","100000.000000001"));
        check(HolderNet.calculate(old,current,2000,11).get("owner1").compareTo(new BigDecimal("0.000000001"))==0,"decimal precision");
        check(HolderNet.calculate(old,current,2000,10).isEmpty(),"same slot rejected");
        check(HolderNet.calculate(old,current,500,11).isEmpty(),"backward clock rejected");
        check(HolderNet.calculate(old,current,HolderNet.MAX_GAP_MS+1001,11).isEmpty(),"stale baseline rejected");
        current.put("b",account("changedOwner","150000"));
        check(!HolderNet.calculate(old,current,2000,11).containsKey("owner1"),"owner change invalidates owner total");
        current.remove("a");
        check(HolderNet.calculate(old,current,2000,11).isEmpty(),"missing or closed account is unknown, not zero");
        check(HolderNet.calculate(new JSONObject(),current,2000,11).isEmpty(),"first observation only baseline");
        System.out.println("HolderNetTest passed (10 scenarios)");
    }
}
