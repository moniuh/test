package pl.radarpracy.profile;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CvVariantRepository extends JpaRepository<CvVariant, Long> {

    List<CvVariant> findAllByOrderByIdAsc();
}
