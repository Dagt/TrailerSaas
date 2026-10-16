import { z } from 'zod'
import { ApiProblem } from '../../../shared/api/client'

// Closed catalogs and per-action whitelists from the accepted #61 OpenAPI.
export const uuid = z.string().regex(/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/)
export const instant = z.iso.datetime({ offset: true }).regex(/^\d{4}-/)
export const cursor = z.string().min(1).max(512).regex(/^[A-Za-z0-9_-]+$/)
export const auditActionCode = z.enum(["TENANT_SETTINGS_UPDATED", "CUSTOMER_CREATED", "CUSTOMER_UPDATED", "CUSTOMER_SITE_ASSOCIATED", "CUSTOMER_SITE_DISASSOCIATED", "SITE_CREATED", "SITE_UPDATED", "LOCATION_CREATED", "LOCATION_UPDATED", "DRIVER_CREATED", "DRIVER_UPDATED", "DRIVER_USER_LINKED", "DRIVER_USER_UNLINKED", "TRACTOR_CREATED", "TRACTOR_UPDATED", "TRAILER_CREATED", "TRAILER_UPDATED", "TRAILER_QR_ISSUED", "TRAILER_QR_REVOKED", "INVITATION_CREATED", "INVITATION_EXPIRED", "INVITATION_REVOKED", "INVITATION_ACCEPTED", "MEMBERSHIP_CREATED"])
export const auditEntityType = z.enum(["TENANT_SETTINGS", "CUSTOMER", "PHYSICAL_SITE", "OPERATIONAL_LOCATION", "DRIVER", "TRACTOR", "TRAILER", "TRAILER_QR_IDENTITY", "INVITATION", "TENANT_MEMBERSHIP"])
export const auditActor = z.object({ type: z.enum(["HUMAN_USER", "SYSTEM", "INTEGRATION"]), userId: uuid.nullable(), membershipId: uuid.nullable() }).strict()
export const auditRole = z.object({ roleCode: z.enum(["TENANT_ADMIN", "TRANSPORT_OPERATIONS", "CUSTOMER_DISPATCHER", "YARD_SPOTTER", "DRIVER"]), scopeType: z.enum(["TENANT", "CUSTOMER"]), customerId: uuid.nullable() }).strict()
export const auditBusinessAddress = z.object({ line1: z.string().nullable(), line2: z.string().nullable(), city: z.string().nullable(), stateRegion: z.string().nullable(), postalCode: z.string().nullable(), countryCode: z.string().nullable() }).strict()
export const auditSettingsSnapshot = z.object({ staleTrailerThresholdHours: z.number().int().min(1).optional(), defaultTimezone: z.string().nullable().optional(), version: z.number().int().min(0).optional() }).strict()
export const auditCustomerSnapshot = z.object({ name: z.string().optional(), status: z.enum(["ACTIVE", "INACTIVE"]).optional(), version: z.number().int().min(0).optional() }).strict()
export const auditSiteSnapshot = z.object({ name: z.string().optional(), address: auditBusinessAddress.optional(), timezone: z.string().optional(), latitude: z.number().min(-90).max(90).nullable().optional(), longitude: z.number().min(-180).max(180).nullable().optional(), geofenceRadiusM: z.number().int().min(1).nullable().optional(), active: z.boolean().optional(), version: z.number().int().min(0).optional() }).strict()
export const auditLocationSnapshot = z.object({ siteId: uuid.optional(), customerId: uuid.nullable().optional(), name: z.string().optional(), type: z.enum(["BASE", "YARD", "DOCK", "CUSTOMER_AREA", "SHOP", "OTHER"]).optional(), code: z.string().nullable().optional(), active: z.boolean().optional(), version: z.number().int().min(0).optional() }).strict()
export const auditDriverSnapshot = z.object({ name: z.string().optional(), userId: uuid.nullable().optional(), active: z.boolean().optional(), licenseNumberPresent: z.boolean().optional(), medicalCertificatePresent: z.boolean().optional(), redactedChanges: z.array(z.enum(["licenseNumber", "medicalCertificateExpires"])).refine(v => new Set(v).size === v.length).optional(), version: z.number().int().min(0).optional() }).strict()
export const auditTractorSnapshot = z.object({ operationalNumber: z.string().optional(), active: z.boolean().optional(), version: z.number().int().min(0).optional() }).strict()
export const auditTrailerSnapshot = z.object({ operationalNumber: z.string().optional(), designatedCustomerId: uuid.nullable().optional(), trailerType: z.string().nullable().optional(), usagePurpose: z.string().nullable().optional(), active: z.boolean().optional(), capabilityNotesPresent: z.boolean().optional(), redactedChanges: z.array(z.enum(["capabilityNotes"])).refine(v => new Set(v).size === v.length).optional(), version: z.number().int().min(0).optional() }).strict()
export const auditQrSnapshot = z.object({ trailerId: uuid.optional(), active: z.boolean().optional(), issuedAt: instant.optional(), revokedAt: instant.nullable().optional() }).strict()
export const auditInvitationSnapshot = z.object({ status: z.enum(["PENDING", "ACCEPTED", "EXPIRED", "REVOKED"]).optional(), expiresAt: instant.optional(), roles: z.array(auditRole).optional(), membershipId: uuid.nullable().optional() }).strict()
export const auditMembershipSnapshot = z.object({ userId: uuid.optional(), status: z.enum(["ACTIVE", "SUSPENDED", "DISABLED"]).optional(), roles: z.array(auditRole).optional() }).strict()
export const auditAssociationSnapshot = z.object({ siteId: uuid.optional(), associationId: uuid.nullable().optional(), associated: z.boolean().optional() }).strict()

