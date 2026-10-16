package com.equigcc.service;

import com.equigcc.service.CapTable.HolderPosition;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * What a funding round does to the cap table, without saving anything. The option pool top-up is created before the
 * investment (it dilutes existing holders, not the new investor), the usual term sheet convention.
 * Share counts are fully diluted: issued shares plus options, warrants and convertibles.
 */
@Service
public class RoundSimulator {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final MathContext MC = new MathContext(30, RoundingMode.HALF_UP);

    /**
     * A SAFE or convertible note that converts in this round. {@code amount} is the money put in (a note's principal);
     * a note also earns simple interest of {@code interestPct} a year for {@code months}. It converts at the lowest of
     * the round price, the round price less {@code discountPct}, and {@code capValuation} divided by the fully diluted
     * shares before the round (pool and converting instruments excluded). Converted shares sit in the pre-money.
     */
    public record Convertible(String name, String kind, BigDecimal amount, BigDecimal interestPct, Integer months, BigDecimal capValuation,
                              BigDecimal discountPct) {
    }

    public record Input(BigDecimal preMoney, BigDecimal investment, BigDecimal poolPct, String investorName, List<Convertible> convertibles) {

        public Input(BigDecimal preMoney, BigDecimal investment, BigDecimal poolPct, String investorName) {
            this(preMoney, investment, poolPct, investorName, List.of());
        }
    }

    /** How one SAFE or note converted: the amount including interest, the price it converted at, the shares, and why that price. */
    public record Conversion(String name, String kind, BigDecimal amountConverted, BigDecimal conversionPrice, long shares, String basis) {
    }

    public record Row(UUID shareholderId, String name, long sharesBefore, BigDecimal pctBefore, long sharesAfter, BigDecimal pctAfter) {
    }

    public record Result(BigDecimal preMoney, BigDecimal investment, BigDecimal postMoney, BigDecimal pricePerShare,
                         long sharesBefore, long newInvestorShares, long newPoolShares, long sharesAfter, BigDecimal investorPct,
                         BigDecimal poolPct, List<Row> rows, long convertedShares, List<Conversion> conversions) {
    }

    private final CapTableService capTables;

    public RoundSimulator(CapTableService capTables) {
        this.capTables = capTables;
    }

    @Transactional(readOnly = true)
    public Result simulate(UUID orgId, Input in) {
        return compute(capTables.calculate(orgId).holders(), in);
    }

    static Result compute(List<HolderPosition> holders, Input in) {
        if (in.preMoney() == null || in.preMoney().signum() <= 0) {
            throw new BusinessRuleException("The pre-money valuation must be positive");
        }
        if (in.investment() == null || in.investment().signum() <= 0) {
            throw new BusinessRuleException("The investment must be positive");
        }
        BigDecimal p = in.poolPct() == null ? BigDecimal.ZERO : in.poolPct().divide(HUNDRED, MC);
        if (p.signum() < 0) {
            throw new BusinessRuleException("The option pool cannot be negative");
        }
        long before = holders.stream().mapToLong(HolderPosition::fullyDilutedShares).sum();
        if (before <= 0) {
            throw new BusinessRuleException("There are no shares on the register to simulate a round against");
        }
        if (in.convertibles() != null && !in.convertibles().isEmpty()) {
            return withConvertibles(holders, in, before, p);
        }
        BigDecimal post = in.preMoney().add(in.investment());
        BigDecimal k = post.divide(in.preMoney(), MC);
        BigDecimal pk = p.multiply(k, MC);
        if (pk.compareTo(BigDecimal.ONE) >= 0) {
            throw new BusinessRuleException("The option pool is too large for this round: it would be at least the whole company");
        }
        BigDecimal preFd = BigDecimal.valueOf(before);
        BigDecimal postFd = preFd.multiply(k, MC).divide(BigDecimal.ONE.subtract(pk), MC);
        long pool = postFd.multiply(p, MC).setScale(0, RoundingMode.HALF_UP).longValueExact();
        BigDecimal price = in.preMoney().divide(BigDecimal.valueOf(before + pool), MC);
        long investor = in.investment().divide(price, MC).setScale(0, RoundingMode.DOWN).longValueExact();
        if (investor <= 0) {
            throw new BusinessRuleException("The investment is too small to buy a single share at this price");
        }
        long after = before + pool + investor;

        List<Row> rows = new ArrayList<>();
        for (HolderPosition h : holders) {
            rows.add(new Row(h.shareholderId(), h.nameEn(), h.fullyDilutedShares(), pct(h.fullyDilutedShares(), before),
                    h.fullyDilutedShares(), pct(h.fullyDilutedShares(), after)));
        }
        String investorName = in.investorName() == null || in.investorName().isBlank() ? "New investor" : in.investorName().trim();
        if (pool > 0) {
            rows.add(new Row(null, "Option pool (new)", 0, BigDecimal.ZERO.setScale(4), pool, pct(pool, after)));
        }
        rows.add(new Row(null, investorName, 0, BigDecimal.ZERO.setScale(4), investor, pct(investor, after)));
        return new Result(in.preMoney(), in.investment(), post, price.setScale(6, RoundingMode.HALF_UP), before, investor, pool, after,
                pct(investor, after), pct(pool, after), rows, 0, List.of());
    }

