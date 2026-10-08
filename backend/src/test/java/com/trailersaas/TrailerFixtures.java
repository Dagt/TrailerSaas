package com.trailersaas;
import java.sql.SQLException;
import java.util.UUID;
import org.flywaydb.core.Flyway;
public final class TrailerFixtures {
    public final EquipmentFixtures db;
    public TrailerFixtures(Flyway flyway) { db=new EquipmentFixtures(flyway); }
    public UUID trailer(UUID tenant,String number,UUID customer) throws SQLException {
        UUID id=UUID.randomUUID();
        db.execute("INSERT INTO trailers(id,tenant_id,operational_number,designated_customer_id,location_state,load_state,serviceability_state,created_at,updated_at) VALUES(?,?,?,?,'LOCATION_UNKNOWN','UNKNOWN','IN_SERVICE',now(),now())",id,tenant,number,customer);
        return id;
    }
    public UUID customer(UUID tenant) throws SQLException {
        UUID id=UUID.randomUUID(); db.execute("INSERT INTO customers VALUES(?, ?, ?, 'ACTIVE',now(),now(),0)",id,tenant,"Customer-"+id); return id;
    }
    public UUID location(UUID tenant) throws SQLException {
        UUID site=UUID.randomUUID(),id=UUID.randomUUID();
        db.execute("INSERT INTO physical_sites(id,tenant_id,name,timezone,active,created_at,updated_at) VALUES(?,?,'Fixture','UTC',true,now(),now())",site,tenant);
        db.execute("INSERT INTO operational_locations(id,tenant_id,site_id,type,name,active,created_at,updated_at) VALUES(?,?,?,'YARD','Fixture',true,now(),now())",id,tenant,site);
        return id;
    }
    public UUID qr(UUID tenant,UUID trailer,String token) throws SQLException {
        UUID id=UUID.randomUUID();
        db.execute("INSERT INTO trailer_qr_identities VALUES(?,?,?,?,true,now(),NULL)",id,tenant,trailer,token); return id;
    }
    public void role(UUID tenant,UUID membership,String role,UUID customer) throws SQLException {
        db.execute("INSERT INTO role_assignments VALUES(?,?,?,?,?,?,now())",UUID.randomUUID(),tenant,membership,role,customer==null?"TENANT":"CUSTOMER",customer);
    }
}
