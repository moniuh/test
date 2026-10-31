package pl.radarpracy.interview;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StarStoryRepository extends JpaRepository<StarStory, Long> {

    List<StarStory> findAllByOrderByIdAsc();
}
