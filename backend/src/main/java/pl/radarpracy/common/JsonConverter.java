package pl.radarpracy.common;

import jakarta.persistence.AttributeConverter;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * Bazowy konwerter JPA: wartość ⇄ JSON w kolumnie tekstowej (działa tak samo w H2 i PostgreSQL).
 *
 * <p>Uwaga: Hibernate wykrywa zmianę takiego pola po referencji — przy modyfikacji listy przypisz
 * nową listę przez setter zamiast mutować istniejącą.</p>
 */
public abstract class JsonConverter<T> implements AttributeConverter<T, String> {

    protected static final JsonMapper MAPPER = JsonMapper.builder().findAndAddModules().build();

    private final TypeReference<T> type;

    protected JsonConverter(TypeReference<T> type) {
        this.type = type;
    }

    protected abstract T empty();

    @Override
    public String convertToDatabaseColumn(T attribute) {
        return MAPPER.writeValueAsString(attribute == null ? empty() : attribute);
    }

    @Override
    public T convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return empty();
        }
        return MAPPER.readValue(dbData, type);
    }
}
