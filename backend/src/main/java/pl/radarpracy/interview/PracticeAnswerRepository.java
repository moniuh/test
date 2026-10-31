package pl.radarpracy.interview;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PracticeAnswerRepository extends JpaRepository<PracticeAnswer, Long> {

    List<PracticeAnswer> findAllByOrderByCreatedAtDescIdDesc();
}
