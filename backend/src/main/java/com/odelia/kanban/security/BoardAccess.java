package com.odelia.kanban.security;

import com.odelia.kanban.entity.Board;
import com.odelia.kanban.entity.BoardList;
import com.odelia.kanban.entity.Card;
import com.odelia.kanban.repository.BoardListRepository;
import com.odelia.kanban.repository.BoardMemberRepository;
import com.odelia.kanban.repository.BoardRepository;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.ForbiddenException;

/**
 * Authorization: a board belongs to whoever created it, who may also share it
 * with other people. Nobody else - bar an {@code admin} - may go near it.
 *
 * <p>{@code @RolesAllowed} alone only answers "is this a signed-in Kanban
 * user?", which is not enough: every user has the {@code user} role, so without
 * these checks anyone could read someone else's board by guessing its id. The
 * coarse role check and this fine-grained one are complementary.</p>
 *
 * <p>Sharing splits that question in two, and the split is the whole point of
 * this class:</p>
 * <ul>
 *   <li>{@link #requireBoardAccess(Board)} - may the caller work <em>on the
 *       board's contents</em>? Its owner, any of its members, or an admin. This
 *       is what the columns and the cards go through, which is why a member can
 *       create, edit, move and delete them exactly like the owner.</li>
 *   <li>{@link #requireBoardOwner(Board)} - may the caller change <em>the board
 *       itself</em>? Only its owner, or an admin. Renaming it, deleting it and
 *       managing its membership stay owner-only.</li>
 * </ul>
 *
 * <p>Both checks read the membership from the database on every request, so
 * removing a member takes effect on their very next call - there is no cached
 * decision to invalidate.</p>
 *
 * <p>Columns and cards store no owner of their own; they inherit one by walking
 * up to their board.</p>
 */
@RequestScoped
public class BoardAccess {

    @Inject
    CurrentUser currentUser;

    @Inject
    BoardRepository boards;

    @Inject
    BoardListRepository lists;

    @Inject
    BoardMemberRepository members;

    /**
     * Fails with 403 unless the caller may work on the board and on everything
     * inside it: its owner, one of its members, or an admin.
     */
    public void requireBoardAccess(Board board) {
        if (!isCollaborator(board)) {
            throw new ForbiddenException("Board " + board.getId() + " is not shared with you");
        }
    }

    /**
     * Fails with 403 unless the caller may change the board itself: its owner,
     * or an admin. A member passing through here is denied - that is what keeps
     * them from renaming the board, deleting it, or managing its members.
     */
    public void requireBoardOwner(Board board) {
        if (!isOwner(board)) {
            throw new ForbiddenException("Only the owner of board " + board.getId() + " can do that");
        }
    }

    /** The contents check, reached through the column's board. */
    public void requireColumnAccess(BoardList column) {
        requireBoardAccess(boardOf(column));
    }

    /** The same check, reached through the card's column and then its board. */
    public void requireCardAccess(Card card) {
        requireColumnAccess(columnOf(card));
    }

    /** An admin is treated as the owner of every board, exactly as before sharing existed. */
    private boolean isOwner(Board board) {
        return currentUser.isAdmin() || currentUser.name().equals(board.getOwner());
    }

    private boolean isCollaborator(Board board) {
        return isOwner(board)
                || members.findByBoardIdAndUsername(board.getId(), currentUser.name()).isPresent();
    }

    private Board boardOf(BoardList column) {
        return boards.findById(column.getBoardId())
                     .orElseThrow(() -> orphan("column " + column.getId()));
    }

    private BoardList columnOf(Card card) {
        return lists.findById(card.getListId())
                    .orElseThrow(() -> orphan("card " + card.getId()));
    }

    /** With no board to check against, nobody can be shown to own the row: deny. */
    private ForbiddenException orphan(String what) {
        return new ForbiddenException("Cannot establish who owns " + what);
    }
}
