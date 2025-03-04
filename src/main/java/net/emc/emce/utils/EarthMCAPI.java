package net.emc.emce.utils;

import com.google.gson.*;
import net.emc.emce.config.ModConfig;
import net.emc.emce.object.*;
import net.emc.emce.object.exception.APIException;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

import static net.emc.emce.EarthMCEssentials.instance;

public class EarthMCAPI {
    private static final HttpClient client = HttpClient.newHttpClient();
    private static final ModConfig config = ModConfig.instance();
    public static final Pattern urlSchemePattern = Pattern.compile("^[a-z][a-z0-9+\\-.]*://");

    public static APIData auroraData = new APIData();
    public static APIData novaData = new APIData();
    public static JsonObject player = new JsonObject();

    public static CompletableFuture<JsonArray> getTownless() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return (JsonArray) JsonParser.parseString(getURL(getRoute(APIRoute.TOWNLESS)));
            } catch (APIException e) {
                Messaging.sendDebugMessage(e.getMessage(), e);
                return new JsonArray();
            }
        });
    }

    public static CompletableFuture<JsonArray> getNearby() {
        return getNearby(config.nearby.xBlocks, config.nearby.zBlocks);
    }

    public static CompletableFuture<JsonArray> getNearby(int xBlocks, int zBlocks) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                MinecraftClient client = MinecraftClient.getInstance();
                ClientPlayerEntity player = client.player;

                if (player != null) {
                    if (!player.getEntityWorld().getDimension().isBedWorking())
                        return new JsonArray();

                    JsonArray array = (JsonArray) JsonParser.parseString(getURL(getRoute(APIRoute.NEARBY) +
                            (int) player.getX() + "/" +
                            (int) player.getZ() + "/" +
                            xBlocks + "/" + zBlocks));

                    for (int i = 0; i < array.size(); i++) {
                        JsonObject currentObj = (JsonObject) array.get(i);
                        if (currentObj.get("name").getAsString().equals(client.player.getName().asString()))
                            array.remove(i);
                    }
                    return array;
                } else
                    return instance().getNearbyPlayers();
            } catch (APIException e) {
                Messaging.sendDebugMessage(e.getMessage(), e);
                return instance().getNearbyPlayers();
            }
        });
    }

    public static CompletableFuture<Resident> getResident(String residentName) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return new Resident((JsonObject) JsonParser.parseString(getURL(getRoute(APIRoute.RESIDENTS) + residentName)));
            } catch (APIException e) {
                Messaging.sendDebugMessage(e.getMessage(), e);
                return new Resident(residentName);
            }
        });
    }

    public static CompletableFuture<JsonElement> getOnlinePlayer(String playerName) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return JsonParser.parseString(getURL(getRoute(APIRoute.ONLINE_PLAYERS) + playerName));
            } catch (APIException e) {
                System.out.println(e.getMessage());
                Messaging.sendDebugMessage(e.getMessage(), e);
                return new JsonObject();
            }
        });
    }

    public static CompletableFuture<JsonArray> getTowns() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return (JsonArray) JsonParser.parseString(getURL(getRoute(APIRoute.TOWNS)));
            } catch (APIException e) {
                Messaging.sendDebugMessage(e.getMessage(), e);
                return new JsonArray();
            }
        });
    }

    public static CompletableFuture<JsonArray> getNations() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return (JsonArray) JsonParser.parseString(getURL(getRoute(APIRoute.NATIONS)));
            } catch (APIException e) {
                Messaging.sendDebugMessage(e.getMessage(), e);
                return new JsonArray();
            }
        });
    }

    public static CompletableFuture<NewsData> getNews() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return new NewsData((JsonObject) JsonParser.parseString(getURL(getRoute(APIRoute.NEWS))));
            } catch (APIException e) {
                Messaging.sendDebugMessage(e.getMessage(), e);
                return new NewsData();
            }
        });
    }

    public static CompletableFuture<JsonArray> getAlliances() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return (JsonArray) JsonParser.parseString(getURL(getRoute(APIRoute.ALLIANCES)));
            } catch (APIException e) {
                Messaging.sendDebugMessage(e.getMessage(), e);
                return new JsonArray();
            }
        });
    }

    public static CompletableFuture<APIData> fetchNova() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return new APIData((JsonObject) JsonParser.parseString(
                    getURL("https://raw.githubusercontent.com/EarthMC-Stats/EarthMCEssentials" +
                           "/main/src/main/resources/api.json")));
            } catch (APIException e) {
                Messaging.sendDebugMessage(e.getMessage(), e);
                return new APIData();
            }
        });
    }

    public static CompletableFuture<APIData> fetchAurora() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return new APIData((JsonObject) JsonParser.parseString(
                        getURL("https://raw.githubusercontent.com/EarthMC-Stats/EarthMCEssentials" +
                               "/main/src/main/resources/aurora_api.json")));
            } catch (APIException e) {
                Messaging.sendDebugMessage(e.getMessage(), e);
                return new APIData();
            }
        });
    }

    public static String getRoute(APIRoute routeType) {
        String route;
        APIData data;

        if (instance().mapName.equals("aurora")) data = auroraData;
        else data = novaData;

        switch(routeType) {
            case TOWNLESS -> route = data.routes.townless;
            case NATIONS -> route = data.routes.nations;
            case RESIDENTS -> route = data.routes.residents;
            case ALL_PLAYERS -> route = data.routes.allPlayers;
            case ONLINE_PLAYERS -> route = data.routes.onlinePlayers;
            case TOWNS -> route = data.routes.towns;
            case ALLIANCES -> route = data.routes.alliances;
            case NEARBY -> route = data.routes.nearby;
            case NEWS -> route = data.routes.news;
            default -> throw new IllegalStateException("Unexpected value: " + routeType);
        }

        route = data.getDomain() + route + "/";
        Messaging.sendDebugMessage("GETTING ROUTE - " + route);

        return route;
    }

    public static String clientName() {
        return MinecraftClient.getInstance().player.getName().asString();
    }

    public static void fetchMaps() {
        fetchAurora().thenAccept(data -> auroraData = data);
        fetchNova().thenAccept(data -> {
            novaData = data;
            instance().scheduler().initMap();
        });
    }

    public static boolean playerOnline(String map) {
        instance().mapName = map;

        JsonObject player = (JsonObject) getOnlinePlayer(clientName()).join();
        System.out.println("Has name: " + player.has("name"));

        boolean online = player.has("name") && player.get("name").getAsString().equals(clientName());

        System.out.println("Online in " + map + ": " + online);
        return online;
    }

    private static String getURL(String urlString) throws APIException {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(urlString))
                    .header("Accept", "application/json")
                    .timeout(Duration.ofSeconds(5))
                    .GET().build();

            final HttpResponse<String> response;
            try {
                response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            } catch (HttpTimeoutException e) {
                throw new APIException("API did not return any data after 5 seconds for URL '" + urlString + "'.");
            }

            System.out.println(response.body());
            if (response.statusCode() != 200 && response.statusCode() != 304)
                throw new APIException("API returned response code " + response.statusCode() + " for URL: " + urlString);

            return response.body();
        } catch (Exception e) {
            throw new APIException(e.getMessage());
        }
    }
}