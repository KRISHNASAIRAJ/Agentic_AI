package com.smsdetector.detection;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface SignatureRepository extends JpaRepository<Signature, Long> {
    Optional<Signature> findByMessageHash(String messageHash);
}
