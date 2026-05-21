FROM mcr.microsoft.com/dotnet/sdk:7.0 AS build
WORKDIR /src

COPY ["MarketplaceOnDapr.sln", "./"]
COPY ["SellerMS/SellerMS.csproj", "SellerMS/"]
COPY ["Common/Common.csproj", "Common/"]

RUN dotnet restore "SellerMS/SellerMS.csproj"

COPY . .

WORKDIR "/src/SellerMS"
RUN dotnet publish -c Release -o /app

FROM mcr.microsoft.com/dotnet/aspnet:7.0
WORKDIR /app
COPY --from=build /app .

ENTRYPOINT ["dotnet", "SellerMS.dll"]