using Evidrilo.Api.Common;

namespace Evidrilo.Api.Billing;

public static class BillingEndpoints
{
    private const int MaxBodyBytes = 128 * 1024;

    public static IEndpointRouteBuilder MapBillingEndpoints(this IEndpointRouteBuilder endpoints)
    {
        endpoints.MapPost(
            "/v1/billing/webhook",
            async (HttpContext context, BillingWebhookService service, CancellationToken cancellationToken) =>
            {
                if (context.Request.ContentLength is > MaxBodyBytes)
                {
                    return Results.Json(
                        ApiErrors.Create(context, "BILLING_PAYLOAD_TOO_LARGE", "The billing event is too large."),
                        statusCode: StatusCodes.Status413PayloadTooLarge);
                }

                var body = await BillingRequestBodyReader.ReadAsync(
                    context.Request.Body,
                    MaxBodyBytes,
                    cancellationToken);
                if (body is null)
                {
                    return Results.Json(
                        ApiErrors.Create(context, "BILLING_PAYLOAD_TOO_LARGE", "The billing event is too large."),
                        statusCode: StatusCodes.Status413PayloadTooLarge);
                }

                var outcome = await service.ProcessAsync(
                    context.Request.Headers.Authorization.FirstOrDefault(),
                    context.Request.Headers["X-RevenueCat-Webhook-Signature"].FirstOrDefault(),
                    body,
                    cancellationToken);
                return Results.Ok(new BillingWebhookResult(
                    "evidrilo.billing-webhook-result",
                    "1",
                    outcome,
                    RequestIdMiddleware.Get(context)));
            })
            .RequireRateLimiting("billing-webhook");

        return endpoints;
    }
}

internal static class BillingRequestBodyReader
{
    private const int BufferSize = 8 * 1024;

    public static async Task<byte[]?> ReadAsync(
        Stream body,
        int maxBytes,
        CancellationToken cancellationToken)
    {
        ArgumentNullException.ThrowIfNull(body);
        ArgumentOutOfRangeException.ThrowIfNegative(maxBytes);

        using var output = new MemoryStream(Math.Min(maxBytes, BufferSize));
        var buffer = new byte[Math.Min(BufferSize, Math.Max(1, maxBytes))];
        var total = 0;
        while (true)
        {
            var remaining = maxBytes - total;
            var readLength = Math.Min(buffer.Length, remaining + 1);
            var read = await body.ReadAsync(buffer.AsMemory(0, readLength), cancellationToken);
            if (read == 0) return output.ToArray();
            if (read > remaining) return null;

            output.Write(buffer, 0, read);
            total += read;
        }
    }
}
