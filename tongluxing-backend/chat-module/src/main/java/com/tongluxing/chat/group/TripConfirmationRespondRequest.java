package com.tongluxing.chat.group;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotBlank;
public record TripConfirmationRespondRequest(
 @NotBlank @Pattern(regexp="CONFIRMED|REJECTED") String status,
 @Size(max=300) String reason) {}
