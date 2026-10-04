import { TestBed } from '@angular/core/testing';

import { provideTestDependencies } from '../../testing/test-dependencies';
import { CandidateServiceService } from './candidate-service.service';

describe('CandidateServiceService', () => {
  let service: CandidateServiceService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideTestDependencies()],
    });
    service = TestBed.inject(CandidateServiceService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });
});