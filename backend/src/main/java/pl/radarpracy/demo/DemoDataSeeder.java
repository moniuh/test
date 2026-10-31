package pl.radarpracy.demo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import pl.radarpracy.application.ApplicationEvent;
import pl.radarpracy.application.ApplicationEventRepository;
import pl.radarpracy.application.ApplicationStatus;
import pl.radarpracy.application.EventType;
import pl.radarpracy.application.Interview;
import pl.radarpracy.application.InterviewKind;
import pl.radarpracy.application.InterviewRepository;
import pl.radarpracy.application.JobApplication;
import pl.radarpracy.application.JobApplicationRepository;
import pl.radarpracy.offer.ContractType;
import pl.radarpracy.offer.JobOffer;
import pl.radarpracy.offer.JobOfferRepository;
import pl.radarpracy.offer.OfferStatus;
import pl.radarpracy.offer.RejectReason;
import pl.radarpracy.offer.RequirementExtractor;
import pl.radarpracy.offer.Seniority;
import pl.radarpracy.offer.WorkMode;
import pl.radarpracy.profile.CandidateProfile;
import pl.radarpracy.profile.CandidateProfileRepository;
import pl.radarpracy.profile.CvVariant;
import pl.radarpracy.profile.CvVariantRepository;
import pl.radarpracy.profile.SkillEntry;
import pl.radarpracy.salary.SalaryPeriod;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

/**
 * Dane demonstracyjne (fikcyjna kandydatka i fikcyjne firmy) tworzone przy pierwszym uruchomieniu,
 * gdy baza jest pusta i {@code radar.demo-data=true}. Daty są liczone względem dnia uruchomienia.
 */
