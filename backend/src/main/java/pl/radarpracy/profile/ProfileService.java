package pl.radarpracy.profile;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.radarpracy.common.BadRequestException;
import pl.radarpracy.common.NotFoundException;
import pl.radarpracy.skills.SkillDictionary;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Dostęp do profilu i wersji CV — punkt wejścia dla innych modułów. */
@Service
public class ProfileService {

    private final CandidateProfileRepository profiles;
    private final CvVariantRepository cvs;
    private final SkillDictionary dictionary;

    public ProfileService(CandidateProfileRepository profiles, CvVariantRepository cvs, SkillDictionary dictionary) {
        this.profiles = profiles;
        this.cvs = cvs;
        this.dictionary = dictionary;
    }

    /** Profil (tworzony pusty przy pierwszym odczycie). */
    @Transactional
    public CandidateProfile profile() {
        return profiles.findById(CandidateProfile.SINGLETON_ID).orElseGet(() -> profiles.save(new CandidateProfile()));
    }

    @Transactional
    public CandidateProfile update(CandidateProfile incoming) {
        CandidateProfile p = profile();
        p.setFullName(incoming.getFullName());
        p.setEmail(incoming.getEmail());
        p.setPhone(incoming.getPhone());
        p.setLocation(incoming.getLocation());
        p.setHeadline(incoming.getHeadline());
        p.setSkills(normalizeSkills(incoming.getSkills()));
        p.setDesiredRoles(incoming.getDesiredRoles());
        p.setPreferredLocations(incoming.getPreferredLocations());
        p.setWorkModes(incoming.getWorkModes());
        p.setContractTypes(incoming.getContractTypes());
        p.setMinMonthlySalary(incoming.getMinMonthlySalary());
        p.setWillingToRelocate(incoming.isWillingToRelocate());
        p.setDealBreakers(incoming.getDealBreakers());
        if (incoming.getWeeklyApplicationGoal() != null) {
            p.setWeeklyApplicationGoal(incoming.getWeeklyApplicationGoal());
        }
        if (incoming.getFollowUpDays() != null) {
            p.setFollowUpDays(incoming.getFollowUpDays());
        }
        if (incoming.getActiveCvId() != null) {
            cv(incoming.getActiveCvId());
            p.setActiveCvId(incoming.getActiveCvId());
        }
        return profiles.save(p);
    }

    /** Nazwy kanoniczne ze słownika, bez duplikatów (ostatni wpis wygrywa). */
    List<SkillEntry> normalizeSkills(List<SkillEntry> skills) {
        Map<String, SkillEntry> out = new LinkedHashMap<>();
        if (skills != null) {
            for (SkillEntry s : skills) {
                if (s == null || s.name() == null || s.name().isBlank()) {
                    continue;
                }
                String name = dictionary.canonicalName(s.name());
                out.put(name.toLowerCase(), new SkillEntry(name, s.level(), s.years()));
            }
        }
        return new ArrayList<>(out.values());
    }

    public List<CvVariant> cvVariants() {
        return cvs.findAllByOrderByIdAsc();
    }

    public CvVariant cv(Long id) {
        return cvs.findById(id).orElseThrow(() -> NotFoundException.of("Wersja CV", id));
    }

    /** Aktywna wersja CV lub pierwsza dostępna. */
    @Transactional
    public Optional<CvVariant> activeCv() {
        CandidateProfile p = profile();
        if (p.getActiveCvId() != null) {
            Optional<CvVariant> cv = cvs.findById(p.getActiveCvId());
            if (cv.isPresent()) {
                return cv;
            }
        }
        return cvs.findAllByOrderByIdAsc().stream().findFirst();
    }

    /** Wersja CV o podanym id albo aktywna, gdy id == null. */
    public Optional<CvVariant> cvOrActive(Long id) {
        return id == null ? activeCv() : Optional.of(cv(id));
    }

    @Transactional
    public CvVariant createCv(CvVariant incoming) {
        if (incoming.getName() == null || incoming.getName().isBlank()) {
            throw new BadRequestException("Podaj nazwę wersji CV");
        }
        CvVariant cv = new CvVariant();
        cv.setName(incoming.getName().strip());
        cv.setTargetRole(incoming.getTargetRole());
        cv.setContent(incoming.getContent() == null ? "" : incoming.getContent());
        cv.setOrigin(incoming.getOrigin() == null ? "ręcznie" : incoming.getOrigin());
        CvVariant saved = cvs.save(cv);
        CandidateProfile p = profile();
        if (p.getActiveCvId() == null) {
            p.setActiveCvId(saved.getId());
            profiles.save(p);
        }
        return saved;
    }

    @Transactional
    public CvVariant updateCv(Long id, CvVariant incoming) {
        CvVariant cv = cv(id);
        if (incoming.getName() != null && !incoming.getName().isBlank()) {
            cv.setName(incoming.getName().strip());
        }
        cv.setTargetRole(incoming.getTargetRole());
        if (incoming.getContent() != null) {
            cv.setContent(incoming.getContent());
        }
        return cvs.save(cv);
    }

    @Transactional
    public void deleteCv(Long id) {
        CvVariant cv = cv(id);
        cvs.delete(cv);
        CandidateProfile p = profile();
        if (id.equals(p.getActiveCvId())) {
            p.setActiveCvId(cvs.findAllByOrderByIdAsc().stream().findFirst().map(CvVariant::getId).orElse(null));
            profiles.save(p);
        }
    }

    @Transactional
    public CandidateProfile activateCv(Long id) {
        cv(id);
        CandidateProfile p = profile();
        p.setActiveCvId(id);
        return profiles.save(p);
    }
}
