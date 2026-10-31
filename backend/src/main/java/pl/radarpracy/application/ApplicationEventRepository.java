package pl.radarpracy.application;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ApplicationEventRepository extends JpaRepository<ApplicationEvent, Long> {

    List<ApplicationEvent> findByApplicationIdOrderByOccurredAtAsc(Long applicationId);

    void deleteByApplicationId(Long applicationId);
}
