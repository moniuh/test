package pl.radarpracy.profile;

import jakarta.persistence.Converter;
import pl.radarpracy.common.JsonConverter;
import tools.jackson.core.type.TypeReference;

import java.util.ArrayList;
import java.util.List;

@Converter
public class SkillEntryListConverter extends JsonConverter<List<SkillEntry>> {

    public SkillEntryListConverter() {
        super(new TypeReference<>() {
        });
    }

    @Override
    protected List<SkillEntry> empty() {
        return new ArrayList<>();
    }
}
