package com.alumindex.repository;

import com.alumindex.entity.CustomerRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CustomerRequestRepository extends JpaRepository<CustomerRequest, UUID> {
    List<CustomerRequest> findByStatusOrderBySubmittedAtAsc(CustomerRequest.Status status);
    long countByStatus(CustomerRequest.Status status);
}
