package me.cooleg.oauthmc.listeners;

import fr.xephi.authme.api.v3.AuthMeApi;
import me.cooleg.oauthmc.OauthMCConfig;
import me.cooleg.oauthmc.authentication.CodeAndLinkResponse;
import me.cooleg.oauthmc.authentication.IOauth;
import me.cooleg.oauthmc.persistence.IDatabaseHook;
import net.kyori.adventure.text.Component;
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

    private final IDatabaseHook db;
    private final IOauth auth;
    private final OauthMCConfig config;
    private final AuthMeApi authMeApi;

    public AuthMeIntegrationListener(IDatabaseHook db, IOauth auth, OauthMCConfig config) {
        this.db = db;
        this.auth = auth;
        this.config = config;
        this.authMeApi = AuthMeApi.getInstance();
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        if (config.getWhitelistedUuids().contains(uuid)) return;

        new BukkitRunnable() {
            @Override
            public void run() {
                handlePlayerLogin(player, uuid);
            }
        }.runTaskAsynchronously(Bukkit.getPluginManager().getPlugin("OauthMC"));
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
        if (!player.isOnline()) return;

        try {
            CodeAndLinkResponse response = auth.beginLogin(uuid);

            String bindingMessage = "<yellow>欢迎来到服务器!</yellow>\n\n" +
                    "<white>首次登录需要绑定 linux.do 账号</white>\n" +
                    "<white>请点击以下链接完成授权:</white>\n" +
                    "<bold><click:open_url:'%link%'><green>[点击授权]</green></click></bold>\n\n" +
                    "<gray>授权有效期：10分钟</gray>\n" +
                    "<gray>完成授权后将自动登录服务器</gray>";

            String message = bindingMessage
                    .replace("%link%", response.loginLink);

            Bukkit.getScheduler().runTask(Bukkit.getPluginManager().getPlugin("OauthMC"), () -> {
                if (player.isOnline()) {
                    player.sendMessage(config.getServerName()
                            .append(Component.text("\n\n"))
                            .append(MiniMessage.miniMessage().deserialize(message)));
                }
            });

            waitForAuthentication(player, uuid, true);
        } catch (Exception ex) {
            Bukkit.getLogger().severe("OauthMC: Failed to initiate binding for player " + player.getName());
            ex.printStackTrace();
        }
    }

    private void handleReturningPlayer(Player player, UUID uuid) {
        long lastAuthTime = db.getLastAuthTime(uuid);
        long currentTime = System.currentTimeMillis();
        long sessionDuration = config.getSessionDurationHours() * 60L * 60L * 1000L;

        if (config.getSessionDurationHours() > 0 && (currentTime - lastAuthTime) < sessionDuration) {
            Bukkit.getScheduler().runTask(Bukkit.getPluginManager().getPlugin("OauthMC"), () -> {
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

        if (!player.isOnline()) return;

        try {
            CodeAndLinkResponse response = auth.beginLogin(uuid);

            String loginMessage = "<yellow>欢迎回来!</yellow>\n\n" +
                    "<white>请重新验证您的 linux.do 账号</white>\n" +
                    "<white>请点击以下链接完成授权:</white>\n" +
                    "<bold><click:open_url:'%link%'><green>[点击授权]</green></click></bold>\n\n" +
                    "<gray>授权有效期：10分钟</gray>\n" +
                    "<gray>完成授权后将自动登录服务器</gray>";

            String message = loginMessage
                    .replace("%link%", response.loginLink);

            Bukkit.getScheduler().runTask(Bukkit.getPluginManager().getPlugin("OauthMC"), () -> {
                if (player.isOnline()) {
                    player.sendMessage(config.getServerName()
                            .append(Component.text("\n\n"))
                            .append(MiniMessage.miniMessage().deserialize(message)));
                }
            });

            waitForAuthentication(player, uuid, false);
        } catch (Exception ex) {
            Bukkit.getLogger().severe("OauthMC: Failed to initiate login for player " + player.getName());
            ex.printStackTrace();
        }
    }

    private void waitForAuthentication(Player player, UUID uuid, boolean isFirstTime) {
        new BukkitRunnable() {
            int checks = 0;
            final int maxChecks = 120;

            @Override
            public void run() {
                if (!player.isOnline()) {
                    cancel();
                    return;
                }

                checks++;
                if (checks > maxChecks) {
                    cancel();
                    return;
                }

                if (db.hasLoggedIn(uuid)) {
                    long lastAuthTime = db.getLastAuthTime(uuid);
                    long currentTime = System.currentTimeMillis();
                    
                    if ((currentTime - lastAuthTime) < 10000) {
                        Bukkit.getScheduler().runTask(Bukkit.getPluginManager().getPlugin("OauthMC"), () -> {
                            if (player.isOnline() && !authMeApi.isAuthenticated(player)) {
                                authMeApi.forceLogin(player);
                                
                                String linuxdoUsername = db.getLinuxDoUsername(uuid);
                                String successMessage = isFirstTime ? 
                                        "<green>绑定成功! 欢迎加入服务器 (绑定账号: " + linuxdoUsername + ")</green>" :
                                        "<green>验证成功! 欢迎回来 (绑定账号: " + linuxdoUsername + ")</green>";
                                
                                player.sendMessage(MiniMessage.miniMessage().deserialize(successMessage));
                                Bukkit.getLogger().info("OauthMC: Player " + player.getName() + " authenticated successfully");
                            }
                        });
                        cancel();
                    }
                }
            }
        }.runTaskTimerAsynchronously(Bukkit.getPluginManager().getPlugin("OauthMC"), 40L, 20L);
    }
}
