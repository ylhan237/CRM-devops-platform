import { Candidate } from './candidate';

/**
 * Candidate is an interface, so it has no runtime representation and cannot be
 * instantiated. The generated spec called `new Candidate()`, which is a
 * TypeScript error (TS2693) and broke compilation of the whole suite before a
 * single spec could run.
 *
 * An interface is erased at compile time, so there is nothing to assert at
 * runtime. What is worth checking is that the shape the components rely on
 * still type-checks, which is what the assignments below do: if a field is
 * renamed or removed from the interface, this file stops compiling.
 */
describe('Candidate', () => {
  it('describes a candidate as the components expect', () => {
    const candidate: Candidate = {
      id: 1,
      firstName: 'Ada',
      lastName: 'Lovelace',
      email: 'ada@example.com',
      phoneNumber: '+33600000000',
      applicationDate: '2026-10-03T10:00:00',
      applicationSource: 'website',
      status: 'NEW',
    };

    expect(candidate.id).toBe(1);
    expect(candidate.firstName).toBe('Ada');
    expect(candidate.imagePath).toBeUndefined();
  });

  it('treats imagePath as optional', () => {
    const withoutPhoto: Candidate = {
      id: 2,
      firstName: 'Grace',
      lastName: 'Hopper',
      email: 'grace@example.com',
      phoneNumber: '+33600000001',
      applicationDate: '2026-10-03T11:00:00',
      applicationSource: 'referral',
      status: 'QUALIFIED',
    };

    expect(Object.prototype.hasOwnProperty.call(withoutPhoto, 'imagePath')).toBeFalse();
  });
});