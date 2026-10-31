package pl.radarpracy.application.snapshot;

import org.junit.jupiter.api.Test;
import pl.radarpracy.application.ApplicationStatus;
import pl.radarpracy.application.JobApplication;
import pl.radarpracy.profile.CvVariant;

import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.radarpracy.application.TestData.application;
import static pl.radarpracy.application.TestData.offer;

class SnapshotTest {

    @Test
    void diffIgnoresWhitespaceAndCountsChangedLines() {
        assertThat(TextDiff.compare("a\n b  c\n\n", "a\nb c").same()).isTrue();
        TextDiff.Result r = TextDiff.compare("Java\nSpring\nSQL", "Java\nSpring Boot\nSQL\nKafka");
        assertThat(r.same()).isFalse();
        assertThat(r.added()).isEqualTo(2);
        assertThat(r.removed()).isEqualTo(1);
        assertThat(TextDiff.compare(null, "x").added()).isEqualTo(1);
    }

    @Test
    void viewComparesSnapshotWithCurrentCv() {
        JobApplication a = application(1, offer(1, "Java Developer", "Firma"), ApplicationStatus.APPLIED,
                LocalDate.of(2026, 10, 1));
        a.setCvVariantId(5L);
        a.setCvVariantName("Backend");
        a.setCvSnapshot("Java\nSpring");
        a.setCoverLetterSnapshot("List v1");
        a.setCoverLetter("List v1");
        a.setSnapshotAt(Instant.parse("2026-10-01T10:00:00Z"));
        CvVariant cv = new CvVariant();
        cv.setId(5L);
        cv.setName("Backend (nowsza)");
        cv.setContent("Java\nSpring\nKafka");

        SnapshotView v = SnapshotService.view(a, cv);
        assertThat(v.hasSnapshot()).isTrue();
        assertThat(v.cvChanged()).isTrue();
        assertThat(v.linesAdded()).isEqualTo(1);
        assertThat(v.linesRemoved()).isZero();
        assertThat(v.coverLetterChanged()).isFalse();
        assertThat(v.currentCvName()).isEqualTo("Backend (nowsza)");

        a.setCoverLetter("List v2");
        assertThat(SnapshotService.view(a, cv).coverLetterChanged()).isTrue();
        SnapshotView noCv = SnapshotService.view(a, null);
        assertThat(noCv.currentCvExists()).isFalse();
        assertThat(noCv.cvChanged()).isNull();
    }

    @Test
    void applicationWithoutSnapshot() {
        JobApplication a = application(2, offer(2, "QA", "X"), ApplicationStatus.PLANNED, null);
        SnapshotView v = SnapshotService.view(a, null);
        assertThat(v.hasSnapshot()).isFalse();
        assertThat(v.coverLetterChanged()).isNull();
    }
}
