import { TestBed } from '@angular/core/testing';
import { WritableSignal, signal } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';

import { AuthService } from '../auth/auth.service';
import { BoardListComponent } from './board-list.component';
import { routes } from '../app.routes';

describe('BoardListComponent', () => {
  let http: HttpTestingController;
  /** Stands in for AuthService so no Keycloak instance is created in a test. */
  let auth: { username: WritableSignal<string | null>; isAdmin: WritableSignal<boolean> };

  beforeEach(async () => {
    auth = { username: signal<string | null>('alice'), isAdmin: signal(false) };
    await TestBed.configureTestingModule({
      imports: [BoardListComponent],
      providers: [
        provideRouter(routes),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: AuthService, useValue: auth }
      ]
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  /** Creates the component and answers its initial GET with two unsorted own boards. */
  function createWithBoards(): BoardListComponent {
    const fixture = TestBed.createComponent(BoardListComponent);
    fixture.detectChanges();
    http.expectOne('/api/boards').flush([
      { id: 1, name: 'Zebra', owner: 'alice' },
      { id: 2, name: 'Apple', owner: 'alice' }
    ]);
    return fixture.componentInstance;
  }

  /** Creates the component with one own board and one board shared by bob. */
  function createWithOwnAndSharedBoards() {
    const fixture = TestBed.createComponent(BoardListComponent);
    fixture.detectChanges();
    http.expectOne('/api/boards').flush([
      { id: 1, name: 'Mine', owner: 'alice' },
      { id: 2, name: 'Shared with me', owner: 'bob' }
    ]);
    fixture.detectChanges();
    return fixture;
  }

  it('loads the boards on init', () => {
    const component = createWithBoards();

    expect(component.boards().map(b => b.name)).toEqual(['Zebra', 'Apple']);
    expect(component.loading()).toBeFalse();
    expect(component.editingBoardId()).toBeNull();
  });

  it('PUTs the trimmed name and re-sorts the list', () => {
    const component = createWithBoards();
    const board = component.boards().find(b => b.id === 1)!;

    component.startRename(board);
    expect(component.editingBoardId()).toBe(1);
    expect(component.renameDraft).toBe('Zebra');

    component.renameDraft = '  Aardvark  ';
    component.saveRename(board);

    const put = http.expectOne('/api/boards/1');
    expect(put.request.method).toBe('PUT');
    expect(put.request.body).toEqual({ name: 'Aardvark' });
    put.flush({ id: 1, name: 'Aardvark', owner: 'alice' });

    expect(component.boards().map(b => b.name)).toEqual(['Aardvark', 'Apple']);
    expect(component.editingBoardId()).toBeNull();
    expect(component.renaming()).toBeFalse();
  });

  it('does not send anything for a blank name', () => {
    const component = createWithBoards();
    const board = component.boards()[0];

    component.startRename(board);
    component.renameDraft = '   ';
    component.saveRename(board);

    http.expectNone('/api/boards/1');
    expect(component.editingBoardId()).toBe(1);
  });

  it('cancel closes the editor and leaves the board untouched', () => {
    const component = createWithBoards();
    const board = component.boards()[0];

    component.startRename(board);
    component.renameDraft = 'Something else';
    component.cancelRename();

    http.expectNone('/api/boards/1');
    expect(component.editingBoardId()).toBeNull();
    expect(component.boards().map(b => b.name)).toEqual(['Zebra', 'Apple']);
  });

  it('keeps the editor open and reports the error when the PUT fails', () => {
    const component = createWithBoards();
    const board = component.boards()[0];

    component.startRename(board);
    component.renameDraft = 'Aardvark';
    component.saveRename(board);

    http.expectOne('/api/boards/1').flush('boom', { status: 500, statusText: 'Server Error' });

    expect(component.error()).toContain('Zebra');
    expect(component.editingBoardId()).toBe(1);
    expect(component.renameDraft).toBe('Aardvark');
    expect(component.renaming()).toBeFalse();
    expect(component.boards().map(b => b.name)).toEqual(['Zebra', 'Apple']);
  });

  it('renders the editor for the active board only', () => {
    const fixture = TestBed.createComponent(BoardListComponent);
    const component = fixture.componentInstance;
    fixture.detectChanges();
    http.expectOne('/api/boards').flush([
      { id: 1, name: 'Zebra', owner: 'alice' },
      { id: 2, name: 'Apple', owner: 'alice' }
    ]);
    fixture.detectChanges();

    const element: HTMLElement = fixture.nativeElement;
    expect(element.querySelectorAll('form.rename').length).toBe(0);

    component.startRename(component.boards()[0]);
    fixture.detectChanges();

    expect(element.querySelectorAll('form.rename').length).toBe(1);
    expect(element.querySelectorAll('a.board-name').length).toBe(1);
  });

  it('lists the boards shared with the user, told apart from their own', () => {
    const element: HTMLElement = createWithOwnAndSharedBoards().nativeElement;
    const tags = Array.from(element.querySelectorAll('.tag')).map(tag => tag.textContent?.trim());

    expect(tags).toEqual(['Yours', 'Shared by bob']);
  });

  it('offers Rename and Delete on the user own boards only', () => {
    const fixture = createWithOwnAndSharedBoards();
    const element: HTMLElement = fixture.nativeElement;
    const rows = element.querySelectorAll('.board-row');

    expect(fixture.componentInstance.canManage(fixture.componentInstance.boards()[1])).toBeFalse();
    expect(rows[0].querySelectorAll('button.danger').length).toBe(1);
    expect(rows[1].querySelectorAll('button.danger').length).toBe(0);
    expect(rows[1].querySelectorAll('button.ghost').length).toBe(0);
  });

  it('lets an admin manage every board', () => {
    auth.isAdmin.set(true);
    const element: HTMLElement = createWithOwnAndSharedBoards().nativeElement;

    expect(element.querySelectorAll('button.danger').length).toBe(2);
  });
});
