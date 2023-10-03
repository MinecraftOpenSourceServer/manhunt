package manhunt.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import manhunt.config.ManhuntConfig;
import net.minecraft.command.CommandSource;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import nota.Nota;
import nota.model.Song;
import nota.player.RadioSongPlayer;
import nota.utils.NBSDecoder;

import java.io.File;

import static manhunt.Manhunt.songs;
import static manhunt.config.ManhuntConfig.musicDirectory;
import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public final class JukeboxCommand {

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(literal("jukebox")
                .then(literal("play")
                        .then(argument("song", StringArgumentType.word()).suggests(songSuggestions())
                        .executes(context -> {
                            var source = context.getSource();
                            var songName = StringArgumentType.getString(context, "song");

                            return playSong(source, songName);
                        })
                ))
                .then(literal("stop")
                        .executes(context -> stopPlaying(context.getSource()))
                )
        );
        dispatcher.register(literal("jukeboxall")
                .requires(serverCommandSource -> serverCommandSource.hasPermissionLevel(4))
                .then(literal("play")
                        .then(argument("song", StringArgumentType.word()).suggests(songSuggestions())
                                .executes(context -> {
                                    var source = context.getSource();
                                    var songName = StringArgumentType.getString(context, "song");

                                    return playAllSong(source, songName);
                                })
                        ))
                .then(literal("stop")
                        .requires(serverCommandSource -> serverCommandSource.hasPermissionLevel(4))
                        .executes(context -> stopPlayingAll(context.getSource())
                        )
        ));
    }

    private static SuggestionProvider<ServerCommandSource> songSuggestions() {
        return (context, builder) -> CommandSource.suggestMatching(songs, builder);
    }

    private static int playSong(ServerCommandSource source, String songName) {
        ManhuntConfig.load();

        Song song = NBSDecoder.parse(new File(musicDirectory + "/" + songName + ".nbs"));

        RadioSongPlayer rsp = new RadioSongPlayer(song);

        rsp.addPlayer(source.getPlayer());
        rsp.setPlaying(true);

        source.sendFeedback(() -> Text.translatable("Now playing: " + songName), false);

        return Command.SINGLE_SUCCESS;
    }

    private static int stopPlaying(ServerCommandSource source) {
        Nota.stopPlaying(source.getPlayer());

        source.sendFeedback(() -> Text.translatable("Stopped playing"), false);

        return Command.SINGLE_SUCCESS;
    }

    private static int playAllSong(ServerCommandSource source, String songName) {
        ManhuntConfig.load();

        Song song = NBSDecoder.parse(new File(musicDirectory + "/" + songName + ".nbs"));

        RadioSongPlayer rsp = new RadioSongPlayer(song);

        for (ServerPlayerEntity player : source.getServer().getPlayerManager().getPlayerList()) {
            rsp.addPlayer(player);
        }
        rsp.setPlaying(true);

        source.sendFeedback(() -> Text.translatable("Now playing: " + songName), false);

        return Command.SINGLE_SUCCESS;
    }

    private static int stopPlayingAll(ServerCommandSource source) {
        for (ServerPlayerEntity player : source.getServer().getPlayerManager().getPlayerList()) {
            Nota.stopPlaying(player);
        }

        source.sendFeedback(() -> Text.translatable("Stopped playing"), false);

        return Command.SINGLE_SUCCESS;
    }
}