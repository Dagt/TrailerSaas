/** Display text only. Never use these labels as API values or permission predicates. */
const labels = new Map<string, string>([
  ['OPEN', 'Open'], ['PLANNED', 'Planned'], ['ASSIGNED', 'Assigned'],
  ['IN_PROGRESS', 'In progress'], ['PARTIALLY_DELIVERED', 'Partially delivered'],
  ['DELIVERED', 'Delivered'], ['COMPLETED', 'Completed'],
  ['CANCELLING', 'Canceling'], ['CANCELLED', 'Canceled'],
  ['ACTIVE', 'Active'], ['INACTIVE', 'Inactive'], ['EXCEPTION', 'Exception'],
  ['AT_LOCATION', 'At location'], ['IN_TRANSIT', 'In transit'], ['AT_STOP', 'At Stop'],
  ['LOCATION_UNKNOWN', 'Location unknown'], ['UNKNOWN', 'Unknown'],
  ['EMPTY', 'Empty'], ['LOADED', 'Loaded'], ['IN_SERVICE', 'In service'],
  ['OUT_OF_SERVICE', 'Out of service'], ['COUPLED', 'Coupled'], ['UNCOUPLED', 'Uncoupled'],
  ['PENDING', 'Pending'], ['EN_ROUTE', 'En route'], ['ARRIVED', 'Arrived'],
  ['UPLOADED', 'Uploaded'], ['DELIVERY_PHOTO', 'Delivery photo'],
  ['SIGNED_BOL', 'Signed BOL'], ['NO_RECEIVER_AVAILABLE', 'No receiver available'],
  ['PICKUP', 'Pickup'], ['DELIVERY', 'Delivery'],
  ['RETURN_TO_BASE', 'Return to base'], ['REROUTE', 'Reroute'],
  ['HOLD_CONTACT_DISPATCH', 'Hold and contact dispatch'], ['OTHER_AUTHORIZED', 'Other authorized resolution'],
  ['true', 'Yes'], ['false', 'No'],
])

export function customerLabel(value: string): string {
  // An unfamiliar server value stays explicit instead of inventing its meaning.
  return labels.get(value) ?? value
}
