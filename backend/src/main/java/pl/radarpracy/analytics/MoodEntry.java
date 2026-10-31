package pl.radarpracy.analytics;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.time.LocalDate;

/** Wpis w dzienniku samopoczucia (pomysł #76) — najwyżej jeden na dzień. */
@Entity
@Table(name = "analytics_mood_entry", uniqueConstraints = @UniqueConstraint(columnNames = "entry_date"))
public class MoodEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Dzień wpisu (kolumna „entry_date”, bo DATE to słowo kluczowe SQL). */
    @Column(name = "entry_date", nullable = false)
    private LocalDate date;

    /** Nastrój 1–5 (1 = bardzo źle, 5 = bardzo dobrze). */
    @Column(nullable = false)
    private int mood;

    /** Energia 1–5 (1 = bardzo niska, 5 = bardzo wysoka). */
    @Column(nullable = false)
    private int energy;

    @Column(length = 1000)
    private String note;

    private Instant createdAt;
    private Instant updatedAt;

    public MoodEntry() {
    }

    public MoodEntry(LocalDate date, int mood, int energy, String note) {
        this.date = date;
        this.mood = mood;
        this.energy = energy;
        this.note = note;
    }

    @PrePersist
    void onCreate() {
        createdAt = updatedAt = Instant.now();
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }
    public int getMood() { return mood; }
    public void setMood(int mood) { this.mood = mood; }
    public int getEnergy() { return energy; }
    public void setEnergy(int energy) { this.energy = energy; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
