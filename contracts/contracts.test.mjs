import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';
import test from 'node:test';
import { fileURLToPath } from 'node:url';

const root = path.dirname(fileURLToPath(import.meta.url));
const repositoryRoot = path.resolve(root, '..');
const schemaRoot = path.join(root, 'schemas');
const schemaFiles = [
  'health.v1.json',
  'http-errors.v1.json',
  'account-summary.v1.json',
  'account-export.v1.json',
  'account-export-job.v2.json',
  'account-export.v2.json',
  'sync-command.v1.json',
  'sync-push-request.v1.json',
  'sync-push-result.v1.json',
  'sync-pull.v1.json',
  'analytics-event.v1.json',
  'analytics-event-result.v1.json',
  'billing-webhook-result.v1.json',
  'progress-summary.v1.json',
  'progress-daily.v1.json',
  'case-summary.v1.json',
  'case-catalogue.v1.json',
  'evidence-graph.v1.json',
  'recommendation.v1.json',
  'cohort-summary.v1.json',
  'ai-assist-result.v1.json',
  'ai-assist-result.v2.json',
  'ai-conversation-session.v1.json',
  'ai-conversation-turn.v1.json',
  'ai-conversation-turn.v2.json',
  'ai-conversation-clear.v1.json',
  'case-authoring-result.v1.json',
  'case-lifecycle-audit.v1.json',
  'recommendation-interaction-result.v1.json',
  'entitlements.v1.json',
  'account-deletion-result.v1.json',
  'membership-operation-result.v1.json',
    'notification-preferences.v1.json',
    'notification-preferences-update.v1.json',
    'notification-preferences-update-result.v1.json',
  'student-project-mutation-result.v1.json',
  'student-project-cloud-consent.v1.json',
  'student-project-cloud-consent-update.v1.json',
  'student-project-list.v1.json',
  'student-project.v1.json',
  'student-project-revisions.v1.json',
  'student-project-structure-report.v1.json',
  'student-project-permanent-delete.v1.json',
    'project-ai-consent.v1.json',
    'project-ai-consent-grant.v1.json',
    'project-ai-scaffold.v1.json',
    'project-ai-scaffold-request.v1.json',
    'project-ai-scaffold-settlement.v1.json',
    'project-ai-scaffold-settlement-request.v1.json',
    'project-ai-stage-assist.v1.json',
    'project-ai-stage-assist-request.v1.json',
    'project-ai-stage-assist-settlement.v1.json',
    'project-ai-stage-assist-settlement-request.v1.json',
    'project-ai-local-project-context-request.v1.json',
    'project-ai-local-project-context.v1.json',
    'project-ai-local-project-context-delete.v1.json',
  'project-ai-activity-history.v1.json',
  'project-ai-activity-clear.v1.json',
  'project-ai-general-chat-request.v2.json',
  'project-ai-general-chat-result.v2.json',
  'project-template-families.v1.json',
  'project-template-catalogue.v1.json',
  'project-template-detail.v1.json',
  'project-template-operation.v1.json',
  'project-template-starter-drafts.v1.json',
];

const fixtureFiles = [
  'account-summary-authenticated.json',
  'account-export.json',
  'health-degraded.json',
  'health-live.json',
  'health-ready.json',
  'http-error-unauthorized.json',
  'sync-push-result.json',
  'sync-pull.json',
  'analytics-event.json',
  'progress-summary.json',
  'progress-daily.json',
  'published-case-summary.json',
  'case-catalogue.json',
  'case-lifecycle-audit.json',
  'evidence-graph.json',
];

function readJson(relativePath) {
  const filePath = path.join(root, relativePath);
  return JSON.parse(fs.readFileSync(filePath, 'utf8'));
}

function readSchema(relativePath) {
  const filePath = path.join(schemaRoot, relativePath);
  return JSON.parse(fs.readFileSync(filePath, 'utf8'));
}

function hasRepositoryPaths(...relativePaths) {
  return relativePaths.every((relativePath) =>
    fs.existsSync(path.join(repositoryRoot, relativePath)),
  );
}

function repositoryFiles(relativeDirectory, extension) {
  const directory = path.join(repositoryRoot, relativeDirectory);
  const files = [];
  const visit = (currentDirectory) => {
    for (const entry of fs.readdirSync(currentDirectory, { withFileTypes: true })) {
      const entryPath = path.join(currentDirectory, entry.name);
      if (entry.isDirectory()) visit(entryPath);
      else if (entry.name.endsWith(extension)) files.push(entryPath);
    }
  };
  visit(directory);
  return files;
}

function assertNoCredentialShapedFields(value, location = '$') {
  if (Array.isArray(value)) {
    value.forEach((item, index) => assertNoCredentialShapedFields(item, `${location}[${index}]`));
    return;
  }

  if (!value || typeof value !== 'object') return;

  for (const [key, nestedValue] of Object.entries(value)) {
    const normalizedKey = key.toLowerCase().replaceAll('-', '').replaceAll('_', '');
    assert.equal(
      /^(password|accesstoken|refreshtoken|servicerole|privatekey|apikey|rawproviderpayload|rawclaims)$/.test(normalizedKey),
      false,
      `${location}.${key} must not cross the public contract boundary`,
    );
    assertNoCredentialShapedFields(nestedValue, `${location}.${key}`);
  }
}

test('all versioned response schemas are present and closed at the root', () => {
  for (const file of schemaFiles) {
    const schema = readSchema(file);
    assert.equal(schema.$schema, 'https://json-schema.org/draft/2020-12/schema');
    assert.match(schema.$id, /^https:\/\/evidrilo\.dev\/contracts\/[a-z-]+\.v[1-9][0-9]*\.json$/);
    assert.equal(schema.type, 'object');
    assert.equal(schema.additionalProperties, false);
    assert.match(schema.title, /Evidrilo/);
  }
});

