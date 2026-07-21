package com.tongluxing.admin.sos;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
public record ResolveSosEventRequest(@NotBlank @Size(max=500) String resolutionNote) {}
