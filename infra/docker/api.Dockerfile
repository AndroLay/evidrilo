# syntax=docker/dockerfile:1

FROM mcr.microsoft.com/dotnet/sdk:10.0 AS build
WORKDIR /src

COPY platform/api/Evidrilo.Api.csproj platform/api/
RUN dotnet restore platform/api/Evidrilo.Api.csproj

COPY platform/api/ platform/api/
RUN dotnet publish platform/api/Evidrilo.Api.csproj \
    --configuration Release \
    --output /app/publish \
    --no-restore \
    /p:UseAppHost=false

FROM mcr.microsoft.com/dotnet/aspnet:10.0 AS runtime
WORKDIR /app
ENV ASPNETCORE_URLS=http://+:5080
EXPOSE 5080
USER $APP_UID

COPY --from=build /app/publish .
ENTRYPOINT ["dotnet", "Evidrilo.Api.dll"]
