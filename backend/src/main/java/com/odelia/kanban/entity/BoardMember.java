package com.odelia.kanban.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/**
 * One shared access to a board: the user named by {@code username} may work on
 * board {@code boardId} and on everything inside it, without owning it.
 *
 * <p>The board's owner is deliberately never stored here - {@link Board#getOwner()}
 * already answers that question. Keeping it in one place is what makes
 * "is this person the owner?" impossible to answer two different ways, and it
 * is why adding or removing the owner as a member is rejected rather than
 * silently accepted.</p>
 *
 * <p>A row <em>is</em> the {@code MEMBER} access level. There is only one level
 * today, so it does not earn a column of its own; a second level (a read-only
 * viewer, say) would add one here rather than a second table.</p>
 *
 * <p>Like {@link BoardList}, the parent board is referenced by its identifier
 * rather than by a JPA {@code @ManyToOne} association: it keeps the Jakarta Data
 * repositories simple and the JSON produced by the REST layer flat.</p>
 */
@Entity
@Table(name = "BOARD_MEMBER",
       uniqueConstraints = @UniqueConstraint(name = "UQ_BOARD_MEMBER",
                                            columnNames = { "BOARD_ID", "USERNAME" }))
public class BoardMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "BOARD_ID", nullable = false)
    private long boardId;

    /** The member's {@code preferred_username}, the same name a board is owned by. */
    @NotBlank
    @Size(max = 120)
    @Column(name = "USERNAME", nullable = false, length = 120)
    private String username;

    @Column(name = "ADDED_AT")
    private LocalDateTime addedAt;

    public BoardMember() {
    }

    public BoardMember(long boardId, String username) {
        this.boardId = boardId;
        this.username = username;
        this.addedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public long getBoardId() {
        return boardId;
    }

    public void setBoardId(long boardId) {
        this.boardId = boardId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public LocalDateTime getAddedAt() {
        return addedAt;
    }

    public void setAddedAt(LocalDateTime addedAt) {
        this.addedAt = addedAt;
    }
}
