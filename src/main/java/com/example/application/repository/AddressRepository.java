package com.example.application.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.example.application.model.Address;

public interface AddressRepository extends JpaRepository<Address, Long> {
    
}
