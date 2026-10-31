package pl.radarpracy.offer;

import jakarta.persistence.Converter;
import pl.radarpracy.common.JsonConverter;
import tools.jackson.core.type.TypeReference;

import java.util.ArrayList;
import java.util.List;

@Converter
public class WorkModeListConverter extends JsonConverter<List<WorkMode>> {

    public WorkModeListConverter() {
        super(new TypeReference<>() {
        });
    }

    @Override
    protected List<WorkMode> empty() {
        return new ArrayList<>();
    }
}
