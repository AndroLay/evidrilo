using System.Text.RegularExpressions;

namespace Evidrilo.Api.Tests;

public sealed class NoSecretOutputTests : IClassFixture<ApiFactory>
{
    private readonly HttpClient client;

    public NoSecretOutputTests(ApiFactory factory)
    {
        client = factory.CreateClient();
    }

    [Fact]
    public async Task Anonymous_error_body_contains_no_credential_value_patterns()
    {
        using var response = await client.GetAsync("/v1/account/me", cancellationToken: TestContext.Current.CancellationToken);
        var body = await response.Content.ReadAsStringAsync(cancellationToken: TestContext.Current.CancellationToken);

        Assert.DoesNotContain("eyJ", body, StringComparison.Ordinal);
        Assert.DoesNotContain("service_role", body, StringComparison.OrdinalIgnoreCase);
        Assert.DoesNotContain("refresh_token", body, StringComparison.OrdinalIgnoreCase);
        Assert.DoesNotContain("password", body, StringComparison.OrdinalIgnoreCase);
        Assert.DoesNotMatch(new Regex("-----BEGIN [A-Z ]+ KEY-----", RegexOptions.CultureInvariant), body);
    }

    [Fact]
    public void Public_example_configuration_contains_placeholders_only()
    {
        var root = FindRepositoryRoot();
        var example = File.ReadAllText(Path.Combine(root, "platform", "api", ".env.example"));

        Assert.Contains("your-project.supabase.co", example, StringComparison.Ordinal);
        Assert.Contains("replace-with-local-publishable-key", example, StringComparison.Ordinal);
        Assert.DoesNotContain("eyJ", example, StringComparison.Ordinal);
        Assert.DoesNotContain("service_role", example, StringComparison.OrdinalIgnoreCase);
    }

    private static string FindRepositoryRoot()
    {
        var configuredRoot = Environment.GetEnvironmentVariable("EVIDRILO_REPO_ROOT");
        if (!string.IsNullOrWhiteSpace(configuredRoot)
            && File.Exists(Path.Combine(configuredRoot, "settings.gradle.kts")))
            return Path.GetFullPath(configuredRoot);

        var directory = new DirectoryInfo(AppContext.BaseDirectory);
        while (directory is not null)
        {
            if (File.Exists(Path.Combine(directory.FullName, "settings.gradle.kts"))) return directory.FullName;
            directory = directory.Parent;
        }

        throw new DirectoryNotFoundException("Repository root was not found from the test output directory.");
    }
}
