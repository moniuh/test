package pl.radarpracy.offer;

import jakarta.persistence.Converter;
import pl.radarpracy.common.JsonConverter;
import tools.jackson.core.type.TypeReference;

import java.util.ArrayList;
import java.util.List;

@Converter
public class ContractTypeListConverter extends JsonConverter<List<ContractType>> {

    public ContractTypeListConverter() {
        super(new TypeReference<>() {
        });
    }

    @Override
    protected List<ContractType> empty() {
        return new ArrayList<>();
    }
}