test('registered API routes map to response schemas and endpoint tests', () => {
  const manifest = readJson('routes.v1.json');
  assert.deepEqual(
    Object.keys(manifest).sort(),
    ['routes', 'schema', 'version'],
  );
  assert.equal(manifest.schema, 'evidrilo.api-route-manifest');
  assert.equal(manifest.version, '1');
  assert.equal(manifest.routes.length, 61);

  const routeKeys = (routes) => routes
    .map((route) => `${route.method} ${route.path}`)
    .sort();
  const registeredRoutes = [];
  for (const file of repositoryFiles('platform/api', '.cs')) {
    const source = fs.readFileSync(file, 'utf8');
    for (const match of source.matchAll(/endpoints\.Map(Get|Post|Put|Patch|Delete)\(\s*"([^"]+)"/g)) {
      registeredRoutes.push({ method: match[1].toUpperCase(), path: match[2] });
    }
  }

  assert.deepEqual(routeKeys(registeredRoutes), routeKeys(manifest.routes));
  const seen = new Set();
  for (const route of manifest.routes) {
    assert.match(route.method, /^(GET|POST|PUT|PATCH|DELETE)$/);
    assert.match(route.authentication, /^(none|supabase_bearer|revenuecat_webhook)$/);
    for (const parameter of route.queryParameters ?? []) {
      assert.match(parameter.name, /^[A-Za-z][A-Za-z0-9]*$/);
      assert.equal(typeof parameter.required, 'boolean');
      assert.equal(typeof parameter.schema?.type, 'string');
    }
    for (const parameter of route.headerParameters ?? []) {
      assert.match(parameter.name, /^X-[A-Za-z0-9-]+$|^Idempotency-Key$/);
      assert.equal(typeof parameter.required, 'boolean');
      assert.equal(typeof parameter.schema?.type, 'string');
    }
    assert.match(route.path, /^\/(?:health|v[1-9][0-9]*)\/[A-Za-z0-9._:{}-]+(?:\/[A-Za-z0-9._:{}-]+)*$/);
    assert.ok(!seen.has(`${route.method} ${route.path}`), `duplicate ${route.method} ${route.path}`);
    seen.add(`${route.method} ${route.path}`);

    const schema = readSchema(route.responseSchema);
    assert.equal(schema.$id, `https://evidrilo.dev/contracts/${route.responseSchema}`);
    const testPath = path.join(repositoryRoot, route.testFile);
    assert.equal(fs.existsSync(testPath), true, `${route.testFile} must exist`);
    const testSource = fs.readFileSync(testPath, 'utf8');
    assert.match(testSource, new RegExp(`class ${path.basename(route.testFile, '.cs')}`));
    assert.equal(
      testSource.includes(route.testAnchor),
      true,
      `${route.testFile} must exercise ${route.testAnchor}`,
    );
    if (route.requestSchema) {
      const requestSchema = readSchema(route.requestSchema);
      assert.equal(requestSchema.$id, `https://evidrilo.dev/contracts/${route.requestSchema}`);
    }
  }
  const negotiatedAiResponses = [
    [
      'POST',
      '/v1/ai/assist',
      'application/vnd.evidrilo.ai-assist-result.v2+json',
      'ai-assist-result.v2.json',
    ],
    [
      'POST',
      '/v1/ai/conversations/{sessionId:guid}/turns',
      'application/vnd.evidrilo.ai-conversation-turn.v2+json',
      'ai-conversation-turn.v2.json',
    ],
  ];
  for (const [method, routePath, mediaType, schemaName] of negotiatedAiResponses) {
    const route = manifest.routes.find((candidate) => candidate.method === method && candidate.path === routePath);
    assert.deepEqual(route?.responseVariants, [{ mediaType, responseSchema: schemaName }]);
    const versionedSchema = readSchema(schemaName);
    assert.equal(versionedSchema.properties.creditCost.minimum, 0);
    assert.equal(versionedSchema.properties.creditCost.maximum, 200);
    assert.equal(versionedSchema.required.includes('creditCost'), true);
  }
  for (const [method, routePath, requestSchema] of [
    ['PUT', '/v1/notifications/preferences', 'notification-preferences-update.v1.json'],
    ['POST', '/v1/project-ai/scaffold', 'project-ai-scaffold-request.v1.json'],
    ['POST', '/v1/project-ai/scaffold/settlement', 'project-ai-scaffold-settlement-request.v1.json'],
    ['POST', '/v1/project-ai/stage-assist', 'project-ai-stage-assist-request.v1.json'],
    ['POST', '/v1/project-ai/stage-assist/settlement', 'project-ai-stage-assist-settlement-request.v1.json'],
    ['PUT', '/v1/project-ai/consent', 'project-ai-consent-grant.v1.json'],
    ['DELETE', '/v1/projects/{projectId:guid}/permanent', 'student-project-permanent-delete.v1.json'],
  ]) {
    assert.equal(
      manifest.routes.find((route) => route.method === method && route.path === routePath)?.requestSchema,
      requestSchema,
      `${method} ${routePath} request schema must be registered`,
    );
  }
});

test('General chat v2 is isolated and exposes only a bounded message contract', () => {
  const manifest = readJson('routes.v1.json');
  const route = manifest.routes.find((candidate) =>
    candidate.method === 'POST' && candidate.path === '/v2/project-ai/general-chat');
  assert.ok(route, 'General chat v2 must be registered as a separate API operation');
  assert.equal(route.authentication, 'supabase_bearer');
  assert.equal(route.requestSchema, 'project-ai-general-chat-request.v2.json');
  assert.equal(route.responseSchema, 'project-ai-general-chat-result.v2.json');
  assert.equal(route.headerParameters.find((parameter) => parameter.name === 'Idempotency-Key')?.required, true);
  assert.equal(route.headerParameters.find((parameter) => parameter.name === 'X-Evidrilo-General-Chat-Consent')?.required, true);
  assert.equal(
    route.headerParameters.find((parameter) => parameter.name === 'X-Evidrilo-General-Chat-Consent')?.schema.const,
    'general-chat.v1',
  );

  const request = readSchema(route.requestSchema);
  assert.equal(request.additionalProperties, false);
  assert.deepEqual(Object.keys(request.properties).sort(), [
    'installationId', 'locale', 'message', 'schema', 'version',
  ]);
  assert.equal(request.properties.version.const, '2');
  assert.equal(request.properties.message.type, 'string');
  assert.equal(request.properties.message.minLength, 1);
  assert.equal(request.properties.message.maxLength, 4000);
  assert.ok(request.required.includes('message'));

  const response = readSchema(route.responseSchema);
  assert.equal(response.additionalProperties, false);
  assert.deepEqual(response.required, [
    'schema', 'version', 'mode', 'status', 'answer', 'recommendedNextPrompts', 'requestId', 'creditCost',
  ]);
  assert.equal(response.properties.mode.const, 'GENERAL');
  assert.ok(response.required.includes('answer'));
  assert.equal(response.properties.recommendedNextPrompts.type, 'array');
  assert.equal(response.properties.recommendedNextPrompts.minItems, 1);
  assert.equal(response.properties.recommendedNextPrompts.maxItems, 3);
  assert.equal(response.properties.recommendedNextPrompts.items.maxLength, 240);
  assert.ok(response.required.includes('requestId'));
  assert.ok(response.required.includes('creditCost'));
  for (const privateField of ['message', 'projectId', 'selectedFieldIds', 'selectedEvidenceIds']) {
    assert.equal(Object.hasOwn(response.properties, privateField), false);
  }
  assert.ok(readSchema('project-ai-activity-history.v1.json')
    .properties.activities.items.properties.outcome.enum.includes('COMPLETED'));
});

test('the committed OpenAPI document is reproducible from the route and schema contracts', () => {
  const generated = spawnSync(
    process.execPath,
    [path.join(root, 'openapi', 'generate.mjs'), '--check'],
    { cwd: repositoryRoot, encoding: 'utf8' },
  );
  assert.equal(generated.status, 0, generated.stderr || generated.stdout);

  const document = readJson('openapi/openapi.v1.json');
  const manifest = readJson('routes.v1.json');
  assert.equal(document.openapi, '3.1.0');
  assert.equal(document.info.title, 'Evidrilo API');
  assert.deepEqual(
    Object.keys(document.paths).sort(),
    [...new Set(manifest.routes.map((route) => route.path.replace(/:\w+(?=\})/g, '')))].sort(),
  );

  const publicRoutes = manifest.routes
    .filter((route) => route.authentication === 'none')
    .map((route) => `${route.method} ${route.path}`)
    .sort();
  assert.deepEqual(publicRoutes, [
    'GET /health/live',
    'GET /health/ready',
    'GET /v1/project-template-families',
    'GET /v1/project-templates',
    'GET /v1/project-templates/{templateId}/versions/{templateVersion:int}',
  ]);

  for (const route of manifest.routes) {
    const pathKey = route.path.replace(/:\w+(?=\})/g, '');
    const operation = document.paths[pathKey][route.method.toLowerCase()];
    assert.ok(operation, `${route.method} ${route.path} must be in OpenAPI`);
    assert.ok(operation.responses.default, `${route.method} ${route.path} must document safe API errors`);
    assert.ok(operation['x-evidrilo-test']);
    if (route.requestSchema) {
      assert.ok(operation.requestBody, `${route.method} ${route.path} must document its request schema`);
    }
  }

  assert.deepEqual(document.paths['/health/ready'].get.security, []);
  assert.deepEqual(document.paths['/v1/project-templates'].get.security, []);
  assert.ok(document.paths['/v1/account/me'].get.security.length > 0);
  assert.deepEqual(
    document.paths['/v1/billing/webhook'].post.security,
    [{ RevenueCatWebhookAuthorization: [] }, { RevenueCatWebhookSignature: [] }],
  );
  assert.ok(document.paths['/v1/projects/{projectId}'].parameters.some((parameter) =>
    parameter.name === 'projectId' && parameter.schema.format === 'uuid'));
  assert.ok(document.paths['/v1/projects'].get.parameters.some((parameter) =>
    parameter.name === 'beforeCreatedAt' && parameter.schema.format === 'date-time'));
  assert.ok(document.paths['/v1/project-ai/activity'].get.parameters.some((parameter) =>
    parameter.name === 'installationId' && parameter.required));
  assert.ok(document.paths['/v1/project-ai/stage-assist'].post.parameters.some((parameter) =>
    parameter.name === 'Idempotency-Key' && parameter.in === 'header' && parameter.required));
  assert.equal(document.paths['/v1/ai/assist'].post.parameters[0].required, true);
  assert.equal(document.paths['/v1/account/me'].delete.parameters[0].schema.const, 'delete-my-account');
  assert.ok(document.components.schemas['student-project.v1'].$defs.projectDocument);
  assert.ok(document.paths['/v1/project-ai/stage-assist'].post.requestBody.content['application/json']);
  assert.ok(document.paths['/v1/ai/assist'].post.responses['2XX'].content[
    'application/vnd.evidrilo.ai-assist-result.v2+json']);
  const checkReferences = (value, location = '$') => {
    if (Array.isArray(value)) {
      value.forEach((item, index) => checkReferences(item, `${location}[${index}]`));
      return;
    }
    if (!value || typeof value !== 'object') return;
    if (typeof value.$ref === 'string') {
      assert.match(value.$ref, /^#\/components\/schemas\//, `${location} must resolve locally`);
      const pointer = value.$ref.slice(2).split('/').map((part) =>
        part.replaceAll('~1', '/').replaceAll('~0', '~'));
      const target = pointer.reduce((current, part) => current?.[part], document);
      assert.notEqual(target, undefined, `${location} points to a defined schema`);
    }
    Object.entries(value).forEach(([key, nested]) => checkReferences(nested, `${location}.${key}`));
  };
  checkReferences(document.paths);
  checkReferences(document.components.schemas);
  assert.equal(JSON.stringify(document).includes('OPENAI_API_KEY'), false);
  assert.equal(JSON.stringify(document).includes('SUPABASE_SECRET_KEY'), false);
});

