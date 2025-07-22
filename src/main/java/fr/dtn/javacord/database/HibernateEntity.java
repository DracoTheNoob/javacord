package fr.dtn.javacord.database;

import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;

import java.util.UUID;

@MappedSuperclass
public abstract class HibernateEntity {
    @Id
    @Column(name = "id", nullable = false, updatable = false, unique = true)
    private String id;

    public HibernateEntity() {
        this.id = UUID.randomUUID().toString();
    }

    public UUID getId() {
        return UUID.fromString(id);
    }

    protected void setId(String id) {
        this.id = id;
    }

    protected void setId(UUID uuid) {
        this.id = uuid.toString();
    }
}
