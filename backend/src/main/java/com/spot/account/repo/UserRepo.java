package com.spot.account.repo;

import com.spot.account.entity.UserEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepo extends JpaRepository<UserEntity, UUID> {
    Optional<UserEntity> findByEmail(String email);
    Optional<UserEntity> findByPhone(String phone);

    @Query("select u from UserEntity u where (u.email = :id) or (u.phone = :id)")
    Optional<UserEntity> findByIdentifier(@Param("id") String identifier);
}
