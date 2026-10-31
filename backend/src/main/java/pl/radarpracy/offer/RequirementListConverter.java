package pl.radarpracy.offer;

import jakarta.persistence.Converter;
import pl.radarpracy.common.JsonConverter;
import tools.jackson.core.type.TypeReference;

import java.util.ArrayList;
import java.util.List;

@Converter
public class RequirementListConverter extends JsonConverter<List<Requirement>> {

    public RequirementListConverter() {
        super(new TypeReference<>() {
        });
    }

    @Override
    protected List<Requirement> empty() {
        return new ArrayList<>();
    }
}
