package pl.radarpracy.tailoring;

import jakarta.persistence.Converter;
import pl.radarpracy.common.JsonConverter;
import tools.jackson.core.type.TypeReference;

/** Opcje generatora zapisane jako JSON w kolumnie tekstowej. */
@Converter
public class TailoringOptionsConverter extends JsonConverter<TailoringOptions> {

    public TailoringOptionsConverter() {
        super(new TypeReference<>() {
        });
    }

    @Override
    protected TailoringOptions empty() {
        return TailoringOptions.defaults();
    }
}
