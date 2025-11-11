package com.example.reminder.dto;

import com.example.reminder.model.Event;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AdminEventResponse {
    private Long id;
    private String title;
    private String description;
    private LocalDate eventDate;
    private LocalDateTime reminderTime;
    private boolean reminderSent;
    private LocalDateTime reminderSentTime;
    private String userEmail;

    public static AdminEventResponse fromEntity(Event e) {
        return new AdminEventResponse(
                e.getId(),
                e.getTitle(),
                e.getDescription(),
                e.getEventDate(),
                e.getReminderTime(),
                e.isReminderSent(),
                e.getReminderSentTime(),
                e.getUser().getEmail()
        );
    }

}