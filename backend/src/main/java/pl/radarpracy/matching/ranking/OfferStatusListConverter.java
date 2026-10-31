package pl.radarpracy.matching.ranking;

import jakarta.persistence.Converter;
import pl.radarpracy.common.JsonConverter;
import pl.radarpracy.offer.OfferStatus;
import tools.jackson.core.type.TypeReference;

import java.util.ArrayList;
import java.util.List;

@Converter
public class OfferStatusListConverter extends JsonConverter<List<OfferStatus>> {

    public OfferStatusListConverter() {
        super(new TypeReference<>() {
        });
    }

    @Override
    protected List<OfferStatus> empty() {
        return new ArrayList<>();
    }
}
