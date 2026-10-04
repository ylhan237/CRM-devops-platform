import { FieldFilterPipe } from './field-filter.pipe';

describe('FieldFilterPipe', () => {
  it('create an instance', () => {
    const pipe = new FieldFilterPipe();
    expect(pipe).toBeTruthy();
  });

  it('sorts a copy without mutating the input array', () => {
    const pipe = new FieldFilterPipe();
    const users = [{ fullName: 'Zoë' }, { fullName: 'Ada' }];

    const result = pipe.transform(users);

    expect(result.map((user) => user.fullName)).toEqual(['Ada', 'Zoë']);
    expect(users.map((user) => user.fullName)).toEqual(['Zoë', 'Ada']);
  });
});
