using System.Globalization;
using Npgsql;

namespace Evidrilo.Platform;

public static class DatabaseConnectionStringParser
{
    public static string? Normalize(string? value, bool requireEncryptedTransport)
    {
        if (string.IsNullOrWhiteSpace(value))
            return null;

        try
        {
            var raw = value.Trim();
            var builder = raw.StartsWith("postgresql://", StringComparison.OrdinalIgnoreCase)
                || raw.StartsWith("postgres://", StringComparison.OrdinalIgnoreCase)
                ? ParseUri(raw, requireEncryptedTransport)
                : new NpgsqlConnectionStringBuilder(raw);

            if (string.IsNullOrWhiteSpace(builder.Host)
                || string.IsNullOrWhiteSpace(builder.Database)
                || (requireEncryptedTransport && string.IsNullOrWhiteSpace(builder.Username)))
            {
                throw InvalidConfiguration();
            }

            if (requireEncryptedTransport
                && builder.SslMode is not (SslMode.Require or SslMode.VerifyCA or SslMode.VerifyFull))
            {
                throw InvalidConfiguration();
            }

            return builder.ConnectionString;
        }
        catch (Exception exception) when (
            exception is ArgumentException or FormatException or OverflowException)
        {
            // Do not include the supplied connection string in an exception or log.
            throw InvalidConfiguration();
        }
    }

    private static NpgsqlConnectionStringBuilder ParseUri(string raw, bool requireEncryptedTransport)
    {
        if (!Uri.TryCreate(raw, UriKind.Absolute, out var uri)
            || (uri.Scheme != "postgresql" && uri.Scheme != "postgres")
            || string.IsNullOrWhiteSpace(uri.Host)
            || uri.Fragment.Length > 0)
        {
            throw InvalidConfiguration();
        }

        var databasePath = uri.GetComponents(UriComponents.Path, UriFormat.UriEscaped).Trim('/');
        if (databasePath.Length == 0 || databasePath.Contains('/', StringComparison.Ordinal))
            throw InvalidConfiguration();

        var builder = new NpgsqlConnectionStringBuilder
        {
            Host = uri.IdnHost,
            Port = uri.IsDefaultPort || uri.Port < 1 ? 5432 : uri.Port,
            Database = Decode(databasePath),
            SslMode = requireEncryptedTransport ? SslMode.Require : SslMode.Prefer,
        };

        var userInfo = uri.GetComponents(UriComponents.UserInfo, UriFormat.UriEscaped);
        if (userInfo.Length > 0)
        {
            var separator = userInfo.IndexOf(':');
            builder.Username = Decode(separator < 0 ? userInfo : userInfo[..separator]);
            if (separator >= 0)
                builder.Password = Decode(userInfo[(separator + 1)..]);
        }

        ApplyQuery(uri.Query, builder);
        return builder;
    }

    private static void ApplyQuery(string query, NpgsqlConnectionStringBuilder builder)
    {
        var seenKeys = new HashSet<string>(StringComparer.OrdinalIgnoreCase);
        foreach (var part in query.TrimStart('?').Split('&', StringSplitOptions.RemoveEmptyEntries))
        {
            var separator = part.IndexOf('=');
            var key = Decode(separator < 0 ? part : part[..separator]);
            var value = separator < 0 ? string.Empty : Decode(part[(separator + 1)..]);
            if (!seenKeys.Add(key))
                throw InvalidConfiguration();

            switch (key.ToLowerInvariant())
            {
                case "sslmode":
                    builder.SslMode = ParseSslMode(value);
                    break;
                case "sslrootcert":
                    builder.RootCertificate = value;
                    break;
                case "application_name":
                    builder.ApplicationName = value;
                    break;
                case "connect_timeout":
                    if (!int.TryParse(value, NumberStyles.None, CultureInfo.InvariantCulture, out var timeout)
                        || timeout is < 1 or > 300)
                    {
                        throw InvalidConfiguration();
                    }
                    builder.Timeout = timeout;
                    break;
                case "options":
                    builder.Options = value;
                    break;
                default:
                    throw InvalidConfiguration();
            }
        }
    }

    private static SslMode ParseSslMode(string value) => value.ToLowerInvariant() switch
    {
        "disable" => SslMode.Disable,
        "allow" => SslMode.Allow,
        "prefer" => SslMode.Prefer,
        "require" => SslMode.Require,
        "verify-ca" => SslMode.VerifyCA,
        "verify-full" => SslMode.VerifyFull,
        _ => throw InvalidConfiguration(),
    };

    private static string Decode(string value) => Uri.UnescapeDataString(value);

    private static FormatException InvalidConfiguration() =>
        new("The PostgreSQL connection configuration is invalid or violates the required security policy.");
}
