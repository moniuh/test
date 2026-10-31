package pl.radarpracy.aggregator;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AggregatorRunRepository extends JpaRepository<AggregatorRun, Long> {

    List<AggregatorRun> findAllByOrderByStartedAtDescIdDesc(Pageable page);

    List<AggregatorRun> findBySourceIdOrderByStartedAtDescIdDesc(Long sourceId, Pageable page);
}
