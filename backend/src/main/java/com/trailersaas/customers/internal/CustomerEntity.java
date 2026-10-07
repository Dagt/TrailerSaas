package com.trailersaas.customers.internal;

import jakarta.persistence.*;

@Entity
@Table(name = "customers")
class CustomerEntity extends VersionedMasterData {
    @Column(nullable = false) String status = "ACTIVE";
    protected CustomerEntity() {}
}
