package de.uni_leipzig.eva.tausendfuessler.coordinator.persistence;

import jakarta.persistence.EntityManager;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.util.List;

/** Postgres full-text search with {@code to_tsvector}/{@code plainto_tsquery}, ranked by {@code ts_rank}. */
@Repository
@Profile("!test")
public class PostgresSearchRepository implements SearchRepository {

    private static final String SQL = """
            select * from pages
            where error is null
              and to_tsvector('simple', coalesce(title, '') || ' ' || coalesce(text_snippet, ''))
                  @@ plainto_tsquery('simple', :q)
            order by ts_rank(to_tsvector('simple', coalesce(title, '') || ' ' || coalesce(text_snippet, '')),
                             plainto_tsquery('simple', :q)) desc
            """;

    private final EntityManager entityManager;

    public PostgresSearchRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<PageEntity> search(String query, int limit) {
        return entityManager.createNativeQuery(SQL, PageEntity.class)
                .setParameter("q", query)
                .setMaxResults(limit)
                .getResultList();
    }
}
