package pl.radarpracy.analytics;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface MoodEntryRepository extends JpaRepository<MoodEntry, Long> {

    Optional<MoodEntry> findByDate(LocalDate date);

    List<MoodEntry> findByDateGreaterThanEqualOrderByDateDesc(LocalDate from);
}
