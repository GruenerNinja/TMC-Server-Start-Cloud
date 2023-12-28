package org.themodcraft.tmc_server_start_colud;

import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.plugin.Command;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.api.plugin.TabExecutor;
import net.md_5.bungee.config.Configuration;
import net.md_5.bungee.config.ConfigurationProvider;
import net.md_5.bungee.config.YamlConfiguration;

import java.awt.*;
import java.io.*;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class TMCServerStartCloud extends Plugin {

    private Configuration configuration;

    @Override
    public void onEnable() {
        // Load the config
        loadConfig();

        // Register the /startserver and /TMCServerStartCloud:reload commands
        getProxy().getPluginManager().registerCommand(this, new StartServerCommand(this));
        getProxy().getPluginManager().registerCommand(this, new ReloadCommand(this));

        getLogger().info("TMC Server Start Cloud has been enabled!");
    }

    @Override
    public void onDisable() {
        // Save the config when the plugin is disabled
        saveConfig();
        getLogger().info("TMC Server Start Cloud has been disabled!");
    }

    public void loadConfig() {
        try {
            Path configFile = getDataFolder().toPath().resolve("config.yml");

            // Ensure the parent directory exists
            Files.createDirectories(configFile.getParent());

            // Check if the config file exists
            if (!Files.exists(configFile)) {
                // Copy the default config file from resources to the plugin data folder
                try (InputStream is = getResourceAsStream("config.yml")) {
                    Files.copy(is, configFile);
                }
            }

            // Load the configuration from the file
            ConfigurationProvider configurationProvider = YamlConfiguration.getProvider(YamlConfiguration.class);
            configuration = configurationProvider.load(configFile.toFile());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void saveConfig() {
        // Save the configuration only if it was loaded successfully
        if (configuration != null) {
            File configFile = new File(getDataFolder(), "config.yml");
            try {
                ConfigurationProvider.getProvider(YamlConfiguration.class).save(configuration, configFile);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public Configuration getConfig() {
        return configuration;
    }

    public class StartServerCommand extends Command implements TabExecutor {
        public StartServerCommand(Plugin plugin) {
            super("startserver", null, "ss");
        }

        @Override
        public void execute(CommandSender sender, String[] args) {
            if (!(sender instanceof ProxiedPlayer)) {
                sender.sendMessage(ChatColor.RED + "This command can only be executed by a player.");
                return;
            }

            ProxiedPlayer player = (ProxiedPlayer) sender;

            if (!player.hasPermission("tmcserverstartcloud.startserver")) {
                sender.sendMessage(ChatColor.RED + "You do not have permission to use this command.");
                return;
            }

            if (args.length != 1) {
                sender.sendMessage(ChatColor.RED + "Usage: /startserver <servername>");
                return;
            }

            String serverName = args[0];
            String startCommandPath = getConfig().getString("servers." + serverName + ".startCommand");

            if (startCommandPath != null) {
                File startCommandFile = new File(startCommandPath);

                if (startCommandFile.exists()) {
                    executeCommand(sender, startCommandFile.getAbsolutePath());
                    sender.sendMessage(ChatColor.GREEN + "Starting server: " + serverName);
                    broadcastMessage(ChatColor.YELLOW + "Server " + serverName + " is starting!");

                    if (getConfig().getBoolean("servers." + serverName + ".standalone", false)) {
                        String standaloneIP = getConfig().getString("servers." + serverName + ".standaloneip", "127.0.0.1");
                        sendCommandToStandalone(standaloneIP, startCommandPath);
                    }
                } else {
                    sender.sendMessage(ChatColor.RED + "Start command file not found: " + startCommandPath);
                }

            } else {
                sender.sendMessage(ChatColor.RED + "Server not found in the config.");
            }
        }

        @Override
        public Iterable<String> onTabComplete(CommandSender sender, String[] args) {
            if (args.length == 1) {
                List<String> suggestions = new ArrayList<>();
                for (String server : getConfig().getSection("servers").getKeys()) {
                    if (server.toLowerCase().startsWith(args[0].toLowerCase())) {
                        suggestions.add(server);
                    }
                }
                return suggestions;
            }
            return Collections.emptyList();
        }

        private void sendCommandToStandalone(String standaloneIP, String command) {
            try (Socket socket = new Socket(standaloneIP, 12345);
                 ObjectOutputStream oos = new ObjectOutputStream(socket.getOutputStream())) {

                oos.writeObject(command);
                oos.flush();

                System.out.println("Command sent to standalone server: " + command);

            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        private void executeCommand(CommandSender sender, String commandPath) {
            try {
                File commandFile = new File(commandPath);

                if (Desktop.isDesktopSupported() && commandFile.isFile()) {
                    Desktop.getDesktop().open(commandFile);
                } else {
                    String[] cmd = {"/bin/sh", "-c", commandPath};
                    Process process = Runtime.getRuntime().exec(cmd);
                    process.waitFor();
                }
            } catch (IOException | InterruptedException e) {
                sender.sendMessage(ChatColor.RED + "Error executing the command: " + commandPath);
                e.printStackTrace();
            }
        }
    }

    public class ReloadCommand extends Command {
        public ReloadCommand(Plugin plugin) {
            super("TMCServerStartCloud:reload");
        }

        @Override
        public void execute(CommandSender sender, String[] args) {
            loadConfig();
            sender.sendMessage(ChatColor.GREEN + "TMC Server Start Cloud configuration reloaded!");
        }
    }

    private void broadcastMessage(String message) {
        ProxyServer.getInstance().broadcast(message);
    }
}
