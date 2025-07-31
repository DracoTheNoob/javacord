package fr.dtn.javacord;

import com.moandjiezana.toml.Toml;
import fr.dtn.javacord.commands.raw.RawCommand;
import fr.dtn.javacord.commands.slash.Parameter;
import fr.dtn.javacord.commands.slash.SlashCommand;
import fr.dtn.javacord.database.Database;
import fr.dtn.javacord.event.EventHandler;
import fr.dtn.javacord.interaction.ButtonExecutor;
import fr.dtn.javacord.interaction.JavacordButton;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.exceptions.InvalidTokenException;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.buttons.ButtonStyle;
import net.dv8tion.jda.api.requests.ErrorResponse;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.sharding.DefaultShardManagerBuilder;
import net.dv8tion.jda.api.sharding.ShardManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

public class Bot {
    private static final Logger logger = LogManager.getLogger(Bot.class);

    private static final GatewayIntent[] DEFAULT_INTENTS = {
            GatewayIntent.GUILD_MESSAGES, GatewayIntent.DIRECT_MESSAGES, GatewayIntent.MESSAGE_CONTENT
    };
    private static final OnlineStatus DEFAULT_STATUS = OnlineStatus.ONLINE;

    private final File directory;
    private final Toml config;
    private final String commandPrefix;
    private ShardManager bot;
    private final boolean debugMode;

    private final List<EventHandler<?>> eventHandlers;
    private final List<RawCommand> rawCommands;
    private final List<SlashCommand> slashCommands;
    private final List<JavacordButton> buttons;

    private Database database;

    private MessageEmbed noPermissionMessage = EmbedUtils.createError("You do not have the permission to use this command");
    private MessageEmbed errorMessage = EmbedUtils.createError("An internal error has occurred");

    public Bot(String path) {
        // Load directory
        this.directory = new File(path);

        if (!directory.exists()) {
            logger.warn("The specified directory does not exist : '{}'", path);
            logger.warn("Consider using the Javacord#setup function to create and setup the bot directory");
            throw new IllegalArgumentException("The specified directory does not exist : '" + path + "'");
        }

        logger.info("Creating a Discord Bot instance using directory '{}'", path);

        // Load configuration
        File configFile = new File(directory, "config.toml");

        if (!configFile.exists()) {
            logger.warn("The specified directory '{}' does not contain any config.toml configuration file...", directory.getPath());
            logger.warn("Consider using the Javacord#setup function to create and setup the bot directory");
            throw new IllegalArgumentException("The specified directory '" + directory.getPath() + "' does not contain any config.toml configuration file...");
        }

        /* *

        if (!configFile.exists()) {
            logger.warn("There is no 'config.toml' file in the directory");
            logger.warn("Creating a default configuration file...");

            try {
                Files.copy(new FileInputStream("src/main/resources/default-configuration.toml"), configFile.toPath());
                logger.warn("PLEASE FILL THE CONFIGURATION FILE BEFORE RUNNING YOUR PROGRAM AGAIN !");

                System.exit(0);
            } catch (IOException e) {
                logger.error("Unable to create the default configuration");
                throw new InternalError(e);
            }
        }

         */

        logger.info("Loading config : '{}'", configFile.getPath());

        try {
            this.config = new Toml().read(configFile);
        } catch (RuntimeException e) {
            logger.error("Unable to load the configuration from file '{}'", configFile.getPath());
            throw new RuntimeException(e);
        }

        // Load token
        if (!config.contains("bot.token")) {
            logger.warn("The mandatory value 'bot.token' is not specified");
            throw new RuntimeException("The mandatory value 'bot.token' is not specified");
        }

        String token = config.getString("bot.token");

        // Load intents from config
        List<?> intentObjects = config.getList("bot.intents", new ArrayList<>());
        List<GatewayIntent> intents = new ArrayList<>();

        for (Object intent : intentObjects) {
            if (intent instanceof String intentName) {
                try {
                    intents.add(GatewayIntent.valueOf(intentName));
                } catch (IllegalArgumentException e) {
                    logger.warn("One of given the intents does not exist and will be ignored : '{}'", intentName);
                }
            } else {
                logger.warn("One of the given intents is not a String and will be ignored : {}", intent);
            }
        }

        for (GatewayIntent defaultIntent : DEFAULT_INTENTS) {
            if (!intents.contains(defaultIntent)) {
                intents.add(defaultIntent);
            }
        }

        logger.info("Creating Bot with intents : {}", intents);

        // Create bot builder
        DefaultShardManagerBuilder botBuilder = DefaultShardManagerBuilder.createDefault(token, intents).setEventPassthrough(true);

        // Load status from config
        String statusName = config.getString("bot.status");

        try {
            Objects.requireNonNull(statusName);
            botBuilder.setStatus(OnlineStatus.valueOf(statusName));

            logger.info("Using status '{}'", statusName);
        } catch (NullPointerException | IllegalArgumentException e) {
            logger.warn("The bot status is not specified in the config, or its value is invalid : '{}'", statusName);
            logger.warn("Using default status : {}", DEFAULT_STATUS);

            botBuilder.setStatus(DEFAULT_STATUS);
        }

        // Load activity from config
        String activity = config.getString("bot.activity");

        try {
            Objects.requireNonNull(activity);

            int delimiterIndex = activity.indexOf(' ');

            if (delimiterIndex == -1) {
                throw new IllegalArgumentException(activity);
            }

            String activityName = activity.substring(0, delimiterIndex);
            String activityContent = activity.substring(delimiterIndex + 1);

            botBuilder.setActivity(Activity.of(Activity.ActivityType.valueOf(activityName), activityContent));
            logger.info("Using activity : {}", activity);
        } catch (NullPointerException e) {
            logger.warn("The bot activity is not specified in the config");
        } catch (IllegalArgumentException e) {
            logger.warn("The bot activity specified in the config is invalid : '{}'", activity);
        }

        // Load raw command prefix
        this.commandPrefix = config.getString("bot.prefix", "!");
        logger.info("Using raw command prefix '{}'", commandPrefix);

        logger.info("Bot created successfully !");

        this.debugMode = config.getBoolean("log.debug", false);
        logger.info("The logging debug mode is {}", debugMode ? "ENABLED" : "DISABLED");

        String databaseUrl = config.getString("database.url");
        String databaseUser = config.getString("database.user");
        String databasePassword = config.getString("database.password");

        if (databaseUrl != null && databaseUser != null) {
            try {
                this.database = new Database(this, databaseUrl, databaseUser, databasePassword);
            } catch (IllegalArgumentException e) {
                logger.warn("Invalid database url, user, or password");
                this.database = null;
            }
        } else {
            this.database = null;
        }

        // Create bot
        try {
            this.bot = botBuilder.build();
            this.bot.addEventListener(new JavacordEventHandler(this));
        } catch (InvalidTokenException e) {
            logger.error("The token specified in the config is invalid");
            System.exit(0);
        }

        this.eventHandlers = new ArrayList<>();
        this.rawCommands = new ArrayList<>();
        this.slashCommands = new ArrayList<>();
        this.buttons = new ArrayList<>();

        registerRawCommands(new File(this.directory, "commands/raw"));
        registerSlashCommands(new File(this.directory, "commands/slash"));
    }

