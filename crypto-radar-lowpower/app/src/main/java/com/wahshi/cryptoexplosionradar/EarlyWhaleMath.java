package com.wahshi.cryptoexplosionradar;

/** Exchange order-flow evidence, never a claim about wallet identity. */
public final class EarlyWhaleMath {
    private EarlyWhaleMath() {}
    public static double score(double rvol, double buyRatio, double priceChange,
                               int largeBuys, double largeBuyQuote, double largeSellQuote) {
        if (!Double.isFinite(rvol) || !Double.isFinite(buyRatio)
                || !Double.isFinite(priceChange) || rvol < 0 || buyRatio < 0 || buyRatio > 1
                || Math.abs(priceChange) > 2.5) return 0;
        double score = 0;
        if (rvol >= 1.4 && buyRatio >= .56) score += .65;
        if (rvol >= 2 && buyRatio >= .62) score += .45;
        if (largeBuys >= 2 && largeBuyQuote >= 75000 && largeBuyQuote > largeSellQuote * 1.5)
            score += .9;
        return score;
    }
}
