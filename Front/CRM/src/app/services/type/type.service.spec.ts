import { TestBed } from '@angular/core/testing';

import { provideTestDependencies } from '../../testing/test-dependencies';
import { TypeService } from './type.service';

describe('TypeService', () => {
  let service: TypeService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideTestDependencies()],
    });
    service = TestBed.inject(TypeService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });
});