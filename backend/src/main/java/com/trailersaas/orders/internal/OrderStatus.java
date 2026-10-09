package com.trailersaas.orders.internal;

/** Accepted lifecycle vocabulary; #31 only creates OPEN and exposes no transitions. */
enum OrderStatus { OPEN, PLANNED, IN_PROGRESS, PARTIALLY_DELIVERED, DELIVERED, COMPLETED, CANCELLING, CANCELLED }
