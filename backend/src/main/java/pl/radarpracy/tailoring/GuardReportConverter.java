package pl.radarpracy.tailoring;

import jakarta.persistence.Converter;
import pl.radarpracy.common.JsonConverter;
import tools.jackson.core.type.TypeReference;

/** Raport strażnika zapisany jako JSON w kolumnie tekstowej. */
@Converter
public class GuardReportConverter extends JsonConverter<GuardReport> {

    public GuardReportConverter() {
        super(new TypeReference<>() {
        });
    }

    @Override
    protected GuardReport empty() {
        return GuardReport.empty();
    }
}
