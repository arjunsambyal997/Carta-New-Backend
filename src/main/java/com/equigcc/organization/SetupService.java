package com.equigcc.organization;

import com.equigcc.audit.AuditAction;
import com.equigcc.audit.AuditService;
import com.equigcc.captable.ShareClass;
import com.equigcc.captable.ShareClassRepository;
import com.equigcc.captable.ShareClassType;
import com.equigcc.captable.Shareholder;
import com.equigcc.captable.ShareholderRepository;
import com.equigcc.captable.ShareholderType;
import com.equigcc.common.BusinessRuleException;
import com.equigcc.common.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

/** Creates the register's reference data: share classes and shareholders. */
@Service
public class SetupService {

    private final OrganizationRepository organizations;
    private final ShareClassRepository shareClasses;
    private final ShareholderRepository shareholders;
    private final AuditService audit;

    public SetupService(OrganizationRepository organizations, ShareClassRepository shareClasses,
                        ShareholderRepository shareholders, AuditService audit) {
        this.organizations = organizations;
        this.shareClasses = shareClasses;
        this.shareholders = shareholders;
        this.audit = audit;
    }

    @Transactional
    public ShareClass createShareClass(UUID orgId, SetupDtos.ShareClassRequest r, String user) {
        var org = organizations.findById(orgId).orElseThrow(() -> new NotFoundException("Organization " + orgId + " not found"));
        var c = new ShareClass();
        c.setOrganization(org);
        c.setName(r.name());
        c.setNameAr(r.nameAr());
        c.setClassType(r.classType());
        c.setParValue(r.parValue());
        c.setCurrency(r.currency());
        boolean noVotes = r.classType() == ShareClassType.NON_VOTING_ECONOMIC || r.classType() == ShareClassType.TREASURY;
        c.setVotingMultiplier(r.votingMultiplier() != null ? r.votingMultiplier() : noVotes ? BigDecimal.ZERO : BigDecimal.ONE);
        c.setDividendPreferenceRatio(r.dividendPreferenceRatio() != null ? r.dividendPreferenceRatio() : BigDecimal.ONE);
        c.setLiquidationMultiple(r.liquidationMultiple() != null ? r.liquidationMultiple() : BigDecimal.ZERO);
        c.setParticipating(r.participating());
        c.setSeniorityRank(r.seniorityRank());
        c.setAuthorizedShares(r.authorizedShares());
        if (r.classType() == ShareClassType.NON_VOTING_ECONOMIC && c.getVotingMultiplier().signum() != 0) {
            throw new BusinessRuleException("A non-voting class must have a voting multiplier of 0");
        }
        c = shareClasses.save(c);
        audit.record(orgId, "ShareClass", c.getId(), AuditAction.CREATE, "created share class " + c.getName(), user);
        return c;
    }

    @Transactional
    public Shareholder createShareholder(UUID orgId, SetupDtos.ShareholderRequest r, String user) {
        var org = organizations.findById(orgId).orElseThrow(() -> new NotFoundException("Organization " + orgId + " not found"));
        var s = new Shareholder();
        s.setOrganization(org);
        s.setHolderType(r.holderType());
        s.setFullNameEn(r.fullNameEn());
        s.setFullNameAr(r.fullNameAr());
        s.setPassportNo(r.passportNo());
        s.setNationalId(r.nationalId());
        s.setNationality(r.nationality() == null ? null : r.nationality().toUpperCase());
        s.setDateOfBirth(r.holderType() == ShareholderType.INDIVIDUAL ? r.dateOfBirth() : null);
        s.setTaxId(r.taxId());
        s.setRegistrationNo(r.registrationNo());
        s.setAddress(r.address());
        s.setEmail(r.email());
        s = shareholders.save(s);
        audit.record(orgId, "Shareholder", s.getId(), AuditAction.CREATE, "added shareholder " + s.getFullNameEn(), user);
        return s;
    }
}
