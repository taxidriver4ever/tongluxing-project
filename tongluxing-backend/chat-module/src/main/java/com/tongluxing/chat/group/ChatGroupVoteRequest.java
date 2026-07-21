package com.tongluxing.chat.group;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
public record ChatGroupVoteRequest(@NotBlank @Size(max=64) String optionKey){}
