package pl.radarpracy.application;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface JobApplicationRepository extends JpaRepository<JobApplication, Long> {

    List<JobApplication> findAllByOrderByStatusAscBoardPositionAscIdAsc();

    List<JobApplication> findByOfferId(Long offerId);

    List<JobApplication> findByStatus(ApplicationStatus status);
}
