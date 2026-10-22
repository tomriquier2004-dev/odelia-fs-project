package com.odelia.kanban.security;

import com.odelia.kanban.entity.Board;
import com.odelia.kanban.entity.BoardMember;
import com.odelia.kanban.repository.BoardMemberRepository;
import jakarta.data.Order;
import jakarta.data.page.Page;
import jakarta.data.page.PageRequest;
import jakarta.ws.rs.ForbiddenException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The two questions {@link BoardAccess} answers since a board can be shared:
 *
 * <ul>
 *   <li>{@code requireBoardAccess} - may the caller work on the board's
 *       <em>contents</em>? Its owner, any of its members, or an admin.</li>
 *   <li>{@code requireBoardOwner} - may the caller change <em>the board
 *       itself</em>? Only its owner, or an admin.</li>
 * </ul>
 *
 * <p>No CDI container runs here, so {@code BoardAccess} is built by hand and its
 * injected fields are assigned directly. Only {@code currentUser} and
 * {@code members} are needed: {@code boards} and {@code lists} are reached only
 * by the column and card variants, which walk up to the board and then ask the
 * very same methods tested below.</p>
 */
class BoardAccessTest {

    private static final String OWNER = "alice";
    private static final String MEMBER = "bob";
    private static final String STRANGER = "dave";
    private static final long BOARD_ID = 7L;

    private final Board board = board(OWNER);

    // ----------------------------------------------------- the contents check

    @Test
    void requireBoardAccessAllowsTheOwner() {
        BoardAccess access = accessFor(OWNER, false);

        assertDoesNotThrow(() -> access.requireBoardAccess(board));
    }

    /** R7: a member works on the columns and cards exactly like the owner. */
    @Test
    void requireBoardAccessAllowsAMember() {
        BoardAccess access = accessFor(MEMBER, false, MEMBER);

        assertDoesNotThrow(() -> access.requireBoardAccess(board));
    }

    /** R9: someone with no relationship to the board is still denied. */
    @Test
    void requireBoardAccessDeniesAStranger() {
        BoardAccess access = accessFor(STRANGER, false, MEMBER);

        assertThrows(ForbiddenException.class, () -> access.requireBoardAccess(board));
    }

    /** An admin keeps bypassing the check, exactly as before sharing existed. */
    @Test
    void requireBoardAccessAllowsAnAdminOnSomeoneElsesBoard() {
        BoardAccess access = accessFor("carol", true);

        assertDoesNotThrow(() -> access.requireBoardAccess(board));
    }

    // -------------------------------------------------------- the owner check

    @Test
    void requireBoardOwnerAllowsTheOwner() {
        BoardAccess access = accessFor(OWNER, false);

        assertDoesNotThrow(() -> access.requireBoardOwner(board));
    }

    /**
     * R8, and the point of the whole split: a member passes the contents check
     * but not this one, which is what stops them renaming the board, deleting it
     * or managing its members.
     */
    @Test
    void requireBoardOwnerDeniesAMember() {
        BoardAccess access = accessFor(MEMBER, false, MEMBER);

        assertDoesNotThrow(() -> access.requireBoardAccess(board));
        assertThrows(ForbiddenException.class, () -> access.requireBoardOwner(board));
    }

    @Test
    void requireBoardOwnerDeniesAStranger() {
        BoardAccess access = accessFor(STRANGER, false);

        assertThrows(ForbiddenException.class, () -> access.requireBoardOwner(board));
    }

    @Test
    void requireBoardOwnerAllowsAnAdmin() {
        BoardAccess access = accessFor("carol", true);

        assertDoesNotThrow(() -> access.requireBoardOwner(board));
    }

    // ----------------------------------------------------------------- wiring

    private static Board board(String owner) {
        Board board = new Board("Sprint 1", owner);
        board.setId(BOARD_ID);
        return board;
    }

    /** A {@link BoardAccess} as the container would have assembled it. */
    private static BoardAccess accessFor(String caller, boolean admin, String... members) {
        BoardAccess access = new BoardAccess();
        access.currentUser = new StubCurrentUser(caller, admin);
        access.members = new StubMembers(Set.of(members));
        return access;
    }

    /** {@link CurrentUser} without a JWT: the two questions it is asked, answered by hand. */
    private static final class StubCurrentUser extends CurrentUser {

        private final String name;
        private final boolean admin;

        private StubCurrentUser(String name, boolean admin) {
            this.name = name;
            this.admin = admin;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public boolean isAdmin() {
            return admin;
        }
    }

    /**
     * The membership lookup and nothing else - it is all {@link BoardAccess}
     * calls. Everything inherited from {@code CrudRepository} still has to be
     * declared, so it fails loudly instead of pretending to work.
     */
    private static final class StubMembers implements BoardMemberRepository {

        private final Set<String> usernames;

        private StubMembers(Set<String> usernames) {
            this.usernames = usernames;
        }

        @Override
        public Optional<BoardMember> findByBoardIdAndUsername(long boardId, String username) {
            return usernames.contains(username)
                    ? Optional.of(new BoardMember(boardId, username))
                    : Optional.empty();
        }

        @Override
        public List<BoardMember> findByBoardIdOrderByUsernameAsc(long boardId) {
            throw unsupported();
        }

        @Override
        public List<BoardMember> findByUsernameOrderByBoardIdAsc(String username) {
            throw unsupported();
        }

        @Override
        public void deleteByBoardId(long boardId) {
            throw unsupported();
        }

        @Override
        public <S extends BoardMember> S save(S entity) {
            throw unsupported();
        }

        @Override
        public <S extends BoardMember> List<S> saveAll(List<S> entities) {
            throw unsupported();
        }

        @Override
        public Optional<BoardMember> findById(Long id) {
            throw unsupported();
        }

        @Override
        public Stream<BoardMember> findAll() {
            throw unsupported();
        }

        @Override
        public Page<BoardMember> findAll(PageRequest pageRequest, Order<BoardMember> sortBy) {
            throw unsupported();
        }

        @Override
        public void deleteById(Long id) {
            throw unsupported();
        }

        @Override
        public void delete(BoardMember entity) {
            throw unsupported();
        }

        @Override
        public void deleteAll(List<? extends BoardMember> entities) {
            throw unsupported();
        }

        @Override
        public <S extends BoardMember> S insert(S entity) {
            throw unsupported();
        }

        @Override
        public <S extends BoardMember> List<S> insertAll(List<S> entities) {
            throw unsupported();
        }

        @Override
        public <S extends BoardMember> S update(S entity) {
            throw unsupported();
        }

        @Override
        public <S extends BoardMember> List<S> updateAll(List<S> entities) {
            throw unsupported();
        }

        private static UnsupportedOperationException unsupported() {
            return new UnsupportedOperationException("not needed by these tests");
        }
    }
}
