import { afterAll, beforeAll, beforeEach, describe, expect, it } from 'vitest';
import { createLiveConnection } from '../utils/fixtures.js';
import { executeAsync } from '../utils/index.js';
import {
  jsonResponse,
  loginSuccess,
  logoutSuccess,
  type StubMapping,
  WiremockServer,
} from '../utils/wiremock/index.js';

const QUERY_REQUEST_PATH = '/queries/v1/query-request.*';
const INSERT_SQL = 'INSERT INTO t (id) VALUES (?)';
const CREATE_STAGE_FRAGMENT = 'TEMPORARY STAGE';

function createFailingQueryRequest(
  sqlFragment: string,
  code: string,
  message: string,
): StubMapping {
  return {
    priority: 1,
    request: {
      method: 'POST',
      urlPathPattern: QUERY_REQUEST_PATH,
      bodyPatterns: [{ contains: sqlFragment }],
    },
    response: jsonResponse(200, { success: false, code, message }),
  };
}

describe('Stage binding fallback', () => {
  let wiremock: WiremockServer;

  async function insertAboveThreshold(): Promise<Record<string, unknown>[]> {
    await wiremock.stub([loginSuccess(), logoutSuccess()]);
    await wiremock.stubFromFile('query/insert_success_after_stage_binding_retry.json');
    const connection = await createLiveConnection({
      ...wiremock.connectionOptions,
      arrayBindingThreshold: 1,
    });

    const { statement } = await executeAsync(connection, INSERT_SQL, { binds: [[1], [2]] });

    expect(statement.getNumUpdatedRows()).toBe(1);
    return (await wiremock.findRequests(QUERY_REQUEST_PATH)).map(
      (request) => JSON.parse(request.body) as Record<string, unknown>,
    );
  }

  function expectInlineInsert(request: Record<string, unknown> | undefined) {
    expect(request).toEqual(
      expect.objectContaining({
        sqlText: INSERT_SQL,
        bindings: { '1': { type: 'FIXED', value: ['1', '2'] } },
      }),
    );
    expect(request).not.toHaveProperty('bindStage');
  }

  beforeAll(async () => {
    wiremock = await WiremockServer.spawn();
  });

  beforeEach(async () => {
    await wiremock.reset();
  });

  afterAll(async () => {
    await wiremock.destroy();
  });

  it('should resend the binds inline when the bind stage cannot be created', async () => {
    await wiremock.stub(
      createFailingQueryRequest(
        CREATE_STAGE_FRAGMENT,
        '002043',
        'SQL compilation error: Cannot perform CREATE STAGE. This session does not have a current database.',
      ),
    );

    const queryRequests = await insertAboveThreshold();

    expect(queryRequests.map((body) => body.sqlText)).toEqual([
      expect.stringContaining(CREATE_STAGE_FRAGMENT),
      INSERT_SQL,
    ]);
    expectInlineInsert(queryRequests.at(-1));
  });

  it('should resend the binds inline when the upload to the bind stage fails', async () => {
    await wiremock.stub(createFailingQueryRequest('PUT ', '253006', 'File upload failed.'));

    const queryRequests = await insertAboveThreshold();

    expect(queryRequests.map((body) => body.sqlText)).toEqual([
      expect.stringContaining(CREATE_STAGE_FRAGMENT),
      expect.stringMatching(/^PUT /),
      INSERT_SQL,
    ]);
    expectInlineInsert(queryRequests.at(-1));
  });
});
