package com.equigcc.service;

import com.equigcc.domain.AuditAction;
import com.equigcc.domain.Currency;
import com.equigcc.domain.InstrumentType;
import com.equigcc.domain.IssuanceStatus;
import com.equigcc.domain.Organization;
import com.equigcc.domain.ShareClass;
import com.equigcc.domain.ShareIssuance;
import com.equigcc.domain.VestingSchedule;
import com.equigcc.repository.OrganizationRepository;
import com.equigcc.repository.ShareClassRepository;
import com.equigcc.repository.ShareIssuanceRepository;
import com.equigcc.repository.VestingScheduleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Vesting and exercise of option grants. Options are certificates of instrument type OPTION; a vesting schedule says how
 * many of them can be exercised by a given date (monthly after the cliff). Exercising retires the option certificate and
 * issues a share certificate (and a remainder option certificate) that point back to it, so the register keeps the chain.
 */
@Service
public class VestingService {

    public record Status(UUID id, String certificateNo, UUID shareholderId, String holderNameEn, String holderNameAr, String shareClassName,
                         BigDecimal strikePrice, Currency currency, long granted, long exercised, long vested, long exercisable, long unvested,
                         LocalDate startDate, int cliffMonths, int vestingMonths, LocalDate cliffDate, LocalDate fullyVestedDate, LocalDate nextVestDate) {
    }

    private final OrganizationRepository organizations;
    private final ShareIssuanceRepository issuances;
    private final ShareClassRepository classes;
    private final VestingScheduleRepository schedules;
    private final CertificateHasher hasher;
    private final AuditService audit;
    private final Clock clock;

    public VestingService(OrganizationRepository organizations, ShareIssuanceRepository issuances, ShareClassRepository classes,
                          VestingScheduleRepository schedules, CertificateHasher hasher, AuditService audit, Clock clock) {
        this.organizations = organizations;
        this.issuances = issuances;
        this.classes = classes;
        this.schedules = schedules;
        this.hasher = hasher;
        this.audit = audit;
        this.clock = clock;
    }

    /** Shares vested on a date: nothing before the cliff, then a whole-month proportion until fully vested. */
    static long vestedShares(long granted, LocalDate start, int cliffMonths, int vestingMonths, LocalDate asOf) {
        if (asOf.isBefore(start)) {
            return 0;
        }
        long months = ChronoUnit.MONTHS.between(start, asOf);
        if (months < cliffMonths) {
            return 0;
        }
        if (months >= vestingMonths) {
            return granted;
        }
        return BigDecimal.valueOf(granted).multiply(BigDecimal.valueOf(months)).divide(BigDecimal.valueOf(vestingMonths), 0, java.math.RoundingMode.DOWN).longValueExact();
    }

    @Transactional
    public Status schedule(UUID orgId, String certificateNo, LocalDate start, int cliffMonths, int vestingMonths, String user) {
        if (vestingMonths <= 0 || cliffMonths < 0 || cliffMonths > vestingMonths) {
            throw new BusinessRuleException("Vesting needs a positive number of months and a cliff no longer than the vesting period");
        }
        organizations.findByIdForUpdate(orgId).orElseThrow(() -> new NotFoundException("Organization " + orgId + " not found"));
        ShareIssuance cert = option(orgId, certificateNo);
        if (schedules.findByCertificate(cert.getId(), orgId).isPresent()) {
            throw new BusinessRuleException("Certificate " + certificateNo + " already has a vesting schedule");
        }
        var v = new VestingSchedule();
        v.setOrganization(cert.getOrganization());
        v.setCertificate(cert);
        v.setGrantedQuantity(cert.getQuantity());
        v.setStartDate(start);
        v.setCliffMonths(cliffMonths);
        v.setVestingMonths(vestingMonths);
        v.setCreatedBy(user);
        v = schedules.save(v);
        audit.record(orgId, "VestingSchedule", v.getId(), AuditAction.CREATE,
                "vesting for option certificate " + certificateNo + ": " + cert.getQuantity() + " options from " + start + ", cliff " + cliffMonths + " months, over " + vestingMonths + " months", user);
        return status(v, LocalDate.now(clock));
    }

    @Transactional(readOnly = true)
    public List<Status> list(UUID orgId, LocalDate asOf) {
        LocalDate on = asOf != null ? asOf : LocalDate.now(clock);
        return schedules.findByOrganization(orgId).stream().map(v -> status(v, on)).toList();
    }

