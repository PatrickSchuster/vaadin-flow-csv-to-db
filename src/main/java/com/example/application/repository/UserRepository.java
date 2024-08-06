package com.example.application.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.example.application.model.User;

public interface UserRepository extends JpaRepository<User, Long>{
    
}
