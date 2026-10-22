package com.odelia.kanban.resource;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code POST /api/boards/{id}/members}: who to share the board with.
 *
 * @param username the {@code preferred_username} of the user to add, matched
 *                 as-is against the name their own token carries. A blank one
 *                 is rejected by Bean Validation before the resource method
 *                 runs.
 */
public record MemberCommand(@NotBlank @Size(max = 120) String username) {
}