test('notification preference updates require an optimistic revision precondition', () => {
  const schema = readSchema('notification-preferences-update.v1.json');

  assert.equal(schema.required.includes('expectedRevision'), true);
  assert.deepEqual(schema.properties.expectedRevision, { type: 'integer', minimum: 0 });
});

test('project API exposes only explicitly confirmed permanent deletion', () => {
  const manifest = readJson('routes.v1.json');
  const ordinaryDelete = manifest.routes.find(
    (route) => route.method === 'DELETE' && route.path === '/v1/projects/{projectId:guid}',
  );
  assert.equal(ordinaryDelete, undefined, 'ordinary project delete must not hard-delete data');

  const permanentDelete = manifest.routes.find(
    (route) => route.method === 'DELETE' && route.path === '/v1/projects/{projectId:guid}/permanent',
  );
  assert.ok(permanentDelete, 'hard deletion must use an explicit permanent route');
  assert.equal(permanentDelete.requestSchema, 'student-project-permanent-delete.v1.json');

  const request = readSchema(permanentDelete.requestSchema);
  assert.equal(request.properties.confirmPermanently.const, true);
  assert.equal(request.required.includes('confirmPermanently'), true);
});

test('student project contract supports multiple stable claims and typed evidence relations', () => {
  const project = readSchema('student-project.v1.json').$defs.projectDocument;
  const claim = readSchema('student-project.v1.json').$defs.claim;
  const relation = readSchema('student-project.v1.json').$defs.claimEvidenceLink;

  assert.deepEqual(project.properties.claims.type, ['array', 'null']);
  assert.equal(project.properties.claims.maxItems, 100);
  assert.deepEqual(project.properties.claimEvidenceLinks.type, ['array', 'null']);
  assert.equal(project.properties.claimEvidenceLinks.items.$ref, '#/$defs/claimEvidenceLink');
  assert.deepEqual(claim.required, ['id', 'statement', 'scopeNote', 'limitationsNote', 'reviewStatus']);
  assert.deepEqual(relation.required, ['claimId', 'evidenceItemId', 'relationship', 'rationale']);
  assert.deepEqual(relation.properties.relationship.enum, ['supports', 'contradicts', 'provides_context']);
});

test('Project AI preview and settlement contracts expose server-held cost and idempotent decisions', () => {
  const preview = readSchema('project-ai-scaffold.v1.json');
  const previewRequest = readSchema('project-ai-scaffold-request.v1.json');
  const settlementRequest = readSchema('project-ai-scaffold-settlement-request.v1.json');
  const settlementResponse = readSchema('project-ai-scaffold-settlement.v1.json');

  assert.equal(preview.required.includes('creditCost'), true);
  assert.deepEqual(
    [preview.properties.creditCost.type, preview.properties.creditCost.minimum, preview.properties.creditCost.maximum],
    ['integer', 1, 200],
  );
  assert.deepEqual(preview.properties.operation.enum, ['create_project', 'assist_project']);
  assert.equal(previewRequest.required.includes('operation'), true);
  assert.equal(previewRequest.required.includes('projectId'), true);
  assert.equal(previewRequest.required.includes('baseProjectRevision'), true);
  assert.equal(previewRequest.allOf.length, 2);
  assert.equal(preview.allOf.length, 2);
  assert.equal(Object.hasOwn(preview.allOf[0].then.properties, 'creditCost'), false);
  assert.equal(Object.hasOwn(preview.allOf[1].then.properties, 'creditCost'), false);
  assert.deepEqual(settlementRequest.required, ['schema', 'version', 'requestId', 'decision']);
  assert.deepEqual(settlementRequest.properties.decision.enum, ['apply', 'dismiss']);
  assert.equal(settlementRequest.description.includes('Idempotency-Key'), true);
  assert.deepEqual(
    settlementResponse.required,
    ['schema', 'version', 'status', 'requestId', 'creditCost'],
  );
  assert.deepEqual(settlementResponse.properties.status.enum, ['applied', 'dismissed']);
});

test('AI credit contract bounds the one-time Free and monthly Pro grants', () => {
  const credits = readSchema('ai-credits.v1.json');
  const grant = credits.properties.grants.items;

  assert.equal(credits.properties.available.maximum, 220);
  assert.equal(grant.properties.granted.maximum, 200);
  assert.deepEqual(
    grant.allOf.map((rule) => rule.then.properties.granted.const),
    [20, 200],
  );
});

test('D-119 stage assistance and activity contracts stay project-bound and metadata-only', () => {
  const request = readSchema('project-ai-stage-assist-request.v1.json');
  const preview = readSchema('project-ai-stage-assist.v1.json');
  const settlement = readSchema('project-ai-stage-assist-settlement-request.v1.json');
  const activity = readSchema('project-ai-activity-history.v1.json');
  const clear = readSchema('project-ai-activity-clear.v1.json');

  assert.deepEqual(request.oneOf.map((entry) => entry.$ref), [
    '#/$defs/projectRequest', '#/$defs/localProjectRequest', '#/$defs/generalRequest',
  ]);
  assert.equal(request.$defs.projectPayload.properties.projectId.format, 'uuid');
  assert.equal(request.$defs.projectPayload.properties.selectedFieldIds.maxItems, 32);
  assert.ok(request.$defs.projectPayload.properties.selectedEvidenceIds, 'Project requests must identify selected evidence items.');
  assert.equal(request.$defs.projectPayload.properties.selectedEvidenceIds.maxItems, 32);
  assert.equal(request.$defs.projectPayload.required.includes('selectedEvidenceIds'), true);
  assert.equal(Object.hasOwn(request.$defs.projectPayload.properties, 'selectedFields'), true);
  assert.equal(request.$defs.projectRequest.allOf[2].not.anyOf.some((condition) => condition.required.includes('selectedFields')), true);
  assert.equal(request.$defs.localProjectRequest.allOf[2].required.includes('projectBindingGeneration'), true);
  assert.equal(request.$defs.localProjectRequest.allOf[2].required.includes('selectedFields'), true);
  assert.equal(request.$defs.generalRequest.allOf[1].properties.mode.const, 'GENERAL');
  assert.equal(request.$defs.generalRequest.allOf[1].properties.selectedFieldIds.maxItems, 0);
  assert.equal(request.$defs.generalRequest.allOf[1].properties.selectedEvidenceIds.maxItems, 0);
  assert.equal(preview.required.includes('assist'), true);
  assert.equal(preview.required.includes('evaluationPreview'), true);
  assert.equal(preview.$defs.assist.properties.items.items.$ref, '#/$defs/assistItem');
  assert.equal(preview.$defs.assistItem.properties.kind.enum.includes('PROPOSAL'), true);
  assert.equal(preview.$defs.evaluationPreview.properties.assessmentStatus.const, 'NOT_ASSESSED');
  assert.ok(preview.$defs.evaluationPreview.properties.reportedKnownLimits);
  assert.ok(preview.$defs.evaluationPreview.properties.templateLimits);
  assert.deepEqual(
    [preview.properties.creditCost.type, preview.properties.creditCost.minimum, preview.properties.creditCost.maximum],
    ['integer', 1, 200],
  );
  assert.deepEqual(settlement.properties.outcome.enum, ['APPLIED', 'EDITED', 'DISMISSED', 'STALE']);
  assert.equal(activity.properties.activities.items.properties.projectId.oneOf[1].type, 'null');
  assert.equal(activity.properties.activities.items.properties.outcome.enum.includes('FAILED'), true);
  assert.deepEqual(clear.required, ['schema', 'version', 'clearedCount']);
  for (const property of ['prompt', 'responseText', 'transcript', 'sourceText']) {
    assert.equal(Object.hasOwn(activity.properties.activities.items.properties, property), false);
  }
});

