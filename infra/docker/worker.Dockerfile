# syntax=docker/dockerfile:1

FROM mcr.microsoft.com/dotnet/sdk:10.0 AS build
WORKDIR /src

COPY version.props Directory.Build.props ./
COPY platform/worker/Evidrilo.Worker.csproj platform/worker/
RUN dotnet restore platform/worker/Evidrilo.Worker.csproj

COPY platform/worker/ platform/worker/
RUN dotnet publish platform/worker/Evidrilo.Worker.csproj \
    --configuration Release \
    --output /app/publish \
    --no-restore \
    /p:UseAppHost=false

FROM mcr.microsoft.com/dotnet/runtime:10.0 AS runtime
ARG EVIDRILO_VERSION=dev
LABEL org.opencontainers.image.title="Evidrilo Worker" \
      org.opencontainers.image.version="$EVIDRILO_VERSION"
WORKDIR /app
USER $APP_UID

COPY --from=build /app/publish .
ENTRYPOINT ["dotnet", "Evidrilo.Worker.dll"]
