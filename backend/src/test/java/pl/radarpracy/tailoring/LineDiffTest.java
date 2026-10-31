package pl.radarpracy.tailoring;

import org.junit.jupiter.api.Test;
import pl.radarpracy.tailoring.LineDiff.Entry;
import pl.radarpracy.tailoring.LineDiff.Kind;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LineDiffTest {

    @Test
    void classifiesAddedMovedChangedAndRemovedLines() {
        String base = """
                Jan
                - a pierwszy punkt
                - b drugi punkt
                - c trzeci punkt
                Java, Docker, Kafka
                """;
        String tailored = """
                Jan
                Nowa linia podsumowania
                - c trzeci punkt
                - a pierwszy punkt
                Kafka, Java, Docker
                """;
        List<Entry> diff = LineDiff.diff(base, tailored);
        assertThat(diff).extracting(Entry::kind).containsExactly(Kind.SAME, Kind.ADDED, Kind.MOVED, Kind.SAME,
                Kind.CHANGED, Kind.REMOVED);
        assertThat(diff.get(1).text()).isEqualTo("Nowa linia podsumowania");
        assertThat(diff.get(2).text()).isEqualTo("- c trzeci punkt");
        assertThat(diff.get(2).baseLine()).isEqualTo(4);
        assertThat(diff.get(2).newLine()).isEqualTo(3);
        assertThat(diff.get(4).previous()).isEqualTo("Java, Docker, Kafka");
        assertThat(diff.get(5).text()).isEqualTo("- b drugi punkt");
        assertThat(diff.get(5).newLine()).isNull();
    }

    @Test
    void identicalTextsAreAllSameAndBlankLinesAreIgnored() {
        List<Entry> diff = LineDiff.diff("a\n\nb\n", "a\nb");
        assertThat(diff).extracting(Entry::kind).containsExactly(Kind.SAME, Kind.SAME);
        assertThat(LineDiff.diff("", "")).isEmpty();
        assertThat(LineDiff.diff(null, "x")).extracting(Entry::kind).containsExactly(Kind.ADDED);
    }
}
