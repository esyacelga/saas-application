package com.gymadmin.auth.dto.response;

import java.util.List;

public record PersonaPageResponse(
        List<PersonaResponse> content,
        long totalElements,
        int totalPages,
        int number,
        int size
) {}
