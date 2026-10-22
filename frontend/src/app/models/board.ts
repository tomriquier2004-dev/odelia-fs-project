/** Mirrors com.odelia.kanban.entity.Board on the backend. */
export interface Board {
  id?: number;
  name: string;
  owner?: string;
  createdAt?: string;
}

/**
 * Mirrors com.odelia.kanban.entity.BoardMember: one user the board is shared
 * with. The owner is never in this list — `Board.owner` says who that is.
 */
export interface BoardMember {
  id?: number;
  boardId: number;
  username: string;
  addedAt?: string;
}

/** Mirrors com.odelia.kanban.entity.BoardList: a column of a board. */
export interface BoardList {
  id?: number;
  boardId: number;
  name: string;
  position: number;
}

/** Mirrors com.odelia.kanban.entity.Card: a card in a column. */
export interface Card {
  id?: number;
  listId: number;
  title: string;
  description?: string;
  position: number;
  createdAt?: string;
}
