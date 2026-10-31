package pl.radarpracy.profile;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import pl.radarpracy.common.StringListConverter;
import pl.radarpracy.offer.ContractType;
import pl.radarpracy.offer.ContractTypeListConverter;
import pl.radarpracy.offer.WorkMode;
import pl.radarpracy.offer.WorkModeListConverter;

import java.util.ArrayList;
import java.util.List;

/** Profil kandydata (aplikacja jest jednoosobowa — zawsze id = 1). */
@Entity
@Table(name = "candidate_profile")
public class CandidateProfile {

    public static final long SINGLETON_ID = 1L;

    @Id
    private Long id = SINGLETON_ID;

    private String fullName;
    private String email;
    private String phone;
    /** Miasto zamieszkania. */
    private String location;
    /** Krótki nagłówek, np. „Java Developer, 4 lata doświadczenia”. */
    private String headline;

    @Convert(converter = SkillEntryListConverter.class)
    @Column(columnDefinition = "TEXT")
    private List<SkillEntry> skills = new ArrayList<>();

    // --- preferencje ---

    @Convert(converter = StringListConverter.class)
    @Column(columnDefinition = "TEXT")
    private List<String> desiredRoles = new ArrayList<>();

    @Convert(converter = StringListConverter.class)
    @Column(columnDefinition = "TEXT")
    private List<String> preferredLocations = new ArrayList<>();

    @Convert(converter = WorkModeListConverter.class)
    @Column(columnDefinition = "TEXT")
    private List<WorkMode> workModes = new ArrayList<>();

    @Convert(converter = ContractTypeListConverter.class)
    @Column(columnDefinition = "TEXT")
    private List<ContractType> contractTypes = new ArrayList<>();

    /** Minimalne wynagrodzenie miesięczne w PLN jako równoważnik UoP brutto. */
    private Integer minMonthlySalary;

    private boolean willingToRelocate;

    /** Frazy wykluczające ofertę (np. „praca zmianowa”, „weekendy”). */
    @Convert(converter = StringListConverter.class)
    @Column(columnDefinition = "TEXT")
    private List<String> dealBreakers = new ArrayList<>();

    /** Cel: liczba aplikacji tygodniowo. */
    private Integer weeklyApplicationGoal = 10;

    /** Po ilu dniach bez odpowiedzi przypominać o follow-upie. */
    private Integer followUpDays = 7;

    /** Aktywna wersja CV używana domyślnie do dopasowania. */
    private Long activeCvId;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
    public String getHeadline() { return headline; }
    public void setHeadline(String headline) { this.headline = headline; }
    public List<SkillEntry> getSkills() { return skills; }
    public void setSkills(List<SkillEntry> skills) { this.skills = skills == null ? new ArrayList<>() : new ArrayList<>(skills); }
    public List<String> getDesiredRoles() { return desiredRoles; }
    public void setDesiredRoles(List<String> desiredRoles) { this.desiredRoles = desiredRoles == null ? new ArrayList<>() : new ArrayList<>(desiredRoles); }
    public List<String> getPreferredLocations() { return preferredLocations; }
    public void setPreferredLocations(List<String> preferredLocations) { this.preferredLocations = preferredLocations == null ? new ArrayList<>() : new ArrayList<>(preferredLocations); }
    public List<WorkMode> getWorkModes() { return workModes; }
    public void setWorkModes(List<WorkMode> workModes) { this.workModes = workModes == null ? new ArrayList<>() : new ArrayList<>(workModes); }
    public List<ContractType> getContractTypes() { return contractTypes; }
    public void setContractTypes(List<ContractType> contractTypes) { this.contractTypes = contractTypes == null ? new ArrayList<>() : new ArrayList<>(contractTypes); }
    public Integer getMinMonthlySalary() { return minMonthlySalary; }
    public void setMinMonthlySalary(Integer minMonthlySalary) { this.minMonthlySalary = minMonthlySalary; }
    public boolean isWillingToRelocate() { return willingToRelocate; }
    public void setWillingToRelocate(boolean willingToRelocate) { this.willingToRelocate = willingToRelocate; }
    public List<String> getDealBreakers() { return dealBreakers; }
    public void setDealBreakers(List<String> dealBreakers) { this.dealBreakers = dealBreakers == null ? new ArrayList<>() : new ArrayList<>(dealBreakers); }
    public Integer getWeeklyApplicationGoal() { return weeklyApplicationGoal; }
    public void setWeeklyApplicationGoal(Integer weeklyApplicationGoal) { this.weeklyApplicationGoal = weeklyApplicationGoal; }
    public Integer getFollowUpDays() { return followUpDays; }
    public void setFollowUpDays(Integer followUpDays) { this.followUpDays = followUpDays; }
    public Long getActiveCvId() { return activeCvId; }
    public void setActiveCvId(Long activeCvId) { this.activeCvId = activeCvId; }
}
