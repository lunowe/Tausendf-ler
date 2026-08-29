package de.uni_leipzig.eva.tausendfuessler.coordinator.persistence;

import de.uni_leipzig.eva.tausendfuessler.coordinator.crawl.JobStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface JobRepository extends JpaRepository<JobEntity, String> {

    List<JobEntity> findByOwnerOrderByCreatedAtDesc(long owner);

    List<JobEntity> findByStatusIn(Collection<JobStatus> statuses);

    long countByStatusIn(Collection<JobStatus> statuses);

    List<JobEntity> findByCreatedAtBefore(Instant cutoff);

    long deleteByCreatedAtBefore(Instant cutoff);
}
