package fr.dtn.javacord.commands.raw;

import com.moandjiezana.toml.Toml;
import fr.dtn.javacord.Bot;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;

public class RawCommand {
    private static final Logger logger = LogManager.getLogger();

    private final String displayName, description;
    private final String[] calls;
    private final Permission[] permissions;
    private final RawCommandExecutor executor;

    public RawCommand(File file) {
        String path = file.getPath();
        logger.info("Loading command from file {}", path);

        Toml command = new Toml().read(file);

        for (String key : new String[] {"name", "description", "calls", "permissions", "executor"}) {
            if (!command.contains(key)) {
                logger.error("The given command file '{}' does not contain any value for the key {}", path, key);
                throw new IllegalArgumentException("The given command file '" + path + "' does not contain any value for the key " + key);
            }
        }

        this.displayName = command.getString("name");
        this.description = command.getString("description");
        this.calls = command.getList("calls").stream().map(String::valueOf).toArray(String[]::new);
        this.permissions = command.getList("permissions").stream().map(String::valueOf).map(Permission::valueOf).toArray(Permission[]::new);
        this.executor = RawCommandExecutor.fromClassName(command.getString("executor"));
    }

    public void execute(Bot bot, Guild guild, TextChannel channel, Message message, User author, Member member, String[] args) {
        executor.execute(bot, guild, channel, message, author, member, args);
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    public String[] getCalls() {
        return calls;
    }

    public Permission[] getPermissions() {
        return permissions;
    }

    public RawCommandExecutor getExecutor() {
        return executor;
    }
}
