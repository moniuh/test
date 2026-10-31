package pl.radarpracy.tailoring;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TailoredCvRepository extends JpaRepository<TailoredCv, Long> {

    List<TailoredCv> findAllByOrderByCreatedAtDescIdDesc();

    List<TailoredCv> findByOfferIdOrderByCreatedAtDescIdDesc(Long offerId);

    Optional<TailoredCv> findByCvVariantId(Long cvVariantId);
}
