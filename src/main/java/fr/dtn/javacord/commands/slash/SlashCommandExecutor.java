package fr.dtn.javacord.commands.slash;

import fr.dtn.javacord.Bot;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;

/**
 * Class that represents which code will be executed when slash commands are used
 */
public interface SlashCommandExecutor {
    /**
     * To execute the command
     * @param bot The current bot
     * @param guild The guild where the slash command is used
     * @param channel The text channel where the slash command is used
     * @param author The author of the slash command
     * @param member The author of the slash command as a member of the guild
     * @param event The event
     */
    void execute(Bot bot, SlashCommandInteractionEvent event);
}