package com.taskforge.repository;
import com.taskforge.model.Job;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface JobRepository extends JpaRepository<Job, UUID> {
    Page<Job> findAllByOrderByCreatedAtDesc(Pageable pageable);
    Page<Job> findByStatusOrderByCreatedAtDesc(Job.JobStatus status, Pageable pageable);
    Page<Job> findByQueuePriorityOrderByCreatedAtDesc(Job.QueuePriority priority, Pageable pageable);
    long countByStatus(Job.JobStatus status);

    @Query("SELECT j FROM Job j WHERE j.status = 'DEAD_LETTER' ORDER BY j.updatedAt DESC")
    List<Job> findDeadLetterJobs();
}
