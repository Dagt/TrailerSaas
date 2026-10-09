package com.trailersaas;

import java.sql.SQLException;
import java.util.UUID;
import org.flywaydb.core.Flyway;

public final class OrderFixtures {
    public final EquipmentFixtures db;
    public OrderFixtures(Flyway flyway) { db = new EquipmentFixtures(flyway); }
    public UUID customer(UUID tenant) throws SQLException {
        var id = UUID.randomUUID(); db.execute("INSERT INTO customers VALUES(?,?,?,'ACTIVE',now(),now(),0)", id, tenant, "Customer-" + id); return id;
    }
    public UUID site(UUID tenant, UUID customer) throws SQLException {
        var id = UUID.randomUUID();
        db.execute("INSERT INTO physical_sites(id,tenant_id,name,timezone,active,created_at,updated_at) VALUES(?,?,'Receiving','America/Chicago',true,now(),now())", id, tenant);
        if (customer != null) { db.execute("INSERT INTO customer_sites VALUES(?,?,?,?,now())", UUID.randomUUID(), tenant, customer, id); }
        return id;
    }
    public UUID location(UUID tenant, UUID site, UUID customer) throws SQLException {
        var id = UUID.randomUUID();
        db.execute("INSERT INTO operational_locations(id,tenant_id,site_id,customer_id,type,name,active,created_at,updated_at) VALUES(?,?,?,?,'DOCK','Receiving',true,now(),now())", id, tenant, site, customer); return id;
    }
    public UUID order(UUID tenant, UUID customer, UUID user) throws SQLException {
        var id = UUID.randomUUID();
        db.execute("INSERT INTO orders(id,tenant_id,customer_id,order_number,status,created_by_user_id,created_at,updated_at) VALUES(?,?,?,'Repeated number','OPEN',?,now(),now())", id, tenant, customer, user); return id;
    }
    public UUID stop(UUID tenant, UUID order, int sequence, UUID site, UUID location, String snapshot) throws SQLException {
        var id = UUID.randomUUID();
        db.execute("INSERT INTO order_stops(id,tenant_id,order_id,sequence_number,site_id,location_id,stop_type,site_snapshot,created_at,updated_at) VALUES(?,?,?,?,?,?,'DELIVERY',?::jsonb,now(),now())", id, tenant, order, sequence, site, location, snapshot); return id;
    }
}