    /** Exercises options: the vested, unexercised part of the grant can be converted into shares of the chosen class. */
    @Transactional
    public List<ShareIssuance> exercise(UUID orgId, String certificateNo, long quantity, UUID targetClassId, LocalDate date, String user) {
        if (quantity <= 0) {
            throw new BusinessRuleException("The quantity must be positive");
        }
        Organization org = organizations.findByIdForUpdate(orgId).orElseThrow(() -> new NotFoundException("Organization " + orgId + " not found"));
        ShareIssuance old = option(orgId, certificateNo);
        LocalDate on = date != null ? date : LocalDate.now(clock);
        VestingSchedule schedule = schedules.findByCertificate(old.getId(), orgId).orElse(null);
        long exercisable = schedule == null ? old.getQuantity() : Math.min(old.getQuantity(), status(schedule, on).exercisable());
        if (quantity > exercisable) {
            throw new BusinessRuleException("Only " + exercisable + " options can be exercised on " + on + ", not " + quantity);
        }
        ShareClass target = targetClassId == null ? old.getShareClass()
                : classes.findByIdAndOrganizationId(targetClassId, orgId).orElseThrow(() -> new NotFoundException("Share class " + targetClassId + " not found in organization"));
        if (target.isTreasury()) {
            throw new BusinessRuleException("Options cannot be exercised into the treasury class");
        }
        if (target.getCurrency() != old.getCurrency()) {
            throw new BusinessRuleException("The share class currency must match the option currency " + old.getCurrency());
        }
        if (old.getPricePerShare().compareTo(target.getParValue()) < 0) {
            throw new BusinessRuleException("The exercise price " + old.getPricePerShare() + " is below the par value " + target.getParValue() + " of " + target.getName());
        }
        long remainder = old.getQuantity() - quantity;
        old.setStatus(IssuanceStatus.CONVERTED);
        issuances.flush();
        if (target.getAuthorizedShares() != null) {
            long need = quantity + (target.getId().equals(old.getShareClass().getId()) ? remainder : 0);
            long room = target.getAuthorizedShares() - issuances.sumIssuedQuantityByClass(target.getId());
            if (need > room) {
                throw new BusinessRuleException("Exercising " + quantity + " options exceeds the authorized " + target.getName() + " shares (" + room + " remaining)");
            }
        }
        List<ShareIssuance> created = new ArrayList<>();
        ShareIssuance shares = replacement(org, old, InstrumentType.SHARES, target, quantity, on, user);
        created.add(shares);
        audit.record(orgId, "ShareIssuance", shares.getId(), AuditAction.CREATE,
                "option exercise: " + quantity + " options of certificate " + old.getCertificateNo() + " became " + quantity + " " + target.getName()
                        + " shares at " + old.getPricePerShare().toPlainString() + " (certificate " + shares.getCertificateNo() + ")", user);
        ShareIssuance rest = null;
        if (remainder > 0) {
            rest = replacement(org, old, InstrumentType.OPTION, old.getShareClass(), remainder, on, user);
            created.add(rest);
            audit.record(orgId, "ShareIssuance", rest.getId(), AuditAction.CREATE,
                    "remaining " + remainder + " options kept as certificate " + rest.getCertificateNo(), user);
        }
        if (schedule != null) {
            schedule.setExercisedQuantity(schedule.getExercisedQuantity() + quantity);
            if (rest != null) {
                schedule.setCertificate(rest);
            }
        }
        return created;
    }

    private Status status(VestingSchedule v, LocalDate asOf) {
        ShareIssuance c = v.getCertificate();
        long vested = vestedShares(v.getGrantedQuantity(), v.getStartDate(), v.getCliffMonths(), v.getVestingMonths(), asOf);
        long exercisable = Math.max(0, vested - v.getExercisedQuantity());
        LocalDate cliff = v.getStartDate().plusMonths(v.getCliffMonths());
        LocalDate end = v.getStartDate().plusMonths(v.getVestingMonths());
        LocalDate next = null;
        if (vested < v.getGrantedQuantity()) {
            long months = Math.max(0, ChronoUnit.MONTHS.between(v.getStartDate(), asOf));
            next = asOf.isBefore(cliff) ? cliff : v.getStartDate().plusMonths(months + 1);
        }
        return new Status(v.getId(), c.getCertificateNo(), c.getShareholder().getId(), c.getShareholder().getFullNameEn(), c.getShareholder().getFullNameAr(),
                c.getShareClass().getName(), c.getPricePerShare(), c.getCurrency(), v.getGrantedQuantity(), v.getExercisedQuantity(), vested, exercisable,
                v.getGrantedQuantity() - vested, v.getStartDate(), v.getCliffMonths(), v.getVestingMonths(), cliff, end, next);
    }

    private ShareIssuance option(UUID orgId, String certificateNo) {
        ShareIssuance c = issuances.findByCertificateNo(certificateNo).orElseThrow(() -> new NotFoundException("Certificate " + certificateNo + " not found"));
        if (!c.getOrganization().getId().equals(orgId)) {
            throw new NotFoundException("Certificate " + certificateNo + " not found");
        }
        if (c.getInstrumentType() != InstrumentType.OPTION) {
            throw new BusinessRuleException("Certificate " + certificateNo + " is not an option grant");
        }
        if (c.getStatus() != IssuanceStatus.ISSUED) {
            throw new BusinessRuleException("Certificate " + certificateNo + " is no longer active (" + c.getStatus() + ")");
        }
        return c;
    }

    private ShareIssuance replacement(Organization org, ShareIssuance old, InstrumentType type, ShareClass cls, long qty, LocalDate date, String user) {
        var n = new ShareIssuance();
        n.setOrganization(org);
        n.setShareClass(cls);
        n.setShareholder(old.getShareholder());
        n.setInstrumentType(type);
        n.setQuantity(qty);
        n.setPricePerShare(old.getPricePerShare());
        n.setCurrency(old.getCurrency());
        n.setDateIssued(date);
        n.setIssuedBy(user);
        n.setPredecessorId(old.getId());
        n.setCertificateNo(org.nextCertificateNumber());
        n.setRecordHash(hasher.hash(n));
        return issuances.save(n);
    }
}
