package pl.radarpracy.aggregator;

import jakarta.persistence.Converter;
import pl.radarpracy.common.JsonConverter;
import tools.jackson.core.type.TypeReference;

import java.util.ArrayList;
import java.util.List;

/** Lista sygnałów prompt injection jako JSON w kolumnie TEXT. */
@Converter
public class InjectionSignalListConverter extends JsonConverter<List<InjectionSignal>> {

    public InjectionSignalListConverter() {
        super(new TypeReference<>() {
        });
    }

    @Override
    protected List<InjectionSignal> empty() {
        return new ArrayList<>();
    }
}