test('synthetic fixtures identify their schema and version', () => {
  for (const file of fixtureFiles) {
    const fixture = readJson(path.join('fixtures', file));
    assert.equal(typeof fixture.schema, 'string', `${file} schema`);
    assert.equal(fixture.version, '1', `${file} version`);
    assert.match(fixture.requestId, /^[A-Za-z0-9_-]{8,128}$/, `${file} requestId`);
    assertNoCredentialShapedFields(fixture);
  }
});

test('starter project templates remain bounded, complete, and explicitly unreviewed drafts', {
  skip: !hasRepositoryPaths('docs/product/project-template-starter-drafts.v1.json'),
}, () => {
  const pack = JSON.parse(fs.readFileSync(
    path.join(repositoryRoot, 'docs', 'product', 'project-template-starter-drafts.v1.json'),
    'utf8',
  ));
  const schema = readSchema('project-template-starter-drafts.v1.json');
  const expectedFamilies = [
    'experimental_laboratory',
    'observational_survey',
    'literature_review',
    'qualitative_interview_field_study',
    'design_engineering',
  ];

  assert.equal(pack.schema, 'evidrilo.project-template-starter-drafts');
  assert.equal(pack.version, '1');
  assert.deepEqual(pack.templates.map((template) => template.family), expectedFamilies);
  assert.equal(new Set(pack.templates.map((template) => template.templateId)).size, 5);

  for (const draft of pack.templates) {
    assert.equal(draft.state, 'draft');
    assert.equal(draft.templateVersion, 1);
    assert.equal(draft.content.examples.length > 0, true);
    assert.equal(draft.content.examples.every((example) => example.reviewed === false), true);
    assert.deepEqual(
      new Set(draft.content.examples.map((example) => example.kind)),
      new Set(['normal', 'edge_or_conflicting']),
      `${draft.templateId} must include nominal and boundary examples`,
    );
    assert.equal(
      draft.content.inputFields.some((field) => field.kind === 'hypothesis' && field.required),
      false,
      `${draft.templateId} must not require a hypothesis for every method`,
    );
    assert.equal(draft.content.methodSpecificLimitations.length > 0, true);
    assert.equal(draft.content.provenanceRequirements.length > 0, true);
    assert.equal(draft.content.accessibilityExpectations.length > 0, true);
    const inputIds = new Set(draft.content.inputFields.map((field) => field.id));
    for (const step of draft.content.steps) {
      assert.equal(step.inputFieldIds.every((fieldId) => inputIds.has(fieldId)), true);
    }
  }

  assert.equal(schema.properties.schema.const, pack.schema);
  assert.equal(schema.properties.version.const, pack.version);
});

test('template review pack covers every starter example without claiming human review', {
  skip: !hasRepositoryPaths('docs/product/project-template-starter-drafts.v1.json'),
}, () => {
  const starterPack = JSON.parse(fs.readFileSync(
    path.join(repositoryRoot, 'docs', 'product', 'project-template-starter-drafts.v1.json'),
    'utf8',
  ));
  const reviewPackPath = path.join(
    repositoryRoot,
    'internal',
    'research',
    'next-gen',
    'project-template-review-pack.v1.json',
  );

  assert.equal(fs.existsSync(reviewPackPath), true, 'owner-local review pack must exist');
  const reviewPack = JSON.parse(fs.readFileSync(reviewPackPath, 'utf8'));
  assert.equal(reviewPack.schema, 'evidrilo.project-template-review-pack');
  assert.equal(reviewPack.version, '1');
  assert.equal(reviewPack.status, 'REVIEW_READY_DRAFT');
  assert.equal(reviewPack.studentSelectable, false);
  assert.equal(reviewPack.reviewPolicy.independentReviewerRequired, true);
  assert.equal(reviewPack.reviewPolicy.authorMayApproveOwnTemplate, false);
  assert.deepEqual(reviewPack.humanReviewRecords, []);
  assert.equal(reviewPack.commonReviewChecklist.length >= 5, true);
  assert.equal(reviewPack.reviewRecordTemplate.status, 'BLANK_TEMPLATE_NOT_A_REVIEW');
  assert.equal(reviewPack.reviewRecordTemplate.changesServerLifecycle, false);
  assert.deepEqual(reviewPack.reviewPolicy.publicationRequiresReviewedExampleKinds, [
    'normal',
    'edge_or_conflicting',
  ]);
  assert.deepEqual(reviewPack.reviewRecordTemplate.allowedDispositions, [
    'CHANGES_REQUIRED',
    'NO_BLOCKING_FINDINGS_IDENTIFIED',
    'CANNOT_ASSESS',
  ]);
  for (const field of [
    'reviewerRole',
    'methodExpertise',
    'conflictDisclosure',
    'templateId',
    'templateVersion',
    'workedExampleIds',
    'checklistResults',
    'findings',
    'reviewDisposition',
    'rationale',
    'requestedChanges',
  ]) {
    assert.equal(reviewPack.reviewRecordTemplate.requiredFields.includes(field), true);
  }
  assert.deepEqual(
    reviewPack.templates.map((template) => template.templateId),
    starterPack.templates.map((template) => template.templateId),
  );

  for (const [index, candidate] of starterPack.templates.entries()) {
    const reviewTemplate = reviewPack.templates[index];
    assert.equal(reviewTemplate.templateVersion, candidate.templateVersion);
    assert.equal(reviewTemplate.family, candidate.family);
    assert.equal(reviewTemplate.title, candidate.content.title);
    assert.equal(reviewTemplate.lifecycleState, 'draft');
    assert.equal(reviewTemplate.reviewStatus, 'NOT_REVIEWED');
    assert.equal(reviewTemplate.studentSelectable, false);
    assert.deepEqual(reviewTemplate.reviewedExampleIds, []);
    assert.equal(reviewTemplate.reviewDecision, null);
    assert.equal(reviewTemplate.methodReviewChecklist.length >= 3, true);
    assert.deepEqual(
      reviewTemplate.workedExamples.map((example) => example.exampleId),
      candidate.content.examples.map((example) => example.id),
    );
    assert.equal(reviewTemplate.workedExamples.length >= 2, true);
    assert.deepEqual(
      [...new Set(reviewTemplate.workedExamples.map((example) => example.kind))].sort(),
      ['edge_or_conflicting', 'normal'],
    );

    const allowedInputIds = new Set(candidate.content.inputFields.map((field) => field.id));
    for (const example of reviewTemplate.workedExamples) {
      const sourceExample = candidate.content.examples.find((item) => item.id === example.exampleId);
      assert.equal(example.synthetic, true);
      assert.equal(example.reviewed, false);
      assert.equal(example.kind, sourceExample.kind);
      assert.equal(example.summary, sourceExample.summary);
      assert.equal(example.studentInputs.length > 0, true);
      assert.equal(example.studentInputs.every((input) => allowedInputIds.has(input.fieldId)), true);
      assert.equal(example.evidenceAnchors.length > 0, true);
      assert.equal(example.candidateClaim.length > 0, true);
      assert.equal(example.illustrativeBoundedReading.length > 0, true);
      assert.equal(example.mustNotInfer.length > 0, true);
      assert.equal(example.reviewerQuestions.length > 0, true);
    }
  }

  assertNoCredentialShapedFields(reviewPack);
});

