package com.wahshi.cryptoexplosionradar;

import java.math.BigDecimal;
import java.util.*;
import org.json.JSONObject;

/** Signed changes over the observed account cohort only; never proof of a purchase. */
public final class HolderNet {
    public static final long MAX_GAP_MS = 24*60*60*1000L;
    private HolderNet() {}
    public static Map<String, BigDecimal> calculate(JSONObject old, JSONObject current, long now, long slot) throws org.json.JSONException {
        Map<String, BigDecimal> net = new HashMap<>();
        long then=old.optLong("at",0), previousSlot=old.optLong("slot",0);
        if (then<=0 || now<=then || now-then>MAX_GAP_MS || slot<=previousSlot) return net;
        JSONObject accounts=old.optJSONObject("accounts");
        if(accounts==null) return net;
        Set<String> invalidOwners=new HashSet<>();
        for(java.util.Iterator<String> keys=accounts.keys();keys.hasNext();) {
            String key=keys.next();
            JSONObject before=accounts.getJSONObject(key), after=current.optJSONObject(key);
            String owner=before.getString("owner");
            if(after==null) return Collections.emptyMap(); // incomplete cohort: no inference
            if(!owner.equals(after.getString("owner"))) {
                invalidOwners.add(owner); invalidOwners.add(after.getString("owner")); continue;
            }
            BigDecimal delta=new BigDecimal(after.getString("amount")).subtract(new BigDecimal(before.getString("amount")));
            net.put(owner,net.getOrDefault(owner,BigDecimal.ZERO).add(delta));
        }
        for(String owner:invalidOwners) net.remove(owner);
        return net;
    }
}
