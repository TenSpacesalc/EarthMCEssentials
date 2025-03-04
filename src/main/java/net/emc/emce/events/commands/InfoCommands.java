package net.emc.emce.events.commands;

import com.google.gson.JsonObject;
import com.mojang.brigadier.arguments.StringArgumentType;

import net.emc.emce.EarthMCEssentials;
import net.emc.emce.caches.NationDataCache;
import net.emc.emce.caches.TownDataCache;
import net.emc.emce.object.Resident;
import net.emc.emce.object.Translation;
import net.emc.emce.utils.Messaging;
import net.fabricmc.fabric.api.client.command.v1.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v1.FabricClientCommandSource;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.platform.fabric.FabricClientAudiences;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.minecraft.client.MinecraftClient;

import java.util.Locale;

public record InfoCommands(EarthMCEssentials instance) {

    public void register() {
        registerTownInfoCommand();
        registerNationInfoCommand();
    }

    public void registerTownInfoCommand() {
        ClientCommandManager.DISPATCHER.register(ClientCommandManager.literal("towninfo").then(
                ClientCommandManager.argument("townName", StringArgumentType.string()).executes(c -> {
                    String townName = StringArgumentType.getString(c, "townName");
                    trySendTown(townName);
                    return 1;
                })
        ).executes(c -> {
            FabricClientCommandSource source = c.getSource();
            Resident clientResident = instance.getClientResident();

            if (clientResident == null) {
                Messaging.send(Translation.of("text_shared_notregistered", MinecraftClient.getInstance().player.getName()));
                return 1;
            }

            String townName = clientResident.getTown();
            if (townName.equals("") || townName.equals("No Town"))
                Messaging.send(Translation.of("text_towninfo_not_registered"));
            else trySendTown(townName);

            return 1;
        }));
    }

    public void registerNationInfoCommand() {
        ClientCommandManager.DISPATCHER.register(ClientCommandManager.literal("nationinfo").then(
                ClientCommandManager.argument("nationName", StringArgumentType.string()).executes(c -> {
                    String nationName = StringArgumentType.getString(c, "nationName");
                    trySendNation(nationName);

                    return 1;
                })
        ).executes(c -> {
            FabricClientCommandSource source = c.getSource();
            Resident clientResident = instance.getClientResident();

            if (clientResident == null) {
                Messaging.sendPrefixed(Translation.of("text_shared_notregistered", MinecraftClient.getInstance().player.getName()));
                return 1;
            }

            String nationName = clientResident.getNation();
            if (nationName.equals("") || nationName.equals("No Nation"))
                Messaging.sendPrefixed(Translation.of("text_nationinfo_not_registered"));
            else trySendNation(nationName);

            return 1;
        }));
    }

    private void trySendTown(String townName) {
        NamedTextColor townTextColour = instance.getConfig().commands.townInfoTextColour.named();
        TownDataCache.INSTANCE.getCache().thenAccept(towns -> {
            JsonObject townObject = towns.get(townName.toLowerCase(Locale.ROOT));

            if (townObject == null) {
                Component townArg = Component.text(townName).color(townTextColour);
                Messaging.sendPrefixed(Messaging.create("text_towninfo_err", NamedTextColor.RED, townArg));
            }
            else sendTownInfo(townObject, townTextColour);
        });
    }

    private void trySendNation(String nationName) {
        NamedTextColor nationTextColour = instance.getConfig().commands.nationInfoTextColour.named();
        NationDataCache.INSTANCE.getCache().thenAccept(nations -> {
            JsonObject nationObject = nations.get(nationName.toLowerCase(Locale.ROOT));

            if (nationObject == null) {
                Component nationArg = Component.text(nationName).color(nationTextColour);
                Messaging.sendPrefixed(Messaging.create("text_nationinfo_err", NamedTextColor.RED, nationArg));
            }
            else sendNationInfo(nationObject, nationTextColour);
        });
    }

    private void sendTownInfo(JsonObject townObject, NamedTextColor colour) {
        Audience audience = FabricClientAudiences.of().audience();

        audience.sendMessage(Translation.of("text_towninfo_header", townObject.get("name").getAsString()).color(colour));
        audience.sendMessage(Translation.of("text_towninfo_mayor", townObject.get("mayor").getAsString()).color(colour));
        audience.sendMessage(Translation.of("text_shared_area", townObject.get("area").getAsString()).color(colour));
        audience.sendMessage(Translation.of("text_shared_residents", townObject.get("residents").getAsJsonArray().size()).color(colour));
        audience.sendMessage(Translation.of("text_towninfo_location", townObject.get("x").getAsString(), townObject.get("z").getAsString()).color(colour));
    }

    private void sendNationInfo(JsonObject nationObject, NamedTextColor colour) {
        Audience audience = FabricClientAudiences.of().audience();

        audience.sendMessage(Translation.of("text_nationinfo_header", nationObject.get("name").getAsString()).color(colour));
        audience.sendMessage(Translation.of("text_nationinfo_king", nationObject.get("king").getAsString()).color(colour));
        audience.sendMessage(Translation.of("text_nationinfo_capital", nationObject.get("capitalName").getAsString()).color(colour));
        audience.sendMessage(Translation.of("text_shared_area", nationObject.get("area").getAsString()).color(colour));
        audience.sendMessage(Translation.of("text_shared_residents", nationObject.get("residents").getAsJsonArray().size()).color(colour));
        audience.sendMessage(Translation.of("text_nationinfo_towns", nationObject.get("towns").getAsJsonArray().size()).color(colour));
    }
}