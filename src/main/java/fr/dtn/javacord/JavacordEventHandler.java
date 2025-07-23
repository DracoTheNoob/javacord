package fr.dtn.javacord;

import fr.dtn.javacord.commands.raw.RawCommand;
import fr.dtn.javacord.commands.slash.SlashCommand;
import fr.dtn.javacord.event.EventHandler;
import fr.dtn.javacord.interaction.JavacordButton;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.GenericEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.List;

public class JavacordEventHandler extends ListenerAdapter {
    private final static Logger logger = LogManager.getLogger(Bot.class);

    private final Bot bot;

    public JavacordEventHandler(Bot bot) {
        this.bot = bot;
    }

    @Override
    public void onGenericEvent(@NotNull GenericEvent event) {
        String eventId = event.getClass().getSimpleName();

        if (bot.isDebugMode()) {
            logger.info("Event occurred : {}", eventId);
        }

        for (EventHandler<?> handler : bot.getEventHandlers()) {
            try {
                Type[] genericInterfaces = handler.getClass().getGenericInterfaces();

                if (genericInterfaces.length != 1) {
                    throw new InternalError("An event handler instance has more than one generic interface");
                }

                for (Type type : ((ParameterizedType) genericInterfaces[0]).getActualTypeArguments()) {
                    Class<? extends GenericEvent> caster = (Class<? extends GenericEvent>) type;

                    if (caster.isInstance(event)) {
                        handler.getClass().cast(handler).onEvent(bot, caster.cast(event));
                        break;
                    }
                }
            } catch (ClassCastException ignored) {
            } catch (Exception e) {
                logger.warn("An error occured while executing one of your EventHandler instances");
                throw e;
            }
        }
    }

    @Override
    public void onMessageReceived(@NotNull MessageReceivedEvent event) { // TODO : solve possibles issues when commands are used in DM with bot
        User author = event.getAuthor();
        Member member = event.getMember();
        Message message = event.getMessage();
        String text = message.getContentRaw();

        if (author.isBot() || member ==  null || !text.startsWith(bot.getCommandPrefix())) {
            return;
        }

        Guild guild = event.getGuild();
        TextChannel channel = event.getChannel().asTextChannel();

        List<String> split = Arrays.asList(text.split(" +"));
        String commandName = split.get(0).substring(bot.getCommandPrefix().length());
        List<String> args = split.subList(1, split.size());

        RawCommand command = bot.getRawCommandByCall(commandName);

        if (command == null) {
            return;
        }

        if (!member.hasPermission(command.getPermissions())) {
            message.replyEmbeds(bot.getNoPermissionMessage()).queue();
            return;
        }

        if (bot.isDebugMode()) {
            logger.info("'{}' command was called by @{}", command.getDisplayName(), author.getName());
        }

        command.execute(bot, guild, channel, message, author, member, args.toArray(String[]::new));
    }

    @Override
    public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent event) {
        Guild guild = event.getGuild();

        if(guild == null)
            return;

        TextChannel channel = event.getChannel().asTextChannel();
        User author = event.getUser();
        Member member = event.getMember();

        if(member == null)
            return;

        SlashCommand command = bot.getSlashCommandByName(event.getName());

        if(command == null){
            logger.warn("No executor for slash command '" + event.getName() + "' : Unable to execute it");
            event.deferReply().queue();
            event.deferReply(true).queue(hook -> hook.sendMessage("An internal error occured...").queue());
            return;
        }

        if(!member.hasPermission(command.getPermissions())){
            if (bot.isDebugMode()) {
                logger.info("@"+author.getName()+" (" + member.getNickname()+") tried to call slash command '"+command.getDisplayName()+"' ("+member.getNickname()+") on ("+guild.getName()+"#"+channel.getName()+") -> refused : missing permission(s)");
            }

            event.deferReply().queue();
            event.deferReply(true).queue(hook -> hook.sendMessage("You do not have the permission to use this command").queue());
            return;
        }

        if (bot.isDebugMode()) {
            logger.info(author.getName()+" ("+member.getNickname()+") use slash command '"+command.getDisplayName()+"' in ("+guild.getName()+"/"+channel.getName()+")");
        }

        command.execute(bot, event);
    }

    @Override
    public void onButtonInteraction(@NotNull ButtonInteractionEvent event) {
        Button button = event.getButton();
        String buttonId = button.getId();
        JavacordButton executor = bot.getButtonById(buttonId);

        if (executor == null) {
            logger.warn("No executor for button with id='" + buttonId + "' and label='" + button.getLabel() + "' : Unable to execute it");
            event.deferReply().queue();
            event.deferReply(true).queue(hook -> hook.sendMessage("An internal error occured...").queue());
            return;
        }

        executor.execute(bot, event);
    }
}
