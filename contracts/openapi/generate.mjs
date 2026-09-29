import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const directory = path.dirname(fileURLToPath(import.meta.url));
const contractsDirectory = path.dirname(directory);
const manifest = JSON.parse(fs.readFileSync(path.join(contractsDirectory, 'routes.v1.json'), 'utf8'));
const outputPath = path.join(directory, 'openapi.v1.json');
const checkOnly = process.argv.includes('--check');

if (process.argv.slice(2).some((argument) => argument !== '--check')) {
  process.stderr.write('Usage: node contracts/openapi/generate.mjs [--check]\n');
  process.exit(2);
}

const operationsByPath = new Map();
const schemaFiles = new Set(['http-errors.v1.json']);

function schemaName(fileName) {
  return fileName.replace(/\.json$/u, '');
}

function schemaReference(fileName) {
  return `#/components/schemas/${schemaName(fileName)}`;
}

function addRouteSchemas(route) {
  schemaFiles.add(route.responseSchema);
  if (route.requestSchema) schemaFiles.add(route.requestSchema);
  for (const variant of route.responseVariants ?? []) schemaFiles.add(variant.responseSchema);
}

function normalizePath(routePath) {
  return routePath.replace(/\{([A-Za-z][A-Za-z0-9]*):([a-z]+)\}/gu, '{$1}');
}

function pathParameters(routePath) {
  return [...routePath.matchAll(/\{([A-Za-z][A-Za-z0-9]*)(?::([a-z]+))?\}/gu)]
    .map(([, name, constraint]) => ({
      name,
      in: 'path',
      required: true,
      schema: constraint === 'guid'
        ? { type: 'string', format: 'uuid' }
        : constraint === 'int'
          ? { type: 'integer' }
          : { type: 'string' },
    }));
}

function operationSecurity(authentication) {
  switch (authentication) {
    case 'none':
      return [];
    case 'supabase_bearer':
      return [{ SupabaseBearer: [] }];
    case 'revenuecat_webhook':
      return [
        { RevenueCatWebhookAuthorization: [] },
        { RevenueCatWebhookSignature: [] },
      ];
    default:
      throw new Error(`Unsupported route authentication: ${authentication}`);
  }
}

function routeTag(routePath) {
  const segments = routePath.split('/').filter(Boolean);
  return segments[0] === 'v1' ? (segments[1] ?? 'api') : segments[0];
}

function operationId(method, routePath) {
  const suffix = routePath
    .split('/')
    .filter(Boolean)
    .map((segment) => segment.replace(/[{}:]/gu, '').replace(/[^A-Za-z0-9]+/gu, '_'))
    .filter(Boolean)
    .join('_');
  return `${method.toLowerCase()}_${suffix}`;
}

