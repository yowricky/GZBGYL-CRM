package com.gzbgyl.crm.identity.web;

import com.gzbgyl.crm.identity.application.UserSummary;
import java.util.List;
import org.springframework.data.domain.Page;

public record UserPageResponse(List<UserSummary> content, int page, int size,
        long totalElements, int totalPages) {
    static UserPageResponse from(Page<UserSummary> result) {
        return new UserPageResponse(result.getContent(), result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages());
    }
}
