package pl.radarpracy.application.snapshot;

import org.springframework.stereotype.Service;
import pl.radarpracy.application.ApplicationService;
import pl.radarpracy.application.JobApplication;
import pl.radarpracy.common.NotFoundException;
import pl.radarpracy.profile.CvVariant;
import pl.radarpracy.profile.ProfileService;

import java.util.Objects;

/** Snapshot wysłanych dokumentów (pomysł #46). Zapis snapshotu odbywa się w {@link ApplicationService}. */
@Service
public class SnapshotService {

    private final ApplicationService applications;
    private final ProfileService profiles;

    public SnapshotService(ApplicationService applications, ProfileService profiles) {
        this.applications = applications;
        this.profiles = profiles;
    }

    public SnapshotView view(Long applicationId) {
        JobApplication a = applications.get(applicationId);
        CvVariant current = null;
        if (a.getCvVariantId() != null) {
            try {
                current = profiles.cv(a.getCvVariantId());
            } catch (NotFoundException e) {
                current = null;
            }
        }
        return view(a, current);
    }

    public SnapshotView take(Long applicationId) {
        applications.snapshotNow(applicationId);
        return view(applicationId);
    }

    static SnapshotView view(JobApplication a, CvVariant current) {
        boolean has = a.getSnapshotAt() != null || a.getCvSnapshot() != null;
        Boolean cvChanged = null;
        int added = 0;
        int removed = 0;
        if (a.getCvSnapshot() != null && current != null) {
            TextDiff.Result diff = TextDiff.compare(a.getCvSnapshot(), current.getContent());
            cvChanged = !diff.same();
            added = diff.added();
            removed = diff.removed();
        }
        Boolean letterChanged = a.getSnapshotAt() == null ? null
                : !Objects.equals(normalize(a.getCoverLetterSnapshot()), normalize(a.getCoverLetter()));
        return new SnapshotView(a.getId(), has, a.getSnapshotAt(), a.getCvVariantId(), a.getCvVariantName(),
                a.getCvSnapshot(), a.getCoverLetterSnapshot(), current != null,
                current == null ? null : current.getName(), cvChanged, added, removed, letterChanged);
    }

    private static String normalize(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }
}
