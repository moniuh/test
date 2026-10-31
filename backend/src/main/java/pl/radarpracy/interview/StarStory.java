package pl.radarpracy.interview;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import pl.radarpracy.common.StringListConverter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Historia STAR z banku osiągnięć kandydata (pomysł #56) — zapisana raz, podpowiadana do ofert i pytań. */
@Entity
@Table(name = "interview_star_story")
public class StarStory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;
    @Column(columnDefinition = "TEXT")
    private String situation;
    @Column(columnDefinition = "TEXT")
    private String task;
    @Column(columnDefinition = "TEXT")
    private String action;
    @Column(columnDefinition = "TEXT")
    private String result;

    /** Umiejętności, które historia pokazuje (nazwy kanoniczne ze słownika, gdy rozpoznane). */
    @Convert(converter = StringListConverter.class)
    @Column(columnDefinition = "TEXT")
    private List<String> skills = new ArrayList<>();

    private Instant createdAt;
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        createdAt = updatedAt = Instant.now();
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    /** Cała treść historii (do rozpoznawania umiejętności i wstawiania do treningu odpowiedzi). */
    public String fullText() {
        StringBuilder sb = new StringBuilder();
        for (String part : new String[]{title, situation, task, action, result}) {
            if (part != null && !part.isBlank()) {
                sb.append(part.strip()).append('\n');
            }
        }
        return sb.toString();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getSituation() { return situation; }
    public void setSituation(String situation) { this.situation = situation; }
    public String getTask() { return task; }
    public void setTask(String task) { this.task = task; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public String getResult() { return result; }
    public void setResult(String result) { this.result = result; }
    public List<String> getSkills() { return skills; }
    public void setSkills(List<String> skills) { this.skills = skills == null ? new ArrayList<>() : new ArrayList<>(skills); }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
