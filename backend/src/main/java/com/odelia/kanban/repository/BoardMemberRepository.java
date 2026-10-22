package com.odelia.kanban.repository;

import com.odelia.kanban.entity.BoardMember;
import jakarta.data.repository.CrudRepository;
import jakarta.data.repository.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Jakarta Data repository for {@link BoardMember}, the boards shared with a user.
 *
 * <p>Every method here is a derived query - the name alone says what it does,
 * the runtime writes the JPQL - which is the same approach as the other
 * repositories of this project.</p>
 */
@Repository(dataStore = "jdbc/kanban")
public interface BoardMemberRepository extends CrudRepository<BoardMember, Long> {

    /** The members of one board, for {@code GET /api/boards/{id}/members}. */
    List<BoardMember> findByBoardIdOrderByUsernameAsc(long boardId);

    /** The boards shared with one user, for {@code GET /api/boards}. */
    List<BoardMember> findByUsernameOrderByBoardIdAsc(String username);

    /**
     * One membership, which answers both "is this person a member?" (the
     * authorization check) and "which row should be deleted?" (the removal).
     * Empty when the user is not a member of that board.
     */
    Optional<BoardMember> findByBoardIdAndUsername(long boardId, String username);

    /** Used when a board is deleted, so no membership outlives its board. */
    void deleteByBoardId(long boardId);
}
