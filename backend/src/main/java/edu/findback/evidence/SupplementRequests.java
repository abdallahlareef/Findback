package edu.findback.evidence;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
public interface SupplementRequests extends JpaRepository<SupplementRequest,Long> {
    Optional<SupplementRequest> findByPairIdAndRequestKey(Long pair, String key);
}
