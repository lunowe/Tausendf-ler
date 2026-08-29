package de.uni_leipzig.eva.tausendfuessler.coordinator.persistence;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface PageRepository extends JpaRepository<PageEntity, Long> {

    List<PageEntity> findByJobIdAndSeqGreaterThanOrderBySeqAsc(String jobId, long afterSeq, Pageable pageable);

    long countByJobId(String jobId);

    long deleteByJobId(String jobId);

    /** Only the url column, used for the top-domain statistic. */
    @Query("select p.url from PageEntity p where p.error is null")
    List<String> findAllUrls();
}
