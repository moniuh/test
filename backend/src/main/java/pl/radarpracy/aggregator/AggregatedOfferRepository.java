package pl.radarpracy.aggregator;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface AggregatedOfferRepository extends JpaRepository<AggregatedOffer, Long> {

    Optional<AggregatedOffer> findByOfferId(Long offerId);

    List<AggregatedOffer> findBySourceIdIsNotNull();

    List<AggregatedOffer> findBySourceIdIsNotNullAndSeenAtIsNull();

    List<AggregatedOffer> findBySourceIdAndSeenAtIsNull(Long sourceId);

    boolean existsBySourceIdAndExternalId(Long sourceId, String externalId);

    boolean existsByCanonicalUrl(String canonicalUrl);

    boolean existsByContentHashAndFetchedAtAfter(String contentHash, Instant after);
}
