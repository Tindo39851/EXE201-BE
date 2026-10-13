package com.gametrust.backend.dto.ai;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public record AiChatRequest(
        @NotEmpty(message = "At least one message is required")
        @Size(max = 20, message = "A chat request can contain at most 20 messages")
        List<@Valid Message> messages
) {
    public record Message(
            @NotBlank(message = "Message role is required")
            @Pattern(regexp = "user|assistant", message = "Message role must be user or assistant")
            String role,

            @NotBlank(message = "Message content is required")
            @Size(max = 4000, message = "Each message can contain at most 4000 characters")
            String content
    ) {
    }
}
