package com.odelia.kanban.resource;

import com.odelia.kanban.entity.Board;
import com.odelia.kanban.entity.BoardList;
import com.odelia.kanban.entity.BoardMember;
import com.odelia.kanban.repository.BoardListRepository;
import com.odelia.kanban.repository.BoardMemberRepository;
import com.odelia.kanban.repository.BoardRepository;
import com.odelia.kanban.repository.CardRepository;
import com.odelia.kanban.security.BoardAccess;
import com.odelia.kanban.security.CurrentUser;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * CRUD over boards, plus the lists (columns) they contain.
 *
 * <p>Every endpoint needs a valid Keycloak token: {@code @RolesAllowed}
 * turns an anonymous call into a 401 and a token without the {@code user} role
 * into a 403. On top of that a board belongs to the person who created it - the
 * {@code preferred_username} from the JWT - so the listing is filtered and the
 * per-board endpoints go through {@link BoardAccess}.</p>
 *
 * <p>A board can also be shared, which splits those per-board endpoints in two:
 * reading the board and working on its columns ask
 * {@link BoardAccess#requireBoardAccess(Board)}, so a member passes; renaming
 * it, deleting it and managing its members ask
 * {@link BoardAccess#requireBoardOwner(Board)}, so a member is denied.</p>
 */
@Path("/boards")
@RequestScoped
@RolesAllowed({ CurrentUser.USER, CurrentUser.ADMIN })
@SecurityRequirement(name = "bearerAuth")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class BoardResource {

    private static final List<String> DEFAULT_LISTS = List.of("To do", "Doing", "Done");

    @Inject
    CurrentUser currentUser;

    @Inject
    BoardAccess access;

    @Inject
    BoardRepository boards;

    @Inject
    BoardListRepository lists;

    @Inject
    CardRepository cards;

    @Inject
    BoardMemberRepository members;

    @Context
    UriInfo uriInfo;

    /**
     * The boards the caller owns plus the boards shared with them, by name - or
     * every board, for an admin.
     *
     * <p>The two lists cannot overlap, because the owner of a board is never one
     * of its members (see {@link #addMember}), so nothing has to be
     * de-duplicated here.</p>
     */
    @GET
    @Operation(operationId = "getBoards")
    public List<Board> listBoards() {
        if (currentUser.isAdmin()) {
            return boards.findAllSortedByName();
        }

        String caller = currentUser.name();
        List<Board> visible = new ArrayList<>(boards.findByOwnerOrderByNameAsc(caller));
        List<Long> sharedIds = members.findByUsernameOrderByBoardIdAsc(caller)
                                      .stream()
                                      .map(BoardMember::getBoardId)
                                      .toList();
        if (!sharedIds.isEmpty()) {
            visible.addAll(boards.findByIdIn(sharedIds));
        }
        visible.sort(Comparator.comparing(Board::getName));
        return visible;
    }

    @GET
    @Path("/{id}")
    public Board getBoard(@PathParam("id") long id) {
        return accessibleBoard(id);
    }

    /**
     * Creates a board and seeds it with the three usual columns, so a new board
     * is immediately usable from the UI. The owner always comes from the token:
     * an {@code owner} sent by the client is ignored.
     */
    @POST
    @Transactional
    public Response createBoard(@Valid Board board) {
        board.setId(null);
        board.setCreatedAt(LocalDateTime.now());
        board.setOwner(currentUser.name());
        Board created = boards.insert(board);

        for (int i = 0; i < DEFAULT_LISTS.size(); i++) {
            lists.insert(new BoardList(created.getId(), DEFAULT_LISTS.get(i), i));
        }

        return Response.created(uriInfo.getAbsolutePathBuilder().path(String.valueOf(created.getId())).build())
                       .entity(created)
                       .build();
    }

    /** Renaming a board is the owner's call alone: a member gets a 403 here. */
    @PUT
    @Path("/{id}")
    @Transactional
    public Board updateBoard(@PathParam("id") long id, @Valid Board board) {
        Board existing = ownedBoard(id);
        existing.setName(board.getName());
        return boards.save(existing);
    }

    /** Deleting a board is owner-only too, and takes its memberships with it. */
    @DELETE
    @Path("/{id}")
    @Transactional
    public Response deleteBoard(@PathParam("id") long id) {
        Board existing = ownedBoard(id);
        for (BoardList column : lists.findByBoardIdOrderByPositionAsc(id)) {
            cards.deleteByListId(column.getId());
        }
        lists.deleteByBoardId(id);
        members.deleteByBoardId(id);
        boards.delete(existing);
        return Response.noContent().build();
    }

    @GET
    @Path("/{id}/lists")
    public List<BoardList> listColumns(@PathParam("id") long id) {
        accessibleBoard(id);
        return lists.findByBoardIdOrderByPositionAsc(id);
    }

    @POST
    @Path("/{id}/lists")
    @Transactional
    public Response addColumn(@PathParam("id") long id, @Valid BoardList column) {
        accessibleBoard(id);
        column.setId(null);
        column.setBoardId(id);
        BoardList created = lists.insert(column);
        return Response.created(uriInfo.getBaseUriBuilder().path("lists").path(String.valueOf(created.getId())).build())
                       .entity(created)
                       .build();
    }

    /**
     * The board's members. Readable by anyone who can open the board - its
     * owner, any of its members, or an admin - so the members panel can be shown
     * to everyone who sees the board, not only to the person who owns it.
     */
    @GET
    @Path("/{id}/members")
    public List<BoardMember> listMembers(@PathParam("id") long id) {
        accessibleBoard(id);
        return members.findByBoardIdOrderByUsernameAsc(id);
    }

    /**
     * Shares the board with another user. Owner-only.
     *
     * <p>Adding the owner, or adding someone who is already a member, is a 400
     * rather than a duplicate row: the owner relationship lives in
     * {@code Board.owner} and must not be mirrored here. The username is
     * deliberately not checked against Keycloak - a board can be shared with
     * someone who has not signed in yet, and their first token will carry
     * exactly the name stored here.</p>
     */
    @POST
    @Path("/{id}/members")
    @Transactional
    public Response addMember(@PathParam("id") long id, @Valid MemberCommand command) {
        Board board = ownedBoard(id);
        String username = command.username().trim();

        if (username.equals(board.getOwner())) {
            throw badRequest(username + " already owns this board");
        }
        if (members.findByBoardIdAndUsername(id, username).isPresent()) {
            throw badRequest(username + " is already a member of this board");
        }

        BoardMember created = members.insert(new BoardMember(id, username));
        return Response.created(uriInfo.getAbsolutePathBuilder().path(username).build())
                       .entity(created)
                       .build();
    }

    /**
     * Stops sharing the board with one user. Owner-only, and effective at once:
     * {@link BoardAccess} re-reads the membership on every request, so the
     * removed member's next call is already denied.
     */
    @DELETE
    @Path("/{id}/members/{username}")
    @Transactional
    public Response removeMember(@PathParam("id") long id, @PathParam("username") String username) {
        Board board = ownedBoard(id);

        if (username.equals(board.getOwner())) {
            throw badRequest("The owner of a board is not one of its members");
        }

        BoardMember member = members.findByBoardIdAndUsername(id, username)
                                    .orElseThrow(() -> new WebApplicationException(
                                            username + " is not a member of board " + id,
                                            Response.Status.NOT_FOUND));
        members.delete(member);
        return Response.noContent().build();
    }

    /** Loads a board the caller is allowed to work on: 404 if it is gone, 403 if it is not shared with them. */
    private Board accessibleBoard(long id) {
        Board board = boards.findById(id).orElseThrow(() -> notFound(id));
        access.requireBoardAccess(board);
        return board;
    }

    /** Loads a board only its owner - or an admin - may change: 404 if it is gone, 403 otherwise. */
    private Board ownedBoard(long id) {
        Board board = boards.findById(id).orElseThrow(() -> notFound(id));
        access.requireBoardOwner(board);
        return board;
    }

    /**
     * A 400 shaped like the 403s from {@code ForbiddenExceptionMapper}, so the
     * frontend can read {@code error} out of either one and show it as it is.
     */
    private WebApplicationException badRequest(String message) {
        return new WebApplicationException(Response.status(Response.Status.BAD_REQUEST)
                                                   .type(MediaType.APPLICATION_JSON)
                                                   .entity(Map.of("error", message))
                                                   .build());
    }

    private WebApplicationException notFound(long id) {
        return new WebApplicationException("No board with id " + id, Response.Status.NOT_FOUND);
    }
}
