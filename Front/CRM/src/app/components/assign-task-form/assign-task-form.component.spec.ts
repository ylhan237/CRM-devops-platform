import { ComponentFixture, TestBed } from '@angular/core/testing';

import { provideTestDependencies } from '../../testing/test-dependencies';
import { AssignTaskFormComponent } from './assign-task-form.component';

describe('AssignTaskFormComponent', () => {
  let component: AssignTaskFormComponent;
  let fixture: ComponentFixture<AssignTaskFormComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AssignTaskFormComponent],
      providers: [provideTestDependencies()],
    }).compileComponents();

    fixture = TestBed.createComponent(AssignTaskFormComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});