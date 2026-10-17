# Feature spec: User management

Status: **implemented and tested**.

Scope: this application is its own identity provider (see [docs/security.md](../../security.md))
- there is no external directory to manage users in by hand. This feature lets an `ADMIN`
  create, list, and manage every other user's role/active status from this API itself.
  Platform-level, cross-cutting, builds on
  [06-roles-and-permissions](../06-roles-and-permissions/requirements.md) and
  [07-audit-trail](../07-audit-trail/requirements.md).

## Decision

- **Users are persisted locally** - `id`, `email` (globally unique), `passwordHash`
  (BCrypt), `role` (`STAFF`/`MANAGER`/`ADMIN`), `active`, `createdAt`. See
  [docs/security.md](../../security.md) for the full authentication design this builds on.
- **No email delivery.** This project has no SMTP configured (see
  [error-handling-and-logging.md](../../conventions/error-handling-and-logging.md)'s "no
  external notification service wired up" stance, consistent elsewhere). Creating a user
  generates a randomly generated, one-time temporary password and returns it in the `POST`
  response **once** - never retrievable again. The creating `ADMIN` relays it out of band;
  there is no forced-change-at-next-login flag (no identity provider to enforce it), so the
  `ADMIN` is expected to tell the new user to change it ASAP - a self-service "change my
  password" endpoint is a natural next feature but out of scope here (see below).
- **A user holds exactly one of `STAFF`/`MANAGER`/`ADMIN`** - same additive-tier model as
  [06-roles-and-permissions](../06-roles-and-permissions/requirements.md). Changing a user's
  role replaces whichever of the three they currently hold; it is never additive.
- **Deactivating, not deleting.** "Removing" a teammate's access disables their account
  (`active: false` - `AuthenticationService` then rejects login even with the correct
  password) rather than deleting it, so their past audit history/attribution stays
  meaningful. Reactivating re-enables it.
- **Role visibility and changes are `ADMIN`-only, full stop** - including listing the user
  roster. This is the one resource in the whole API where even reading is not `STAFF`/
  `MANAGER`-accessible, since the roster itself (who has `ADMIN`) is sensitive.
- **The very first `ADMIN` is bootstrapped at startup, not through this API** - see
  `AdminBootstrapRunner` and [docs/security.md](../../security.md). This feature's own
  endpoints require an already-authenticated `ADMIN`, so something has to exist before any
  of them can ever be called.

## Entities

- **User**: `id`, `email`, `role` (`STAFF`/`MANAGER`/`ADMIN`), `active`, `createdAt`.
  `passwordHash` is never serialized into any API response or audit snapshot.

## Endpoints

- `POST /api/v1/users` - body `{ email, role }`. Creates the user with a temporary
  password. Response includes `temporaryPassword` - this is the only time it is ever
  returned.
- `GET /api/v1/users` (paginated, no filters), `GET /api/v1/users/{userId}`.
- `PATCH /api/v1/users/{userId}/role` - body `{ role }`. Replaces the user's role.
- `POST /api/v1/users/{userId}/deactivation` - disables the account.
- `POST /api/v1/users/{userId}/reactivation` - re-enables it.

## Acceptance criteria

1. Every endpoint requires `ADMIN` - `403` otherwise, including for `GET`.
2. `POST /api/v1/users` with an `email` already used by another user fails with `409`,
   `USER_EMAIL_ALREADY_EXISTS`.
3. A successful `POST` returns `201`, the created user (`active: true`), and a
   `temporaryPassword` that satisfies no special complexity policy beyond being
   cryptographically random and at least 16 characters.
4. `PATCH .../role` on an unknown `userId` fails with `404`, `USER_NOT_FOUND`.
5. `PATCH .../role` replaces exactly whichever of `STAFF`/`MANAGER`/`ADMIN` the user
   previously held with the new one - a subsequent `GET` shows only the new role.
6. A deactivated user's correct password is rejected at `POST /api/v1/auth/login` with
   `401`, `UNAUTHENTICATED` - identical to a wrong password, never a distinguishable error.
7. Every write produces the audit entries per
   [07-audit-trail](../07-audit-trail/requirements.md) (`entityType = USER`, `entityId` =
   the user's id). The password hash and temporary password are **never** included in an
   audit snapshot.

## Out of scope for this feature

- Email delivery of the temporary password - see "Decision".
- Self-service password reset/forgot-password, or a "change my own password" endpoint -
  out of scope until a real need appears; this project has no email channel to deliver a
  reset link through yet.
- Inviting a user with more than one role, or any role model beyond the existing three tiers.
- Any self-registration/signup flow - every user is created by an existing `ADMIN` (or is
  the one bootstrap `ADMIN` - see "Decision").
- Deleting a user outright - deactivation is the only "remove access" primitive.
