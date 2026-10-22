import { TestBed } from '@angular/core/testing';
import { WritableSignal, signal } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ActivatedRoute } from '@angular/router';

import { AuthService } from '../auth/auth.service';
import { BoardDetailComponent } from './board-detail.component';

describe('BoardDetailComponent', () => {
  let http: HttpTestingController;
  /** Stands in for AuthService so no Keycloak instance is created in a test. */
  let auth: { username: WritableSignal<string | null>; isAdmin: WritableSignal<boolean> };

  beforeEach(async () => {
    auth = { username: signal<string | null>('alice'), isAdmin: signal(false) };
    await TestBed.configureTestingModule({
      imports: [BoardDetailComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: new Map([['id', '7']]) } } },
        { provide: AuthService, useValue: auth }
      ]
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  /** Answers the three requests reload() fires, with the board owned by `owner`. */
  function flushBoard(owner = 'alice', members: unknown[] = []): void {
    http.expectOne('/api/boards/7').flush({ id: 7, name: 'Sprint 1', owner });
    http.expectOne('/api/boards/7/members').flush(members);
  }

  it('loads the board, its members and its columns on init', () => {
    const fixture = TestBed.createComponent(BoardDetailComponent);
    fixture.detectChanges();

    flushBoard('alice', [{ id: 1, boardId: 7, username: 'bob' }]);

    const listsReq = http.expectOne('/api/boards/7/lists');
    listsReq.flush([{ id: 1, boardId: 7, name: 'To do', position: 0 }]);

    http.expectOne('/api/lists/1/cards').flush([
      { id: 10, listId: 1, title: 'First card', position: 0 }
    ]);

    expect(fixture.componentInstance.board()?.name).toBe('Sprint 1');
    expect(fixture.componentInstance.members().map(m => m.username)).toEqual(['bob']);
    expect(fixture.componentInstance.cardsFor(1).length).toBe(1);
  });

  it('PATCHes the card when it is dropped into another column', () => {
    const fixture = TestBed.createComponent(BoardDetailComponent);
    const component = fixture.componentInstance;
    fixture.detectChanges();

    flushBoard();
    http.expectOne('/api/boards/7/lists').flush([
      { id: 1, boardId: 7, name: 'To do', position: 0 },
      { id: 2, boardId: 7, name: 'Doing', position: 1 }
    ]);
    http.expectOne('/api/lists/1/cards').flush([{ id: 10, listId: 1, title: 'Card', position: 0 }]);
    http.expectOne('/api/lists/2/cards').flush([]);

    const card = component.cardsFor(1)[0];
    component.drop({
      item: { data: card },
      container: { data: { id: 2 } },
      previousContainer: { data: { id: 1 } },
      previousIndex: 0,
      currentIndex: 0
    } as never);

    const move = http.expectOne('/api/cards/10/move');
    expect(move.request.method).toBe('PATCH');
    expect(move.request.body).toEqual({ targetListId: 2, position: 0 });
    move.flush({ id: 10, listId: 2, title: 'Card', position: 0 });

    expect(component.cardsFor(1).length).toBe(0);
    expect(component.cardsFor(2).length).toBe(1);
  });

  it('PUTs the card and updates it in place when the inline editor is saved', () => {
    const fixture = TestBed.createComponent(BoardDetailComponent);
    const component = fixture.componentInstance;
    fixture.detectChanges();

    flushBoard();
    http.expectOne('/api/boards/7/lists').flush([
      { id: 1, boardId: 7, name: 'To do', position: 0 }
    ]);
    http.expectOne('/api/lists/1/cards').flush([
      { id: 10, listId: 1, title: 'Card', position: 0 }
    ]);

    const card = component.cardsFor(1)[0];
    component.startEdit(card);
    expect(component.editingCardId()).toBe(10);

    component.editDraft.title = 'Card renamed';
    component.editDraft.description = 'Now with details';
    component.saveEdit(card);

    const put = http.expectOne('/api/cards/10');
    expect(put.request.method).toBe('PUT');
    expect(put.request.body).toEqual({ title: 'Card renamed', description: 'Now with details' });
    put.flush({ id: 10, listId: 1, title: 'Card renamed', description: 'Now with details', position: 0 });

    expect(component.editingCardId()).toBeNull();
    expect(component.cardsFor(1)[0].description).toBe('Now with details');
  });

  it('opens and closes the "add a card" composer one column at a time', () => {
    const fixture = TestBed.createComponent(BoardDetailComponent);
    const component = fixture.componentInstance;
    fixture.detectChanges();

    flushBoard();
    http.expectOne('/api/boards/7/lists').flush([
      { id: 1, boardId: 7, name: 'To do', position: 0 }
    ]);
    http.expectOne('/api/lists/1/cards').flush([]);

    expect(component.composingColumnId()).toBeNull();

    component.startComposing(1);
    expect(component.composingColumnId()).toBe(1);

    component.cancelComposing();
    expect(component.composingColumnId()).toBeNull();
  });

  it('POSTs title and description from the "add a card" form', () => {
    const fixture = TestBed.createComponent(BoardDetailComponent);
    const component = fixture.componentInstance;
    fixture.detectChanges();

    flushBoard();
    http.expectOne('/api/boards/7/lists').flush([
      { id: 1, boardId: 7, name: 'To do', position: 0 }
    ]);
    http.expectOne('/api/lists/1/cards').flush([]);

    const column = component.columns()[0];
    const draft = component.draftFor(1);
    draft.title = 'New card';
    draft.description = 'With a description';
    component.addCard(column);

    const post = http.expectOne('/api/lists/1/cards');
    expect(post.request.method).toBe('POST');
    expect(post.request.body).toEqual({ title: 'New card', description: 'With a description' });
    post.flush({ id: 20, listId: 1, title: 'New card', description: 'With a description', position: 0 });

    expect(component.cardsFor(1).length).toBe(1);
    expect(draft.title).toBe('');
    expect(draft.description).toBe('');
  });

  /** Creates the component on a board owned by `owner`, with one member. */
  function createOnBoardOwnedBy(owner: string) {
    const fixture = TestBed.createComponent(BoardDetailComponent);
    fixture.detectChanges();
    flushBoard(owner, [{ id: 1, boardId: 7, username: 'bob' }]);
    http.expectOne('/api/boards/7/lists').flush([]);
    fixture.detectChanges();
    return fixture;
  }

  it('shows the members panel with its controls to the owner', () => {
    const fixture = createOnBoardOwnedBy('alice');
    const element: HTMLElement = fixture.nativeElement;

    expect(fixture.componentInstance.isOwner()).toBeTrue();
    expect(element.querySelectorAll('.member').length).toBe(1);
    expect(element.querySelectorAll('.member .remove').length).toBe(1);
    expect(element.querySelector('form.add-member')).not.toBeNull();
  });

  it('shows the members panel without its controls to a member', () => {
    const fixture = createOnBoardOwnedBy('bob');
    const element: HTMLElement = fixture.nativeElement;

    expect(fixture.componentInstance.isOwner()).toBeFalse();
    expect(element.querySelectorAll('.member').length).toBe(1);
    expect(element.querySelectorAll('.member .remove').length).toBe(0);
    expect(element.querySelector('form.add-member')).toBeNull();
  });

  it('lets an admin manage the membership of a board they do not own', () => {
    auth.isAdmin.set(true);
    const fixture = createOnBoardOwnedBy('bob');

    expect(fixture.componentInstance.isOwner()).toBeTrue();
    expect(fixture.nativeElement.querySelector('form.add-member')).not.toBeNull();
  });

  it('POSTs the trimmed username and inserts the new member in order', () => {
    const component = createOnBoardOwnedBy('alice').componentInstance;

    component.newMemberName = '  carol  ';
    component.addMember();

    const post = http.expectOne('/api/boards/7/members');
    expect(post.request.method).toBe('POST');
    expect(post.request.body).toEqual({ username: 'carol' });
    post.flush({ id: 2, boardId: 7, username: 'carol' });

    expect(component.members().map(m => m.username)).toEqual(['bob', 'carol']);
    expect(component.newMemberName).toBe('');
    expect(component.addingMember()).toBeFalse();
  });

  it('surfaces the backend message when the add is rejected', () => {
    const component = createOnBoardOwnedBy('alice').componentInstance;

    component.newMemberName = 'bob';
    component.addMember();

    http.expectOne('/api/boards/7/members').flush(
      { error: 'bob is already a member of this board' },
      { status: 400, statusText: 'Bad Request' }
    );

    expect(component.error()).toBe('bob is already a member of this board');
    expect(component.members().map(m => m.username)).toEqual(['bob']);
    expect(component.addingMember()).toBeFalse();
  });

  it('DELETEs the member and drops it from the panel', () => {
    spyOn(window, 'confirm').and.returnValue(true);
    const component = createOnBoardOwnedBy('alice').componentInstance;

    component.removeMember(component.members()[0]);

    const deleted = http.expectOne('/api/boards/7/members/bob');
    expect(deleted.request.method).toBe('DELETE');
    deleted.flush(null, { status: 204, statusText: 'No Content' });

    expect(component.members().length).toBe(0);
  });

  it('sends nothing for a blank username', () => {
    const component = createOnBoardOwnedBy('alice').componentInstance;

    component.newMemberName = '   ';
    component.addMember();

    http.expectNone('/api/boards/7/members');
  });
});