    /**
     * With SAFEs or notes the price, the converted shares and the pool depend on each other, so the round price is
     * found by bisection on {@code price x (existing + pool + converted) = pre-money}, which only ever increases with price.
     */
    private static Result withConvertibles(List<HolderPosition> holders, Input in, long before, BigDecimal poolFraction) {
        var cs = in.convertibles();
        if (cs.size() > 20) {
            throw new BusinessRuleException("At most 20 SAFEs or notes can be modelled at once");
        }
        double pre = in.preMoney().doubleValue();
        double inv = in.investment().doubleValue();
        double p = poolFraction.doubleValue();
        if (p >= 1) {
            throw new BusinessRuleException("The option pool is too large for this round");
        }
        int n = cs.size();
        BigDecimal[] amount = new BigDecimal[n];
        BigDecimal[] discount = new BigDecimal[n];
        BigDecimal[] capPrice = new BigDecimal[n];
        for (int i = 0; i < n; i++) {
            var c = cs.get(i);
            if (c.amount() == null || c.amount().signum() <= 0) {
                throw new BusinessRuleException("Each SAFE or note needs a positive amount");
            }
            BigDecimal d = c.discountPct() == null ? BigDecimal.ZERO : c.discountPct();
            if (d.signum() < 0 || d.compareTo(HUNDRED) >= 0) {
                throw new BusinessRuleException("A discount must be from 0 up to (not including) 100%");
            }
            if (c.capValuation() != null && c.capValuation().signum() <= 0) {
                throw new BusinessRuleException("A valuation cap must be positive");
            }
            BigDecimal a = c.amount();
            if ("NOTE".equalsIgnoreCase(c.kind()) && c.interestPct() != null && c.months() != null) {
                if (c.interestPct().signum() < 0 || c.months() < 0 || c.months() > 120) {
                    throw new BusinessRuleException("A note's interest must be 0 or more and its term 0 to 120 months");
                }
                a = a.add(a.multiply(c.interestPct()).multiply(BigDecimal.valueOf(c.months())).divide(BigDecimal.valueOf(1200), MC));
            }
            amount[i] = a;
            discount[i] = BigDecimal.ONE.subtract(d.divide(HUNDRED, MC));
            capPrice[i] = c.capValuation() == null ? null : c.capValuation().divide(BigDecimal.valueOf(before), MC);
        }

        double hi = pre / before;
        double lo = hi * 1e-9;
        if (gap(lo, pre, inv, p, before, amount, discount, capPrice) > 0) {
            throw new BusinessRuleException("The SAFEs, notes and option pool are too large for this pre-money valuation");
        }
        for (int i = 0; i < 200; i++) {
            double mid = (lo + hi) / 2;
            if (gap(mid, pre, inv, p, before, amount, discount, capPrice) > 0) {
                hi = mid;
            } else {
                lo = mid;
            }
        }
        BigDecimal price = BigDecimal.valueOf((lo + hi) / 2).setScale(6, RoundingMode.HALF_UP);
        if (price.signum() <= 0) {
            throw new BusinessRuleException("The SAFEs and notes are too large for this pre-money valuation");
        }

        List<Conversion> conversions = new ArrayList<>();
        long converted = 0;
        for (int i = 0; i < n; i++) {
            BigDecimal byDiscount = price.multiply(discount[i], MC);
            BigDecimal cp = byDiscount;
            String basis = discount[i].compareTo(BigDecimal.ONE) < 0 ? "DISCOUNT" : "ROUND";
            if (capPrice[i] != null && capPrice[i].compareTo(cp) < 0) {
                cp = capPrice[i];
                basis = "CAP";
            }
            long shares = amount[i].divide(cp, MC).setScale(0, RoundingMode.DOWN).longValueExact();
            converted += shares;
            var c = cs.get(i);
            String label = c.name() == null || c.name().isBlank() ? ("NOTE".equalsIgnoreCase(c.kind()) ? "Note " : "SAFE ") + (i + 1) : c.name().trim();
            conversions.add(new Conversion(label, "NOTE".equalsIgnoreCase(c.kind()) ? "NOTE" : "SAFE", amount[i].setScale(2, RoundingMode.HALF_UP),
                    cp.setScale(6, RoundingMode.HALF_UP), shares, basis));
        }
        long investor = in.investment().divide(price, MC).setScale(0, RoundingMode.DOWN).longValueExact();
        if (investor <= 0) {
            throw new BusinessRuleException("The investment is too small to buy a single share at this price");
        }
        long pool = poolFraction.signum() == 0 ? 0
                : BigDecimal.valueOf(before + converted + investor).multiply(poolFraction, MC).divide(BigDecimal.ONE.subtract(poolFraction), MC)
                        .setScale(0, RoundingMode.HALF_UP).longValueExact();
        long after = before + converted + pool + investor;

        List<Row> rows = new ArrayList<>();
        for (HolderPosition h : holders) {
            rows.add(new Row(h.shareholderId(), h.nameEn(), h.fullyDilutedShares(), pct(h.fullyDilutedShares(), before),
                    h.fullyDilutedShares(), pct(h.fullyDilutedShares(), after)));
        }
        for (Conversion c : conversions) {
            rows.add(new Row(null, c.name(), 0, BigDecimal.ZERO.setScale(4), c.shares(), pct(c.shares(), after)));
        }
        if (pool > 0) {
            rows.add(new Row(null, "Option pool (new)", 0, BigDecimal.ZERO.setScale(4), pool, pct(pool, after)));
        }
        String investorName = in.investorName() == null || in.investorName().isBlank() ? "New investor" : in.investorName().trim();
        rows.add(new Row(null, investorName, 0, BigDecimal.ZERO.setScale(4), investor, pct(investor, after)));
        return new Result(in.preMoney(), in.investment(), in.preMoney().add(in.investment()), price, before, investor, pool, after,
                pct(investor, after), pct(pool, after), rows, converted, conversions);
    }

    /** price x (existing + pool + converted shares) minus the pre-money: zero at the round price. */
    private static double gap(double price, double pre, double inv, double p, long before, BigDecimal[] amount, BigDecimal[] discount, BigDecimal[] capPrice) {
        double converted = 0;
        for (int i = 0; i < amount.length; i++) {
            double cp = price * discount[i].doubleValue();
            if (capPrice[i] != null) {
                cp = Math.min(cp, capPrice[i].doubleValue());
            }
            converted += amount[i].doubleValue() / cp;
        }
        double investor = inv / price;
        double pool = p * (before + converted + investor) / (1 - p);
        return price * (before + pool + converted) - pre;
    }

    private static BigDecimal pct(long part, long whole) {
        return BigDecimal.valueOf(part).multiply(HUNDRED).divide(BigDecimal.valueOf(whole), 4, RoundingMode.HALF_UP);
    }
}
