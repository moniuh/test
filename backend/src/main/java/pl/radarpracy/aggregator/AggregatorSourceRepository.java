package pl.radarpracy.aggregator;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AggregatorSourceRepository extends JpaRepository<AggregatorSource, Long> {

    List<AggregatorSource> findAllByOrderByIdAsc();

    List<AggregatorSource> findByEnabledTrueOrderByIdAsc();
}