test('health fixtures distinguish dependency-free liveness from readiness', () => {
  const live = readJson(path.join('fixtures', 'health-live.json'));
  const ready = readJson(path.join('fixtures', 'health-ready.json'));
  const degraded = readJson(path.join('fixtures', 'health-degraded.json'));

  assert.deepEqual(live, {
    schema: 'evidrilo.health',
    version: '1',
    check: 'live',
    status: 'ok',
    requestId: live.requestId,
  });
  assert.equal(ready.check, 'ready');
  assert.equal(ready.status, 'ready');
  assert.equal(ready.dependencies.config, 'ready');
  assert.equal(degraded.check, 'ready');
  assert.equal(degraded.status, 'degraded');
  assert.equal(degraded.dependencies.config, 'missing');
});

test('Android draft persistence does not synchronously commit on the UI event path', () => {
  const source = fs.readFileSync(
    path.join(
      root,
      '..',
      'modules',
      'data',
      'src',
      'androidMain',
      'kotlin',
      'dev',
      'nextgen',
      'mobile',
      'storage',
      'ConclusionSessionStore.android.kt',
    ),
    'utf8',
  );
  const saveStart = source.indexOf('override fun save');
  const clearStart = source.indexOf('override fun clear');

  assert.ok(saveStart >= 0 && clearStart > saveStart, 'save and clear implementations must remain explicit');
  const saveBody = source.slice(saveStart, clearStart);
  assert.match(saveBody, /\.apply\(\)/);
  assert.doesNotMatch(saveBody, /\.commit\(\)/);
});

test('error and account fixtures expose only safe public fields', () => {
  const error = readJson(path.join('fixtures', 'http-error-unauthorized.json'));
  const account = readJson(path.join('fixtures', 'account-summary-authenticated.json'));
  const accountExport = readJson(path.join('fixtures', 'account-export.json'));

  assert.deepEqual(Object.keys(error).sort(), ['code', 'message', 'requestId', 'schema', 'version']);
  assert.match(error.code, /^[A-Z][A-Z0-9_]{2,63}$/);
  assert.equal(typeof error.message, 'string');
  assert.ok(error.message.length > 0 && error.message.length <= 240);

  assert.deepEqual(Object.keys(account).sort(), [
    'accountId',
    'emailVerified',
    'requestId',
    'schema',
    'serverTime',
    'version',
  ]);
  assert.match(account.accountId, /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/);
  assert.equal(account.emailVerified, true);
  assert.match(account.serverTime, /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d{3}Z$/);

  assert.deepEqual(accountExport.data.studentProjects, []);
  assert.deepEqual(accountExport.data.studentProjectRevisions, []);
  assert.equal(Object.hasOwn(accountExport.data, 'studentProjectCommands'), false);
  const exportDataSchema = readSchema('account-export.v1.json').properties.data;
  assert.ok(exportDataSchema.required.includes('studentProjects'));
  assert.ok(exportDataSchema.required.includes('studentProjectRevisions'));
  assert.deepEqual(exportDataSchema.not.required, ['studentProjectCommands']);

  assert.deepEqual(Object.keys(accountExport).sort(), [
    'accountId',
    'data',
    'generatedAt',
    'requestId',
    'schema',
    'version',
  ]);
  assert.equal(accountExport.schema, 'evidrilo.account-export');
  assert.equal(accountExport.version, '1');
  assert.match(accountExport.accountId, /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/);
  assert.match(accountExport.generatedAt, /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d{3}Z$/);
  assert.match(accountExport.requestId, /^[A-Za-z0-9_-]{8,128}$/);
  assert.equal(accountExport.data.localDrafts, 'not_on_server');
  assertNoCredentialShapedFields(accountExport);
});

test('sync fixtures preserve idempotency and cursor boundaries', () => {
  const push = readJson(path.join('fixtures', 'sync-push-result.json'));
  const pull = readJson(path.join('fixtures', 'sync-pull.json'));

  assert.equal(push.schema, 'evidrilo.sync-push-result');
  assert.equal(push.results[0].outcome, 'accepted');
  assert.equal(push.results[1].outcome, 'duplicate');
  assert.equal(push.nextCursor, 41);
  assert.equal(pull.schema, 'evidrilo.sync-pull');
  assert.equal(pull.cursor, 40);
  assert.equal(pull.nextCursor, 41);
  assert.equal(pull.hasMore, false);
  assert.equal(pull.changes[0].commandType, 'attempt_submitted');
  assert.equal(pull.changes[0].snapshotDigest.length, 64);
});

test('analytics contracts are consented, typed, and free of raw learner text', () => {
  const event = readJson(path.join('fixtures', 'analytics-event.json'));
  const progress = readJson(path.join('fixtures', 'progress-summary.json'));
  const daily = readJson(path.join('fixtures', 'progress-daily.json'));

  assert.equal(event.consent, 'granted');
  assert.equal(event.source, 'mobile');
  assert.equal(event.eventVersion, 1);
  assert.equal(event.properties.revisionChanged, true);
  assert.equal('rawDraftText' in event.properties, false);
  assert.equal('email' in event.properties, false);
  assert.equal(progress.calculationVersion, 'progress.v1');
  assert.ok(progress.coverage >= 0 && progress.coverage <= 1);
  assert.equal(daily.schema, 'evidrilo.progress-daily');
  assert.equal(daily.items[0].projectionDate, '2026-09-10');
  assert.ok(daily.items[0].coverage >= 0 && daily.items[0].coverage <= 1);
});

test('analytics schema covers funnel, premium conversion, and safe client errors', () => {
  const schema = readSchema('analytics-event.v1.json');
  const eventNames = schema.properties.eventName.enum;
  for (const name of [
    'practice_started',
    'paywall_viewed',
    'premium_action',
    'client_error',
  ]) {
    assert.equal(eventNames.includes(name), true, `${name} must be versioned`);
  }

  const properties = schema.properties.properties;
  assert.equal(properties.additionalProperties, false);
  assert.equal('errorMessage' in properties.properties, false);
  assert.match(properties.properties.surfaceId.pattern, /\^\[A-Za-z0-9._:-\]/);
  assert.match(properties.properties.errorCode.pattern, /\^\[A-Z\]/);
  assert.deepEqual(properties.properties.productId.enum, ['monthly', 'yearly', null]);
});

