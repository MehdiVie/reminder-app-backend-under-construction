package com.example.reminder.service;
import com.example.reminder.dto.EventRequest;
import com.example.reminder.dto.EventResponse;
import com.example.reminder.exception.ResourceNotFoundException;
import com.example.reminder.model.Event;
import com.example.reminder.model.User;
import com.example.reminder.repository.EventRepository;
import com.example.reminder.security.AuthContext;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
public class EventService {
    private final EventRepository repo;
    private final EmailService emailService;



    // Allowed sort fields (white list)
    private static final Set<String> ALLOWED_SORTS = Set.of("id", "eventDate", "title", "reminderTime");


    public EventService(EventRepository repository, EmailService emailService) {
        this.repo = repository;
        this.emailService = emailService;



    }


    public  Page<Event> getPagedEventsForUser(User user,Integer page, Integer size, String sortBy,
                                      String direction, LocalDate afterDate, String search) {
        // defaults
        int p = (page == null || page < 0) ? 0 : page;
        int s = (size == null || size <= 0 || size > 100) ? 10 : size;

        // safe direction
        Sort.Direction dir;
        try {
            dir = (direction == null) ? Sort.Direction.ASC : Sort.Direction.valueOf(direction.toUpperCase());
        } catch (IllegalArgumentException e) {
            dir = Sort.Direction.ASC;
        }

        //safe sortBy
        String sortProb = (sortBy == null || !ALLOWED_SORTS.contains(sortBy)) ? "id" : sortBy;

        // stable sort
        Sort sort = Sort.by(new Sort.Order(dir, sortProb) , new Sort.Order(dir , "id"));

        Pageable pageable = PageRequest.of(p,s,sort);

        if(afterDate != null && search != null && !search.isEmpty()) {
            String likeSearch = "%" + search.toLowerCase().trim() + "%";
            return repo.findByUserAndAfterDateAndSearch(user, afterDate, likeSearch, pageable);
        } else if (afterDate != null) {
            return repo.findByUserAndAfterDate(user,afterDate, pageable);
        } else if (search != null && !search.isEmpty()){
            String likeSearch = "%" + search.toLowerCase().trim() + "%";

            return repo.findByUserAndSearch(user, likeSearch, pageable);
        }


        return repo.findByUser(user,pageable);


    }

    public  Page<Event> getPagedEventsForAdmin(Integer page, Integer size, String sortBy,
                                              String direction, LocalDate afterDate) {
        // defaults
        int p = (page == null || page < 0) ? 0 : page;
        int s = (size == null || size <= 0 || size > 100) ? 10 : size;

        // safe direction
        Sort.Direction dir;
        try {
            dir = (direction == null) ? Sort.Direction.ASC : Sort.Direction.valueOf(direction.toUpperCase());
        } catch (IllegalArgumentException e) {
            dir = Sort.Direction.ASC;
        }

        //safe sortBy
        String sortProb = (sortBy == null || !ALLOWED_SORTS.contains(sortBy)) ? "id" : sortBy;

        // stable sort
        Sort sort = Sort.by(new Sort.Order(dir, sortProb) , new Sort.Order(dir , "id"));

        Pageable pageable = PageRequest.of(p,s,sort);

        if (afterDate != null) {
            return repo.findAllAfterDate(afterDate, pageable);
        }

        return repo.findAll(pageable);

    }

    public Event getEventById(User user , Long id) {

        Event event =  repo.findById(id).orElse(null);

        if (event == null) {
            throw new ResourceNotFoundException("Event with ID " + id + " not found.");
        }

        if (!event.getUser().equals(user)) {
            throw new SecurityException("Access denied to this event.");
        }

        return event;
    }

    public List<Event> getAllEventsForCurrentUser(User user) {

        log.info("Current authenticated user: {}", user != null ? user.getEmail() : "null");
        return repo.findByUser(user);

    }

    public List<Event> getAllUpcomingReminders(User user,LocalDateTime now, LocalDateTime threshold) {
        return repo.findUpcomingReminders(user, now, threshold);
    }


    public Event createEvent(User user,EventRequest eventRequest) {

        if (eventRequest == null) {
            throw new IllegalArgumentException("Event cannot be null");
        }


        Event createdEvent = new Event();
        createdEvent.setTitle(eventRequest.getTitle());
        createdEvent.setDescription(eventRequest.getDescription());
        createdEvent.setEventDate(eventRequest.getEventDate());
        createdEvent.setReminderTime(eventRequest.getReminderTime());
        createdEvent.setUser(user);

        return repo.save(createdEvent);
    }

    public Event updateEvent(User user,Long id , EventRequest updatedEvent) {
        Event event = repo.findById(id).orElse(null);

        if (event == null || !event.getUser().equals(user)) return null;

        event.setTitle(updatedEvent.getTitle());
        event.setDescription(updatedEvent.getDescription());
        event.setEventDate(updatedEvent.getEventDate());
        event.setReminderTime(updatedEvent.getReminderTime());
        event.setReminderSent(false);
        event.setReminderSentTime(null);

        return repo.save(event);
    }

    public void deleteEvent(User user,Long id) {
        Event event = repo.findById(id).orElse(null);


        if (event == null || !event.getUser().equals(user)) {
            throw new SecurityException("Not allowed to delete this event");
        }
        repo.delete(event);
    }

    @Scheduled(fixedDelay = 60000)
    @Transactional
    public void checkReminders() {

        LocalDateTime now = LocalDateTime.now().withNano(0);
        /*System.out.println("------------------------------------------------");
        System.out.println("checkReminders() running at: " + now);
        System.out.println("Local time: " + LocalDateTime.now());*/
        List<Event> dueEvents = repo.findPendingReminders(now);
        List<Long> okIdies = new ArrayList<>();


        for(Event e : dueEvents) {
            //System.out.println("Scheduler running at: " + now);
            //System.out.println("Its time for event "+e.getTitle()+"("+e.getReminderTime()+")");
            try {
                String html = emailService.buildReminderHtml(e);
                emailService.sendReminderHtml(
                        e.getUser().getEmail(),
                        "Reminder: "+e.getTitle() ,
                        html
                );
                okIdies.add(e.getId());
            } catch (Exception ex) {
                log.error("Failed to update reminder for event {}", e.getId(), ex);
            }
        }

        if (!okIdies.isEmpty()) {
            int updated = repo.markRemindersSentByIds(okIdies);
            //System.out.println("Proccessed "+updated+" reminders at "+now);
            log.info("Proccessed {} reminders at {} ",updated,now);
        }

    }




}