    public void registerEventHandlers(EventHandler<?> first, EventHandler<?>... others) {
        this.eventHandlers.add(first);
        this.eventHandlers.addAll(Arrays.asList(others));
    }

    public void registerEventHandler(EventHandler<?> handler) {
        eventHandlers.add(handler);
    }

    public void registerEventHandler(Class<? extends EventHandler<?>> handlerClass) {
        try {
            registerEventHandlers(handlerClass.getConstructor().newInstance());
        } catch(NoSuchMethodException | IllegalAccessException | IllegalArgumentException e) {
            logger.error("An error occured while registering the event handler from class : {}", handlerClass.getName());
            logger.error("CAUSE : There is no default & public constructor");

            throw new IllegalArgumentException("There is no default & public constructor for the class " + handlerClass.getName(), e);
        } catch(InstantiationException e) {
            logger.error("An error occured while registering the event handler from class : {}", handlerClass.getName());
            logger.error("CAUSE : The given class is abstract, and cannot be instantiated");

            throw new IllegalArgumentException("There is no default & public constructor for the class " + handlerClass.getName(), e);
        } catch (InvocationTargetException | ExceptionInInitializerError e) {
            logger.error("An error occured while registering the event handler from class : {}", handlerClass.getName());
            logger.error("CAUSE : not determined. It probably depends on your code");

            throw new IllegalArgumentException(e);
        }
    }

    @SafeVarargs
    public final void registerEventHandlers(Class<? extends EventHandler<?>> first, Class<? extends EventHandler<?>>... others) {
        registerEventHandler(first);

        for (Class<? extends EventHandler<?>> handlerClass : others) {
            registerEventHandler(handlerClass);
        }
    }

    private void registerRawCommand(File file) {
        RawCommand command = new RawCommand(file);

        for (String call : command.getCalls()) {
            if (getRawCommandByCall(call) != null) {
                logger.error("The command from file '{}' has an already used call : '{}'", file.getPath(), call);
                throw new IllegalArgumentException("The command from file '" + file.getPath() + "' has an already used call : '" + call + "'");
            }
        }

        rawCommands.add(command);
    }

