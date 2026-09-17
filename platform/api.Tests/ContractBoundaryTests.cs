using System.Text.Json;

namespace Evidrilo.Api.Tests;

public sealed class ContractBoundaryTests
{
    [Fact]
    public void Public_fixtures_do_not_contain_credential_shaped_fields()
    {
        var root = FindRepositoryRoot();
        var fixtureDirectory = Path.Combine(root, "contracts", "fixtures");
        var forbidden = new HashSet<string>(StringComparer.OrdinalIgnoreCase)
        {
            "password", "access_token", "accessToken", "refresh_token", "refreshToken",
            "service_role", "serviceRole", "private_key", "privateKey", "api_key", "apiKey",
            "raw_provider_payload", "rawProviderPayload", "raw_claims", "rawClaims",
        };

        foreach (var file in Directory.EnumerateFiles(fixtureDirectory, "*.json"))
        {
            using var document = JsonDocument.Parse(File.ReadAllText(file));
            AssertNoForbiddenProperty(document.RootElement, forbidden, file);
        }
    }

    [Fact]
    public void Versioned_schemas_are_closed_at_the_response_root()
    {
        var root = FindRepositoryRoot();
        var schemaDirectory = Path.Combine(root, "contracts", "schemas");

        var files = Directory.EnumerateFiles(schemaDirectory, "*.v1.json")
            .OrderBy(path => path, StringComparer.Ordinal)
            .ToArray();
        Assert.NotEmpty(files);

        foreach (var file in files)
        {
            using var document = JsonDocument.Parse(File.ReadAllText(file));
            var json = document.RootElement;
            Assert.Equal("https://json-schema.org/draft/2020-12/schema", json.GetProperty("$schema").GetString());
            Assert.Equal(JsonValueKind.Object, json.ValueKind);
            Assert.Equal("object", json.GetProperty("type").GetString());
            Assert.False(json.GetProperty("additionalProperties").GetBoolean());
            Assert.StartsWith("https://evidrilo.dev/contracts/", json.GetProperty("$id").GetString());
        }
    }

    private static string FindRepositoryRoot()
    {
        var directory = new DirectoryInfo(AppContext.BaseDirectory);
        while (directory is not null)
        {
            if (File.Exists(Path.Combine(directory.FullName, "settings.gradle.kts"))) return directory.FullName;
            directory = directory.Parent;
        }

        throw new DirectoryNotFoundException("Repository root was not found from the test output directory.");
    }

    private static void AssertNoForbiddenProperty(
        JsonElement element,
        ISet<string> forbidden,
        string file)
    {
        if (element.ValueKind == JsonValueKind.Object)
        {
            foreach (var property in element.EnumerateObject())
            {
                Assert.DoesNotContain(property.Name, forbidden, StringComparer.OrdinalIgnoreCase);
                AssertNoForbiddenProperty(property.Value, forbidden, file);
            }
        }
        else if (element.ValueKind == JsonValueKind.Array)
        {
            foreach (var item in element.EnumerateArray()) AssertNoForbiddenProperty(item, forbidden, file);
        }
    }
}
