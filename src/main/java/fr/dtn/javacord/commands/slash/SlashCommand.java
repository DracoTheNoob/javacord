package fr.dtn.javacord.commands.slash;

import com.moandjiezana.toml.Toml;
import fr.dtn.javacord.Bot;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class SlashCommand {
    private static final Logger logger = LogManager.getLogger();

    private final String displayName, description;
    private Permission[] permissions;
    private SlashCommandExecutor executor;
    private List<Parameter> parameters;

    public SlashCommand(File file){
        logger.info("Loading slash command from file '" + file.getPath() + "'");
        Toml toml = new Toml().read(file);

        if (toml.contains("name")) {
            this.displayName = toml.getString("name");
        } else {
            this.displayName = file.getName().replace(".toml", "");
        }

        this.description = toml.getString("description");

        List<Object> perms = toml.getList("permissions");

        try{
            this.permissions = new Permission[perms.size()];
        }catch(NullPointerException e){
            logger.warn("Permissions of slash command '" + displayName + "' are not set, using default empty permissions");
            this.permissions = new Permission[0];
        }

        for(int i = 0; i < permissions.length; i++){
            String permission = perms.get(i).toString().toUpperCase();

            try{
                permissions[i] = Permission.valueOf(permission);
            }catch(IllegalArgumentException e){
                logger.error("Loading slash command '" + displayName + "' : Failed to load permission '" + permission + "' : Permission does not exist");
            }
        }


        String className = toml.getString("executor");
        try {
            this.executor = (SlashCommandExecutor) Class.forName(className).getConstructor().newInstance();
        }catch(ClassNotFoundException e){
            logger.error("Loading slash command '" + displayName + "' failed : Impossible to load executor class '" + className + "' : Class not found");
            return;
        }catch(NoSuchMethodException e){
            logger.error("Loading slash command '" + displayName + "' failed : Class '" + className + "' does not have an argument-less constructor");
            return;
        }catch(InvocationTargetException | InstantiationException e){
            logger.error("Loading slash command '" + displayName + "' failed : Class '" + className + "' cannot be instantiated : Unknown reason");
            return;
        }catch(IllegalAccessException e){
            logger.error("Loading slash command '" + displayName + "' failed : Class '" + className + "' argument-less constructor is not public");
            return;
        }

        logger.info("Loading slash command options");
        List<String> names = toml.getList("parameters.name");
        List<String> descriptions = toml.getList("parameters.description");
        List<Boolean> required = toml.getList("parameters.required");
        List<Boolean> autoCompletes = toml.getList("parameters.autoComplete");
        List<List<String>> choice = toml.getList("parameters.choice");
        List<String> typesNames = toml.getList("parameters.type");


        for(List<?> list : Arrays.asList(descriptions, required, autoCompletes, choice, typesNames)){
            if(list.size() != names.size()){
                logger.error("Loading slash command '" + displayName + "' failed : Different options amount");
                return;
            }
        }

        List<OptionType> types = new ArrayList<>();
        typesNames.forEach(type -> {
            try{
                types.add(OptionType.valueOf(type));
            }catch(IllegalArgumentException e){
                logger.error("Loading slash command '" + displayName + "' : Failed to load parameter type '" + type + "' :  Type does not exist");
            }
        });

        this.parameters = new ArrayList<>();
        for(int i = 0; i < names.size(); i++)
            this.parameters.add(new Parameter(types.get(i), names.get(i), descriptions.get(i), required.get(i), autoCompletes.get(i), choice.get(i).toArray(new String[0])));

        logger.info("Slash command '" + displayName + "' loaded successfully");
    }

    public void execute(Bot bot, SlashCommandInteractionEvent event) {
        executor.execute(bot, event);
    }

    public String getDisplayName() { return displayName; }

    public String getDescription() { return description; }

    public Permission[] getPermissions() { return permissions; }

    public List<Parameter> getParameters(){ return parameters; }
}