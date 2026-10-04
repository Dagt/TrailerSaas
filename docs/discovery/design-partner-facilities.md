# Design Partner Facilities and Dock Examples

**Status:** Confirmed discovery evidence  
**Purpose:** Preserve the real-world facility topology used for requirements validation. These values are design-partner data and must never be hard-coded into the generic SaaS.

## Hood Container

### 4800 Austin Blvd, Chicago, Illinois

- Yard at this location.
- Docks **4–32** associated with this location.

### 5800 51st St, Chicago, Illinois

- Docks **1–10**.

### 5750 51st St, Chicago, Illinois

- Docks **11–18**.

## Stanfast

### 710 Kimberly Dr, Carol Stream, Illinois

- Yard at this location.
- Docks **14–23**.

## Black Hawk

### 710 Kimberly Dr, Carol Stream, Illinois

- Yard at this location.
- Docks **1–13**.

### Important modeling observation

Stanfast and Black Hawk share the same street address but use different dock ranges and represent different customer operational contexts.

Therefore a `Location` cannot be modeled only as a unique street address. The domain must be able to represent:

- physical site/address;
- tenant/customer ownership or access;
- yard areas;
- dock ranges or sublocations.

A geofence may represent the physical site while business sublocations distinguish customer/dock context.

## HR Controls / HRCA

### 873 Cambridge Dr, Elk Grove Village, Illinois

- One dock.

## SaaS implication

Facility topology must be data-driven.

Do not hard-code:

- customer names;
- addresses;
- dock counts;
- dock ranges;
- yard names.

The system should support multiple customer/business contexts at the same physical address.
