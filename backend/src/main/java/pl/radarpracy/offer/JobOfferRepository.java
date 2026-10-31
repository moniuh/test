package pl.radarpracy.offer;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface JobOfferRepository extends JpaRepository<JobOffer, Long> {

    List<JobOffer> findAllByOrderByCreatedAtDesc();

    List<JobOffer> findByStatusOrderByCreatedAtDesc(OfferStatus status);

    List<JobOffer> findByCompanyIgnoreCase(String company);
}
