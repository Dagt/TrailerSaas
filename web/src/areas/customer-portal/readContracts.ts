import { z } from 'zod'
const id=z.uuid(), text=z.string().nullable(), time=z.iso.datetime({offset:true}), stamp=time.nullable()
export const locationStates=['AT_LOCATION','IN_TRANSIT','LOCATION_UNKNOWN'] as const
export const loadStates=['EMPTY','LOADED','UNKNOWN'] as const
export const serviceStates=['IN_SERVICE','OUT_OF_SERVICE'] as const
export const stopStates=['PENDING','EN_ROUTE','ARRIVED','IN_SERVICE','COMPLETED','CANCELLED'] as const
export const documentTypes=['BOL','POD','DELIVERY_PHOTO'] as const
export const customerTrailer=z.object({id,operationalNumber:z.string(),designatedCustomerId:id,trailerType:text,capabilityNotes:z.null(),
  locationState:z.enum(locationStates),currentLocationId:id.nullable(),lastKnownLocationId:id.nullable(),lastSeenAt:stamp,lastDriverId:z.null(),
  loadState:z.enum(loadStates),serviceabilityState:z.enum(serviceStates),usagePurpose:text,active:z.boolean(),version:z.number().int().nonnegative(),createdAt:time,updatedAt:time,
  designatedCustomerName:text,currentLocationName:text,lastKnownLocationName:text,couplingState:z.enum(['COUPLED','UNCOUPLED']),
  currentDriverId:z.null(),currentDriverName:z.null(),currentTractorId:z.null(),currentTractorOperationalNumber:z.null(),lastDriverName:z.null(),lastTractorId:z.null(),lastTractorOperationalNumber:z.null(),
}).strict()
const location=z.object({id,name:z.string(),type:z.string(),siteId:id,siteName:text}).strict()
export const customerTrailerDetail=customerTrailer.extend({designatedCustomer:z.object({id,name:z.string()}).strict(),currentLocation:location.nullable(),lastKnownLocation:location.nullable(),
  lastDriver:z.null(),currentTractor:z.null(),lastTractor:z.null(),currentCoupling:z.null(),lastCoupling:z.null(),
  openManifests:z.array(z.object({id,status:z.literal('OPEN'),version:z.number().int().nonnegative(),updatedAt:time,ordersTruncated:z.boolean(),orders:z.array(z.object({id,customerId:id,customerName:z.string(),orderNumber:z.string(),status:z.string()}).strict())}).strict()),manifestSummaryTruncated:z.literal(false),
}).strict()
export const trailerPage=z.object({items:z.array(customerTrailer),nextCursor:z.string().min(1).nullable()}).strict()
export const timelineItem=z.object({id,eventType:z.string(),actorType:z.string(),sourceType:z.string(),occurredAt:time,recordedAt:time,reason:z.null(),locationId:id.nullable(),locationName:text,
  driverId:z.null(),driverName:z.null(),tractorId:z.null(),tractorOperationalNumber:z.null(),tripId:z.null(),tripStopId:z.null(),
  changes:z.object({previousLocationState:z.enum(locationStates).optional(),previousLoadState:z.enum(loadStates).optional(),newLoadState:z.enum(loadStates).optional(),previousServiceabilityState:z.enum(serviceStates).optional(),newServiceabilityState:z.enum(serviceStates).optional(),previousLocationId:id.nullable().optional(),newLocationId:id.nullable().optional(),previousLocationName:text.optional(),newLocationName:text.optional()}).strict(),
}).strict()
export const timelinePage=z.object({items:z.array(timelineItem),nextCursor:z.string().min(1).nullable()}).strict()
export const evidenceDocument=z.object({customerId:id,id,documentType:z.enum(documentTypes),uploadStatus:z.literal('UPLOADED'),contentType:z.enum(['image/jpeg','image/png','application/pdf']),sizeBytes:z.number().int().positive(),uploadedAt:time,
  orderId:id,orderNumber:z.string(),stopId:id,acceptedAt:time,evidenceMode:z.enum(['SIGNED_BOL','NO_RECEIVER_AVAILABLE']),
}).strict()
export const documentPage=z.object({customerId:id,items:z.array(evidenceDocument),nextCursor:z.string().min(1).nullable()}).strict()
export const delivery=z.object({customerId:id,orderId:id,orderNumber:z.string(),orderStatus:z.enum(['OPEN','PLANNED','IN_PROGRESS','PARTIALLY_DELIVERED','DELIVERED','COMPLETED','CANCELLING','CANCELLED']),stopId:id,tripId:id,destination:text,scheduledAt:stamp,status:z.enum(stopStates),
  arrivedAt:stamp,serviceStartedAt:stamp,completedAt:stamp,departedAt:stamp,expectedTrailer:z.object({id,operationalNumber:z.string()}).strict().nullable(),evidenceMode:z.enum(['SIGNED_BOL','NO_RECEIVER_AVAILABLE']).nullable(),acceptedAt:stamp,fileAssociationRecorded:z.boolean(),documents:z.array(evidenceDocument).max(2),
}).strict()
export const deliveryPage=z.object({customerId:id,items:z.array(delivery),nextCursor:z.string().min(1).nullable()}).strict()
export const documentMetadata=z.object({id,documentType:z.enum(documentTypes),contentType:z.enum(['image/jpeg','image/png','application/pdf']),sizeBytes:z.number().int().positive(),checksumSha256:z.string().regex(/^[a-f0-9]{64}$/),uploadStatus:z.literal('UPLOADED'),createdAt:time,uploadedAt:time,
  links:z.array(z.object({id,entityType:z.literal('TRIP_STOP'),entityId:id,createdAt:time}).strict()).min(1).max(50),
}).strict()
export const downloadGrant=z.object({downloadUrl:z.url(),expiresAt:time}).strict()
export type CustomerTrailer=z.infer<typeof customerTrailer>
export type CustomerDelivery=z.infer<typeof delivery>
export type EvidenceDocument=z.infer<typeof evidenceDocument>