function rewriteDefinitionRefs(value, name) {
  if (Array.isArray(value)) {
    value.forEach((entry) => rewriteDefinitionRefs(entry, name));
    return;
  }
  if (!value || typeof value !== 'object') return;

  for (const [key, nested] of Object.entries(value)) {
    if (key === '$ref' && typeof nested === 'string' && nested.startsWith('#/$defs/')) {
      value[key] = `#/components/schemas/${name}${nested.slice(1)}`;
    } else if (key === '$ref' && typeof nested === 'string' && !nested.startsWith('#')) {
      const match = /^([^#]+\.json)(#.*)?$/u.exec(nested);
      if (match) {
        const dependency = path.posix.basename(match[1]);
        if (!schemaFiles.has(dependency)) {
          throw new Error(`Undiscovered schema dependency: ${dependency}`);
        }
        value[key] = `#/components/schemas/${schemaName(dependency)}${(match[2] ?? '').replace(/^#/u, '')}`;
      }
    } else {
      rewriteDefinitionRefs(nested, name);
    }
  }
}

function referencedSchemaFiles(value) {
  const files = new Set();
  const visit = (node) => {
    if (Array.isArray(node)) {
      node.forEach(visit);
      return;
    }
    if (!node || typeof node !== 'object') return;
    if (typeof node.$ref === 'string' && !node.$ref.startsWith('#')) {
      const match = /^([^#]+\.json)(?:#.*)?$/u.exec(node.$ref);
      if (match) files.add(path.posix.basename(match[1]));
    }
    Object.values(node).forEach(visit);
  };
  visit(value);
  return files;
}

for (const route of manifest.routes) addRouteSchemas(route);

const pendingSchemaFiles = [...schemaFiles];
while (pendingSchemaFiles.length > 0) {
  const fileName = pendingSchemaFiles.pop();
  const schemaPath = path.join(contractsDirectory, 'schemas', fileName);
  const schema = JSON.parse(fs.readFileSync(schemaPath, 'utf8'));
  for (const dependency of referencedSchemaFiles(schema)) {
    if (schemaFiles.has(dependency)) continue;
    schemaFiles.add(dependency);
    pendingSchemaFiles.push(dependency);
  }
}

function loadSchema(fileName) {
  const schemaPath = path.join(contractsDirectory, 'schemas', fileName);
  const schema = JSON.parse(fs.readFileSync(schemaPath, 'utf8'));
  rewriteDefinitionRefs(schema, schemaName(fileName));
  return schema;
}

for (const route of manifest.routes) {
  addRouteSchemas(route);
  const routePath = normalizePath(route.path);
  if (!operationsByPath.has(routePath)) operationsByPath.set(routePath, {});

  const responseContent = {
    'application/json': { schema: { $ref: schemaReference(route.responseSchema) } },
  };
  for (const variant of route.responseVariants ?? []) {
    responseContent[variant.mediaType] = { schema: { $ref: schemaReference(variant.responseSchema) } };
  }

  const operation = {
    operationId: operationId(route.method, route.path),
    tags: [routeTag(route.path)],
    security: operationSecurity(route.authentication),
    responses: {
      '2XX': {
        description: 'Successful response.',
        content: responseContent,
      },
      default: {
        description: 'API error response.',
        content: {
          'application/json': { schema: { $ref: schemaReference('http-errors.v1.json') } },
        },
      },
    },
    'x-evidrilo-test': {
      file: route.testFile,
      anchor: route.testAnchor,
    },
  };

  if (route.requestSchema) {
    operation.requestBody = {
      required: true,
      content: {
        'application/json': { schema: { $ref: schemaReference(route.requestSchema) } },
      },
    };
  }
  const operationParameters = [
    ...(route.queryParameters ?? []).map((parameter) => ({
      name: parameter.name,
      in: 'query',
      required: parameter.required,
      ...(parameter.description ? { description: parameter.description } : {}),
      schema: parameter.schema,
    })),
    ...(route.headerParameters ?? []).map((parameter) => ({
      name: parameter.name,
      in: 'header',
      required: parameter.required,
      ...(parameter.description ? { description: parameter.description } : {}),
      schema: parameter.schema,
    })),
  ];
  if (operationParameters.length > 0) operation.parameters = operationParameters;

  operationsByPath.get(routePath)[route.method.toLowerCase()] = operation;
  operationsByPath.get(routePath).parameters = pathParameters(route.path);
}

const schemas = Object.fromEntries(
  [...schemaFiles].sort().map((fileName) => [schemaName(fileName), loadSchema(fileName)]),
);
const paths = Object.fromEntries(
  [...operationsByPath.entries()].sort(([left], [right]) => left.localeCompare(right)),
);
const document = {
  openapi: '3.1.0',
  info: {
    title: 'Evidrilo API',
    version: '1.0.0',
    description: 'Public contract for the versioned Evidrilo API routes.',
  },
  paths,
  components: {
    securitySchemes: {
      SupabaseBearer: {
        type: 'http',
        scheme: 'bearer',
        bearerFormat: 'JWT',
      },
      RevenueCatWebhookAuthorization: {
        type: 'apiKey',
        in: 'header',
        name: 'Authorization',
      },
      RevenueCatWebhookSignature: {
        type: 'apiKey',
        in: 'header',
        name: 'X-RevenueCat-Webhook-Signature',
      },
    },
    schemas,
  },
};
const generated = `${JSON.stringify(document, null, 2)}\n`;

if (checkOnly) {
  let current;
  try {
    current = fs.readFileSync(outputPath, 'utf8');
  } catch {
    process.stderr.write('OpenAPI artifact is missing; run node contracts/openapi/generate.mjs.\n');
    process.exit(1);
  }
  if (current !== generated) {
    process.stderr.write('OpenAPI artifact is stale; run node contracts/openapi/generate.mjs.\n');
    process.exit(1);
  }
} else {
  fs.mkdirSync(directory, { recursive: true });
  fs.writeFileSync(outputPath, generated);
}
