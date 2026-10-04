import { CandidateFilterPipe } from './candidate-filter.pipe';

describe('CandidateFilterPipe', () => {
  it('create an instance', () => {
    const pipe = new CandidateFilterPipe();
    expect(pipe).toBeTruthy();
  });

  it('sorts a copy without mutating the input array', () => {
    const pipe = new CandidateFilterPipe();
    const candidates = [{ firstName: 'Zoë' }, { firstName: 'Ada' }];

    const result = pipe.transform(candidates);

    expect(result.map((candidate) => candidate.firstName)).toEqual(['Ada', 'Zoë']);
    expect(candidates.map((candidate) => candidate.firstName)).toEqual(['Zoë', 'Ada']);
  });
});
