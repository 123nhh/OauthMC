package me.cooleg.oauthmc.listeners;

import me.cooleg.oauthmc.OauthMCConfig;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

public class PlayerCommandListener implements Listener {

    private final OauthMCConfig config;

    public PlayerCommandListener(OauthMCConfig config) {
        this.config = config;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        if (!config.isDisablePasswordLogin()) return;

        String command = event.getMessage().toLowerCase();
        Player player = event.getPlayer();

        if (command.startsWith("/login") || command.startsWith("/l ") || 
            command.startsWith("/register") || command.startsWith("/reg ")) {
            event.setCancelled(true);
            player.sendMessage(MiniMessage.miniMessage().deserialize(
                    "<red>密码登录已被禁用!</red>\n" +
                    "<yellow>本服务器使用 linux.do OAuth 验证</yellow>\n" +
                    "<white>请按照加入服务器时的提示完成验证</white>"));
        }
    }
}
