package com.example.reminder.dto;

import com.example.reminder.model.User;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserSummaryResponse {
    private Long id;
    private String email;
    private boolean enabled;
    private int eventCount;

    public static UserSummaryResponse fromEntity(User u) {
        return new UserSummaryResponse(
                u.getId(),
                u.getEmail(),
                u.isEnabled(),
                u.getEvents().size()
        );
    }
}
