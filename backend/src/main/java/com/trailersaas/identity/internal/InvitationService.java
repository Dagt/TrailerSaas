package com.trailersaas.identity.internal;

import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import java.util.Map;
import java.util.List;
import com.trailersaas.audit.AuditWriter;
import static com.trailersaas.audit.AuditWriter.snapshot;
import static com.trailersaas.audit.AuditWriter.Action.*;
import com.trailersaas.identity.AuthenticatedUserPrincipal;
import com.trailersaas.identity.Capability;
import com.trailersaas.identity.TenantAuthorization;
import com.trailersaas.identity.TenantContext;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class InvitationService {
    private final InvitationRepository invitations;
    private final TenantAuthorization authorization;
    private final TenantContext tenants;
    private final InvitationOnboardingContext onboarding;
    private final InvitationToken tokens;
    private final Clock clock;
    private final AuditWriter audit;

    InvitationService(InvitationRepository invitations, TenantAuthorization authorization, TenantContext tenants,
            InvitationOnboardingContext onboarding, InvitationToken tokens, Clock clock, AuditWriter audit) {
        this.invitations = invitations; this.authorization = authorization; this.tenants = tenants;
        this.onboarding = onboarding; this.tokens = tokens; this.clock = clock;
        this.audit = audit;
    }
    @Transactional
    public InvitationContracts.Created create(AuthenticatedUserPrincipal user, UUID tenant, InvitationContracts.CreateRequest request) {
        authorization.requireTenantCapability(user, tenant, Capability.USER_INVITE);
        var email = InvitationContracts.normalizeEmail(request.email());
        try {
            var previous = invitations.lockPending(tenant, email);
            // Obtain time after waiting on the row lock.
            var now = clock.instant();
            if (previous.isPresent()) {
                if (previous.get().expiresAt().isAfter(now)) { throw InvitationFailure.conflict(); }
                invitations.status(previous.get(), InvitationStatus.EXPIRED);
                var old=previous.get();var roles=invitations.roles(tenant,old.id());
                audit.record(tenant,AuditWriter.Actor.system(),INVITATION_EXPIRED,old.id(),auditState(old,old.status(),roles,null),auditState(old,InvitationStatus.EXPIRED,roles,null),null);
            }
            String raw = tokens.generate();
            var invitation = new InvitationRepository.Invitation(UUID.randomUUID(), tenant, request.email(), email,
                    request.displayName(), InvitationStatus.PENDING, now.plus(Duration.ofDays(7)), null, now);
            invitations.create(invitation, user.userId(), InvitationToken.hash(raw), request.roles());
            audit.human(tenant,user.userId(),INVITATION_CREATED,invitation.id(),null,auditState(invitation,InvitationStatus.PENDING,request.roles(),null));
            return new InvitationContracts.Created(invitation.id(), invitation.email(), invitation.displayName(),
                    invitation.status(), invitation.expiresAt(), request.roles(), raw);
        } catch (DataIntegrityViolationException | ConstraintViolationException failure) {
            if (!InvitationIntegrity.isExpectedRejection(failure)) { throw failure; }
            throw InvitationFailure.conflict();
        }
    }
    @Transactional(readOnly = true)
    public InvitationContracts.Invitations list(AuthenticatedUserPrincipal user, UUID tenant, InvitationStatus status) {
        authorization.requireTenantCapability(user, tenant, Capability.USER_INVITE);
        return new InvitationContracts.Invitations(invitations.listForTenant(tenant, status).stream()
                .map(invitation -> summary(invitation, invitation.status())).toList());
    }
    @Transactional
    public InvitationContracts.Summary revoke(AuthenticatedUserPrincipal user, UUID tenant, UUID id) {
        authorization.requireTenantCapability(user, tenant, Capability.USER_INVITE);
        var invitation = invitations.lockForTenant(tenant, id).orElseThrow(InvitationFailure::missing);
        if (invitation.status() == InvitationStatus.ACCEPTED) { throw InvitationFailure.conflict(); }
        var status = invitation.status();
        if (status == InvitationStatus.PENDING) {
            status = invitation.expiresAt().isAfter(clock.instant()) ? InvitationStatus.REVOKED : InvitationStatus.EXPIRED;
            invitations.status(invitation, status);
            var roles=invitations.roles(tenant,id);
            var action=status==InvitationStatus.EXPIRED?INVITATION_EXPIRED:INVITATION_REVOKED;
            var actor=status==InvitationStatus.EXPIRED?AuditWriter.Actor.system():AuditWriter.Actor.human(user.userId());
            audit.record(tenant,actor,action,id,auditState(invitation,invitation.status(),roles,null),auditState(invitation,status,roles,null),null);
        }
        return summary(invitation, status);
    }
    @Transactional
    public InvitationContracts.Accepted accept(AuthenticatedUserPrincipal user, InvitationContracts.AcceptRequest request) {
        String hash = tokens.validatedHash(request.token());
        onboarding.establish(hash);
        var invitation = invitations.lockForOnboardingHash(hash).orElseThrow(InvitationFailure::notAcceptable);
        if (invitation.status() == InvitationStatus.ACCEPTED) {
            if (!user.userId().equals(invitation.acceptedByUserId())) { throw InvitationFailure.notAcceptable(); }
            tenants.establishTenant(invitation.tenantId());
            var membership = invitations.membershipForUser(invitation.tenantId(), user.userId())
                    .orElseThrow(InvitationFailure::notAcceptable);
            return accepted(invitation, membership);
        }
        var now = clock.instant();
        if (invitation.status() != InvitationStatus.PENDING || !invitation.expiresAt().isAfter(now)
                || !user.authenticatedEmailVerified() || user.authenticatedEmail() == null
                || !InvitationContracts.normalizeEmail(user.authenticatedEmail()).equals(invitation.emailNormalized())) {
            throw InvitationFailure.notAcceptable();
        }
        // The locked, eligible invitation is the sole source of this Tenant.
        // This exception grants no general capability and cannot be called by other modules.
        tenants.establishTenant(invitation.tenantId());
        if (invitations.membershipForUser(invitation.tenantId(), user.userId()).isPresent()) {
            throw InvitationFailure.notAcceptable();
        }
        var roles = invitations.roles(invitation.tenantId(), invitation.id());
        if (roles.isEmpty()) { throw InvitationFailure.notAcceptable(); }
        try {
            var membership=invitations.materialize(invitation, user.userId(), now, roles);
            audit.human(invitation.tenantId(),user.userId(),INVITATION_ACCEPTED,invitation.id(),auditState(invitation,invitation.status(),roles,null),auditState(invitation,InvitationStatus.ACCEPTED,roles,membership));
            audit.human(invitation.tenantId(),user.userId(),MEMBERSHIP_CREATED,membership,null,snapshot("userId",user.userId(),"status","ACTIVE","roles",auditRoles(roles)));
            return accepted(invitation, membership);
        } catch (DataIntegrityViolationException | ConstraintViolationException failure) {
            if (!InvitationIntegrity.isExpectedRejection(failure)) { throw failure; }
            // Throwing rolls back membership, every role and invitation together.
            throw InvitationFailure.notAcceptable();
        }
    }
    private InvitationContracts.Summary summary(InvitationRepository.Invitation invitation, InvitationStatus status) {
        return new InvitationContracts.Summary(invitation.id(), invitation.email(), invitation.displayName(), status,
                invitation.expiresAt(), invitations.roles(invitation.tenantId(), invitation.id()));
    }
    private static List<Map<String,Object>> auditRoles(List<InvitationContracts.Role> roles) {
        return roles.stream().map(r->snapshot("roleCode",r.roleCode().name(),"scopeType",r.scopeType().name(),"customerId",r.customerId())).toList();
    }
    private static Map<String,Object> auditState(InvitationRepository.Invitation i,InvitationStatus status,List<InvitationContracts.Role> roles,UUID membership) {
        // Identity tokens, hashes, e-mail and displayName never enter administrative snapshots.
        return snapshot("status",status.name(),"expiresAt",i.expiresAt(),"roles",auditRoles(roles),"membershipId",membership);
    }
    private InvitationContracts.Accepted accepted(InvitationRepository.Invitation invitation, UUID membership) {
        return new InvitationContracts.Accepted(invitation.id(), invitation.tenantId(), membership, true);
    }
}
