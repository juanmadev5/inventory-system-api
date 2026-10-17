package com.jm.inventory.application.audit;

/**
 * Application-owned abstraction for "who is making this write". The real implementation
 * (backed by the authenticated JWT) lives in {@code api}; {@link AuditLogService} depends
 * only on this interface.
 */
public interface CurrentActor {

    /**
     * @return the current caller's stable id (the JWT {@code sub} claim), or {@code null}
     *     if there is no authenticated caller in context - see
     *     docs/specs/07-audit-trail/requirements.md for the one legitimate case this
     *     happens (a direct application-layer call with no HTTP request behind it).
     */
    String actorId();

    /**
     * @return the current caller's human-readable username (the JWT {@code email} claim),
     *     or {@code null} if absent or there is no authenticated caller in context.
     */
    String actorUsername();
}
