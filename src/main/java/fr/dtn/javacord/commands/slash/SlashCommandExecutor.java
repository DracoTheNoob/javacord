package fr.dtn.javacord.commands.slash;

import fr.dtn.javacord.Bot;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;

public interface SlashCommandExecutor {
    void execute(Bot bot, SlashCommandInteractionEvent event);
}