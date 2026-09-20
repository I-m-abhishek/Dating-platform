package com.dating.platform.common.entity;

import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.proxy.HibernateProxy;

import java.util.Objects;
import java.util.UUID;

/**
 * UUID primary keys: safe to expose publicly, no enumeration of user ids,
 * and shardable later without a migration.
 */
@Getter
@Setter
@MappedSuperclass
public abstract class BaseUuidEntity extends BaseEntity {

    @Id
    @GeneratedValue
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Override
    public final boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null) {
            return false;
        }
        Class<?> thisClass = this instanceof HibernateProxy hp
                ? hp.getHibernateLazyInitializer().getPersistentClass() : getClass();
        Class<?> otherClass = o instanceof HibernateProxy hp
                ? hp.getHibernateLazyInitializer().getPersistentClass() : o.getClass();
        if (!thisClass.equals(otherClass)) {
            return false;
        }
        BaseUuidEntity other = (BaseUuidEntity) o;
        return id != null && Objects.equals(id, other.getId());
    }

    @Override
    public final int hashCode() {
        return getClass().hashCode();
    }
}
