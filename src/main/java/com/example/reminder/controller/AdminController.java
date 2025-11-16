package com.example.reminder.controller;

import com.example.reminder.dto.*;
import com.example.reminder.exception.ResourceNotFoundException;
import com.example.reminder.model.Event;
import com.example.reminder.repository.EventRepository;
import com.example.reminder.repository.UserRepository;
import com.example.reminder.service.EmailService;
import com.example.reminder.service.EventService;
import jakarta.validation.constraints.Email;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin")
public class AdminController {

    private final EventRepository eventRepo;
    private final UserRepository userRepo;
    private final EmailService emailService;
    private final EventService eventService;

    @GetMapping("/events/paged")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Object>> getPagedEvents (
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "5") Integer size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "asc") String direction,
            @RequestParam(required = false) String afterDate,
            @RequestParam(required = false) String search) {

        LocalDate dateFilter = null;
        if (afterDate != null && !afterDate.isEmpty()) {
            dateFilter = LocalDate.parse(afterDate);
        }

        var pageResult= eventService.getPagedEventsForAdmin(page,size,sortBy,direction,dateFilter,search);

        List<AdminEventResponse> eventResponses = pageResult.getContent().stream()
                .map(AdminEventResponse::fromEntity)
                .toList();



        log.info("Get /api/events/paged -> page={} , size={} , sortBy={} , direction={} , afterDate={} , " +
                        " search={} " ,
                page , size , sortBy , direction , afterDate, search
        );

        PageResponse<AdminEventResponse> responseData = new PageResponse<>();
        responseData.setContent(eventResponses);
        responseData.setCurrentPage( pageResult.getNumber());
        responseData.setTotalItems( pageResult.getTotalElements());
        responseData.setTotalPages( pageResult.getTotalPages());
        responseData.setSize(pageResult.getSize());

        return  ResponseEntity.ok(new ApiResponse<>("success", "Paged Events retrieved" , responseData));
    }

    // Only ADMINs can access
    @GetMapping("/events")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<AdminEventResponse>>> getAllEventsForAdmin(
            @RequestParam (required = false) String userEmail ,
            @RequestParam (required = false) Boolean reminderSent
    ) {

        List<Event> events = eventRepo.findAll();

        if (userEmail != null) {
            events = events.stream()
                    .filter(e-> e.getUser().getEmail().equalsIgnoreCase(userEmail))
                    .toList();
        }

        if (reminderSent != null) {
            events = events.stream()
                    .filter(e-> e.isReminderSent() == reminderSent)
                    .toList();
        }

        List<AdminEventResponse> response = events.stream()
                .map(AdminEventResponse::fromEntity)
                .toList();

        return ResponseEntity.ok(new ApiResponse<>("success","All events fetched",response));
    }

    @GetMapping("/events/pending")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<AdminEventResponse>>> getPendingRemindersForAdmin() {
        List<AdminEventResponse> events = eventRepo.findAll().stream()
                .filter(e -> !e.isReminderSent())
                .map(AdminEventResponse::fromEntity)
                .toList();

        return ResponseEntity.ok(new ApiResponse<>("success","Pending events fetched",events));
    }

    @GetMapping("/users")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<UserSummaryResponse>>> getAllUsers() {
        List<UserSummaryResponse> users = userRepo.findAll().stream()
                .map(UserSummaryResponse::fromEntity)
                .toList();

        return ResponseEntity.ok(new ApiResponse<>("success","All Users",users));
    }

    @PostMapping("/events/{id}/send-reminder")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> sendReminderNow(@PathVariable Long id) {
        Event event = eventRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found with id " + id));

        if (event.isReminderSent()) {
            return ResponseEntity.badRequest()
                    .body(new ApiResponse<>("error", "Reminder already sent.",null));
        }

        try {
            String htmlBody = emailService.buildReminderHtml(event);
            emailService.sendReminderHtml(
                    event.getUser().getEmail(),
                    "Reminder: " + event.getTitle(),
                    htmlBody
                    );

            event.setReminderSent(true);
            event.setReminderSentTime(LocalDateTime.now());
            eventRepo.save(event);

            return ResponseEntity.ok(
                    new ApiResponse<>("success", "Reminder sent successfully.",null)
            );

        } catch (Exception e) {
            log.error("Faild to send manual reminder for event {}", event ,e);
            return ResponseEntity.internalServerError()
                    .body(new ApiResponse<>("error", "Faild to send reminder.",null));
        }

    }

    @GetMapping("/stats")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Map<String,Object>>> getSystemStats() {
        Map<String,Object> stats = new HashMap<>();

        // Overall Statistics
        stats.put("totalUsers" , userRepo.count());
        stats.put("totalEvents", eventRepo.count());
        stats.put("totalReminderSent", eventRepo.countByReminderSentTrue());
        stats.put("totalPendingReminders", eventRepo.countByReminderSentFalse());
        stats.put("totalEventsLastSevenDays", eventRepo.countEventsCreatedAfter(LocalDateTime.now().minusDays(7)));


        // Time-based Statistics
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime last7Days = now.minusDays(6);
        LocalDateTime last24Hours = now.minusHours(24);
        LocalDateTime next24Hours = now.plusHours(24);

        stats.put("countEventsLast24Hours", eventRepo.countEventsCreatedAfter(last24Hours));

        stats.put("countEventsLast7Days", eventRepo.countEventsCreatedAfter(last7Days));

        stats.put("countRemindersSentLast24Hours", eventRepo.countReminderSentAfter(last24Hours));
        stats.put("countUpcomingRemindersNext24Hours", eventRepo.countReminderSentBetween(now , next24Hours));

        stats.put("eventsLast7Days", eventService.getEventsPerDay());

        return ResponseEntity.ok(
                new ApiResponse<>("success", "System stats fetched.",stats)
        );
    }

}
