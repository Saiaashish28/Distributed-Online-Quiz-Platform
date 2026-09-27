package com.quizsphere.repository;

import com.quizsphere.entity.Answer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface AnswerRepository extends JpaRepository<Answer, Long> {

    List<Answer> findByAttemptId(Long attemptId);

    List<Answer> findByAttemptIdIn(Collection<Long> attemptIds);

    /**
     * Upserts one answer per (attempt, question). A write only wins when its client sequence
     * number is newer, so a delayed retry can never overwrite a later selection.
     */
    @Modifying
    @Query(value = """
            INSERT INTO answers (attempt_id, question_id, option_id, client_seq, updated_at)
            VALUES (:attemptId, :questionId, :optionId, :seq, :now)
            ON CONFLICT (attempt_id, question_id) DO UPDATE
               SET option_id = EXCLUDED.option_id, client_seq = EXCLUDED.client_seq, updated_at = EXCLUDED.updated_at
             WHERE answers.client_seq < EXCLUDED.client_seq
            """, nativeQuery = true)
    int upsert(Long attemptId, Long questionId, Long optionId, long seq, Instant now);
}
