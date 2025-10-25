package me.cooleg.oauthmc.commands;

import me.cooleg.oauthmc.OauthMCConfig;
import me.cooleg.oauthmc.persistence.IDatabaseHook;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.UUID;

public class OauthCommand implements CommandExecutor {

    private final IDatabaseHook db;
    private final OauthMCConfig config;

    public OauthCommand(IDatabaseHook db, OauthMCConfig config) {
        this.db = db;
        this.config = config;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(MiniMessage.miniMessage().deserialize(
                    "<yellow>OauthMC 命令帮助:</yellow>\n" +
                    "<white>/oauth status</white> - 查看绑定状态\n" +
                    "<white>/oauth unbind <玩家></white> - 解绑玩家 (管理员)"));
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "status":
                return handleStatus(sender);
            case "unbind":
                return handleUnbind(sender, args);
            default:
                sender.sendMessage(MiniMessage.miniMessage().deserialize(
                        "<red>未知命令! 使用 /oauth 查看帮助</red>"));
                return true;
        }
    }

    private boolean handleStatus(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("This command can only be used by players.");
            return true;
        }

        Player player = (Player) sender;
        UUID uuid = player.getUniqueId();

        Bukkit.getScheduler().runTaskAsynchronously(Bukkit.getPluginManager().getPlugin("OauthMC"), () -> {
            String email = db.getEmail(uuid);
            String linuxdoUsername = db.getLinuxDoUsername(uuid);
            String linuxdoId = db.getLinuxDoId(uuid);
            long lastAuthTime = db.getLastAuthTime(uuid);

            StringBuilder message = new StringBuilder("<yellow>===== OAuth 绑定状态 =====</yellow>\n");

            if (email != null) {
                message.append("<green>已绑定</green>\n");
                message.append("<white>邮箱: </white><gray>").append(email).append("</gray>\n");
                
                if (linuxdoUsername != null) {
                    message.append("<white>Linux.do 用户名: </white><gray>").append(linuxdoUsername).append("</gray>\n");
                }
                
                if (linuxdoId != null) {
                    message.append("<white>Linux.do ID: </white><gray>").append(linuxdoId).append("</gray>\n");
                }

                if (lastAuthTime > 0) {
                    long hoursSinceAuth = (System.currentTimeMillis() - lastAuthTime) / (1000 * 60 * 60);
                    message.append("<white>上次验证: </white><gray>").append(hoursSinceAuth).append(" 小时前</gray>\n");
                    
                    if (config.getSessionDurationHours() > 0) {
                        long hoursRemaining = config.getSessionDurationHours() - hoursSinceAuth;
                        if (hoursRemaining > 0) {
                            message.append("<white>会话有效期: </white><gray>").append(hoursRemaining).append(" 小时</gray>");
                        } else {
                            message.append("<yellow>会话已过期，下次登录需要重新验证</yellow>");
                        }
                    }
                }
            } else {
                message.append("<red>未绑定</red>\n");
                message.append("<white>请重新加入服务器以开始绑定流程</white>");
            }

            Bukkit.getScheduler().runTask(Bukkit.getPluginManager().getPlugin("OauthMC"), () -> {
                player.sendMessage(MiniMessage.miniMessage().deserialize(message.toString()));
            });
        });

        return true;
    }

    private boolean handleUnbind(CommandSender sender, String[] args) {
        if (!sender.hasPermission("oauthmc.admin")) {
            sender.sendMessage(MiniMessage.miniMessage().deserialize(
                    "<red>你没有权限执行此命令!</red>"));
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(MiniMessage.miniMessage().deserialize(
                    "<red>用法: /oauth unbind <玩家></red>"));
            return true;
        }

        String playerName = args[1];
        Player target = Bukkit.getPlayer(playerName);

        if (target == null) {
            sender.sendMessage(MiniMessage.miniMessage().deserialize(
                    "<red>玩家不在线或不存在!</red>"));
            return true;
        }

        UUID targetUuid = target.getUniqueId();

        Bukkit.getScheduler().runTaskAsynchronously(Bukkit.getPluginManager().getPlugin("OauthMC"), () -> {
            if (!db.hasLoggedIn(targetUuid)) {
                Bukkit.getScheduler().runTask(Bukkit.getPluginManager().getPlugin("OauthMC"), () -> {
                    sender.sendMessage(MiniMessage.miniMessage().deserialize(
                            "<red>该玩家尚未绑定账号!</red>"));
                });
                return;
            }

            db.removeBinding(targetUuid);

            Bukkit.getScheduler().runTask(Bukkit.getPluginManager().getPlugin("OauthMC"), () -> {
                sender.sendMessage(MiniMessage.miniMessage().deserialize(
                        "<green>已成功解绑玩家 " + target.getName() + " 的账号!</green>"));
                
                target.sendMessage(MiniMessage.miniMessage().deserialize(
                        "<yellow>你的 OAuth 绑定已被管理员解除</yellow>\n" +
                        "<white>请重新加入服务器以重新绑定账号</white>"));
                
                target.kickPlayer("您的账号绑定已被解除，请重新加入服务器");
            });
        });

        return true;
    }
}
