package com.example.reminder.repository;

import com.example.reminder.model.Event;
import com.example.reminder.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface EventRepository extends JpaRepository<Event, Long> {

    List<Event> findByUser(@Param("user") User user);

    Page<Event> findByUser(@Param("user") User user, Pageable pageable);

    Page<Event> findAll(Pageable pageable);

    // return all Events after specific date
    @Query("SELECT e FROM Event e WHERE e.eventDate >= :date")
    Page<Event> findAllAfterDate(@Param("date") LocalDate date , Pageable pageable);

    @Query("SELECT e FROM Event e WHERE e.user=:user AND e.eventDate >= :date " +
            "AND (LOWER(title) LIKE :search OR " +
            "LOWER(description) LIKE :search)")
    Page<Event> findByUserAndAfterDateAndSearch(@Param("user") User user, @Param("date")LocalDate date ,
                                                @Param("search") String search , Pageable pageable);
    // return all Events after specific date
    @Query("SELECT e FROM Event e WHERE e.user=:user AND e.eventDate >= :date")
    Page<Event> findByUserAndAfterDate(@Param("user") User user, @Param("date")LocalDate date ,
                                       Pageable pageable);

    @Query("SELECT e FROM Event e WHERE e.user=:user " +
            "AND (LOWER(title) LIKE :search OR " +
            "LOWER(description) LIKE :search)")
    Page<Event> findByUserAndSearch(@Param("user") User user,@Param("search") String search , Pageable pageable);

    @Query("SELECT e FROM Event e WHERE e.eventDate >= :date " +
            "AND (LOWER(title) LIKE :search OR " +
            "LOWER(description) LIKE :search)")
    Page<Event> findAllEventsAndAfterDateAndSearch(@Param("date")LocalDate date ,
                                                @Param("search") String search , Pageable pageable);
    @Query("SELECT e FROM Event e WHERE  " +
            " (LOWER(title) LIKE :search OR " +
            "LOWER(description) LIKE :search)")
    Page<Event> findAllEventsAndSearch(@Param("search") String search , Pageable pageable);

    @Query("SELECT e FROM Event e WHERE e.eventDate >= :date")
    Page<Event> findAllEventsAndAfterDate(@Param("date")LocalDate date ,
                                       Pageable pageable);

    @Query("SELECT e FROM Event e WHERE e.reminderSent = false AND e.reminderTime <= :now ")
    List<Event> findPendingReminders(@Param("now") LocalDateTime now);

    @Query("SELECT e FROM Event e WHERE e.user=:user AND e.reminderSent = false AND " +
                                    "e.reminderTime <= :threshold  AND e.reminderTime >= :now ")
    List<Event> findUpcomingReminders(@Param("user") User user, @Param("now") LocalDateTime now,
                                      @Param("threshold")LocalDateTime threshold);

    @Modifying(clearAutomatically = true , flushAutomatically = true)
    @Query("UPDATE Event e SET reminderSent=true , reminderSentTime=now() WHERE e.id in :ids")
    int markRemindersSentByIds(@Param("ids") List<Long> ids);

    @Query("SELECT COUNT(e) FROM Event e WHERE e.reminderSent = true")
    Long countByReminderSentTrue();

    @Query("SELECT COUNT(e) FROM Event e WHERE e.reminderSent = false")
    Long countByReminderSentFalse();

    @Query("SELECT COUNT(e) FROM Event e WHERE e.createdAt >= :from")
    Long countEventsCreatedAfter(@Param("from") LocalDateTime from);

    @Query("SELECT COUNT(e) FROM Event e WHERE e.reminderSent=true AND e.reminderSentTime >= :from")
    Long countReminderSentAfter(@Param("from") LocalDateTime from);

    @Query("SELECT COUNT(e) FROM Event e WHERE e.reminderSent=false AND e.reminderTime >= :from" +
            " AND e.reminderTime <= :to")
    Long countReminderSentBetween(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query("SELECT e.eventDate as date , COUNT(e) as cnt FROM Event e WHERE " +
            " e.eventDate >= :from AND e.eventDate <= :to GROUP BY  e.eventDate ORDER BY  e.eventDate")
    List<Object[]> eventsPerDaySince(@Param("from") LocalDate from,@Param("to") LocalDate to);
}
