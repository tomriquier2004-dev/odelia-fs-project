import { Component, ElementRef, OnInit, inject, signal, viewChildren } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';

import { AuthService } from '../auth/auth.service';
import { Board } from '../models/board';
import { BoardService } from '../services/board.service';

/**
 * Lists the boards, creates one, renames one inline, deletes one. Each board
 * name links to the drag-and-drop board detail view ({@code /boards/:id}).
 *
 * The listing holds both the boards the user owns and the boards shared with
 * them, so each row says which it is, and renaming and deleting - which the
 * backend reserves for the owner - are only offered on the user's own boards.
 */
@Component({
  selector: 'app-board-list',
  imports: [FormsModule, DatePipe, RouterLink],
  templateUrl: './board-list.component.html',
  styleUrl: './board-list.component.css'
})
export class BoardListComponent implements OnInit {
  private readonly boardService = inject(BoardService);
  private readonly auth = inject(AuthService);

  readonly boards = signal<Board[]>([]);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);

  newBoardName = '';

  /** Id of the board whose inline rename editor is open, or null when none is. */
  readonly editingBoardId = signal<number | null>(null);
  /** Working copy of the name while the editor is open; the board itself is untouched until Save. */
  renameDraft = '';
  /** True while a rename request is in flight, so Save cannot be sent twice. */
  readonly renaming = signal(false);
  /** The rename input (only one is in the DOM at a time), used to focus it when the editor opens. */
  private readonly renameInputs = viewChildren<ElementRef<HTMLInputElement>>('renameInput');

  ngOnInit(): void {
    this.reload();
  }

  reload(): void {
    this.loading.set(true);
    this.error.set(null);
    this.boardService.getBoards().subscribe({
      next: boards => {
        this.boards.set(boards);
        this.loading.set(false);
      },
      error: err => this.fail('Could not load the boards', err)
    });
  }

  /** True when the signed-in user owns this board, as opposed to being a member of it. */
  owns(board: Board): boolean {
    return board.owner === this.auth.username();
  }

  /**
   * True when the signed-in user may rename or delete this board: its owner, or
   * an admin. A member is shown neither control - the backend answers 403 to
   * both anyway, this only keeps the UI honest about it.
   */
  canManage(board: Board): boolean {
    return this.owns(board) || this.auth.isAdmin();
  }

  createBoard(): void {
    const name = this.newBoardName.trim();
    if (!name) {
      return;
    }
    this.error.set(null);
    this.boardService.createBoard(name).subscribe({
      next: board => {
        this.boards.update(boards => [...boards, board].sort((a, b) => a.name.localeCompare(b.name)));
        this.newBoardName = '';
      },
      error: err => this.fail(`Could not create the board "${name}"`, err)
    });
  }

  /** Opens the inline editor for one board, pre-filled with its current name. */
  startRename(board: Board): void {
    if (board.id === undefined) {
      return;
    }
    this.error.set(null);
    this.renameDraft = board.name;
    this.editingBoardId.set(board.id);
    setTimeout(() => this.renameInputs()[0]?.nativeElement.focus());
  }

  /** Closes the editor and drops the draft; the board keeps its current name. */
  cancelRename(): void {
    this.editingBoardId.set(null);
    this.renameDraft = '';
  }

  /**
   * Sends the trimmed draft as {@code PUT /api/boards/{id}} and swaps the
   * returned board into the list, re-sorted, without reloading everything.
   * On failure the editor stays open with the draft so the user can retry.
   */
  saveRename(board: Board): void {
    const name = this.renameDraft.trim();
    if (!name || board.id === undefined || this.renaming()) {
      return;
    }
    this.error.set(null);
    this.renaming.set(true);
    this.boardService.renameBoard(board.id, name).subscribe({
      next: updated => {
        this.boards.update(list =>
          list.map(b => (b.id === board.id ? updated : b)).sort((a, b) => a.name.localeCompare(b.name))
        );
        this.renaming.set(false);
        this.cancelRename();
      },
      error: err => {
        this.renaming.set(false);
        this.fail(`Could not rename the board "${board.name}"`, err);
      }
    });
  }

  deleteBoard(board: Board): void {
    if (board.id === undefined) {
      return;
    }
    if (!confirm(`Delete board "${board.name}"? This cannot be undone.`)) {
      return;
    }
    this.error.set(null);
    this.boardService.deleteBoard(board.id).subscribe({
      next: () => this.boards.update(boards => boards.filter(b => b.id !== board.id)),
      error: err => this.fail(`Could not delete the board "${board.name}"`, err)
    });
  }

  private fail(message: string, err: unknown): void {
    console.error(message, err);
    this.error.set(message);
    this.loading.set(false);
  }
}
