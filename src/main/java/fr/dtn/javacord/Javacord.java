package fr.dtn.javacord;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;

public class Javacord {
    private final static Logger logger = LogManager.getLogger();

    private static boolean createDirectory(File directory) {
        if (!directory.exists()) {
            logger.info("The directory '{}' does not exist : creating it", directory.getPath());

            try {
                if (!directory.mkdir()) {
                    logger.error("Unable to create the directory : unknown reason");
                    logger.error("Consider creating this directory manually");
                    return false;
                } else {
                    logger.info("Directory '{}' created", directory.getPath());
                    return true;
                }
            } catch (SecurityException e) {
                logger.error("Unable to create the directory : no permission");
                logger.error("Consider creating this directory manually");
                return false;
            }
        } else {
            logger.info("{} already exists : no need to create it", directory.getPath());
            return true;
        }
    }

    private static void createFile(File file, String content) {
        try (FileWriter write = new FileWriter(file); BufferedWriter writer = new BufferedWriter(write)) {
            writer.write(content);
            writer.flush();
            writer.newLine();
        } catch (IOException e) {
            logger.error(e);
        }
    }

    private static String askString(Scanner scanner, String message, String... possibilities) {
        if (possibilities == null || possibilities.length == 0) {
            System.out.print(message);
            return scanner.nextLine();
        } else {
            List<String> choices = Arrays.asList(possibilities);
            String response;

            do {
                System.out.print(message);
                response = scanner.nextLine();
            } while ((!choices.contains(response)));

            return response;
        }
    }

    public static void setup(String directoryPath) {
        File directory = new File(directoryPath);

        if (!createDirectory(directory)) {
            return;
        }

        try (Scanner scanner = new Scanner(System.in)) {
            logger.info("PLEASE GIVE THOSE INFORMATION TO FILL THE 'config.toml' FILE");
            System.out.println("\n[Bot general information]");

            String token = askString(scanner, "Token: ");
            String status = askString(scanner, "Status (ONLINE / IDLE / DO_NOT_DISTURB / INVISIBLE / OFFLINE / none=''): ", "ONLINE", "IDLE", "DO_NOT_DISTURB", "INVISIBLE", "OFFLINE", "");
            String activity = askString(scanner, "Activity (PLAYING / STREAMING / LISTENING / CUSTOM_STATUS / COMPETING / none='') : ", "PLAYING", "STREAMING", "LISTENING", "CUSTOM_STATUS", "COMPETING", "");
            String activityContent = activity.isEmpty() ? "" : askString(scanner, "Detailed activity (what should be displayed after the activity type");
            String totalActivity = activity.isEmpty() ? "" : activity + " " + activityContent;
            String prefix = askString(scanner, "Raw text command prefix : ");

            System.out.println("\n[Database related information]");
            boolean database = askString(scanner, "Enable database (y / n): ").equals("y");

            String url = database ? askString(scanner, "url : ") : "";
            String user = database ? askString(scanner, "user : ") : "";
            String password = database ? askString(scanner, "password : ") : "";

            boolean debug = askString(scanner, "Enable debug mode (y / n): ").equals("y");

            String config = String.format(
                    """
                    [bot]
                    token = '%s' # Mandatory : Your bot token
                    status = '%s' # Optional : The status of your bot (ONLINE / IDLE / DO_NOT_DISTURB / INVISIBLE / OFFLINE )
                    activity = '%s' # Optional : Type of activity in ['PLAYING', 'STREAMING', 'LISTENING', 'CUSTOM_STATUS', 'COMPETING', ''] + " " + text of activity
                    prefix = '%s' # Optional : The prefix of raw text commands. Default value : '!'
                    intents = [] # Mandatory : The string names of the intents that you bot enable
                                    
                    # Optional : your database information (all fields are mandatory if you use a database)
                    [database]
                    url = '%s' # The url that points to the database to use
                    user = '%s' # The name of the user that has permissions on the database
                    password = '%s' # The given user's password
                                    
                    [log]
                    debug = %s # Whether the debug logs are enabled or not (true : logging whenever event handlers, commands, etc. are used)
                    """,
                    token, status, totalActivity, prefix, url, user, password, debug
            );

            File configFile = new File(directory, "config.toml");
            createFile(configFile, config);

            File commandsDirectory = new File(directory, "commands");

            createDirectory(commandsDirectory);
            createDirectory(new File(commandsDirectory, "raw"));
            createDirectory(new File(commandsDirectory, "slash"));
        }
    }
}
