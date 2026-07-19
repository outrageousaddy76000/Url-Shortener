package com.adarsh.urlshortener.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShortenRequest {

    @NotBlank(message = "URL cannot be empty")
    @Pattern(regexp="https?://.+",message="Invalid URL")
    private String url;

    @Pattern(regexp = "^$|^[A-Za-z0-9_-]{3,30}$", message = "Alias must be alphanumeric, between 3 and 30 characters, and can only contain underscores or hyphens")
    private String alias;

}
