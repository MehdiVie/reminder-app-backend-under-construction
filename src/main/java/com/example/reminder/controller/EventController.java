package com.example.reminder.controller;
import com.example.reminder.dto.ApiResponse;
import com.example.reminder.dto.EventRequest;
import com.example.reminder.dto.EventResponse;
import com.example.reminder.dto.PageResponse;
import com.example.reminder.model.Event;
import com.example.reminder.exception.ResourceNotFoundException;
import com.example.reminder.security.AuthContext;
import com.example.reminder.service.EventService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/events")// Allow all origins (for Angular frontend)
public class EventController {
    private final EventService service;
    private final AuthContext authContext;

    public EventController(EventService service,  AuthContext authContext) {
        this.service = service;
        this.authContext =  authContext;
    }

    /*
    Get /api/events/paged
    support pagination, sorting and optional date filter
    Example
    /api/events/paged?page=0&size=5&sortBy=is&direction=asc&afterDate=2025-10-18
    * */
    @GetMapping("/paged")
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

        var pageResult= service.getPagedEventsForUser(authContext.getCurrentUser(),page,size,sortBy,direction,
                dateFilter,search);

        List<EventResponse> eventResponses = pageResult.getContent().stream()
                        .map(EventResponse::fromEntity)
                                .toList();

        log.info("Get /api/events/paged -> page={} , size={} , sortBy={} , direction={} , afterDate={} , search={}" ,
                page , size , sortBy , direction , afterDate, search
                );

        PageResponse<EventResponse> responseData = new PageResponse<>();
        responseData.setContent(eventResponses);
        responseData.setCurrentPage( pageResult.getNumber());
        responseData.setTotalItems( pageResult.getTotalElements());
        responseData.setTotalPages( pageResult.getTotalPages());
        responseData.setSize(pageResult.getSize());

        return  ResponseEntity.ok(new ApiResponse<>("success", "Paged Events retrieved" , responseData));
    }

    @GetMapping("/upcoming")
    public ResponseEntity<ApiResponse<List<EventResponse>>> getUpcomingEvents (
                                                    @RequestParam(defaultValue = "1") long minute) {
        var currentUser = authContext.getCurrentUser();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime threshold = LocalDateTime.now().plusMinutes(minute);
        List<Event> events = service.getAllUpcomingReminders(currentUser, now, threshold);
        List<EventResponse> eventResponses = events.stream()
                .map(EventResponse::fromEntity)
                .toList();
        return ResponseEntity.ok(new ApiResponse<>("success", "Upcoming Events retrieved" , eventResponses));
    }

    /**
     * GET /api/events
     * Retrieve all events from the database.
     * Returns 200 OK if data exists, If list is empty, data = [] and a friendly message.
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<EventResponse>>> getAll() {
        List<Event> events = service.getAllEventsForCurrentUser(authContext.getCurrentUser());
        log.info("Get /api/events -> {} items", events.size());
        String message = (events.isEmpty()) ? "No Events found for current user." : "Events retrieved successfully.";
        List<EventResponse> eventResponseList = new ArrayList<>();
        for(Event e : events) {
            eventResponseList.add(
                    EventResponse.fromEntity(e)
            );
        };;
        return ResponseEntity.ok(new ApiResponse<>("success", message, eventResponseList));
    }


    /**
     * GET /api/events/{id}
     * Retrieve single event from the database.
     * On missing record -> throw ResourceNotFoundException (handled globally)
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<EventResponse>> getById(@PathVariable Long id) {
        Event event = service.getEventById(authContext.getCurrentUser(),id);
        if (event == null) {
            log.warn("Get /api/events/{} -> not found", id);
            throw new ResourceNotFoundException("Event with ID : "+ id +" not found.");
        }
        log.info("Get /api/events/{} -> OK ", id);

        return ResponseEntity.ok(new ApiResponse<>("success", "Event retrieved successfully",
                EventResponse.fromEntity(event)));
    }

    /**
     * POST /api/events
     * Creates a new Event
     * Return 201 after create
     * Validation Errors handled globally
     */
    @PostMapping
    public ResponseEntity<ApiResponse<EventResponse>> create(@RequestBody @Valid EventRequest eventRequest) {
        Event createdEvent = service.createEvent(authContext.getCurrentUser(),eventRequest);
        log.info("Post /api/events -> created id={} , title={}", createdEvent.getId(), createdEvent.getTitle());
        EventResponse eventResponse = EventResponse.fromEntity(createdEvent);
        return ResponseEntity.status(HttpStatus.CREATED).
                body(new ApiResponse<>("success", "Event Created",eventResponse)); // 201 Created
    }
    /**
     * PUT /api/events/{id}
     * Update an Event
     * If entity missing -> 404 via exception.
     * Validation Errors handled globally
     */
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<EventResponse>> update(@PathVariable Long id ,
                                                             @RequestBody @Valid EventRequest eventRequest) {

        Event existingEvent = service.getEventById(authContext.getCurrentUser(),id);

        if (existingEvent == null) {
            log.warn("Get /api/events/{} -> not found", id);
            throw new ResourceNotFoundException("Event with ID : "+ id +" not found.");
        }
        Event updatedEvent = service.updateEvent(authContext.getCurrentUser(),id, eventRequest);
        log.info("Put /api/events/{} -> updated", id);
        EventResponse updatedEventResponse = EventResponse.fromEntity(updatedEvent);
        return ResponseEntity.ok(new ApiResponse<>("success" , "Event Updated.",updatedEventResponse ));
    }

    /**
     * DELETE /api/events/{id}
     * If entity missing -> 404 via exception.
     * We return 200 with a success message (envelope) for consistency.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<EventResponse>> delete(@PathVariable Long id) {
        Event existingEvent = service.getEventById(authContext.getCurrentUser(),id);

        if (existingEvent == null) {
            log.warn("Get /api/events/{} -> not found", id);
            throw new ResourceNotFoundException("Event with ID : "+ id +" not found.");
        }
        EventResponse eventResponse = EventResponse.fromEntity(existingEvent);
        service.deleteEvent(authContext.getCurrentUser(),id);
        log.info("Delete /api/events/{} -> deleted", id);
        return ResponseEntity.ok(new ApiResponse<>("success","Event Deleted.",eventResponse));
    }



}
