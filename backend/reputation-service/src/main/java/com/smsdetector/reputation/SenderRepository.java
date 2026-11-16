package com.smsdetector.reputation;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface SenderRepository extends JpaRepository<SenderReputationEntity, String> {
    List<SenderReputationEntity> findByLabel(String label);
}
