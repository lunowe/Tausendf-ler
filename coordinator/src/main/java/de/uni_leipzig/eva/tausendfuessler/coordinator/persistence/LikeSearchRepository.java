package de.uni_leipzig.eva.tausendfuessler.coordinator.persistence;

import jakarta.persistence.EntityManager;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Locale;

/** Portable fallback for the H2 test profile: case-insensitive substring match on title and snippet. */
@Repository
@Profile("test")
public class LikeSearchRepository implements SearchRepository {

    private static final String JPQL = """
            select p from PageEntity p
            where p.error is null
              and (lower(p.title) like :q or lower(p.textSnippet) like :q)
            order by p.id asc
            """;

    private final EntityManager entityManager;

    public LikeSearchRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public List<PageEntity> search(String query, int limit) {
        return entityManager.createQuery(JPQL, PageEntity.class)
                .setParameter("q", "%" + query.toLowerCase(Locale.ROOT) + "%")
                .setMaxResults(limit)
                .getResultList();
    }
}