const projections = {
  TENANT_SETTINGS: auditSettingsSnapshot, CUSTOMER: auditCustomerSnapshot, PHYSICAL_SITE: auditSiteSnapshot,
  OPERATIONAL_LOCATION: auditLocationSnapshot, DRIVER: auditDriverSnapshot, TRACTOR: auditTractorSnapshot,
  TRAILER: auditTrailerSnapshot, TRAILER_QR_IDENTITY: auditQrSnapshot, INVITATION: auditInvitationSnapshot,
  TENANT_MEMBERSHIP: auditMembershipSnapshot,
} as const
export const actionEntity: Record<z.infer<typeof auditActionCode>, keyof typeof projections> = {
  TENANT_SETTINGS_UPDATED: 'TENANT_SETTINGS', CUSTOMER_CREATED: 'CUSTOMER', CUSTOMER_UPDATED: 'CUSTOMER',
  CUSTOMER_SITE_ASSOCIATED: 'CUSTOMER', CUSTOMER_SITE_DISASSOCIATED: 'CUSTOMER', SITE_CREATED: 'PHYSICAL_SITE', SITE_UPDATED: 'PHYSICAL_SITE',
  LOCATION_CREATED: 'OPERATIONAL_LOCATION', LOCATION_UPDATED: 'OPERATIONAL_LOCATION', DRIVER_CREATED: 'DRIVER', DRIVER_UPDATED: 'DRIVER',
  DRIVER_USER_LINKED: 'DRIVER', DRIVER_USER_UNLINKED: 'DRIVER', TRACTOR_CREATED: 'TRACTOR', TRACTOR_UPDATED: 'TRACTOR',
  TRAILER_CREATED: 'TRAILER', TRAILER_UPDATED: 'TRAILER', TRAILER_QR_ISSUED: 'TRAILER_QR_IDENTITY', TRAILER_QR_REVOKED: 'TRAILER_QR_IDENTITY',
  INVITATION_CREATED: 'INVITATION', INVITATION_EXPIRED: 'INVITATION', INVITATION_REVOKED: 'INVITATION', INVITATION_ACCEPTED: 'INVITATION', MEMBERSHIP_CREATED: 'TENANT_MEMBERSHIP',
}
export const auditEventSchema = z.object({
  id: uuid, actor: auditActor, sourceType: z.enum(['USER_ACTION', 'MOBILE_AUTOMATION', 'SYSTEM_PROCESS', 'EXTERNAL_INTEGRATION']),
  actionCode: auditActionCode, entityType: auditEntityType, entityId: uuid,
  beforeData: z.record(z.string(), z.unknown()).nullable(), afterData: z.record(z.string(), z.unknown()), reason: z.string().max(4000).nullable(), occurredAt: instant,
}).strict().superRefine((e, ctx) => {
  const validActor = e.actor.type === 'HUMAN_USER' ? e.actor.userId !== null && e.actor.membershipId !== null && ['USER_ACTION', 'MOBILE_AUTOMATION'].includes(e.sourceType)
    : e.actor.userId === null && e.actor.membershipId === null && e.sourceType === (e.actor.type === 'SYSTEM' ? 'SYSTEM_PROCESS' : 'EXTERNAL_INTEGRATION')
  const projection = e.actionCode === 'CUSTOMER_SITE_ASSOCIATED' || e.actionCode === 'CUSTOMER_SITE_DISASSOCIATED' ? auditAssociationSnapshot : projections[e.entityType]
  const validSnapshot = (value: Record<string, unknown>) => Object.keys(value).length > 0 && projection.safeParse(value).success && new TextEncoder().encode(JSON.stringify(value)).length <= 65536
  if (!validActor || actionEntity[e.actionCode] !== e.entityType || !validSnapshot(e.afterData) || e.beforeData !== null && !validSnapshot(e.beforeData))
    ctx.addIssue({ code: 'custom', message: 'Invalid Audit projection' })
})
export type AuditEvent = z.infer<typeof auditEventSchema>
export const auditPageSchema = z.object({ items: z.array(auditEventSchema).max(100), nextCursor: cursor.nullable() }).strict()
export function validated<T>(schema: z.ZodType<T>, value: unknown): T {
  const result = schema.safeParse(value)
  // Do not propagate a Zod error containing snapshot values to diagnostics.
  if (!result.success) throw new ApiProblem({ status: 502, code: 'UNEXPECTED_RESPONSE' })
  return result.data
}
export const filterSchema = z.object({ actorUserId: uuid.optional(), actionCode: auditActionCode.optional(), entityType: auditEntityType.optional(), entityId: uuid.optional(), from: instant.optional(), to: instant.optional() }).strict()
  .refine(v => !v.from || !v.to || Date.parse(v.from) <= Date.parse(v.to), 'From must be earlier than or equal to To.')
export type AuditFilters = z.infer<typeof filterSchema>
export function parseFilters(search: URLSearchParams): AuditFilters {
  const entries = [...search.entries()]
  if (new Set(entries.map(([key]) => key)).size !== entries.length) throw new Error('Duplicate filters')
  return filterSchema.parse(Object.fromEntries(entries))
}