@Component
@Order(0)
public class DemoDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    private final boolean enabled;
    private final CandidateProfileRepository profiles;
    private final CvVariantRepository cvs;
    private final JobOfferRepository offers;
    private final JobApplicationRepository applications;
    private final ApplicationEventRepository events;
    private final InterviewRepository interviews;
    private final RequirementExtractor extractor;

    public DemoDataSeeder(@Value("${radar.demo-data:true}") boolean enabled, CandidateProfileRepository profiles,
                          CvVariantRepository cvs, JobOfferRepository offers, JobApplicationRepository applications,
                          ApplicationEventRepository events, InterviewRepository interviews,
                          RequirementExtractor extractor) {
        this.enabled = enabled;
        this.profiles = profiles;
        this.cvs = cvs;
        this.offers = offers;
        this.applications = applications;
        this.events = events;
        this.interviews = interviews;
        this.extractor = extractor;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!enabled || offers.count() > 0 || cvs.count() > 0) {
            return;
        }
        log.info("Tworzę dane demonstracyjne (radar.demo-data=true)");
        seed();
    }

    @Transactional
    public void seed() {
        CvVariant backend = cv("Backend Java", "Backend Developer", DemoTexts.CV_BACKEND);
        CvVariant fullstack = cv("Fullstack Java + React", "Fullstack Developer", DemoTexts.CV_FULLSTACK);

        CandidateProfile p = profiles.findById(CandidateProfile.SINGLETON_ID).orElseGet(CandidateProfile::new);
        p.setFullName("Anna Nowak");
        p.setEmail("anna.nowak@example.com");
        p.setPhone("+48 600 100 200");
        p.setLocation("Warszawa");
        p.setHeadline("Java Developer, 4 lata doświadczenia komercyjnego");
        p.setSkills(List.of(new SkillEntry("Java", 4, 4), new SkillEntry("Spring Boot", 4, 4),
                new SkillEntry("PostgreSQL", 3, 4), new SkillEntry("React", 3, 2), new SkillEntry("TypeScript", 3, 2),
                new SkillEntry("Docker", 3, 3), new SkillEntry("Kafka", 2, 1), new SkillEntry("Angielski", 4, null),
                new SkillEntry("Git", 4, 4)));
        p.setDesiredRoles(List.of("Java Developer", "Backend Developer", "Fullstack Developer"));
        p.setPreferredLocations(List.of("Warszawa", "zdalnie"));
        p.setWorkModes(List.of(WorkMode.REMOTE, WorkMode.HYBRID));
        p.setContractTypes(List.of(ContractType.UOP, ContractType.B2B));
        p.setMinMonthlySalary(15000);
        p.setWillingToRelocate(false);
        p.setDealBreakers(List.of("praca zmianowa", "dyspozycyjność 24/7"));
        p.setWeeklyApplicationGoal(8);
        p.setFollowUpDays(7);
        p.setActiveCvId(backend.getId());
        profiles.save(p);

        LocalDate today = LocalDate.now();

        JobOffer finpay = offer("Senior Java Developer", "FinPay Systems", "Warszawa", "justjoin.it",
                WorkMode.HYBRID, List.of(ContractType.B2B), Seniority.SENIOR, 22000, 28000, SalaryPeriod.MONTH, true,
                DemoTexts.OFFER_FINPAY, today.minusDays(3), List.of("Prywatna opieka medyczna", "Budżet szkoleniowy 5 000 zł"));
        JobOffer logitech = offer("Java Developer", "LogiTech Solutions", "Kraków", "pracuj.pl",
                WorkMode.REMOTE, List.of(ContractType.UOP), Seniority.MID, 14000, 18000, SalaryPeriod.MONTH, true,
                DemoTexts.OFFER_LOGITECH, today.minusDays(16), List.of("Karta sportowa", "Opieka medyczna", "PPK"));
        JobOffer medikos = offer("Fullstack Developer (Java + React)", "Medikos", "Warszawa", "LinkedIn",
                WorkMode.HYBRID, List.of(ContractType.B2B), Seniority.MID, 140, 170, SalaryPeriod.HOUR, true,
                DemoTexts.OFFER_MEDIKOS, today.minusDays(24), List.of("Elastyczne godziny", "MacBook"));
        JobOffer logitechDup = offer("Java Developer (Mid)", "LogiTech Solutions sp. z o.o.", "Kraków", "justjoin.it",
                WorkMode.REMOTE, List.of(ContractType.UOP), Seniority.MID, 14000, 18000, SalaryPeriod.MONTH, true,
                DemoTexts.OFFER_LOGITECH_DUP, today.minusDays(14), List.of("Karta sportowa", "PPK"));
        JobOffer sklepik = offer("Frontend Developer (React)", "Sklepik.pl", "Poznań", "nofluffjobs.com",
                WorkMode.REMOTE, List.of(ContractType.UOP), Seniority.MID, 11000, 15000, SalaryPeriod.MONTH, true,
                DemoTexts.OFFER_SKLEPIK, today.minusDays(30), List.of("Karta sportowa"));
        JobOffer datawise = offer("Junior Python Developer", "DataWise", "Wrocław", "pracuj.pl",
                WorkMode.ONSITE, List.of(ContractType.UOP), Seniority.JUNIOR, 7000, 9000, SalaryPeriod.MONTH, true,
                DemoTexts.OFFER_DATAWISE, today.minusDays(5), List.of());
        JobOffer redflags = offer("Młodszy specjalista ds. obsługi klienta", "Dynamiczny Zespół sp. z o.o.", "Warszawa",
                "olx.pl", WorkMode.ONSITE, List.of(ContractType.UOD), Seniority.JUNIOR, null, null, null, null,
                DemoTexts.OFFER_REDFLAGS, today.minusDays(2), List.of());
        JobOffer scam = offer("Praca zdalna — przyjmowanie paczek, 500 zł dziennie", "Global Parcel Partners", "zdalnie",
                "grupa na Facebooku", WorkMode.REMOTE, List.of(), null, 500, null, SalaryPeriod.DAY, false,
                DemoTexts.OFFER_SCAM, today.minusDays(1), List.of());
        JobOffer cloudnine = offer("DevOps Engineer", "CloudNine", "Gdańsk", "justjoin.it",
                WorkMode.REMOTE, List.of(ContractType.B2B), Seniority.SENIOR, 25000, 32000, SalaryPeriod.MONTH, true,
                DemoTexts.OFFER_CLOUDNINE, today.minusDays(4), List.of("Budżet na sprzęt"));
        JobOffer bankpol = offer("Tech Lead Java", "BankPol", "Warszawa", "strona firmy",
                WorkMode.ONSITE, List.of(ContractType.UOP), Seniority.LEAD, 28000, 34000, SalaryPeriod.MONTH, true,
                DemoTexts.OFFER_BANKPOL, today.minusDays(6), List.of("Premia roczna", "Opieka medyczna", "PPK"));
        JobOffer nordic = offer("Java Developer (remote, EU)", "Nordlys Software AB", "zdalnie", "LinkedIn",
                WorkMode.REMOTE, List.of(ContractType.B2B), Seniority.MID, 5500, 6500, SalaryPeriod.MONTH, true,
                DemoTexts.OFFER_NORDIC, today.minusDays(8), List.of("Workation", "Budżet szkoleniowy"));
        JobOffer kotlinFin = offer("Backend Developer (Kotlin)", "Kredyto", "Warszawa", "polecenie",
                WorkMode.REMOTE, List.of(ContractType.UOP, ContractType.B2B), Seniority.MID, 16000, 20000,
                SalaryPeriod.MONTH, true, DemoTexts.OFFER_KREDYTO, today.minusDays(9),
                List.of("Opieka medyczna", "Karta sportowa", "Akcje pracownicze"));
        JobOffer oldShop = offer("Java Developer", "RetailOne", "Warszawa", "pracuj.pl",
                WorkMode.HYBRID, List.of(ContractType.UOP), Seniority.MID, 13000, 16000, SalaryPeriod.MONTH, true,
                DemoTexts.OFFER_RETAILONE, today.minusDays(55), List.of("Opieka medyczna"));
        JobOffer oldInsure = offer("Java/Spring Developer", "InsureTech", "Łódź", "nofluffjobs.com",
                WorkMode.REMOTE, List.of(ContractType.B2B), Seniority.MID, 18000, 22000, SalaryPeriod.MONTH, true,
                DemoTexts.OFFER_INSURETECH, today.minusDays(48), List.of());

        nordic.setSalaryCurrency("EUR");
        finpay.setStatus(OfferStatus.SAVED);
        datawise.setStatus(OfferStatus.REJECTED);
        datawise.setRejectReason(RejectReason.TECH_STACK);
        datawise.setRejectNote("Python zamiast Javy, praca tylko z biura");
        oldShop.setStatus(OfferStatus.ARCHIVED);
        oldInsure.setStatus(OfferStatus.ARCHIVED);
        offers.saveAll(List.of(nordic, finpay, datawise, oldShop, oldInsure));

        // --- aplikacje ---
        JobApplication aLogi = application(logitech, ApplicationStatus.APPLIED, today.minusDays(12), "pracuj.pl",
                backend, null, null);
        aLogi.setNextFollowUpAt(today.minusDays(5));
        applications.save(aLogi);
        event(aLogi, EventType.CREATED, null, ApplicationStatus.APPLIED, "Aplikacja przez formularz pracuj.pl",
                today.minusDays(12));

        JobApplication aMed = application(medikos, ApplicationStatus.INTERVIEW, today.minusDays(20), "LinkedIn",
                fullstack, today.minusDays(14), today.minusDays(3));
        aMed.setContactName("Katarzyna Wiśniewska (Talent Acquisition)");
        aMed.setContactEmail("rekrutacja@medikos.example");
        aMed.setSalaryExpectation(26000);
        applications.save(aMed);
        event(aMed, EventType.CREATED, null, ApplicationStatus.APPLIED, null, today.minusDays(20));
        event(aMed, EventType.STATUS_CHANGED, ApplicationStatus.APPLIED, ApplicationStatus.SCREENING,
                "Telefon od rekruterki, umówiona rozmowa HR", today.minusDays(14));
        event(aMed, EventType.STATUS_CHANGED, ApplicationStatus.SCREENING, ApplicationStatus.INTERVIEW,
                "Zaproszenie na rozmowę techniczną", today.minusDays(3));
        Interview hr = new Interview();
        hr.setApplicationId(aMed.getId());
        hr.setScheduledAt(LocalDateTime.of(today.minusDays(10), LocalTime.of(10, 0)));
        hr.setDurationMinutes(30);
        hr.setKind(InterviewKind.HR);
        hr.setLocation("Google Meet");
        hr.setInterviewers("Katarzyna Wiśniewska");
        hr.setNotes("Pytania o motywację i oczekiwania finansowe. Zespół 6 osób, produkt dla przychodni.");
        hr.setQuestionsAsked("Dlaczego chcesz zmienić pracę?\nJakie masz oczekiwania finansowe?\nOpowiedz o najtrudniejszym projekcie.");
        hr.setWentWell("Dobrze opowiedziałam o migracji do Kafki.");
        hr.setToImprove("Za długo mówiłam o oczekiwaniach finansowych — przygotować widełki wcześniej.");
        hr.setSelfRating(4);
        hr.setDebriefedAt(Instant.now().minusSeconds(9 * 86400L));
        interviews.save(hr);
        Interview tech = new Interview();
        tech.setApplicationId(aMed.getId());
        tech.setScheduledAt(LocalDateTime.of(today.plusDays(3), LocalTime.of(14, 0)));
        tech.setDurationMinutes(90);
        tech.setKind(InterviewKind.TECHNICAL);
        tech.setLocation("https://meet.example.com/medikos-tech");
        tech.setInterviewers("Tech Lead + Senior Frontend");
        interviews.save(tech);

        JobApplication aSklep = application(sklepik, ApplicationStatus.REJECTED, today.minusDays(28), "nofluffjobs.com",
                fullstack, today.minusDays(22), today.minusDays(22));
        event(aSklep, EventType.CREATED, null, ApplicationStatus.APPLIED, null, today.minusDays(28));
        event(aSklep, EventType.STATUS_CHANGED, ApplicationStatus.APPLIED, ApplicationStatus.REJECTED,
                "Automatyczna odmowa: szukają osoby z większym doświadczeniem w React", today.minusDays(22));

        JobApplication aKred = application(kotlinFin, ApplicationStatus.SCREENING, today.minusDays(5), "polecenie",
                backend, today.minusDays(2), today.minusDays(2));
        aKred.setContactName("Piotr Zieliński (Engineering Manager)");
        applications.save(aKred);
        event(aKred, EventType.CREATED, null, ApplicationStatus.APPLIED, "Polecenie od byłego kolegi z zespołu",
                today.minusDays(5));
        event(aKred, EventType.STATUS_CHANGED, ApplicationStatus.APPLIED, ApplicationStatus.SCREENING,
                "Mail z prośbą o termin rozmowy", today.minusDays(2));

        JobApplication aBank = application(bankpol, ApplicationStatus.PLANNED, null, "strona firmy", backend, null, null);
        event(aBank, EventType.CREATED, null, ApplicationStatus.PLANNED, "Do wysłania po dopisaniu doświadczenia z mentoringu",
                today.minusDays(1));

        JobApplication aRetail = application(oldShop, ApplicationStatus.NO_RESPONSE, today.minusDays(52), "pracuj.pl",
                backend, null, null);
        event(aRetail, EventType.CREATED, null, ApplicationStatus.APPLIED, null, today.minusDays(52));
        event(aRetail, EventType.STATUS_CHANGED, ApplicationStatus.APPLIED, ApplicationStatus.NO_RESPONSE,
                "Brak odpowiedzi po 2 follow-upach", today.minusDays(30));

        JobApplication aInsure = application(oldInsure, ApplicationStatus.REJECTED, today.minusDays(45), "nofluffjobs.com",
                backend, today.minusDays(38), today.minusDays(31));
        event(aInsure, EventType.CREATED, null, ApplicationStatus.APPLIED, null, today.minusDays(45));
        event(aInsure, EventType.STATUS_CHANGED, ApplicationStatus.APPLIED, ApplicationStatus.INTERVIEW,
                "Rozmowa techniczna", today.minusDays(38));
        event(aInsure, EventType.STATUS_CHANGED, ApplicationStatus.INTERVIEW, ApplicationStatus.REJECTED,
                "Odmowa po rozmowie technicznej — za słabe AWS", today.minusDays(31));
        Interview insureTech = new Interview();
        insureTech.setApplicationId(aInsure.getId());
        insureTech.setScheduledAt(LocalDateTime.of(today.minusDays(36), LocalTime.of(11, 0)));
        insureTech.setKind(InterviewKind.TECHNICAL);
        insureTech.setInterviewers("Dwóch seniorów z zespołu platformy");
        insureTech.setQuestionsAsked("Jak działa Spring @Transactional?\nJak zaprojektujesz idempotentny consumer Kafki?\nJakie usługi AWS znasz?");
        insureTech.setWentWell("Pytania o Springa i Kafkę.");
        insureTech.setToImprove("AWS — znam tylko S3 i EC2, trzeba nadrobić ECS i IAM.");
        insureTech.setSelfRating(3);
        insureTech.setDebriefedAt(Instant.now().minusSeconds(35 * 86400L));
        interviews.save(insureTech);
    }

    private CvVariant cv(String name, String role, String content) {
        CvVariant cv = new CvVariant();
        cv.setName(name);
        cv.setTargetRole(role);
        cv.setContent(content);
        cv.setOrigin("dane demonstracyjne");
        return cvs.save(cv);
    }

    private JobOffer offer(String title, String company, String location, String source, WorkMode mode,
                           List<ContractType> contracts, Seniority seniority, Integer min, Integer max,
                           SalaryPeriod period, Boolean gross, String description, LocalDate published,
                           List<String> benefits) {
        JobOffer o = new JobOffer();
        o.setTitle(title);
        o.setCompany(company);
        o.setLocation(location);
        o.setSource(source);
        o.setUrl("https://example.com/oferty/" + Math.abs((title + company).hashCode()));
        o.setWorkMode(mode);
        o.setContractTypes(contracts);
        o.setSeniority(seniority);
        o.setSalaryMin(min);
        o.setSalaryMax(max);
        o.setSalaryCurrency("PLN");
        o.setSalaryPeriod(period);
        o.setSalaryGross(gross);
        o.setDescription(description);
        o.setRequirements(extractor.extract(description));
        o.setBenefits(benefits);
        o.setPublishedAt(published);
        o.setExpiresAt(published.plusDays(30));
        JobOffer saved = offers.save(o);
        saved.setCreatedAt(published.atStartOfDay(ZoneId.systemDefault()).toInstant());
        return offers.save(saved);
    }

    private JobApplication application(JobOffer offer, ApplicationStatus status, LocalDate appliedAt, String source,
                                       CvVariant cv, LocalDate firstResponse, LocalDate lastContact) {
        JobApplication a = new JobApplication();
        a.setOffer(offer);
        a.setStatus(status);
        a.setAppliedAt(appliedAt);
        a.setSource(source);
        a.setCvVariantId(cv.getId());
        a.setCvVariantName(cv.getName());
        a.setCvSnapshot(status == ApplicationStatus.PLANNED ? null : cv.getContent());
        a.setFirstResponseAt(firstResponse);
        a.setLastContactAt(lastContact);
        a.setBoardPosition((int) applications.count());
        return applications.save(a);
    }

    private void event(JobApplication a, EventType type, ApplicationStatus from, ApplicationStatus to, String note,
                       LocalDate when) {
        ApplicationEvent e = new ApplicationEvent(a.getId(), type, note);
        e.setFromStatus(from);
        e.setToStatus(to);
        e.setOccurredAt(when.atTime(9, 30).atZone(ZoneId.systemDefault()).toInstant());
        events.save(e);
    }
}
