package pl.radarpracy.interview;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import pl.radarpracy.common.StringListConverter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Zapisana próba odpowiedzi na pytanie rekrutacyjne z oceną STAR (pomysł #54). */
@Entity
@Table(name = "interview_practice_answer")
public class PracticeAnswer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String question;
    @Column(columnDefinition = "TEXT", nullable = false)
    private String answer;
    /** Oferta, do której ćwiczono (opcjonalnie). */
    private Long offerId;

    private int score;
    @Column(name = "star_situation")
    private boolean situation;
    @Column(name = "star_task")
    private boolean task;
    @Column(name = "star_action")
    private boolean action;
    @Column(name = "star_result")
    private boolean result;
    private boolean hasNumbers;
    private int wordCount;

    @Convert(converter = StringListConverter.class)
    @Column(columnDefinition = "TEXT")
    private List<String> feedback = new ArrayList<>();

    /** Komentarz AI (gdy był dostępny). */
    @Column(columnDefinition = "TEXT")
    private String aiComment;

    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getQuestion() { return question; }
    public void setQuestion(String question) { this.question = question; }
    public String getAnswer() { return answer; }
    public void setAnswer(String answer) { this.answer = answer; }
    public Long getOfferId() { return offerId; }
    public void setOfferId(Long offerId) { this.offerId = offerId; }
    public int getScore() { return score; }
    public void setScore(int score) { this.score = score; }
    public boolean isSituation() { return situation; }
    public void setSituation(boolean situation) { this.situation = situation; }
    public boolean isTask() { return task; }
    public void setTask(boolean task) { this.task = task; }
    public boolean isAction() { return action; }
    public void setAction(boolean action) { this.action = action; }
    public boolean isResult() { return result; }
    public void setResult(boolean result) { this.result = result; }
    public boolean isHasNumbers() { return hasNumbers; }
    public void setHasNumbers(boolean hasNumbers) { this.hasNumbers = hasNumbers; }
    public int getWordCount() { return wordCount; }
    public void setWordCount(int wordCount) { this.wordCount = wordCount; }
    public List<String> getFeedback() { return feedback; }
    public void setFeedback(List<String> feedback) { this.feedback = feedback == null ? new ArrayList<>() : new ArrayList<>(feedback); }
    public String getAiComment() { return aiComment; }
    public void setAiComment(String aiComment) { this.aiComment = aiComment; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
