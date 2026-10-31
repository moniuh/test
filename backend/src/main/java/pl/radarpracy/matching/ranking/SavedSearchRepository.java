package pl.radarpracy.matching.ranking;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SavedSearchRepository extends JpaRepository<SavedSearch, Long> {

    List<SavedSearch> findAllByOrderByIdAsc();
}
