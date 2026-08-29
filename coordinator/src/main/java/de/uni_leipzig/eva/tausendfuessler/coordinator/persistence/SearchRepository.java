package de.uni_leipzig.eva.tausendfuessler.coordinator.persistence;

import java.util.List;

/** Full-text search over crawled pages. Two implementations: Postgres tsvector (prod) and LIKE (H2 tests). */
public interface SearchRepository {

    List<PageEntity> search(String query, int limit);
}
