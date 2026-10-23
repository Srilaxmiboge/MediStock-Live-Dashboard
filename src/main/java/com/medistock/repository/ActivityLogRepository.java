package com.medistock.repository;

import com.medistock.model.ActivityLog;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ActivityLogRepository extends JpaRepository<ActivityLog, Long> {
    List<ActivityLog> findTop8ByOrderByOccurredAtDescIdDesc();

    List<ActivityLog> findTop100ByOrderByOccurredAtDescIdDesc();
}
