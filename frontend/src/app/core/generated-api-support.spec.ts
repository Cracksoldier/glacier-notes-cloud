import { OpenApiHttpParams } from '../shared/generated-api/query.params';
import { COLLECTION_FORMATS } from '../shared/generated-api/variables';

describe('generated Angular support contracts', () => {
  it('serializes exploded arrays as repeated query parameters', () => {
    const parameters = new OpenApiHttpParams().set('label', ['first value', 'second'], {
      explode: true,
    });

    expect(parameters.toString()).toBe('label=first%20value&label=second');
  });

  it('represents a single exploded value as a scalar record entry', () => {
    const parameters = new OpenApiHttpParams().set('label', 'only value', { explode: true });

    expect(parameters.toRecord()).toEqual({ label: 'only%20value' });
  });

  it('uses a real tab for TSV collection parameters', () => {
    expect(COLLECTION_FORMATS.tsv).toBe('\t');
  });
});
