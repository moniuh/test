package pl.radarpracy.aggregator;

import org.junit.jupiter.api.Test;
import pl.radarpracy.offer.ContractType;
import pl.radarpracy.offer.JobOffer;
import pl.radarpracy.offer.WorkMode;
import pl.radarpracy.salary.SalaryNormalizer;
import pl.radarpracy.salary.SalaryPeriod;
import pl.radarpracy.salary.SalaryProperties;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SourceFilterTest {

    private final SalaryNormalizer normalizer = new SalaryNormalizer(SalaryProperties.defaults());

    private static JobOffer offer(String title, String location, WorkMode mode, String description) {
        JobOffer o = new JobOffer();
        o.setTitle(title);
        o.setCompany("Acme");
        o.setLocation(location);
        o.setWorkMode(mode);
        o.setDescription(description);
        return o;
    }

    private static SourceFilters filters(List<String> include, List<String> exclude, List<String> locations, Integer min) {
        return new SourceFilters(include, exclude, locations, min);
    }

    @Test
    void includeKeywordsMatchWholeWordsWithoutPolishCharacters() {
        JobOffer o = offer("Programista Java", "Kraków", WorkMode.HYBRID, "Praca z Spring Boot i księgowością.");
        assertThat(SourceFilter.evaluate(filters(List.of("java"), List.of(), List.of(), null), o, normalizer).accepted()).isTrue();
        assertThat(SourceFilter.evaluate(filters(List.of("KSIĘGOWOŚCIĄ"), List.of(), List.of(), null), o, normalizer).accepted()).isTrue();
        assertThat(SourceFilter.evaluate(filters(List.of("spring boot"), List.of(), List.of(), null), o, normalizer).accepted()).isTrue();
        SourceFilter.Decision d = SourceFilter.evaluate(filters(List.of("python", "go"), List.of(), List.of(), null), o, normalizer);
        assertThat(d.accepted()).isFalse();
        assertThat(d.reason()).contains("python, go");
        // „jav” nie jest całym słowem, ale „jav*” to prefiks
        assertThat(SourceFilter.evaluate(filters(List.of("jav"), List.of(), List.of(), null), o, normalizer).accepted()).isFalse();
        assertThat(SourceFilter.evaluate(filters(List.of("jav*"), List.of(), List.of(), null), o, normalizer).accepted()).isTrue();
    }

    @Test
    void excludeKeywordsRejectWithReason() {
        JobOffer o = offer("Senior Java Developer", "Warszawa", WorkMode.ONSITE, "Praca zmianowa, współpraca z seniorami.");
        SourceFilter.Decision d = SourceFilter.evaluate(filters(List.of(), List.of("senior"), List.of(), null), o, normalizer);
        assertThat(d.accepted()).isFalse();
        assertThat(d.reason()).isEqualTo("wykluczone słowo „senior” w tytule");
        JobOffer mid = offer("Java Developer", "Warszawa", WorkMode.ONSITE, "Współpraca z seniorami.");
        assertThat(SourceFilter.evaluate(filters(List.of(), List.of("senior"), List.of(), null), mid, normalizer).accepted()).isTrue();
        assertThat(SourceFilter.evaluate(filters(List.of(), List.of("praca zmianowa"), List.of(), null), o, normalizer).reason())
                .isEqualTo("wykluczone słowo „praca zmianowa” w treści");
        assertThat(SourceFilter.matches("programista c++ i c#", "c")).isFalse();
        assertThat(SourceFilter.matches("programista c++ i c#", "c++")).isTrue();
    }

    @Test
    void locationsAcceptMatchingCityRemoteWorkAndUnknownLocation() {
        SourceFilters f = filters(List.of(), List.of(), List.of("Warszawa", "zdalnie"), null);
        assertThat(SourceFilter.evaluate(f, offer("A", "Warszawa, Mokotów", WorkMode.HYBRID, ""), normalizer).accepted()).isTrue();
        assertThat(SourceFilter.evaluate(f, offer("A", "Gdańsk", WorkMode.REMOTE, ""), normalizer).accepted()).isTrue();
        assertThat(SourceFilter.evaluate(f, offer("A", "Remote (EU)", null, ""), normalizer).accepted()).isTrue();
        assertThat(SourceFilter.evaluate(f, offer("A", null, null, ""), normalizer).accepted()).isTrue();
        SourceFilter.Decision d = SourceFilter.evaluate(f, offer("A", "Kraków", WorkMode.ONSITE, ""), normalizer);
        assertThat(d.accepted()).isFalse();
        assertThat(d.reason()).contains("Kraków");
        SourceFilters onlyCity = filters(List.of(), List.of(), List.of("Łódź"), null);
        assertThat(SourceFilter.evaluate(onlyCity, offer("A", "Lodz", WorkMode.ONSITE, ""), normalizer).accepted()).isTrue();
    }

    @Test
    void minSalaryComparesComparableUpperBound() {
        SourceFilters f = filters(List.of(), List.of(), List.of(), 15000);
        JobOffer low = offer("A", "Kraków", WorkMode.ONSITE, "");
        low.setSalaryMin(9000);
        low.setSalaryMax(12000);
        low.setSalaryPeriod(SalaryPeriod.MONTH);
        low.setSalaryGross(true);
        low.setContractTypes(List.of(ContractType.UOP));
        SourceFilter.Decision d = SourceFilter.evaluate(f, low, normalizer);
        assertThat(d.accepted()).isFalse();
        assertThat(d.reason()).contains("12").contains("15");

        // B2B 120 zł/h × 168 h ÷ 1,25 ≈ 16 130 zł — powyżej minimum
        JobOffer b2b = offer("A", "Kraków", WorkMode.REMOTE, "");
        b2b.setSalaryMin(100);
        b2b.setSalaryMax(120);
        b2b.setSalaryPeriod(SalaryPeriod.HOUR);
        b2b.setContractTypes(List.of(ContractType.B2B));
        assertThat(SourceFilter.evaluate(f, b2b, normalizer).accepted()).isTrue();

        // brak widełek albo tylko „od” — nie odrzucamy
        assertThat(SourceFilter.evaluate(f, offer("A", "Kraków", null, ""), normalizer).accepted()).isTrue();
        JobOffer from = offer("A", "Kraków", null, "");
        from.setSalaryMin(8000);
        assertThat(SourceFilter.evaluate(f, from, normalizer).accepted()).isTrue();
    }
}
