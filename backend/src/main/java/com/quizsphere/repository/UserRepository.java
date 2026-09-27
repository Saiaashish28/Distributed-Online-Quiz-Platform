package com.quizsphere.repository;

import com.quizsphere.entity.Role;
import com.quizsphere.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    @Query("select u from User u where lower(u.email) = lower(:email) and u.role = :role")
    Optional<User> findByEmailAndRole(String email, Role role);

    boolean existsByRole(Role role);
}
