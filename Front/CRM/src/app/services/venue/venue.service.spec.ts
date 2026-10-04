import { TestBed } from '@angular/core/testing';

import { provideTestDependencies } from '../../testing/test-dependencies';
import { VenueService } from './venue.service';

describe('VenueService', () => {
  let service: VenueService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideTestDependencies()],
    });
    service = TestBed.inject(VenueService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });
});