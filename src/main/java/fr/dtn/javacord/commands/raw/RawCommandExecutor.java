package fr.dtn.javacord.commands.raw;

import fr.dtn.javacord.Bot;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.InvocationTargetException;

public interface RawCommandExecutor {
    Logger logger = LogManager.getLogger();

    void execute(Bot bot, Guild guild, TextChannel channel, Message message, User author, Member member, String[] args);

    static RawCommandExecutor fromClassName(String className) {
        try {
            return (RawCommandExecutor) Class.forName(className).getConstructor().newInstance();
        }catch(ClassNotFoundException e){
            logger.error("Loading command from class '{}' failed : class not found", className);
            throw new IllegalArgumentException("Loading command from class '" + className + "' failed : class not found");
        }catch(NoSuchMethodException e){
            logger.error("Loading command from class '{}' failed : default constructor not found", className);
            throw new IllegalArgumentException("Loading command from class '" + className + "' failed : default constructor not found");
        }catch(InvocationTargetException | InstantiationException e){
            logger.error("Loading command from class '{}' failed : unknown reason", className);
            throw new InternalError("Loading command from class '" + className + "' failed : unknown reason");
        }catch(IllegalAccessException e){
            logger.error("Loading command from class '{}' failed : default constructor not public", className);
            throw new IllegalArgumentException("Loading command from class '" + className + "' failed : default constructor not public");
        }
    }
}
