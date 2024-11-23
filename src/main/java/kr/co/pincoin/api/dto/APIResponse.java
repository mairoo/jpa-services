package kr.co.pincoin.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class APIResponse {
    private String referenceId;

    private String status;

    private LocalDateTime timestamp;
}