test('analytics idempotency normalizes nullable properties across app versions', () => {
  const repositoryRoot = path.resolve(root, '..');
  const store = fs.readFileSync(
    path.join(repositoryRoot, 'platform', 'api', 'Analytics', 'AnalyticsStore.cs'),
    'utf8',
  );
  assert.equal((store.match(/jsonb_strip_nulls/g) ?? []).length, 2);
  assert.match(store, /jsonb_strip_nulls\(jsonb_build_object\(/);
});

test('the repository verification harness is documented and non-secret', () => {
  const repositoryRoot = path.resolve(root, '..');
  const harnessPath = path.join(repositoryRoot, 'scripts', 'ci', 'verify-local.sh');
  assert.equal(fs.existsSync(harnessPath), true, 'scripts/ci/verify-local.sh must exist');
  const harness = fs.readFileSync(harnessPath, 'utf8');
  for (const task of [
    ':modules:core:jvmTest',
    ':modules:domain:jvmTest',
    ':modules:data:jvmTest',
    ':modules:design-system:jvmTest',
    ':modules:application:jvmTest',
    ':modules:features:jvmTest',
    ':composeApp:jvmTest',
  ]) {
    assert.match(
      harness,
      new RegExp(task.replaceAll(':', '\\:')),
      `${task} must be part of the local verification harness`,
    );
  }
  const workflow = fs.readFileSync(
    path.join(repositoryRoot, '.github', 'workflows', 'verify.yml'),
    'utf8',
  );
  for (const task of [
    ':modules:core:jvmTest',
    ':modules:domain:jvmTest',
    ':modules:data:jvmTest',
    ':modules:design-system:jvmTest',
    ':modules:application:jvmTest',
    ':modules:features:jvmTest',
    ':composeApp:jvmTest',
  ]) {
    assert.match(
      workflow,
      new RegExp(task.replaceAll(':', '\\:')),
      `${task} must be part of the CI mobile verification job`,
    );
  }
  assert.match(harness, /contracts\/contracts\.test\.mjs/);
  assert.match(harness, /platform\/database\/migrations\/migrations\.test\.mjs/);
  assert.match(harness, /scripts\/release\/check-version-alignment\.test\.mjs/);
  assert.match(harness, /toolchain-paths\.sh/);
  assert.match(harness, /evidrilo_dotnet_root/);
  assert.match(harness, /platform\/api\.Tests\/Evidrilo\.Api\.Tests\.csproj/);
  assert.match(harness, /javac -version/);
  assert.match(harness, /version \"21/);
  assert.match(harness, /gradle_available/);
  assert.doesNotMatch(harness, /(?:sk_|service_role|access_token|refresh_token|password\s*=)/i);

  const testingGuide = fs.readFileSync(path.join(repositoryRoot, 'docs', 'testing.md'), 'utf8');
  assert.match(testingGuide, /UNAVAILABLE/);
  const roadmap = fs.readFileSync(path.join(repositoryRoot, 'docs', 'roadmap.md'), 'utf8');
  assert.match(roadmap, /repository-owned preparation boundary/);
  assert.match(
    roadmap,
    /### Platform API semantic closure[\s\S]*?033_ai_credit_request_fingerprint/,
  );
});

test('billing webhook and forwarded headers have explicit boundaries', () => {
  const repositoryRoot = path.resolve(root, '..');
  const billingEndpoints = fs.readFileSync(
    path.join(repositoryRoot, 'platform', 'api', 'Billing', 'BillingEndpoints.cs'),
    'utf8',
  );
  const program = fs.readFileSync(
    path.join(repositoryRoot, 'platform', 'api', 'Program.cs'),
    'utf8',
  );
  const options = fs.readFileSync(
    path.join(repositoryRoot, 'platform', 'api', 'Configuration', 'PlatformOptions.cs'),
    'utf8',
  );

  assert.match(billingEndpoints, /RequireRateLimiting\("billing-webhook"\)/);
  assert.match(program, /AddPolicy\("billing-webhook"/);
  assert.match(program, /TrustedProxyAddresses/);
  assert.match(program, /UseForwardedHeaders\(\)/);
  assert.match(options, /TRUSTED_PROXY_ADDRESSES/);
});

test('Android local billing configuration uses the documented ignored properties', () => {
  const repositoryRoot = path.resolve(root, '..');
  const composeBuild = fs.readFileSync(
    path.join(repositoryRoot, 'apps', 'mobile-shared', 'build.gradle.kts'),
    'utf8',
  );

  assert.match(composeBuild, /local\.properties/);
  assert.match(composeBuild, /providers\.fileContents/);
  assert.match(composeBuild, /revenuecatAndroidApiKey/);
  assert.match(composeBuild, /revenuecatEntitlementId/);
  assert.match(composeBuild, /revenuecatProductIds/);
  assert.doesNotMatch(composeBuild, /test_[A-Za-z0-9]{20,}/);
});

test('RevenueCat Test Store runbook keeps the approved monthly/yearly catalog', {
  skip: !hasRepositoryPaths('docs/operations/revenuecat-test-store-runbook.md'),
}, () => {
  const repositoryRoot = path.resolve(root, '..');
  const runbook = fs.readFileSync(
    path.join(repositoryRoot, 'docs', 'operations', 'revenuecat-test-store-runbook.md'),
    'utf8',
  );

  assert.match(runbook, /`evidrilo_pro` with only `monthly` and\s+`yearly`/);
  assert.match(runbook, /Lifetime\s+is not part\s+of the approved catalog/i);
  assert.doesNotMatch(runbook, /`monthly`, `yearly`, and `lifetime`/);
  assert.doesNotMatch(runbook, /monthly,yearly,lifetime/);
});

test('RevenueCat Test Store runbook does not overclaim dashboard configuration', {
  skip: !hasRepositoryPaths('docs/operations/revenuecat-test-store-runbook.md'),
}, () => {
  const repositoryRoot = path.resolve(root, '..');
  const runbook = fs.readFileSync(
    path.join(repositoryRoot, 'docs', 'operations', 'revenuecat-test-store-runbook.md'),
    'utf8',
  );

  assert.match(runbook, /(?:approved )?price replacement and transaction matrix remain\s+owner\s+gates/i);
  assert.doesNotMatch(runbook, /catalog is now configured in the owner-authorized dashboard/i);
});

test('runtime matrix separates the current boundary from historical Android evidence', {
  skip: !hasRepositoryPaths('audit/runtime-matrix.md'),
}, () => {
  const repositoryRoot = path.resolve(root, '..');
  const runtimeMatrix = fs.readFileSync(
    path.join(repositoryRoot, 'audit', 'runtime-matrix.md'),
    'utf8',
  );

  const latestDocumentedRecord = runtimeMatrix.match(/^Last documentation check: .*?\((E\d{3})/m)?.[1];
  const currentBoundary = runtimeMatrix.match(/^## Current Evidrilo evidence boundary — (.+)$/m)?.[1];
  assert.ok(latestDocumentedRecord, 'runtime matrix names its latest evidence record');
  assert.ok(currentBoundary, 'runtime matrix contains its current boundary heading');
  assert.ok(currentBoundary.startsWith(latestDocumentedRecord + ' /'));
  assert.match(runtimeMatrix, /^\| Notification permission\/schedule \| `ANDROID_RUNTIME \/ LOCAL_PERMISSION_AND_ALARM_SCHEDULE_CANCEL_OBSERVED` \| E213 \|/m);
  assert.doesNotMatch(runtimeMatrix, /^## Current Evidrilo status — E107$/m);
  assert.match(runtimeMatrix, /^\| Android runtime \| `ANDROID_RUNTIME \/ [A-Z0-9_]+` \| E\d{3}(?:, E\d{3})* \|.*process restart/m);
  assert.match(runtimeMatrix, /^\| RevenueCat Test Store \| `PROVIDER_TEST_STORE \/ UNRUN` \| E185 status \|/m);
});

test('conclusion choices use native radio and checkbox semantics', () => {
  const repositoryRoot = path.resolve(root, '..');
  const source = fs.readFileSync(
    path.join(repositoryRoot, 'apps', 'mobile-shared', 'src', 'commonMain', 'kotlin', 'dev', 'nextgen', 'mobile', 'EvidriloApp.kt'),
    'utf8',
  );
  const choiceButton = source.slice(
    source.indexOf('private fun EvidriloChoiceButton'),
    source.indexOf('private fun EvidriloFeedbackCard'),
  );

  assert.match(choiceButton, /Modifier\.toggleable\(/);
  assert.match(choiceButton, /Modifier\.selectable\(/);
  assert.match(choiceButton, /mergeDescendants = true/);
  assert.doesNotMatch(choiceButton, /Modifier\.clickable\(/);
});

test('current all-area audit records the latest local verification boundary', {
  skip: !hasRepositoryPaths('audit/evidence/evidrilo-all-areas-audit-2026-09-13.md'),
}, () => {
  const repositoryRoot = path.resolve(root, '..');
  const audit = fs.readFileSync(
    path.join(repositoryRoot, 'audit', 'evidence', 'evidrilo-all-areas-audit-2026-09-13.md'),
    'utf8',
  );

  assert.match(audit, /^# Evidrilo All-Area Capability Audit$/m);
  assert.match(audit, /^Latest increment: E192 \/ REPOSITORY_SEMANTIC_CLOSURE_VERIFIED \/ E191 \/ TARGET_SURFACES_AND_CASE_CATALOGUE_INTEGRATED \/ E190 \/ EVIDENCE_GRAPH_ANCHOR_CLOSURE_HARDENED \/ E189 \/ KOTLIN_MODULE_BOUNDARIES_AND_VERIFICATION_ALIGNED \/ E188 \/ RELEASE_VERSION_SOURCE_ALIGNED /m);
  assert.match(audit, /E189[\s\S]*?Node contract,[\s\S]*?`106\/106`/);
  assert.match(audit, /API[\s\S]*?`172\/172`/);
  assert.match(audit, /E186 hardens the backend-first engine[\s\S]*?Kotlin\/JVM `332\/332`/);
  assert.match(audit, /E176 records request lifecycle and input-boundary hardening/);
  assert.match(audit, /E177 records sync pull page-size hardening/);
  assert.match(audit, /E178 records API full-match input hardening/);
  assert.match(audit, /E179 records consent-bound sync orchestration/);
  assert.match(audit, /E174 records fail-closed response and refresh hardening/);
  assert.match(audit, /E151[^\n]*product allowlist/i);
  assert.match(audit, /E152[^\n]*last active owner/i);
  assert.match(audit, /E153[^\n]*client\/request boundary/i);
  assert.match(audit, /E154[^\n]*auth callback/i);
  assert.doesNotMatch(audit, /Status: `E147 \/ AI_SCOPE_DECISION_LOCKED/);
});

test('active backend execution register points to the current verification boundary', {
  skip: !hasRepositoryPaths('docs/operations/evidrilo-backend-execution.md'),
}, () => {
  const repositoryRoot = path.resolve(root, '..');
  const register = fs.readFileSync(
    path.join(repositoryRoot, 'docs', 'operations', 'evidrilo-backend-execution.md'),
    'utf8',
  );

  assert.match(register, /^# Evidrilo Backend — Execution Boundary/m);
  assert.match(register, /idempotency/i);
  assert.match(register, /AI (?:allowance|is enabled)|server ledger/i);
  assert.match(register, /External gates/);
  assert.doesNotMatch(register, /Last synchronized: 2026-09-10 \(E110\)/);
});

test('RevenueCat managed UI stays platform-scoped and keeps a local fallback', () => {
  const repositoryRoot = path.resolve(root, '..');
  const catalog = fs.readFileSync(
    path.join(repositoryRoot, 'gradle', 'libs.versions.toml'),
    'utf8',
  );
  const build = fs.readFileSync(
    path.join(repositoryRoot, 'apps', 'mobile-shared', 'build.gradle.kts'),
    'utf8',
  );
  const commonUi = fs.readFileSync(
    path.join(
      repositoryRoot,
      'apps',
      'mobile-shared',
      'src',
      'commonMain',
      'kotlin',
      'dev',
      'nextgen',
      'mobile',
      'billing',
      'RevenueCatUi.kt',
    ),
    'utf8',
  );
  const androidUi = fs.readFileSync(
    path.join(
      repositoryRoot,
      'apps',
      'mobile-shared',
      'src',
      'androidMain',
      'kotlin',
      'dev',
      'nextgen',
      'mobile',
      'billing',
      'RevenueCatUi.android.kt',
    ),
    'utf8',
  );
  const iosUi = fs.readFileSync(
    path.join(
      repositoryRoot,
      'apps',
      'mobile-shared',
      'src',
      'iosMain',
      'kotlin',
      'dev',
      'nextgen',
      'mobile',
      'billing',
      'RevenueCatUi.ios.kt',
    ),
    'utf8',
  );
  const jvmUi = fs.readFileSync(
    path.join(
      repositoryRoot,
      'apps',
      'mobile-shared',
      'src',
      'jvmMain',
      'kotlin',
      'dev',
      'nextgen',
      'mobile',
      'billing',
      'RevenueCatUi.jvm.kt',
    ),
    'utf8',
  );

  assert.match(catalog, /revenuecat-kmp-ui = \{ module = "com\.revenuecat\.purchases:purchases-kmp-ui"/);
  assert.equal((build.match(/libs\.revenuecat\.kmp\.ui/g) ?? []).length, 2);
  assert.match(commonUi, /expect fun RevenueCatManagedPaywall/);
  assert.match(commonUi, /expect fun RevenueCatCustomerCenter/);
  assert.match(androidUi, /com\.revenuecat\.purchases\.kmp\.ui\.revenuecatui\.Paywall/);
  assert.match(androidUi, /com\.revenuecat\.purchases\.kmp\.ui\.revenuecatui\.CustomerCenter/);
  assert.match(iosUi, /com\.revenuecat\.purchases\.kmp\.ui\.revenuecatui\.Paywall/);
  assert.match(iosUi, /com\.revenuecat\.purchases\.kmp\.ui\.revenuecatui\.CustomerCenter/);
  assert.match(jvmUi, /RevenueCatUiAvailability\(canPresent = false\)/);
});

test('published case contract carries canonical learning content', () => {
  const schema = readSchema('case-summary.v1.json');
  const properties = schema.properties;

  for (const name of ['objective', 'difficulty', 'evidenceReferences', 'facts', 'rules', 'variants']) {
    assert.equal(name in properties, true, `${name} must be part of the published case contract`);
  }

  assert.equal(properties.facts.items.additionalProperties, false);
  assert.equal(properties.rules.items.additionalProperties, false);
  assert.equal(properties.variants.items.additionalProperties, false);
  assert.equal(properties.caseId.pattern, '^[A-Za-z0-9._:-]{1,128}$');
  assert.equal(properties.caseVersionId.pattern, '^[A-Za-z0-9._:-]{1,128}$');
  assert.equal(properties.evaluatorVersion.pattern, '^[A-Za-z0-9._:-]{1,128}$');
  assert.equal(properties.title.minLength, 1);
  assert.equal(properties.skillTags.minItems, 1);
  assert.equal(properties.skillTags.maxItems, 32);
  assert.equal(properties.skillTags.items.pattern, '^[A-Za-z0-9._:-]{1,128}$');
  assert.equal(properties.variants.minItems, 1);
  assert.deepEqual(properties.facts.items.properties.type.enum, [
    'aim',
    'context',
    'observation',
    'limitation',
    'boundary',
  ]);
  assert.deepEqual(properties.rules.items.properties.outcome.enum, [
    'PASS',
    'ACTION_REQUIRED',
    'CANNOT_ASSESS',
    'INCOMPLETE',
  ]);

  const fixture = readJson(path.join('fixtures', 'published-case-summary.json'));
  assert.equal(fixture.schema, 'evidrilo.case-summary');
  assert.equal(fixture.facts[0].type, 'observation');
  assert.equal(fixture.facts[1].type, 'limitation');
  assert.equal(fixture.rules[0].outcome, 'PASS');
  assert.equal(fixture.variants[0].id, 'CHALLENGE-1');
});

test('recommendation contract preserves status-specific response invariants', () => {
  const schema = readSchema('recommendation.v1.json');

  assert.deepEqual(schema.required, [
    'schema',
    'version',
    'status',
    'calculationVersion',
    'caseVersionId',
    'objective',
    'reasonCode',
    'evidenceReferences',
    'requestId',
  ]);
  assert.equal(schema.properties.calculationVersion.const, 'recommendation.v1');
  assert.deepEqual(schema.properties.status.enum, ['recommended', 'abstain']);
  assert.deepEqual(schema.properties.reasonCode.enum, [
    'START_HERE',
    'PRACTICE_ACTION_REQUIRED',
    'NEXT_PRACTICE',
    'NO_ELIGIBLE_CASE',
    'INSUFFICIENT_PROJECTION',
  ]);
  assert.equal(schema.allOf.length, 2);
});

test('case lifecycle audit contract is closed, bounded, and deletion-safe', () => {
  const schema = readSchema('case-lifecycle-audit.v1.json');
  const fixture = readJson(path.join('fixtures', 'case-lifecycle-audit.json'));

  assert.equal(schema.additionalProperties, false);
  assert.deepEqual(
    schema.required,
    ['schema', 'version', 'caseVersionId', 'events', 'truncated', 'requestId'],
  );
  assert.equal(schema.properties.events.maxItems, 128);
  assert.equal(schema.properties.events.items.additionalProperties, false);
  assert.equal(fixture.schema, 'evidrilo.case-lifecycle-audit');
  assert.equal(fixture.events[0].eventType, 'created');
  assert.equal(fixture.events[0].fromState, null);
  assert.equal(fixture.events[0].actorAccountId, null);
  assert.equal(fixture.events[1].eventType, 'transitioned');
  assert.equal(fixture.events[1].fromState, 'draft');
  assert.equal(fixture.events[1].toState, 'review');
  assert.equal(fixture.truncated, false);
  assertNoCredentialShapedFields(fixture);
});

test('RevenueCat architecture documentation does not overclaim dashboard state', () => {
  const repositoryRoot = path.resolve(root, '..');
  const architecture = fs.readFileSync(
    path.join(repositoryRoot, 'docs', 'architecture', 'revenuecat.md'),
    'utf8',
  );

  assert.match(architecture, /remaining dashboard price migration and purchase\/restore\/revoke matrix remain\s+owner gates/i);
  assert.match(architecture, /historical Test Store\s+observation is recorded/i);
  assert.match(architecture, /approved monthly\/yearly\s+product/i);
  assert.doesNotMatch(architecture, /authorized dashboard contains the Test Store catalog/i);
  assert.doesNotMatch(architecture, /The Test Store catalog is configured/i);
  assert.doesNotMatch(architecture, /The current Test Store observation is recorded/i);
});

test('account export streams database rows instead of aggregating an account-sized JSON value', () => {
  const accountExport = fs.readFileSync(
    path.join(repositoryRoot, 'platform', 'shared', 'AccountExportDataWriter.cs'),
    'utf8',
  );

  assert.doesNotMatch(accountExport, /\bjsonb_agg\s*\(/i);
  assert.match(accountExport, /ExecuteReaderAsync/);
  assert.match(accountExport, /Utf8JsonWriter/);
  assert.match(accountExport, /FlushAsync/);
  assert.match(accountExport, /"studentProjects"/);
  assert.match(accountExport, /"studentProjectRevisions"/);
  assert.doesNotMatch(accountExport, /"studentProjectCommands"/);
});

test('account export response cap matches the mobile transport limit', () => {
  const accountContracts = fs.readFileSync(
    path.join(repositoryRoot, 'platform', 'api', 'Account', 'AccountContracts.cs'),
    'utf8',
  );
  const accountTransport = fs.readFileSync(
    path.join(
      repositoryRoot,
      'modules',
      'application',
      'src',
      'commonMain',
      'kotlin',
      'dev',
      'nextgen',
      'mobile',
      'account',
      'AccountHttpTransport.kt',
    ),
    'utf8',
  );

  assert.match(accountContracts, /MaximumResponseBytes\s*=\s*128\s*\*\s*1024/);
  assert.match(accountContracts, /Status413PayloadTooLarge/);
  assert.match(accountContracts, /BoundedAccountExportStream/);
  assert.match(accountContracts, /CopyToAsync\(httpContext\.Response\.Body/);
  assert.match(accountTransport, /MAX_ACCOUNT_HTTP_BODY_BYTES:\s*Int\s*=\s*128\s*\*\s*1024/);
});

test('Project AI dispatch is bound to the saved consent generation and account deletion fence', () => {
  const consentStore = fs.readFileSync(
    path.join(repositoryRoot, 'platform', 'api', 'ProjectAi', 'ProjectAiConsentStore.cs'),
    'utf8',
  );
  const scaffoldEndpoint = fs.readFileSync(
    path.join(repositoryRoot, 'platform', 'api', 'ProjectAi', 'ProjectAiScaffoldEndpoints.cs'),
    'utf8',
  );

  assert.match(consentStore, /StillAuthorizesDispatch[\s\S]*initial\.Generation == current\.Generation/);
  assert.match(consentStore, /EnsureAccountNotDeletedAsync/);
  assert.match(consentStore, /account_deletion_tombstones/);
  assert.match(scaffoldEndpoint, /dispatchConsent[\s\S]*StillAuthorizesDispatch\(consent, dispatchConsent\)/);
  assert.match(scaffoldEndpoint, /PROJECT_AI_CONSENT_REQUIRED/);
});

test('notification preference writes share the account deletion fence', () => {
  const store = fs.readFileSync(
    path.join(repositoryRoot, 'platform/api/Notifications/NotificationStore.cs'),
    'utf8',
  );
  const fenceMigration = fs.readFileSync(
    path.join(repositoryRoot, 'platform/database/migrations/046_project_ai_consent_deletion_fence.sql'),
    'utf8',
  );

  assert.match(store, /PutOwnAsync[\s\S]*?LockAccountDeletionFenceAsync\(connection, transaction, accountId/);
  assert.match(store, /LockAccountDeletionFenceAsync[\s\S]*?pg_advisory_xact_lock\(hashtextextended\(@account_id::text, 0\)\)/);
  assert.match(store, /EnsureAccountNotDeletedAsync[\s\S]*?account_deletion_tombstones[\s\S]*?account_deletion_requests[\s\S]*?status = 'completed'/);
  assert.match(fenceMigration, /pg_advisory_xact_lock\(hashtextextended\(new\.account_id::text, 0\)\)/);
});

test('nullable response contracts keep null keys without changing unrelated API JSON', () => {
  const program = fs.readFileSync(path.join(repositoryRoot, 'platform/api/Program.cs'), 'utf8');
  assert.match(program, /DefaultIgnoreCondition\s*=\s*JsonIgnoreCondition\.WhenWritingNull/);
  for (const relativePath of [
    'platform/api/ProjectAi/ProjectAiConsentEndpoints.cs',
    'platform/api/ProjectAi/ProjectAiScaffoldEndpoints.cs',
    'platform/api/Notifications/NotificationEndpoints.cs',
  ]) {
    const source = fs.readFileSync(path.join(repositoryRoot, relativePath), 'utf8');
    assert.match(source, /ResponseJsonOptions[\s\S]*?DefaultIgnoreCondition\s*=\s*JsonIgnoreCondition\.Never/);
    assert.match(source, /Results\.Json\([\s\S]*?options:\s*ResponseJsonOptions/);
  }
});

test('account export v2 routes require idempotency and expose bounded metadata-only artifacts', () => {
  const manifest = readJson('routes.v1.json');
  const openapi = readJson('openapi/openapi.v1.json');
  const createRoute = manifest.routes.find((route) => route.method === 'POST' && route.path === '/v2/account/exports');
  const statusRoute = manifest.routes.find((route) => route.method === 'GET' && route.path === '/v2/account/exports/{exportId:guid}');
  const downloadRoute = manifest.routes.find((route) => route.method === 'GET' && route.path === '/v2/account/exports/{exportId:guid}/download');
  const deleteRoute = manifest.routes.find((route) => route.method === 'DELETE' && route.path === '/v2/account/exports/{exportId:guid}');
  const exportSchema = readSchema('account-export.v2.json');
  const jobSchema = readSchema('account-export-job.v2.json');

  assert.equal(createRoute?.authentication, 'supabase_bearer');
  assert.equal(createRoute?.successStatusCode, 202);
  assert.equal(createRoute?.headerParameters.find((parameter) => parameter.name === 'Idempotency-Key')?.required, true);
  assert.equal(statusRoute?.responseSchema, 'account-export-job.v2.json');
  assert.equal(downloadRoute?.responseSchema, 'account-export.v2.json');
  assert.equal(deleteRoute?.responseSchema, 'account-export-job.v2.json');
  assert.equal(
    openapi.paths['/v2/account/exports'].post.responses['202'].content[
      'application/vnd.evidrilo.account-export-job.v2+json'].schema.$ref,
    '#/components/schemas/account-export-job.v2',
  );
  assert.equal(
    openapi.paths['/v2/account/exports/{exportId}/download'].get.responses['2XX'].content[
      'application/vnd.evidrilo.account-export.v2+json'].schema.$ref,
    '#/components/schemas/account-export.v2',
  );
  assert.ok(exportSchema.properties.data.required.includes('projectAiConsent'));
  assert.ok(exportSchema.properties.data.required.includes('projectAiActivity'));
  assert.ok(exportSchema.properties.data.required.includes('aiConversationTurnRequests'));
  assert.equal(jobSchema.properties.artifactBytes.maximum, 67108864);
  for (const propertyName of ['prompt', 'response', 'requestHash', 'settlementHash', 'activeRequestHash']) {
    assert.equal(Object.hasOwn(exportSchema.properties.data.properties, propertyName), false);
    for (const metadataCollection of [
      'projectAiConsentEvents',
      'projectAiActivity',
      'aiConversationSessions',
      'aiConversationTurnRequests',
    ]) {
      const schema = exportSchema.properties.data.properties[metadataCollection];
      const itemProperties = schema.items?.properties ?? schema.properties;
      assert.equal(Object.hasOwn(itemProperties, propertyName), false);
    }
  }
});
