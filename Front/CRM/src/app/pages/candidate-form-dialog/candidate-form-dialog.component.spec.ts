import { ComponentFixture, TestBed } from '@angular/core/testing';

import { provideTestDependencies } from '../../testing/test-dependencies';
import { CandidateFormDialogComponent } from './candidate-form-dialog.component';

describe('CandidateFormDialogComponent', () => {
  let component: CandidateFormDialogComponent;
  let fixture: ComponentFixture<CandidateFormDialogComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [CandidateFormDialogComponent],
      // The dialog injects MatDialogRef and MAT_DIALOG_DATA in its constructor.
      providers: [provideTestDependencies()],
    }).compileComponents();

    fixture = TestBed.createComponent(CandidateFormDialogComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});