package me.cooleg.oauthmc.listeners;

import fr.xephi.authme.api.v3.AuthMeApi;
import me.cooleg.oauthmc.OauthMC;
import me.cooleg.oauthmc.OauthMCConfig;
import me.cooleg.oauthmc.authentication.CodeAndLinkResponse;
import me.cooleg.oauthmc.authentication.IOauth;
import me.cooleg.oauthmc.persistence.IDatabaseHook;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.UUID;

public class AuthMeIntegrationListener implements Listener {

    private static final long PROMPT_TIMEOUT_TICKS = 12000L;
    private static final int AUTH_CHECK_DELAY_TICKS = 40;
    private static final int AUTH_CHECK_PERIOD_TICKS = 20;
    private static final int AUTH_MAX_CHECKS = 600;

    private final OauthMC plugin;
    private final IDatabaseHook db;
    private final IOauth auth;
    private final OauthMCConfig config;
    private final AuthMeApi authMeApi;

    public AuthMeIntegrationListener(OauthMC plugin, IDatabaseHook db, IOauth auth, OauthMCConfig config) {
        this.plugin = plugin;
        this.db = db;
        this.auth = auth;
        this.config = config;
        this.authMeApi = AuthMeApi.getInstance();
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        if (config.getWhitelistedUuids().contains(uuid)) {
            return;
        }

        new BukkitRunnable() {
            @Override
            public void run() {
                handlePlayerLogin(player, uuid);
            }
        }.runTaskAsynchronously(plugin);
    }

    private void handlePlayerLogin(Player player, UUID uuid) {
        boolean hasLoggedIn = db.hasLoggedIn(uuid);

        if (!hasLoggedIn) {
            handleFirstTimeBinding(player, uuid);
        } else {
            handleReturningPlayer(player, uuid);
        }
    }

    private void handleFirstTimeBinding(Player player, UUID uuid) {
        if (!player.isOnline()) {
            return;
        }

        try {
            CodeAndLinkResponse response = auth.beginLogin(uuid);
            if (response == null || response.loginLink == null) {
                Bukkit.getLogger().severe("OauthMC: Failed to initiate binding for player " + player.getName() + " (missing response)");
                return;
            }

            plugin.getPromptManager().startPrompt(player, response.loginLink, response.userCode, true);
            schedulePromptTimeout(player);
            waitForAuthentication(player, uuid, true);
        } catch (Exception ex) {
            Bukkit.getLogger().severe("OauthMC: Failed to initiate binding for player " + player.getName());
            plugin.getPromptManager().stopPrompt(player);
            ex.printStackTrace();
        }
    }

    private void handleReturningPlayer(Player player, UUID uuid) {
        long lastAuthTime = db.getLastAuthTime(uuid);
        long currentTime = System.currentTimeMillis();
        long sessionDuration = config.getSessionDurationHours() * 60L * 60L * 1000L;

        if (config.getSessionDurationHours() > 0 && (currentTime - lastAuthTime) < sessionDuration) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (player.isOnline() && !authMeApi.isAuthenticated(player)) {
                    authMeApi.forceLogin(player);
                    String linuxdoUsername = db.getLinuxDoUsername(uuid);
                    if (linuxdoUsername != null) {
                        player.sendMessage(MiniMessage.miniMessage().deserialize(
                                "<green>欢迎回来! 已自动登录 (绑定账号: " + linuxdoUsername + ")</green>"));
                    }
                }
            });
            return;
        }

        if (!player.isOnline()) {
            return;
        }

        try {
            CodeAndLinkResponse response = auth.beginLogin(uuid);
            if (response == null || response.loginLink == null) {
                Bukkit.getLogger().severe("OauthMC: Failed to initiate login for player " + player.getName() + " (missing response)");
                return;
            }

            plugin.getPromptManager().startPrompt(player, response.loginLink, response.userCode, false);
            schedulePromptTimeout(player);
            waitForAuthentication(player, uuid, false);
        } catch (Exception ex) {
            Bukkit.getLogger().severe("OauthMC: Failed to initiate login for player " + player.getName());
            plugin.getPromptManager().stopPrompt(player);
            ex.printStackTrace();
        }
    }

    private void waitForAuthentication(Player player, UUID uuid, boolean isFirstTime) {
        new BukkitRunnable() {
            int checks = 0;

            @Override
            public void run() {
                if (!player.isOnline()) {
                    plugin.getPromptManager().stopPrompt(player);
                    cancel();
                    return;
                }

                checks++;
                if (checks >= AUTH_MAX_CHECKS) {
                    plugin.getPromptManager().stopPrompt(player);
                    cancel();
                    return;
                }

                if (db.hasLoggedIn(uuid)) {
                    cancel();

                    long lastAuthTime = db.getLastAuthTime(uuid);
                    long currentTime = System.currentTimeMillis();

                    if ((currentTime - lastAuthTime) < 10000) {
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            if (!player.isOnline()) {
                                plugin.getPromptManager().stopPrompt(player);
                                Bukkit.getLogger().warning("OauthMC: Player " + player.getName() + " went offline during authentication");
                                return;
                            }

                            plugin.getPromptManager().stopPrompt(player);

                            if (authMeApi.isAuthenticated(player)) {
                                Bukkit.getLogger().info("OauthMC: Player " + player.getName() + " is already authenticated");
                                return;
                            }

                            String playerName = player.getName();
                            boolean isRegistered = authMeApi.isRegistered(playerName);
                            Bukkit.getLogger().info("OauthMC: Player " + playerName + " Authme registration status: " + isRegistered);

                            if (!isRegistered) {
                                String dummyPassword = UUID.randomUUID().toString();
                                Bukkit.getLogger().info("OauthMC: Attempting to auto-register player " + playerName + " in Authme");

                                boolean registered = authMeApi.registerPlayer(playerName, dummyPassword);

                                if (!registered) {
                                    player.sendMessage(MiniMessage.miniMessage().deserialize("<red>注册失败，请联系管理员</red>"));
                                    Bukkit.getLogger().severe("OauthMC: Failed to register player " + playerName + " in Authme");
                                    return;
                                }

                                Bukkit.getLogger().info("OauthMC: Successfully auto-registered player " + playerName + " in Authme");
                            }

                            Bukkit.getLogger().info("OauthMC: Attempting to force login player " + playerName);
                            authMeApi.forceLogin(player);

                            String linuxdoUsername = db.getLinuxDoUsername(uuid);
                            String successMessage = isFirstTime ?
                                    "<green>绑定成功! 欢迎加入服务器 (绑定账号: " + linuxdoUsername + ")</green>" :
                                    "<green>验证成功! 欢迎回来 (绑定账号: " + linuxdoUsername + ")</green>";

                            player.sendMessage(MiniMessage.miniMessage().deserialize(successMessage));
                            Bukkit.getLogger().info("OauthMC: Player " + playerName + " successfully auto-logged in after authorization");
                        });
                    }
                }
            }
        }.runTaskTimerAsynchronously(plugin, AUTH_CHECK_DELAY_TICKS, AUTH_CHECK_PERIOD_TICKS);
    }

    private void schedulePromptTimeout(Player player) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> plugin.getPromptManager().stopPrompt(player), PROMPT_TIMEOUT_TICKS);
    }
}
