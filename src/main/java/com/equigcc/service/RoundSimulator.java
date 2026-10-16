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

    public record Input(BigDecimal preMoney, BigDecimal investment, BigDecimal poolPct, String investorName) {
    }

    public record Row(UUID shareholderId, String name, long sharesBefore, BigDecimal pctBefore, long sharesAfter, BigDecimal pctAfter) {
    }

    public record Result(BigDecimal preMoney, BigDecimal investment, BigDecimal postMoney, BigDecimal pricePerShare,
                         long sharesBefore, long newInvestorShares, long newPoolShares, long sharesAfter, BigDecimal investorPct,
                         BigDecimal poolPct, List<Row> rows) {
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
                pct(investor, after), pct(pool, after), rows);
    }

    private static BigDecimal pct(long part, long whole) {
        return BigDecimal.valueOf(part).multiply(HUNDRED).divide(BigDecimal.valueOf(whole), 4, RoundingMode.HALF_UP);
    }
}
