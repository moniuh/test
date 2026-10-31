package pl.radarpracy.common;

import jakarta.persistence.Converter;
import tools.jackson.core.type.TypeReference;

import java.util.ArrayList;
import java.util.List;

@Converter
public class StringListConverter extends JsonConverter<List<String>> {

    public StringListConverter() {
        super(new TypeReference<>() {
        });
    }

    @Override
    protected List<String> empty() {
        return new ArrayList<>();
    }
}
