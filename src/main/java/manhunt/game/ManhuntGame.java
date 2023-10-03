package manhunt.game;

import net.minecraft.advancement.AdvancementEntry;
import net.minecraft.advancement.AdvancementProgress;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.network.packet.s2c.play.PlaySoundS2CPacket;
import net.minecraft.network.packet.s2c.play.SubtitleS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.GameMode;
import net.minecraft.world.GameRules;
import net.minecraft.world.Heightmap;

import java.util.Objects;

import static manhunt.Manhunt.lobbyRegistryKey;
import static manhunt.config.ManhuntConfig.hunterFreeze;
import static manhunt.game.ManhuntState.*;

public class ManhuntGame {

    public static ManhuntState state;

    public static void state(ManhuntState state, MinecraftServer server) {
        server.setMotd(state.getColor() + "[" + state.getMotd() + "]§f Minecraft MANHUNT");
        ManhuntGame.state = state;
    }

    public static void start(MinecraftServer server) {
        ManhuntGame.state(PLAYING, server);

        var lobbyWorld = server.getWorld(lobbyRegistryKey);
        lobbyWorld.getChunkManager().removePersistentTickets();

        var world = server.getOverworld();
        world.setTimeOfDay(0);

        server.getGameRules().get(GameRules.ANNOUNCE_ADVANCEMENTS).set(true, server);
        server.getGameRules().get(GameRules.DO_DAYLIGHT_CYCLE).set(true, server);
        server.getGameRules().get(GameRules.DO_FIRE_TICK).set(true, server);
        server.getGameRules().get(GameRules.DO_INSOMNIA).set(true, server);
        server.getGameRules().get(GameRules.DO_MOB_SPAWNING).set(true, server);
        server.getGameRules().get(GameRules.DO_WEATHER_CYCLE).set(true, server);
        server.getGameRules().get(GameRules.FALL_DAMAGE).set(true, server);
        server.getGameRules().get(GameRules.RANDOM_TICK_SPEED).set(3, server);
        server.getGameRules().get(GameRules.SHOW_DEATH_MESSAGES).set(true, server);
        server.getGameRules().get(GameRules.SPAWN_RADIUS).set(10, server);

        server.setPvpEnabled(true);

        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            player.teleport(world, findSpawnPos(world).getX(), findSpawnPos(world).getY(), findSpawnPos(world).getZ(), 0, 0);
            player.clearStatusEffects();
            player.getInventory().clear();
            player.setFireTicks(0);
            player.setOnFire(false);
            player.setHealth(20);
            player.getHungerManager().setFoodLevel(20);
            player.getHungerManager().setSaturationLevel(5);
            player.getHungerManager().setExhaustion(0);

            for (AdvancementEntry advancement : server.getAdvancementLoader().getAdvancements()) {
                AdvancementProgress progress = player.getAdvancementTracker().getProgress(advancement);
                for(String criteria : progress.getObtainedCriteria()) {
                    player.getAdvancementTracker().revokeCriterion(advancement, criteria);
                }
            }

            updateGameMode(player);

            player.networkHandler.sendPacket(new TitleS2CPacket(Text.translatable("manhunt.title.game")));
            player.networkHandler.sendPacket(new SubtitleS2CPacket(Text.translatable("manhunt.title.start")));
            player.networkHandler.sendPacket(
                    new PlaySoundS2CPacket(
                        SoundEvents.BLOCK_NOTE_BLOCK_PLING,
                        SoundCategory.BLOCKS,
                        player.getX(),
                        player.getY(),
                        player.getZ(),
                        0.5f, 2f, 0
                    )
            );

            if (player.getScoreboardTeam().getName().equals("hunters")) {
                if (hunterFreeze != 0) {
                    player.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, hunterFreeze * 20, 255, false, true));
                    player.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, hunterFreeze * 20, 255, false, false));
                    player.addStatusEffect(new StatusEffectInstance(StatusEffects.JUMP_BOOST, hunterFreeze * 20, 248, false, false));
                    player.addStatusEffect(new StatusEffectInstance(StatusEffects.MINING_FATIGUE, (hunterFreeze - 1) * 20, 255, false, false));
                    player.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, hunterFreeze * 20, 255, false, false));
                    player.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, hunterFreeze * 20, 255, false, false));
                }
            }

        }

        world.getServer().getCommandManager().executeWithPrefix(world.getServer().getCommandSource().withSilent(), "chunky cancel");
        world.getServer().getCommandManager().executeWithPrefix(world.getServer().getCommandSource().withSilent(), "chunky confirm");
    }

    public static void updateGameMode(ServerPlayerEntity player) {
        if(ManhuntGame.state == PREGAME) {
            player.changeGameMode(GameMode.ADVENTURE);
        } else if(ManhuntGame.state == PLAYING) {
            player.changeGameMode(GameMode.SURVIVAL);
        } else if(ManhuntGame.state == POSTGAME) {
            player.changeGameMode(GameMode.SPECTATOR);
        } else {
            player.changeGameMode(GameMode.SPECTATOR);
        }
    }

    public static BlockPos findSpawnPos(ServerWorld world) {
        var chunkManager = world.getChunkManager();
        var noiseConfig = chunkManager.getNoiseConfig();
        var chunkGenerator = chunkManager.getChunkGenerator();
        var startChunkPos = new ChunkPos(noiseConfig.getMultiNoiseSampler().findBestSpawnPosition());

        var dx = 0;
        var dz = 0;
        var stepX = 0;
        var stepZ = -1;
        for (var i = 0; i < 11 * 11; i++) {
            if (dx >= -5 && dx <= 5 && dz >= -5 && dz <= 5) {
                var chunkPos = new ChunkPos(startChunkPos.x + dx, startChunkPos.z + dz);
                var x = chunkPos.getStartX() + 8;
                var z = chunkPos.getStartZ() + 8;
                var y = chunkGenerator.getHeightOnGround(x, z, Heightmap.Type.MOTION_BLOCKING, world, noiseConfig);
                var oceanFloorY = chunkGenerator.getHeightOnGround(x, z, Heightmap.Type.OCEAN_FLOOR, world, noiseConfig);
                if (oceanFloorY >= y)
                    return new BlockPos(x, y, z);
            }
            if (dx == dz || dx < 0 && dx == -dz || dx > 0 && dx == 1 - dz) {
                var tmp = stepX;
                stepX = -stepZ;
                stepZ = tmp;
            }
            dx += stepX;
            dz += stepZ;
        }

        var x = startChunkPos.getStartX() + 8;
        var z = startChunkPos.getStartZ() + 8;
        var y = chunkGenerator.getHeightOnGround(x, z, Heightmap.Type.MOTION_BLOCKING, world, noiseConfig);
        return new BlockPos(x, y, z);
    }
}