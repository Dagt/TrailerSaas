package com.trailersaas.trips.internal;

import com.trailersaas.orders.OrderPlanningDirectory;
import jakarta.persistence.EntityManager;
import java.util.*;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.*;

@Component
@Transactional(propagation = Propagation.MANDATORY, readOnly = true)
class TripOrderPlanningDirectory implements OrderPlanningDirectory {
  private final EntityManager em;

  TripOrderPlanningDirectory(EntityManager em) {
    this.em = em;
  }

  public List<UUID> page(Query input) {
    var absent =
        """
        (o.status NOT IN ('COMPLETED','CANCELLED') AND NOT EXISTS (
            SELECT 1 FROM trip_stop_orders a JOIN trip_stops s ON s.tenant_id=a.tenant_id AND s.id=a.trip_stop_id
            JOIN trips t ON t.tenant_id=s.tenant_id AND t.id=s.trip_id
            WHERE a.tenant_id=o.tenant_id AND a.order_id=o.id
                AND t.status IN ('PLANNED','ASSIGNED','IN_TRANSIT','AT_STOP','CANCELLING')))
        """;
    var q =
        em.createNativeQuery(
                "select o.id from orders o where o.tenant_id=:tenant and "
                    + (input.unplanned() ? absent : "NOT " + absent)
                    + (input.scope().tenantWide() ? "" : " and o.customer_id in (:customers)")
                    + (input.customer() == null ? "" : " and o.customer_id=:customer")
                    + (input.status() == null ? "" : " and o.status=:status")
                    + (input.before() == null
                        ? ""
                        : " and (o.created_at<:at or (o.created_at=:at and o.id<:id))")
                    + " order by o.created_at desc,o.id desc",
                UUID.class)
            .setParameter("tenant", input.tenant());
    if (!input.scope().tenantWide()) {
      q.setParameter("customers", input.scope().customerIds());
    }
    if (input.customer() != null) {
      q.setParameter("customer", input.customer());
    }
    if (input.status() != null) {
      q.setParameter("status", input.status());
    }
    if (input.before() != null) {
      q.setParameter("at", input.before());
      q.setParameter("id", input.beforeId());
    }
    return q.setMaxResults(input.limit()).getResultList();
  }
}
