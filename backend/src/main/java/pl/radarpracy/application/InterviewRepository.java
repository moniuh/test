package pl.radarpracy.application;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InterviewRepository extends JpaRepository<Interview, Long> {

    List<Interview> findAllByOrderByScheduledAtAsc();

    List<Interview> findByApplicationIdOrderByScheduledAtAsc(Long applicationId);

    void deleteByApplicationId(Long applicationId);
}
