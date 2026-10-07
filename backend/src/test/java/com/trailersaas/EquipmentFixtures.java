package com.trailersaas;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.UUID;
import org.flywaydb.core.Flyway;

/** Migrator fixtures only; no production identity/master-data bootstrap surface. */
public final class EquipmentFixtures {
    private final Flyway flyway;
    public EquipmentFixtures(Flyway flyway) { this.flyway=flyway; }
    public void execute(String sql,Object... values) throws SQLException {
        try(var c=flyway.getConfiguration().getDataSource().getConnection()) { execute(c,sql,values); }
    }
    public int execute(Connection c,String sql,Object... values) throws SQLException {
        try(var q=c.prepareStatement(sql)) { bind(q,values); return q.executeUpdate(); }
    }
    public Object value(String sql,Object... values) throws SQLException {
        try(var c=flyway.getConfiguration().getDataSource().getConnection();var q=c.prepareStatement(sql)) {
            bind(q,values); try(var r=q.executeQuery()) { if(!r.next()) { throw new IllegalStateException("Fixture row missing"); } return r.getObject(1); }
        }
    }
    private void bind(java.sql.PreparedStatement q,Object[] values) throws SQLException {
        for(int i=0;i<values.length;i++) { q.setObject(i+1,values[i]); }
    }
    public UUID tenant() throws SQLException {
        UUID id=UUID.randomUUID(); execute("INSERT INTO tenants VALUES(?,?,'CANDIDATE',now(),now())",id,"Tenant-"+id); return id;
    }
    public UUID user() throws SQLException {
        UUID id=UUID.randomUUID(); execute("INSERT INTO users VALUES(?,?,NULL,NULL,now(),now())",id,"fixture-"+id); return id;
    }
    public UUID membership(UUID tenant,UUID user,String status,String role) throws SQLException {
        UUID id=UUID.randomUUID(); execute("INSERT INTO tenant_memberships VALUES(?,?,?,?,now(),now())",id,tenant,user,status);
        if(role!=null) {
            UUID customer=null; String scope="TENANT";
            if(role.equals("CUSTOMER_DISPATCHER")) {
                customer=UUID.randomUUID(); scope="CUSTOMER";
                execute("INSERT INTO customers VALUES(?,?,'Link fixture','ACTIVE',now(),now(),0)",customer,tenant);
            }
            execute("INSERT INTO role_assignments VALUES(?,?,?,?,?,?,now())",UUID.randomUUID(),tenant,id,role,scope,customer);
        }
        return id;
    }
    public UUID driver(UUID tenant,UUID user) throws SQLException {
        UUID id=UUID.randomUUID();
        execute("INSERT INTO drivers(id,tenant_id,user_id,name,created_at,updated_at) VALUES(?,?,?,'Fixture',now(),now())",id,tenant,user); return id;
    }
    public UUID tractor(UUID tenant,String number) throws SQLException {
        UUID id=UUID.randomUUID();
        execute("INSERT INTO tractors(id,tenant_id,operational_number,created_at,updated_at) VALUES(?,?,?,now(),now())",id,tenant,number); return id;
    }
}