    private void registerRawCommands(File directory) {
        if (!directory.exists() || directory.isFile()) {
            return;
        }

        File[] subFiles = directory.listFiles();

        if (subFiles == null) {
            return;
        }

        for (File file : subFiles) {
            if (file.isDirectory()) {
                registerRawCommands(file);
            } else {
                registerRawCommand(file);
            }
        }
    }

    private void registerSlashCommand(File file){
        SlashCommand command = new SlashCommand(file);
        SlashCommandData data = Commands.slash(command.getDisplayName(), command.getDescription());

        for(Parameter parameter : command.getParameters()) {
            OptionData option = new OptionData(parameter.type(), parameter.name(), parameter.description());
            String[] choices = parameter.choices();

            for(String choice : choices) {
                option.addChoices(new net.dv8tion.jda.api.interactions.commands.Command.Choice(choice, choice));
            }

            data.addOptions(option);
        }

        this.bot.getShards().forEach(jda -> jda.updateCommands().addCommands(data).queue());
        this.slashCommands.add(command);
    }

    private void registerSlashCommands(File directory) {
        if (!directory.exists() || directory.isFile()) {
            return;
        }

        File[] subFiles = directory.listFiles();

        if (subFiles == null) {
            return;
        }

        for (File file : subFiles) {
            if (file.isDirectory()) {
                registerSlashCommands(file);
            } else {
                registerSlashCommand(file);
            }
        }
    }

    protected List<EventHandler<?>> getEventHandlers() {
        return eventHandlers;
    }

    public File getDirectory() {
        return directory;
    }

    public Toml getConfig() {
        return config;
    }

    public String getCommandPrefix() {
        return commandPrefix;
    }

    public boolean isDebugMode() {
        return debugMode;
    }

    public RawCommand getRawCommandByCall(String call) {
        for (RawCommand command : rawCommands) {
            for (String commandCall : command.getCalls()) {
                if (commandCall.equals(call)) {
                    return command;
                }
            }
        }

        return null;
    }

    public MessageEmbed getNoPermissionMessage() {
        return noPermissionMessage;
    }

    public void setNoPermissionMessage(MessageEmbed noPermissionMessage) {
        this.noPermissionMessage = noPermissionMessage;
    }

    public SlashCommand getSlashCommandByName(String name) {
        for (SlashCommand command : slashCommands) {
            if (command.getDisplayName().equals(name)) {
                return command;
            }
        }

        return null;
    }

    public void sendTemporalMessage(InteractionHook hook, String message, long duration, TimeUnit unit) {
        hook.sendMessage(message).queue(createdMessage -> createdMessage.delete().queueAfter(
                duration, unit,
                success -> {}, // Do nothing, the message was successfully deleted
                fail -> {
                    if (!(fail instanceof ErrorResponseException e &&
                            (e.getErrorResponse() == ErrorResponse.UNKNOWN_MESSAGE ||
                                    e.getErrorResponse() == ErrorResponse.MISSING_PERMISSIONS)
                    )) {
                        logger.error("Internal Error : " + fail.getMessage());
                    } // The bot cannot delete the message due to missing permissions

                    // If the condition is not verified, it means that the message is already deleted : so no need to do it
                }));
    }

    public void sendTemporalEmbed(InteractionHook hook, MessageEmbed embed, long duration, TimeUnit unit) {
        hook.sendMessageEmbeds(embed).queue(createdMessage -> createdMessage.delete().queueAfter(
                duration, unit,
                success -> {}, // Do nothing, the message was successfully deleted
                fail -> {
                    if (!(fail instanceof ErrorResponseException e &&
                            (e.getErrorResponse() == ErrorResponse.UNKNOWN_MESSAGE ||
                                    e.getErrorResponse() == ErrorResponse.MISSING_PERMISSIONS)
                    )) {
                        logger.error("Internal Error : " + fail.getMessage());
                    } // The bot cannot delete the message due to missing permissions

                    // If the condition is not verified, it means that the message is already deleted : so no need to do it
                }));
    }

    public void sendTemporalEmbed(InteractionHook hook, EmbedBuilder embed, long duration, TimeUnit unit) {
        sendTemporalEmbed(hook, embed.build(), duration, unit);
    }

    public JavacordButton getButtonById(String id) {
        for (JavacordButton button : buttons) {
            if (button.getId().toString().equals(id)) {
                return button;
            }
        }

        return null;
    }

    public Button createButton(JavacordButton button) {
        this.buttons.add(button);
        return Button.of(button.getStyle(), button.getId().toString(), button.getLabel());
    }

    public Button createButton(ButtonStyle style, String label, ButtonExecutor executor) {
        return createButton(new JavacordButton(style, label, executor));
    }

    public Database getDatabase() {
        if (database != null) {
            return database;
        }

        throw new IllegalStateException("The database was not instantiated in the configuration file");
    }

    public MessageEmbed getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(MessageEmbed errorMessage) {
        this.errorMessage = errorMessage;
    }
}
